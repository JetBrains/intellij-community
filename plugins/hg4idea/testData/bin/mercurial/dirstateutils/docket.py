# dirstatedocket.py - docket file for dirstate-v2
#
# Copyright Mercurial Contributors
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import struct

from ..revlogutils import docket as docket_mod


V2_FORMAT_MARKER = b"dirstate-v2\n"

# Must match the constant of the same name in
# `rust/hg-core/src/dirstate_tree/on_disk.rs`
TREE_METADATA_SIZE = 44

# * 12 bytes: format marker
# * 32 bytes: node ID of the working directory's first parent
# * 32 bytes: node ID of the working directory's second parent
# * 4 bytes: big-endian used size of the data file
# * {TREE_METADATA_SIZE} bytes: tree metadata, parsed separately
# * 1 byte: length of the data file's UUID
# * variable: data file's UUID
#
# Node IDs are null-padded if shorter than 32 bytes.
# A data file shorter than the specified used size is corrupted (truncated)
HEADER = struct.Struct(
    ">{}s32s32sL{}sB".format(len(V2_FORMAT_MARKER), TREE_METADATA_SIZE)
)


class DirstateDocket(object):
    data_filename_pattern = b'dirstate.%s.d'

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
        marker, p1, p2, data_size, meta, uuid_size = HEADER.unpack_from(data)
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
            self.data_size,
            self.tree_metadata,
            len(self.uuid),
        )
        return header + self.uuid

    def data_filename(self):
        return self.data_filename_pattern % self.uuid
