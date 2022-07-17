# parsers.py - Python implementation of parsers.c
#
# Copyright 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import struct
import zlib

from ..node import (
    nullrev,
    sha1nodeconstants,
)
from ..thirdparty import attr
from .. import (
    error,
    pycompat,
    revlogutils,
    util,
)

from ..revlogutils import nodemap as nodemaputil
from ..revlogutils import constants as revlog_constants

stringio = pycompat.bytesio


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


@attr.s(slots=True, init=False)
class DirstateItem(object):
    """represent a dirstate entry

    It contains:

    - state (one of 'n', 'a', 'r', 'm')
    - mode,
    - size,
    - mtime,
    """

    _state = attr.ib()
    _mode = attr.ib()
    _size = attr.ib()
    _mtime = attr.ib()

    def __init__(self, state, mode, size, mtime):
        self._state = state
        self._mode = mode
        self._size = size
        self._mtime = mtime

    @classmethod
    def from_v1_data(cls, state, mode, size, mtime):
        """Build a new DirstateItem object from V1 data

        Since the dirstate-v1 format is frozen, the signature of this function
        is not expected to change, unlike the __init__ one.
        """
        return cls(
            state=state,
            mode=mode,
            size=size,
            mtime=mtime,
        )

    def set_possibly_dirty(self):
        """Mark a file as "possibly dirty"

        This means the next status call will have to actually check its content
        to make sure it is correct.
        """
        self._mtime = AMBIGUOUS_TIME

    def __getitem__(self, idx):
        if idx == 0 or idx == -4:
            msg = b"do not use item[x], use item.state"
            util.nouideprecwarn(msg, b'6.0', stacklevel=2)
            return self._state
        elif idx == 1 or idx == -3:
            msg = b"do not use item[x], use item.mode"
            util.nouideprecwarn(msg, b'6.0', stacklevel=2)
            return self._mode
        elif idx == 2 or idx == -2:
            msg = b"do not use item[x], use item.size"
            util.nouideprecwarn(msg, b'6.0', stacklevel=2)
            return self._size
        elif idx == 3 or idx == -1:
            msg = b"do not use item[x], use item.mtime"
            util.nouideprecwarn(msg, b'6.0', stacklevel=2)
            return self._mtime
        else:
            raise IndexError(idx)

    @property
    def mode(self):
        return self._mode

    @property
    def size(self):
        return self._size

    @property
    def mtime(self):
        return self._mtime

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
        return self._state

    @property
    def tracked(self):
        """True is the file is tracked in the working copy"""
        return self._state in b"nma"

    @property
    def added(self):
        """True if the file has been added"""
        return self._state == b'a'

    @property
    def merged(self):
        """True if the file has been merged

        Should only be set if a merge is in progress in the dirstate
        """
        return self._state == b'm'

    @property
    def from_p2(self):
        """True if the file have been fetched from p2 during the current merge

        This is only True is the file is currently tracked.

        Should only be set if a merge is in progress in the dirstate
        """
        return self._state == b'n' and self._size == FROM_P2

    @property
    def from_p2_removed(self):
        """True if the file has been removed, but was "from_p2" initially

        This property seems like an abstraction leakage and should probably be
        dealt in this class (or maybe the dirstatemap) directly.
        """
        return self._state == b'r' and self._size == FROM_P2

    @property
    def removed(self):
        """True if the file has been removed"""
        return self._state == b'r'

    @property
    def merged_removed(self):
        """True if the file has been removed, but was "merged" initially

        This property seems like an abstraction leakage and should probably be
        dealt in this class (or maybe the dirstatemap)  directly.
        """
        return self._state == b'r' and self._size == NONNORMAL

    @property
    def dm_nonnormal(self):
        """True is the entry is non-normal in the dirstatemap sense

        There is no reason for any code, but the dirstatemap one to use this.
        """
        return self.state != b'n' or self.mtime == AMBIGUOUS_TIME

    @property
    def dm_otherparent(self):
        """True is the entry is `otherparent` in the dirstatemap sense

        There is no reason for any code, but the dirstatemap one to use this.
        """
        return self._size == FROM_P2

    def v1_state(self):
        """return a "state" suitable for v1 serialization"""
        return self._state

    def v1_mode(self):
        """return a "mode" suitable for v1 serialization"""
        return self._mode

    def v1_size(self):
        """return a "size" suitable for v1 serialization"""
        return self._size

    def v1_mtime(self):
        """return a "mtime" suitable for v1 serialization"""
        return self._mtime

    def need_delay(self, now):
        """True if the stored mtime would be ambiguous with the current time"""
        return self._state == b'n' and self._mtime == now


def gettype(q):
    return int(q & 0xFFFF)


class BaseIndexObject(object):
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
    )

    @util.propertycache
    def entry_size(self):
        return self.index_format.size

    @property
    def nodemap(self):
        msg = b"index.nodemap is deprecated, use index.[has_node|rev|get_rev]"
        util.nouideprecwarn(msg, b'5.3', stacklevel=2)
        return self._nodemap

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
            raise IndexError

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


def parse_index2(data, inline, revlogv2=False):
    if not inline:
        cls = IndexObject2 if revlogv2 else IndexObject
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
        return entry + (data_comp, sidedata_comp)

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

    def _unpack_entry(self, rev, data, r=True):
        items = self.index_format.unpack(data)
        entry = items[:3] + (rev, rev) + items[3:8]
        data_comp = items[8] & 3
        sidedata_comp = (items[8] >> 2) & 3
        return entry + (data_comp, sidedata_comp)

    def _pack_entry(self, rev, entry):
        assert entry[3] == rev, entry[3]
        assert entry[4] == rev, entry[4]
        data = entry[:3] + entry[5:10]
        data_comp = entry[10] & 3
        sidedata_comp = (entry[11] & 3) << 2
        data += (data_comp | sidedata_comp,)
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


def pack_dirstate(dmap, copymap, pl, now):
    now = int(now)
    cs = stringio()
    write = cs.write
    write(b"".join(pl))
    for f, e in pycompat.iteritems(dmap):
        if e.need_delay(now):
            # The file was last modified "simultaneously" with the current
            # write to dirstate (i.e. within the same second for file-
            # systems with a granularity of 1 sec). This commonly happens
            # for at least a couple of files on 'update'.
            # The user could change the file without changing its size
            # within the same second. Invalidate the file's mtime in
            # dirstate, forcing future 'status' calls to compare the
            # contents of the file if the size is the same. This prevents
            # mistakenly treating such files as clean.
            e.set_possibly_dirty()

        if f in copymap:
            f = b"%s\0%s" % (f, copymap[f])
        e = _pack(
            b">cllll",
            e.v1_state(),
            e.v1_mode(),
            e.v1_size(),
            e.v1_mtime(),
            len(f),
        )
        write(e)
        write(f)
    return cs.getvalue()
