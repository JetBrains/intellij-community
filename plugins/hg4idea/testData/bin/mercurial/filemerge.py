# filemerge.py - file-level merge handling for Mercurial
#
# Copyright 2006, 2007, 2008 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import contextlib
import os
import re
import shutil

from .i18n import _
from .node import (
    hex,
    short,
)

from . import (
    encoding,
    error,
    formatter,
    match,
    pycompat,
    registrar,
    scmutil,
    simplemerge,
    tagmerge,
    templatekw,
    templater,
    templateutil,
    util,
)

from .utils import (
    procutil,
    stringutil,
)


def _toolstr(ui, tool, part, *args):
    return ui.config(b"merge-tools", tool + b"." + part, *args)


def _toolbool(ui, tool, part, *args):
    return ui.configbool(b"merge-tools", tool + b"." + part, *args)


def _toollist(ui, tool, part):
    return ui.configlist(b"merge-tools", tool + b"." + part)


internals = {}
# Merge tools to document.
internalsdoc = {}

internaltool = registrar.internalmerge()

# internal tool merge types
nomerge = internaltool.nomerge
mergeonly = internaltool.mergeonly  # just the full merge, no premerge
fullmerge = internaltool.fullmerge  # both premerge and merge

# IMPORTANT: keep the last line of this prompt very short ("What do you want to
# do?") because of issue6158, ideally to <40 English characters (to allow other
# languages that may take more columns to still have a chance to fit in an
# 80-column screen).
_localchangedotherdeletedmsg = _(
    b"file '%(fd)s' was deleted in other%(o)s but was modified in local%(l)s.\n"
    b"You can use (c)hanged version, (d)elete, or leave (u)nresolved.\n"
    b"What do you want to do?"
    b"$$ &Changed $$ &Delete $$ &Unresolved"
)

_otherchangedlocaldeletedmsg = _(
    b"file '%(fd)s' was deleted in local%(l)s but was modified in other%(o)s.\n"
    b"You can use (c)hanged version, leave (d)eleted, or leave (u)nresolved.\n"
    b"What do you want to do?"
    b"$$ &Changed $$ &Deleted $$ &Unresolved"
)


class absentfilectx:
    """Represents a file that's ostensibly in a context but is actually not
    present in it.

    This is here because it's very specific to the filemerge code for now --
    other code is likely going to break with the values this returns."""

    def __init__(self, ctx, f):
        self._ctx = ctx
        self._f = f

    def __bytes__(self):
        return b'absent file %s@%s' % (self._f, self._ctx)

    def path(self):
        return self._f

    def size(self):
        return None

    def data(self):
        return None

    def filenode(self):
        return self._ctx.repo().nullid

    _customcmp = True

    def cmp(self, fctx):
        """compare with other file context

        returns True if different from fctx.
        """
        return not (
            fctx.isabsent()
            and fctx.changectx() == self.changectx()
            and fctx.path() == self.path()
        )

    def flags(self):
        return b''

    def changectx(self):
        return self._ctx

    def isbinary(self):
        return False

    def isabsent(self):
        return True


def _findtool(ui, tool):
    if tool in internals:
        return tool
    cmd = _toolstr(ui, tool, b"executable", tool)
    if cmd.startswith(b'python:'):
        return cmd
    return findexternaltool(ui, tool)


def _quotetoolpath(cmd):
    if cmd.startswith(b'python:'):
        return cmd
    return procutil.shellquote(cmd)


def findexternaltool(ui, tool):
    for kn in (b"regkey", b"regkeyalt"):
        k = _toolstr(ui, tool, kn)
        if not k:
            continue
        p = util.lookupreg(k, _toolstr(ui, tool, b"regname"))
        if p:
            p = procutil.findexe(p + _toolstr(ui, tool, b"regappend"))
            if p:
                return p
    exe = _toolstr(ui, tool, b"executable", tool)
    return procutil.findexe(util.expandpath(exe))


def _picktool(repo, ui, path, binary, symlink, changedelete):
    strictcheck = ui.configbool(b'merge', b'strict-capability-check')

    def hascapability(tool, capability, strict=False):
        if tool in internals:
            return strict and internals[tool].capabilities.get(capability)
        return _toolbool(ui, tool, capability)

    def supportscd(tool):
        return tool in internals and internals[tool].mergetype == nomerge

    def check(tool, pat, symlink, binary, changedelete):
        tmsg = tool
        if pat:
            tmsg = _(b"%s (for pattern %s)") % (tool, pat)
        if not _findtool(ui, tool):
            if pat:  # explicitly requested tool deserves a warning
                ui.warn(_(b"couldn't find merge tool %s\n") % tmsg)
            else:  # configured but non-existing tools are more silent
                ui.note(_(b"couldn't find merge tool %s\n") % tmsg)
        elif symlink and not hascapability(tool, b"symlink", strictcheck):
            ui.warn(_(b"tool %s can't handle symlinks\n") % tmsg)
        elif binary and not hascapability(tool, b"binary", strictcheck):
            ui.warn(_(b"tool %s can't handle binary\n") % tmsg)
        elif changedelete and not supportscd(tool):
            # the nomerge tools are the only tools that support change/delete
            # conflicts
            pass
        elif not procutil.gui() and _toolbool(ui, tool, b"gui"):
            ui.warn(_(b"tool %s requires a GUI\n") % tmsg)
        else:
            return True
        return False

    # internal config: ui.forcemerge
    # forcemerge comes from command line arguments, highest priority
    force = ui.config(b'ui', b'forcemerge')
    if force:
        toolpath = _findtool(ui, force)
        if changedelete and not supportscd(toolpath):
            return b":prompt", None
        else:
            if toolpath:
                return (force, _quotetoolpath(toolpath))
            else:
                # mimic HGMERGE if given tool not found
                return (force, force)

    # HGMERGE takes next precedence
    hgmerge = encoding.environ.get(b"HGMERGE")
    if hgmerge:
        if changedelete and not supportscd(hgmerge):
            return b":prompt", None
        else:
            return (hgmerge, hgmerge)

    # then patterns

    # whether binary capability should be checked strictly
    binarycap = binary and strictcheck

    for pat, tool in ui.configitems(b"merge-patterns"):
        mf = match.match(repo.root, b'', [pat])
        if mf(path) and check(tool, pat, symlink, binarycap, changedelete):
            if binary and not hascapability(tool, b"binary", strict=True):
                ui.warn(
                    _(
                        b"warning: check merge-patterns configurations,"
                        b" if %r for binary file %r is unintentional\n"
                        b"(see 'hg help merge-tools'"
                        b" for binary files capability)\n"
                    )
                    % (pycompat.bytestr(tool), pycompat.bytestr(path))
                )
            toolpath = _findtool(ui, tool)
            return (tool, _quotetoolpath(toolpath))

    # then merge tools
    tools = {}
    disabled = set()
    for k, v in ui.configitems(b"merge-tools"):
        t = k.split(b'.')[0]
        if t not in tools:
            tools[t] = int(_toolstr(ui, t, b"priority"))
        if _toolbool(ui, t, b"disabled"):
            disabled.add(t)
    names = tools.keys()
    tools = sorted(
        [(-p, tool) for tool, p in tools.items() if tool not in disabled]
    )
    uimerge = ui.config(b"ui", b"merge")
    if uimerge:
        # external tools defined in uimerge won't be able to handle
        # change/delete conflicts
        if check(uimerge, path, symlink, binary, changedelete):
            if uimerge not in names and not changedelete:
                return (uimerge, uimerge)
            tools.insert(0, (None, uimerge))  # highest priority
    tools.append((None, b"hgmerge"))  # the old default, if found
    for p, t in tools:
        if check(t, None, symlink, binary, changedelete):
            toolpath = _findtool(ui, t)
            return (t, _quotetoolpath(toolpath))

    # internal merge or prompt as last resort
    if symlink or binary or changedelete:
        if not changedelete and len(tools):
            # any tool is rejected by capability for symlink or binary
            ui.warn(_(b"no tool found to merge %s\n") % path)
        return b":prompt", None
    return b":merge", None


def _eoltype(data):
    """Guess the EOL type of a file"""
    if b'\0' in data:  # binary
        return None
    if b'\r\n' in data:  # Windows
        return b'\r\n'
    if b'\r' in data:  # Old Mac
        return b'\r'
    if b'\n' in data:  # UNIX
        return b'\n'
    return None  # unknown


def _matcheol(file, backup):
    """Convert EOL markers in a file to match origfile"""
    tostyle = _eoltype(backup.data())  # No repo.wread filters?
    if tostyle:
        data = util.readfile(file)
        style = _eoltype(data)
        if style:
            newdata = data.replace(style, tostyle)
            if newdata != data:
                util.writefile(file, newdata)


@internaltool(b'prompt', nomerge)
def _iprompt(repo, mynode, local, other, base, toolconf):
    """Asks the user which of the local `p1()` or the other `p2()` version to
    keep as the merged version."""
    ui = repo.ui
    fd = local.fctx.path()
    uipathfn = scmutil.getuipathfn(repo)

    # Avoid prompting during an in-memory merge since it doesn't support merge
    # conflicts.
    if local.fctx.changectx().isinmemory():
        raise error.InMemoryMergeConflictsError(
            b'in-memory merge does not support file conflicts'
        )

    prompts = partextras([local.label, other.label])
    prompts[b'fd'] = uipathfn(fd)
    try:
        if other.fctx.isabsent():
            index = ui.promptchoice(_localchangedotherdeletedmsg % prompts, 2)
            choice = [b'local', b'other', b'unresolved'][index]
        elif local.fctx.isabsent():
            index = ui.promptchoice(_otherchangedlocaldeletedmsg % prompts, 2)
            choice = [b'other', b'local', b'unresolved'][index]
        else:
            # IMPORTANT: keep the last line of this prompt ("What do you want to
            # do?") very short, see comment next to _localchangedotherdeletedmsg
            # at the top of the file for details.
            index = ui.promptchoice(
                _(
                    b"file '%(fd)s' needs to be resolved.\n"
                    b"You can keep (l)ocal%(l)s, take (o)ther%(o)s, or leave "
                    b"(u)nresolved.\n"
                    b"What do you want to do?"
                    b"$$ &Local $$ &Other $$ &Unresolved"
                )
                % prompts,
                2,
            )
            choice = [b'local', b'other', b'unresolved'][index]

        if choice == b'other':
            return _iother(repo, mynode, local, other, base, toolconf)
        elif choice == b'local':
            return _ilocal(repo, mynode, local, other, base, toolconf)
        elif choice == b'unresolved':
            return _ifail(repo, mynode, local, other, base, toolconf)
    except error.ResponseExpected:
        ui.write(b"\n")
        return _ifail(repo, mynode, local, other, base, toolconf)


@internaltool(b'local', nomerge)
def _ilocal(repo, mynode, local, other, base, toolconf):
    """Uses the local `p1()` version of files as the merged version."""
    return 0, local.fctx.isabsent()


@internaltool(b'other', nomerge)
def _iother(repo, mynode, local, other, base, toolconf):
    """Uses the other `p2()` version of files as the merged version."""
    if other.fctx.isabsent():
        # local changed, remote deleted -- 'deleted' picked
        _underlyingfctxifabsent(local.fctx).remove()
        deleted = True
    else:
        _underlyingfctxifabsent(local.fctx).write(
            other.fctx.data(), other.fctx.flags()
        )
        deleted = False
    return 0, deleted


@internaltool(b'fail', nomerge)
def _ifail(repo, mynode, local, other, base, toolconf):
    """
    Rather than attempting to merge files that were modified on both
    branches, it marks them as unresolved. The resolve command must be
    used to resolve these conflicts."""
    # for change/delete conflicts write out the changed version, then fail
    if local.fctx.isabsent():
        _underlyingfctxifabsent(local.fctx).write(
            other.fctx.data(), other.fctx.flags()
        )
    return 1, False


def _underlyingfctxifabsent(filectx):
    """Sometimes when resolving, our fcd is actually an absentfilectx, but
    we want to write to it (to do the resolve). This helper returns the
    underyling workingfilectx in that case.
    """
    if filectx.isabsent():
        return filectx.changectx()[filectx.path()]
    else:
        return filectx


def _verifytext(input, ui):
    """verifies that text is non-binary"""
    if stringutil.binary(input.text()):
        msg = _(b"%s looks like a binary file.") % input.fctx.path()
        ui.warn(_(b'warning: %s\n') % msg)
        raise error.Abort(msg)


def _premerge(repo, local, other, base, toolconf):
    tool, toolpath, binary, symlink, scriptfn = toolconf
    if symlink or local.fctx.isabsent() or other.fctx.isabsent():
        return 1

    ui = repo.ui

    validkeep = [b'keep', b'keep-merge3', b'keep-mergediff']

    # do we attempt to simplemerge first?
    try:
        premerge = _toolbool(ui, tool, b"premerge", not binary)
    except error.ConfigError:
        premerge = _toolstr(ui, tool, b"premerge", b"").lower()
        if premerge not in validkeep:
            _valid = b', '.join([b"'" + v + b"'" for v in validkeep])
            raise error.ConfigError(
                _(b"%s.premerge not valid ('%s' is neither boolean nor %s)")
                % (tool, premerge, _valid)
            )

    if premerge:
        mode = b'merge'
        if premerge == b'keep-mergediff':
            mode = b'mergediff'
        elif premerge == b'keep-merge3':
            mode = b'merge3'
        if any(
            stringutil.binary(input.text()) for input in (local, base, other)
        ):
            return 1  # continue merging
        merged_text, conflicts = simplemerge.simplemerge(
            local, base, other, mode=mode
        )
        if not conflicts or premerge in validkeep:
            # fcd.flags() already has the merged flags (done in
            # mergestate.resolve())
            local.fctx.write(merged_text, local.fctx.flags())
        if not conflicts:
            ui.debug(b" premerge successful\n")
            return 0
    return 1  # continue merging


def _mergecheck(repo, mynode, fcd, fco, fca, toolconf):
    tool, toolpath, binary, symlink, scriptfn = toolconf
    uipathfn = scmutil.getuipathfn(repo)
    if symlink:
        repo.ui.warn(
            _(b'warning: internal %s cannot merge symlinks for %s\n')
            % (tool, uipathfn(fcd.path()))
        )
        return False
    if fcd.isabsent() or fco.isabsent():
        repo.ui.warn(
            _(
                b'warning: internal %s cannot merge change/delete '
                b'conflict for %s\n'
            )
            % (tool, uipathfn(fcd.path()))
        )
        return False
    return True


def _merge(repo, local, other, base, mode):
    """
    Uses the internal non-interactive simple merge algorithm for merging
    files. It will fail if there are any conflicts and leave markers in
    the partially merged file. Markers will have two sections, one for each
    side of merge, unless mode equals 'union' or 'union-other-first' which
    suppresses the markers."""
    ui = repo.ui

    try:
        _verifytext(local, ui)
        _verifytext(base, ui)
        _verifytext(other, ui)
    except error.Abort:
        return True, True, False
    else:
        merged_text, conflicts = simplemerge.simplemerge(
            local, base, other, mode=mode
        )
        # fcd.flags() already has the merged flags (done in
        # mergestate.resolve())
        local.fctx.write(merged_text, local.fctx.flags())
        return True, conflicts, False


@internaltool(
    b'union',
    fullmerge,
    _(
        b"warning: conflicts while merging %s! "
        b"(edit, then use 'hg resolve --mark')\n"
    ),
    precheck=_mergecheck,
)
def _iunion(repo, mynode, local, other, base, toolconf, backup):
    """
    Uses the internal non-interactive simple merge algorithm for merging
    files. It will use both local and other sides for conflict regions by
    adding local on top of other.
    No markers are inserted."""
    return _merge(repo, local, other, base, b'union')


@internaltool(
    b'union-other-first',
    fullmerge,
    _(
        b"warning: conflicts while merging %s! "
        b"(edit, then use 'hg resolve --mark')\n"
    ),
    precheck=_mergecheck,
)
def _iunion_other_first(repo, mynode, local, other, base, toolconf, backup):
    """
    Like :union, but add other on top of local."""
    return _merge(repo, local, other, base, b'union-other-first')


@internaltool(
    b'merge',
    fullmerge,
    _(
        b"warning: conflicts while merging %s! "
        b"(edit, then use 'hg resolve --mark')\n"
    ),
    precheck=_mergecheck,
)
def _imerge(repo, mynode, local, other, base, toolconf, backup):
    """
    Uses the internal non-interactive simple merge algorithm for merging
    files. It will fail if there are any conflicts and leave markers in
    the partially merged file. Markers will have two sections, one for each side
    of merge."""
    return _merge(repo, local, other, base, b'merge')


@internaltool(
    b'merge3',
    fullmerge,
    _(
        b"warning: conflicts while merging %s! "
        b"(edit, then use 'hg resolve --mark')\n"
    ),
    precheck=_mergecheck,
)
def _imerge3(repo, mynode, local, other, base, toolconf, backup):
    """
    Uses the internal non-interactive simple merge algorithm for merging
    files. It will fail if there are any conflicts and leave markers in
    the partially merged file. Marker will have three sections, one from each
    side of the merge and one for the base content."""
    return _merge(repo, local, other, base, b'merge3')


@internaltool(
    b'merge3-lie-about-conflicts',
    fullmerge,
    b'',
    precheck=_mergecheck,
)
def _imerge3alwaysgood(*args, **kwargs):
    # Like merge3, but record conflicts as resolved with markers in place.
    #
    # This is used for `diff.merge` to show the differences between
    # the auto-merge state and the committed merge state. It may be
    # useful for other things.
    b1, junk, b2 = _imerge3(*args, **kwargs)
    # TODO is this right? I'm not sure what these return values mean,
    # but as far as I can tell this will indicate to callers tha the
    # merge succeeded.
    return b1, False, b2


@internaltool(
    b'mergediff',
    fullmerge,
    _(
        b"warning: conflicts while merging %s! "
        b"(edit, then use 'hg resolve --mark')\n"
    ),
    precheck=_mergecheck,
)
def _imerge_diff(repo, mynode, local, other, base, toolconf, backup):
    """
    Uses the internal non-interactive simple merge algorithm for merging
    files. It will fail if there are any conflicts and leave markers in
    the partially merged file. The marker will have two sections, one with the
    content from one side of the merge, and one with a diff from the base
    content to the content on the other side. (experimental)"""
    return _merge(repo, local, other, base, b'mergediff')


@internaltool(b'merge-local', mergeonly, precheck=_mergecheck)
def _imergelocal(repo, mynode, local, other, base, toolconf, backup):
    """
    Like :merge, but resolve all conflicts non-interactively in favor
    of the local `p1()` changes."""
    return _merge(repo, local, other, base, b'local')


@internaltool(b'merge-other', mergeonly, precheck=_mergecheck)
def _imergeother(repo, mynode, local, other, base, toolconf, backup):
    """
    Like :merge, but resolve all conflicts non-interactively in favor
    of the other `p2()` changes."""
    return _merge(repo, local, other, base, b'other')


@internaltool(
    b'tagmerge',
    mergeonly,
    _(
        b"automatic tag merging of %s failed! "
        b"(use 'hg resolve --tool :merge' or another merge "
        b"tool of your choice)\n"
    ),
)
def _itagmerge(repo, mynode, local, other, base, toolconf, backup):
    """
    Uses the internal tag merge algorithm (experimental).
    """
    success, status = tagmerge.merge(repo, local.fctx, other.fctx, base.fctx)
    return success, status, False


@internaltool(b'dump', fullmerge, binary=True, symlink=True)
def _idump(repo, mynode, local, other, base, toolconf, backup):
    """
    Creates three versions of the files to merge, containing the
    contents of local, other and base. These files can then be used to
    perform a merge manually. If the file to be merged is named
    ``a.txt``, these files will accordingly be named ``a.txt.local``,
    ``a.txt.other`` and ``a.txt.base`` and they will be placed in the
    same directory as ``a.txt``.

    This implies premerge. Therefore, files aren't dumped, if premerge
    runs successfully. Use :forcedump to forcibly write files out.
    """
    a = _workingpath(repo, local.fctx)
    fd = local.fctx.path()

    from . import context

    if isinstance(local.fctx, context.overlayworkingfilectx):
        raise error.InMemoryMergeConflictsError(
            b'in-memory merge does not support the :dump tool.'
        )

    util.writefile(a + b".local", local.fctx.decodeddata())
    repo.wwrite(fd + b".other", other.fctx.data(), other.fctx.flags())
    repo.wwrite(fd + b".base", base.fctx.data(), base.fctx.flags())
    return False, 1, False


@internaltool(b'forcedump', mergeonly, binary=True, symlink=True)
def _forcedump(repo, mynode, local, other, base, toolconf, backup):
    """
    Creates three versions of the files as same as :dump, but omits premerge.
    """
    return _idump(repo, mynode, local, other, base, toolconf, backup)


def _xmergeimm(repo, mynode, local, other, base, toolconf, backup):
    # In-memory merge simply raises an exception on all external merge tools,
    # for now.
    #
    # It would be possible to run most tools with temporary files, but this
    # raises the question of what to do if the user only partially resolves the
    # file -- we can't leave a merge state. (Copy to somewhere in the .hg/
    # directory and tell the user how to get it is my best idea, but it's
    # clunky.)
    raise error.InMemoryMergeConflictsError(
        b'in-memory merge does not support external merge tools'
    )


def _describemerge(ui, repo, mynode, fcl, fcb, fco, env, toolpath, args):
    tmpl = ui.config(b'command-templates', b'pre-merge-tool-output')
    if not tmpl:
        return

    mappingdict = templateutil.mappingdict
    props = {
        b'ctx': fcl.changectx(),
        b'node': hex(mynode),
        b'path': fcl.path(),
        b'local': mappingdict(
            {
                b'ctx': fcl.changectx(),
                b'fctx': fcl,
                b'node': hex(mynode),
                b'name': _(b'local'),
                b'islink': b'l' in fcl.flags(),
                b'label': env[b'HG_MY_LABEL'],
            }
        ),
        b'base': mappingdict(
            {
                b'ctx': fcb.changectx(),
                b'fctx': fcb,
                b'name': _(b'base'),
                b'islink': b'l' in fcb.flags(),
                b'label': env[b'HG_BASE_LABEL'],
            }
        ),
        b'other': mappingdict(
            {
                b'ctx': fco.changectx(),
                b'fctx': fco,
                b'name': _(b'other'),
                b'islink': b'l' in fco.flags(),
                b'label': env[b'HG_OTHER_LABEL'],
            }
        ),
        b'toolpath': toolpath,
        b'toolargs': args,
    }

    # TODO: make all of this something that can be specified on a per-tool basis
    tmpl = templater.unquotestring(tmpl)

    # Not using cmdutil.rendertemplate here since it causes errors importing
    # things for us to import cmdutil.
    tres = formatter.templateresources(ui, repo)
    t = formatter.maketemplater(
        ui, tmpl, defaults=templatekw.keywords, resources=tres
    )
    ui.status(t.renderdefault(props))


def _xmerge(repo, mynode, local, other, base, toolconf, backup):
    fcd = local.fctx
    fco = other.fctx
    fca = base.fctx
    tool, toolpath, binary, symlink, scriptfn = toolconf
    uipathfn = scmutil.getuipathfn(repo)
    if fcd.isabsent() or fco.isabsent():
        repo.ui.warn(
            _(b'warning: %s cannot merge change/delete conflict for %s\n')
            % (tool, uipathfn(fcd.path()))
        )
        return False, 1, None
    localpath = _workingpath(repo, fcd)
    args = _toolstr(repo.ui, tool, b"args")

    files = [
        (b"base", fca.path(), fca.decodeddata()),
        (b"other", fco.path(), fco.decodeddata()),
    ]
    outpath = b""
    if b"$output" in args:
        # read input from backup, write to original
        outpath = localpath
        localoutputpath = backup.path()
        # Remove the .orig to make syntax-highlighting more likely.
        if localoutputpath.endswith(b'.orig'):
            localoutputpath, ext = os.path.splitext(localoutputpath)
        files.append((b"local", localoutputpath, backup.data()))

    with _maketempfiles(files) as temppaths:
        basepath, otherpath = temppaths[:2]
        if len(temppaths) == 3:
            localpath = temppaths[2]

        def format_label(input):
            if input.label_detail:
                return b'%s: %s' % (input.label, input.label_detail)
            else:
                return input.label

        env = {
            b'HG_FILE': fcd.path(),
            b'HG_MY_NODE': short(mynode),
            b'HG_OTHER_NODE': short(fco.changectx().node()),
            b'HG_BASE_NODE': short(fca.changectx().node()),
            b'HG_MY_ISLINK': b'l' in fcd.flags(),
            b'HG_OTHER_ISLINK': b'l' in fco.flags(),
            b'HG_BASE_ISLINK': b'l' in fca.flags(),
            b'HG_MY_LABEL': format_label(local),
            b'HG_OTHER_LABEL': format_label(other),
            b'HG_BASE_LABEL': format_label(base),
        }
        ui = repo.ui

        replace = {
            b'local': localpath,
            b'base': basepath,
            b'other': otherpath,
            b'output': outpath,
            b'labellocal': format_label(local),
            b'labelother': format_label(other),
            b'labelbase': format_label(base),
        }
        args = util.interpolate(
            br'\$',
            replace,
            args,
            lambda s: procutil.shellquote(util.localpath(s)),
        )
        if _toolbool(ui, tool, b"gui"):
            repo.ui.status(
                _(b'running merge tool %s for file %s\n')
                % (tool, uipathfn(fcd.path()))
            )
        if scriptfn is None:
            cmd = toolpath + b' ' + args
            repo.ui.debug(b'launching merge tool: %s\n' % cmd)
            _describemerge(ui, repo, mynode, fcd, fca, fco, env, toolpath, args)
            r = ui.system(
                cmd, cwd=repo.root, environ=env, blockedtag=b'mergetool'
            )
        else:
            repo.ui.debug(
                b'launching python merge script: %s:%s\n' % (toolpath, scriptfn)
            )
            r = 0
            try:
                # avoid cycle cmdutil->merge->filemerge->extensions->cmdutil
                from . import extensions

                mod_name = 'hgmerge.%s' % pycompat.sysstr(tool)
                mod = extensions.loadpath(toolpath, mod_name)
            except Exception:
                raise error.Abort(
                    _(b"loading python merge script failed: %s") % toolpath
                )
            mergefn = getattr(mod, pycompat.sysstr(scriptfn), None)
            if mergefn is None:
                raise error.Abort(
                    _(b"%s does not have function: %s") % (toolpath, scriptfn)
                )
            argslist = procutil.shellsplit(args)
            # avoid cycle cmdutil->merge->filemerge->hook->extensions->cmdutil
            from . import hook

            ret, raised = hook.pythonhook(
                ui, repo, b"merge", toolpath, mergefn, {b'args': argslist}, True
            )
            if raised:
                r = 1
        repo.ui.debug(b'merge tool returned: %d\n' % r)
        return True, r, False


def _populate_label_detail(input, template):
    """Applies the given template to the ctx and stores it in the input."""
    ctx = input.fctx.changectx()
    if ctx.node() is None:
        ctx = ctx.p1()

    props = {b'ctx': ctx}
    templateresult = template.renderdefault(props)
    input.label_detail = stringutil.firstline(templateresult)  # avoid '\n'


def _populate_label_details(repo, inputs, tool=None):
    """Populates the label details using the conflict marker template."""
    ui = repo.ui
    template = ui.config(b'command-templates', b'mergemarker')
    if tool is not None:
        template = _toolstr(ui, tool, b'mergemarkertemplate', template)
    template = templater.unquotestring(template)
    tres = formatter.templateresources(ui, repo)
    tmpl = formatter.maketemplater(
        ui, template, defaults=templatekw.keywords, resources=tres
    )

    for input in inputs:
        _populate_label_detail(input, tmpl)


def partextras(labels):
    """Return a dictionary of extra labels for use in prompts to the user

    Intended use is in strings of the form "(l)ocal%(l)s".
    """
    if labels is None:
        return {
            b"l": b"",
            b"o": b"",
        }

    return {
        b"l": b" [%s]" % labels[0],
        b"o": b" [%s]" % labels[1],
    }


def _makebackup(repo, ui, fcd):
    """Makes and returns a filectx-like object for ``fcd``'s backup file.

    In addition to preserving the user's pre-existing modifications to `fcd`
    (if any), the backup is used to undo certain premerges, confirm whether a
    merge changed anything, and determine what line endings the new file should
    have.

    Backups only need to be written once since their content doesn't change
    afterwards.
    """
    if fcd.isabsent():
        return None
    # TODO: Break this import cycle somehow. (filectx -> ctx -> fileset ->
    # merge -> filemerge). (I suspect the fileset import is the weakest link)
    from . import context

    if isinstance(fcd, context.overlayworkingfilectx):
        # If we're merging in-memory, we're free to put the backup anywhere.
        fd, backup = pycompat.mkstemp(b'hg-merge-backup')
        with os.fdopen(fd, 'wb') as f:
            f.write(fcd.data())
    else:
        backup = scmutil.backuppath(ui, repo, fcd.path())
        a = _workingpath(repo, fcd)
        util.copyfile(a, backup)

    return context.arbitraryfilectx(backup, repo=repo)


@contextlib.contextmanager
def _maketempfiles(files):
    """Creates a temporary file for each (prefix, path, data) tuple in `files`,
    so an external merge tool may use them.
    """
    tmproot = pycompat.mkdtemp(prefix=b'hgmerge-')

    def maketempfrompath(prefix, path, data):
        fullbase, ext = os.path.splitext(path)
        pre = b"%s~%s" % (os.path.basename(fullbase), prefix)
        name = os.path.join(tmproot, pre)
        if ext:
            name += ext
        util.writefile(name, data)
        return name

    temp_files = []
    for prefix, path, data in files:
        temp_files.append(maketempfrompath(prefix, path, data))
    try:
        yield temp_files
    finally:
        shutil.rmtree(tmproot)


def filemerge(repo, wctx, mynode, orig, fcd, fco, fca, labels=None):
    """perform a 3-way merge in the working directory

    mynode = parent node before merge
    orig = original local filename before merge
    fco = other file context
    fca = ancestor file context
    fcd = local file context for current/destination file

    Returns whether the merge is complete, the return value of the merge, and
    a boolean indicating whether the file was deleted from disk."""
    ui = repo.ui
    fd = fcd.path()
    uipathfn = scmutil.getuipathfn(repo)
    fduipath = uipathfn(fd)
    binary = fcd.isbinary() or fco.isbinary() or fca.isbinary()
    symlink = b'l' in fcd.flags() + fco.flags()
    changedelete = fcd.isabsent() or fco.isabsent()
    tool, toolpath = _picktool(repo, ui, fd, binary, symlink, changedelete)
    scriptfn = None
    if tool in internals and tool.startswith(b'internal:'):
        # normalize to new-style names (':merge' etc)
        tool = tool[len(b'internal') :]
    if toolpath and toolpath.startswith(b'python:'):
        invalidsyntax = False
        if toolpath.count(b':') >= 2:
            script, scriptfn = toolpath[7:].rsplit(b':', 1)
            if not scriptfn:
                invalidsyntax = True
            # missing :callable can lead to spliting on windows drive letter
            if b'\\' in scriptfn or b'/' in scriptfn:
                invalidsyntax = True
        else:
            invalidsyntax = True
        if invalidsyntax:
            raise error.Abort(_(b"invalid 'python:' syntax: %s") % toolpath)
        toolpath = script
    ui.debug(
        b"picked tool '%s' for %s (binary %s symlink %s changedelete %s)\n"
        % (
            tool,
            fduipath,
            pycompat.bytestr(binary),
            pycompat.bytestr(symlink),
            pycompat.bytestr(changedelete),
        )
    )

    if tool in internals:
        func = internals[tool]
        mergetype = func.mergetype
        onfailure = func.onfailure
        precheck = func.precheck
        isexternal = False
    else:
        if wctx.isinmemory():
            func = _xmergeimm
        else:
            func = _xmerge
        mergetype = fullmerge
        onfailure = _(b"merging %s failed!\n")
        precheck = None
        isexternal = True

    toolconf = tool, toolpath, binary, symlink, scriptfn

    if not labels:
        labels = [b'local', b'other']
    if len(labels) < 3:
        labels.append(b'base')
    local = simplemerge.MergeInput(fcd, labels[0])
    other = simplemerge.MergeInput(fco, labels[1])
    base = simplemerge.MergeInput(fca, labels[2])
    if mergetype == nomerge:
        return func(
            repo,
            mynode,
            local,
            other,
            base,
            toolconf,
        )

    if orig != fco.path():
        ui.status(
            _(b"merging %s and %s to %s\n")
            % (uipathfn(orig), uipathfn(fco.path()), fduipath)
        )
    else:
        ui.status(_(b"merging %s\n") % fduipath)

    ui.debug(b"my %s other %s ancestor %s\n" % (fcd, fco, fca))

    if precheck and not precheck(repo, mynode, fcd, fco, fca, toolconf):
        if onfailure:
            if wctx.isinmemory():
                raise error.InMemoryMergeConflictsError(
                    b'in-memory merge does not support merge conflicts'
                )
            ui.warn(onfailure % fduipath)
        return 1, False

    backup = _makebackup(repo, ui, fcd)
    r = 1
    try:
        internalmarkerstyle = ui.config(b'ui', b'mergemarkers')
        if isexternal:
            markerstyle = _toolstr(ui, tool, b'mergemarkers')
        else:
            markerstyle = internalmarkerstyle

        if mergetype == fullmerge:
            _run_partial_resolution_tools(repo, local, other, base)
            # conflict markers generated by premerge will use 'detailed'
            # settings if either ui.mergemarkers or the tool's mergemarkers
            # setting is 'detailed'. This way tools can have basic labels in
            # space-constrained areas of the UI, but still get full information
            # in conflict markers if premerge is 'keep' or 'keep-merge3'.
            labeltool = None
            if markerstyle != b'basic':
                # respect 'tool's mergemarkertemplate (which defaults to
                # command-templates.mergemarker)
                labeltool = tool
            if internalmarkerstyle != b'basic' or markerstyle != b'basic':
                _populate_label_details(
                    repo, [local, other, base], tool=labeltool
                )

            r = _premerge(
                repo,
                local,
                other,
                base,
                toolconf,
            )
            # we're done if premerge was successful (r is 0)
            if not r:
                return r, False

            # Reset to basic labels
            local.label_detail = None
            other.label_detail = None
            base.label_detail = None

        if markerstyle != b'basic':
            _populate_label_details(repo, [local, other, base], tool=tool)

        needcheck, r, deleted = func(
            repo,
            mynode,
            local,
            other,
            base,
            toolconf,
            backup,
        )

        if needcheck:
            r = _check(repo, r, ui, tool, fcd, backup)

        if r:
            if onfailure:
                if wctx.isinmemory():
                    raise error.InMemoryMergeConflictsError(
                        b'in-memory merge '
                        b'does not support '
                        b'merge conflicts'
                    )
                ui.warn(onfailure % fduipath)
            _onfilemergefailure(ui)

        return r, deleted
    finally:
        if not r and backup is not None:
            backup.remove()


def _run_partial_resolution_tools(repo, local, other, base):
    """Runs partial-resolution tools on the three inputs and updates them."""
    ui = repo.ui
    if ui.configbool(b'merge', b'disable-partial-tools'):
        return
    # Tuples of (order, name, executable path, args)
    tools = []
    seen = set()
    section = b"partial-merge-tools"
    for k, v in ui.configitems(section):
        name = k.split(b'.')[0]
        if name in seen:
            continue
        patterns = ui.configlist(section, b'%s.patterns' % name, [])
        is_match = True
        if patterns:
            m = match.match(
                repo.root, b'', patterns, ctx=local.fctx.changectx()
            )
            is_match = m(local.fctx.path())
        if is_match:
            if ui.configbool(section, b'%s.disable' % name):
                continue
            order = ui.configint(section, b'%s.order' % name, 0)
            executable = ui.config(section, b'%s.executable' % name, name)
            args = ui.config(section, b'%s.args' % name)
            tools.append((order, name, executable, args))

    if not tools:
        return
    # Sort in configured order (first in tuple)
    tools.sort()

    files = [
        (b"local", local.fctx.path(), local.text()),
        (b"base", base.fctx.path(), base.text()),
        (b"other", other.fctx.path(), other.text()),
    ]

    with _maketempfiles(files) as temppaths:
        localpath, basepath, otherpath = temppaths

        for order, name, executable, args in tools:
            cmd = procutil.shellquote(executable)
            replace = {
                b'local': localpath,
                b'base': basepath,
                b'other': otherpath,
            }
            args = util.interpolate(
                br'\$',
                replace,
                args,
                lambda s: procutil.shellquote(util.localpath(s)),
            )

            cmd = b'%s %s' % (cmd, args)
            r = ui.system(cmd, cwd=repo.root, blockedtag=b'partial-mergetool')
            if r:
                raise error.StateError(
                    b'partial merge tool %s exited with code %d' % (name, r)
                )
            local_text = util.readfile(localpath)
            other_text = util.readfile(otherpath)
            if local_text == other_text:
                # No need to run other tools if all conflicts have been resolved
                break

        local.set_text(local_text)
        base.set_text(util.readfile(basepath))
        other.set_text(other_text)


def _haltmerge():
    msg = _(b'merge halted after failed merge (see hg resolve)')
    raise error.InterventionRequired(msg)


def _onfilemergefailure(ui):
    action = ui.config(b'merge', b'on-failure')
    if action == b'prompt':
        msg = _(b'continue merge operation (yn)?$$ &Yes $$ &No')
        if ui.promptchoice(msg, 0) == 1:
            _haltmerge()
    if action == b'halt':
        _haltmerge()
    # default action is 'continue', in which case we neither prompt nor halt


def hasconflictmarkers(data):
    # Detect lines starting with a string of 7 identical characters from the
    # subset Mercurial uses for conflict markers, followed by either the end of
    # line or a space and some text. Note that using [<>=+|-]{7} would detect
    # `<><><><><` as a conflict marker, which we don't want.
    return bool(
        re.search(
            br"^([<>=+|-])\1{6}( .*)$",
            data,
            re.MULTILINE,
        )
    )


def _check(repo, r, ui, tool, fcd, backup):
    fd = fcd.path()
    uipathfn = scmutil.getuipathfn(repo)

    if not r and (
        _toolbool(ui, tool, b"checkconflicts")
        or b'conflicts' in _toollist(ui, tool, b"check")
    ):
        if hasconflictmarkers(fcd.data()):
            r = 1

    checked = False
    if b'prompt' in _toollist(ui, tool, b"check"):
        checked = True
        if ui.promptchoice(
            _(b"was merge of '%s' successful (yn)?$$ &Yes $$ &No")
            % uipathfn(fd),
            1,
        ):
            r = 1

    if (
        not r
        and not checked
        and (
            _toolbool(ui, tool, b"checkchanged")
            or b'changed' in _toollist(ui, tool, b"check")
        )
    ):
        if backup is not None and not fcd.cmp(backup):
            if ui.promptchoice(
                _(
                    b" output file %s appears unchanged\n"
                    b"was merge successful (yn)?"
                    b"$$ &Yes $$ &No"
                )
                % uipathfn(fd),
                1,
            ):
                r = 1

    if backup is not None and _toolbool(ui, tool, b"fixeol"):
        _matcheol(_workingpath(repo, fcd), backup)

    return r


def _workingpath(repo, ctx):
    return repo.wjoin(ctx.path())


def loadinternalmerge(ui, extname, registrarobj):
    """Load internal merge tool from specified registrarobj"""
    for name, func in registrarobj._table.items():
        fullname = b':' + name
        internals[fullname] = func
        internals[b'internal:' + name] = func
        internalsdoc[fullname] = func

        capabilities = sorted([k for k, v in func.capabilities.items() if v])
        if capabilities:
            capdesc = b"    (actual capabilities: %s)" % b', '.join(
                capabilities
            )
            func.__doc__ = func.__doc__ + pycompat.sysstr(b"\n\n%s" % capdesc)

    # to put i18n comments into hg.pot for automatically generated texts

    # i18n: "binary" and "symlink" are keywords
    # i18n: this text is added automatically
    _(b"    (actual capabilities: binary, symlink)")
    # i18n: "binary" is keyword
    # i18n: this text is added automatically
    _(b"    (actual capabilities: binary)")
    # i18n: "symlink" is keyword
    # i18n: this text is added automatically
    _(b"    (actual capabilities: symlink)")


# load built-in merge tools explicitly to setup internalsdoc
loadinternalmerge(None, None, internaltool)

# tell hggettext to extract docstrings from these functions:
i18nfunctions = internals.values()
