# store.py - repository store handling for Mercurial
#
# Copyright 2008 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno
import functools
import os
import re
import stat

from .i18n import _
from .pycompat import getattr
from .node import hex
from . import (
    changelog,
    error,
    manifest,
    policy,
    pycompat,
    util,
    vfs as vfsmod,
)
from .utils import hashutil

parsers = policy.importmod('parsers')
# how much bytes should be read from fncache in one read
# It is done to prevent loading large fncache files into memory
fncache_chunksize = 10 ** 6


def _matchtrackedpath(path, matcher):
    """parses a fncache entry and returns whether the entry is tracking a path
    matched by matcher or not.

    If matcher is None, returns True"""

    if matcher is None:
        return True
    path = decodedir(path)
    if path.startswith(b'data/'):
        return matcher(path[len(b'data/') : -len(b'.i')])
    elif path.startswith(b'meta/'):
        return matcher.visitdir(path[len(b'meta/') : -len(b'/00manifest.i')])

    raise error.ProgrammingError(b"cannot decode path %s" % path)


# This avoids a collision between a file named foo and a dir named
# foo.i or foo.d
def _encodedir(path):
    """
    >>> _encodedir(b'data/foo.i')
    'data/foo.i'
    >>> _encodedir(b'data/foo.i/bla.i')
    'data/foo.i.hg/bla.i'
    >>> _encodedir(b'data/foo.i.hg/bla.i')
    'data/foo.i.hg.hg/bla.i'
    >>> _encodedir(b'data/foo.i\\ndata/foo.i/bla.i\\ndata/foo.i.hg/bla.i\\n')
    'data/foo.i\\ndata/foo.i.hg/bla.i\\ndata/foo.i.hg.hg/bla.i\\n'
    """
    return (
        path.replace(b".hg/", b".hg.hg/")
        .replace(b".i/", b".i.hg/")
        .replace(b".d/", b".d.hg/")
    )


encodedir = getattr(parsers, 'encodedir', _encodedir)


def decodedir(path):
    """
    >>> decodedir(b'data/foo.i')
    'data/foo.i'
    >>> decodedir(b'data/foo.i.hg/bla.i')
    'data/foo.i/bla.i'
    >>> decodedir(b'data/foo.i.hg.hg/bla.i')
    'data/foo.i.hg/bla.i'
    """
    if b".hg/" not in path:
        return path
    return (
        path.replace(b".d.hg/", b".d/")
        .replace(b".i.hg/", b".i/")
        .replace(b".hg.hg/", b".hg/")
    )


def _reserved():
    """characters that are problematic for filesystems

    * ascii escapes (0..31)
    * ascii hi (126..255)
    * windows specials

    these characters will be escaped by encodefunctions
    """
    winreserved = [ord(x) for x in u'\\:*?"<>|']
    for x in range(32):
        yield x
    for x in range(126, 256):
        yield x
    for x in winreserved:
        yield x


def _buildencodefun():
    """
    >>> enc, dec = _buildencodefun()

    >>> enc(b'nothing/special.txt')
    'nothing/special.txt'
    >>> dec(b'nothing/special.txt')
    'nothing/special.txt'

    >>> enc(b'HELLO')
    '_h_e_l_l_o'
    >>> dec(b'_h_e_l_l_o')
    'HELLO'

    >>> enc(b'hello:world?')
    'hello~3aworld~3f'
    >>> dec(b'hello~3aworld~3f')
    'hello:world?'

    >>> enc(b'the\\x07quick\\xADshot')
    'the~07quick~adshot'
    >>> dec(b'the~07quick~adshot')
    'the\\x07quick\\xadshot'
    """
    e = b'_'
    xchr = pycompat.bytechr
    asciistr = list(map(xchr, range(127)))
    capitals = list(range(ord(b"A"), ord(b"Z") + 1))

    cmap = {x: x for x in asciistr}
    for x in _reserved():
        cmap[xchr(x)] = b"~%02x" % x
    for x in capitals + [ord(e)]:
        cmap[xchr(x)] = e + xchr(x).lower()

    dmap = {}
    for k, v in pycompat.iteritems(cmap):
        dmap[v] = k

    def decode(s):
        i = 0
        while i < len(s):
            for l in pycompat.xrange(1, 4):
                try:
                    yield dmap[s[i : i + l]]
                    i += l
                    break
                except KeyError:
                    pass
            else:
                raise KeyError

    return (
        lambda s: b''.join(
            [cmap[s[c : c + 1]] for c in pycompat.xrange(len(s))]
        ),
        lambda s: b''.join(list(decode(s))),
    )


_encodefname, _decodefname = _buildencodefun()


def encodefilename(s):
    """
    >>> encodefilename(b'foo.i/bar.d/bla.hg/hi:world?/HELLO')
    'foo.i.hg/bar.d.hg/bla.hg.hg/hi~3aworld~3f/_h_e_l_l_o'
    """
    return _encodefname(encodedir(s))


def decodefilename(s):
    """
    >>> decodefilename(b'foo.i.hg/bar.d.hg/bla.hg.hg/hi~3aworld~3f/_h_e_l_l_o')
    'foo.i/bar.d/bla.hg/hi:world?/HELLO'
    """
    return decodedir(_decodefname(s))


def _buildlowerencodefun():
    """
    >>> f = _buildlowerencodefun()
    >>> f(b'nothing/special.txt')
    'nothing/special.txt'
    >>> f(b'HELLO')
    'hello'
    >>> f(b'hello:world?')
    'hello~3aworld~3f'
    >>> f(b'the\\x07quick\\xADshot')
    'the~07quick~adshot'
    """
    xchr = pycompat.bytechr
    cmap = {xchr(x): xchr(x) for x in pycompat.xrange(127)}
    for x in _reserved():
        cmap[xchr(x)] = b"~%02x" % x
    for x in range(ord(b"A"), ord(b"Z") + 1):
        cmap[xchr(x)] = xchr(x).lower()

    def lowerencode(s):
        return b"".join([cmap[c] for c in pycompat.iterbytestr(s)])

    return lowerencode


lowerencode = getattr(parsers, 'lowerencode', None) or _buildlowerencodefun()

# Windows reserved names: con, prn, aux, nul, com1..com9, lpt1..lpt9
_winres3 = (b'aux', b'con', b'prn', b'nul')  # length 3
_winres4 = (b'com', b'lpt')  # length 4 (with trailing 1..9)


def _auxencode(path, dotencode):
    """
    Encodes filenames containing names reserved by Windows or which end in
    period or space. Does not touch other single reserved characters c.
    Specifically, c in '\\:*?"<>|' or ord(c) <= 31 are *not* encoded here.
    Additionally encodes space or period at the beginning, if dotencode is
    True. Parameter path is assumed to be all lowercase.
    A segment only needs encoding if a reserved name appears as a
    basename (e.g. "aux", "aux.foo"). A directory or file named "foo.aux"
    doesn't need encoding.

    >>> s = b'.foo/aux.txt/txt.aux/con/prn/nul/foo.'
    >>> _auxencode(s.split(b'/'), True)
    ['~2efoo', 'au~78.txt', 'txt.aux', 'co~6e', 'pr~6e', 'nu~6c', 'foo~2e']
    >>> s = b'.com1com2/lpt9.lpt4.lpt1/conprn/com0/lpt0/foo.'
    >>> _auxencode(s.split(b'/'), False)
    ['.com1com2', 'lp~749.lpt4.lpt1', 'conprn', 'com0', 'lpt0', 'foo~2e']
    >>> _auxencode([b'foo. '], True)
    ['foo.~20']
    >>> _auxencode([b' .foo'], True)
    ['~20.foo']
    """
    for i, n in enumerate(path):
        if not n:
            continue
        if dotencode and n[0] in b'. ':
            n = b"~%02x" % ord(n[0:1]) + n[1:]
            path[i] = n
        else:
            l = n.find(b'.')
            if l == -1:
                l = len(n)
            if (l == 3 and n[:3] in _winres3) or (
                l == 4
                and n[3:4] <= b'9'
                and n[3:4] >= b'1'
                and n[:3] in _winres4
            ):
                # encode third letter ('aux' -> 'au~78')
                ec = b"~%02x" % ord(n[2:3])
                n = n[0:2] + ec + n[3:]
                path[i] = n
        if n[-1] in b'. ':
            # encode last period or space ('foo...' -> 'foo..~2e')
            path[i] = n[:-1] + b"~%02x" % ord(n[-1:])
    return path


_maxstorepathlen = 120
_dirprefixlen = 8
_maxshortdirslen = 8 * (_dirprefixlen + 1) - 4


def _hashencode(path, dotencode):
    digest = hex(hashutil.sha1(path).digest())
    le = lowerencode(path[5:]).split(b'/')  # skips prefix 'data/' or 'meta/'
    parts = _auxencode(le, dotencode)
    basename = parts[-1]
    _root, ext = os.path.splitext(basename)
    sdirs = []
    sdirslen = 0
    for p in parts[:-1]:
        d = p[:_dirprefixlen]
        if d[-1] in b'. ':
            # Windows can't access dirs ending in period or space
            d = d[:-1] + b'_'
        if sdirslen == 0:
            t = len(d)
        else:
            t = sdirslen + 1 + len(d)
            if t > _maxshortdirslen:
                break
        sdirs.append(d)
        sdirslen = t
    dirs = b'/'.join(sdirs)
    if len(dirs) > 0:
        dirs += b'/'
    res = b'dh/' + dirs + digest + ext
    spaceleft = _maxstorepathlen - len(res)
    if spaceleft > 0:
        filler = basename[:spaceleft]
        res = b'dh/' + dirs + filler + digest + ext
    return res


def _hybridencode(path, dotencode):
    """encodes path with a length limit

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
    """
    path = encodedir(path)
    ef = _encodefname(path).split(b'/')
    res = b'/'.join(_auxencode(ef, dotencode))
    if len(res) > _maxstorepathlen:
        res = _hashencode(path, dotencode)
    return res


def _pathencode(path):
    de = encodedir(path)
    if len(path) > _maxstorepathlen:
        return _hashencode(de, True)
    ef = _encodefname(de).split(b'/')
    res = b'/'.join(_auxencode(ef, True))
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
        if (0o777 & ~util.umask) == (0o777 & mode):
            mode = None
    except OSError:
        mode = None
    return mode


_data = [
    b'bookmarks',
    b'narrowspec',
    b'data',
    b'meta',
    b'00manifest.d',
    b'00manifest.i',
    b'00changelog.d',
    b'00changelog.i',
    b'phaseroots',
    b'obsstore',
    b'requires',
]

REVLOG_FILES_MAIN_EXT = (b'.i', b'i.tmpcensored')
REVLOG_FILES_OTHER_EXT = (
    b'.idx',
    b'.d',
    b'.dat',
    b'.n',
    b'.nd',
    b'.sda',
    b'd.tmpcensored',
)
# files that are "volatile" and might change between listing and streaming
#
# note: the ".nd" file are nodemap data and won't "change" but they might be
# deleted.
REVLOG_FILES_VOLATILE_EXT = (b'.n', b'.nd')

# some exception to the above matching
#
# XXX This is currently not in use because of issue6542
EXCLUDED = re.compile(b'.*undo\.[^/]+\.(nd?|i)$')


def is_revlog(f, kind, st):
    if kind != stat.S_IFREG:
        return None
    return revlog_type(f)


def revlog_type(f):
    # XXX we need to filter `undo.` created by the transaction here, however
    # being naive about it also filter revlog for `undo.*` files, leading to
    # issue6542. So we no longer use EXCLUDED.
    if f.endswith(REVLOG_FILES_MAIN_EXT):
        return FILEFLAGS_REVLOG_MAIN
    elif f.endswith(REVLOG_FILES_OTHER_EXT):
        t = FILETYPE_FILELOG_OTHER
        if f.endswith(REVLOG_FILES_VOLATILE_EXT):
            t |= FILEFLAGS_VOLATILE
        return t
    return None


# the file is part of changelog data
FILEFLAGS_CHANGELOG = 1 << 13
# the file is part of manifest data
FILEFLAGS_MANIFESTLOG = 1 << 12
# the file is part of filelog data
FILEFLAGS_FILELOG = 1 << 11
# file that are not directly part of a revlog
FILEFLAGS_OTHER = 1 << 10

# the main entry point for a revlog
FILEFLAGS_REVLOG_MAIN = 1 << 1
# a secondary file for a revlog
FILEFLAGS_REVLOG_OTHER = 1 << 0

# files that are "volatile" and might change between listing and streaming
FILEFLAGS_VOLATILE = 1 << 20

FILETYPE_CHANGELOG_MAIN = FILEFLAGS_CHANGELOG | FILEFLAGS_REVLOG_MAIN
FILETYPE_CHANGELOG_OTHER = FILEFLAGS_CHANGELOG | FILEFLAGS_REVLOG_OTHER
FILETYPE_MANIFESTLOG_MAIN = FILEFLAGS_MANIFESTLOG | FILEFLAGS_REVLOG_MAIN
FILETYPE_MANIFESTLOG_OTHER = FILEFLAGS_MANIFESTLOG | FILEFLAGS_REVLOG_OTHER
FILETYPE_FILELOG_MAIN = FILEFLAGS_FILELOG | FILEFLAGS_REVLOG_MAIN
FILETYPE_FILELOG_OTHER = FILEFLAGS_FILELOG | FILEFLAGS_REVLOG_OTHER
FILETYPE_OTHER = FILEFLAGS_OTHER


class basicstore(object):
    '''base class for local repository stores'''

    def __init__(self, path, vfstype):
        vfs = vfstype(path)
        self.path = vfs.base
        self.createmode = _calcmode(vfs)
        vfs.createmode = self.createmode
        self.rawvfs = vfs
        self.vfs = vfsmod.filtervfs(vfs, encodedir)
        self.opener = self.vfs

    def join(self, f):
        return self.path + b'/' + encodedir(f)

    def _walk(self, relpath, recurse):
        '''yields (unencoded, encoded, size)'''
        path = self.path
        if relpath:
            path += b'/' + relpath
        striplen = len(self.path) + 1
        l = []
        if self.rawvfs.isdir(path):
            visit = [path]
            readdir = self.rawvfs.readdir
            while visit:
                p = visit.pop()
                for f, kind, st in readdir(p, stat=True):
                    fp = p + b'/' + f
                    rl_type = is_revlog(f, kind, st)
                    if rl_type is not None:
                        n = util.pconvert(fp[striplen:])
                        l.append((rl_type, decodedir(n), n, st.st_size))
                    elif kind == stat.S_IFDIR and recurse:
                        visit.append(fp)
        l.sort()
        return l

    def changelog(self, trypending, concurrencychecker=None):
        return changelog.changelog(
            self.vfs,
            trypending=trypending,
            concurrencychecker=concurrencychecker,
        )

    def manifestlog(self, repo, storenarrowmatch):
        rootstore = manifest.manifestrevlog(repo.nodeconstants, self.vfs)
        return manifest.manifestlog(self.vfs, repo, rootstore, storenarrowmatch)

    def datafiles(self, matcher=None):
        files = self._walk(b'data', True) + self._walk(b'meta', True)
        for (t, u, e, s) in files:
            yield (FILEFLAGS_FILELOG | t, u, e, s)

    def topfiles(self):
        # yield manifest before changelog
        files = reversed(self._walk(b'', False))
        for (t, u, e, s) in files:
            if u.startswith(b'00changelog'):
                yield (FILEFLAGS_CHANGELOG | t, u, e, s)
            elif u.startswith(b'00manifest'):
                yield (FILEFLAGS_MANIFESTLOG | t, u, e, s)
            else:
                yield (FILETYPE_OTHER | t, u, e, s)

    def walk(self, matcher=None):
        """return file related to data storage (ie: revlogs)

        yields (file_type, unencoded, encoded, size)

        if a matcher is passed, storage files of only those tracked paths
        are passed with matches the matcher
        """
        # yield data files first
        for x in self.datafiles(matcher):
            yield x
        for x in self.topfiles():
            yield x

    def copylist(self):
        return _data

    def write(self, tr):
        pass

    def invalidatecaches(self):
        pass

    def markremoved(self, fn):
        pass

    def __contains__(self, path):
        '''Checks if the store contains path'''
        path = b"/".join((b"data", path))
        # file?
        if self.vfs.exists(path + b".i"):
            return True
        # dir?
        if not path.endswith(b"/"):
            path = path + b"/"
        return self.vfs.exists(path)


class encodedstore(basicstore):
    def __init__(self, path, vfstype):
        vfs = vfstype(path + b'/store')
        self.path = vfs.base
        self.createmode = _calcmode(vfs)
        vfs.createmode = self.createmode
        self.rawvfs = vfs
        self.vfs = vfsmod.filtervfs(vfs, encodefilename)
        self.opener = self.vfs

    # note: topfiles would also need a decode phase. It is just that in
    # practice we do not have any file outside of `data/` that needs encoding.
    # However that might change so we should probably add a test and encoding
    # decoding for it too. see issue6548

    def datafiles(self, matcher=None):
        for t, a, b, size in super(encodedstore, self).datafiles():
            try:
                a = decodefilename(a)
            except KeyError:
                a = None
            if a is not None and not _matchtrackedpath(a, matcher):
                continue
            yield t, a, b, size

    def join(self, f):
        return self.path + b'/' + encodefilename(f)

    def copylist(self):
        return [b'requires', b'00changelog.i'] + [b'store/' + f for f in _data]


class fncache(object):
    # the filename used to be partially encoded
    # hence the encodedir/decodedir dance
    def __init__(self, vfs):
        self.vfs = vfs
        self.entries = None
        self._dirty = False
        # set of new additions to fncache
        self.addls = set()

    def ensureloaded(self, warn=None):
        """read the fncache file if not already read.

        If the file on disk is corrupted, raise. If warn is provided,
        warn and keep going instead."""
        if self.entries is None:
            self._load(warn)

    def _load(self, warn=None):
        '''fill the entries from the fncache file'''
        self._dirty = False
        try:
            fp = self.vfs(b'fncache', mode=b'rb')
        except IOError:
            # skip nonexistent file
            self.entries = set()
            return

        self.entries = set()
        chunk = b''
        for c in iter(functools.partial(fp.read, fncache_chunksize), b''):
            chunk += c
            try:
                p = chunk.rindex(b'\n')
                self.entries.update(decodedir(chunk[: p + 1]).splitlines())
                chunk = chunk[p + 1 :]
            except ValueError:
                # substring '\n' not found, maybe the entry is bigger than the
                # chunksize, so let's keep iterating
                pass

        if chunk:
            msg = _(b"fncache does not ends with a newline")
            if warn:
                warn(msg + b'\n')
            else:
                raise error.Abort(
                    msg,
                    hint=_(
                        b"use 'hg debugrebuildfncache' to "
                        b"rebuild the fncache"
                    ),
                )
        self._checkentries(fp, warn)
        fp.close()

    def _checkentries(self, fp, warn):
        """make sure there is no empty string in entries"""
        if b'' in self.entries:
            fp.seek(0)
            for n, line in enumerate(util.iterfile(fp)):
                if not line.rstrip(b'\n'):
                    t = _(b'invalid entry in fncache, line %d') % (n + 1)
                    if warn:
                        warn(t + b'\n')
                    else:
                        raise error.Abort(t)

    def write(self, tr):
        if self._dirty:
            assert self.entries is not None
            self.entries = self.entries | self.addls
            self.addls = set()
            tr.addbackup(b'fncache')
            fp = self.vfs(b'fncache', mode=b'wb', atomictemp=True)
            if self.entries:
                fp.write(encodedir(b'\n'.join(self.entries) + b'\n'))
            fp.close()
            self._dirty = False
        if self.addls:
            # if we have just new entries, let's append them to the fncache
            tr.addbackup(b'fncache')
            fp = self.vfs(b'fncache', mode=b'ab', atomictemp=True)
            if self.addls:
                fp.write(encodedir(b'\n'.join(self.addls) + b'\n'))
            fp.close()
            self.entries = None
            self.addls = set()

    def add(self, fn):
        if self.entries is None:
            self._load()
        if fn not in self.entries:
            self.addls.add(fn)

    def remove(self, fn):
        if self.entries is None:
            self._load()
        if fn in self.addls:
            self.addls.remove(fn)
            return
        try:
            self.entries.remove(fn)
            self._dirty = True
        except KeyError:
            pass

    def __contains__(self, fn):
        if fn in self.addls:
            return True
        if self.entries is None:
            self._load()
        return fn in self.entries

    def __iter__(self):
        if self.entries is None:
            self._load()
        return iter(self.entries | self.addls)


class _fncachevfs(vfsmod.proxyvfs):
    def __init__(self, vfs, fnc, encode):
        vfsmod.proxyvfs.__init__(self, vfs)
        self.fncache = fnc
        self.encode = encode

    def __call__(self, path, mode=b'r', *args, **kw):
        encoded = self.encode(path)
        if mode not in (b'r', b'rb') and (
            path.startswith(b'data/') or path.startswith(b'meta/')
        ):
            # do not trigger a fncache load when adding a file that already is
            # known to exist.
            notload = self.fncache.entries is None and self.vfs.exists(encoded)
            if notload and b'r+' in mode and not self.vfs.stat(encoded).st_size:
                # when appending to an existing file, if the file has size zero,
                # it should be considered as missing. Such zero-size files are
                # the result of truncation when a transaction is aborted.
                notload = False
            if not notload:
                self.fncache.add(path)
        return self.vfs(encoded, mode, *args, **kw)

    def join(self, path):
        if path:
            return self.vfs.join(self.encode(path))
        else:
            return self.vfs.join(path)

    def register_file(self, path):
        """generic hook point to lets fncache steer its stew"""
        if path.startswith(b'data/') or path.startswith(b'meta/'):
            self.fncache.add(path)


class fncachestore(basicstore):
    def __init__(self, path, vfstype, dotencode):
        if dotencode:
            encode = _pathencode
        else:
            encode = _plainhybridencode
        self.encode = encode
        vfs = vfstype(path + b'/store')
        self.path = vfs.base
        self.pathsep = self.path + b'/'
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

    def datafiles(self, matcher=None):
        for f in sorted(self.fncache):
            if not _matchtrackedpath(f, matcher):
                continue
            ef = self.encode(f)
            try:
                t = revlog_type(f)
                assert t is not None, f
                t |= FILEFLAGS_FILELOG
                yield t, f, ef, self.getsize(ef)
            except OSError as err:
                if err.errno != errno.ENOENT:
                    raise

    def copylist(self):
        d = (
            b'bookmarks',
            b'narrowspec',
            b'data',
            b'meta',
            b'dh',
            b'fncache',
            b'phaseroots',
            b'obsstore',
            b'00manifest.d',
            b'00manifest.i',
            b'00changelog.d',
            b'00changelog.i',
            b'requires',
        )
        return [b'requires', b'00changelog.i'] + [b'store/' + f for f in d]

    def write(self, tr):
        self.fncache.write(tr)

    def invalidatecaches(self):
        self.fncache.entries = None
        self.fncache.addls = set()

    def markremoved(self, fn):
        self.fncache.remove(fn)

    def _exists(self, f):
        ef = self.encode(f)
        try:
            self.getsize(ef)
            return True
        except OSError as err:
            if err.errno != errno.ENOENT:
                raise
            # nonexistent entry
            return False

    def __contains__(self, path):
        '''Checks if the store contains path'''
        path = b"/".join((b"data", path))
        # check for files (exact match)
        e = path + b'.i'
        if e in self.fncache and self._exists(e):
            return True
        # now check for directories (prefix match)
        if not path.endswith(b'/'):
            path += b'/'
        for e in self.fncache:
            if e.startswith(path) and self._exists(e):
                return True
        return False
