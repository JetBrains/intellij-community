# dirstate.py - working directory tracking for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
import errno

from node import nullid
from i18n import _
import scmutil, util, ignore, osutil, parsers, encoding
import os, stat, errno, gc

propertycache = util.propertycache
filecache = scmutil.filecache
_rangemask = 0x7fffffff

class repocache(filecache):
    """filecache for files in .hg/"""
    def join(self, obj, fname):
        return obj._opener.join(fname)

class rootcache(filecache):
    """filecache for files in the repository root"""
    def join(self, obj, fname):
        return obj._join(fname)

class dirstate(object):

    def __init__(self, opener, ui, root, validate):
        '''Create a new dirstate object.

        opener is an open()-like callable that can be used to open the
        dirstate file; root is the root of the directory tracked by
        the dirstate.
        '''
        self._opener = opener
        self._validate = validate
        self._root = root
        self._rootdir = os.path.join(root, '')
        self._dirty = False
        self._dirtypl = False
        self._lastnormaltime = 0
        self._ui = ui
        self._filecache = {}

    @propertycache
    def _map(self):
        '''Return the dirstate contents as a map from filename to
        (state, mode, size, time).'''
        self._read()
        return self._map

    @propertycache
    def _copymap(self):
        self._read()
        return self._copymap

    @propertycache
    def _foldmap(self):
        f = {}
        for name, s in self._map.iteritems():
            if s[0] != 'r':
                f[util.normcase(name)] = name
        for name in self._dirs:
            f[util.normcase(name)] = name
        f['.'] = '.' # prevents useless util.fspath() invocation
        return f

    @repocache('branch')
    def _branch(self):
        try:
            return self._opener.read("branch").strip() or "default"
        except IOError, inst:
            if inst.errno != errno.ENOENT:
                raise
            return "default"

    @propertycache
    def _pl(self):
        try:
            fp = self._opener("dirstate")
            st = fp.read(40)
            fp.close()
            l = len(st)
            if l == 40:
                return st[:20], st[20:40]
            elif l > 0 and l < 40:
                raise util.Abort(_('working directory state appears damaged!'))
        except IOError, err:
            if err.errno != errno.ENOENT:
                raise
        return [nullid, nullid]

    @propertycache
    def _dirs(self):
        return scmutil.dirs(self._map, 'r')

    def dirs(self):
        return self._dirs

    @rootcache('.hgignore')
    def _ignore(self):
        files = [self._join('.hgignore')]
        for name, path in self._ui.configitems("ui"):
            if name == 'ignore' or name.startswith('ignore.'):
                files.append(util.expandpath(path))
        return ignore.ignore(self._root, files, self._ui.warn)

    @propertycache
    def _slash(self):
        return self._ui.configbool('ui', 'slash') and os.sep != '/'

    @propertycache
    def _checklink(self):
        return util.checklink(self._root)

    @propertycache
    def _checkexec(self):
        return util.checkexec(self._root)

    @propertycache
    def _checkcase(self):
        return not util.checkcase(self._join('.hg'))

    def _join(self, f):
        # much faster than os.path.join()
        # it's safe because f is always a relative path
        return self._rootdir + f

    def flagfunc(self, buildfallback):
        if self._checklink and self._checkexec:
            def f(x):
                try:
                    st = os.lstat(self._join(x))
                    if util.statislink(st):
                        return 'l'
                    if util.statisexec(st):
                        return 'x'
                except OSError:
                    pass
                return ''
            return f

        fallback = buildfallback()
        if self._checklink:
            def f(x):
                if os.path.islink(self._join(x)):
                    return 'l'
                if 'x' in fallback(x):
                    return 'x'
                return ''
            return f
        if self._checkexec:
            def f(x):
                if 'l' in fallback(x):
                    return 'l'
                if util.isexec(self._join(x)):
                    return 'x'
                return ''
            return f
        else:
            return fallback

    def getcwd(self):
        cwd = os.getcwd()
        if cwd == self._root:
            return ''
        # self._root ends with a path separator if self._root is '/' or 'C:\'
        rootsep = self._root
        if not util.endswithsep(rootsep):
            rootsep += os.sep
        if cwd.startswith(rootsep):
            return cwd[len(rootsep):]
        else:
            # we're outside the repo. return an absolute path.
            return cwd

    def pathto(self, f, cwd=None):
        if cwd is None:
            cwd = self.getcwd()
        path = util.pathto(self._root, cwd, f)
        if self._slash:
            return util.pconvert(path)
        return path

    def __getitem__(self, key):
        '''Return the current state of key (a filename) in the dirstate.

        States are:
          n  normal
          m  needs merging
          r  marked for removal
          a  marked for addition
          ?  not tracked
        '''
        return self._map.get(key, ("?",))[0]

    def __contains__(self, key):
        return key in self._map

    def __iter__(self):
        for x in sorted(self._map):
            yield x

    def iteritems(self):
        return self._map.iteritems()

    def parents(self):
        return [self._validate(p) for p in self._pl]

    def p1(self):
        return self._validate(self._pl[0])

    def p2(self):
        return self._validate(self._pl[1])

    def branch(self):
        return encoding.tolocal(self._branch)

    def setparents(self, p1, p2=nullid):
        """Set dirstate parents to p1 and p2.

        When moving from two parents to one, 'm' merged entries a
        adjusted to normal and previous copy records discarded and
        returned by the call.

        See localrepo.setparents()
        """
        self._dirty = self._dirtypl = True
        oldp2 = self._pl[1]
        self._pl = p1, p2
        copies = {}
        if oldp2 != nullid and p2 == nullid:
            # Discard 'm' markers when moving away from a merge state
            for f, s in self._map.iteritems():
                if s[0] == 'm':
                    if f in self._copymap:
                        copies[f] = self._copymap[f]
                    self.normallookup(f)
        return copies

    def setbranch(self, branch):
        self._branch = encoding.fromlocal(branch)
        f = self._opener('branch', 'w', atomictemp=True)
        try:
            f.write(self._branch + '\n')
            f.close()

            # make sure filecache has the correct stat info for _branch after
            # replacing the underlying file
            ce = self._filecache['_branch']
            if ce:
                ce.refresh()
        except: # re-raises
            f.discard()
            raise

    def _read(self):
        self._map = {}
        self._copymap = {}
        try:
            st = self._opener.read("dirstate")
        except IOError, err:
            if err.errno != errno.ENOENT:
                raise
            return
        if not st:
            return

        # Python's garbage collector triggers a GC each time a certain number
        # of container objects (the number being defined by
        # gc.get_threshold()) are allocated. parse_dirstate creates a tuple
        # for each file in the dirstate. The C version then immediately marks
        # them as not to be tracked by the collector. However, this has no
        # effect on when GCs are triggered, only on what objects the GC looks
        # into. This means that O(number of files) GCs are unavoidable.
        # Depending on when in the process's lifetime the dirstate is parsed,
        # this can get very expensive. As a workaround, disable GC while
        # parsing the dirstate.
        gcenabled = gc.isenabled()
        gc.disable()
        try:
            p = parsers.parse_dirstate(self._map, self._copymap, st)
        finally:
            if gcenabled:
                gc.enable()
        if not self._dirtypl:
            self._pl = p

    def invalidate(self):
        for a in ("_map", "_copymap", "_foldmap", "_branch", "_pl", "_dirs",
                "_ignore"):
            if a in self.__dict__:
                delattr(self, a)
        self._lastnormaltime = 0
        self._dirty = False

    def copy(self, source, dest):
        """Mark dest as a copy of source. Unmark dest if source is None."""
        if source == dest:
            return
        self._dirty = True
        if source is not None:
            self._copymap[dest] = source
        elif dest in self._copymap:
            del self._copymap[dest]

    def copied(self, file):
        return self._copymap.get(file, None)

    def copies(self):
        return self._copymap

    def _droppath(self, f):
        if self[f] not in "?r" and "_dirs" in self.__dict__:
            self._dirs.delpath(f)

    def _addpath(self, f, state, mode, size, mtime):
        oldstate = self[f]
        if state == 'a' or oldstate == 'r':
            scmutil.checkfilename(f)
            if f in self._dirs:
                raise util.Abort(_('directory %r already in dirstate') % f)
            # shadows
            for d in scmutil.finddirs(f):
                if d in self._dirs:
                    break
                if d in self._map and self[d] != 'r':
                    raise util.Abort(
                        _('file %r in dirstate clashes with %r') % (d, f))
        if oldstate in "?r" and "_dirs" in self.__dict__:
            self._dirs.addpath(f)
        self._dirty = True
        self._map[f] = (state, mode, size, mtime)

    def normal(self, f):
        '''Mark a file normal and clean.'''
        s = os.lstat(self._join(f))
        mtime = int(s.st_mtime)
        self._addpath(f, 'n', s.st_mode,
                      s.st_size & _rangemask, mtime & _rangemask)
        if f in self._copymap:
            del self._copymap[f]
        if mtime > self._lastnormaltime:
            # Remember the most recent modification timeslot for status(),
            # to make sure we won't miss future size-preserving file content
            # modifications that happen within the same timeslot.
            self._lastnormaltime = mtime

    def normallookup(self, f):
        '''Mark a file normal, but possibly dirty.'''
        if self._pl[1] != nullid and f in self._map:
            # if there is a merge going on and the file was either
            # in state 'm' (-1) or coming from other parent (-2) before
            # being removed, restore that state.
            entry = self._map[f]
            if entry[0] == 'r' and entry[2] in (-1, -2):
                source = self._copymap.get(f)
                if entry[2] == -1:
                    self.merge(f)
                elif entry[2] == -2:
                    self.otherparent(f)
                if source:
                    self.copy(source, f)
                return
            if entry[0] == 'm' or entry[0] == 'n' and entry[2] == -2:
                return
        self._addpath(f, 'n', 0, -1, -1)
        if f in self._copymap:
            del self._copymap[f]

    def otherparent(self, f):
        '''Mark as coming from the other parent, always dirty.'''
        if self._pl[1] == nullid:
            raise util.Abort(_("setting %r to other parent "
                               "only allowed in merges") % f)
        self._addpath(f, 'n', 0, -2, -1)
        if f in self._copymap:
            del self._copymap[f]

    def add(self, f):
        '''Mark a file added.'''
        self._addpath(f, 'a', 0, -1, -1)
        if f in self._copymap:
            del self._copymap[f]

    def remove(self, f):
        '''Mark a file removed.'''
        self._dirty = True
        self._droppath(f)
        size = 0
        if self._pl[1] != nullid and f in self._map:
            # backup the previous state
            entry = self._map[f]
            if entry[0] == 'm': # merge
                size = -1
            elif entry[0] == 'n' and entry[2] == -2: # other parent
                size = -2
        self._map[f] = ('r', 0, size, 0)
        if size == 0 and f in self._copymap:
            del self._copymap[f]

    def merge(self, f):
        '''Mark a file merged.'''
        if self._pl[1] == nullid:
            return self.normallookup(f)
        s = os.lstat(self._join(f))
        self._addpath(f, 'm', s.st_mode,
                      s.st_size & _rangemask, int(s.st_mtime) & _rangemask)
        if f in self._copymap:
            del self._copymap[f]

    def drop(self, f):
        '''Drop a file from the dirstate'''
        if f in self._map:
            self._dirty = True
            self._droppath(f)
            del self._map[f]

    def _normalize(self, path, isknown, ignoremissing=False, exists=None):
        normed = util.normcase(path)
        folded = self._foldmap.get(normed, None)
        if folded is None:
            if isknown:
                folded = path
            else:
                if exists is None:
                    exists = os.path.lexists(os.path.join(self._root, path))
                if not exists:
                    # Maybe a path component exists
                    if not ignoremissing and '/' in path:
                        d, f = path.rsplit('/', 1)
                        d = self._normalize(d, isknown, ignoremissing, None)
                        folded = d + "/" + f
                    else:
                        # No path components, preserve original case
                        folded = path
                else:
                    # recursively normalize leading directory components
                    # against dirstate
                    if '/' in normed:
                        d, f = normed.rsplit('/', 1)
                        d = self._normalize(d, isknown, ignoremissing, True)
                        r = self._root + "/" + d
                        folded = d + "/" + util.fspath(f, r)
                    else:
                        folded = util.fspath(normed, self._root)
                    self._foldmap[normed] = folded

        return folded

    def normalize(self, path, isknown=False, ignoremissing=False):
        '''
        normalize the case of a pathname when on a casefolding filesystem

        isknown specifies whether the filename came from walking the
        disk, to avoid extra filesystem access.

        If ignoremissing is True, missing path are returned
        unchanged. Otherwise, we try harder to normalize possibly
        existing path components.

        The normalized case is determined based on the following precedence:

        - version of name already stored in the dirstate
        - version of name stored on disk
        - version provided via command arguments
        '''

        if self._checkcase:
            return self._normalize(path, isknown, ignoremissing)
        return path

    def clear(self):
        self._map = {}
        if "_dirs" in self.__dict__:
            delattr(self, "_dirs")
        self._copymap = {}
        self._pl = [nullid, nullid]
        self._lastnormaltime = 0
        self._dirty = True

    def rebuild(self, parent, allfiles, changedfiles=None):
        changedfiles = changedfiles or allfiles
        oldmap = self._map
        self.clear()
        for f in allfiles:
            if f not in changedfiles:
                self._map[f] = oldmap[f]
            else:
                if 'x' in allfiles.flags(f):
                    self._map[f] = ('n', 0777, -1, 0)
                else:
                    self._map[f] = ('n', 0666, -1, 0)
        self._pl = (parent, nullid)
        self._dirty = True

    def write(self):
        if not self._dirty:
            return
        st = self._opener("dirstate", "w", atomictemp=True)

        def finish(s):
            st.write(s)
            st.close()
            self._lastnormaltime = 0
            self._dirty = self._dirtypl = False

        # use the modification time of the newly created temporary file as the
        # filesystem's notion of 'now'
        now = util.fstat(st).st_mtime
        finish(parsers.pack_dirstate(self._map, self._copymap, self._pl, now))

    def _dirignore(self, f):
        if f == '.':
            return False
        if self._ignore(f):
            return True
        for p in scmutil.finddirs(f):
            if self._ignore(p):
                return True
        return False

    def walk(self, match, subrepos, unknown, ignored):
        '''
        Walk recursively through the directory tree, finding all files
        matched by match.

        Return a dict mapping filename to stat-like object (either
        mercurial.osutil.stat instance or return value of os.stat()).
        '''

        def fwarn(f, msg):
            self._ui.warn('%s: %s\n' % (self.pathto(f), msg))
            return False

        def badtype(mode):
            kind = _('unknown')
            if stat.S_ISCHR(mode):
                kind = _('character device')
            elif stat.S_ISBLK(mode):
                kind = _('block device')
            elif stat.S_ISFIFO(mode):
                kind = _('fifo')
            elif stat.S_ISSOCK(mode):
                kind = _('socket')
            elif stat.S_ISDIR(mode):
                kind = _('directory')
            return _('unsupported file type (type is %s)') % kind

        ignore = self._ignore
        dirignore = self._dirignore
        if ignored:
            ignore = util.never
            dirignore = util.never
        elif not unknown:
            # if unknown and ignored are False, skip step 2
            ignore = util.always
            dirignore = util.always

        matchfn = match.matchfn
        matchalways = match.always()
        badfn = match.bad
        dmap = self._map
        normpath = util.normpath
        listdir = osutil.listdir
        lstat = os.lstat
        getkind = stat.S_IFMT
        dirkind = stat.S_IFDIR
        regkind = stat.S_IFREG
        lnkkind = stat.S_IFLNK
        join = self._join
        work = []
        wadd = work.append

        exact = skipstep3 = False
        if matchfn == match.exact: # match.exact
            exact = True
            dirignore = util.always # skip step 2
        elif match.files() and not match.anypats(): # match.match, no patterns
            skipstep3 = True

        if not exact and self._checkcase:
            normalize = self._normalize
            skipstep3 = False
        else:
            normalize = None

        files = sorted(match.files())
        subrepos.sort()
        i, j = 0, 0
        while i < len(files) and j < len(subrepos):
            subpath = subrepos[j] + "/"
            if files[i] < subpath:
                i += 1
                continue
            while i < len(files) and files[i].startswith(subpath):
                del files[i]
            j += 1

        if not files or '.' in files:
            files = ['']
        results = dict.fromkeys(subrepos)
        results['.hg'] = None

        # step 1: find all explicit files
        for ff in files:
            if normalize:
                nf = normalize(normpath(ff), False, True)
            else:
                nf = normpath(ff)
            if nf in results:
                continue

            try:
                st = lstat(join(nf))
                kind = getkind(st.st_mode)
                if kind == dirkind:
                    skipstep3 = False
                    if nf in dmap:
                        #file deleted on disk but still in dirstate
                        results[nf] = None
                    match.dir(nf)
                    if not dirignore(nf):
                        wadd(nf)
                elif kind == regkind or kind == lnkkind:
                    results[nf] = st
                else:
                    badfn(ff, badtype(kind))
                    if nf in dmap:
                        results[nf] = None
            except OSError, inst:
                if nf in dmap: # does it exactly match a file?
                    results[nf] = None
                else: # does it match a directory?
                    prefix = nf + "/"
                    for fn in dmap:
                        if fn.startswith(prefix):
                            match.dir(nf)
                            skipstep3 = False
                            break
                    else:
                        badfn(ff, inst.strerror)

        # step 2: visit subdirectories
        while work:
            nd = work.pop()
            skip = None
            if nd == '.':
                nd = ''
            else:
                skip = '.hg'
            try:
                entries = listdir(join(nd), stat=True, skip=skip)
            except OSError, inst:
                if inst.errno in (errno.EACCES, errno.ENOENT):
                    fwarn(nd, inst.strerror)
                    continue
                raise
            for f, kind, st in entries:
                if normalize:
                    nf = normalize(nd and (nd + "/" + f) or f, True, True)
                else:
                    nf = nd and (nd + "/" + f) or f
                if nf not in results:
                    if kind == dirkind:
                        if not ignore(nf):
                            match.dir(nf)
                            wadd(nf)
                        if nf in dmap and (matchalways or matchfn(nf)):
                            results[nf] = None
                    elif kind == regkind or kind == lnkkind:
                        if nf in dmap:
                            if matchalways or matchfn(nf):
                                results[nf] = st
                        elif (matchalways or matchfn(nf)) and not ignore(nf):
                            results[nf] = st
                    elif nf in dmap and (matchalways or matchfn(nf)):
                        results[nf] = None

        for s in subrepos:
            del results[s]
        del results['.hg']

        # step 3: report unseen items in the dmap hash
        if not skipstep3 and not exact:
            if not results and matchalways:
                visit = dmap.keys()
            else:
                visit = [f for f in dmap if f not in results and matchfn(f)]
            visit.sort()

            if unknown:
                # unknown == True means we walked the full directory tree above.
                # So if a file is not seen it was either a) not matching matchfn
                # b) ignored, c) missing, or d) under a symlink directory.
                audit_path = scmutil.pathauditor(self._root)

                for nf in iter(visit):
                    # Report ignored items in the dmap as long as they are not
                    # under a symlink directory.
                    if audit_path.check(nf):
                        try:
                            results[nf] = lstat(join(nf))
                        except OSError:
                            # file doesn't exist
                            results[nf] = None
                    else:
                        # It's either missing or under a symlink directory
                        results[nf] = None
            else:
                # We may not have walked the full directory tree above,
                # so stat everything we missed.
                nf = iter(visit).next
                for st in util.statfiles([join(i) for i in visit]):
                    results[nf()] = st
        return results

    def status(self, match, subrepos, ignored, clean, unknown):
        '''Determine the status of the working copy relative to the
        dirstate and return a tuple of lists (unsure, modified, added,
        removed, deleted, unknown, ignored, clean), where:

          unsure:
            files that might have been modified since the dirstate was
            written, but need to be read to be sure (size is the same
            but mtime differs)
          modified:
            files that have definitely been modified since the dirstate
            was written (different size or mode)
          added:
            files that have been explicitly added with hg add
          removed:
            files that have been explicitly removed with hg remove
          deleted:
            files that have been deleted through other means ("missing")
          unknown:
            files not in the dirstate that are not ignored
          ignored:
            files not in the dirstate that are ignored
            (by _dirignore())
          clean:
            files that have definitely not been modified since the
            dirstate was written
        '''
        listignored, listclean, listunknown = ignored, clean, unknown
        lookup, modified, added, unknown, ignored = [], [], [], [], []
        removed, deleted, clean = [], [], []

        dmap = self._map
        ladd = lookup.append            # aka "unsure"
        madd = modified.append
        aadd = added.append
        uadd = unknown.append
        iadd = ignored.append
        radd = removed.append
        dadd = deleted.append
        cadd = clean.append
        mexact = match.exact
        dirignore = self._dirignore
        checkexec = self._checkexec
        checklink = self._checklink
        copymap = self._copymap
        lastnormaltime = self._lastnormaltime

        lnkkind = stat.S_IFLNK

        for fn, st in self.walk(match, subrepos, listunknown,
                                listignored).iteritems():
            if fn not in dmap:
                if (listignored or mexact(fn)) and dirignore(fn):
                    if listignored:
                        iadd(fn)
                elif listunknown:
                    uadd(fn)
                continue

            state, mode, size, time = dmap[fn]

            if not st and state in "nma":
                dadd(fn)
            elif state == 'n':
                # The "mode & lnkkind != lnkkind or self._checklink"
                # lines are an expansion of "islink => checklink"
                # where islink means "is this a link?" and checklink
                # means "can we check links?".
                mtime = int(st.st_mtime)
                if (size >= 0 and
                    ((size != st.st_size and size != st.st_size & _rangemask)
                     or ((mode ^ st.st_mode) & 0100 and checkexec))
                    and (mode & lnkkind != lnkkind or checklink)
                    or size == -2 # other parent
                    or fn in copymap):
                    madd(fn)
                elif ((time != mtime and time != mtime & _rangemask)
                      and (mode & lnkkind != lnkkind or checklink)):
                    ladd(fn)
                elif mtime == lastnormaltime:
                    # fn may have been changed in the same timeslot without
                    # changing its size. This can happen if we quickly do
                    # multiple commits in a single transaction.
                    # Force lookup, so we don't miss such a racy file change.
                    ladd(fn)
                elif listclean:
                    cadd(fn)
            elif state == 'm':
                madd(fn)
            elif state == 'a':
                aadd(fn)
            elif state == 'r':
                radd(fn)

        return (lookup, modified, added, removed, deleted, unknown, ignored,
                clean)
