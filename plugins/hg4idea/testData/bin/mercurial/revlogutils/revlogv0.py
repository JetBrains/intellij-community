# revlogv0 - code related to revlog format "V0"
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from ..node import sha1nodeconstants
from .constants import (
    INDEX_ENTRY_V0,
)
from ..i18n import _

from .. import (
    error,
    node,
    revlogutils,
    util,
)

from . import (
    nodemap as nodemaputil,
)


def getoffset(q):
    return int(q >> 16)


def gettype(q):
    return int(q & 0xFFFF)


class revlogoldindex(list):
    rust_ext_compat = 0
    entry_size = INDEX_ENTRY_V0.size
    null_item = revlogutils.entry(
        data_offset=0,
        data_compressed_length=0,
        data_delta_base=node.nullrev,
        link_rev=node.nullrev,
        parent_rev_1=node.nullrev,
        parent_rev_2=node.nullrev,
        node_id=sha1nodeconstants.nullid,
    )

    @util.propertycache
    def _nodemap(self):
        nodemap = nodemaputil.NodeMap({sha1nodeconstants.nullid: node.nullrev})
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

    def append(self, tup):
        self._nodemap[tup[7]] = len(self)
        super(revlogoldindex, self).append(tup)

    def __delitem__(self, i):
        if not isinstance(i, slice) or not i.stop == -1 or i.step is not None:
            raise ValueError(b"deleting slices only supports a:-1 with step 1")
        for r in range(i.start, len(self)):
            del self._nodemap[self[r][7]]
        super(revlogoldindex, self).__delitem__(i)

    def clearcaches(self):
        self.__dict__.pop('_nodemap', None)

    def __getitem__(self, i):
        if i == -1:
            return self.null_item
        return list.__getitem__(self, i)

    def pack_header(self, header):
        """pack header information in binary"""
        return b''

    def entry_binary(self, rev):
        """return the raw binary string representing a revision"""
        entry = self[rev]
        if gettype(entry[0]):
            raise error.RevlogError(
                _(b'index entry flags need revlog version 1')
            )
        e2 = (
            getoffset(entry[0]),
            entry[1],
            entry[3],
            entry[4],
            self[entry[5]][7],
            self[entry[6]][7],
            entry[7],
        )
        return INDEX_ENTRY_V0.pack(*e2)


def parse_index_v0(data, inline):
    s = INDEX_ENTRY_V0.size
    index = []
    nodemap = nodemaputil.NodeMap({node.nullid: node.nullrev})
    n = off = 0
    l = len(data)
    while off + s <= l:
        cur = data[off : off + s]
        off += s
        e = INDEX_ENTRY_V0.unpack(cur)
        # transform to revlogv1 format
        e2 = revlogutils.entry(
            data_offset=e[0],
            data_compressed_length=e[1],
            data_delta_base=e[2],
            link_rev=e[3],
            parent_rev_1=nodemap.get(e[4], node.nullrev),
            parent_rev_2=nodemap.get(e[5], node.nullrev),
            node_id=e[6],
        )
        index.append(e2)
        nodemap[e[6]] = n
        n += 1

    index = revlogoldindex(index)
    return index, None
