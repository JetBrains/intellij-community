# dirstatedocket.py - docket file for dirstate-v2
#
# Copyright Mercurial Contributors
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import struct

from ..revlogutils import docket as docket_mod
from . import v2

V2_FORMAT_MARKER = b"dirstate-v2\n"

# * 12 bytes: format marker
# * 32 bytes: node ID of the working directory's first parent
# * 32 bytes: node ID of the working directory's second parent
# * {TREE_METADATA_SIZE} bytes: tree metadata, parsed separately
# * 4 bytes: big-endian used size of the data file
# * 1 byte: length of the data file's UUID
# * variable: data file's UUID
#
# Node IDs are null-padded if shorter than 32 bytes.
# A data file shorter than the specified used size is corrupted (truncated)
HEADER = struct.Struct(
    ">{}s32s32s{}sLB".format(len(V2_FORMAT_MARKER), v2.TREE_METADATA_SIZE)
)


class DirstateDocket:
    data_filename_pattern = b'dirstate.%s'

    def __init__(self, parents, data_size, tree_metadata, uuid):
        self.parents = parents
        self.data_size = data_size
        self.tree_metadata = tree_metadata
        self.uuid = uuid

    @classmethod
    def with_new_uuid(cls, parents, data_size, tree_metadata):
        return cls(parents, data_size, tree_metadata, docket_mod.make_uid())

    @classmethod
    def parse(cls, data, nodeconstants):
        if not data:
            parents = (nodeconstants.nullid, nodeconstants.nullid)
            return cls(parents, 0, b'', None)
        marker, p1, p2, meta, data_size, uuid_size = HEADER.unpack_from(data)
        if marker != V2_FORMAT_MARKER:
            raise ValueError("expected dirstate-v2 marker")
        uuid = data[HEADER.size : HEADER.size + uuid_size]
        p1 = p1[: nodeconstants.nodelen]
        p2 = p2[: nodeconstants.nodelen]
        return cls((p1, p2), data_size, meta, uuid)

    def serialize(self):
        p1, p2 = self.parents
        header = HEADER.pack(
            V2_FORMAT_MARKER,
            p1,
            p2,
            self.tree_metadata,
            self.data_size,
            len(self.uuid),
        )
        return header + self.uuid

    def data_filename(self):
        return self.data_filename_pattern % self.uuid
