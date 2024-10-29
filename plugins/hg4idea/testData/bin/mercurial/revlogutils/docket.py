# docket - code related to revlog "docket"
#
# Copyright 2021 Pierre-Yves David <pierre-yves.david@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

### Revlog docket file
#
# The revlog is stored on disk using multiple files:
#
# * a small docket file, containing metadata and a pointer,
#
# * an index file, containing fixed width information about revisions,
#
# * a data file, containing variable width data for these revisions,


import os
import random
import struct

from .. import (
    encoding,
    error,
    node,
    util,
)

from . import (
    constants,
)


def make_uid(id_size=8):
    """return a new unique identifier.

    The identifier is random and composed of ascii characters."""
    # size we "hex" the result we need half the number of bits to have a final
    # uuid of size ID_SIZE
    return node.hex(os.urandom(id_size // 2))


# some special test logic to avoid anoying random output in the test
stable_docket_file = encoding.environ.get(b'HGTEST_UUIDFILE')

if stable_docket_file:

    def make_uid(id_size=8):
        try:
            with open(stable_docket_file, mode='rb') as f:
                seed = f.read().strip()
        except FileNotFoundError:
            seed = b'04'  # chosen by a fair dice roll. garanteed to be random
        iter_seed = iter(seed)
        # some basic circular sum hashing on 64 bits
        int_seed = 0
        low_mask = int('1' * 35, 2)
        for i in iter_seed:
            high_part = int_seed >> 35
            low_part = (int_seed & low_mask) << 28
            int_seed = high_part + low_part + i
        r = random.Random()
        r.seed(int_seed, version=1)
        # once we drop python 3.8 support we can simply use r.randbytes
        raw = r.getrandbits(id_size * 4)
        assert id_size == 8
        p = struct.pack('>L', raw)
        new = node.hex(p)
        with open(stable_docket_file, 'wb') as f:
            f.write(new)
        return new


# Docket format
#
# * 4 bytes: revlog version
#          |   This is mandatory as docket must be compatible with the previous
#          |   revlog index header.
# * 1 bytes: size of index uuid
# * 1 bytes: number of outdated index uuid
# * 1 bytes: size of data uuid
# * 1 bytes: number of outdated data uuid
# * 1 bytes: size of sizedata uuid
# * 1 bytes: number of outdated data uuid
# * 8 bytes: size of index-data
# * 8 bytes: pending size of index-data
# * 8 bytes: size of data
# * 8 bytes: size of sidedata
# * 8 bytes: pending size of data
# * 8 bytes: pending size of sidedata
# * 1 bytes: default compression header
S_HEADER = struct.Struct(constants.INDEX_HEADER_FMT + b'BBBBBBQQQQQQc')
# * 1 bytes: size of index uuid
# * 8 bytes: size of file
S_OLD_UID = struct.Struct('>BL')


class RevlogDocket:
    """metadata associated with revlog"""

    def __init__(
        self,
        revlog,
        use_pending=False,
        version_header=None,
        index_uuid=None,
        older_index_uuids=(),
        data_uuid=None,
        older_data_uuids=(),
        sidedata_uuid=None,
        older_sidedata_uuids=(),
        index_end=0,
        pending_index_end=0,
        data_end=0,
        pending_data_end=0,
        sidedata_end=0,
        pending_sidedata_end=0,
        default_compression_header=None,
    ):
        self._version_header = version_header
        self._read_only = bool(use_pending)
        self._dirty = False
        self._radix = revlog.radix
        self._path = revlog._docket_file
        self._opener = revlog.opener
        self._index_uuid = index_uuid
        self._older_index_uuids = older_index_uuids
        self._data_uuid = data_uuid
        self._older_data_uuids = older_data_uuids
        self._sidedata_uuid = sidedata_uuid
        self._older_sidedata_uuids = older_sidedata_uuids
        assert not set(older_index_uuids) & set(older_data_uuids)
        assert not set(older_data_uuids) & set(older_sidedata_uuids)
        assert not set(older_index_uuids) & set(older_sidedata_uuids)
        # thes asserts should be True as long as we have a single index filename
        assert index_end <= pending_index_end
        assert data_end <= pending_data_end
        assert sidedata_end <= pending_sidedata_end
        self._initial_index_end = index_end
        self._pending_index_end = pending_index_end
        self._initial_data_end = data_end
        self._pending_data_end = pending_data_end
        self._initial_sidedata_end = sidedata_end
        self._pending_sidedata_end = pending_sidedata_end
        if use_pending:
            self._index_end = self._pending_index_end
            self._data_end = self._pending_data_end
            self._sidedata_end = self._pending_sidedata_end
        else:
            self._index_end = self._initial_index_end
            self._data_end = self._initial_data_end
            self._sidedata_end = self._initial_sidedata_end
        self.default_compression_header = default_compression_header

    def index_filepath(self):
        """file path to the current index file associated to this docket"""
        # very simplistic version at first
        if self._index_uuid is None:
            self._index_uuid = make_uid()
        return b"%s-%s.idx" % (self._radix, self._index_uuid)

    def new_index_file(self):
        """switch index file to a new UID

        The previous index UID is moved to the "older" list."""
        old = (self._index_uuid, self._index_end)
        self._older_index_uuids.insert(0, old)
        self._index_uuid = make_uid()
        return self.index_filepath()

    def old_index_filepaths(self, include_empty=True):
        """yield file path to older index files associated to this docket"""
        # very simplistic version at first
        for uuid, size in self._older_index_uuids:
            if include_empty or size > 0:
                yield b"%s-%s.idx" % (self._radix, uuid)

    def data_filepath(self):
        """file path to the current data file associated to this docket"""
        # very simplistic version at first
        if self._data_uuid is None:
            self._data_uuid = make_uid()
        return b"%s-%s.dat" % (self._radix, self._data_uuid)

    def new_data_file(self):
        """switch data file to a new UID

        The previous data UID is moved to the "older" list."""
        old = (self._data_uuid, self._data_end)
        self._older_data_uuids.insert(0, old)
        self._data_uuid = make_uid()
        return self.data_filepath()

    def old_data_filepaths(self, include_empty=True):
        """yield file path to older data files associated to this docket"""
        # very simplistic version at first
        for uuid, size in self._older_data_uuids:
            if include_empty or size > 0:
                yield b"%s-%s.dat" % (self._radix, uuid)

    def sidedata_filepath(self):
        """file path to the current sidedata file associated to this docket"""
        # very simplistic version at first
        if self._sidedata_uuid is None:
            self._sidedata_uuid = make_uid()
        return b"%s-%s.sda" % (self._radix, self._sidedata_uuid)

    def new_sidedata_file(self):
        """switch sidedata file to a new UID

        The previous sidedata UID is moved to the "older" list."""
        old = (self._sidedata_uuid, self._sidedata_end)
        self._older_sidedata_uuids.insert(0, old)
        self._sidedata_uuid = make_uid()
        return self.sidedata_filepath()

    def old_sidedata_filepaths(self, include_empty=True):
        """yield file path to older sidedata files associated to this docket"""
        # very simplistic version at first
        for uuid, size in self._older_sidedata_uuids:
            if include_empty or size > 0:
                yield b"%s-%s.sda" % (self._radix, uuid)

    @property
    def index_end(self):
        return self._index_end

    @index_end.setter
    def index_end(self, new_size):
        if new_size != self._index_end:
            self._index_end = new_size
            self._dirty = True

    @property
    def data_end(self):
        return self._data_end

    @data_end.setter
    def data_end(self, new_size):
        if new_size != self._data_end:
            self._data_end = new_size
            self._dirty = True

    @property
    def sidedata_end(self):
        return self._sidedata_end

    @sidedata_end.setter
    def sidedata_end(self, new_size):
        if new_size != self._sidedata_end:
            self._sidedata_end = new_size
            self._dirty = True

    def write(self, transaction, pending=False, stripping=False):
        """write the modification of disk if any

        This make the new content visible to all process"""
        if not self._dirty:
            return False
        else:
            if self._read_only:
                msg = b'writing read-only docket: %s'
                msg %= self._path
                raise error.ProgrammingError(msg)
            if not stripping:
                # XXX we could, leverage the docket while stripping. However it
                # is not powerfull enough at the time of this comment
                transaction.addbackup(self._path, location=b'store')
            with self._opener(self._path, mode=b'w', atomictemp=True) as f:
                f.write(self._serialize(pending=pending))
            # if pending we still need to the write final data eventually
            self._dirty = pending
            return True

    def _serialize(self, pending=False):
        if pending:
            official_index_end = self._initial_index_end
            official_data_end = self._initial_data_end
            official_sidedata_end = self._initial_sidedata_end
        else:
            official_index_end = self._index_end
            official_data_end = self._data_end
            official_sidedata_end = self._sidedata_end

        # this assert should be True as long as we have a single index filename
        assert official_data_end <= self._data_end
        assert official_sidedata_end <= self._sidedata_end
        data = (
            self._version_header,
            len(self._index_uuid),
            len(self._older_index_uuids),
            len(self._data_uuid),
            len(self._older_data_uuids),
            len(self._sidedata_uuid),
            len(self._older_sidedata_uuids),
            official_index_end,
            self._index_end,
            official_data_end,
            self._data_end,
            official_sidedata_end,
            self._sidedata_end,
            self.default_compression_header,
        )
        s = []
        s.append(S_HEADER.pack(*data))

        s.append(self._index_uuid)
        for u, size in self._older_index_uuids:
            s.append(S_OLD_UID.pack(len(u), size))
        for u, size in self._older_index_uuids:
            s.append(u)

        s.append(self._data_uuid)
        for u, size in self._older_data_uuids:
            s.append(S_OLD_UID.pack(len(u), size))
        for u, size in self._older_data_uuids:
            s.append(u)

        s.append(self._sidedata_uuid)
        for u, size in self._older_sidedata_uuids:
            s.append(S_OLD_UID.pack(len(u), size))
        for u, size in self._older_sidedata_uuids:
            s.append(u)
        return b''.join(s)


def default_docket(revlog, version_header):
    """given a revlog version a new docket object for the given revlog"""
    rl_version = version_header & 0xFFFF
    if rl_version not in (constants.REVLOGV2, constants.CHANGELOGV2):
        return None
    comp = util.compengines[
        revlog.feature_config.compression_engine
    ].revlogheader()
    docket = RevlogDocket(
        revlog,
        version_header=version_header,
        default_compression_header=comp,
    )
    docket._dirty = True
    return docket


def _parse_old_uids(get_data, count):
    all_sizes = []
    all_uids = []
    for i in range(0, count):
        raw = get_data(S_OLD_UID.size)
        all_sizes.append(S_OLD_UID.unpack(raw))

    for uid_size, file_size in all_sizes:
        uid = get_data(uid_size)
        all_uids.append((uid, file_size))
    return all_uids


def parse_docket(revlog, data, use_pending=False):
    """given some docket data return a docket object for the given revlog"""
    header = S_HEADER.unpack(data[: S_HEADER.size])

    # this is a mutable closure capture used in `get_data`
    offset = [S_HEADER.size]

    def get_data(size):
        """utility closure to access the `size` next bytes"""
        if offset[0] + size > len(data):
            # XXX better class
            msg = b"docket is too short, expected %d got %d"
            msg %= (offset[0] + size, len(data))
            raise error.Abort(msg)
        raw = data[offset[0] : offset[0] + size]
        offset[0] += size
        return raw

    iheader = iter(header)

    version_header = next(iheader)

    index_uuid_size = next(iheader)
    index_uuid = get_data(index_uuid_size)

    older_index_uuid_count = next(iheader)
    older_index_uuids = _parse_old_uids(get_data, older_index_uuid_count)

    data_uuid_size = next(iheader)
    data_uuid = get_data(data_uuid_size)

    older_data_uuid_count = next(iheader)
    older_data_uuids = _parse_old_uids(get_data, older_data_uuid_count)

    sidedata_uuid_size = next(iheader)
    sidedata_uuid = get_data(sidedata_uuid_size)

    older_sidedata_uuid_count = next(iheader)
    older_sidedata_uuids = _parse_old_uids(get_data, older_sidedata_uuid_count)

    index_size = next(iheader)

    pending_index_size = next(iheader)

    data_size = next(iheader)

    pending_data_size = next(iheader)

    sidedata_size = next(iheader)

    pending_sidedata_size = next(iheader)

    default_compression_header = next(iheader)

    docket = RevlogDocket(
        revlog,
        use_pending=use_pending,
        version_header=version_header,
        index_uuid=index_uuid,
        older_index_uuids=older_index_uuids,
        data_uuid=data_uuid,
        older_data_uuids=older_data_uuids,
        sidedata_uuid=sidedata_uuid,
        older_sidedata_uuids=older_sidedata_uuids,
        index_end=index_size,
        pending_index_end=pending_index_size,
        data_end=data_size,
        pending_data_end=pending_data_size,
        sidedata_end=sidedata_size,
        pending_sidedata_end=pending_sidedata_size,
        default_compression_header=default_compression_header,
    )
    return docket
