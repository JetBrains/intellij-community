# mercurial.revlogutils -- basic utilities for revlog
#
# Copyright 2019 Pierre-Yves David <pierre-yves.david@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from ..thirdparty import attr
from ..interfaces import repository

# See mercurial.revlogutils.constants for doc
COMP_MODE_INLINE = 2


def offset_type(offset, type):
    if (type & ~repository.REVISION_FLAGS_KNOWN) != 0:
        raise ValueError(b'unknown revlog index flags: %d' % type)
    return int(int(offset) << 16 | type)


def entry(
    data_offset,
    data_compressed_length,
    data_delta_base,
    link_rev,
    parent_rev_1,
    parent_rev_2,
    node_id,
    flags=0,
    data_uncompressed_length=-1,
    data_compression_mode=COMP_MODE_INLINE,
    sidedata_offset=0,
    sidedata_compressed_length=0,
    sidedata_compression_mode=COMP_MODE_INLINE,
):
    """Build one entry from symbolic name

    This is useful to abstract the actual detail of how we build the entry
    tuple for caller who don't care about it.

    This should always be called using keyword arguments. Some arguments have
    default value, this match the value used by index version that does not store such data.
    """
    return (
        offset_type(data_offset, flags),
        data_compressed_length,
        data_uncompressed_length,
        data_delta_base,
        link_rev,
        parent_rev_1,
        parent_rev_2,
        node_id,
        sidedata_offset,
        sidedata_compressed_length,
        data_compression_mode,
        sidedata_compression_mode,
    )


@attr.s(slots=True, frozen=True)
class revisioninfo(object):
    """Information about a revision that allows building its fulltext
    node:       expected hash of the revision
    p1, p2:     parent revs of the revision
    btext:      built text cache consisting of a one-element list
    cachedelta: (baserev, uncompressed_delta) or None
    flags:      flags associated to the revision storage

    One of btext[0] or cachedelta must be set.
    """

    node = attr.ib()
    p1 = attr.ib()
    p2 = attr.ib()
    btext = attr.ib()
    textlen = attr.ib()
    cachedelta = attr.ib()
    flags = attr.ib()
