# parsers.py - Python implementation of parsers.c
#
# Copyright 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import io
import stat
import struct
import zlib

from ..node import (
    nullrev,
    sha1nodeconstants,
)
from ..thirdparty import attr
from .. import (
    error,
    revlogutils,
    util,
)

from ..revlogutils import nodemap as nodemaputil
from ..revlogutils import constants as revlog_constants

stringio = io.BytesIO


_pack = struct.pack
_unpack = struct.unpack
_compress = zlib.compress
_decompress = zlib.decompress


# a special value used internally for `size` if the file come from the other parent
FROM_P2 = -2

# a special value used internally for `size` if the file is modified/merged/added
NONNORMAL = -1

# a special value used internally for `time` if the time is ambigeous
AMBIGUOUS_TIME = -1

# Bits of the `flags` byte inside a node in the file format
DIRSTATE_V2_WDIR_TRACKED = 1 << 0
DIRSTATE_V2_P1_TRACKED = 1 << 1
DIRSTATE_V2_P2_INFO = 1 << 2
DIRSTATE_V2_MODE_EXEC_PERM = 1 << 3
DIRSTATE_V2_MODE_IS_SYMLINK = 1 << 4
DIRSTATE_V2_HAS_FALLBACK_EXEC = 1 << 5
DIRSTATE_V2_FALLBACK_EXEC = 1 << 6
DIRSTATE_V2_HAS_FALLBACK_SYMLINK = 1 << 7
DIRSTATE_V2_FALLBACK_SYMLINK = 1 << 8
DIRSTATE_V2_EXPECTED_STATE_IS_MODIFIED = 1 << 9
DIRSTATE_V2_HAS_MODE_AND_SIZE = 1 << 10
DIRSTATE_V2_HAS_MTIME = 1 << 11
DIRSTATE_V2_MTIME_SECOND_AMBIGUOUS = 1 << 12
DIRSTATE_V2_DIRECTORY = 1 << 13
DIRSTATE_V2_ALL_UNKNOWN_RECORDED = 1 << 14
DIRSTATE_V2_ALL_IGNORED_RECORDED = 1 << 15


@attr.s(slots=True, init=False)
class DirstateItem:
    """represent a dirstate entry

    It hold multiple attributes

    # about file tracking
    - wc_tracked: is the file tracked by the working copy
    - p1_tracked: is the file tracked in working copy first parent
    - p2_info: the file has been involved in some merge operation. Either
               because it was actually merged, or because the p2 version was
               ahead, or because some rename moved it there. In either case
               `hg status` will want it displayed as modified.

    # about the file state expected from p1 manifest:
    - mode: the file mode in p1
    - size: the file size in p1

    These value can be set to None, which mean we don't have a meaningful value
    to compare with. Either because we don't really care about them as there
    `status` is known without having to look at the disk or because we don't
    know these right now and a full comparison will be needed to find out if
    the file is clean.

    # about the file state on disk last time we saw it:
    - mtime: the last known clean mtime for the file.

    This value can be set to None if no cachable state exist. Either because we
    do not care (see previous section) or because we could not cache something
    yet.
    """

    _wc_tracked = attr.ib()
    _p1_tracked = attr.ib()
    _p2_info = attr.ib()
    _mode = attr.ib()
    _size = attr.ib()
    _mtime_s = attr.ib()
    _mtime_ns = attr.ib()
    _fallback_exec = attr.ib()
    _fallback_symlink = attr.ib()
    _mtime_second_ambiguous = attr.ib()

    def __init__(
        self,
        wc_tracked=False,
        p1_tracked=False,
        p2_info=False,
        has_meaningful_data=True,
        has_meaningful_mtime=True,
        parentfiledata=None,
        fallback_exec=None,
        fallback_symlink=None,
    ):
        self._wc_tracked = wc_tracked
        self._p1_tracked = p1_tracked
        self._p2_info = p2_info

        self._fallback_exec = fallback_exec
        self._fallback_symlink = fallback_symlink

        self._mode = None
        self._size = None
        self._mtime_s = None
        self._mtime_ns = None
        self._mtime_second_ambiguous = False
        if parentfiledata is None:
            has_meaningful_mtime = False
            has_meaningful_data = False
        elif parentfiledata[2] is None:
            has_meaningful_mtime = False
        if has_meaningful_data:
            self._mode = parentfiledata[0]
            self._size = parentfiledata[1]
        if has_meaningful_mtime:
            (
                self._mtime_s,
                self._mtime_ns,
                self._mtime_second_ambiguous,
            ) = parentfiledata[2]

    @classmethod
    def from_v2_data(cls, flags, size, mtime_s, mtime_ns):
        """Build a new DirstateItem object from V2 data"""
        has_mode_size = bool(flags & DIRSTATE_V2_HAS_MODE_AND_SIZE)
        has_meaningful_mtime = bool(flags & DIRSTATE_V2_HAS_MTIME)
        mode = None

        if flags & +DIRSTATE_V2_EXPECTED_STATE_IS_MODIFIED:
            # we do not have support for this flag in the code yet,
            # force a lookup for this file.
            has_mode_size = False
            has_meaningful_mtime = False

        fallback_exec = None
        if flags & DIRSTATE_V2_HAS_FALLBACK_EXEC:
            fallback_exec = flags & DIRSTATE_V2_FALLBACK_EXEC

        fallback_symlink = None
        if flags & DIRSTATE_V2_HAS_FALLBACK_SYMLINK:
            fallback_symlink = flags & DIRSTATE_V2_FALLBACK_SYMLINK

        if has_mode_size:
            assert stat.S_IXUSR == 0o100
            if flags & DIRSTATE_V2_MODE_EXEC_PERM:
                mode = 0o755
            else:
                mode = 0o644
            if flags & DIRSTATE_V2_MODE_IS_SYMLINK:
                mode |= stat.S_IFLNK
            else:
                mode |= stat.S_IFREG

        second_ambiguous = flags & DIRSTATE_V2_MTIME_SECOND_AMBIGUOUS
        return cls(
            wc_tracked=bool(flags & DIRSTATE_V2_WDIR_TRACKED),
            p1_tracked=bool(flags & DIRSTATE_V2_P1_TRACKED),
            p2_info=bool(flags & DIRSTATE_V2_P2_INFO),
            has_meaningful_data=has_mode_size,
            has_meaningful_mtime=has_meaningful_mtime,
            parentfiledata=(mode, size, (mtime_s, mtime_ns, second_ambiguous)),
            fallback_exec=fallback_exec,
            fallback_symlink=fallback_symlink,
        )

    @classmethod
    def from_v1_data(cls, state, mode, size, mtime):
        """Build a new DirstateItem object from V1 data

        Since the dirstate-v1 format is frozen, the signature of this function
        is not expected to change, unlike the __init__ one.
        """
        if state == b'm':
            return cls(wc_tracked=True, p1_tracked=True, p2_info=True)
        elif state == b'a':
            return cls(wc_tracked=True)
        elif state == b'r':
            if size == NONNORMAL:
                p1_tracked = True
                p2_info = True
            elif size == FROM_P2:
                p1_tracked = False
                p2_info = True
            else:
                p1_tracked = True
                p2_info = False
            return cls(p1_tracked=p1_tracked, p2_info=p2_info)
        elif state == b'n':
            if size == FROM_P2:
                return cls(wc_tracked=True, p2_info=True)
            elif size == NONNORMAL:
                return cls(wc_tracked=True, p1_tracked=True)
            elif mtime == AMBIGUOUS_TIME:
                return cls(
                    wc_tracked=True,
                    p1_tracked=True,
                    has_meaningful_mtime=False,
                    parentfiledata=(mode, size, (42, 0, False)),
                )
            else:
                return cls(
                    wc_tracked=True,
                    p1_tracked=True,
                    parentfiledata=(mode, size, (mtime, 0, False)),
                )
        else:
            raise RuntimeError(b'unknown state: %s' % state)

    def set_possibly_dirty(self):
        """Mark a file as "possibly dirty"

        This means the next status call will have to actually check its content
        to make sure it is correct.
        """
        self._mtime_s = None
        self._mtime_ns = None

    def set_clean(self, mode, size, mtime):
        """mark a file as "clean" cancelling potential "possibly dirty call"

        Note: this function is a descendant of `dirstate.normal` and is
        currently expected to be call on "normal" entry only. There are not
        reason for this to not change in the future as long as the ccode is
        updated to preserve the proper state of the non-normal files.
        """
        self._wc_tracked = True
        self._p1_tracked = True
        self._mode = mode
        self._size = size
        self._mtime_s, self._mtime_ns, self._mtime_second_ambiguous = mtime

    def set_tracked(self):
        """mark a file as tracked in the working copy

        This will ultimately be called by command like `hg add`.
        """
        self._wc_tracked = True
        # `set_tracked` is replacing various `normallookup` call. So we mark
        # the files as needing lookup
        #
        # Consider dropping this in the future in favor of something less broad.
        self._mtime_s = None
        self._mtime_ns = None

    def set_untracked(self):
        """mark a file as untracked in the working copy

        This will ultimately be called by command like `hg remove`.
        """
        self._wc_tracked = False
        self._mode = None
        self._size = None
        self._mtime_s = None
        self._mtime_ns = None

    def drop_merge_data(self):
        """remove all "merge-only" information from a DirstateItem

        This is to be call by the dirstatemap code when the second parent is dropped
        """
        if self._p2_info:
            self._p2_info = False
            self._mode = None
            self._size = None
            self._mtime_s = None
            self._mtime_ns = None

    @property
    def mode(self):
        return self._v1_mode()

    @property
    def size(self):
        return self._v1_size()

    @property
    def mtime(self):
        return self._v1_mtime()

    def mtime_likely_equal_to(self, other_mtime):
        self_sec = self._mtime_s
        if self_sec is None:
            return False
        self_ns = self._mtime_ns
        other_sec, other_ns, second_ambiguous = other_mtime
        if self_sec != other_sec:
            # seconds are different theses mtime are definitly not equal
            return False
        elif other_ns == 0 or self_ns == 0:
            # at least one side as no nano-seconds information

            if self._mtime_second_ambiguous:
                # We cannot trust the mtime in this case
                return False
            else:
                # the "seconds" value was reliable on its own. We are good to go.
                return True
        else:
            # We have nano second information, let us use them !
            return self_ns == other_ns

    @property
    def state(self):
        """
        States are:
          n  normal
          m  needs merging
          r  marked for removal
          a  marked for addition

        XXX This "state" is a bit obscure and mostly a direct expression of the
        dirstatev1 format. It would make sense to ultimately deprecate it in
        favor of the more "semantic" attributes.
        """
        if not self.any_tracked:
            return b'?'
        return self._v1_state()

    @property
    def has_fallback_exec(self):
        """True if "fallback" information are available for the "exec" bit

        Fallback information can be stored in the dirstate to keep track of
        filesystem attribute tracked by Mercurial when the underlying file
        system or operating system does not support that property, (e.g.
        Windows).

        Not all version of the dirstate on-disk storage support preserving this
        information.
        """
        return self._fallback_exec is not None

    @property
    def fallback_exec(self):
        """ "fallback" information for the executable bit

        True if the file should be considered executable when we cannot get
        this information from the files system. False if it should be
        considered non-executable.

        See has_fallback_exec for details."""
        return self._fallback_exec

    @fallback_exec.setter
    def set_fallback_exec(self, value):
        """control "fallback" executable bit

        Set to:
        - True if the file should be considered executable,
        - False if the file should be considered non-executable,
        - None if we do not have valid fallback data.

        See has_fallback_exec for details."""
        if value is None:
            self._fallback_exec = None
        else:
            self._fallback_exec = bool(value)

    @property
    def has_fallback_symlink(self):
        """True if "fallback" information are available for symlink status

        Fallback information can be stored in the dirstate to keep track of
        filesystem attribute tracked by Mercurial when the underlying file
        system or operating system does not support that property, (e.g.
        Windows).

        Not all version of the dirstate on-disk storage support preserving this
        information."""
        return self._fallback_symlink is not None

    @property
    def fallback_symlink(self):
        """ "fallback" information for symlink status

        True if the file should be considered executable when we cannot get
        this information from the files system. False if it should be
        considered non-executable.

        See has_fallback_exec for details."""
        return self._fallback_symlink

    @fallback_symlink.setter
    def set_fallback_symlink(self, value):
        """control "fallback" symlink status

        Set to:
        - True if the file should be considered a symlink,
        - False if the file should be considered not a symlink,
        - None if we do not have valid fallback data.

        See has_fallback_symlink for details."""
        if value is None:
            self._fallback_symlink = None
        else:
            self._fallback_symlink = bool(value)

    @property
    def tracked(self):
        """True is the file is tracked in the working copy"""
        return self._wc_tracked

    @property
    def any_tracked(self):
        """True is the file is tracked anywhere (wc or parents)"""
        return self._wc_tracked or self._p1_tracked or self._p2_info

    @property
    def added(self):
        """True if the file has been added"""
        return self._wc_tracked and not (self._p1_tracked or self._p2_info)

    @property
    def modified(self):
        """True if the file has been modified"""
        return self._wc_tracked and self._p1_tracked and self._p2_info

    @property
    def maybe_clean(self):
        """True if the file has a chance to be in the "clean" state"""
        if not self._wc_tracked:
            return False
        elif not self._p1_tracked:
            return False
        elif self._p2_info:
            return False
        return True

    @property
    def p1_tracked(self):
        """True if the file is tracked in the first parent manifest"""
        return self._p1_tracked

    @property
    def p2_info(self):
        """True if the file needed to merge or apply any input from p2

        See the class documentation for details.
        """
        return self._wc_tracked and self._p2_info

    @property
    def removed(self):
        """True if the file has been removed"""
        return not self._wc_tracked and (self._p1_tracked or self._p2_info)

    def v2_data(self):
        """Returns (flags, mode, size, mtime) for v2 serialization"""
        flags = 0
        if self._wc_tracked:
            flags |= DIRSTATE_V2_WDIR_TRACKED
        if self._p1_tracked:
            flags |= DIRSTATE_V2_P1_TRACKED
        if self._p2_info:
            flags |= DIRSTATE_V2_P2_INFO
        if self._mode is not None and self._size is not None:
            flags |= DIRSTATE_V2_HAS_MODE_AND_SIZE
            if self.mode & stat.S_IXUSR:
                flags |= DIRSTATE_V2_MODE_EXEC_PERM
            if stat.S_ISLNK(self.mode):
                flags |= DIRSTATE_V2_MODE_IS_SYMLINK
        if self._mtime_s is not None:
            flags |= DIRSTATE_V2_HAS_MTIME
        if self._mtime_second_ambiguous:
            flags |= DIRSTATE_V2_MTIME_SECOND_AMBIGUOUS

        if self._fallback_exec is not None:
            flags |= DIRSTATE_V2_HAS_FALLBACK_EXEC
            if self._fallback_exec:
                flags |= DIRSTATE_V2_FALLBACK_EXEC

        if self._fallback_symlink is not None:
            flags |= DIRSTATE_V2_HAS_FALLBACK_SYMLINK
            if self._fallback_symlink:
                flags |= DIRSTATE_V2_FALLBACK_SYMLINK

        # Note: we do not need to do anything regarding
        # DIRSTATE_V2_ALL_UNKNOWN_RECORDED and DIRSTATE_V2_ALL_IGNORED_RECORDED
        # since we never set _DIRSTATE_V2_HAS_DIRCTORY_MTIME
        return (flags, self._size or 0, self._mtime_s or 0, self._mtime_ns or 0)

    def _v1_state(self):
        """return a "state" suitable for v1 serialization"""
        if not self.any_tracked:
            # the object has no state to record, this is -currently-
            # unsupported
            raise RuntimeError('untracked item')
        elif self.removed:
            return b'r'
        elif self._p1_tracked and self._p2_info:
            return b'm'
        elif self.added:
            return b'a'
        else:
            return b'n'

    def _v1_mode(self):
        """return a "mode" suitable for v1 serialization"""
        return self._mode if self._mode is not None else 0

    def _v1_size(self):
        """return a "size" suitable for v1 serialization"""
        if not self.any_tracked:
            # the object has no state to record, this is -currently-
            # unsupported
            raise RuntimeError('untracked item')
        elif self.removed and self._p1_tracked and self._p2_info:
            return NONNORMAL
        elif self._p2_info:
            return FROM_P2
        elif self.removed:
            return 0
        elif self.added:
            return NONNORMAL
        elif self._size is None:
            return NONNORMAL
        else:
            return self._size

    def _v1_mtime(self):
        """return a "mtime" suitable for v1 serialization"""
        if not self.any_tracked:
            # the object has no state to record, this is -currently-
            # unsupported
            raise RuntimeError('untracked item')
        elif self.removed:
            return 0
        elif self._mtime_s is None:
            return AMBIGUOUS_TIME
        elif self._p2_info:
            return AMBIGUOUS_TIME
        elif not self._p1_tracked:
            return AMBIGUOUS_TIME
        elif self._mtime_second_ambiguous:
            return AMBIGUOUS_TIME
        else:
            return self._mtime_s


def gettype(q):
    return int(q & 0xFFFF)


class BaseIndexObject:
    # Can I be passed to an algorithme implemented in Rust ?
    rust_ext_compat = 0
    # Format of an index entry according to Python's `struct` language
    index_format = revlog_constants.INDEX_ENTRY_V1
    # Size of a C unsigned long long int, platform independent
    big_int_size = struct.calcsize(b'>Q')
    # Size of a C long int, platform independent
    int_size = struct.calcsize(b'>i')
    # An empty index entry, used as a default value to be overridden, or nullrev
    null_item = (
        0,
        0,
        0,
        -1,
        -1,
        -1,
        -1,
        sha1nodeconstants.nullid,
        0,
        0,
        revlog_constants.COMP_MODE_INLINE,
        revlog_constants.COMP_MODE_INLINE,
        revlog_constants.RANK_UNKNOWN,
    )

    @util.propertycache
    def entry_size(self):
        return self.index_format.size

    @util.propertycache
    def _nodemap(self):
        nodemap = nodemaputil.NodeMap({sha1nodeconstants.nullid: nullrev})
        for r in range(0, len(self)):
            n = self[r][7]
            nodemap[n] = r
        return nodemap

    def has_node(self, node):
        """return True if the node exist in the index"""
        return node in self._nodemap

    def rev(self, node):
        """return a revision for a node

        If the node is unknown, raise a RevlogError"""
        return self._nodemap[node]

    def get_rev(self, node):
        """return a revision for a node

        If the node is unknown, return None"""
        return self._nodemap.get(node)

    def _stripnodes(self, start):
        if '_nodemap' in vars(self):
            for r in range(start, len(self)):
                n = self[r][7]
                del self._nodemap[n]

    def clearcaches(self):
        self.__dict__.pop('_nodemap', None)

    def __len__(self):
        return self._lgt + len(self._extra)

    def append(self, tup):
        if '_nodemap' in vars(self):
            self._nodemap[tup[7]] = len(self)
        data = self._pack_entry(len(self), tup)
        self._extra.append(data)

    def _pack_entry(self, rev, entry):
        assert entry[8] == 0
        assert entry[9] == 0
        return self.index_format.pack(*entry[:8])

    def _check_index(self, i):
        if not isinstance(i, int):
            raise TypeError(b"expecting int indexes")
        if i < 0 or i >= len(self):
            raise IndexError(i)

    def __getitem__(self, i):
        if i == -1:
            return self.null_item
        self._check_index(i)
        if i >= self._lgt:
            data = self._extra[i - self._lgt]
        else:
            index = self._calculate_index(i)
            data = self._data[index : index + self.entry_size]
        r = self._unpack_entry(i, data)
        if self._lgt and i == 0:
            offset = revlogutils.offset_type(0, gettype(r[0]))
            r = (offset,) + r[1:]
        return r

    def _unpack_entry(self, rev, data):
        r = self.index_format.unpack(data)
        r = r + (
            0,
            0,
            revlog_constants.COMP_MODE_INLINE,
            revlog_constants.COMP_MODE_INLINE,
            revlog_constants.RANK_UNKNOWN,
        )
        return r

    def pack_header(self, header):
        """pack header information as binary"""
        v_fmt = revlog_constants.INDEX_HEADER
        return v_fmt.pack(header)

    def entry_binary(self, rev):
        """return the raw binary string representing a revision"""
        entry = self[rev]
        p = revlog_constants.INDEX_ENTRY_V1.pack(*entry[:8])
        if rev == 0:
            p = p[revlog_constants.INDEX_HEADER.size :]
        return p


class IndexObject(BaseIndexObject):
    def __init__(self, data):
        assert len(data) % self.entry_size == 0, (
            len(data),
            self.entry_size,
            len(data) % self.entry_size,
        )
        self._data = data
        self._lgt = len(data) // self.entry_size
        self._extra = []

    def _calculate_index(self, i):
        return i * self.entry_size

    def __delitem__(self, i):
        if not isinstance(i, slice) or not i.stop == -1 or i.step is not None:
            raise ValueError(b"deleting slices only supports a:-1 with step 1")
        i = i.start
        self._check_index(i)
        self._stripnodes(i)
        if i < self._lgt:
            self._data = self._data[: i * self.entry_size]
            self._lgt = i
            self._extra = []
        else:
            self._extra = self._extra[: i - self._lgt]


class PersistentNodeMapIndexObject(IndexObject):
    """a Debug oriented class to test persistent nodemap

    We need a simple python object to test API and higher level behavior. See
    the Rust implementation for  more serious usage. This should be used only
    through the dedicated `devel.persistent-nodemap` config.
    """

    def nodemap_data_all(self):
        """Return bytes containing a full serialization of a nodemap

        The nodemap should be valid for the full set of revisions in the
        index."""
        return nodemaputil.persistent_data(self)

    def nodemap_data_incremental(self):
        """Return bytes containing a incremental update to persistent nodemap

        This containst the data for an append-only update of the data provided
        in the last call to `update_nodemap_data`.
        """
        if self._nm_root is None:
            return None
        docket = self._nm_docket
        changed, data = nodemaputil.update_persistent_data(
            self, self._nm_root, self._nm_max_idx, self._nm_docket.tip_rev
        )

        self._nm_root = self._nm_max_idx = self._nm_docket = None
        return docket, changed, data

    def update_nodemap_data(self, docket, nm_data):
        """provide full block of persisted binary data for a nodemap

        The data are expected to come from disk. See `nodemap_data_all` for a
        produceur of such data."""
        if nm_data is not None:
            self._nm_root, self._nm_max_idx = nodemaputil.parse_data(nm_data)
            if self._nm_root:
                self._nm_docket = docket
            else:
                self._nm_root = self._nm_max_idx = self._nm_docket = None


class InlinedIndexObject(BaseIndexObject):
    def __init__(self, data, inline=0):
        self._data = data
        self._lgt = self._inline_scan(None)
        self._inline_scan(self._lgt)
        self._extra = []

    def _inline_scan(self, lgt):
        off = 0
        if lgt is not None:
            self._offsets = [0] * lgt
        count = 0
        while off <= len(self._data) - self.entry_size:
            start = off + self.big_int_size
            (s,) = struct.unpack(
                b'>i',
                self._data[start : start + self.int_size],
            )
            if lgt is not None:
                self._offsets[count] = off
            count += 1
            off += self.entry_size + s
        if off != len(self._data):
            raise ValueError(b"corrupted data")
        return count

    def __delitem__(self, i):
        if not isinstance(i, slice) or not i.stop == -1 or i.step is not None:
            raise ValueError(b"deleting slices only supports a:-1 with step 1")
        i = i.start
        self._check_index(i)
        self._stripnodes(i)
        if i < self._lgt:
            self._offsets = self._offsets[:i]
            self._lgt = i
            self._extra = []
        else:
            self._extra = self._extra[: i - self._lgt]

    def _calculate_index(self, i):
        return self._offsets[i]


def parse_index2(data, inline, format=revlog_constants.REVLOGV1):
    if format == revlog_constants.CHANGELOGV2:
        return parse_index_cl_v2(data)
    if not inline:
        if format == revlog_constants.REVLOGV2:
            cls = IndexObject2
        else:
            cls = IndexObject
        return cls(data), None
    cls = InlinedIndexObject
    return cls(data, inline), (0, data)


def parse_index_cl_v2(data):
    return IndexChangelogV2(data), None


class IndexObject2(IndexObject):
    index_format = revlog_constants.INDEX_ENTRY_V2

    def replace_sidedata_info(
        self,
        rev,
        sidedata_offset,
        sidedata_length,
        offset_flags,
        compression_mode,
    ):
        """
        Replace an existing index entry's sidedata offset and length with new
        ones.
        This cannot be used outside of the context of sidedata rewriting,
        inside the transaction that creates the revision `rev`.
        """
        if rev < 0:
            raise KeyError
        self._check_index(rev)
        if rev < self._lgt:
            msg = b"cannot rewrite entries outside of this transaction"
            raise KeyError(msg)
        else:
            entry = list(self[rev])
            entry[0] = offset_flags
            entry[8] = sidedata_offset
            entry[9] = sidedata_length
            entry[11] = compression_mode
            entry = tuple(entry)
            new = self._pack_entry(rev, entry)
            self._extra[rev - self._lgt] = new

    def _unpack_entry(self, rev, data):
        data = self.index_format.unpack(data)
        entry = data[:10]
        data_comp = data[10] & 3
        sidedata_comp = (data[10] & (3 << 2)) >> 2
        return entry + (data_comp, sidedata_comp, revlog_constants.RANK_UNKNOWN)

    def _pack_entry(self, rev, entry):
        data = entry[:10]
        data_comp = entry[10] & 3
        sidedata_comp = (entry[11] & 3) << 2
        data += (data_comp | sidedata_comp,)

        return self.index_format.pack(*data)

    def entry_binary(self, rev):
        """return the raw binary string representing a revision"""
        entry = self[rev]
        return self._pack_entry(rev, entry)

    def pack_header(self, header):
        """pack header information as binary"""
        msg = 'version header should go in the docket, not the index: %d'
        msg %= header
        raise error.ProgrammingError(msg)


class IndexChangelogV2(IndexObject2):
    index_format = revlog_constants.INDEX_ENTRY_CL_V2

    null_item = (
        IndexObject2.null_item[: revlog_constants.ENTRY_RANK]
        + (0,)  # rank of null is 0
        + IndexObject2.null_item[revlog_constants.ENTRY_RANK :]
    )

    def _unpack_entry(self, rev, data, r=True):
        items = self.index_format.unpack(data)
        return (
            items[revlog_constants.INDEX_ENTRY_V2_IDX_OFFSET],
            items[revlog_constants.INDEX_ENTRY_V2_IDX_COMPRESSED_LENGTH],
            items[revlog_constants.INDEX_ENTRY_V2_IDX_UNCOMPRESSED_LENGTH],
            rev,
            rev,
            items[revlog_constants.INDEX_ENTRY_V2_IDX_PARENT_1],
            items[revlog_constants.INDEX_ENTRY_V2_IDX_PARENT_2],
            items[revlog_constants.INDEX_ENTRY_V2_IDX_NODEID],
            items[revlog_constants.INDEX_ENTRY_V2_IDX_SIDEDATA_OFFSET],
            items[
                revlog_constants.INDEX_ENTRY_V2_IDX_SIDEDATA_COMPRESSED_LENGTH
            ],
            items[revlog_constants.INDEX_ENTRY_V2_IDX_COMPRESSION_MODE] & 3,
            (items[revlog_constants.INDEX_ENTRY_V2_IDX_COMPRESSION_MODE] >> 2)
            & 3,
            items[revlog_constants.INDEX_ENTRY_V2_IDX_RANK],
        )

    def _pack_entry(self, rev, entry):

        base = entry[revlog_constants.ENTRY_DELTA_BASE]
        link_rev = entry[revlog_constants.ENTRY_LINK_REV]
        assert base == rev, (base, rev)
        assert link_rev == rev, (link_rev, rev)
        data = (
            entry[revlog_constants.ENTRY_DATA_OFFSET],
            entry[revlog_constants.ENTRY_DATA_COMPRESSED_LENGTH],
            entry[revlog_constants.ENTRY_DATA_UNCOMPRESSED_LENGTH],
            entry[revlog_constants.ENTRY_PARENT_1],
            entry[revlog_constants.ENTRY_PARENT_2],
            entry[revlog_constants.ENTRY_NODE_ID],
            entry[revlog_constants.ENTRY_SIDEDATA_OFFSET],
            entry[revlog_constants.ENTRY_SIDEDATA_COMPRESSED_LENGTH],
            entry[revlog_constants.ENTRY_DATA_COMPRESSION_MODE] & 3
            | (entry[revlog_constants.ENTRY_SIDEDATA_COMPRESSION_MODE] & 3)
            << 2,
            entry[revlog_constants.ENTRY_RANK],
        )
        return self.index_format.pack(*data)


def parse_index_devel_nodemap(data, inline):
    """like parse_index2, but alway return a PersistentNodeMapIndexObject"""
    return PersistentNodeMapIndexObject(data), None


def parse_dirstate(dmap, copymap, st):
    parents = [st[:20], st[20:40]]
    # dereference fields so they will be local in loop
    format = b">cllll"
    e_size = struct.calcsize(format)
    pos1 = 40
    l = len(st)

    # the inner loop
    while pos1 < l:
        pos2 = pos1 + e_size
        e = _unpack(b">cllll", st[pos1:pos2])  # a literal here is faster
        pos1 = pos2 + e[4]
        f = st[pos2:pos1]
        if b'\0' in f:
            f, c = f.split(b'\0')
            copymap[f] = c
        dmap[f] = DirstateItem.from_v1_data(*e[:4])
    return parents


def pack_dirstate(dmap, copymap, pl):
    cs = stringio()
    write = cs.write
    write(b"".join(pl))
    for f, e in dmap.items():
        if f in copymap:
            f = b"%s\0%s" % (f, copymap[f])
        e = _pack(
            b">cllll",
            e._v1_state(),
            e._v1_mode(),
            e._v1_size(),
            e._v1_mtime(),
            len(f),
        )
        write(e)
        write(f)
    return cs.getvalue()
