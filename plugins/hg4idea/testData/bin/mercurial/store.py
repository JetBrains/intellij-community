# store.py - repository store handling for Mercurial
#
# Copyright 2008 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import scmutil, util, parsers
import os, stat, errno

_sha = util.sha1

# This avoids a collision between a file named foo and a dir named
# foo.i or foo.d
def _encodedir(path):
    '''
    >>> _encodedir('data/foo.i')
    'data/foo.i'
    >>> _encodedir('data/foo.i/bla.i')
    'data/foo.i.hg/bla.i'
    >>> _encodedir('data/foo.i.hg/bla.i')
    'data/foo.i.hg.hg/bla.i'
    >>> _encodedir('data/foo.i\\ndata/foo.i/bla.i\\ndata/foo.i.hg/bla.i\\n')
    'data/foo.i\\ndata/foo.i.hg/bla.i\\ndata/foo.i.hg.hg/bla.i\\n'
    '''
    return (path
            .replace(".hg/", ".hg.hg/")
            .replace(".i/", ".i.hg/")
            .replace(".d/", ".d.hg/"))

encodedir = getattr(parsers, 'encodedir', _encodedir)

def decodedir(path):
    '''
    >>> decodedir('data/foo.i')
    'data/foo.i'
    >>> decodedir('data/foo.i.hg/bla.i')
    'data/foo.i/bla.i'
    >>> decodedir('data/foo.i.hg.hg/bla.i')
    'data/foo.i.hg/bla.i'
    '''
    if ".hg/" not in path:
        return path
    return (path
            .replace(".d.hg/", ".d/")
            .replace(".i.hg/", ".i/")
            .replace(".hg.hg/", ".hg/"))

def _buildencodefun():
    '''
    >>> enc, dec = _buildencodefun()

    >>> enc('nothing/special.txt')
    'nothing/special.txt'
    >>> dec('nothing/special.txt')
    'nothing/special.txt'

    >>> enc('HELLO')
    '_h_e_l_l_o'
    >>> dec('_h_e_l_l_o')
    'HELLO'

    >>> enc('hello:world?')
    'hello~3aworld~3f'
    >>> dec('hello~3aworld~3f')
    'hello:world?'

    >>> enc('the\x07quick\xADshot')
    'the~07quick~adshot'
    >>> dec('the~07quick~adshot')
    'the\\x07quick\\xadshot'
    '''
    e = '_'
    winreserved = [ord(x) for x in '\\:*?"<>|']
    cmap = dict([(chr(x), chr(x)) for x in xrange(127)])
    for x in (range(32) + range(126, 256) + winreserved):
        cmap[chr(x)] = "~%02x" % x
    for x in range(ord("A"), ord("Z") + 1) + [ord(e)]:
        cmap[chr(x)] = e + chr(x).lower()
    dmap = {}
    for k, v in cmap.iteritems():
        dmap[v] = k
    def decode(s):
        i = 0
        while i < len(s):
            for l in xrange(1, 4):
                try:
                    yield dmap[s[i:i + l]]
                    i += l
                    break
                except KeyError:
                    pass
            else:
                raise KeyError
    return (lambda s: ''.join([cmap[c] for c in s]),
            lambda s: ''.join(list(decode(s))))

_encodefname, _decodefname = _buildencodefun()

def encodefilename(s):
    '''
    >>> encodefilename('foo.i/bar.d/bla.hg/hi:world?/HELLO')
    'foo.i.hg/bar.d.hg/bla.hg.hg/hi~3aworld~3f/_h_e_l_l_o'
    '''
    return _encodefname(encodedir(s))

def decodefilename(s):
    '''
    >>> decodefilename('foo.i.hg/bar.d.hg/bla.hg.hg/hi~3aworld~3f/_h_e_l_l_o')
    'foo.i/bar.d/bla.hg/hi:world?/HELLO'
    '''
    return decodedir(_decodefname(s))

def _buildlowerencodefun():
    '''
    >>> f = _buildlowerencodefun()
    >>> f('nothing/special.txt')
    'nothing/special.txt'
    >>> f('HELLO')
    'hello'
    >>> f('hello:world?')
    'hello~3aworld~3f'
    >>> f('the\x07quick\xADshot')
    'the~07quick~adshot'
    '''
    winreserved = [ord(x) for x in '\\:*?"<>|']
    cmap = dict([(chr(x), chr(x)) for x in xrange(127)])
    for x in (range(32) + range(126, 256) + winreserved):
        cmap[chr(x)] = "~%02x" % x
    for x in range(ord("A"), ord("Z") + 1):
        cmap[chr(x)] = chr(x).lower()
    return lambda s: "".join([cmap[c] for c in s])

lowerencode = getattr(parsers, 'lowerencode', None) or _buildlowerencodefun()

# Windows reserved names: con, prn, aux, nul, com1..com9, lpt1..lpt9
_winres3 = ('aux', 'con', 'prn', 'nul') # length 3
_winres4 = ('com', 'lpt')               # length 4 (with trailing 1..9)
def _auxencode(path, dotencode):
    '''
    Encodes filenames containing names reserved by Windows or which end in
    period or space. Does not touch other single reserved characters c.
    Specifically, c in '\\:*?"<>|' or ord(c) <= 31 are *not* encoded here.
    Additionally encodes space or period at the beginning, if dotencode is
    True. Parameter path is assumed to be all lowercase.
    A segment only needs encoding if a reserved name appears as a
    basename (e.g. "aux", "aux.foo"). A directory or file named "foo.aux"
    doesn't need encoding.

    >>> s = '.foo/aux.txt/txt.aux/con/prn/nul/foo.'
    >>> _auxencode(s.split('/'), True)
    ['~2efoo', 'au~78.txt', 'txt.aux', 'co~6e', 'pr~6e', 'nu~6c', 'foo~2e']
    >>> s = '.com1com2/lpt9.lpt4.lpt1/conprn/com0/lpt0/foo.'
    >>> _auxencode(s.split('/'), False)
    ['.com1com2', 'lp~749.lpt4.lpt1', 'conprn', 'com0', 'lpt0', 'foo~2e']
    >>> _auxencode(['foo. '], True)
    ['foo.~20']
    >>> _auxencode([' .foo'], True)
    ['~20.foo']
    '''
    for i, n in enumerate(path):
        if not n:
            continue
        if dotencode and n[0] in '. ':
            n = "~%02x" % ord(n[0]) + n[1:]
            path[i] = n
        else:
            l = n.find('.')
            if l == -1:
                l = len(n)
            if ((l == 3 and n[:3] in _winres3) or
                (l == 4 and n[3] <= '9' and n[3] >= '1'
                        and n[:3] in _winres4)):
                # encode third letter ('aux' -> 'au~78')
                ec = "~%02x" % ord(n[2])
                n = n[0:2] + ec + n[3:]
                path[i] = n
        if n[-1] in '. ':
            # encode last period or space ('foo...' -> 'foo..~2e')
            path[i] = n[:-1] + "~%02x" % ord(n[-1])
    return path

_maxstorepathlen = 120
_dirprefixlen = 8
_maxshortdirslen = 8 * (_dirprefixlen + 1) - 4

def _hashencode(path, dotencode):
    digest = _sha(path).hexdigest()
    le = lowerencode(path).split('/')[1:]
    parts = _auxencode(le, dotencode)
    basename = parts[-1]
    _root, ext = os.path.splitext(basename)
    sdirs = []
    sdirslen = 0
    for p in parts[:-1]:
        d = p[:_dirprefixlen]
        if d[-1] in '. ':
            # Windows can't access dirs ending in period or space
            d = d[:-1] + '_'
        if sdirslen == 0:
            t = len(d)
        else:
            t = sdirslen + 1 + len(d)
            if t > _maxshortdirslen:
                break
        sdirs.append(d)
        sdirslen = t
    dirs = '/'.join(sdirs)
    if len(dirs) > 0:
        dirs += '/'
    res = 'dh/' + dirs + digest + ext
    spaceleft = _maxstorepathlen - len(res)
    if spaceleft > 0:
        filler = basename[:spaceleft]
        res = 'dh/' + dirs + filler + digest + ext
    return res

def _hybridencode(path, dotencode):
    '''encodes path with a length limit

    Encodes all paths that begin with 'data/', according to the following.

    Default encoding (reversible):

    Encodes all uppercase letters 'X' as '_x'. All reserved or illegal
    characters are encoded as '~xx', where xx is the two digit hex code
    of the character (see encodefilename).
    Relevant path components consisting of Windows reserved filenames are
    masked by encoding the third character ('aux' -> 'au~78', see _auxencode).

    Hashed encoding (not reversible):

    If the default-encoded path is longer than _maxstorepathlen, a
    non-reversible hybrid hashing of the path is done instead.
    This encoding uses up to _dirprefixlen characters of all directory
    levels of the lowerencoded path, but not more levels than can fit into
    _maxshortdirslen.
    Then follows the filler followed by the sha digest of the full path.
    The filler is the beginning of the basename of the lowerencoded path
    (the basename is everything after the last path separator). The filler
    is as long as possible, filling in characters from the basename until
    the encoded path has _maxstorepathlen characters (or all chars of the
    basename have been taken).
    The extension (e.g. '.i' or '.d') is preserved.

    The string 'data/' at the beginning is replaced with 'dh/', if the hashed
    encoding was used.
    '''
    path = encodedir(path)
    ef = _encodefname(path).split('/')
    res = '/'.join(_auxencode(ef, dotencode))
    if len(res) > _maxstorepathlen:
        res = _hashencode(path, dotencode)
    return res

def _pathencode(path):
    de = encodedir(path)
    if len(path) > _maxstorepathlen:
        return _hashencode(de, True)
    ef = _encodefname(de).split('/')
    res = '/'.join(_auxencode(ef, True))
    if len(res) > _maxstorepathlen:
        return _hashencode(de, True)
    return res

_pathencode = getattr(parsers, 'pathencode', _pathencode)

def _plainhybridencode(f):
    return _hybridencode(f, False)

def _calcmode(vfs):
    try:
        # files in .hg/ will be created using this mode
        mode = vfs.stat().st_mode
            # avoid some useless chmods
        if (0777 & ~util.umask) == (0777 & mode):
            mode = None
    except OSError:
        mode = None
    return mode

_data = ('data 00manifest.d 00manifest.i 00changelog.d 00changelog.i'
         ' phaseroots obsstore')

class basicstore(object):
    '''base class for local repository stores'''
    def __init__(self, path, vfstype):
        vfs = vfstype(path)
        self.path = vfs.base
        self.createmode = _calcmode(vfs)
        vfs.createmode = self.createmode
        self.rawvfs = vfs
        self.vfs = scmutil.filtervfs(vfs, encodedir)
        self.opener = self.vfs

    def join(self, f):
        return self.path + '/' + encodedir(f)

    def _walk(self, relpath, recurse):
        '''yields (unencoded, encoded, size)'''
        path = self.path
        if relpath:
            path += '/' + relpath
        striplen = len(self.path) + 1
        l = []
        if self.rawvfs.isdir(path):
            visit = [path]
            readdir = self.rawvfs.readdir
            while visit:
                p = visit.pop()
                for f, kind, st in readdir(p, stat=True):
                    fp = p + '/' + f
                    if kind == stat.S_IFREG and f[-2:] in ('.d', '.i'):
                        n = util.pconvert(fp[striplen:])
                        l.append((decodedir(n), n, st.st_size))
                    elif kind == stat.S_IFDIR and recurse:
                        visit.append(fp)
        l.sort()
        return l

    def datafiles(self):
        return self._walk('data', True)

    def walk(self):
        '''yields (unencoded, encoded, size)'''
        # yield data files first
        for x in self.datafiles():
            yield x
        # yield manifest before changelog
        for x in reversed(self._walk('', False)):
            yield x

    def copylist(self):
        return ['requires'] + _data.split()

    def write(self):
        pass

    def __contains__(self, path):
        '''Checks if the store contains path'''
        path = "/".join(("data", path))
        # file?
        if os.path.exists(self.join(path + ".i")):
            return True
        # dir?
        if not path.endswith("/"):
            path = path + "/"
        return os.path.exists(self.join(path))

class encodedstore(basicstore):
    def __init__(self, path, vfstype):
        vfs = vfstype(path + '/store')
        self.path = vfs.base
        self.createmode = _calcmode(vfs)
        vfs.createmode = self.createmode
        self.rawvfs = vfs
        self.vfs = scmutil.filtervfs(vfs, encodefilename)
        self.opener = self.vfs

    def datafiles(self):
        for a, b, size in self._walk('data', True):
            try:
                a = decodefilename(a)
            except KeyError:
                a = None
            yield a, b, size

    def join(self, f):
        return self.path + '/' + encodefilename(f)

    def copylist(self):
        return (['requires', '00changelog.i'] +
                ['store/' + f for f in _data.split()])

class fncache(object):
    # the filename used to be partially encoded
    # hence the encodedir/decodedir dance
    def __init__(self, vfs):
        self.vfs = vfs
        self.entries = None
        self._dirty = False

    def _load(self):
        '''fill the entries from the fncache file'''
        self._dirty = False
        try:
            fp = self.vfs('fncache', mode='rb')
        except IOError:
            # skip nonexistent file
            self.entries = set()
            return
        self.entries = set(decodedir(fp.read()).splitlines())
        if '' in self.entries:
            fp.seek(0)
            for n, line in enumerate(fp):
                if not line.rstrip('\n'):
                    t = _('invalid entry in fncache, line %s') % (n + 1)
                    raise util.Abort(t)
        fp.close()

    def _write(self, files, atomictemp):
        fp = self.vfs('fncache', mode='wb', atomictemp=atomictemp)
        if files:
            fp.write(encodedir('\n'.join(files) + '\n'))
        fp.close()
        self._dirty = False

    def rewrite(self, files):
        self._write(files, False)
        self.entries = set(files)

    def write(self):
        if self._dirty:
            self._write(self.entries, True)

    def add(self, fn):
        if self.entries is None:
            self._load()
        if fn not in self.entries:
            self._dirty = True
            self.entries.add(fn)

    def __contains__(self, fn):
        if self.entries is None:
            self._load()
        return fn in self.entries

    def __iter__(self):
        if self.entries is None:
            self._load()
        return iter(self.entries)

class _fncachevfs(scmutil.abstractvfs, scmutil.auditvfs):
    def __init__(self, vfs, fnc, encode):
        scmutil.auditvfs.__init__(self, vfs)
        self.fncache = fnc
        self.encode = encode

    def __call__(self, path, mode='r', *args, **kw):
        if mode not in ('r', 'rb') and path.startswith('data/'):
            self.fncache.add(path)
        return self.vfs(self.encode(path), mode, *args, **kw)

    def join(self, path):
        if path:
            return self.vfs.join(self.encode(path))
        else:
            return self.vfs.join(path)

class fncachestore(basicstore):
    def __init__(self, path, vfstype, dotencode):
        if dotencode:
            encode = _pathencode
        else:
            encode = _plainhybridencode
        self.encode = encode
        vfs = vfstype(path + '/store')
        self.path = vfs.base
        self.pathsep = self.path + '/'
        self.createmode = _calcmode(vfs)
        vfs.createmode = self.createmode
        self.rawvfs = vfs
        fnc = fncache(vfs)
        self.fncache = fnc
        self.vfs = _fncachevfs(vfs, fnc, encode)
        self.opener = self.vfs

    def join(self, f):
        return self.pathsep + self.encode(f)

    def getsize(self, path):
        return self.rawvfs.stat(path).st_size

    def datafiles(self):
        rewrite = False
        existing = []
        for f in sorted(self.fncache):
            ef = self.encode(f)
            try:
                yield f, ef, self.getsize(ef)
                existing.append(f)
            except OSError, err:
                if err.errno != errno.ENOENT:
                    raise
                # nonexistent entry
                rewrite = True
        if rewrite:
            # rewrite fncache to remove nonexistent entries
            # (may be caused by rollback / strip)
            self.fncache.rewrite(existing)

    def copylist(self):
        d = ('data dh fncache phaseroots obsstore'
             ' 00manifest.d 00manifest.i 00changelog.d 00changelog.i')
        return (['requires', '00changelog.i'] +
                ['store/' + f for f in d.split()])

    def write(self):
        self.fncache.write()

    def _exists(self, f):
        ef = self.encode(f)
        try:
            self.getsize(ef)
            return True
        except OSError, err:
            if err.errno != errno.ENOENT:
                raise
            # nonexistent entry
            return False

    def __contains__(self, path):
        '''Checks if the store contains path'''
        path = "/".join(("data", path))
        # check for files (exact match)
        e = path + '.i'
        if e in self.fncache and self._exists(e):
            return True
        # now check for directories (prefix match)
        if not path.endswith('/'):
            path += '/'
        for e in self.fncache:
            if e.startswith(path) and self._exists(e):
                return True
        return False

def store(requirements, path, vfstype):
    if 'store' in requirements:
        if 'fncache' in requirements:
            return fncachestore(path, vfstype, 'dotencode' in requirements)
        return encodedstore(path, vfstype)
    return basicstore(path, vfstype)
