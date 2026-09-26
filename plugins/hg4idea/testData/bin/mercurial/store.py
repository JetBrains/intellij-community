# store.py - repository store handling for Mercurial)
#
# Copyright 2008 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import collections
import functools
import os
import re
import stat
from typing import Generator, List

from .i18n import _
from .thirdparty import attr
from .node import hex
from .revlogutils.constants import (
    INDEX_HEADER,
    KIND_CHANGELOG,
    KIND_FILELOG,
    KIND_MANIFESTLOG,
)
from . import (
    changelog,
    error,
    filelog,
    manifest,
    policy,
    pycompat,
    revlog as revlogmod,
    util,
    vfs as vfsmod,
)
from .utils import hashutil

parsers = policy.importmod('parsers')
# how much bytes should be read from fncache in one read
# It is done to prevent loading large fncache files into memory
fncache_chunksize = 10 ** 6


def _match_tracked_entry(entry, matcher):
    """parses a fncache entry and returns whether the entry is tracking a path
    matched by matcher or not.

    If matcher is None, returns True"""

    if matcher is None:
        return True
    if entry.is_filelog:
        return matcher(entry.target_id)
    elif entry.is_manifestlog:
        return matcher.visitdir(entry.target_id.rstrip(b'/'))
    raise error.ProgrammingError(b"cannot process entry %r" % entry)


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
    for k, v in cmap.items():
        dmap[v] = k

    def decode(s):
        i = 0
        while i < len(s):
            for l in range(1, 4):
                try:
                    yield dmap[s[i : i + l]]
                    i += l
                    break
                except KeyError:
                    pass
            else:
                raise KeyError

    return (
        lambda s: b''.join([cmap[s[c : c + 1]] for c in range(len(s))]),
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
    cmap = {xchr(x): xchr(x) for x in range(127)}
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

REVLOG_FILES_EXT = (
    b'.i',
    b'.idx',
    b'.d',
    b'.dat',
    b'.n',
    b'.nd',
    b'.sda',
)
# file extension that also use a `-SOMELONGIDHASH.ext` form
REVLOG_FILES_LONG_EXT = (
    b'.nd',
    b'.idx',
    b'.dat',
    b'.sda',
)
# files that are "volatile" and might change between listing and streaming
#
# note: the ".nd" file are nodemap data and won't "change" but they might be
# deleted.
REVLOG_FILES_VOLATILE_EXT = (b'.n', b'.nd')

# some exception to the above matching
#
# XXX This is currently not in use because of issue6542
EXCLUDED = re.compile(br'.*undo\.[^/]+\.(nd?|i)$')


def is_revlog(f, kind, st):
    if kind != stat.S_IFREG:
        return False
    if f.endswith(REVLOG_FILES_EXT):
        return True
    return False


def is_revlog_file(f):
    if f.endswith(REVLOG_FILES_EXT):
        return True
    return False


@attr.s(slots=True)
class StoreFile:
    """a file matching a store entry"""

    unencoded_path = attr.ib()
    _file_size = attr.ib(default=None)
    is_volatile = attr.ib(default=False)

    def file_size(self, vfs):
        if self._file_size is None:
            if vfs is None:
                msg = b"calling vfs-less file_size without prior call: %s"
                msg %= self.unencoded_path
                raise error.ProgrammingError(msg)
            try:
                self._file_size = vfs.stat(self.unencoded_path).st_size
            except FileNotFoundError:
                self._file_size = 0
        return self._file_size

    @property
    def has_size(self):
        return self._file_size is not None

    def get_stream(self, vfs, copies):
        """return data "stream" information for this file

        (unencoded_file_path, content_iterator, content_size)
        """
        size = self.file_size(None)

        def get_stream():
            actual_path = copies[vfs.join(self.unencoded_path)]
            with open(actual_path, 'rb') as fp:
                yield None  # ready to stream
                if size <= 65536:
                    yield fp.read(size)
                else:
                    yield from util.filechunkiter(fp, limit=size)

        s = get_stream()
        next(s)
        return (self.unencoded_path, s, size)


@attr.s(slots=True, init=False)
class BaseStoreEntry:
    """An entry in the store

    This is returned by `store.walk` and represent some data in the store."""

    maybe_volatile = True

    def files(self) -> List[StoreFile]:
        raise NotImplementedError

    def get_streams(
        self,
        repo=None,
        vfs=None,
        copies=None,
        max_changeset=None,
        preserve_file_count=False,
    ):
        """return a list of data stream associated to files for this entry

        return [(unencoded_file_path, content_iterator, content_size), …]
        """
        assert vfs is not None
        return [f.get_stream(vfs, copies) for f in self.files()]


@attr.s(slots=True, init=False)
class SimpleStoreEntry(BaseStoreEntry):
    """A generic entry in the store"""

    is_revlog = False

    maybe_volatile = attr.ib()
    _entry_path = attr.ib()
    _is_volatile = attr.ib(default=False)
    _file_size = attr.ib(default=None)
    _files = attr.ib(default=None)

    def __init__(
        self,
        entry_path,
        is_volatile=False,
        file_size=None,
    ):
        super().__init__()
        self._entry_path = entry_path
        self._is_volatile = is_volatile
        self._file_size = file_size
        self._files = None
        self.maybe_volatile = is_volatile

    def files(self) -> List[StoreFile]:
        if self._files is None:
            self._files = [
                StoreFile(
                    unencoded_path=self._entry_path,
                    file_size=self._file_size,
                    is_volatile=self._is_volatile,
                )
            ]
        return self._files


@attr.s(slots=True, init=False)
class RevlogStoreEntry(BaseStoreEntry):
    """A revlog entry in the store"""

    is_revlog = True

    revlog_type = attr.ib(default=None)
    target_id = attr.ib(default=None)
    maybe_volatile = attr.ib(default=True)
    _path_prefix = attr.ib(default=None)
    _details = attr.ib(default=None)
    _files = attr.ib(default=None)

    def __init__(
        self,
        revlog_type,
        path_prefix,
        target_id,
        details,
    ):
        super().__init__()
        self.revlog_type = revlog_type
        self.target_id = target_id
        self._path_prefix = path_prefix
        assert b'.i' in details, (path_prefix, details)
        for ext in details:
            if ext.endswith(REVLOG_FILES_VOLATILE_EXT):
                self.maybe_volatile = True
                break
        else:
            self.maybe_volatile = False
        self._details = details
        self._files = None

    @property
    def is_changelog(self):
        return self.revlog_type == KIND_CHANGELOG

    @property
    def is_manifestlog(self):
        return self.revlog_type == KIND_MANIFESTLOG

    @property
    def is_filelog(self):
        return self.revlog_type == KIND_FILELOG

    def main_file_path(self):
        """unencoded path of the main revlog file"""
        return self._path_prefix + b'.i'

    def files(self) -> List[StoreFile]:
        if self._files is None:
            self._files = []
            for ext in sorted(self._details, key=_ext_key):
                path = self._path_prefix + ext
                file_size = self._details[ext]
                # files that are "volatile" and might change between
                # listing and streaming
                #
                # note: the ".nd" file are nodemap data and won't "change"
                # but they might be deleted.
                volatile = ext.endswith(REVLOG_FILES_VOLATILE_EXT)
                f = StoreFile(path, file_size, volatile)
                self._files.append(f)
        return self._files

    def get_streams(
        self,
        repo=None,
        vfs=None,
        copies=None,
        max_changeset=None,
        preserve_file_count=False,
    ):
        pre_sized = all(f.has_size for f in self.files())
        if pre_sized and (
            repo is None
            or max_changeset is None
            # This use revlog-v2, ignore for now
            or any(k.endswith(b'.idx') for k in self._details.keys())
            # This is not inline, no race expected
            or b'.d' in self._details
        ):
            return super().get_streams(
                repo=repo,
                vfs=vfs,
                copies=copies,
                max_changeset=max_changeset,
                preserve_file_count=preserve_file_count,
            )
        elif not preserve_file_count:
            stream = [
                f.get_stream(vfs, copies)
                for f in self.files()
                if not f.unencoded_path.endswith((b'.i', b'.d'))
            ]
            rl = self.get_revlog_instance(repo).get_revlog()
            rl_stream = rl.get_streams(max_changeset)
            stream.extend(rl_stream)
            return stream

        name_to_size = {}
        for f in self.files():
            name_to_size[f.unencoded_path] = f.file_size(None)

        stream = [
            f.get_stream(vfs, copies)
            for f in self.files()
            if not f.unencoded_path.endswith(b'.i')
        ]

        index_path = self._path_prefix + b'.i'

        index_file = None
        try:
            index_file = vfs(index_path)
            header = index_file.read(INDEX_HEADER.size)
            if revlogmod.revlog.is_inline_index(header):
                size = name_to_size[index_path]

                # no split underneath, just return the stream
                def get_stream():
                    fp = index_file
                    try:
                        fp.seek(0)
                        yield None
                        if size <= 65536:
                            yield fp.read(size)
                        else:
                            yield from util.filechunkiter(fp, limit=size)
                    finally:
                        fp.close()

                s = get_stream()
                next(s)
                index_file = None
                stream.append((index_path, s, size))
            else:
                rl = self.get_revlog_instance(repo).get_revlog()
                rl_stream = rl.get_streams(max_changeset, force_inline=True)
                for name, s, size in rl_stream:
                    if name_to_size.get(name, 0) != size:
                        msg = _(b"expected %d bytes but %d provided for %s")
                        msg %= name_to_size.get(name, 0), size, name
                        raise error.Abort(msg)
                stream.extend(rl_stream)
        finally:
            if index_file is not None:
                index_file.close()

        files = self.files()
        assert len(stream) == len(files), (
            stream,
            files,
            self._path_prefix,
            self.target_id,
        )
        return stream

    def get_revlog_instance(self, repo):
        """Obtain a revlog instance from this store entry

        An instance of the appropriate class is returned.
        """
        if self.is_changelog:
            return changelog.changelog(repo.svfs)
        elif self.is_manifestlog:
            mandir = self.target_id
            return manifest.manifestrevlog(
                repo.nodeconstants, repo.svfs, tree=mandir
            )
        else:
            return filelog.filelog(repo.svfs, self.target_id)


def _gather_revlog(files_data):
    """group files per revlog prefix

    The returns a two level nested dict. The top level key is the revlog prefix
    without extension, the second level is all the file "suffix" that were
    seen for this revlog and arbitrary file data as value.
    """
    revlogs = collections.defaultdict(dict)
    for u, value in files_data:
        name, ext = _split_revlog_ext(u)
        revlogs[name][ext] = value
    return sorted(revlogs.items())


def _split_revlog_ext(filename):
    """split the revlog file prefix from the variable extension"""
    if filename.endswith(REVLOG_FILES_LONG_EXT):
        char = b'-'
    else:
        char = b'.'
    idx = filename.rfind(char)
    return filename[:idx], filename[idx:]


def _ext_key(ext):
    """a key to order revlog suffix

    important to issue .i after other entry."""
    # the only important part of this order is to keep the `.i` last.
    if ext.endswith(b'.n'):
        return (0, ext)
    elif ext.endswith(b'.nd'):
        return (10, ext)
    elif ext.endswith(b'.d'):
        return (20, ext)
    elif ext.endswith(b'.i'):
        return (50, ext)
    else:
        return (40, ext)


class basicstore:
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

    def _walk(self, relpath, recurse, undecodable=None):
        '''yields (revlog_type, unencoded, size)'''
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
                    if is_revlog(f, kind, st):
                        n = util.pconvert(fp[striplen:])
                        l.append((decodedir(n), st.st_size))
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

    def data_entries(
        self, matcher=None, undecodable=None
    ) -> Generator[BaseStoreEntry, None, None]:
        """Like walk, but excluding the changelog and root manifest.

        When [undecodable] is None, revlogs names that can't be
        decoded cause an exception. When it is provided, it should
        be a list and the filenames that can't be decoded are added
        to it instead. This is very rarely needed."""
        dirs = [
            (b'data', KIND_FILELOG, False),
            (b'meta', KIND_MANIFESTLOG, True),
        ]
        for base_dir, rl_type, strip_filename in dirs:
            files = self._walk(base_dir, True, undecodable=undecodable)
            for revlog, details in _gather_revlog(files):
                revlog_target_id = revlog.split(b'/', 1)[1]
                if strip_filename and b'/' in revlog:
                    revlog_target_id = revlog_target_id.rsplit(b'/', 1)[0]
                    revlog_target_id += b'/'
                yield RevlogStoreEntry(
                    path_prefix=revlog,
                    revlog_type=rl_type,
                    target_id=revlog_target_id,
                    details=details,
                )

    def top_entries(
        self, phase=False, obsolescence=False
    ) -> Generator[BaseStoreEntry, None, None]:
        if phase and self.vfs.exists(b'phaseroots'):
            yield SimpleStoreEntry(
                entry_path=b'phaseroots',
                is_volatile=True,
            )

        if obsolescence and self.vfs.exists(b'obsstore'):
            # XXX if we had the file size it could be non-volatile
            yield SimpleStoreEntry(
                entry_path=b'obsstore',
                is_volatile=True,
            )

        files = reversed(self._walk(b'', False))

        changelogs = collections.defaultdict(dict)
        manifestlogs = collections.defaultdict(dict)

        for u, s in files:
            if u.startswith(b'00changelog'):
                name, ext = _split_revlog_ext(u)
                changelogs[name][ext] = s
            elif u.startswith(b'00manifest'):
                name, ext = _split_revlog_ext(u)
                manifestlogs[name][ext] = s
            else:
                yield SimpleStoreEntry(
                    entry_path=u,
                    is_volatile=False,
                    file_size=s,
                )
        # yield manifest before changelog
        top_rl = [
            (manifestlogs, KIND_MANIFESTLOG),
            (changelogs, KIND_CHANGELOG),
        ]
        assert len(manifestlogs) <= 1
        assert len(changelogs) <= 1
        for data, revlog_type in top_rl:
            for revlog, details in sorted(data.items()):
                yield RevlogStoreEntry(
                    path_prefix=revlog,
                    revlog_type=revlog_type,
                    target_id=b'',
                    details=details,
                )

    def walk(
        self, matcher=None, phase=False, obsolescence=False
    ) -> Generator[BaseStoreEntry, None, None]:
        """return files related to data storage (ie: revlogs)

        yields instance from BaseStoreEntry subclasses

        if a matcher is passed, storage files of only those tracked paths
        are passed with matches the matcher
        """
        # yield data files first
        for x in self.data_entries(matcher):
            yield x
        for x in self.top_entries(phase=phase, obsolescence=obsolescence):
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

    def _walk(self, relpath, recurse, undecodable=None):
        old = super()._walk(relpath, recurse)
        new = []
        for f1, value in old:
            try:
                f2 = decodefilename(f1)
            except KeyError:
                if undecodable is None:
                    msg = _(b'undecodable revlog name %s') % f1
                    raise error.StorageError(msg)
                else:
                    undecodable.append(f1)
                    continue
            new.append((f2, value))
        return new

    def data_entries(
        self, matcher=None, undecodable=None
    ) -> Generator[BaseStoreEntry, None, None]:
        entries = super(encodedstore, self).data_entries(
            undecodable=undecodable
        )
        for entry in entries:
            if _match_tracked_entry(entry, matcher):
                yield entry

    def join(self, f):
        return self.path + b'/' + encodefilename(f)

    def copylist(self):
        return [b'requires', b'00changelog.i'] + [b'store/' + f for f in _data]


class fncache:
    # the filename used to be partially encoded
    # hence the encodedir/decodedir dance
    def __init__(self, vfs):
        self.vfs = vfs
        self._ignores = set()
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
            for n, line in enumerate(fp):
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

    def addignore(self, fn):
        self._ignores.add(fn)

    def add(self, fn):
        if fn in self._ignores:
            return
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
        if (
            mode not in (b'r', b'rb')
            and (path.startswith(b'data/') or path.startswith(b'meta/'))
            and is_revlog_file(path)
        ):
            # do not trigger a fncache load when adding a file that already is
            # known to exist.
            notload = self.fncache.entries is None and (
                # if the file has size zero, it should be considered as missing.
                # Such zero-size files are the result of truncation when a
                # transaction is aborted.
                self.vfs.exists(encoded)
                and self.vfs.stat(encoded).st_size
            )
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

    def data_entries(
        self, matcher=None, undecodable=None
    ) -> Generator[BaseStoreEntry, None, None]:
        # Note: all files in fncache should be revlog related, However the
        # fncache might contains such file added by previous version of
        # Mercurial.
        files = ((f, None) for f in self.fncache if is_revlog_file(f))
        by_revlog = _gather_revlog(files)
        for revlog, details in by_revlog:
            if revlog.startswith(b'data/'):
                rl_type = KIND_FILELOG
                revlog_target_id = revlog.split(b'/', 1)[1]
            elif revlog.startswith(b'meta/'):
                rl_type = KIND_MANIFESTLOG
                # drop the initial directory and the `00manifest` file part
                tmp = revlog.split(b'/', 1)[1]
                revlog_target_id = tmp.rsplit(b'/', 1)[0] + b'/'
            else:
                # unreachable
                assert False, revlog
            entry = RevlogStoreEntry(
                path_prefix=revlog,
                revlog_type=rl_type,
                target_id=revlog_target_id,
                details=details,
            )
            if _match_tracked_entry(entry, matcher):
                yield entry

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
        except FileNotFoundError:
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
