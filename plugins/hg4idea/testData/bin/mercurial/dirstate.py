# dirstate.py - working directory tracking for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import nullid
from i18n import _
import util, ignore, osutil, parsers
import struct, os, stat, errno
import cStringIO

_unknown = ('?', 0, 0, 0)
_format = ">cllll"
propertycache = util.propertycache

def _finddirs(path):
    pos = path.rfind('/')
    while pos != -1:
        yield path[:pos]
        pos = path.rfind('/', 0, pos)

def _incdirs(dirs, path):
    for base in _finddirs(path):
        if base in dirs:
            dirs[base] += 1
            return
        dirs[base] = 1

def _decdirs(dirs, path):
    for base in _finddirs(path):
        if dirs[base] > 1:
            dirs[base] -= 1
            return
        del dirs[base]

class dirstate(object):

    def __init__(self, opener, ui, root):
        '''Create a new dirstate object.

        opener is an open()-like callable that can be used to open the
        dirstate file; root is the root of the directory tracked by
        the dirstate.
        '''
        self._opener = opener
        self._root = root
        self._rootdir = os.path.join(root, '')
        self._dirty = False
        self._dirtypl = False
        self._ui = ui

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
        for name in self._map:
            f[os.path.normcase(name)] = name
        return f

    @propertycache
    def _branch(self):
        try:
            return self._opener("branch").read().strip() or "default"
        except IOError:
            return "default"

    @propertycache
    def _pl(self):
        try:
            st = self._opener("dirstate").read(40)
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
        dirs = {}
        for f, s in self._map.iteritems():
            if s[0] != 'r':
                _incdirs(dirs, f)
        return dirs

    @propertycache
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

    def flagfunc(self, fallback):
        if self._checklink:
            if self._checkexec:
                def f(x):
                    p = self._join(x)
                    if os.path.islink(p):
                        return 'l'
                    if util.is_exec(p):
                        return 'x'
                    return ''
                return f
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
                if util.is_exec(self._join(x)):
                    return 'x'
                return ''
            return f
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
            return util.normpath(path)
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

    def parents(self):
        return self._pl

    def branch(self):
        return self._branch

    def setparents(self, p1, p2=nullid):
        self._dirty = self._dirtypl = True
        self._pl = p1, p2

    def setbranch(self, branch):
        if branch in ['tip', '.', 'null']:
            raise util.Abort(_('the name \'%s\' is reserved') % branch)
        self._branch = branch
        self._opener("branch", "w").write(branch + '\n')

    def _read(self):
        self._map = {}
        self._copymap = {}
        try:
            st = self._opener("dirstate").read()
        except IOError, err:
            if err.errno != errno.ENOENT:
                raise
            return
        if not st:
            return

        p = parsers.parse_dirstate(self._map, self._copymap, st)
        if not self._dirtypl:
            self._pl = p

    def invalidate(self):
        for a in "_map _copymap _foldmap _branch _pl _dirs _ignore".split():
            if a in self.__dict__:
                delattr(self, a)
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
            _decdirs(self._dirs, f)

    def _addpath(self, f, check=False):
        oldstate = self[f]
        if check or oldstate == "r":
            if '\r' in f or '\n' in f:
                raise util.Abort(
                    _("'\\n' and '\\r' disallowed in filenames: %r") % f)
            if f in self._dirs:
                raise util.Abort(_('directory %r already in dirstate') % f)
            # shadows
            for d in _finddirs(f):
                if d in self._dirs:
                    break
                if d in self._map and self[d] != 'r':
                    raise util.Abort(
                        _('file %r in dirstate clashes with %r') % (d, f))
        if oldstate in "?r" and "_dirs" in self.__dict__:
            _incdirs(self._dirs, f)

    def normal(self, f):
        '''Mark a file normal and clean.'''
        self._dirty = True
        self._addpath(f)
        s = os.lstat(self._join(f))
        self._map[f] = ('n', s.st_mode, s.st_size, int(s.st_mtime))
        if f in self._copymap:
            del self._copymap[f]

    def normallookup(self, f):
        '''Mark a file normal, but possibly dirty.'''
        if self._pl[1] != nullid and f in self._map:
            # if there is a merge going on and the file was either
            # in state 'm' or dirty before being removed, restore that state.
            entry = self._map[f]
            if entry[0] == 'r' and entry[2] in (-1, -2):
                source = self._copymap.get(f)
                if entry[2] == -1:
                    self.merge(f)
                elif entry[2] == -2:
                    self.normaldirty(f)
                if source:
                    self.copy(source, f)
                return
            if entry[0] == 'm' or entry[0] == 'n' and entry[2] == -2:
                return
        self._dirty = True
        self._addpath(f)
        self._map[f] = ('n', 0, -1, -1)
        if f in self._copymap:
            del self._copymap[f]

    def normaldirty(self, f):
        '''Mark a file normal, but dirty.'''
        self._dirty = True
        self._addpath(f)
        self._map[f] = ('n', 0, -2, -1)
        if f in self._copymap:
            del self._copymap[f]

    def add(self, f):
        '''Mark a file added.'''
        self._dirty = True
        self._addpath(f, True)
        self._map[f] = ('a', 0, -1, -1)
        if f in self._copymap:
            del self._copymap[f]

    def remove(self, f):
        '''Mark a file removed.'''
        self._dirty = True
        self._droppath(f)
        size = 0
        if self._pl[1] != nullid and f in self._map:
            entry = self._map[f]
            if entry[0] == 'm':
                size = -1
            elif entry[0] == 'n' and entry[2] == -2:
                size = -2
        self._map[f] = ('r', 0, size, 0)
        if size == 0 and f in self._copymap:
            del self._copymap[f]

    def merge(self, f):
        '''Mark a file merged.'''
        self._dirty = True
        s = os.lstat(self._join(f))
        self._addpath(f)
        self._map[f] = ('m', s.st_mode, s.st_size, int(s.st_mtime))
        if f in self._copymap:
            del self._copymap[f]

    def forget(self, f):
        '''Forget a file.'''
        self._dirty = True
        try:
            self._droppath(f)
            del self._map[f]
        except KeyError:
            self._ui.warn(_("not in dirstate: %s\n") % f)

    def _normalize(self, path, knownpath):
        norm_path = os.path.normcase(path)
        fold_path = self._foldmap.get(norm_path, None)
        if fold_path is None:
            if knownpath or not os.path.exists(os.path.join(self._root, path)):
                fold_path = path
            else:
                fold_path = self._foldmap.setdefault(norm_path,
                                util.fspath(path, self._root))
        return fold_path

    def clear(self):
        self._map = {}
        if "_dirs" in self.__dict__:
            delattr(self, "_dirs")
        self._copymap = {}
        self._pl = [nullid, nullid]
        self._dirty = True

    def rebuild(self, parent, files):
        self.clear()
        for f in files:
            if 'x' in files.flags(f):
                self._map[f] = ('n', 0777, -1, 0)
            else:
                self._map[f] = ('n', 0666, -1, 0)
        self._pl = (parent, nullid)
        self._dirty = True

    def write(self):
        if not self._dirty:
            return
        st = self._opener("dirstate", "w", atomictemp=True)

        # use the modification time of the newly created temporary file as the
        # filesystem's notion of 'now'
        now = int(util.fstat(st).st_mtime)

        cs = cStringIO.StringIO()
        copymap = self._copymap
        pack = struct.pack
        write = cs.write
        write("".join(self._pl))
        for f, e in self._map.iteritems():
            if e[0] == 'n' and e[3] == now:
                # The file was last modified "simultaneously" with the current
                # write to dirstate (i.e. within the same second for file-
                # systems with a granularity of 1 sec). This commonly happens
                # for at least a couple of files on 'update'.
                # The user could change the file without changing its size
                # within the same second. Invalidate the file's stat data in
                # dirstate, forcing future 'status' calls to compare the
                # contents of the file. This prevents mistakenly treating such
                # files as clean.
                e = (e[0], 0, -1, -1)   # mark entry as 'unset'
                self._map[f] = e

            if f in copymap:
                f = "%s\0%s" % (f, copymap[f])
            e = pack(_format, e[0], e[1], e[2], e[3], len(f))
            write(e)
            write(f)
        st.write(cs.getvalue())
        st.rename()
        self._dirty = self._dirtypl = False

    def _dirignore(self, f):
        if f == '.':
            return False
        if self._ignore(f):
            return True
        for p in _finddirs(f):
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

        if self._checkcase:
            normalize = self._normalize
        else:
            normalize = lambda x, y: x

        exact = skipstep3 = False
        if matchfn == match.exact: # match.exact
            exact = True
            dirignore = util.always # skip step 2
        elif match.files() and not match.anypats(): # match.match, no patterns
            skipstep3 = True

        files = set(match.files())
        if not files or '.' in files:
            files = ['']
        results = dict.fromkeys(subrepos)
        results['.hg'] = None

        # step 1: find all explicit files
        for ff in sorted(files):
            nf = normalize(normpath(ff), False)
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
                if inst.errno == errno.EACCES:
                    fwarn(nd, inst.strerror)
                    continue
                raise
            for f, kind, st in entries:
                nf = normalize(nd and (nd + "/" + f) or f, True)
                if nf not in results:
                    if kind == dirkind:
                        if not ignore(nf):
                            match.dir(nf)
                            wadd(nf)
                        if nf in dmap and matchfn(nf):
                            results[nf] = None
                    elif kind == regkind or kind == lnkkind:
                        if nf in dmap:
                            if matchfn(nf):
                                results[nf] = st
                        elif matchfn(nf) and not ignore(nf):
                            results[nf] = st
                    elif nf in dmap and matchfn(nf):
                        results[nf] = None

        # step 3: report unseen items in the dmap hash
        if not skipstep3 and not exact:
            visit = sorted([f for f in dmap if f not in results and matchfn(f)])
            for nf, st in zip(visit, util.statfiles([join(i) for i in visit])):
                if not st is None and not getkind(st.st_mode) in (regkind, lnkkind):
                    st = None
                results[nf] = st
        for s in subrepos:
            del results[s]
        del results['.hg']
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

        for fn, st in self.walk(match, subrepos, listunknown,
                                listignored).iteritems():
            if fn not in dmap:
                if (listignored or match.exact(fn)) and self._dirignore(fn):
                    if listignored:
                        iadd(fn)
                elif listunknown:
                    uadd(fn)
                continue

            state, mode, size, time = dmap[fn]

            if not st and state in "nma":
                dadd(fn)
            elif state == 'n':
                if (size >= 0 and
                    (size != st.st_size
                     or ((mode ^ st.st_mode) & 0100 and self._checkexec))
                    or size == -2
                    or fn in self._copymap):
                    madd(fn)
                elif time != int(st.st_mtime):
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
