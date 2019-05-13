# filemerge.py - file-level merge handling for Mercurial
#
# Copyright 2006, 2007, 2008 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import short
from i18n import _
import util, simplemerge, match, error
import os, tempfile, re, filecmp

def _toolstr(ui, tool, part, default=""):
    return ui.config("merge-tools", tool + "." + part, default)

def _toolbool(ui, tool, part, default=False):
    return ui.configbool("merge-tools", tool + "." + part, default)

def _toollist(ui, tool, part, default=[]):
    return ui.configlist("merge-tools", tool + "." + part, default)

internals = {}

def internaltool(name, trymerge, onfailure=None):
    '''return a decorator for populating internal merge tool table'''
    def decorator(func):
        fullname = 'internal:' + name
        func.__doc__ = "``%s``\n" % fullname + func.__doc__.strip()
        internals[fullname] = func
        func.trymerge = trymerge
        func.onfailure = onfailure
        return func
    return decorator

def _findtool(ui, tool):
    if tool in internals:
        return tool
    for kn in ("regkey", "regkeyalt"):
        k = _toolstr(ui, tool, kn)
        if not k:
            continue
        p = util.lookupreg(k, _toolstr(ui, tool, "regname"))
        if p:
            p = util.findexe(p + _toolstr(ui, tool, "regappend"))
            if p:
                return p
    exe = _toolstr(ui, tool, "executable", tool)
    return util.findexe(util.expandpath(exe))

def _picktool(repo, ui, path, binary, symlink):
    def check(tool, pat, symlink, binary):
        tmsg = tool
        if pat:
            tmsg += " specified for " + pat
        if not _findtool(ui, tool):
            if pat: # explicitly requested tool deserves a warning
                ui.warn(_("couldn't find merge tool %s\n") % tmsg)
            else: # configured but non-existing tools are more silent
                ui.note(_("couldn't find merge tool %s\n") % tmsg)
        elif symlink and not _toolbool(ui, tool, "symlink"):
            ui.warn(_("tool %s can't handle symlinks\n") % tmsg)
        elif binary and not _toolbool(ui, tool, "binary"):
            ui.warn(_("tool %s can't handle binary\n") % tmsg)
        elif not util.gui() and _toolbool(ui, tool, "gui"):
            ui.warn(_("tool %s requires a GUI\n") % tmsg)
        else:
            return True
        return False

    # forcemerge comes from command line arguments, highest priority
    force = ui.config('ui', 'forcemerge')
    if force:
        toolpath = _findtool(ui, force)
        if toolpath:
            return (force, util.shellquote(toolpath))
        else:
            # mimic HGMERGE if given tool not found
            return (force, force)

    # HGMERGE takes next precedence
    hgmerge = os.environ.get("HGMERGE")
    if hgmerge:
        return (hgmerge, hgmerge)

    # then patterns
    for pat, tool in ui.configitems("merge-patterns"):
        mf = match.match(repo.root, '', [pat])
        if mf(path) and check(tool, pat, symlink, False):
            toolpath = _findtool(ui, tool)
            return (tool, util.shellquote(toolpath))

    # then merge tools
    tools = {}
    for k, v in ui.configitems("merge-tools"):
        t = k.split('.')[0]
        if t not in tools:
            tools[t] = int(_toolstr(ui, t, "priority", "0"))
    names = tools.keys()
    tools = sorted([(-p, t) for t, p in tools.items()])
    uimerge = ui.config("ui", "merge")
    if uimerge:
        if uimerge not in names:
            return (uimerge, uimerge)
        tools.insert(0, (None, uimerge)) # highest priority
    tools.append((None, "hgmerge")) # the old default, if found
    for p, t in tools:
        if check(t, None, symlink, binary):
            toolpath = _findtool(ui, t)
            return (t, util.shellquote(toolpath))

    # internal merge or prompt as last resort
    if symlink or binary:
        return "internal:prompt", None
    return "internal:merge", None

def _eoltype(data):
    "Guess the EOL type of a file"
    if '\0' in data: # binary
        return None
    if '\r\n' in data: # Windows
        return '\r\n'
    if '\r' in data: # Old Mac
        return '\r'
    if '\n' in data: # UNIX
        return '\n'
    return None # unknown

def _matcheol(file, origfile):
    "Convert EOL markers in a file to match origfile"
    tostyle = _eoltype(util.readfile(origfile))
    if tostyle:
        data = util.readfile(file)
        style = _eoltype(data)
        if style:
            newdata = data.replace(style, tostyle)
            if newdata != data:
                util.writefile(file, newdata)

@internaltool('prompt', False)
def _iprompt(repo, mynode, orig, fcd, fco, fca, toolconf):
    """Asks the user which of the local or the other version to keep as
    the merged version."""
    ui = repo.ui
    fd = fcd.path()

    if ui.promptchoice(_(" no tool found to merge %s\n"
                         "keep (l)ocal or take (o)ther?") % fd,
                       (_("&Local"), _("&Other")), 0):
        return _iother(repo, mynode, orig, fcd, fco, fca, toolconf)
    else:
        return _ilocal(repo, mynode, orig, fcd, fco, fca, toolconf)

@internaltool('local', False)
def _ilocal(repo, mynode, orig, fcd, fco, fca, toolconf):
    """Uses the local version of files as the merged version."""
    return 0

@internaltool('other', False)
def _iother(repo, mynode, orig, fcd, fco, fca, toolconf):
    """Uses the other version of files as the merged version."""
    repo.wwrite(fcd.path(), fco.data(), fco.flags())
    return 0

@internaltool('fail', False)
def _ifail(repo, mynode, orig, fcd, fco, fca, toolconf):
    """
    Rather than attempting to merge files that were modified on both
    branches, it marks them as unresolved. The resolve command must be
    used to resolve these conflicts."""
    return 1

def _premerge(repo, toolconf, files):
    tool, toolpath, binary, symlink = toolconf
    if symlink:
        return 1
    a, b, c, back = files

    ui = repo.ui

    # do we attempt to simplemerge first?
    try:
        premerge = _toolbool(ui, tool, "premerge", not binary)
    except error.ConfigError:
        premerge = _toolstr(ui, tool, "premerge").lower()
        valid = 'keep'.split()
        if premerge not in valid:
            _valid = ', '.join(["'" + v + "'" for v in valid])
            raise error.ConfigError(_("%s.premerge not valid "
                                      "('%s' is neither boolean nor %s)") %
                                    (tool, premerge, _valid))

    if premerge:
        r = simplemerge.simplemerge(ui, a, b, c, quiet=True)
        if not r:
            ui.debug(" premerge successful\n")
            return 0
        if premerge != 'keep':
            util.copyfile(back, a) # restore from backup and try again
    return 1 # continue merging

@internaltool('merge', True,
              _("merging %s incomplete! "
                "(edit conflicts, then use 'hg resolve --mark')\n"))
def _imerge(repo, mynode, orig, fcd, fco, fca, toolconf, files):
    """
    Uses the internal non-interactive simple merge algorithm for merging
    files. It will fail if there are any conflicts and leave markers in
    the partially merged file."""
    tool, toolpath, binary, symlink = toolconf
    if symlink:
        repo.ui.warn(_('warning: internal:merge cannot merge symlinks '
                       'for %s\n') % fcd.path())
        return False, 1

    r = _premerge(repo, toolconf, files)
    if r:
        a, b, c, back = files

        ui = repo.ui

        r = simplemerge.simplemerge(ui, a, b, c, label=['local', 'other'])
        return True, r
    return False, 0

@internaltool('dump', True)
def _idump(repo, mynode, orig, fcd, fco, fca, toolconf, files):
    """
    Creates three versions of the files to merge, containing the
    contents of local, other and base. These files can then be used to
    perform a merge manually. If the file to be merged is named
    ``a.txt``, these files will accordingly be named ``a.txt.local``,
    ``a.txt.other`` and ``a.txt.base`` and they will be placed in the
    same directory as ``a.txt``."""
    r = _premerge(repo, toolconf, files)
    if r:
        a, b, c, back = files

        fd = fcd.path()

        util.copyfile(a, a + ".local")
        repo.wwrite(fd + ".other", fco.data(), fco.flags())
        repo.wwrite(fd + ".base", fca.data(), fca.flags())
    return False, r

def _xmerge(repo, mynode, orig, fcd, fco, fca, toolconf, files):
    r = _premerge(repo, toolconf, files)
    if r:
        tool, toolpath, binary, symlink = toolconf
        a, b, c, back = files
        out = ""
        env = dict(HG_FILE=fcd.path(),
                   HG_MY_NODE=short(mynode),
                   HG_OTHER_NODE=str(fco.changectx()),
                   HG_BASE_NODE=str(fca.changectx()),
                   HG_MY_ISLINK='l' in fcd.flags(),
                   HG_OTHER_ISLINK='l' in fco.flags(),
                   HG_BASE_ISLINK='l' in fca.flags())

        ui = repo.ui

        args = _toolstr(ui, tool, "args", '$local $base $other')
        if "$output" in args:
            out, a = a, back # read input from backup, write to original
        replace = dict(local=a, base=b, other=c, output=out)
        args = util.interpolate(r'\$', replace, args,
                                lambda s: util.shellquote(util.localpath(s)))
        r = util.system(toolpath + ' ' + args, cwd=repo.root, environ=env,
                        out=ui.fout)
        return True, r
    return False, 0

def filemerge(repo, mynode, orig, fcd, fco, fca):
    """perform a 3-way merge in the working directory

    mynode = parent node before merge
    orig = original local filename before merge
    fco = other file context
    fca = ancestor file context
    fcd = local file context for current/destination file
    """

    def temp(prefix, ctx):
        pre = "%s~%s." % (os.path.basename(ctx.path()), prefix)
        (fd, name) = tempfile.mkstemp(prefix=pre)
        data = repo.wwritedata(ctx.path(), ctx.data())
        f = os.fdopen(fd, "wb")
        f.write(data)
        f.close()
        return name

    if not fco.cmp(fcd): # files identical?
        return None

    ui = repo.ui
    fd = fcd.path()
    binary = fcd.isbinary() or fco.isbinary() or fca.isbinary()
    symlink = 'l' in fcd.flags() + fco.flags()
    tool, toolpath = _picktool(repo, ui, fd, binary, symlink)
    ui.debug("picked tool '%s' for %s (binary %s symlink %s)\n" %
               (tool, fd, binary, symlink))

    if tool in internals:
        func = internals[tool]
        trymerge = func.trymerge
        onfailure = func.onfailure
    else:
        func = _xmerge
        trymerge = True
        onfailure = _("merging %s failed!\n")

    toolconf = tool, toolpath, binary, symlink

    if not trymerge:
        return func(repo, mynode, orig, fcd, fco, fca, toolconf)

    a = repo.wjoin(fd)
    b = temp("base", fca)
    c = temp("other", fco)
    back = a + ".orig"
    util.copyfile(a, back)

    if orig != fco.path():
        ui.status(_("merging %s and %s to %s\n") % (orig, fco.path(), fd))
    else:
        ui.status(_("merging %s\n") % fd)

    ui.debug("my %s other %s ancestor %s\n" % (fcd, fco, fca))

    needcheck, r = func(repo, mynode, orig, fcd, fco, fca, toolconf,
                        (a, b, c, back))
    if not needcheck:
        if r:
            if onfailure:
                ui.warn(onfailure % fd)
        else:
            os.unlink(back)

        os.unlink(b)
        os.unlink(c)
        return r

    if not r and (_toolbool(ui, tool, "checkconflicts") or
                  'conflicts' in _toollist(ui, tool, "check")):
        if re.search("^(<<<<<<< .*|=======|>>>>>>> .*)$", fcd.data(),
                     re.MULTILINE):
            r = 1

    checked = False
    if 'prompt' in _toollist(ui, tool, "check"):
        checked = True
        if ui.promptchoice(_("was merge of '%s' successful (yn)?") % fd,
                           (_("&Yes"), _("&No")), 1):
            r = 1

    if not r and not checked and (_toolbool(ui, tool, "checkchanged") or
                                  'changed' in _toollist(ui, tool, "check")):
        if filecmp.cmp(a, back):
            if ui.promptchoice(_(" output file %s appears unchanged\n"
                                 "was merge successful (yn)?") % fd,
                               (_("&Yes"), _("&No")), 1):
                r = 1

    if _toolbool(ui, tool, "fixeol"):
        _matcheol(a, back)

    if r:
        if onfailure:
            ui.warn(onfailure % fd)
    else:
        os.unlink(back)

    os.unlink(b)
    os.unlink(c)
    return r

# tell hggettext to extract docstrings from these functions:
i18nfunctions = internals.values()
