# scmutil.py - Mercurial core utility functions
#
#  Copyright Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
from mercurial.node import nullrev
import util, error, osutil, revset, similar, encoding, phases, parsers
import match as matchmod
import os, errno, re, stat, glob

if os.name == 'nt':
    import scmwindows as scmplatform
else:
    import scmposix as scmplatform

systemrcpath = scmplatform.systemrcpath
userrcpath = scmplatform.userrcpath

def nochangesfound(ui, repo, excluded=None):
    '''Report no changes for push/pull, excluded is None or a list of
    nodes excluded from the push/pull.
    '''
    secretlist = []
    if excluded:
        for n in excluded:
            if n not in repo:
                # discovery should not have included the filtered revision,
                # we have to explicitly exclude it until discovery is cleanup.
                continue
            ctx = repo[n]
            if ctx.phase() >= phases.secret and not ctx.extinct():
                secretlist.append(n)

    if secretlist:
        ui.status(_("no changes found (ignored %d secret changesets)\n")
                  % len(secretlist))
    else:
        ui.status(_("no changes found\n"))

def checknewlabel(repo, lbl, kind):
    # Do not use the "kind" parameter in ui output.
    # It makes strings difficult to translate.
    if lbl in ['tip', '.', 'null']:
        raise util.Abort(_("the name '%s' is reserved") % lbl)
    for c in (':', '\0', '\n', '\r'):
        if c in lbl:
            raise util.Abort(_("%r cannot be used in a name") % c)
    try:
        int(lbl)
        raise util.Abort(_("cannot use an integer as a name"))
    except ValueError:
        pass

def checkfilename(f):
    '''Check that the filename f is an acceptable filename for a tracked file'''
    if '\r' in f or '\n' in f:
        raise util.Abort(_("'\\n' and '\\r' disallowed in filenames: %r") % f)

def checkportable(ui, f):
    '''Check if filename f is portable and warn or abort depending on config'''
    checkfilename(f)
    abort, warn = checkportabilityalert(ui)
    if abort or warn:
        msg = util.checkwinfilename(f)
        if msg:
            msg = "%s: %r" % (msg, f)
            if abort:
                raise util.Abort(msg)
            ui.warn(_("warning: %s\n") % msg)

def checkportabilityalert(ui):
    '''check if the user's config requests nothing, a warning, or abort for
    non-portable filenames'''
    val = ui.config('ui', 'portablefilenames', 'warn')
    lval = val.lower()
    bval = util.parsebool(val)
    abort = os.name == 'nt' or lval == 'abort'
    warn = bval or lval == 'warn'
    if bval is None and not (warn or abort or lval == 'ignore'):
        raise error.ConfigError(
            _("ui.portablefilenames value is invalid ('%s')") % val)
    return abort, warn

class casecollisionauditor(object):
    def __init__(self, ui, abort, dirstate):
        self._ui = ui
        self._abort = abort
        allfiles = '\0'.join(dirstate._map)
        self._loweredfiles = set(encoding.lower(allfiles).split('\0'))
        self._dirstate = dirstate
        # The purpose of _newfiles is so that we don't complain about
        # case collisions if someone were to call this object with the
        # same filename twice.
        self._newfiles = set()

    def __call__(self, f):
        fl = encoding.lower(f)
        if (fl in self._loweredfiles and f not in self._dirstate and
            f not in self._newfiles):
            msg = _('possible case-folding collision for %s') % f
            if self._abort:
                raise util.Abort(msg)
            self._ui.warn(_("warning: %s\n") % msg)
        self._loweredfiles.add(fl)
        self._newfiles.add(f)

class pathauditor(object):
    '''ensure that a filesystem path contains no banned components.
    the following properties of a path are checked:

    - ends with a directory separator
    - under top-level .hg
    - starts at the root of a windows drive
    - contains ".."
    - traverses a symlink (e.g. a/symlink_here/b)
    - inside a nested repository (a callback can be used to approve
      some nested repositories, e.g., subrepositories)
    '''

    def __init__(self, root, callback=None):
        self.audited = set()
        self.auditeddir = set()
        self.root = root
        self.callback = callback
        if os.path.lexists(root) and not util.checkcase(root):
            self.normcase = util.normcase
        else:
            self.normcase = lambda x: x

    def __call__(self, path):
        '''Check the relative path.
        path may contain a pattern (e.g. foodir/**.txt)'''

        path = util.localpath(path)
        normpath = self.normcase(path)
        if normpath in self.audited:
            return
        # AIX ignores "/" at end of path, others raise EISDIR.
        if util.endswithsep(path):
            raise util.Abort(_("path ends in directory separator: %s") % path)
        parts = util.splitpath(path)
        if (os.path.splitdrive(path)[0]
            or parts[0].lower() in ('.hg', '.hg.', '')
            or os.pardir in parts):
            raise util.Abort(_("path contains illegal component: %s") % path)
        if '.hg' in path.lower():
            lparts = [p.lower() for p in parts]
            for p in '.hg', '.hg.':
                if p in lparts[1:]:
                    pos = lparts.index(p)
                    base = os.path.join(*parts[:pos])
                    raise util.Abort(_("path '%s' is inside nested repo %r")
                                     % (path, base))

        normparts = util.splitpath(normpath)
        assert len(parts) == len(normparts)

        parts.pop()
        normparts.pop()
        prefixes = []
        while parts:
            prefix = os.sep.join(parts)
            normprefix = os.sep.join(normparts)
            if normprefix in self.auditeddir:
                break
            curpath = os.path.join(self.root, prefix)
            try:
                st = os.lstat(curpath)
            except OSError, err:
                # EINVAL can be raised as invalid path syntax under win32.
                # They must be ignored for patterns can be checked too.
                if err.errno not in (errno.ENOENT, errno.ENOTDIR, errno.EINVAL):
                    raise
            else:
                if stat.S_ISLNK(st.st_mode):
                    raise util.Abort(
                        _('path %r traverses symbolic link %r')
                        % (path, prefix))
                elif (stat.S_ISDIR(st.st_mode) and
                      os.path.isdir(os.path.join(curpath, '.hg'))):
                    if not self.callback or not self.callback(curpath):
                        raise util.Abort(_("path '%s' is inside nested "
                                           "repo %r")
                                         % (path, prefix))
            prefixes.append(normprefix)
            parts.pop()
            normparts.pop()

        self.audited.add(normpath)
        # only add prefixes to the cache after checking everything: we don't
        # want to add "foo/bar/baz" before checking if there's a "foo/.hg"
        self.auditeddir.update(prefixes)

    def check(self, path):
        try:
            self(path)
            return True
        except (OSError, util.Abort):
            return False

class abstractvfs(object):
    """Abstract base class; cannot be instantiated"""

    def __init__(self, *args, **kwargs):
        '''Prevent instantiation; don't call this from subclasses.'''
        raise NotImplementedError('attempted instantiating ' + str(type(self)))

    def tryread(self, path):
        '''gracefully return an empty string for missing files'''
        try:
            return self.read(path)
        except IOError, inst:
            if inst.errno != errno.ENOENT:
                raise
        return ""

    def read(self, path):
        fp = self(path, 'rb')
        try:
            return fp.read()
        finally:
            fp.close()

    def write(self, path, data):
        fp = self(path, 'wb')
        try:
            return fp.write(data)
        finally:
            fp.close()

    def append(self, path, data):
        fp = self(path, 'ab')
        try:
            return fp.write(data)
        finally:
            fp.close()

    def exists(self, path=None):
        return os.path.exists(self.join(path))

    def isdir(self, path=None):
        return os.path.isdir(self.join(path))

    def islink(self, path=None):
        return os.path.islink(self.join(path))

    def makedir(self, path=None, notindexed=True):
        return util.makedir(self.join(path), notindexed)

    def makedirs(self, path=None, mode=None):
        return util.makedirs(self.join(path), mode)

    def mkdir(self, path=None):
        return os.mkdir(self.join(path))

    def readdir(self, path=None, stat=None, skip=None):
        return osutil.listdir(self.join(path), stat, skip)

    def rename(self, src, dst):
        return util.rename(self.join(src), self.join(dst))

    def readlink(self, path):
        return os.readlink(self.join(path))

    def setflags(self, path, l, x):
        return util.setflags(self.join(path), l, x)

    def stat(self, path=None):
        return os.stat(self.join(path))

class vfs(abstractvfs):
    '''Operate files relative to a base directory

    This class is used to hide the details of COW semantics and
    remote file access from higher level code.
    '''
    def __init__(self, base, audit=True, expandpath=False, realpath=False):
        if expandpath:
            base = util.expandpath(base)
        if realpath:
            base = os.path.realpath(base)
        self.base = base
        self._setmustaudit(audit)
        self.createmode = None
        self._trustnlink = None

    def _getmustaudit(self):
        return self._audit

    def _setmustaudit(self, onoff):
        self._audit = onoff
        if onoff:
            self.audit = pathauditor(self.base)
        else:
            self.audit = util.always

    mustaudit = property(_getmustaudit, _setmustaudit)

    @util.propertycache
    def _cansymlink(self):
        return util.checklink(self.base)

    @util.propertycache
    def _chmod(self):
        return util.checkexec(self.base)

    def _fixfilemode(self, name):
        if self.createmode is None or not self._chmod:
            return
        os.chmod(name, self.createmode & 0666)

    def __call__(self, path, mode="r", text=False, atomictemp=False):
        if self._audit:
            r = util.checkosfilename(path)
            if r:
                raise util.Abort("%s: %r" % (r, path))
        self.audit(path)
        f = self.join(path)

        if not text and "b" not in mode:
            mode += "b" # for that other OS

        nlink = -1
        if mode not in ('r', 'rb'):
            dirname, basename = util.split(f)
            # If basename is empty, then the path is malformed because it points
            # to a directory. Let the posixfile() call below raise IOError.
            if basename:
                if atomictemp:
                    util.ensuredirs(dirname, self.createmode)
                    return util.atomictempfile(f, mode, self.createmode)
                try:
                    if 'w' in mode:
                        util.unlink(f)
                        nlink = 0
                    else:
                        # nlinks() may behave differently for files on Windows
                        # shares if the file is open.
                        fd = util.posixfile(f)
                        nlink = util.nlinks(f)
                        if nlink < 1:
                            nlink = 2 # force mktempcopy (issue1922)
                        fd.close()
                except (OSError, IOError), e:
                    if e.errno != errno.ENOENT:
                        raise
                    nlink = 0
                    util.ensuredirs(dirname, self.createmode)
                if nlink > 0:
                    if self._trustnlink is None:
                        self._trustnlink = nlink > 1 or util.checknlink(f)
                    if nlink > 1 or not self._trustnlink:
                        util.rename(util.mktempcopy(f), f)
        fp = util.posixfile(f, mode)
        if nlink == 0:
            self._fixfilemode(f)
        return fp

    def symlink(self, src, dst):
        self.audit(dst)
        linkname = self.join(dst)
        try:
            os.unlink(linkname)
        except OSError:
            pass

        util.ensuredirs(os.path.dirname(linkname), self.createmode)

        if self._cansymlink:
            try:
                os.symlink(src, linkname)
            except OSError, err:
                raise OSError(err.errno, _('could not symlink to %r: %s') %
                              (src, err.strerror), linkname)
        else:
            self.write(dst, src)

    def join(self, path):
        if path:
            return os.path.join(self.base, path)
        else:
            return self.base

opener = vfs

class auditvfs(object):
    def __init__(self, vfs):
        self.vfs = vfs

    def _getmustaudit(self):
        return self.vfs.mustaudit

    def _setmustaudit(self, onoff):
        self.vfs.mustaudit = onoff

    mustaudit = property(_getmustaudit, _setmustaudit)

class filtervfs(abstractvfs, auditvfs):
    '''Wrapper vfs for filtering filenames with a function.'''

    def __init__(self, vfs, filter):
        auditvfs.__init__(self, vfs)
        self._filter = filter

    def __call__(self, path, *args, **kwargs):
        return self.vfs(self._filter(path), *args, **kwargs)

    def join(self, path):
        if path:
            return self.vfs.join(self._filter(path))
        else:
            return self.vfs.join(path)

filteropener = filtervfs

class readonlyvfs(abstractvfs, auditvfs):
    '''Wrapper vfs preventing any writing.'''

    def __init__(self, vfs):
        auditvfs.__init__(self, vfs)

    def __call__(self, path, mode='r', *args, **kw):
        if mode not in ('r', 'rb'):
            raise util.Abort('this vfs is read only')
        return self.vfs(path, mode, *args, **kw)


def canonpath(root, cwd, myname, auditor=None):
    '''return the canonical path of myname, given cwd and root'''
    if util.endswithsep(root):
        rootsep = root
    else:
        rootsep = root + os.sep
    name = myname
    if not os.path.isabs(name):
        name = os.path.join(root, cwd, name)
    name = os.path.normpath(name)
    if auditor is None:
        auditor = pathauditor(root)
    if name != rootsep and name.startswith(rootsep):
        name = name[len(rootsep):]
        auditor(name)
        return util.pconvert(name)
    elif name == root:
        return ''
    else:
        # Determine whether `name' is in the hierarchy at or beneath `root',
        # by iterating name=dirname(name) until that causes no change (can't
        # check name == '/', because that doesn't work on windows). The list
        # `rel' holds the reversed list of components making up the relative
        # file name we want.
        rel = []
        while True:
            try:
                s = util.samefile(name, root)
            except OSError:
                s = False
            if s:
                if not rel:
                    # name was actually the same as root (maybe a symlink)
                    return ''
                rel.reverse()
                name = os.path.join(*rel)
                auditor(name)
                return util.pconvert(name)
            dirname, basename = util.split(name)
            rel.append(basename)
            if dirname == name:
                break
            name = dirname

        raise util.Abort(_("%s not under root '%s'") % (myname, root))

def walkrepos(path, followsym=False, seen_dirs=None, recurse=False):
    '''yield every hg repository under path, always recursively.
    The recurse flag will only control recursion into repo working dirs'''
    def errhandler(err):
        if err.filename == path:
            raise err
    samestat = getattr(os.path, 'samestat', None)
    if followsym and samestat is not None:
        def adddir(dirlst, dirname):
            match = False
            dirstat = os.stat(dirname)
            for lstdirstat in dirlst:
                if samestat(dirstat, lstdirstat):
                    match = True
                    break
            if not match:
                dirlst.append(dirstat)
            return not match
    else:
        followsym = False

    if (seen_dirs is None) and followsym:
        seen_dirs = []
        adddir(seen_dirs, path)
    for root, dirs, files in os.walk(path, topdown=True, onerror=errhandler):
        dirs.sort()
        if '.hg' in dirs:
            yield root # found a repository
            qroot = os.path.join(root, '.hg', 'patches')
            if os.path.isdir(os.path.join(qroot, '.hg')):
                yield qroot # we have a patch queue repo here
            if recurse:
                # avoid recursing inside the .hg directory
                dirs.remove('.hg')
            else:
                dirs[:] = [] # don't descend further
        elif followsym:
            newdirs = []
            for d in dirs:
                fname = os.path.join(root, d)
                if adddir(seen_dirs, fname):
                    if os.path.islink(fname):
                        for hgname in walkrepos(fname, True, seen_dirs):
                            yield hgname
                    else:
                        newdirs.append(d)
            dirs[:] = newdirs

def osrcpath():
    '''return default os-specific hgrc search path'''
    path = systemrcpath()
    path.extend(userrcpath())
    path = [os.path.normpath(f) for f in path]
    return path

_rcpath = None

def rcpath():
    '''return hgrc search path. if env var HGRCPATH is set, use it.
    for each item in path, if directory, use files ending in .rc,
    else use item.
    make HGRCPATH empty to only look in .hg/hgrc of current repo.
    if no HGRCPATH, use default os-specific path.'''
    global _rcpath
    if _rcpath is None:
        if 'HGRCPATH' in os.environ:
            _rcpath = []
            for p in os.environ['HGRCPATH'].split(os.pathsep):
                if not p:
                    continue
                p = util.expandpath(p)
                if os.path.isdir(p):
                    for f, kind in osutil.listdir(p):
                        if f.endswith('.rc'):
                            _rcpath.append(os.path.join(p, f))
                else:
                    _rcpath.append(p)
        else:
            _rcpath = osrcpath()
    return _rcpath

def revsingle(repo, revspec, default='.'):
    if not revspec:
        return repo[default]

    l = revrange(repo, [revspec])
    if len(l) < 1:
        raise util.Abort(_('empty revision set'))
    return repo[l[-1]]

def revpair(repo, revs):
    if not revs:
        return repo.dirstate.p1(), None

    l = revrange(repo, revs)

    if len(l) == 0:
        if revs:
            raise util.Abort(_('empty revision range'))
        return repo.dirstate.p1(), None

    if len(l) == 1 and len(revs) == 1 and _revrangesep not in revs[0]:
        return repo.lookup(l[0]), None

    return repo.lookup(l[0]), repo.lookup(l[-1])

_revrangesep = ':'

def revrange(repo, revs):
    """Yield revision as strings from a list of revision specifications."""

    def revfix(repo, val, defval):
        if not val and val != 0 and defval is not None:
            return defval
        return repo[val].rev()

    seen, l = set(), []
    for spec in revs:
        if l and not seen:
            seen = set(l)
        # attempt to parse old-style ranges first to deal with
        # things like old-tag which contain query metacharacters
        try:
            if isinstance(spec, int):
                seen.add(spec)
                l.append(spec)
                continue

            if _revrangesep in spec:
                start, end = spec.split(_revrangesep, 1)
                start = revfix(repo, start, 0)
                end = revfix(repo, end, len(repo) - 1)
                if end == nullrev and start <= 0:
                    start = nullrev
                rangeiter = repo.changelog.revs(start, end)
                if not seen and not l:
                    # by far the most common case: revs = ["-1:0"]
                    l = list(rangeiter)
                    # defer syncing seen until next iteration
                    continue
                newrevs = set(rangeiter)
                if seen:
                    newrevs.difference_update(seen)
                    seen.update(newrevs)
                else:
                    seen = newrevs
                l.extend(sorted(newrevs, reverse=start > end))
                continue
            elif spec and spec in repo: # single unquoted rev
                rev = revfix(repo, spec, None)
                if rev in seen:
                    continue
                seen.add(rev)
                l.append(rev)
                continue
        except error.RepoLookupError:
            pass

        # fall through to new-style queries if old-style fails
        m = revset.match(repo.ui, spec)
        dl = [r for r in m(repo, list(repo)) if r not in seen]
        l.extend(dl)
        seen.update(dl)

    return l

def expandpats(pats):
    if not util.expandglobs:
        return list(pats)
    ret = []
    for p in pats:
        kind, name = matchmod._patsplit(p, None)
        if kind is None:
            try:
                globbed = glob.glob(name)
            except re.error:
                globbed = [name]
            if globbed:
                ret.extend(globbed)
                continue
        ret.append(p)
    return ret

def matchandpats(ctx, pats=[], opts={}, globbed=False, default='relpath'):
    if pats == ("",):
        pats = []
    if not globbed and default == 'relpath':
        pats = expandpats(pats or [])

    m = ctx.match(pats, opts.get('include'), opts.get('exclude'),
                         default)
    def badfn(f, msg):
        ctx._repo.ui.warn("%s: %s\n" % (m.rel(f), msg))
    m.bad = badfn
    return m, pats

def match(ctx, pats=[], opts={}, globbed=False, default='relpath'):
    return matchandpats(ctx, pats, opts, globbed, default)[0]

def matchall(repo):
    return matchmod.always(repo.root, repo.getcwd())

def matchfiles(repo, files):
    return matchmod.exact(repo.root, repo.getcwd(), files)

def addremove(repo, pats=[], opts={}, dry_run=None, similarity=None):
    if dry_run is None:
        dry_run = opts.get('dry_run')
    if similarity is None:
        similarity = float(opts.get('similarity') or 0)
    # we'd use status here, except handling of symlinks and ignore is tricky
    added, unknown, deleted, removed = [], [], [], []
    audit_path = pathauditor(repo.root)
    m = match(repo[None], pats, opts)
    rejected = []
    m.bad = lambda x, y: rejected.append(x)

    ctx = repo[None]
    dirstate = repo.dirstate
    walkresults = dirstate.walk(m, sorted(ctx.substate), True, False)
    for abs, st in walkresults.iteritems():
        dstate = dirstate[abs]
        if dstate == '?' and audit_path.check(abs):
            unknown.append(abs)
        elif dstate != 'r' and not st:
            deleted.append(abs)
        # for finding renames
        elif dstate == 'r':
            removed.append(abs)
        elif dstate == 'a':
            added.append(abs)

    unknownset = set(unknown)
    toprint = unknownset.copy()
    toprint.update(deleted)
    for abs in sorted(toprint):
        if repo.ui.verbose or not m.exact(abs):
            rel = m.rel(abs)
            if abs in unknownset:
                status = _('adding %s\n') % ((pats and rel) or abs)
            else:
                status = _('removing %s\n') % ((pats and rel) or abs)
            repo.ui.status(status)

    copies = {}
    if similarity > 0:
        for old, new, score in similar.findrenames(repo,
                added + unknown, removed + deleted, similarity):
            if repo.ui.verbose or not m.exact(old) or not m.exact(new):
                repo.ui.status(_('recording removal of %s as rename to %s '
                                 '(%d%% similar)\n') %
                               (m.rel(old), m.rel(new), score * 100))
            copies[new] = old

    if not dry_run:
        wctx = repo[None]
        wlock = repo.wlock()
        try:
            wctx.forget(deleted)
            wctx.add(unknown)
            for new, old in copies.iteritems():
                wctx.copy(old, new)
        finally:
            wlock.release()

    for f in rejected:
        if f in m.files():
            return 1
    return 0

def dirstatecopy(ui, repo, wctx, src, dst, dryrun=False, cwd=None):
    """Update the dirstate to reflect the intent of copying src to dst. For
    different reasons it might not end with dst being marked as copied from src.
    """
    origsrc = repo.dirstate.copied(src) or src
    if dst == origsrc: # copying back a copy?
        if repo.dirstate[dst] not in 'mn' and not dryrun:
            repo.dirstate.normallookup(dst)
    else:
        if repo.dirstate[origsrc] == 'a' and origsrc == src:
            if not ui.quiet:
                ui.warn(_("%s has not been committed yet, so no copy "
                          "data will be stored for %s.\n")
                        % (repo.pathto(origsrc, cwd), repo.pathto(dst, cwd)))
            if repo.dirstate[dst] in '?r' and not dryrun:
                wctx.add([dst])
        elif not dryrun:
            wctx.copy(origsrc, dst)

def readrequires(opener, supported):
    '''Reads and parses .hg/requires and checks if all entries found
    are in the list of supported features.'''
    requirements = set(opener.read("requires").splitlines())
    missings = []
    for r in requirements:
        if r not in supported:
            if not r or not r[0].isalnum():
                raise error.RequirementError(_(".hg/requires file is corrupt"))
            missings.append(r)
    missings.sort()
    if missings:
        raise error.RequirementError(
            _("unknown repository format: requires features '%s' (upgrade "
              "Mercurial)") % "', '".join(missings))
    return requirements

class filecacheentry(object):
    def __init__(self, path, stat=True):
        self.path = path
        self.cachestat = None
        self._cacheable = None

        if stat:
            self.cachestat = filecacheentry.stat(self.path)

            if self.cachestat:
                self._cacheable = self.cachestat.cacheable()
            else:
                # None means we don't know yet
                self._cacheable = None

    def refresh(self):
        if self.cacheable():
            self.cachestat = filecacheentry.stat(self.path)

    def cacheable(self):
        if self._cacheable is not None:
            return self._cacheable

        # we don't know yet, assume it is for now
        return True

    def changed(self):
        # no point in going further if we can't cache it
        if not self.cacheable():
            return True

        newstat = filecacheentry.stat(self.path)

        # we may not know if it's cacheable yet, check again now
        if newstat and self._cacheable is None:
            self._cacheable = newstat.cacheable()

            # check again
            if not self._cacheable:
                return True

        if self.cachestat != newstat:
            self.cachestat = newstat
            return True
        else:
            return False

    @staticmethod
    def stat(path):
        try:
            return util.cachestat(path)
        except OSError, e:
            if e.errno != errno.ENOENT:
                raise

class filecache(object):
    '''A property like decorator that tracks a file under .hg/ for updates.

    Records stat info when called in _filecache.

    On subsequent calls, compares old stat info with new info, and recreates
    the object when needed, updating the new stat info in _filecache.

    Mercurial either atomic renames or appends for files under .hg,
    so to ensure the cache is reliable we need the filesystem to be able
    to tell us if a file has been replaced. If it can't, we fallback to
    recreating the object on every call (essentially the same behaviour as
    propertycache).'''
    def __init__(self, path):
        self.path = path

    def join(self, obj, fname):
        """Used to compute the runtime path of the cached file.

        Users should subclass filecache and provide their own version of this
        function to call the appropriate join function on 'obj' (an instance
        of the class that its member function was decorated).
        """
        return obj.join(fname)

    def __call__(self, func):
        self.func = func
        self.name = func.__name__
        return self

    def __get__(self, obj, type=None):
        # do we need to check if the file changed?
        if self.name in obj.__dict__:
            assert self.name in obj._filecache, self.name
            return obj.__dict__[self.name]

        entry = obj._filecache.get(self.name)

        if entry:
            if entry.changed():
                entry.obj = self.func(obj)
        else:
            path = self.join(obj, self.path)

            # We stat -before- creating the object so our cache doesn't lie if
            # a writer modified between the time we read and stat
            entry = filecacheentry(path)
            entry.obj = self.func(obj)

            obj._filecache[self.name] = entry

        obj.__dict__[self.name] = entry.obj
        return entry.obj

    def __set__(self, obj, value):
        if self.name not in obj._filecache:
            # we add an entry for the missing value because X in __dict__
            # implies X in _filecache
            ce = filecacheentry(self.join(obj, self.path), False)
            obj._filecache[self.name] = ce
        else:
            ce = obj._filecache[self.name]

        ce.obj = value # update cached copy
        obj.__dict__[self.name] = value # update copy returned by obj.x

    def __delete__(self, obj):
        try:
            del obj.__dict__[self.name]
        except KeyError:
            raise AttributeError(self.name)

class dirs(object):
    '''a multiset of directory names from a dirstate or manifest'''

    def __init__(self, map, skip=None):
        self._dirs = {}
        addpath = self.addpath
        if util.safehasattr(map, 'iteritems') and skip is not None:
            for f, s in map.iteritems():
                if s[0] != skip:
                    addpath(f)
        else:
            for f in map:
                addpath(f)

    def addpath(self, path):
        dirs = self._dirs
        for base in finddirs(path):
            if base in dirs:
                dirs[base] += 1
                return
            dirs[base] = 1

    def delpath(self, path):
        dirs = self._dirs
        for base in finddirs(path):
            if dirs[base] > 1:
                dirs[base] -= 1
                return
            del dirs[base]

    def __iter__(self):
        return self._dirs.iterkeys()

    def __contains__(self, d):
        return d in self._dirs

if util.safehasattr(parsers, 'dirs'):
    dirs = parsers.dirs

def finddirs(path):
    pos = path.rfind('/')
    while pos != -1:
        yield path[:pos]
        pos = path.rfind('/', 0, pos)
