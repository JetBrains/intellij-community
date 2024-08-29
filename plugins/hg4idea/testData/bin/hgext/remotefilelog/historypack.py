import struct

from mercurial.node import (
    hex,
    sha1nodeconstants,
)
from mercurial import (
    util,
)
from mercurial.utils import hashutil
from . import (
    basepack,
    constants,
    shallowutil,
)

# (filename hash, offset, size)
INDEXFORMAT2 = b'!20sQQII'
INDEXENTRYLENGTH2 = struct.calcsize(INDEXFORMAT2)
NODELENGTH = 20

NODEINDEXFORMAT = b'!20sQ'
NODEINDEXENTRYLENGTH = struct.calcsize(NODEINDEXFORMAT)

# (node, p1, p2, linknode)
PACKFORMAT = b"!20s20s20s20sH"
PACKENTRYLENGTH = 82

ENTRYCOUNTSIZE = 4

INDEXSUFFIX = b'.histidx'
PACKSUFFIX = b'.histpack'

ANC_NODE = 0
ANC_P1NODE = 1
ANC_P2NODE = 2
ANC_LINKNODE = 3
ANC_COPYFROM = 4


class historypackstore(basepack.basepackstore):
    INDEXSUFFIX = INDEXSUFFIX
    PACKSUFFIX = PACKSUFFIX

    def getpack(self, path):
        return historypack(path)

    def getancestors(self, name, node, known=None):
        for pack in self.packs:
            try:
                return pack.getancestors(name, node, known=known)
            except KeyError:
                pass

        for pack in self.refresh():
            try:
                return pack.getancestors(name, node, known=known)
            except KeyError:
                pass

        raise KeyError((name, node))

    def getnodeinfo(self, name, node):
        for pack in self.packs:
            try:
                return pack.getnodeinfo(name, node)
            except KeyError:
                pass

        for pack in self.refresh():
            try:
                return pack.getnodeinfo(name, node)
            except KeyError:
                pass

        raise KeyError((name, node))

    def add(self, filename, node, p1, p2, linknode, copyfrom):
        raise RuntimeError(
            b"cannot add to historypackstore (%s:%s)" % (filename, hex(node))
        )


class historypack(basepack.basepack):
    INDEXSUFFIX = INDEXSUFFIX
    PACKSUFFIX = PACKSUFFIX

    SUPPORTED_VERSIONS = [2]

    def __init__(self, path):
        super(historypack, self).__init__(path)
        self.INDEXFORMAT = INDEXFORMAT2
        self.INDEXENTRYLENGTH = INDEXENTRYLENGTH2

    def getmissing(self, keys):
        missing = []
        for name, node in keys:
            try:
                self._findnode(name, node)
            except KeyError:
                missing.append((name, node))

        return missing

    def getancestors(self, name, node, known=None):
        """Returns as many ancestors as we're aware of.

        return value: {
           node: (p1, p2, linknode, copyfrom),
           ...
        }
        """
        if known and node in known:
            return []

        ancestors = self._getancestors(name, node, known=known)
        results = {}
        for ancnode, p1, p2, linknode, copyfrom in ancestors:
            results[ancnode] = (p1, p2, linknode, copyfrom)

        if not results:
            raise KeyError((name, node))
        return results

    def getnodeinfo(self, name, node):
        # Drop the node from the tuple before returning, since the result should
        # just be (p1, p2, linknode, copyfrom)
        return self._findnode(name, node)[1:]

    def _getancestors(self, name, node, known=None):
        if known is None:
            known = set()
        section = self._findsection(name)
        filename, offset, size, nodeindexoffset, nodeindexsize = section
        pending = {node}
        o = 0
        while o < size:
            if not pending:
                break
            entry, copyfrom = self._readentry(offset + o)
            o += PACKENTRYLENGTH
            if copyfrom:
                o += len(copyfrom)

            ancnode = entry[ANC_NODE]
            if ancnode in pending:
                pending.remove(ancnode)
                p1node = entry[ANC_P1NODE]
                p2node = entry[ANC_P2NODE]
                if p1node != sha1nodeconstants.nullid and p1node not in known:
                    pending.add(p1node)
                if p2node != sha1nodeconstants.nullid and p2node not in known:
                    pending.add(p2node)

                yield (ancnode, p1node, p2node, entry[ANC_LINKNODE], copyfrom)

    def _readentry(self, offset):
        data = self._data
        entry = struct.unpack(
            PACKFORMAT, data[offset : offset + PACKENTRYLENGTH]
        )
        copyfrom = None
        copyfromlen = entry[ANC_COPYFROM]
        if copyfromlen != 0:
            offset += PACKENTRYLENGTH
            copyfrom = data[offset : offset + copyfromlen]
        return entry, copyfrom

    def add(self, filename, node, p1, p2, linknode, copyfrom):
        raise RuntimeError(
            b"cannot add to historypack (%s:%s)" % (filename, hex(node))
        )

    def _findnode(self, name, node):
        if self.VERSION == 0:
            ancestors = self._getancestors(name, node)
            for ancnode, p1node, p2node, linknode, copyfrom in ancestors:
                if ancnode == node:
                    return (ancnode, p1node, p2node, linknode, copyfrom)
        else:
            section = self._findsection(name)
            nodeindexoffset, nodeindexsize = section[3:]
            entry = self._bisect(
                node,
                nodeindexoffset,
                nodeindexoffset + nodeindexsize,
                NODEINDEXENTRYLENGTH,
            )
            if entry is not None:
                node, offset = struct.unpack(NODEINDEXFORMAT, entry)
                entry, copyfrom = self._readentry(offset)
                # Drop the copyfromlen from the end of entry, and replace it
                # with the copyfrom string.
                return entry[:4] + (copyfrom,)

        raise KeyError(b"unable to find history for %s:%s" % (name, hex(node)))

    def _findsection(self, name):
        params = self.params
        namehash = hashutil.sha1(name).digest()
        fanoutkey = struct.unpack(
            params.fanoutstruct, namehash[: params.fanoutprefix]
        )[0]
        fanout = self._fanouttable

        start = fanout[fanoutkey] + params.indexstart
        indexend = self._indexend

        for i in range(fanoutkey + 1, params.fanoutcount):
            end = fanout[i] + params.indexstart
            if end != start:
                break
        else:
            end = indexend

        entry = self._bisect(namehash, start, end, self.INDEXENTRYLENGTH)
        if not entry:
            raise KeyError(name)

        rawentry = struct.unpack(self.INDEXFORMAT, entry)
        x, offset, size, nodeindexoffset, nodeindexsize = rawentry
        rawnamelen = self._index[
            nodeindexoffset : nodeindexoffset + constants.FILENAMESIZE
        ]
        actualnamelen = struct.unpack(b'!H', rawnamelen)[0]
        nodeindexoffset += constants.FILENAMESIZE
        actualname = self._index[
            nodeindexoffset : nodeindexoffset + actualnamelen
        ]
        if actualname != name:
            raise KeyError(
                b"found file name %s when looking for %s" % (actualname, name)
            )
        nodeindexoffset += actualnamelen

        filenamelength = struct.unpack(
            b'!H', self._data[offset : offset + constants.FILENAMESIZE]
        )[0]
        offset += constants.FILENAMESIZE

        actualname = self._data[offset : offset + filenamelength]
        offset += filenamelength

        if name != actualname:
            raise KeyError(
                b"found file name %s when looking for %s" % (actualname, name)
            )

        # Skip entry list size
        offset += ENTRYCOUNTSIZE

        nodelistoffset = offset
        nodelistsize = (
            size - constants.FILENAMESIZE - filenamelength - ENTRYCOUNTSIZE
        )
        return (
            name,
            nodelistoffset,
            nodelistsize,
            nodeindexoffset,
            nodeindexsize,
        )

    def _bisect(self, node, start, end, entrylen):
        # Bisect between start and end to find node
        origstart = start
        startnode = self._index[start : start + NODELENGTH]
        endnode = self._index[end : end + NODELENGTH]

        if startnode == node:
            return self._index[start : start + entrylen]
        elif endnode == node:
            return self._index[end : end + entrylen]
        else:
            while start < end - entrylen:
                mid = start + (end - start) // 2
                mid = mid - ((mid - origstart) % entrylen)
                midnode = self._index[mid : mid + NODELENGTH]
                if midnode == node:
                    return self._index[mid : mid + entrylen]
                if node > midnode:
                    start = mid
                elif node < midnode:
                    end = mid
        return None

    def markledger(self, ledger, options=None):
        for filename, node in self:
            ledger.markhistoryentry(self, filename, node)

    def cleanup(self, ledger):
        entries = ledger.sources.get(self, [])
        allkeys = set(self)
        repackedkeys = {
            (e.filename, e.node) for e in entries if e.historyrepacked
        }

        if len(allkeys - repackedkeys) == 0:
            if self.path not in ledger.created:
                util.unlinkpath(self.indexpath, ignoremissing=True)
                util.unlinkpath(self.packpath, ignoremissing=True)

    def __iter__(self):
        for f, n, x, x, x, x in self.iterentries():
            yield f, n

    def iterentries(self):
        # Start at 1 to skip the header
        offset = 1
        while offset < self.datasize:
            data = self._data
            # <2 byte len> + <filename>
            filenamelen = struct.unpack(
                b'!H', data[offset : offset + constants.FILENAMESIZE]
            )[0]
            offset += constants.FILENAMESIZE
            filename = data[offset : offset + filenamelen]
            offset += filenamelen

            revcount = struct.unpack(
                b'!I', data[offset : offset + ENTRYCOUNTSIZE]
            )[0]
            offset += ENTRYCOUNTSIZE

            for i in range(revcount):
                entry = struct.unpack(
                    PACKFORMAT, data[offset : offset + PACKENTRYLENGTH]
                )
                offset += PACKENTRYLENGTH

                copyfrom = data[offset : offset + entry[ANC_COPYFROM]]
                offset += entry[ANC_COPYFROM]

                yield (
                    filename,
                    entry[ANC_NODE],
                    entry[ANC_P1NODE],
                    entry[ANC_P2NODE],
                    entry[ANC_LINKNODE],
                    copyfrom,
                )

                self._pagedin += PACKENTRYLENGTH

            # If we've read a lot of data from the mmap, free some memory.
            self.freememory()


class mutablehistorypack(basepack.mutablebasepack):
    """A class for constructing and serializing a histpack file and index.

    A history pack is a pair of files that contain the revision history for
    various file revisions in Mercurial. It contains only revision history (like
    parent pointers and linknodes), not any revision content information.

    It consists of two files, with the following format:

    .histpack
        The pack itself is a series of file revisions with some basic header
        information on each.

        datapack = <version: 1 byte>
                   [<filesection>,...]
        filesection = <filename len: 2 byte unsigned int>
                      <filename>
                      <revision count: 4 byte unsigned int>
                      [<revision>,...]
        revision = <node: 20 byte>
                   <p1node: 20 byte>
                   <p2node: 20 byte>
                   <linknode: 20 byte>
                   <copyfromlen: 2 byte>
                   <copyfrom>

        The revisions within each filesection are stored in topological order
        (newest first). If a given entry has a parent from another file (a copy)
        then p1node is the node from the other file, and copyfrom is the
        filepath of the other file.

    .histidx
        The index file provides a mapping from filename to the file section in
        the histpack. In V1 it also contains sub-indexes for specific nodes
        within each file. It consists of three parts, the fanout, the file index
        and the node indexes.

        The file index is a list of index entries, sorted by filename hash (one
        per file section in the pack). Each entry has:

        - node (The 20 byte hash of the filename)
        - pack entry offset (The location of this file section in the histpack)
        - pack content size (The on-disk length of this file section's pack
                             data)
        - node index offset (The location of the file's node index in the index
                             file) [1]
        - node index size (the on-disk length of this file's node index) [1]

        The fanout is a quick lookup table to reduce the number of steps for
        bisecting the index. It is a series of 4 byte pointers to positions
        within the index. It has 2^16 entries, which corresponds to hash
        prefixes [00, 01, 02,..., FD, FE, FF]. Example: the pointer in slot 4F
        points to the index position of the first revision whose node starts
        with 4F. This saves log(2^16) bisect steps.

        dataidx = <fanouttable>
                  <file count: 8 byte unsigned> [1]
                  <fileindex>
                  <node count: 8 byte unsigned> [1]
                  [<nodeindex>,...] [1]
        fanouttable = [<index offset: 4 byte unsigned int>,...] (2^16 entries)

        fileindex = [<file index entry>,...]
        fileindexentry = <node: 20 byte>
                         <pack file section offset: 8 byte unsigned int>
                         <pack file section size: 8 byte unsigned int>
                         <node index offset: 4 byte unsigned int> [1]
                         <node index size: 4 byte unsigned int>   [1]
        nodeindex = <filename>[<node index entry>,...] [1]
        filename = <filename len : 2 byte unsigned int><filename value> [1]
        nodeindexentry = <node: 20 byte> [1]
                         <pack file node offset: 8 byte unsigned int> [1]

    [1]: new in version 1.
    """

    INDEXSUFFIX = INDEXSUFFIX
    PACKSUFFIX = PACKSUFFIX

    SUPPORTED_VERSIONS = [2]

    def __init__(self, ui, packpath, version=2):
        super(mutablehistorypack, self).__init__(ui, packpath, version=version)
        self.files = {}
        self.entrylocations = {}
        self.fileentries = {}

        self.INDEXFORMAT = INDEXFORMAT2
        self.INDEXENTRYLENGTH = INDEXENTRYLENGTH2

        self.NODEINDEXFORMAT = NODEINDEXFORMAT
        self.NODEINDEXENTRYLENGTH = NODEINDEXENTRYLENGTH

    def add(self, filename, node, p1, p2, linknode, copyfrom):
        copyfrom = copyfrom or b''
        copyfromlen = struct.pack(b'!H', len(copyfrom))
        self.fileentries.setdefault(filename, []).append(
            (node, p1, p2, linknode, copyfromlen, copyfrom)
        )

    def _write(self):
        for filename in sorted(self.fileentries):
            entries = self.fileentries[filename]
            sectionstart = self.packfp.tell()

            # Write the file section content
            entrymap = {e[0]: e for e in entries}

            def parentfunc(node):
                x, p1, p2, x, x, x = entrymap[node]
                parents = []
                if p1 != sha1nodeconstants.nullid:
                    parents.append(p1)
                if p2 != sha1nodeconstants.nullid:
                    parents.append(p2)
                return parents

            sortednodes = list(
                reversed(
                    shallowutil.sortnodes((e[0] for e in entries), parentfunc)
                )
            )

            # Write the file section header
            self.writeraw(
                b"%s%s%s"
                % (
                    struct.pack(b'!H', len(filename)),
                    filename,
                    struct.pack(b'!I', len(sortednodes)),
                )
            )

            sectionlen = constants.FILENAMESIZE + len(filename) + 4

            rawstrings = []

            # Record the node locations for the index
            locations = self.entrylocations.setdefault(filename, {})
            offset = sectionstart + sectionlen
            for node in sortednodes:
                locations[node] = offset
                raw = b'%s%s%s%s%s%s' % entrymap[node]
                rawstrings.append(raw)
                offset += len(raw)

            rawdata = b''.join(rawstrings)
            sectionlen += len(rawdata)

            self.writeraw(rawdata)

            # Record metadata for the index
            self.files[filename] = (sectionstart, sectionlen)
            node = hashutil.sha1(filename).digest()
            self.entries[node] = node

    def close(self, ledger=None):
        if self._closed:
            return

        self._write()

        return super(mutablehistorypack, self).close(ledger=ledger)

    def createindex(self, nodelocations, indexoffset):
        fileindexformat = self.INDEXFORMAT
        fileindexlength = self.INDEXENTRYLENGTH
        nodeindexformat = self.NODEINDEXFORMAT
        nodeindexlength = self.NODEINDEXENTRYLENGTH

        files = (
            (hashutil.sha1(filename).digest(), filename, offset, size)
            for filename, (offset, size) in self.files.items()
        )
        files = sorted(files)

        # node index is after file index size, file index, and node index size
        indexlensize = struct.calcsize(b'!Q')
        nodeindexoffset = (
            indexoffset
            + indexlensize
            + (len(files) * fileindexlength)
            + indexlensize
        )

        fileindexentries = []
        nodeindexentries = []
        nodecount = 0
        for namehash, filename, offset, size in files:
            # File section index
            nodelocations = self.entrylocations[filename]

            nodeindexsize = len(nodelocations) * nodeindexlength

            rawentry = struct.pack(
                fileindexformat,
                namehash,
                offset,
                size,
                nodeindexoffset,
                nodeindexsize,
            )
            # Node index
            nodeindexentries.append(
                struct.pack(constants.FILENAMESTRUCT, len(filename)) + filename
            )
            nodeindexoffset += constants.FILENAMESIZE + len(filename)

            for node, location in sorted(nodelocations.items()):
                nodeindexentries.append(
                    struct.pack(nodeindexformat, node, location)
                )
                nodecount += 1

            nodeindexoffset += len(nodelocations) * nodeindexlength

            fileindexentries.append(rawentry)

        nodecountraw = struct.pack(b'!Q', nodecount)
        return (
            b''.join(fileindexentries)
            + nodecountraw
            + b''.join(nodeindexentries)
        )
