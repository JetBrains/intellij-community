# remotefilelog.py - filelog implementation where filelog history is stored
#                    remotely
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import collections

from mercurial.node import bin
from mercurial.i18n import _
from mercurial import (
    ancestor,
    error,
    mdiff,
    revlog,
)
from mercurial.utils import storageutil
from mercurial.revlogutils import flagutil

from . import (
    constants,
    shallowutil,
)


class remotefilelognodemap:
    def __init__(self, filename, store):
        self._filename = filename
        self._store = store

    def __contains__(self, node):
        missing = self._store.getmissing([(self._filename, node)])
        return not bool(missing)

    def __get__(self, node):
        if node not in self:
            raise KeyError(node)
        return node


class remotefilelog:

    _flagserrorclass = error.RevlogError

    def __init__(self, opener, path, repo):
        self.opener = opener
        self.filename = path
        self.repo = repo
        self.nodemap = remotefilelognodemap(self.filename, repo.contentstore)

        self.version = 1

        self._flagprocessors = dict(flagutil.flagprocessors)

    def read(self, node):
        """returns the file contents at this node"""
        t = self.revision(node)
        if not t.startswith(b'\1\n'):
            return t
        s = t.index(b'\1\n', 2)
        return t[s + 2 :]

    def add(self, text, meta, transaction, linknode, p1=None, p2=None):
        # hash with the metadata, like in vanilla filelogs
        hashtext = shallowutil.createrevlogtext(
            text, meta.get(b'copy'), meta.get(b'copyrev')
        )
        node = storageutil.hashrevisionsha1(hashtext, p1, p2)
        return self.addrevision(
            hashtext, transaction, linknode, p1, p2, node=node
        )

    def _createfileblob(self, text, meta, flags, p1, p2, node, linknode):
        # text passed to "_createfileblob" does not include filelog metadata
        header = shallowutil.buildfileblobheader(len(text), flags)
        data = b"%s\0%s" % (header, text)

        realp1 = p1
        copyfrom = b""
        if meta and b'copy' in meta:
            copyfrom = meta[b'copy']
            realp1 = bin(meta[b'copyrev'])

        data += b"%s%s%s%s%s\0" % (node, realp1, p2, linknode, copyfrom)

        visited = set()

        pancestors = {}
        queue = []
        if realp1 != self.repo.nullid:
            p1flog = self
            if copyfrom:
                p1flog = remotefilelog(self.opener, copyfrom, self.repo)

            pancestors.update(p1flog.ancestormap(realp1))
            queue.append(realp1)
            visited.add(realp1)
        if p2 != self.repo.nullid:
            pancestors.update(self.ancestormap(p2))
            queue.append(p2)
            visited.add(p2)

        ancestortext = b""

        # add the ancestors in topological order
        while queue:
            c = queue.pop(0)
            pa1, pa2, ancestorlinknode, pacopyfrom = pancestors[c]

            pacopyfrom = pacopyfrom or b''
            ancestortext += b"%s%s%s%s%s\0" % (
                c,
                pa1,
                pa2,
                ancestorlinknode,
                pacopyfrom,
            )

            if pa1 != self.repo.nullid and pa1 not in visited:
                queue.append(pa1)
                visited.add(pa1)
            if pa2 != self.repo.nullid and pa2 not in visited:
                queue.append(pa2)
                visited.add(pa2)

        data += ancestortext

        return data

    def addrevision(
        self,
        text,
        transaction,
        linknode,
        p1,
        p2,
        cachedelta=None,
        node=None,
        flags=revlog.REVIDX_DEFAULT_FLAGS,
        sidedata=None,
    ):
        # text passed to "addrevision" includes hg filelog metadata header
        if node is None:
            node = storageutil.hashrevisionsha1(text, p1, p2)

        meta, metaoffset = storageutil.parsemeta(text)
        rawtext, validatehash = flagutil.processflagswrite(
            self,
            text,
            flags,
        )
        return self.addrawrevision(
            rawtext,
            transaction,
            linknode,
            p1,
            p2,
            node,
            flags,
            cachedelta,
            _metatuple=(meta, metaoffset),
        )

    def addrawrevision(
        self,
        rawtext,
        transaction,
        linknode,
        p1,
        p2,
        node,
        flags,
        cachedelta=None,
        _metatuple=None,
    ):
        if _metatuple:
            # _metatuple: used by "addrevision" internally by remotefilelog
            # meta was parsed confidently
            meta, metaoffset = _metatuple
        else:
            # not from self.addrevision, but something else (repo._filecommit)
            # calls addrawrevision directly. remotefilelog needs to get and
            # strip filelog metadata.
            # we don't have confidence about whether rawtext contains filelog
            # metadata or not (flag processor could replace it), so we just
            # parse it as best-effort.
            # in LFS (flags != 0)'s case, the best way is to call LFS code to
            # get the meta information, instead of storageutil.parsemeta.
            meta, metaoffset = storageutil.parsemeta(rawtext)
        if flags != 0:
            # when flags != 0, be conservative and do not mangle rawtext, since
            # a read flag processor expects the text not being mangled at all.
            metaoffset = 0
        if metaoffset:
            # remotefilelog fileblob stores copy metadata in its ancestortext,
            # not its main blob. so we need to remove filelog metadata
            # (containing copy information) from text.
            blobtext = rawtext[metaoffset:]
        else:
            blobtext = rawtext
        data = self._createfileblob(
            blobtext, meta, flags, p1, p2, node, linknode
        )
        self.repo.contentstore.addremotefilelognode(self.filename, node, data)

        return node

    def renamed(self, node):
        ancestors = self.repo.metadatastore.getancestors(self.filename, node)
        p1, p2, linknode, copyfrom = ancestors[node]
        if copyfrom:
            return (copyfrom, p1)

        return False

    def size(self, node):
        """return the size of a given revision"""
        return len(self.read(node))

    rawsize = size

    def cmp(self, node, text):
        """compare text with a given file revision

        returns True if text is different than what is stored.
        """

        if node == self.repo.nullid:
            return True

        nodetext = self.read(node)
        return nodetext != text

    def __nonzero__(self):
        return True

    __bool__ = __nonzero__

    def __len__(self):
        if self.filename in (b'.hgtags', b'.hgsub', b'.hgsubstate'):
            # Global tag and subrepository support require access to the
            # file history for various performance sensitive operations.
            # excludepattern should be used for repositories depending on
            # those features to fallback to regular filelog.
            return 0

        raise RuntimeError(b"len not supported")

    def heads(self):
        # Fake heads of the filelog to satisfy hgweb.
        return []

    def empty(self):
        return False

    def flags(self, node):
        if isinstance(node, int):
            raise error.ProgrammingError(
                b'remotefilelog does not accept integer rev for flags'
            )
        store = self.repo.contentstore
        return store.getmeta(self.filename, node).get(constants.METAKEYFLAG, 0)

    def parents(self, node):
        if node == self.repo.nullid:
            return self.repo.nullid, self.repo.nullid

        ancestormap = self.repo.metadatastore.getancestors(self.filename, node)
        p1, p2, linknode, copyfrom = ancestormap[node]
        if copyfrom:
            p1 = self.repo.nullid

        return p1, p2

    def parentrevs(self, rev):
        # TODO(augie): this is a node and should be a rev, but for now
        # nothing in core seems to actually break.
        return self.parents(rev)

    def linknode(self, node):
        ancestormap = self.repo.metadatastore.getancestors(self.filename, node)
        p1, p2, linknode, copyfrom = ancestormap[node]
        return linknode

    def linkrev(self, node):
        return self.repo.unfiltered().changelog.rev(self.linknode(node))

    def emitrevisions(
        self,
        nodes,
        nodesorder=None,
        revisiondata=False,
        assumehaveparentrevisions=False,
        deltaprevious=False,
        deltamode=None,
        sidedata_helpers=None,
        debug_info=None,
    ):
        # we don't use any of these parameters here
        del nodesorder, revisiondata, assumehaveparentrevisions, deltaprevious
        del deltamode
        prevnode = None
        for node in nodes:
            p1, p2 = self.parents(node)
            if prevnode is None:
                basenode = prevnode = p1
            if basenode == node:
                basenode = self.repo.nullid
            if basenode != self.repo.nullid:
                revision = None
                delta = self.revdiff(basenode, node)
            else:
                revision = self.rawdata(node)
                delta = None
            yield revlog.revlogrevisiondelta(
                node=node,
                p1node=p1,
                p2node=p2,
                linknode=self.linknode(node),
                basenode=basenode,
                flags=self.flags(node),
                baserevisionsize=None,
                revision=revision,
                delta=delta,
                # Sidedata is not supported yet
                sidedata=None,
                # Protocol flags are not used yet
                protocol_flags=0,
            )

    def revdiff(self, node1, node2):
        return mdiff.textdiff(self.rawdata(node1), self.rawdata(node2))

    def lookup(self, node):
        if len(node) == 40:
            node = bin(node)
        if len(node) != 20:
            raise error.LookupError(
                node, self.filename, _(b'invalid lookup input')
            )

        return node

    def rev(self, node):
        # This is a hack to make TortoiseHG work.
        return node

    def node(self, rev):
        # This is a hack.
        if isinstance(rev, int):
            raise error.ProgrammingError(
                b'remotefilelog does not convert integer rev to node'
            )
        return rev

    def revision(self, node, raw=False):
        """returns the revlog contents at this node.
        this includes the meta data traditionally included in file revlogs.
        this is generally only used for bundling and communicating with vanilla
        hg clients.
        """
        if node == self.repo.nullid:
            return b""
        if len(node) != 20:
            raise error.LookupError(
                node, self.filename, _(b'invalid revision input')
            )
        if (
            node == self.repo.nodeconstants.wdirid
            or node in self.repo.nodeconstants.wdirfilenodeids
        ):
            raise error.WdirUnsupported

        store = self.repo.contentstore
        rawtext = store.get(self.filename, node)
        if raw:
            return rawtext
        flags = store.getmeta(self.filename, node).get(constants.METAKEYFLAG, 0)
        if flags == 0:
            return rawtext
        return flagutil.processflagsread(self, rawtext, flags)[0]

    def rawdata(self, node):
        return self.revision(node, raw=False)

    def ancestormap(self, node):
        return self.repo.metadatastore.getancestors(self.filename, node)

    def ancestor(self, a, b):
        if a == self.repo.nullid or b == self.repo.nullid:
            return self.repo.nullid

        revmap, parentfunc = self._buildrevgraph(a, b)
        nodemap = {v: k for (k, v) in revmap.items()}

        ancs = ancestor.ancestors(parentfunc, revmap[a], revmap[b])
        if ancs:
            # choose a consistent winner when there's a tie
            return min(map(nodemap.__getitem__, ancs))
        return self.repo.nullid

    def commonancestorsheads(self, a, b):
        """calculate all the heads of the common ancestors of nodes a and b"""

        if a == self.repo.nullid or b == self.repo.nullid:
            return self.repo.nullid

        revmap, parentfunc = self._buildrevgraph(a, b)
        nodemap = {v: k for (k, v) in revmap.items()}

        ancs = ancestor.commonancestorsheads(parentfunc, revmap[a], revmap[b])
        return map(nodemap.__getitem__, ancs)

    def _buildrevgraph(self, a, b):
        """Builds a numeric revision graph for the given two nodes.
        Returns a node->rev map and a rev->[revs] parent function.
        """
        amap = self.ancestormap(a)
        bmap = self.ancestormap(b)

        # Union the two maps
        parentsmap = collections.defaultdict(list)
        allparents = set()
        for mapping in (amap, bmap):
            for node, pdata in mapping.items():
                parents = parentsmap[node]
                p1, p2, linknode, copyfrom = pdata
                # Don't follow renames (copyfrom).
                # remotefilectx.ancestor does that.
                if p1 != self.repo.nullid and not copyfrom:
                    parents.append(p1)
                    allparents.add(p1)
                if p2 != self.repo.nullid:
                    parents.append(p2)
                    allparents.add(p2)

        # Breadth first traversal to build linkrev graph
        parentrevs = collections.defaultdict(list)
        revmap = {}
        queue = collections.deque(
            ((None, n) for n in parentsmap if n not in allparents)
        )
        while queue:
            prevrev, current = queue.pop()
            if current in revmap:
                if prevrev:
                    parentrevs[prevrev].append(revmap[current])
                continue

            # Assign linkrevs in reverse order, so start at
            # len(parentsmap) and work backwards.
            currentrev = len(parentsmap) - len(revmap) - 1
            revmap[current] = currentrev

            if prevrev:
                parentrevs[prevrev].append(currentrev)

            for parent in parentsmap.get(current):
                queue.appendleft((currentrev, parent))

        return revmap, parentrevs.__getitem__

    def strip(self, minlink, transaction):
        pass

    # misc unused things
    def files(self):
        return []

    def checksize(self):
        return 0, 0
