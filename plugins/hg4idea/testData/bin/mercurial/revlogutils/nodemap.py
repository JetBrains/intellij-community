# nodemap.py - nodemap related code and utilities
#
# Copyright 2019 Pierre-Yves David <pierre-yves.david@octobus.net>
# Copyright 2019 George Racinet <georges.racinet@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno
import re
import struct

from ..node import hex

from .. import (
    error,
    util,
)
from . import docket as docket_mod


class NodeMap(dict):
    def __missing__(self, x):
        raise error.RevlogError(b'unknown node: %s' % x)


def persisted_data(revlog):
    """read the nodemap for a revlog from disk"""
    if revlog._nodemap_file is None:
        return None
    pdata = revlog.opener.tryread(revlog._nodemap_file)
    if not pdata:
        return None
    offset = 0
    (version,) = S_VERSION.unpack(pdata[offset : offset + S_VERSION.size])
    if version != ONDISK_VERSION:
        return None
    offset += S_VERSION.size
    headers = S_HEADER.unpack(pdata[offset : offset + S_HEADER.size])
    uid_size, tip_rev, data_length, data_unused, tip_node_size = headers
    offset += S_HEADER.size
    docket = NodeMapDocket(pdata[offset : offset + uid_size])
    offset += uid_size
    docket.tip_rev = tip_rev
    docket.tip_node = pdata[offset : offset + tip_node_size]
    docket.data_length = data_length
    docket.data_unused = data_unused

    filename = _rawdata_filepath(revlog, docket)
    use_mmap = revlog.opener.options.get(b"persistent-nodemap.mmap")
    try:
        with revlog.opener(filename) as fd:
            if use_mmap:
                try:
                    data = util.buffer(util.mmapread(fd, data_length))
                except ValueError:
                    # raised when the read file is too small
                    data = b''
            else:
                data = fd.read(data_length)
    except (IOError, OSError) as e:
        if e.errno == errno.ENOENT:
            return None
        else:
            raise
    if len(data) < data_length:
        return None
    return docket, data


def setup_persistent_nodemap(tr, revlog):
    """Install whatever is needed transaction side to persist a nodemap on disk

    (only actually persist the nodemap if this is relevant for this revlog)
    """
    if revlog._inline:
        return  # inlined revlog are too small for this to be relevant
    if revlog._nodemap_file is None:
        return  # we do not use persistent_nodemap on this revlog

    # we need to happen after the changelog finalization, in that use "cl-"
    callback_id = b"nm-revlog-persistent-nodemap-%s" % revlog._nodemap_file
    if tr.hasfinalize(callback_id):
        return  # no need to register again
    tr.addpending(
        callback_id, lambda tr: persist_nodemap(tr, revlog, pending=True)
    )
    tr.addfinalize(callback_id, lambda tr: persist_nodemap(tr, revlog))


class _NoTransaction(object):
    """transaction like object to update the nodemap outside a transaction"""

    def __init__(self):
        self._postclose = {}

    def addpostclose(self, callback_id, callback_func):
        self._postclose[callback_id] = callback_func

    def registertmp(self, *args, **kwargs):
        pass

    def addbackup(self, *args, **kwargs):
        pass

    def add(self, *args, **kwargs):
        pass

    def addabort(self, *args, **kwargs):
        pass

    def _report(self, *args):
        pass


def update_persistent_nodemap(revlog):
    """update the persistent nodemap right now

    To be used for updating the nodemap on disk outside of a normal transaction
    setup (eg, `debugupdatecache`).
    """
    if revlog._inline:
        return  # inlined revlog are too small for this to be relevant
    if revlog._nodemap_file is None:
        return  # we do not use persistent_nodemap on this revlog

    notr = _NoTransaction()
    persist_nodemap(notr, revlog)
    for k in sorted(notr._postclose):
        notr._postclose[k](None)


def delete_nodemap(tr, repo, revlog):
    """Delete nodemap data on disk for a given revlog"""
    if revlog._nodemap_file is None:
        msg = "calling persist nodemap on a revlog without the feature enabled"
        raise error.ProgrammingError(msg)
    repo.svfs.unlink(revlog._nodemap_file)


def persist_nodemap(tr, revlog, pending=False, force=False):
    """Write nodemap data on disk for a given revlog"""
    if getattr(revlog, 'filteredrevs', ()):
        raise error.ProgrammingError(
            "cannot persist nodemap of a filtered changelog"
        )
    if revlog._nodemap_file is None:
        if force:
            revlog._nodemap_file = get_nodemap_file(revlog)
        else:
            msg = "calling persist nodemap on a revlog without the feature enabled"
            raise error.ProgrammingError(msg)

    can_incremental = util.safehasattr(revlog.index, "nodemap_data_incremental")
    ondisk_docket = revlog._nodemap_docket
    feed_data = util.safehasattr(revlog.index, "update_nodemap_data")
    use_mmap = revlog.opener.options.get(b"persistent-nodemap.mmap")

    data = None
    # first attemp an incremental update of the data
    if can_incremental and ondisk_docket is not None:
        target_docket = revlog._nodemap_docket.copy()
        (
            src_docket,
            data_changed_count,
            data,
        ) = revlog.index.nodemap_data_incremental()
        new_length = target_docket.data_length + len(data)
        new_unused = target_docket.data_unused + data_changed_count
        if src_docket != target_docket:
            data = None
        elif new_length <= (new_unused * 10):  # under 10% of unused data
            data = None
        else:
            datafile = _rawdata_filepath(revlog, target_docket)
            # EXP-TODO: if this is a cache, this should use a cache vfs, not a
            # store vfs
            tr.add(datafile, target_docket.data_length)
            with revlog.opener(datafile, b'r+') as fd:
                fd.seek(target_docket.data_length)
                fd.write(data)
                if feed_data:
                    if use_mmap:
                        fd.seek(0)
                        new_data = fd.read(new_length)
                    else:
                        fd.flush()
                        new_data = util.buffer(util.mmapread(fd, new_length))
            target_docket.data_length = new_length
            target_docket.data_unused = new_unused

    if data is None:
        # otherwise fallback to a full new export
        target_docket = NodeMapDocket()
        datafile = _rawdata_filepath(revlog, target_docket)
        if util.safehasattr(revlog.index, "nodemap_data_all"):
            data = revlog.index.nodemap_data_all()
        else:
            data = persistent_data(revlog.index)
        # EXP-TODO: if this is a cache, this should use a cache vfs, not a
        # store vfs

        tryunlink = revlog.opener.tryunlink

        def abortck(tr):
            tryunlink(datafile)

        callback_id = b"delete-%s" % datafile

        # some flavor of the transaction abort does not cleanup new file, it
        # simply empty them.
        tr.addabort(callback_id, abortck)
        with revlog.opener(datafile, b'w+') as fd:
            fd.write(data)
            if feed_data:
                if use_mmap:
                    new_data = data
                else:
                    fd.flush()
                    new_data = util.buffer(util.mmapread(fd, len(data)))
        target_docket.data_length = len(data)
    target_docket.tip_rev = revlog.tiprev()
    target_docket.tip_node = revlog.node(target_docket.tip_rev)
    # EXP-TODO: if this is a cache, this should use a cache vfs, not a
    # store vfs
    file_path = revlog._nodemap_file
    if pending:
        file_path += b'.a'
        tr.registertmp(file_path)
    else:
        tr.addbackup(file_path)

    with revlog.opener(file_path, b'w', atomictemp=True) as fp:
        fp.write(target_docket.serialize())
    revlog._nodemap_docket = target_docket
    if feed_data:
        revlog.index.update_nodemap_data(target_docket, new_data)

    # search for old index file in all cases, some older process might have
    # left one behind.
    olds = _other_rawdata_filepath(revlog, target_docket)
    if olds:
        realvfs = getattr(revlog, '_realopener', revlog.opener)

        def cleanup(tr):
            for oldfile in olds:
                realvfs.tryunlink(oldfile)

        callback_id = b"revlog-cleanup-nodemap-%s" % revlog._nodemap_file
        tr.addpostclose(callback_id, cleanup)


### Nodemap docket file
#
# The nodemap data are stored on disk using 2 files:
#
# * a raw data files containing a persistent nodemap
#   (see `Nodemap Trie` section)
#
# * a small "docket" file containing medatadata
#
# While the nodemap data can be multiple tens of megabytes, the "docket" is
# small, it is easy to update it automatically or to duplicated its content
# during a transaction.
#
# Multiple raw data can exist at the same time (The currently valid one and a
# new one beind used by an in progress transaction). To accomodate this, the
# filename hosting the raw data has a variable parts. The exact filename is
# specified inside the "docket" file.
#
# The docket file contains information to find, qualify and validate the raw
# data. Its content is currently very light, but it will expand as the on disk
# nodemap gains the necessary features to be used in production.

ONDISK_VERSION = 1
S_VERSION = struct.Struct(">B")
S_HEADER = struct.Struct(">BQQQQ")


class NodeMapDocket(object):
    """metadata associated with persistent nodemap data

    The persistent data may come from disk or be on their way to disk.
    """

    def __init__(self, uid=None):
        if uid is None:
            uid = docket_mod.make_uid()
        # a unique identifier for the data file:
        #   - When new data are appended, it is preserved.
        #   - When a new data file is created, a new identifier is generated.
        self.uid = uid
        # the tipmost revision stored in the data file. This revision and all
        # revision before it are expected to be encoded in the data file.
        self.tip_rev = None
        # the node of that tipmost revision, if it mismatch the current index
        # data the docket is not valid for the current index and should be
        # discarded.
        #
        # note: this method is not perfect as some destructive operation could
        # preserve the same tip_rev + tip_node while altering lower revision.
        # However this multiple other caches have the same vulnerability (eg:
        # brancmap cache).
        self.tip_node = None
        # the size (in bytes) of the persisted data to encode the nodemap valid
        # for `tip_rev`.
        #   - data file shorter than this are corrupted,
        #   - any extra data should be ignored.
        self.data_length = None
        # the amount (in bytes) of "dead" data, still in the data file but no
        # longer used for the nodemap.
        self.data_unused = 0

    def copy(self):
        new = NodeMapDocket(uid=self.uid)
        new.tip_rev = self.tip_rev
        new.tip_node = self.tip_node
        new.data_length = self.data_length
        new.data_unused = self.data_unused
        return new

    def __cmp__(self, other):
        if self.uid < other.uid:
            return -1
        if self.uid > other.uid:
            return 1
        elif self.data_length < other.data_length:
            return -1
        elif self.data_length > other.data_length:
            return 1
        return 0

    def __eq__(self, other):
        return self.uid == other.uid and self.data_length == other.data_length

    def serialize(self):
        """return serialized bytes for a docket using the passed uid"""
        data = []
        data.append(S_VERSION.pack(ONDISK_VERSION))
        headers = (
            len(self.uid),
            self.tip_rev,
            self.data_length,
            self.data_unused,
            len(self.tip_node),
        )
        data.append(S_HEADER.pack(*headers))
        data.append(self.uid)
        data.append(self.tip_node)
        return b''.join(data)


def _rawdata_filepath(revlog, docket):
    """The (vfs relative) nodemap's rawdata file for a given uid"""
    prefix = revlog.radix
    return b"%s-%s.nd" % (prefix, docket.uid)


def _other_rawdata_filepath(revlog, docket):
    prefix = revlog.radix
    pattern = re.compile(br"(^|/)%s-[0-9a-f]+\.nd$" % prefix)
    new_file_path = _rawdata_filepath(revlog, docket)
    new_file_name = revlog.opener.basename(new_file_path)
    dirpath = revlog.opener.dirname(new_file_path)
    others = []
    for f in revlog.opener.listdir(dirpath):
        if pattern.match(f) and f != new_file_name:
            others.append(f)
    return others


### Nodemap Trie
#
# This is a simple reference implementation to compute and persist a nodemap
# trie. This reference implementation is write only. The python version of this
# is not expected to be actually used, since it wont provide performance
# improvement over existing non-persistent C implementation.
#
# The nodemap is persisted as Trie using 4bits-address/16-entries block. each
# revision can be adressed using its node shortest prefix.
#
# The trie is stored as a sequence of block. Each block contains 16 entries
# (signed 64bit integer, big endian). Each entry can be one of the following:
#
#  * value >=  0 -> index of sub-block
#  * value == -1 -> no value
#  * value <  -1 -> encoded revision: rev = -(value+2)
#
# See REV_OFFSET and _transform_rev below.
#
# The implementation focus on simplicity, not on performance. A Rust
# implementation should provide a efficient version of the same binary
# persistence. This reference python implementation is never meant to be
# extensively use in production.


def persistent_data(index):
    """return the persistent binary form for a nodemap for a given index"""
    trie = _build_trie(index)
    return _persist_trie(trie)


def update_persistent_data(index, root, max_idx, last_rev):
    """return the incremental update for persistent nodemap from a given index"""
    changed_block, trie = _update_trie(index, root, last_rev)
    return (
        changed_block * S_BLOCK.size,
        _persist_trie(trie, existing_idx=max_idx),
    )


S_BLOCK = struct.Struct(">" + ("l" * 16))

NO_ENTRY = -1
# rev 0 need to be -2 because 0 is used by block, -1 is a special value.
REV_OFFSET = 2


def _transform_rev(rev):
    """Return the number used to represent the rev in the tree.

    (or retrieve a rev number from such representation)

    Note that this is an involution, a function equal to its inverse (i.e.
    which gives the identity when applied to itself).
    """
    return -(rev + REV_OFFSET)


def _to_int(hex_digit):
    """turn an hexadecimal digit into a proper integer"""
    return int(hex_digit, 16)


class Block(dict):
    """represent a block of the Trie

    contains up to 16 entry indexed from 0 to 15"""

    def __init__(self):
        super(Block, self).__init__()
        # If this block exist on disk, here is its ID
        self.ondisk_id = None

    def __iter__(self):
        return iter(self.get(i) for i in range(16))


def _build_trie(index):
    """build a nodemap trie

    The nodemap stores revision number for each unique prefix.

    Each block is a dictionary with keys in `[0, 15]`. Values are either
    another block or a revision number.
    """
    root = Block()
    for rev in range(len(index)):
        current_hex = hex(index[rev][7])
        _insert_into_block(index, 0, root, rev, current_hex)
    return root


def _update_trie(index, root, last_rev):
    """consume"""
    changed = 0
    for rev in range(last_rev + 1, len(index)):
        current_hex = hex(index[rev][7])
        changed += _insert_into_block(index, 0, root, rev, current_hex)
    return changed, root


def _insert_into_block(index, level, block, current_rev, current_hex):
    """insert a new revision in a block

    index: the index we are adding revision for
    level: the depth of the current block in the trie
    block: the block currently being considered
    current_rev: the revision number we are adding
    current_hex: the hexadecimal representation of the of that revision
    """
    changed = 1
    if block.ondisk_id is not None:
        block.ondisk_id = None
    hex_digit = _to_int(current_hex[level : level + 1])
    entry = block.get(hex_digit)
    if entry is None:
        # no entry, simply store the revision number
        block[hex_digit] = current_rev
    elif isinstance(entry, dict):
        # need to recurse to an underlying block
        changed += _insert_into_block(
            index, level + 1, entry, current_rev, current_hex
        )
    else:
        # collision with a previously unique prefix, inserting new
        # vertices to fit both entry.
        other_hex = hex(index[entry][7])
        other_rev = entry
        new = Block()
        block[hex_digit] = new
        _insert_into_block(index, level + 1, new, other_rev, other_hex)
        _insert_into_block(index, level + 1, new, current_rev, current_hex)
    return changed


def _persist_trie(root, existing_idx=None):
    """turn a nodemap trie into persistent binary data

    See `_build_trie` for nodemap trie structure"""
    block_map = {}
    if existing_idx is not None:
        base_idx = existing_idx + 1
    else:
        base_idx = 0
    chunks = []
    for tn in _walk_trie(root):
        if tn.ondisk_id is not None:
            block_map[id(tn)] = tn.ondisk_id
        else:
            block_map[id(tn)] = len(chunks) + base_idx
            chunks.append(_persist_block(tn, block_map))
    return b''.join(chunks)


def _walk_trie(block):
    """yield all the block in a trie

    Children blocks are always yield before their parent block.
    """
    for (__, item) in sorted(block.items()):
        if isinstance(item, dict):
            for sub_block in _walk_trie(item):
                yield sub_block
    yield block


def _persist_block(block_node, block_map):
    """produce persistent binary data for a single block

    Children block are assumed to be already persisted and present in
    block_map.
    """
    data = tuple(_to_value(v, block_map) for v in block_node)
    return S_BLOCK.pack(*data)


def _to_value(item, block_map):
    """persist any value as an integer"""
    if item is None:
        return NO_ENTRY
    elif isinstance(item, dict):
        return block_map[id(item)]
    else:
        return _transform_rev(item)


def parse_data(data):
    """parse parse nodemap data into a nodemap Trie"""
    if (len(data) % S_BLOCK.size) != 0:
        msg = b"nodemap data size is not a multiple of block size (%d): %d"
        raise error.Abort(msg % (S_BLOCK.size, len(data)))
    if not data:
        return Block(), None
    block_map = {}
    new_blocks = []
    for i in range(0, len(data), S_BLOCK.size):
        block = Block()
        block.ondisk_id = len(block_map)
        block_map[block.ondisk_id] = block
        block_data = data[i : i + S_BLOCK.size]
        values = S_BLOCK.unpack(block_data)
        new_blocks.append((block, values))
    for b, values in new_blocks:
        for idx, v in enumerate(values):
            if v == NO_ENTRY:
                continue
            elif v >= 0:
                b[idx] = block_map[v]
            else:
                b[idx] = _transform_rev(v)
    return block, i // S_BLOCK.size


# debug utility


def check_data(ui, index, data):
    """verify that the provided nodemap data are valid for the given idex"""
    ret = 0
    ui.status((b"revision in index:   %d\n") % len(index))
    root, __ = parse_data(data)
    all_revs = set(_all_revisions(root))
    ui.status((b"revision in nodemap: %d\n") % len(all_revs))
    for r in range(len(index)):
        if r not in all_revs:
            msg = b"  revision missing from nodemap: %d\n" % r
            ui.write_err(msg)
            ret = 1
        else:
            all_revs.remove(r)
        nm_rev = _find_node(root, hex(index[r][7]))
        if nm_rev is None:
            msg = b"  revision node does not match any entries: %d\n" % r
            ui.write_err(msg)
            ret = 1
        elif nm_rev != r:
            msg = (
                b"  revision node does not match the expected revision: "
                b"%d != %d\n" % (r, nm_rev)
            )
            ui.write_err(msg)
            ret = 1

    if all_revs:
        for r in sorted(all_revs):
            msg = b"  extra revision in  nodemap: %d\n" % r
            ui.write_err(msg)
        ret = 1
    return ret


def _all_revisions(root):
    """return all revisions stored in a Trie"""
    for block in _walk_trie(root):
        for v in block:
            if v is None or isinstance(v, Block):
                continue
            yield v


def _find_node(block, node):
    """find the revision associated with a given node"""
    entry = block.get(_to_int(node[0:1]))
    if isinstance(entry, dict):
        return _find_node(entry, node[1:])
    return entry


def get_nodemap_file(revlog):
    if revlog._trypending:
        pending_path = revlog.radix + b".n.a"
        if revlog.opener.exists(pending_path):
            return pending_path
    return revlog.radix + b".n"
