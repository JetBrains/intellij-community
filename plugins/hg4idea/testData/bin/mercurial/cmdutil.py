# cmdutil.py - help for command processing in mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import hex, nullid, nullrev, short
from i18n import _
import os, sys, errno, re, tempfile
import util, scmutil, templater, patch, error, templatekw, revlog, copies
import match as matchmod
import subrepo, context, repair, graphmod, revset, phases, obsolete
import changelog
import bookmarks
import lock as lockmod

def parsealiases(cmd):
    return cmd.lstrip("^").split("|")

def findpossible(cmd, table, strict=False):
    """
    Return cmd -> (aliases, command table entry)
    for each matching command.
    Return debug commands (or their aliases) only if no normal command matches.
    """
    choice = {}
    debugchoice = {}

    if cmd in table:
        # short-circuit exact matches, "log" alias beats "^log|history"
        keys = [cmd]
    else:
        keys = table.keys()

    for e in keys:
        aliases = parsealiases(e)
        found = None
        if cmd in aliases:
            found = cmd
        elif not strict:
            for a in aliases:
                if a.startswith(cmd):
                    found = a
                    break
        if found is not None:
            if aliases[0].startswith("debug") or found.startswith("debug"):
                debugchoice[found] = (aliases, table[e])
            else:
                choice[found] = (aliases, table[e])

    if not choice and debugchoice:
        choice = debugchoice

    return choice

def findcmd(cmd, table, strict=True):
    """Return (aliases, command table entry) for command string."""
    choice = findpossible(cmd, table, strict)

    if cmd in choice:
        return choice[cmd]

    if len(choice) > 1:
        clist = choice.keys()
        clist.sort()
        raise error.AmbiguousCommand(cmd, clist)

    if choice:
        return choice.values()[0]

    raise error.UnknownCommand(cmd)

def findrepo(p):
    while not os.path.isdir(os.path.join(p, ".hg")):
        oldp, p = p, os.path.dirname(p)
        if p == oldp:
            return None

    return p

def bailifchanged(repo):
    if repo.dirstate.p2() != nullid:
        raise util.Abort(_('outstanding uncommitted merge'))
    modified, added, removed, deleted = repo.status()[:4]
    if modified or added or removed or deleted:
        raise util.Abort(_("outstanding uncommitted changes"))
    ctx = repo[None]
    for s in sorted(ctx.substate):
        if ctx.sub(s).dirty():
            raise util.Abort(_("uncommitted changes in subrepo %s") % s)

def logmessage(ui, opts):
    """ get the log message according to -m and -l option """
    message = opts.get('message')
    logfile = opts.get('logfile')

    if message and logfile:
        raise util.Abort(_('options --message and --logfile are mutually '
                           'exclusive'))
    if not message and logfile:
        try:
            if logfile == '-':
                message = ui.fin.read()
            else:
                message = '\n'.join(util.readfile(logfile).splitlines())
        except IOError, inst:
            raise util.Abort(_("can't read commit message '%s': %s") %
                             (logfile, inst.strerror))
    return message

def loglimit(opts):
    """get the log limit according to option -l/--limit"""
    limit = opts.get('limit')
    if limit:
        try:
            limit = int(limit)
        except ValueError:
            raise util.Abort(_('limit must be a positive integer'))
        if limit <= 0:
            raise util.Abort(_('limit must be positive'))
    else:
        limit = None
    return limit

def makefilename(repo, pat, node, desc=None,
                  total=None, seqno=None, revwidth=None, pathname=None):
    node_expander = {
        'H': lambda: hex(node),
        'R': lambda: str(repo.changelog.rev(node)),
        'h': lambda: short(node),
        'm': lambda: re.sub('[^\w]', '_', str(desc))
        }
    expander = {
        '%': lambda: '%',
        'b': lambda: os.path.basename(repo.root),
        }

    try:
        if node:
            expander.update(node_expander)
        if node:
            expander['r'] = (lambda:
                    str(repo.changelog.rev(node)).zfill(revwidth or 0))
        if total is not None:
            expander['N'] = lambda: str(total)
        if seqno is not None:
            expander['n'] = lambda: str(seqno)
        if total is not None and seqno is not None:
            expander['n'] = lambda: str(seqno).zfill(len(str(total)))
        if pathname is not None:
            expander['s'] = lambda: os.path.basename(pathname)
            expander['d'] = lambda: os.path.dirname(pathname) or '.'
            expander['p'] = lambda: pathname

        newname = []
        patlen = len(pat)
        i = 0
        while i < patlen:
            c = pat[i]
            if c == '%':
                i += 1
                c = pat[i]
                c = expander[c]()
            newname.append(c)
            i += 1
        return ''.join(newname)
    except KeyError, inst:
        raise util.Abort(_("invalid format spec '%%%s' in output filename") %
                         inst.args[0])

def makefileobj(repo, pat, node=None, desc=None, total=None,
                seqno=None, revwidth=None, mode='wb', modemap={},
                pathname=None):

    writable = mode not in ('r', 'rb')

    if not pat or pat == '-':
        fp = writable and repo.ui.fout or repo.ui.fin
        if util.safehasattr(fp, 'fileno'):
            return os.fdopen(os.dup(fp.fileno()), mode)
        else:
            # if this fp can't be duped properly, return
            # a dummy object that can be closed
            class wrappedfileobj(object):
                noop = lambda x: None
                def __init__(self, f):
                    self.f = f
                def __getattr__(self, attr):
                    if attr == 'close':
                        return self.noop
                    else:
                        return getattr(self.f, attr)

            return wrappedfileobj(fp)
    if util.safehasattr(pat, 'write') and writable:
        return pat
    if util.safehasattr(pat, 'read') and 'r' in mode:
        return pat
    fn = makefilename(repo, pat, node, desc, total, seqno, revwidth, pathname)
    mode = modemap.get(fn, mode)
    if mode == 'wb':
        modemap[fn] = 'ab'
    return open(fn, mode)

def openrevlog(repo, cmd, file_, opts):
    """opens the changelog, manifest, a filelog or a given revlog"""
    cl = opts['changelog']
    mf = opts['manifest']
    msg = None
    if cl and mf:
        msg = _('cannot specify --changelog and --manifest at the same time')
    elif cl or mf:
        if file_:
            msg = _('cannot specify filename with --changelog or --manifest')
        elif not repo:
            msg = _('cannot specify --changelog or --manifest '
                    'without a repository')
    if msg:
        raise util.Abort(msg)

    r = None
    if repo:
        if cl:
            r = repo.changelog
        elif mf:
            r = repo.manifest
        elif file_:
            filelog = repo.file(file_)
            if len(filelog):
                r = filelog
    if not r:
        if not file_:
            raise error.CommandError(cmd, _('invalid arguments'))
        if not os.path.isfile(file_):
            raise util.Abort(_("revlog '%s' not found") % file_)
        r = revlog.revlog(scmutil.opener(os.getcwd(), audit=False),
                          file_[:-2] + ".i")
    return r

def copy(ui, repo, pats, opts, rename=False):
    # called with the repo lock held
    #
    # hgsep => pathname that uses "/" to separate directories
    # ossep => pathname that uses os.sep to separate directories
    cwd = repo.getcwd()
    targets = {}
    after = opts.get("after")
    dryrun = opts.get("dry_run")
    wctx = repo[None]

    def walkpat(pat):
        srcs = []
        badstates = after and '?' or '?r'
        m = scmutil.match(repo[None], [pat], opts, globbed=True)
        for abs in repo.walk(m):
            state = repo.dirstate[abs]
            rel = m.rel(abs)
            exact = m.exact(abs)
            if state in badstates:
                if exact and state == '?':
                    ui.warn(_('%s: not copying - file is not managed\n') % rel)
                if exact and state == 'r':
                    ui.warn(_('%s: not copying - file has been marked for'
                              ' remove\n') % rel)
                continue
            # abs: hgsep
            # rel: ossep
            srcs.append((abs, rel, exact))
        return srcs

    # abssrc: hgsep
    # relsrc: ossep
    # otarget: ossep
    def copyfile(abssrc, relsrc, otarget, exact):
        abstarget = scmutil.canonpath(repo.root, cwd, otarget)
        if '/' in abstarget:
            # We cannot normalize abstarget itself, this would prevent
            # case only renames, like a => A.
            abspath, absname = abstarget.rsplit('/', 1)
            abstarget = repo.dirstate.normalize(abspath) + '/' + absname
        reltarget = repo.pathto(abstarget, cwd)
        target = repo.wjoin(abstarget)
        src = repo.wjoin(abssrc)
        state = repo.dirstate[abstarget]

        scmutil.checkportable(ui, abstarget)

        # check for collisions
        prevsrc = targets.get(abstarget)
        if prevsrc is not None:
            ui.warn(_('%s: not overwriting - %s collides with %s\n') %
                    (reltarget, repo.pathto(abssrc, cwd),
                     repo.pathto(prevsrc, cwd)))
            return

        # check for overwrites
        exists = os.path.lexists(target)
        samefile = False
        if exists and abssrc != abstarget:
            if (repo.dirstate.normalize(abssrc) ==
                repo.dirstate.normalize(abstarget)):
                if not rename:
                    ui.warn(_("%s: can't copy - same file\n") % reltarget)
                    return
                exists = False
                samefile = True

        if not after and exists or after and state in 'mn':
            if not opts['force']:
                ui.warn(_('%s: not overwriting - file exists\n') %
                        reltarget)
                return

        if after:
            if not exists:
                if rename:
                    ui.warn(_('%s: not recording move - %s does not exist\n') %
                            (relsrc, reltarget))
                else:
                    ui.warn(_('%s: not recording copy - %s does not exist\n') %
                            (relsrc, reltarget))
                return
        elif not dryrun:
            try:
                if exists:
                    os.unlink(target)
                targetdir = os.path.dirname(target) or '.'
                if not os.path.isdir(targetdir):
                    os.makedirs(targetdir)
                if samefile:
                    tmp = target + "~hgrename"
                    os.rename(src, tmp)
                    os.rename(tmp, target)
                else:
                    util.copyfile(src, target)
                srcexists = True
            except IOError, inst:
                if inst.errno == errno.ENOENT:
                    ui.warn(_('%s: deleted in working copy\n') % relsrc)
                    srcexists = False
                else:
                    ui.warn(_('%s: cannot copy - %s\n') %
                            (relsrc, inst.strerror))
                    return True # report a failure

        if ui.verbose or not exact:
            if rename:
                ui.status(_('moving %s to %s\n') % (relsrc, reltarget))
            else:
                ui.status(_('copying %s to %s\n') % (relsrc, reltarget))

        targets[abstarget] = abssrc

        # fix up dirstate
        scmutil.dirstatecopy(ui, repo, wctx, abssrc, abstarget,
                             dryrun=dryrun, cwd=cwd)
        if rename and not dryrun:
            if not after and srcexists and not samefile:
                util.unlinkpath(repo.wjoin(abssrc))
            wctx.forget([abssrc])

    # pat: ossep
    # dest ossep
    # srcs: list of (hgsep, hgsep, ossep, bool)
    # return: function that takes hgsep and returns ossep
    def targetpathfn(pat, dest, srcs):
        if os.path.isdir(pat):
            abspfx = scmutil.canonpath(repo.root, cwd, pat)
            abspfx = util.localpath(abspfx)
            if destdirexists:
                striplen = len(os.path.split(abspfx)[0])
            else:
                striplen = len(abspfx)
            if striplen:
                striplen += len(os.sep)
            res = lambda p: os.path.join(dest, util.localpath(p)[striplen:])
        elif destdirexists:
            res = lambda p: os.path.join(dest,
                                         os.path.basename(util.localpath(p)))
        else:
            res = lambda p: dest
        return res

    # pat: ossep
    # dest ossep
    # srcs: list of (hgsep, hgsep, ossep, bool)
    # return: function that takes hgsep and returns ossep
    def targetpathafterfn(pat, dest, srcs):
        if matchmod.patkind(pat):
            # a mercurial pattern
            res = lambda p: os.path.join(dest,
                                         os.path.basename(util.localpath(p)))
        else:
            abspfx = scmutil.canonpath(repo.root, cwd, pat)
            if len(abspfx) < len(srcs[0][0]):
                # A directory. Either the target path contains the last
                # component of the source path or it does not.
                def evalpath(striplen):
                    score = 0
                    for s in srcs:
                        t = os.path.join(dest, util.localpath(s[0])[striplen:])
                        if os.path.lexists(t):
                            score += 1
                    return score

                abspfx = util.localpath(abspfx)
                striplen = len(abspfx)
                if striplen:
                    striplen += len(os.sep)
                if os.path.isdir(os.path.join(dest, os.path.split(abspfx)[1])):
                    score = evalpath(striplen)
                    striplen1 = len(os.path.split(abspfx)[0])
                    if striplen1:
                        striplen1 += len(os.sep)
                    if evalpath(striplen1) > score:
                        striplen = striplen1
                res = lambda p: os.path.join(dest,
                                             util.localpath(p)[striplen:])
            else:
                # a file
                if destdirexists:
                    res = lambda p: os.path.join(dest,
                                        os.path.basename(util.localpath(p)))
                else:
                    res = lambda p: dest
        return res


    pats = scmutil.expandpats(pats)
    if not pats:
        raise util.Abort(_('no source or destination specified'))
    if len(pats) == 1:
        raise util.Abort(_('no destination specified'))
    dest = pats.pop()
    destdirexists = os.path.isdir(dest) and not os.path.islink(dest)
    if not destdirexists:
        if len(pats) > 1 or matchmod.patkind(pats[0]):
            raise util.Abort(_('with multiple sources, destination must be an '
                               'existing directory'))
        if util.endswithsep(dest):
            raise util.Abort(_('destination %s is not a directory') % dest)

    tfn = targetpathfn
    if after:
        tfn = targetpathafterfn
    copylist = []
    for pat in pats:
        srcs = walkpat(pat)
        if not srcs:
            continue
        copylist.append((tfn(pat, dest, srcs), srcs))
    if not copylist:
        raise util.Abort(_('no files to copy'))

    errors = 0
    for targetpath, srcs in copylist:
        for abssrc, relsrc, exact in srcs:
            if copyfile(abssrc, relsrc, targetpath(abssrc), exact):
                errors += 1

    if errors:
        ui.warn(_('(consider using --after)\n'))

    return errors != 0

def service(opts, parentfn=None, initfn=None, runfn=None, logfile=None,
    runargs=None, appendpid=False):
    '''Run a command as a service.'''

    if opts['daemon'] and not opts['daemon_pipefds']:
        # Signal child process startup with file removal
        lockfd, lockpath = tempfile.mkstemp(prefix='hg-service-')
        os.close(lockfd)
        try:
            if not runargs:
                runargs = util.hgcmd() + sys.argv[1:]
            runargs.append('--daemon-pipefds=%s' % lockpath)
            # Don't pass --cwd to the child process, because we've already
            # changed directory.
            for i in xrange(1, len(runargs)):
                if runargs[i].startswith('--cwd='):
                    del runargs[i]
                    break
                elif runargs[i].startswith('--cwd'):
                    del runargs[i:i + 2]
                    break
            def condfn():
                return not os.path.exists(lockpath)
            pid = util.rundetached(runargs, condfn)
            if pid < 0:
                raise util.Abort(_('child process failed to start'))
        finally:
            try:
                os.unlink(lockpath)
            except OSError, e:
                if e.errno != errno.ENOENT:
                    raise
        if parentfn:
            return parentfn(pid)
        else:
            return

    if initfn:
        initfn()

    if opts['pid_file']:
        mode = appendpid and 'a' or 'w'
        fp = open(opts['pid_file'], mode)
        fp.write(str(os.getpid()) + '\n')
        fp.close()

    if opts['daemon_pipefds']:
        lockpath = opts['daemon_pipefds']
        try:
            os.setsid()
        except AttributeError:
            pass
        os.unlink(lockpath)
        util.hidewindow()
        sys.stdout.flush()
        sys.stderr.flush()

        nullfd = os.open(os.devnull, os.O_RDWR)
        logfilefd = nullfd
        if logfile:
            logfilefd = os.open(logfile, os.O_RDWR | os.O_CREAT | os.O_APPEND)
        os.dup2(nullfd, 0)
        os.dup2(logfilefd, 1)
        os.dup2(logfilefd, 2)
        if nullfd not in (0, 1, 2):
            os.close(nullfd)
        if logfile and logfilefd not in (0, 1, 2):
            os.close(logfilefd)

    if runfn:
        return runfn()

def export(repo, revs, template='hg-%h.patch', fp=None, switch_parent=False,
           opts=None):
    '''export changesets as hg patches.'''

    total = len(revs)
    revwidth = max([len(str(rev)) for rev in revs])
    filemode = {}

    def single(rev, seqno, fp):
        ctx = repo[rev]
        node = ctx.node()
        parents = [p.node() for p in ctx.parents() if p]
        branch = ctx.branch()
        if switch_parent:
            parents.reverse()
        prev = (parents and parents[0]) or nullid

        shouldclose = False
        if not fp and len(template) > 0:
            desc_lines = ctx.description().rstrip().split('\n')
            desc = desc_lines[0]    #Commit always has a first line.
            fp = makefileobj(repo, template, node, desc=desc, total=total,
                             seqno=seqno, revwidth=revwidth, mode='wb',
                             modemap=filemode)
            if fp != template:
                shouldclose = True
        if fp and fp != sys.stdout and util.safehasattr(fp, 'name'):
            repo.ui.note("%s\n" % fp.name)

        if not fp:
            write = repo.ui.write
        else:
            def write(s, **kw):
                fp.write(s)


        write("# HG changeset patch\n")
        write("# User %s\n" % ctx.user())
        write("# Date %d %d\n" % ctx.date())
        write("#      %s\n" % util.datestr(ctx.date()))
        if branch and branch != 'default':
            write("# Branch %s\n" % branch)
        write("# Node ID %s\n" % hex(node))
        write("# Parent  %s\n" % hex(prev))
        if len(parents) > 1:
            write("# Parent  %s\n" % hex(parents[1]))
        write(ctx.description().rstrip())
        write("\n\n")

        for chunk, label in patch.diffui(repo, prev, node, opts=opts):
            write(chunk, label=label)

        if shouldclose:
            fp.close()

    for seqno, rev in enumerate(revs):
        single(rev, seqno + 1, fp)

def diffordiffstat(ui, repo, diffopts, node1, node2, match,
                   changes=None, stat=False, fp=None, prefix='',
                   listsubrepos=False):
    '''show diff or diffstat.'''
    if fp is None:
        write = ui.write
    else:
        def write(s, **kw):
            fp.write(s)

    if stat:
        diffopts = diffopts.copy(context=0)
        width = 80
        if not ui.plain():
            width = ui.termwidth()
        chunks = patch.diff(repo, node1, node2, match, changes, diffopts,
                            prefix=prefix)
        for chunk, label in patch.diffstatui(util.iterlines(chunks),
                                             width=width,
                                             git=diffopts.git):
            write(chunk, label=label)
    else:
        for chunk, label in patch.diffui(repo, node1, node2, match,
                                         changes, diffopts, prefix=prefix):
            write(chunk, label=label)

    if listsubrepos:
        ctx1 = repo[node1]
        ctx2 = repo[node2]
        for subpath, sub in subrepo.itersubrepos(ctx1, ctx2):
            tempnode2 = node2
            try:
                if node2 is not None:
                    tempnode2 = ctx2.substate[subpath][1]
            except KeyError:
                # A subrepo that existed in node1 was deleted between node1 and
                # node2 (inclusive). Thus, ctx2's substate won't contain that
                # subpath. The best we can do is to ignore it.
                tempnode2 = None
            submatch = matchmod.narrowmatcher(subpath, match)
            sub.diff(ui, diffopts, tempnode2, submatch, changes=changes,
                     stat=stat, fp=fp, prefix=prefix)

class changeset_printer(object):
    '''show changeset information when templating not requested.'''

    def __init__(self, ui, repo, patch, diffopts, buffered):
        self.ui = ui
        self.repo = repo
        self.buffered = buffered
        self.patch = patch
        self.diffopts = diffopts
        self.header = {}
        self.hunk = {}
        self.lastheader = None
        self.footer = None

    def flush(self, rev):
        if rev in self.header:
            h = self.header[rev]
            if h != self.lastheader:
                self.lastheader = h
                self.ui.write(h)
            del self.header[rev]
        if rev in self.hunk:
            self.ui.write(self.hunk[rev])
            del self.hunk[rev]
            return 1
        return 0

    def close(self):
        if self.footer:
            self.ui.write(self.footer)

    def show(self, ctx, copies=None, matchfn=None, **props):
        if self.buffered:
            self.ui.pushbuffer()
            self._show(ctx, copies, matchfn, props)
            self.hunk[ctx.rev()] = self.ui.popbuffer(labeled=True)
        else:
            self._show(ctx, copies, matchfn, props)

    def _show(self, ctx, copies, matchfn, props):
        '''show a single changeset or file revision'''
        changenode = ctx.node()
        rev = ctx.rev()

        if self.ui.quiet:
            self.ui.write("%d:%s\n" % (rev, short(changenode)),
                          label='log.node')
            return

        log = self.repo.changelog
        date = util.datestr(ctx.date())

        hexfunc = self.ui.debugflag and hex or short

        parents = [(p, hexfunc(log.node(p)))
                   for p in self._meaningful_parentrevs(log, rev)]

        # i18n: column positioning for "hg log"
        self.ui.write(_("changeset:   %d:%s\n") % (rev, hexfunc(changenode)),
                      label='log.changeset changeset.%s' % ctx.phasestr())

        branch = ctx.branch()
        # don't show the default branch name
        if branch != 'default':
            # i18n: column positioning for "hg log"
            self.ui.write(_("branch:      %s\n") % branch,
                          label='log.branch')
        for bookmark in self.repo.nodebookmarks(changenode):
            # i18n: column positioning for "hg log"
            self.ui.write(_("bookmark:    %s\n") % bookmark,
                    label='log.bookmark')
        for tag in self.repo.nodetags(changenode):
            # i18n: column positioning for "hg log"
            self.ui.write(_("tag:         %s\n") % tag,
                          label='log.tag')
        if self.ui.debugflag and ctx.phase():
            # i18n: column positioning for "hg log"
            self.ui.write(_("phase:       %s\n") % _(ctx.phasestr()),
                          label='log.phase')
        for parent in parents:
            # i18n: column positioning for "hg log"
            self.ui.write(_("parent:      %d:%s\n") % parent,
                          label='log.parent changeset.%s' % ctx.phasestr())

        if self.ui.debugflag:
            mnode = ctx.manifestnode()
            # i18n: column positioning for "hg log"
            self.ui.write(_("manifest:    %d:%s\n") %
                          (self.repo.manifest.rev(mnode), hex(mnode)),
                          label='ui.debug log.manifest')
        # i18n: column positioning for "hg log"
        self.ui.write(_("user:        %s\n") % ctx.user(),
                      label='log.user')
        # i18n: column positioning for "hg log"
        self.ui.write(_("date:        %s\n") % date,
                      label='log.date')

        if self.ui.debugflag:
            files = self.repo.status(log.parents(changenode)[0], changenode)[:3]
            for key, value in zip([# i18n: column positioning for "hg log"
                                   _("files:"),
                                   # i18n: column positioning for "hg log"
                                   _("files+:"),
                                   # i18n: column positioning for "hg log"
                                   _("files-:")], files):
                if value:
                    self.ui.write("%-12s %s\n" % (key, " ".join(value)),
                                  label='ui.debug log.files')
        elif ctx.files() and self.ui.verbose:
            # i18n: column positioning for "hg log"
            self.ui.write(_("files:       %s\n") % " ".join(ctx.files()),
                          label='ui.note log.files')
        if copies and self.ui.verbose:
            copies = ['%s (%s)' % c for c in copies]
            # i18n: column positioning for "hg log"
            self.ui.write(_("copies:      %s\n") % ' '.join(copies),
                          label='ui.note log.copies')

        extra = ctx.extra()
        if extra and self.ui.debugflag:
            for key, value in sorted(extra.items()):
                # i18n: column positioning for "hg log"
                self.ui.write(_("extra:       %s=%s\n")
                              % (key, value.encode('string_escape')),
                              label='ui.debug log.extra')

        description = ctx.description().strip()
        if description:
            if self.ui.verbose:
                self.ui.write(_("description:\n"),
                              label='ui.note log.description')
                self.ui.write(description,
                              label='ui.note log.description')
                self.ui.write("\n\n")
            else:
                # i18n: column positioning for "hg log"
                self.ui.write(_("summary:     %s\n") %
                              description.splitlines()[0],
                              label='log.summary')
        self.ui.write("\n")

        self.showpatch(changenode, matchfn)

    def showpatch(self, node, matchfn):
        if not matchfn:
            matchfn = self.patch
        if matchfn:
            stat = self.diffopts.get('stat')
            diff = self.diffopts.get('patch')
            diffopts = patch.diffopts(self.ui, self.diffopts)
            prev = self.repo.changelog.parents(node)[0]
            if stat:
                diffordiffstat(self.ui, self.repo, diffopts, prev, node,
                               match=matchfn, stat=True)
            if diff:
                if stat:
                    self.ui.write("\n")
                diffordiffstat(self.ui, self.repo, diffopts, prev, node,
                               match=matchfn, stat=False)
            self.ui.write("\n")

    def _meaningful_parentrevs(self, log, rev):
        """Return list of meaningful (or all if debug) parentrevs for rev.

        For merges (two non-nullrev revisions) both parents are meaningful.
        Otherwise the first parent revision is considered meaningful if it
        is not the preceding revision.
        """
        parents = log.parentrevs(rev)
        if not self.ui.debugflag and parents[1] == nullrev:
            if parents[0] >= rev - 1:
                parents = []
            else:
                parents = [parents[0]]
        return parents


class changeset_templater(changeset_printer):
    '''format changeset information.'''

    def __init__(self, ui, repo, patch, diffopts, mapfile, buffered):
        changeset_printer.__init__(self, ui, repo, patch, diffopts, buffered)
        formatnode = ui.debugflag and (lambda x: x) or (lambda x: x[:12])
        defaulttempl = {
            'parent': '{rev}:{node|formatnode} ',
            'manifest': '{rev}:{node|formatnode}',
            'file_copy': '{name} ({source})',
            'extra': '{key}={value|stringescape}'
            }
        # filecopy is preserved for compatibility reasons
        defaulttempl['filecopy'] = defaulttempl['file_copy']
        self.t = templater.templater(mapfile, {'formatnode': formatnode},
                                     cache=defaulttempl)
        self.cache = {}

    def use_template(self, t):
        '''set template string to use'''
        self.t.cache['changeset'] = t

    def _meaningful_parentrevs(self, ctx):
        """Return list of meaningful (or all if debug) parentrevs for rev.
        """
        parents = ctx.parents()
        if len(parents) > 1:
            return parents
        if self.ui.debugflag:
            return [parents[0], self.repo['null']]
        if parents[0].rev() >= ctx.rev() - 1:
            return []
        return parents

    def _show(self, ctx, copies, matchfn, props):
        '''show a single changeset or file revision'''

        showlist = templatekw.showlist

        # showparents() behaviour depends on ui trace level which
        # causes unexpected behaviours at templating level and makes
        # it harder to extract it in a standalone function. Its
        # behaviour cannot be changed so leave it here for now.
        def showparents(**args):
            ctx = args['ctx']
            parents = [[('rev', p.rev()), ('node', p.hex())]
                       for p in self._meaningful_parentrevs(ctx)]
            return showlist('parent', parents, **args)

        props = props.copy()
        props.update(templatekw.keywords)
        props['parents'] = showparents
        props['templ'] = self.t
        props['ctx'] = ctx
        props['repo'] = self.repo
        props['revcache'] = {'copies': copies}
        props['cache'] = self.cache

        # find correct templates for current mode

        tmplmodes = [
            (True, None),
            (self.ui.verbose, 'verbose'),
            (self.ui.quiet, 'quiet'),
            (self.ui.debugflag, 'debug'),
        ]

        types = {'header': '', 'footer':'', 'changeset': 'changeset'}
        for mode, postfix  in tmplmodes:
            for type in types:
                cur = postfix and ('%s_%s' % (type, postfix)) or type
                if mode and cur in self.t:
                    types[type] = cur

        try:

            # write header
            if types['header']:
                h = templater.stringify(self.t(types['header'], **props))
                if self.buffered:
                    self.header[ctx.rev()] = h
                else:
                    if self.lastheader != h:
                        self.lastheader = h
                        self.ui.write(h)

            # write changeset metadata, then patch if requested
            key = types['changeset']
            self.ui.write(templater.stringify(self.t(key, **props)))
            self.showpatch(ctx.node(), matchfn)

            if types['footer']:
                if not self.footer:
                    self.footer = templater.stringify(self.t(types['footer'],
                                                      **props))

        except KeyError, inst:
            msg = _("%s: no key named '%s'")
            raise util.Abort(msg % (self.t.mapfile, inst.args[0]))
        except SyntaxError, inst:
            raise util.Abort('%s: %s' % (self.t.mapfile, inst.args[0]))

def show_changeset(ui, repo, opts, buffered=False):
    """show one changeset using template or regular display.

    Display format will be the first non-empty hit of:
    1. option 'template'
    2. option 'style'
    3. [ui] setting 'logtemplate'
    4. [ui] setting 'style'
    If all of these values are either the unset or the empty string,
    regular display via changeset_printer() is done.
    """
    # options
    patch = False
    if opts.get('patch') or opts.get('stat'):
        patch = scmutil.matchall(repo)

    tmpl = opts.get('template')
    style = None
    if tmpl:
        tmpl = templater.parsestring(tmpl, quoted=False)
    else:
        style = opts.get('style')

    # ui settings
    if not (tmpl or style):
        tmpl = ui.config('ui', 'logtemplate')
        if tmpl:
            try:
                tmpl = templater.parsestring(tmpl)
            except SyntaxError:
                tmpl = templater.parsestring(tmpl, quoted=False)
        else:
            style = util.expandpath(ui.config('ui', 'style', ''))

    if not (tmpl or style):
        return changeset_printer(ui, repo, patch, opts, buffered)

    mapfile = None
    if style and not tmpl:
        mapfile = style
        if not os.path.split(mapfile)[0]:
            mapname = (templater.templatepath('map-cmdline.' + mapfile)
                       or templater.templatepath(mapfile))
            if mapname:
                mapfile = mapname

    try:
        t = changeset_templater(ui, repo, patch, opts, mapfile, buffered)
    except SyntaxError, inst:
        raise util.Abort(inst.args[0])
    if tmpl:
        t.use_template(tmpl)
    return t

def finddate(ui, repo, date):
    """Find the tipmost changeset that matches the given date spec"""

    df = util.matchdate(date)
    m = scmutil.matchall(repo)
    results = {}

    def prep(ctx, fns):
        d = ctx.date()
        if df(d[0]):
            results[ctx.rev()] = d

    for ctx in walkchangerevs(repo, m, {'rev': None}, prep):
        rev = ctx.rev()
        if rev in results:
            ui.status(_("found revision %s from %s\n") %
                      (rev, util.datestr(results[rev])))
            return str(rev)

    raise util.Abort(_("revision matching date not found"))

def increasingwindows(start, end, windowsize=8, sizelimit=512):
    if start < end:
        while start < end:
            yield start, min(windowsize, end - start)
            start += windowsize
            if windowsize < sizelimit:
                windowsize *= 2
    else:
        while start > end:
            yield start, min(windowsize, start - end - 1)
            start -= windowsize
            if windowsize < sizelimit:
                windowsize *= 2

def walkchangerevs(repo, match, opts, prepare):
    '''Iterate over files and the revs in which they changed.

    Callers most commonly need to iterate backwards over the history
    in which they are interested. Doing so has awful (quadratic-looking)
    performance, so we use iterators in a "windowed" way.

    We walk a window of revisions in the desired order.  Within the
    window, we first walk forwards to gather data, then in the desired
    order (usually backwards) to display it.

    This function returns an iterator yielding contexts. Before
    yielding each context, the iterator will first call the prepare
    function on each context in the window in forward order.'''

    follow = opts.get('follow') or opts.get('follow_first')

    if opts.get('rev'):
        revs = scmutil.revrange(repo, opts.get('rev'))
    elif follow:
        revs = repo.revs('reverse(:.)')
    else:
        revs = list(repo)
        revs.reverse()
    if not revs:
        return []
    wanted = set()
    slowpath = match.anypats() or (match.files() and opts.get('removed'))
    fncache = {}
    change = repo.changectx

    # First step is to fill wanted, the set of revisions that we want to yield.
    # When it does not induce extra cost, we also fill fncache for revisions in
    # wanted: a cache of filenames that were changed (ctx.files()) and that
    # match the file filtering conditions.

    if not slowpath and not match.files():
        # No files, no patterns.  Display all revs.
        wanted = set(revs)
    copies = []

    if not slowpath and match.files():
        # We only have to read through the filelog to find wanted revisions

        minrev, maxrev = min(revs), max(revs)
        def filerevgen(filelog, last):
            """
            Only files, no patterns.  Check the history of each file.

            Examines filelog entries within minrev, maxrev linkrev range
            Returns an iterator yielding (linkrev, parentlinkrevs, copied)
            tuples in backwards order
            """
            cl_count = len(repo)
            revs = []
            for j in xrange(0, last + 1):
                linkrev = filelog.linkrev(j)
                if linkrev < minrev:
                    continue
                # only yield rev for which we have the changelog, it can
                # happen while doing "hg log" during a pull or commit
                if linkrev >= cl_count:
                    break

                parentlinkrevs = []
                for p in filelog.parentrevs(j):
                    if p != nullrev:
                        parentlinkrevs.append(filelog.linkrev(p))
                n = filelog.node(j)
                revs.append((linkrev, parentlinkrevs,
                             follow and filelog.renamed(n)))

            return reversed(revs)
        def iterfiles():
            pctx = repo['.']
            for filename in match.files():
                if follow:
                    if filename not in pctx:
                        raise util.Abort(_('cannot follow file not in parent '
                                           'revision: "%s"') % filename)
                    yield filename, pctx[filename].filenode()
                else:
                    yield filename, None
            for filename_node in copies:
                yield filename_node
        for file_, node in iterfiles():
            filelog = repo.file(file_)
            if not len(filelog):
                if node is None:
                    # A zero count may be a directory or deleted file, so
                    # try to find matching entries on the slow path.
                    if follow:
                        raise util.Abort(
                            _('cannot follow nonexistent file: "%s"') % file_)
                    slowpath = True
                    break
                else:
                    continue

            if node is None:
                last = len(filelog) - 1
            else:
                last = filelog.rev(node)


            # keep track of all ancestors of the file
            ancestors = set([filelog.linkrev(last)])

            # iterate from latest to oldest revision
            for rev, flparentlinkrevs, copied in filerevgen(filelog, last):
                if not follow:
                    if rev > maxrev:
                        continue
                else:
                    # Note that last might not be the first interesting
                    # rev to us:
                    # if the file has been changed after maxrev, we'll
                    # have linkrev(last) > maxrev, and we still need
                    # to explore the file graph
                    if rev not in ancestors:
                        continue
                    # XXX insert 1327 fix here
                    if flparentlinkrevs:
                        ancestors.update(flparentlinkrevs)

                fncache.setdefault(rev, []).append(file_)
                wanted.add(rev)
                if copied:
                    copies.append(copied)

        # We decided to fall back to the slowpath because at least one
        # of the paths was not a file. Check to see if at least one of them
        # existed in history, otherwise simply return
        if slowpath:
            for path in match.files():
                if path == '.' or path in repo.store:
                    break
            else:
                return []

    if slowpath:
        # We have to read the changelog to match filenames against
        # changed files

        if follow:
            raise util.Abort(_('can only follow copies/renames for explicit '
                               'filenames'))

        # The slow path checks files modified in every changeset.
        for i in sorted(revs):
            ctx = change(i)
            matches = filter(match, ctx.files())
            if matches:
                fncache[i] = matches
                wanted.add(i)

    class followfilter(object):
        def __init__(self, onlyfirst=False):
            self.startrev = nullrev
            self.roots = set()
            self.onlyfirst = onlyfirst

        def match(self, rev):
            def realparents(rev):
                if self.onlyfirst:
                    return repo.changelog.parentrevs(rev)[0:1]
                else:
                    return filter(lambda x: x != nullrev,
                                  repo.changelog.parentrevs(rev))

            if self.startrev == nullrev:
                self.startrev = rev
                return True

            if rev > self.startrev:
                # forward: all descendants
                if not self.roots:
                    self.roots.add(self.startrev)
                for parent in realparents(rev):
                    if parent in self.roots:
                        self.roots.add(rev)
                        return True
            else:
                # backwards: all parents
                if not self.roots:
                    self.roots.update(realparents(self.startrev))
                if rev in self.roots:
                    self.roots.remove(rev)
                    self.roots.update(realparents(rev))
                    return True

            return False

    # it might be worthwhile to do this in the iterator if the rev range
    # is descending and the prune args are all within that range
    for rev in opts.get('prune', ()):
        rev = repo[rev].rev()
        ff = followfilter()
        stop = min(revs[0], revs[-1])
        for x in xrange(rev, stop - 1, -1):
            if ff.match(x):
                wanted.discard(x)

    # Choose a small initial window if we will probably only visit a
    # few commits.
    limit = loglimit(opts)
    windowsize = 8
    if limit:
        windowsize = min(limit, windowsize)

    # Now that wanted is correctly initialized, we can iterate over the
    # revision range, yielding only revisions in wanted.
    def iterate():
        if follow and not match.files():
            ff = followfilter(onlyfirst=opts.get('follow_first'))
            def want(rev):
                return ff.match(rev) and rev in wanted
        else:
            def want(rev):
                return rev in wanted

        for i, window in increasingwindows(0, len(revs), windowsize):
            nrevs = [rev for rev in revs[i:i + window] if want(rev)]
            for rev in sorted(nrevs):
                fns = fncache.get(rev)
                ctx = change(rev)
                if not fns:
                    def fns_generator():
                        for f in ctx.files():
                            if match(f):
                                yield f
                    fns = fns_generator()
                prepare(ctx, fns)
            for rev in nrevs:
                yield change(rev)
    return iterate()

def _makegraphfilematcher(repo, pats, followfirst):
    # When displaying a revision with --patch --follow FILE, we have
    # to know which file of the revision must be diffed. With
    # --follow, we want the names of the ancestors of FILE in the
    # revision, stored in "fcache". "fcache" is populated by
    # reproducing the graph traversal already done by --follow revset
    # and relating linkrevs to file names (which is not "correct" but
    # good enough).
    fcache = {}
    fcacheready = [False]
    pctx = repo['.']
    wctx = repo[None]

    def populate():
        for fn in pats:
            for i in ((pctx[fn],), pctx[fn].ancestors(followfirst=followfirst)):
                for c in i:
                    fcache.setdefault(c.linkrev(), set()).add(c.path())

    def filematcher(rev):
        if not fcacheready[0]:
            # Lazy initialization
            fcacheready[0] = True
            populate()
        return scmutil.match(wctx, fcache.get(rev, []), default='path')

    return filematcher

def _makegraphlogrevset(repo, pats, opts, revs):
    """Return (expr, filematcher) where expr is a revset string built
    from log options and file patterns or None. If --stat or --patch
    are not passed filematcher is None. Otherwise it is a callable
    taking a revision number and returning a match objects filtering
    the files to be detailed when displaying the revision.
    """
    opt2revset = {
        'no_merges':        ('not merge()', None),
        'only_merges':      ('merge()', None),
        '_ancestors':       ('ancestors(%(val)s)', None),
        '_fancestors':      ('_firstancestors(%(val)s)', None),
        '_descendants':     ('descendants(%(val)s)', None),
        '_fdescendants':    ('_firstdescendants(%(val)s)', None),
        '_matchfiles':      ('_matchfiles(%(val)s)', None),
        'date':             ('date(%(val)r)', None),
        'branch':           ('branch(%(val)r)', ' or '),
        '_patslog':         ('filelog(%(val)r)', ' or '),
        '_patsfollow':      ('follow(%(val)r)', ' or '),
        '_patsfollowfirst': ('_followfirst(%(val)r)', ' or '),
        'keyword':          ('keyword(%(val)r)', ' or '),
        'prune':            ('not (%(val)r or ancestors(%(val)r))', ' and '),
        'user':             ('user(%(val)r)', ' or '),
        }

    opts = dict(opts)
    # follow or not follow?
    follow = opts.get('follow') or opts.get('follow_first')
    followfirst = opts.get('follow_first') and 1 or 0
    # --follow with FILE behaviour depends on revs...
    startrev = revs[0]
    followdescendants = (len(revs) > 1 and revs[0] < revs[1]) and 1 or 0

    # branch and only_branch are really aliases and must be handled at
    # the same time
    opts['branch'] = opts.get('branch', []) + opts.get('only_branch', [])
    opts['branch'] = [repo.lookupbranch(b) for b in opts['branch']]
    # pats/include/exclude are passed to match.match() directly in
    # _matchfiles() revset but walkchangerevs() builds its matcher with
    # scmutil.match(). The difference is input pats are globbed on
    # platforms without shell expansion (windows).
    pctx = repo[None]
    match, pats = scmutil.matchandpats(pctx, pats, opts)
    slowpath = match.anypats() or (match.files() and opts.get('removed'))
    if not slowpath:
        for f in match.files():
            if follow and f not in pctx:
                raise util.Abort(_('cannot follow file not in parent '
                                   'revision: "%s"') % f)
            filelog = repo.file(f)
            if not len(filelog):
                # A zero count may be a directory or deleted file, so
                # try to find matching entries on the slow path.
                if follow:
                    raise util.Abort(
                        _('cannot follow nonexistent file: "%s"') % f)
                slowpath = True

        # We decided to fall back to the slowpath because at least one
        # of the paths was not a file. Check to see if at least one of them
        # existed in history - in that case, we'll continue down the
        # slowpath; otherwise, we can turn off the slowpath
        if slowpath:
            for path in match.files():
                if path == '.' or path in repo.store:
                    break
            else:
                slowpath = False

    if slowpath:
        # See walkchangerevs() slow path.
        #
        if follow:
            raise util.Abort(_('can only follow copies/renames for explicit '
                               'filenames'))
        # pats/include/exclude cannot be represented as separate
        # revset expressions as their filtering logic applies at file
        # level. For instance "-I a -X a" matches a revision touching
        # "a" and "b" while "file(a) and not file(b)" does
        # not. Besides, filesets are evaluated against the working
        # directory.
        matchargs = ['r:', 'd:relpath']
        for p in pats:
            matchargs.append('p:' + p)
        for p in opts.get('include', []):
            matchargs.append('i:' + p)
        for p in opts.get('exclude', []):
            matchargs.append('x:' + p)
        matchargs = ','.join(('%r' % p) for p in matchargs)
        opts['_matchfiles'] = matchargs
    else:
        if follow:
            fpats = ('_patsfollow', '_patsfollowfirst')
            fnopats = (('_ancestors', '_fancestors'),
                       ('_descendants', '_fdescendants'))
            if pats:
                # follow() revset interprets its file argument as a
                # manifest entry, so use match.files(), not pats.
                opts[fpats[followfirst]] = list(match.files())
            else:
                opts[fnopats[followdescendants][followfirst]] = str(startrev)
        else:
            opts['_patslog'] = list(pats)

    filematcher = None
    if opts.get('patch') or opts.get('stat'):
        if follow:
            filematcher = _makegraphfilematcher(repo, pats, followfirst)
        else:
            filematcher = lambda rev: match

    expr = []
    for op, val in opts.iteritems():
        if not val:
            continue
        if op not in opt2revset:
            continue
        revop, andor = opt2revset[op]
        if '%(val)' not in revop:
            expr.append(revop)
        else:
            if not isinstance(val, list):
                e = revop % {'val': val}
            else:
                e = '(' + andor.join((revop % {'val': v}) for v in val) + ')'
            expr.append(e)

    if expr:
        expr = '(' + ' and '.join(expr) + ')'
    else:
        expr = None
    return expr, filematcher

def getgraphlogrevs(repo, pats, opts):
    """Return (revs, expr, filematcher) where revs is an iterable of
    revision numbers, expr is a revset string built from log options
    and file patterns or None, and used to filter 'revs'. If --stat or
    --patch are not passed filematcher is None. Otherwise it is a
    callable taking a revision number and returning a match objects
    filtering the files to be detailed when displaying the revision.
    """
    if not len(repo):
        return [], None, None
    limit = loglimit(opts)
    # Default --rev value depends on --follow but --follow behaviour
    # depends on revisions resolved from --rev...
    follow = opts.get('follow') or opts.get('follow_first')
    possiblyunsorted = False # whether revs might need sorting
    if opts.get('rev'):
        revs = scmutil.revrange(repo, opts['rev'])
        # Don't sort here because _makegraphlogrevset might depend on the
        # order of revs
        possiblyunsorted = True
    else:
        if follow and len(repo) > 0:
            revs = repo.revs('reverse(:.)')
        else:
            revs = list(repo.changelog)
            revs.reverse()
    if not revs:
        return [], None, None
    expr, filematcher = _makegraphlogrevset(repo, pats, opts, revs)
    if possiblyunsorted:
        revs.sort(reverse=True)
    if expr:
        # Revset matchers often operate faster on revisions in changelog
        # order, because most filters deal with the changelog.
        revs.reverse()
        matcher = revset.match(repo.ui, expr)
        # Revset matches can reorder revisions. "A or B" typically returns
        # returns the revision matching A then the revision matching B. Sort
        # again to fix that.
        revs = matcher(repo, revs)
        revs.sort(reverse=True)
    if limit is not None:
        revs = revs[:limit]

    return revs, expr, filematcher

def displaygraph(ui, dag, displayer, showparents, edgefn, getrenamed=None,
                 filematcher=None):
    seen, state = [], graphmod.asciistate()
    for rev, type, ctx, parents in dag:
        char = 'o'
        if ctx.node() in showparents:
            char = '@'
        elif ctx.obsolete():
            char = 'x'
        copies = None
        if getrenamed and ctx.rev():
            copies = []
            for fn in ctx.files():
                rename = getrenamed(fn, ctx.rev())
                if rename:
                    copies.append((fn, rename[0]))
        revmatchfn = None
        if filematcher is not None:
            revmatchfn = filematcher(ctx.rev())
        displayer.show(ctx, copies=copies, matchfn=revmatchfn)
        lines = displayer.hunk.pop(rev).split('\n')
        if not lines[-1]:
            del lines[-1]
        displayer.flush(rev)
        edges = edgefn(type, char, lines, seen, rev, parents)
        for type, char, lines, coldata in edges:
            graphmod.ascii(ui, state, type, char, lines, coldata)
    displayer.close()

def graphlog(ui, repo, *pats, **opts):
    # Parameters are identical to log command ones
    revs, expr, filematcher = getgraphlogrevs(repo, pats, opts)
    revdag = graphmod.dagwalker(repo, revs)

    getrenamed = None
    if opts.get('copies'):
        endrev = None
        if opts.get('rev'):
            endrev = max(scmutil.revrange(repo, opts.get('rev'))) + 1
        getrenamed = templatekw.getrenamedfn(repo, endrev=endrev)
    displayer = show_changeset(ui, repo, opts, buffered=True)
    showparents = [ctx.node() for ctx in repo[None].parents()]
    displaygraph(ui, revdag, displayer, showparents,
                 graphmod.asciiedges, getrenamed, filematcher)

def checkunsupportedgraphflags(pats, opts):
    for op in ["newest_first"]:
        if op in opts and opts[op]:
            raise util.Abort(_("-G/--graph option is incompatible with --%s")
                             % op.replace("_", "-"))

def graphrevs(repo, nodes, opts):
    limit = loglimit(opts)
    nodes.reverse()
    if limit is not None:
        nodes = nodes[:limit]
    return graphmod.nodes(repo, nodes)

def add(ui, repo, match, dryrun, listsubrepos, prefix, explicitonly):
    join = lambda f: os.path.join(prefix, f)
    bad = []
    oldbad = match.bad
    match.bad = lambda x, y: bad.append(x) or oldbad(x, y)
    names = []
    wctx = repo[None]
    cca = None
    abort, warn = scmutil.checkportabilityalert(ui)
    if abort or warn:
        cca = scmutil.casecollisionauditor(ui, abort, repo.dirstate)
    for f in repo.walk(match):
        exact = match.exact(f)
        if exact or not explicitonly and f not in repo.dirstate:
            if cca:
                cca(f)
            names.append(f)
            if ui.verbose or not exact:
                ui.status(_('adding %s\n') % match.rel(join(f)))

    for subpath in sorted(wctx.substate):
        sub = wctx.sub(subpath)
        try:
            submatch = matchmod.narrowmatcher(subpath, match)
            if listsubrepos:
                bad.extend(sub.add(ui, submatch, dryrun, listsubrepos, prefix,
                                   False))
            else:
                bad.extend(sub.add(ui, submatch, dryrun, listsubrepos, prefix,
                                   True))
        except error.LookupError:
            ui.status(_("skipping missing subrepository: %s\n")
                           % join(subpath))

    if not dryrun:
        rejected = wctx.add(names, prefix)
        bad.extend(f for f in rejected if f in match.files())
    return bad

def forget(ui, repo, match, prefix, explicitonly):
    join = lambda f: os.path.join(prefix, f)
    bad = []
    oldbad = match.bad
    match.bad = lambda x, y: bad.append(x) or oldbad(x, y)
    wctx = repo[None]
    forgot = []
    s = repo.status(match=match, clean=True)
    forget = sorted(s[0] + s[1] + s[3] + s[6])
    if explicitonly:
        forget = [f for f in forget if match.exact(f)]

    for subpath in sorted(wctx.substate):
        sub = wctx.sub(subpath)
        try:
            submatch = matchmod.narrowmatcher(subpath, match)
            subbad, subforgot = sub.forget(ui, submatch, prefix)
            bad.extend([subpath + '/' + f for f in subbad])
            forgot.extend([subpath + '/' + f for f in subforgot])
        except error.LookupError:
            ui.status(_("skipping missing subrepository: %s\n")
                           % join(subpath))

    if not explicitonly:
        for f in match.files():
            if f not in repo.dirstate and not os.path.isdir(match.rel(join(f))):
                if f not in forgot:
                    if os.path.exists(match.rel(join(f))):
                        ui.warn(_('not removing %s: '
                                  'file is already untracked\n')
                                % match.rel(join(f)))
                    bad.append(f)

    for f in forget:
        if ui.verbose or not match.exact(f):
            ui.status(_('removing %s\n') % match.rel(join(f)))

    rejected = wctx.forget(forget, prefix)
    bad.extend(f for f in rejected if f in match.files())
    forgot.extend(forget)
    return bad, forgot

def duplicatecopies(repo, rev, fromrev):
    '''reproduce copies from fromrev to rev in the dirstate'''
    for dst, src in copies.pathcopies(repo[fromrev], repo[rev]).iteritems():
        # copies.pathcopies returns backward renames, so dst might not
        # actually be in the dirstate
        if repo.dirstate[dst] in "nma":
            repo.dirstate.copy(src, dst)

def commit(ui, repo, commitfunc, pats, opts):
    '''commit the specified files or all outstanding changes'''
    date = opts.get('date')
    if date:
        opts['date'] = util.parsedate(date)
    message = logmessage(ui, opts)

    # extract addremove carefully -- this function can be called from a command
    # that doesn't support addremove
    if opts.get('addremove'):
        scmutil.addremove(repo, pats, opts)

    return commitfunc(ui, repo, message,
                      scmutil.match(repo[None], pats, opts), opts)

def amend(ui, repo, commitfunc, old, extra, pats, opts):
    ui.note(_('amending changeset %s\n') % old)
    base = old.p1()

    wlock = lock = newid = None
    try:
        wlock = repo.wlock()
        lock = repo.lock()
        tr = repo.transaction('amend')
        try:
            # See if we got a message from -m or -l, if not, open the editor
            # with the message of the changeset to amend
            message = logmessage(ui, opts)
            # ensure logfile does not conflict with later enforcement of the
            # message. potential logfile content has been processed by
            # `logmessage` anyway.
            opts.pop('logfile')
            # First, do a regular commit to record all changes in the working
            # directory (if there are any)
            ui.callhooks = False
            currentbookmark = repo._bookmarkcurrent
            try:
                repo._bookmarkcurrent = None
                opts['message'] = 'temporary amend commit for %s' % old
                node = commit(ui, repo, commitfunc, pats, opts)
            finally:
                repo._bookmarkcurrent = currentbookmark
                ui.callhooks = True
            ctx = repo[node]

            # Participating changesets:
            #
            # node/ctx o - new (intermediate) commit that contains changes
            #          |   from working dir to go into amending commit
            #          |   (or a workingctx if there were no changes)
            #          |
            # old      o - changeset to amend
            #          |
            # base     o - parent of amending changeset

            # Update extra dict from amended commit (e.g. to preserve graft
            # source)
            extra.update(old.extra())

            # Also update it from the intermediate commit or from the wctx
            extra.update(ctx.extra())

            if len(old.parents()) > 1:
                # ctx.files() isn't reliable for merges, so fall back to the
                # slower repo.status() method
                files = set([fn for st in repo.status(base, old)[:3]
                             for fn in st])
            else:
                files = set(old.files())

            # Second, we use either the commit we just did, or if there were no
            # changes the parent of the working directory as the version of the
            # files in the final amend commit
            if node:
                ui.note(_('copying changeset %s to %s\n') % (ctx, base))

                user = ctx.user()
                date = ctx.date()
                # Recompute copies (avoid recording a -> b -> a)
                copied = copies.pathcopies(base, ctx)

                # Prune files which were reverted by the updates: if old
                # introduced file X and our intermediate commit, node,
                # renamed that file, then those two files are the same and
                # we can discard X from our list of files. Likewise if X
                # was deleted, it's no longer relevant
                files.update(ctx.files())

                def samefile(f):
                    if f in ctx.manifest():
                        a = ctx.filectx(f)
                        if f in base.manifest():
                            b = base.filectx(f)
                            return (not a.cmp(b)
                                    and a.flags() == b.flags())
                        else:
                            return False
                    else:
                        return f not in base.manifest()
                files = [f for f in files if not samefile(f)]

                def filectxfn(repo, ctx_, path):
                    try:
                        fctx = ctx[path]
                        flags = fctx.flags()
                        mctx = context.memfilectx(fctx.path(), fctx.data(),
                                                  islink='l' in flags,
                                                  isexec='x' in flags,
                                                  copied=copied.get(path))
                        return mctx
                    except KeyError:
                        raise IOError
            else:
                ui.note(_('copying changeset %s to %s\n') % (old, base))

                # Use version of files as in the old cset
                def filectxfn(repo, ctx_, path):
                    try:
                        return old.filectx(path)
                    except KeyError:
                        raise IOError

                user = opts.get('user') or old.user()
                date = opts.get('date') or old.date()
            editmsg = False
            if not message:
                editmsg = True
                message = old.description()

            pureextra = extra.copy()
            extra['amend_source'] = old.hex()

            new = context.memctx(repo,
                                 parents=[base.node(), old.p2().node()],
                                 text=message,
                                 files=files,
                                 filectxfn=filectxfn,
                                 user=user,
                                 date=date,
                                 extra=extra)
            if editmsg:
                new._text = commitforceeditor(repo, new, [])

            newdesc =  changelog.stripdesc(new.description())
            if ((not node)
                and newdesc == old.description()
                and user == old.user()
                and date == old.date()
                and pureextra == old.extra()):
                # nothing changed. continuing here would create a new node
                # anyway because of the amend_source noise.
                #
                # This not what we expect from amend.
                return old.node()

            ph = repo.ui.config('phases', 'new-commit', phases.draft)
            try:
                repo.ui.setconfig('phases', 'new-commit', old.phase())
                newid = repo.commitctx(new)
            finally:
                repo.ui.setconfig('phases', 'new-commit', ph)
            if newid != old.node():
                # Reroute the working copy parent to the new changeset
                repo.setparents(newid, nullid)

                # Move bookmarks from old parent to amend commit
                bms = repo.nodebookmarks(old.node())
                if bms:
                    marks = repo._bookmarks
                    for bm in bms:
                        marks[bm] = newid
                    marks.write()
            #commit the whole amend process
            if obsolete._enabled and newid != old.node():
                # mark the new changeset as successor of the rewritten one
                new = repo[newid]
                obs = [(old, (new,))]
                if node:
                    obs.append((ctx, ()))

                obsolete.createmarkers(repo, obs)
            tr.close()
        finally:
            tr.release()
        if (not obsolete._enabled) and newid != old.node():
            # Strip the intermediate commit (if there was one) and the amended
            # commit
            if node:
                ui.note(_('stripping intermediate changeset %s\n') % ctx)
            ui.note(_('stripping amended changeset %s\n') % old)
            repair.strip(ui, repo, old.node(), topic='amend-backup')
    finally:
        if newid is None:
            repo.dirstate.invalidate()
        lockmod.release(lock, wlock)
    return newid

def commiteditor(repo, ctx, subs):
    if ctx.description():
        return ctx.description()
    return commitforceeditor(repo, ctx, subs)

def commitforceeditor(repo, ctx, subs):
    edittext = []
    modified, added, removed = ctx.modified(), ctx.added(), ctx.removed()
    if ctx.description():
        edittext.append(ctx.description())
    edittext.append("")
    edittext.append("") # Empty line between message and comments.
    edittext.append(_("HG: Enter commit message."
                      "  Lines beginning with 'HG:' are removed."))
    edittext.append(_("HG: Leave message empty to abort commit."))
    edittext.append("HG: --")
    edittext.append(_("HG: user: %s") % ctx.user())
    if ctx.p2():
        edittext.append(_("HG: branch merge"))
    if ctx.branch():
        edittext.append(_("HG: branch '%s'") % ctx.branch())
    if bookmarks.iscurrent(repo):
        edittext.append(_("HG: bookmark '%s'") % repo._bookmarkcurrent)
    edittext.extend([_("HG: subrepo %s") % s for s in subs])
    edittext.extend([_("HG: added %s") % f for f in added])
    edittext.extend([_("HG: changed %s") % f for f in modified])
    edittext.extend([_("HG: removed %s") % f for f in removed])
    if not added and not modified and not removed:
        edittext.append(_("HG: no files changed"))
    edittext.append("")
    # run editor in the repository root
    olddir = os.getcwd()
    os.chdir(repo.root)
    text = repo.ui.edit("\n".join(edittext), ctx.user())
    text = re.sub("(?m)^HG:.*(\n|$)", "", text)
    os.chdir(olddir)

    if not text.strip():
        raise util.Abort(_("empty commit message"))

    return text

def commitstatus(repo, node, branch, bheads=None, opts={}):
    ctx = repo[node]
    parents = ctx.parents()

    if (not opts.get('amend') and bheads and node not in bheads and not
        [x for x in parents if x.node() in bheads and x.branch() == branch]):
        repo.ui.status(_('created new head\n'))
        # The message is not printed for initial roots. For the other
        # changesets, it is printed in the following situations:
        #
        # Par column: for the 2 parents with ...
        #   N: null or no parent
        #   B: parent is on another named branch
        #   C: parent is a regular non head changeset
        #   H: parent was a branch head of the current branch
        # Msg column: whether we print "created new head" message
        # In the following, it is assumed that there already exists some
        # initial branch heads of the current branch, otherwise nothing is
        # printed anyway.
        #
        # Par Msg Comment
        # N N  y  additional topo root
        #
        # B N  y  additional branch root
        # C N  y  additional topo head
        # H N  n  usual case
        #
        # B B  y  weird additional branch root
        # C B  y  branch merge
        # H B  n  merge with named branch
        #
        # C C  y  additional head from merge
        # C H  n  merge with a head
        #
        # H H  n  head merge: head count decreases

    if not opts.get('close_branch'):
        for r in parents:
            if r.closesbranch() and r.branch() == branch:
                repo.ui.status(_('reopening closed branch head %d\n') % r)

    if repo.ui.debugflag:
        repo.ui.write(_('committed changeset %d:%s\n') % (int(ctx), ctx.hex()))
    elif repo.ui.verbose:
        repo.ui.write(_('committed changeset %d:%s\n') % (int(ctx), ctx))

def revert(ui, repo, ctx, parents, *pats, **opts):
    parent, p2 = parents
    node = ctx.node()

    mf = ctx.manifest()
    if node == parent:
        pmf = mf
    else:
        pmf = None

    # need all matching names in dirstate and manifest of target rev,
    # so have to walk both. do not print errors if files exist in one
    # but not other.

    names = {}

    wlock = repo.wlock()
    try:
        # walk dirstate.

        m = scmutil.match(repo[None], pats, opts)
        m.bad = lambda x, y: False
        for abs in repo.walk(m):
            names[abs] = m.rel(abs), m.exact(abs)

        # walk target manifest.

        def badfn(path, msg):
            if path in names:
                return
            if path in ctx.substate:
                return
            path_ = path + '/'
            for f in names:
                if f.startswith(path_):
                    return
            ui.warn("%s: %s\n" % (m.rel(path), msg))

        m = scmutil.match(ctx, pats, opts)
        m.bad = badfn
        for abs in ctx.walk(m):
            if abs not in names:
                names[abs] = m.rel(abs), m.exact(abs)

        # get the list of subrepos that must be reverted
        targetsubs = sorted(s for s in ctx.substate if m(s))
        m = scmutil.matchfiles(repo, names)
        changes = repo.status(match=m)[:4]
        modified, added, removed, deleted = map(set, changes)

        # if f is a rename, also revert the source
        cwd = repo.getcwd()
        for f in added:
            src = repo.dirstate.copied(f)
            if src and src not in names and repo.dirstate[src] == 'r':
                removed.add(src)
                names[src] = (repo.pathto(src, cwd), True)

        def removeforget(abs):
            if repo.dirstate[abs] == 'a':
                return _('forgetting %s\n')
            return _('removing %s\n')

        revert = ([], _('reverting %s\n'))
        add = ([], _('adding %s\n'))
        remove = ([], removeforget)
        undelete = ([], _('undeleting %s\n'))

        disptable = (
            # dispatch table:
            #   file state
            #   action if in target manifest
            #   action if not in target manifest
            #   make backup if in target manifest
            #   make backup if not in target manifest
            (modified, revert, remove, True, True),
            (added, revert, remove, True, False),
            (removed, undelete, None, False, False),
            (deleted, revert, remove, False, False),
            )

        for abs, (rel, exact) in sorted(names.items()):
            mfentry = mf.get(abs)
            target = repo.wjoin(abs)
            def handle(xlist, dobackup):
                xlist[0].append(abs)
                if (dobackup and not opts.get('no_backup') and
                    os.path.lexists(target)):
                    bakname = "%s.orig" % rel
                    ui.note(_('saving current version of %s as %s\n') %
                            (rel, bakname))
                    if not opts.get('dry_run'):
                        util.rename(target, bakname)
                if ui.verbose or not exact:
                    msg = xlist[1]
                    if not isinstance(msg, basestring):
                        msg = msg(abs)
                    ui.status(msg % rel)
            for table, hitlist, misslist, backuphit, backupmiss in disptable:
                if abs not in table:
                    continue
                # file has changed in dirstate
                if mfentry:
                    handle(hitlist, backuphit)
                elif misslist is not None:
                    handle(misslist, backupmiss)
                break
            else:
                if abs not in repo.dirstate:
                    if mfentry:
                        handle(add, True)
                    elif exact:
                        ui.warn(_('file not managed: %s\n') % rel)
                    continue
                # file has not changed in dirstate
                if node == parent:
                    if exact:
                        ui.warn(_('no changes needed to %s\n') % rel)
                    continue
                if pmf is None:
                    # only need parent manifest in this unlikely case,
                    # so do not read by default
                    pmf = repo[parent].manifest()
                if abs in pmf and mfentry:
                    # if version of file is same in parent and target
                    # manifests, do nothing
                    if (pmf[abs] != mfentry or
                        pmf.flags(abs) != mf.flags(abs)):
                        handle(revert, False)
                else:
                    handle(remove, False)

        if not opts.get('dry_run'):
            def checkout(f):
                fc = ctx[f]
                repo.wwrite(f, fc.data(), fc.flags())

            audit_path = scmutil.pathauditor(repo.root)
            for f in remove[0]:
                if repo.dirstate[f] == 'a':
                    repo.dirstate.drop(f)
                    continue
                audit_path(f)
                try:
                    util.unlinkpath(repo.wjoin(f))
                except OSError:
                    pass
                repo.dirstate.remove(f)

            normal = None
            if node == parent:
                # We're reverting to our parent. If possible, we'd like status
                # to report the file as clean. We have to use normallookup for
                # merges to avoid losing information about merged/dirty files.
                if p2 != nullid:
                    normal = repo.dirstate.normallookup
                else:
                    normal = repo.dirstate.normal
            for f in revert[0]:
                checkout(f)
                if normal:
                    normal(f)

            for f in add[0]:
                checkout(f)
                repo.dirstate.add(f)

            normal = repo.dirstate.normallookup
            if node == parent and p2 == nullid:
                normal = repo.dirstate.normal
            for f in undelete[0]:
                checkout(f)
                normal(f)

            copied = copies.pathcopies(repo[parent], ctx)

            for f in add[0] + undelete[0] + revert[0]:
                if f in copied:
                    repo.dirstate.copy(copied[f], f)

            if targetsubs:
                # Revert the subrepos on the revert list
                for sub in targetsubs:
                    ctx.sub(sub).revert(ui, ctx.substate[sub], *pats, **opts)
    finally:
        wlock.release()

def command(table):
    '''returns a function object bound to table which can be used as
    a decorator for populating table as a command table'''

    def cmd(name, options=(), synopsis=None):
        def decorator(func):
            if synopsis:
                table[name] = func, list(options), synopsis
            else:
                table[name] = func, list(options)
            return func
        return decorator

    return cmd
