# revlog.py - storage back-end for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Storage back-end for Mercurial.

This provides efficient delta storage with O(1) retrieve and append
and O(changes) merge between branches.
"""

# import stuff from node for others to import from revlog
from node import bin, hex, nullid, nullrev, short #@UnusedImport
from i18n import _
import changegroup, ancestor, mdiff, parsers, error, util
import struct, zlib, errno

_pack = struct.pack
_unpack = struct.unpack
_compress = zlib.compress
_decompress = zlib.decompress
_sha = util.sha1

# revlog flags
REVLOGV0 = 0
REVLOGNG = 1
REVLOGNGINLINEDATA = (1 << 16)
REVLOG_DEFAULT_FLAGS = REVLOGNGINLINEDATA
REVLOG_DEFAULT_FORMAT = REVLOGNG
REVLOG_DEFAULT_VERSION = REVLOG_DEFAULT_FORMAT | REVLOG_DEFAULT_FLAGS

# amount of data read unconditionally, should be >= 4
# when not inline: threshold for using lazy index
_prereadsize = 1048576
# max size of revlog with inline data
_maxinline = 131072

RevlogError = error.RevlogError
LookupError = error.LookupError

def getoffset(q):
    return int(q >> 16)

def gettype(q):
    return int(q & 0xFFFF)

def offset_type(offset, type):
    return long(long(offset) << 16 | type)

nullhash = _sha(nullid)

def hash(text, p1, p2):
    """generate a hash from the given text and its parent hashes

    This hash combines both the current file contents and its history
    in a manner that makes it easy to distinguish nodes with the same
    content in the revision graph.
    """
    # As of now, if one of the parent node is null, p2 is null
    if p2 == nullid:
        # deep copy of a hash is faster than creating one
        s = nullhash.copy()
        s.update(p1)
    else:
        # none of the parent nodes are nullid
        l = [p1, p2]
        l.sort()
        s = _sha(l[0])
        s.update(l[1])
    s.update(text)
    return s.digest()

def compress(text):
    """ generate a possibly-compressed representation of text """
    if not text:
        return ("", text)
    l = len(text)
    bin = None
    if l < 44:
        pass
    elif l > 1000000:
        # zlib makes an internal copy, thus doubling memory usage for
        # large files, so lets do this in pieces
        z = zlib.compressobj()
        p = []
        pos = 0
        while pos < l:
            pos2 = pos + 2**20
            p.append(z.compress(text[pos:pos2]))
            pos = pos2
        p.append(z.flush())
        if sum(map(len, p)) < l:
            bin = "".join(p)
    else:
        bin = _compress(text)
    if bin is None or len(bin) > l:
        if text[0] == '\0':
            return ("", text)
        return ('u', text)
    return ("", bin)

def decompress(bin):
    """ decompress the given input """
    if not bin:
        return bin
    t = bin[0]
    if t == '\0':
        return bin
    if t == 'x':
        return _decompress(bin)
    if t == 'u':
        return bin[1:]
    raise RevlogError(_("unknown compression type %r") % t)

class lazyparser(object):
    """
    this class avoids the need to parse the entirety of large indices
    """

    # lazyparser is not safe to use on windows if win32 extensions not
    # available. it keeps file handle open, which make it not possible
    # to break hardlinks on local cloned repos.

    def __init__(self, dataf):
        try:
            size = util.fstat(dataf).st_size
        except AttributeError:
            size = 0
        self.dataf = dataf
        self.s = struct.calcsize(indexformatng)
        self.datasize = size
        self.l = size / self.s
        self.index = [None] * self.l
        self.map = {nullid: nullrev}
        self.allmap = 0
        self.all = 0
        self.mapfind_count = 0

    def loadmap(self):
        """
        during a commit, we need to make sure the rev being added is
        not a duplicate.  This requires loading the entire index,
        which is fairly slow.  loadmap can load up just the node map,
        which takes much less time.
        """
        if self.allmap:
            return
        end = self.datasize
        self.allmap = 1
        cur = 0
        count = 0
        blocksize = self.s * 256
        self.dataf.seek(0)
        while cur < end:
            data = self.dataf.read(blocksize)
            off = 0
            for x in xrange(256):
                n = data[off + ngshaoffset:off + ngshaoffset + 20]
                self.map[n] = count
                count += 1
                if count >= self.l:
                    break
                off += self.s
            cur += blocksize

    def loadblock(self, blockstart, blocksize, data=None):
        if self.all:
            return
        if data is None:
            self.dataf.seek(blockstart)
            if blockstart + blocksize > self.datasize:
                # the revlog may have grown since we've started running,
                # but we don't have space in self.index for more entries.
                # limit blocksize so that we don't get too much data.
                blocksize = max(self.datasize - blockstart, 0)
            data = self.dataf.read(blocksize)
        lend = len(data) / self.s
        i = blockstart / self.s
        off = 0
        # lazyindex supports __delitem__
        if lend > len(self.index) - i:
            lend = len(self.index) - i
        for x in xrange(lend):
            if self.index[i + x] is None:
                b = data[off : off + self.s]
                self.index[i + x] = b
                n = b[ngshaoffset:ngshaoffset + 20]
                self.map[n] = i + x
            off += self.s

    def findnode(self, node):
        """search backwards through the index file for a specific node"""
        if self.allmap:
            return None

        # hg log will cause many many searches for the manifest
        # nodes.  After we get called a few times, just load the whole
        # thing.
        if self.mapfind_count > 8:
            self.loadmap()
            if node in self.map:
                return node
            return None
        self.mapfind_count += 1
        last = self.l - 1
        while self.index[last] != None:
            if last == 0:
                self.all = 1
                self.allmap = 1
                return None
            last -= 1
        end = (last + 1) * self.s
        blocksize = self.s * 256
        while end >= 0:
            start = max(end - blocksize, 0)
            self.dataf.seek(start)
            data = self.dataf.read(end - start)
            findend = end - start
            while True:
                # we're searching backwards, so we have to make sure
                # we don't find a changeset where this node is a parent
                off = data.find(node, 0, findend)
                findend = off
                if off >= 0:
                    i = off / self.s
                    off = i * self.s
                    n = data[off + ngshaoffset:off + ngshaoffset + 20]
                    if n == node:
                        self.map[n] = i + start / self.s
                        return node
                else:
                    break
            end -= blocksize
        return None

    def loadindex(self, i=None, end=None):
        if self.all:
            return
        all = False
        if i is None:
            blockstart = 0
            blocksize = (65536 / self.s) * self.s
            end = self.datasize
            all = True
        else:
            if end:
                blockstart = i * self.s
                end = end * self.s
                blocksize = end - blockstart
            else:
                blockstart = (i & ~1023) * self.s
                blocksize = self.s * 1024
                end = blockstart + blocksize
        while blockstart < end:
            self.loadblock(blockstart, blocksize)
            blockstart += blocksize
        if all:
            self.all = True

class lazyindex(object):
    """a lazy version of the index array"""
    def __init__(self, parser):
        self.p = parser
    def __len__(self):
        return len(self.p.index)
    def load(self, pos):
        if pos < 0:
            pos += len(self.p.index)
        self.p.loadindex(pos)
        return self.p.index[pos]
    def __getitem__(self, pos):
        return _unpack(indexformatng, self.p.index[pos] or self.load(pos))
    def __setitem__(self, pos, item):
        self.p.index[pos] = _pack(indexformatng, *item)
    def __delitem__(self, pos):
        del self.p.index[pos]
    def insert(self, pos, e):
        self.p.index.insert(pos, _pack(indexformatng, *e))
    def append(self, e):
        self.p.index.append(_pack(indexformatng, *e))

class lazymap(object):
    """a lazy version of the node map"""
    def __init__(self, parser):
        self.p = parser
    def load(self, key):
        n = self.p.findnode(key)
        if n is None:
            raise KeyError(key)
    def __contains__(self, key):
        if key in self.p.map:
            return True
        self.p.loadmap()
        return key in self.p.map
    def __iter__(self):
        yield nullid
        for i, ret in enumerate(self.p.index):
            if not ret:
                self.p.loadindex(i)
                ret = self.p.index[i]
            if isinstance(ret, str):
                ret = _unpack(indexformatng, ret)
            yield ret[7]
    def __getitem__(self, key):
        try:
            return self.p.map[key]
        except KeyError:
            try:
                self.load(key)
                return self.p.map[key]
            except KeyError:
                raise KeyError("node " + hex(key))
    def __setitem__(self, key, val):
        self.p.map[key] = val
    def __delitem__(self, key):
        del self.p.map[key]

indexformatv0 = ">4l20s20s20s"
v0shaoffset = 56

class revlogoldio(object):
    def __init__(self):
        self.size = struct.calcsize(indexformatv0)

    def parseindex(self, fp, data, inline):
        s = self.size
        index = []
        nodemap =  {nullid: nullrev}
        n = off = 0
        if len(data) == _prereadsize:
            data += fp.read() # read the rest
        l = len(data)
        while off + s <= l:
            cur = data[off:off + s]
            off += s
            e = _unpack(indexformatv0, cur)
            # transform to revlogv1 format
            e2 = (offset_type(e[0], 0), e[1], -1, e[2], e[3],
                  nodemap.get(e[4], nullrev), nodemap.get(e[5], nullrev), e[6])
            index.append(e2)
            nodemap[e[6]] = n
            n += 1

        return index, nodemap, None

    def packentry(self, entry, node, version, rev):
        if gettype(entry[0]):
            raise RevlogError(_("index entry flags need RevlogNG"))
        e2 = (getoffset(entry[0]), entry[1], entry[3], entry[4],
              node(entry[5]), node(entry[6]), entry[7])
        return _pack(indexformatv0, *e2)

# index ng:
# 6 bytes offset
# 2 bytes flags
# 4 bytes compressed length
# 4 bytes uncompressed length
# 4 bytes: base rev
# 4 bytes link rev
# 4 bytes parent 1 rev
# 4 bytes parent 2 rev
# 32 bytes: nodeid
indexformatng = ">Qiiiiii20s12x"
ngshaoffset = 32
versionformat = ">I"

class revlogio(object):
    def __init__(self):
        self.size = struct.calcsize(indexformatng)

    def parseindex(self, fp, data, inline):
        if len(data) == _prereadsize:
            if util.openhardlinks() and not inline:
                # big index, let's parse it on demand
                parser = lazyparser(fp)
                index = lazyindex(parser)
                nodemap = lazymap(parser)
                e = list(index[0])
                type = gettype(e[0])
                e[0] = offset_type(0, type)
                index[0] = e
                return index, nodemap, None
            else:
                data += fp.read()

        # call the C implementation to parse the index data
        index, nodemap, cache = parsers.parse_index(data, inline)
        return index, nodemap, cache

    def packentry(self, entry, node, version, rev):
        p = _pack(indexformatng, *entry)
        if rev == 0:
            p = _pack(versionformat, version) + p[4:]
        return p

class revlog(object):
    """
    the underlying revision storage object

    A revlog consists of two parts, an index and the revision data.

    The index is a file with a fixed record size containing
    information on each revision, including its nodeid (hash), the
    nodeids of its parents, the position and offset of its data within
    the data file, and the revision it's based on. Finally, each entry
    contains a linkrev entry that can serve as a pointer to external
    data.

    The revision data itself is a linear collection of data chunks.
    Each chunk represents a revision and is usually represented as a
    delta against the previous chunk. To bound lookup time, runs of
    deltas are limited to about 2 times the length of the original
    version data. This makes retrieval of a version proportional to
    its size, or O(1) relative to the number of revisions.

    Both pieces of the revlog are written to in an append-only
    fashion, which means we never need to rewrite a file to insert or
    remove data, and can use some simple techniques to avoid the need
    for locking while reading.
    """
    def __init__(self, opener, indexfile):
        """
        create a revlog object

        opener is a function that abstracts the file opening operation
        and can be used to implement COW semantics or the like.
        """
        self.indexfile = indexfile
        self.datafile = indexfile[:-2] + ".d"
        self.opener = opener
        self._cache = None
        self._chunkcache = (0, '')
        self.nodemap = {nullid: nullrev}
        self.index = []

        v = REVLOG_DEFAULT_VERSION
        if hasattr(opener, 'options') and 'defversion' in opener.options:
            v = opener.options['defversion']
            if v & REVLOGNG:
                v |= REVLOGNGINLINEDATA

        i = ''
        try:
            f = self.opener(self.indexfile)
            i = f.read(_prereadsize)
            if len(i) > 0:
                v = struct.unpack(versionformat, i[:4])[0]
        except IOError, inst:
            if inst.errno != errno.ENOENT:
                raise

        self.version = v
        self._inline = v & REVLOGNGINLINEDATA
        flags = v & ~0xFFFF
        fmt = v & 0xFFFF
        if fmt == REVLOGV0 and flags:
            raise RevlogError(_("index %s unknown flags %#04x for format v0")
                              % (self.indexfile, flags >> 16))
        elif fmt == REVLOGNG and flags & ~REVLOGNGINLINEDATA:
            raise RevlogError(_("index %s unknown flags %#04x for revlogng")
                              % (self.indexfile, flags >> 16))
        elif fmt > REVLOGNG:
            raise RevlogError(_("index %s unknown format %d")
                              % (self.indexfile, fmt))

        self._io = revlogio()
        if self.version == REVLOGV0:
            self._io = revlogoldio()
        if i:
            try:
                d = self._io.parseindex(f, i, self._inline)
            except (ValueError, IndexError):
                raise RevlogError(_("index %s is corrupted") % (self.indexfile))
            self.index, self.nodemap, self._chunkcache = d
            if not self._chunkcache:
                self._chunkclear()

        # add the magic null revision at -1 (if it hasn't been done already)
        if (self.index == [] or isinstance(self.index, lazyindex) or
            self.index[-1][7] != nullid) :
            self.index.append((0, 0, 0, -1, -1, -1, -1, nullid))

    def _loadindex(self, start, end):
        """load a block of indexes all at once from the lazy parser"""
        if isinstance(self.index, lazyindex):
            self.index.p.loadindex(start, end)

    def _loadindexmap(self):
        """loads both the map and the index from the lazy parser"""
        if isinstance(self.index, lazyindex):
            p = self.index.p
            p.loadindex()
            self.nodemap = p.map

    def _loadmap(self):
        """loads the map from the lazy parser"""
        if isinstance(self.nodemap, lazymap):
            self.nodemap.p.loadmap()
            self.nodemap = self.nodemap.p.map

    def tip(self):
        return self.node(len(self.index) - 2)
    def __len__(self):
        return len(self.index) - 1
    def __iter__(self):
        for i in xrange(len(self)):
            yield i
    def rev(self, node):
        try:
            return self.nodemap[node]
        except KeyError:
            raise LookupError(node, self.indexfile, _('no node'))
    def node(self, rev):
        return self.index[rev][7]
    def linkrev(self, rev):
        return self.index[rev][4]
    def parents(self, node):
        i = self.index
        d = i[self.rev(node)]
        return i[d[5]][7], i[d[6]][7] # map revisions to nodes inline
    def parentrevs(self, rev):
        return self.index[rev][5:7]
    def start(self, rev):
        return int(self.index[rev][0] >> 16)
    def end(self, rev):
        return self.start(rev) + self.length(rev)
    def length(self, rev):
        return self.index[rev][1]
    def base(self, rev):
        return self.index[rev][3]

    def size(self, rev):
        """return the length of the uncompressed text for a given revision"""
        l = self.index[rev][2]
        if l >= 0:
            return l

        t = self.revision(self.node(rev))
        return len(t)

    def reachable(self, node, stop=None):
        """return the set of all nodes ancestral to a given node, including
         the node itself, stopping when stop is matched"""
        reachable = set((node,))
        visit = [node]
        if stop:
            stopn = self.rev(stop)
        else:
            stopn = 0
        while visit:
            n = visit.pop(0)
            if n == stop:
                continue
            if n == nullid:
                continue
            for p in self.parents(n):
                if self.rev(p) < stopn:
                    continue
                if p not in reachable:
                    reachable.add(p)
                    visit.append(p)
        return reachable

    def ancestors(self, *revs):
        """Generate the ancestors of 'revs' in reverse topological order.

        Yield a sequence of revision numbers starting with the parents
        of each revision in revs, i.e., each revision is *not* considered
        an ancestor of itself.  Results are in breadth-first order:
        parents of each rev in revs, then parents of those, etc.  Result
        does not include the null revision."""
        visit = list(revs)
        seen = set([nullrev])
        while visit:
            for parent in self.parentrevs(visit.pop(0)):
                if parent not in seen:
                    visit.append(parent)
                    seen.add(parent)
                    yield parent

    def descendants(self, *revs):
        """Generate the descendants of 'revs' in revision order.

        Yield a sequence of revision numbers starting with a child of
        some rev in revs, i.e., each revision is *not* considered a
        descendant of itself.  Results are ordered by revision number (a
        topological sort)."""
        seen = set(revs)
        for i in xrange(min(revs) + 1, len(self)):
            for x in self.parentrevs(i):
                if x != nullrev and x in seen:
                    seen.add(i)
                    yield i
                    break

    def findmissing(self, common=None, heads=None):
        """Return the ancestors of heads that are not ancestors of common.

        More specifically, return a list of nodes N such that every N
        satisfies the following constraints:

          1. N is an ancestor of some node in 'heads'
          2. N is not an ancestor of any node in 'common'

        The list is sorted by revision number, meaning it is
        topologically sorted.

        'heads' and 'common' are both lists of node IDs.  If heads is
        not supplied, uses all of the revlog's heads.  If common is not
        supplied, uses nullid."""
        if common is None:
            common = [nullid]
        if heads is None:
            heads = self.heads()

        common = [self.rev(n) for n in common]
        heads = [self.rev(n) for n in heads]

        # we want the ancestors, but inclusive
        has = set(self.ancestors(*common))
        has.add(nullrev)
        has.update(common)

        # take all ancestors from heads that aren't in has
        missing = set()
        visit = [r for r in heads if r not in has]
        while visit:
            r = visit.pop(0)
            if r in missing:
                continue
            else:
                missing.add(r)
                for p in self.parentrevs(r):
                    if p not in has:
                        visit.append(p)
        missing = list(missing)
        missing.sort()
        return [self.node(r) for r in missing]

    def nodesbetween(self, roots=None, heads=None):
        """Return a topological path from 'roots' to 'heads'.

        Return a tuple (nodes, outroots, outheads) where 'nodes' is a
        topologically sorted list of all nodes N that satisfy both of
        these constraints:

          1. N is a descendant of some node in 'roots'
          2. N is an ancestor of some node in 'heads'

        Every node is considered to be both a descendant and an ancestor
        of itself, so every reachable node in 'roots' and 'heads' will be
        included in 'nodes'.

        'outroots' is the list of reachable nodes in 'roots', i.e., the
        subset of 'roots' that is returned in 'nodes'.  Likewise,
        'outheads' is the subset of 'heads' that is also in 'nodes'.

        'roots' and 'heads' are both lists of node IDs.  If 'roots' is
        unspecified, uses nullid as the only root.  If 'heads' is
        unspecified, uses list of all of the revlog's heads."""
        nonodes = ([], [], [])
        if roots is not None:
            roots = list(roots)
            if not roots:
                return nonodes
            lowestrev = min([self.rev(n) for n in roots])
        else:
            roots = [nullid] # Everybody's a descendent of nullid
            lowestrev = nullrev
        if (lowestrev == nullrev) and (heads is None):
            # We want _all_ the nodes!
            return ([self.node(r) for r in self], [nullid], list(self.heads()))
        if heads is None:
            # All nodes are ancestors, so the latest ancestor is the last
            # node.
            highestrev = len(self) - 1
            # Set ancestors to None to signal that every node is an ancestor.
            ancestors = None
            # Set heads to an empty dictionary for later discovery of heads
            heads = {}
        else:
            heads = list(heads)
            if not heads:
                return nonodes
            ancestors = set()
            # Turn heads into a dictionary so we can remove 'fake' heads.
            # Also, later we will be using it to filter out the heads we can't
            # find from roots.
            heads = dict.fromkeys(heads, 0)
            # Start at the top and keep marking parents until we're done.
            nodestotag = set(heads)
            # Remember where the top was so we can use it as a limit later.
            highestrev = max([self.rev(n) for n in nodestotag])
            while nodestotag:
                # grab a node to tag
                n = nodestotag.pop()
                # Never tag nullid
                if n == nullid:
                    continue
                # A node's revision number represents its place in a
                # topologically sorted list of nodes.
                r = self.rev(n)
                if r >= lowestrev:
                    if n not in ancestors:
                        # If we are possibly a descendent of one of the roots
                        # and we haven't already been marked as an ancestor
                        ancestors.add(n) # Mark as ancestor
                        # Add non-nullid parents to list of nodes to tag.
                        nodestotag.update([p for p in self.parents(n) if
                                           p != nullid])
                    elif n in heads: # We've seen it before, is it a fake head?
                        # So it is, real heads should not be the ancestors of
                        # any other heads.
                        heads.pop(n)
            if not ancestors:
                return nonodes
            # Now that we have our set of ancestors, we want to remove any
            # roots that are not ancestors.

            # If one of the roots was nullid, everything is included anyway.
            if lowestrev > nullrev:
                # But, since we weren't, let's recompute the lowest rev to not
                # include roots that aren't ancestors.

                # Filter out roots that aren't ancestors of heads
                roots = [n for n in roots if n in ancestors]
                # Recompute the lowest revision
                if roots:
                    lowestrev = min([self.rev(n) for n in roots])
                else:
                    # No more roots?  Return empty list
                    return nonodes
            else:
                # We are descending from nullid, and don't need to care about
                # any other roots.
                lowestrev = nullrev
                roots = [nullid]
        # Transform our roots list into a set.
        descendents = set(roots)
        # Also, keep the original roots so we can filter out roots that aren't
        # 'real' roots (i.e. are descended from other roots).
        roots = descendents.copy()
        # Our topologically sorted list of output nodes.
        orderedout = []
        # Don't start at nullid since we don't want nullid in our output list,
        # and if nullid shows up in descedents, empty parents will look like
        # they're descendents.
        for r in xrange(max(lowestrev, 0), highestrev + 1):
            n = self.node(r)
            isdescendent = False
            if lowestrev == nullrev:  # Everybody is a descendent of nullid
                isdescendent = True
            elif n in descendents:
                # n is already a descendent
                isdescendent = True
                # This check only needs to be done here because all the roots
                # will start being marked is descendents before the loop.
                if n in roots:
                    # If n was a root, check if it's a 'real' root.
                    p = tuple(self.parents(n))
                    # If any of its parents are descendents, it's not a root.
                    if (p[0] in descendents) or (p[1] in descendents):
                        roots.remove(n)
            else:
                p = tuple(self.parents(n))
                # A node is a descendent if either of its parents are
                # descendents.  (We seeded the dependents list with the roots
                # up there, remember?)
                if (p[0] in descendents) or (p[1] in descendents):
                    descendents.add(n)
                    isdescendent = True
            if isdescendent and ((ancestors is None) or (n in ancestors)):
                # Only include nodes that are both descendents and ancestors.
                orderedout.append(n)
                if (ancestors is not None) and (n in heads):
                    # We're trying to figure out which heads are reachable
                    # from roots.
                    # Mark this head as having been reached
                    heads[n] = 1
                elif ancestors is None:
                    # Otherwise, we're trying to discover the heads.
                    # Assume this is a head because if it isn't, the next step
                    # will eventually remove it.
                    heads[n] = 1
                    # But, obviously its parents aren't.
                    for p in self.parents(n):
                        heads.pop(p, None)
        heads = [n for n in heads.iterkeys() if heads[n] != 0]
        roots = list(roots)
        assert orderedout
        assert roots
        assert heads
        return (orderedout, roots, heads)

    def heads(self, start=None, stop=None):
        """return the list of all nodes that have no children

        if start is specified, only heads that are descendants of
        start will be returned
        if stop is specified, it will consider all the revs from stop
        as if they had no children
        """
        if start is None and stop is None:
            count = len(self)
            if not count:
                return [nullid]
            ishead = [1] * (count + 1)
            index = self.index
            for r in xrange(count):
                e = index[r]
                ishead[e[5]] = ishead[e[6]] = 0
            return [self.node(r) for r in xrange(count) if ishead[r]]

        if start is None:
            start = nullid
        if stop is None:
            stop = []
        stoprevs = set([self.rev(n) for n in stop])
        startrev = self.rev(start)
        reachable = set((startrev,))
        heads = set((startrev,))

        parentrevs = self.parentrevs
        for r in xrange(startrev + 1, len(self)):
            for p in parentrevs(r):
                if p in reachable:
                    if r not in stoprevs:
                        reachable.add(r)
                    heads.add(r)
                if p in heads and p not in stoprevs:
                    heads.remove(p)

        return [self.node(r) for r in heads]

    def children(self, node):
        """find the children of a given node"""
        c = []
        p = self.rev(node)
        for r in range(p + 1, len(self)):
            prevs = [pr for pr in self.parentrevs(r) if pr != nullrev]
            if prevs:
                for pr in prevs:
                    if pr == p:
                        c.append(self.node(r))
            elif p == nullrev:
                c.append(self.node(r))
        return c

    def _match(self, id):
        if isinstance(id, (long, int)):
            # rev
            return self.node(id)
        if len(id) == 20:
            # possibly a binary node
            # odds of a binary node being all hex in ASCII are 1 in 10**25
            try:
                node = id
                self.rev(node) # quick search the index
                return node
            except LookupError:
                pass # may be partial hex id
        try:
            # str(rev)
            rev = int(id)
            if str(rev) != id:
                raise ValueError
            if rev < 0:
                rev = len(self) + rev
            if rev < 0 or rev >= len(self):
                raise ValueError
            return self.node(rev)
        except (ValueError, OverflowError):
            pass
        if len(id) == 40:
            try:
                # a full hex nodeid?
                node = bin(id)
                self.rev(node)
                return node
            except (TypeError, LookupError):
                pass

    def _partialmatch(self, id):
        if len(id) < 40:
            try:
                # hex(node)[:...]
                l = len(id) // 2  # grab an even number of digits
                bin_id = bin(id[:l * 2])
                nl = [n for n in self.nodemap if n[:l] == bin_id]
                nl = [n for n in nl if hex(n).startswith(id)]
                if len(nl) > 0:
                    if len(nl) == 1:
                        return nl[0]
                    raise LookupError(id, self.indexfile,
                                      _('ambiguous identifier'))
                return None
            except TypeError:
                pass

    def lookup(self, id):
        """locate a node based on:
            - revision number or str(revision number)
            - nodeid or subset of hex nodeid
        """
        n = self._match(id)
        if n is not None:
            return n
        n = self._partialmatch(id)
        if n:
            return n

        raise LookupError(id, self.indexfile, _('no match found'))

    def cmp(self, node, text):
        """compare text with a given file revision"""
        p1, p2 = self.parents(node)
        return hash(text, p1, p2) != node

    def _addchunk(self, offset, data):
        o, d = self._chunkcache
        # try to add to existing cache
        if o + len(d) == offset and len(d) + len(data) < _prereadsize:
            self._chunkcache = o, d + data
        else:
            self._chunkcache = offset, data

    def _loadchunk(self, offset, length):
        if self._inline:
            df = self.opener(self.indexfile)
        else:
            df = self.opener(self.datafile)

        readahead = max(65536, length)
        df.seek(offset)
        d = df.read(readahead)
        self._addchunk(offset, d)
        if readahead > length:
            return d[:length]
        return d

    def _getchunk(self, offset, length):
        o, d = self._chunkcache
        l = len(d)

        # is it in the cache?
        cachestart = offset - o
        cacheend = cachestart + length
        if cachestart >= 0 and cacheend <= l:
            if cachestart == 0 and cacheend == l:
                return d # avoid a copy
            return d[cachestart:cacheend]

        return self._loadchunk(offset, length)

    def _chunkraw(self, startrev, endrev):
        start = self.start(startrev)
        length = self.end(endrev) - start
        if self._inline:
            start += (startrev + 1) * self._io.size
        return self._getchunk(start, length)

    def _chunk(self, rev):
        return decompress(self._chunkraw(rev, rev))

    def _chunkclear(self):
        self._chunkcache = (0, '')

    def revdiff(self, rev1, rev2):
        """return or calculate a delta between two revisions"""
        if rev1 + 1 == rev2 and self.base(rev1) == self.base(rev2):
            return self._chunk(rev2)

        return mdiff.textdiff(self.revision(self.node(rev1)),
                              self.revision(self.node(rev2)))

    def revision(self, node):
        """return an uncompressed revision of a given node"""
        if node == nullid:
            return ""
        if self._cache and self._cache[0] == node:
            return self._cache[2]

        # look up what we need to read
        text = None
        rev = self.rev(node)
        base = self.base(rev)

        # check rev flags
        if self.index[rev][0] & 0xFFFF:
            raise RevlogError(_('incompatible revision flag %x') %
                              (self.index[rev][0] & 0xFFFF))

        # do we have useful data cached?
        if self._cache and self._cache[1] >= base and self._cache[1] < rev:
            base = self._cache[1]
            text = self._cache[2]

        self._loadindex(base, rev + 1)
        self._chunkraw(base, rev)
        if text is None:
            text = self._chunk(base)

        bins = [self._chunk(r) for r in xrange(base + 1, rev + 1)]
        text = mdiff.patches(text, bins)
        p1, p2 = self.parents(node)
        if node != hash(text, p1, p2):
            raise RevlogError(_("integrity check failed on %s:%d")
                              % (self.indexfile, rev))

        self._cache = (node, rev, text)
        return text

    def checkinlinesize(self, tr, fp=None):
        if not self._inline or (self.start(-2) + self.length(-2)) < _maxinline:
            return

        trinfo = tr.find(self.indexfile)
        if trinfo is None:
            raise RevlogError(_("%s not found in the transaction")
                              % self.indexfile)

        trindex = trinfo[2]
        dataoff = self.start(trindex)

        tr.add(self.datafile, dataoff)

        if fp:
            fp.flush()
            fp.close()

        df = self.opener(self.datafile, 'w')
        try:
            for r in self:
                df.write(self._chunkraw(r, r))
        finally:
            df.close()

        fp = self.opener(self.indexfile, 'w', atomictemp=True)
        self.version &= ~(REVLOGNGINLINEDATA)
        self._inline = False
        for i in self:
            e = self._io.packentry(self.index[i], self.node, self.version, i)
            fp.write(e)

        # if we don't call rename, the temp file will never replace the
        # real index
        fp.rename()

        tr.replace(self.indexfile, trindex * self._io.size)
        self._chunkclear()

    def addrevision(self, text, transaction, link, p1, p2, d=None):
        """add a revision to the log

        text - the revision data to add
        transaction - the transaction object used for rollback
        link - the linkrev data to add
        p1, p2 - the parent nodeids of the revision
        d - an optional precomputed delta
        """
        dfh = None
        if not self._inline:
            dfh = self.opener(self.datafile, "a")
        ifh = self.opener(self.indexfile, "a+")
        try:
            return self._addrevision(text, transaction, link, p1, p2, d, ifh, dfh)
        finally:
            if dfh:
                dfh.close()
            ifh.close()

    def _addrevision(self, text, transaction, link, p1, p2, d, ifh, dfh):
        node = hash(text, p1, p2)
        if node in self.nodemap:
            return node

        curr = len(self)
        prev = curr - 1
        base = self.base(prev)
        offset = self.end(prev)

        if curr:
            if not d:
                ptext = self.revision(self.node(prev))
                d = mdiff.textdiff(ptext, text)
            data = compress(d)
            l = len(data[1]) + len(data[0])
            dist = l + offset - self.start(base)

        # full versions are inserted when the needed deltas
        # become comparable to the uncompressed text
        if not curr or dist > len(text) * 2:
            data = compress(text)
            l = len(data[1]) + len(data[0])
            base = curr

        e = (offset_type(offset, 0), l, len(text),
             base, link, self.rev(p1), self.rev(p2), node)
        self.index.insert(-1, e)
        self.nodemap[node] = curr

        entry = self._io.packentry(e, self.node, self.version, curr)
        if not self._inline:
            transaction.add(self.datafile, offset)
            transaction.add(self.indexfile, curr * len(entry))
            if data[0]:
                dfh.write(data[0])
            dfh.write(data[1])
            dfh.flush()
            ifh.write(entry)
        else:
            offset += curr * self._io.size
            transaction.add(self.indexfile, offset, curr)
            ifh.write(entry)
            ifh.write(data[0])
            ifh.write(data[1])
            self.checkinlinesize(transaction, ifh)

        if type(text) == str: # only accept immutable objects
            self._cache = (node, curr, text)
        return node

    def descendant(self, start, end):
        for i in self.descendants(start):
            if i == end:
                return True
            elif i > end:
                break
        return False

    def ancestor(self, a, b):
        """calculate the least common ancestor of nodes a and b"""

        # fast path, check if it is a descendant
        a, b = self.rev(a), self.rev(b)
        start, end = sorted((a, b))
        if self.descendant(start, end):
            return self.node(start)

        def parents(rev):
            return [p for p in self.parentrevs(rev) if p != nullrev]

        c = ancestor.ancestor(a, b, parents)
        if c is None:
            return nullid

        return self.node(c)

    def group(self, nodelist, lookup, infocollect=None):
        """Calculate a delta group, yielding a sequence of changegroup chunks
        (strings).

        Given a list of changeset revs, return a set of deltas and
        metadata corresponding to nodes. the first delta is
        parent(nodes[0]) -> nodes[0] the receiver is guaranteed to
        have this parent as it has all history before these
        changesets. parent is parent[0]
        """

        revs = [self.rev(n) for n in nodelist]

        # if we don't have any revisions touched by these changesets, bail
        if not revs:
            yield changegroup.closechunk()
            return

        # add the parent of the first rev
        p = self.parentrevs(revs[0])[0]
        revs.insert(0, p)

        # build deltas
        for d in xrange(len(revs) - 1):
            a, b = revs[d], revs[d + 1]
            nb = self.node(b)

            if infocollect is not None:
                infocollect(nb)

            p = self.parents(nb)
            meta = nb + p[0] + p[1] + lookup(nb)
            if a == -1:
                d = self.revision(nb)
                meta += mdiff.trivialdiffheader(len(d))
            else:
                d = self.revdiff(a, b)
            yield changegroup.chunkheader(len(meta) + len(d))
            yield meta
            if len(d) > 2**20:
                pos = 0
                while pos < len(d):
                    pos2 = pos + 2 ** 18
                    yield d[pos:pos2]
                    pos = pos2
            else:
                yield d

        yield changegroup.closechunk()

    def addgroup(self, revs, linkmapper, transaction):
        """
        add a delta group

        given a set of deltas, add them to the revision log. the
        first delta is against its parent, which should be in our
        log, the rest are against the previous delta.
        """

        #track the base of the current delta log
        r = len(self)
        t = r - 1
        node = None

        base = prev = nullrev
        start = end = textlen = 0
        if r:
            end = self.end(t)

        ifh = self.opener(self.indexfile, "a+")
        isize = r * self._io.size
        if self._inline:
            transaction.add(self.indexfile, end + isize, r)
            dfh = None
        else:
            transaction.add(self.indexfile, isize, r)
            transaction.add(self.datafile, end)
            dfh = self.opener(self.datafile, "a")

        try:
            # loop through our set of deltas
            chain = None
            for chunk in revs:
                node, p1, p2, cs = struct.unpack("20s20s20s20s", chunk[:80])
                link = linkmapper(cs)
                if node in self.nodemap:
                    # this can happen if two branches make the same change
                    chain = node
                    continue
                delta = buffer(chunk, 80)
                del chunk

                for p in (p1, p2):
                    if not p in self.nodemap:
                        raise LookupError(p, self.indexfile, _('unknown parent'))

                if not chain:
                    # retrieve the parent revision of the delta chain
                    chain = p1
                    if not chain in self.nodemap:
                        raise LookupError(chain, self.indexfile, _('unknown base'))

                # full versions are inserted when the needed deltas become
                # comparable to the uncompressed text or when the previous
                # version is not the one we have a delta against. We use
                # the size of the previous full rev as a proxy for the
                # current size.

                if chain == prev:
                    cdelta = compress(delta)
                    cdeltalen = len(cdelta[0]) + len(cdelta[1])
                    textlen = mdiff.patchedsize(textlen, delta)

                if chain != prev or (end - start + cdeltalen) > textlen * 2:
                    # flush our writes here so we can read it in revision
                    if dfh:
                        dfh.flush()
                    ifh.flush()
                    text = self.revision(chain)
                    if len(text) == 0:
                        # skip over trivial delta header
                        text = buffer(delta, 12)
                    else:
                        text = mdiff.patches(text, [delta])
                    del delta
                    chk = self._addrevision(text, transaction, link, p1, p2, None,
                                            ifh, dfh)
                    if not dfh and not self._inline:
                        # addrevision switched from inline to conventional
                        # reopen the index
                        dfh = self.opener(self.datafile, "a")
                        ifh = self.opener(self.indexfile, "a")
                    if chk != node:
                        raise RevlogError(_("consistency error adding group"))
                    textlen = len(text)
                else:
                    e = (offset_type(end, 0), cdeltalen, textlen, base,
                         link, self.rev(p1), self.rev(p2), node)
                    self.index.insert(-1, e)
                    self.nodemap[node] = r
                    entry = self._io.packentry(e, self.node, self.version, r)
                    if self._inline:
                        ifh.write(entry)
                        ifh.write(cdelta[0])
                        ifh.write(cdelta[1])
                        self.checkinlinesize(transaction, ifh)
                        if not self._inline:
                            dfh = self.opener(self.datafile, "a")
                            ifh = self.opener(self.indexfile, "a")
                    else:
                        dfh.write(cdelta[0])
                        dfh.write(cdelta[1])
                        ifh.write(entry)

                t, r, chain, prev = r, r + 1, node, node
                base = self.base(t)
                start = self.start(base)
                end = self.end(t)
        finally:
            if dfh:
                dfh.close()
            ifh.close()

        return node

    def strip(self, minlink, transaction):
        """truncate the revlog on the first revision with a linkrev >= minlink

        This function is called when we're stripping revision minlink and
        its descendants from the repository.

        We have to remove all revisions with linkrev >= minlink, because
        the equivalent changelog revisions will be renumbered after the
        strip.

        So we truncate the revlog on the first of these revisions, and
        trust that the caller has saved the revisions that shouldn't be
        removed and that it'll readd them after this truncation.
        """
        if len(self) == 0:
            return

        if isinstance(self.index, lazyindex):
            self._loadindexmap()

        for rev in self:
            if self.index[rev][4] >= minlink:
                break
        else:
            return

        # first truncate the files on disk
        end = self.start(rev)
        if not self._inline:
            transaction.add(self.datafile, end)
            end = rev * self._io.size
        else:
            end += rev * self._io.size

        transaction.add(self.indexfile, end)

        # then reset internal state in memory to forget those revisions
        self._cache = None
        self._chunkclear()
        for x in xrange(rev, len(self)):
            del self.nodemap[self.node(x)]

        del self.index[rev:-1]

    def checksize(self):
        expected = 0
        if len(self):
            expected = max(0, self.end(len(self) - 1))

        try:
            f = self.opener(self.datafile)
            f.seek(0, 2)
            actual = f.tell()
            dd = actual - expected
        except IOError, inst:
            if inst.errno != errno.ENOENT:
                raise
            dd = 0

        try:
            f = self.opener(self.indexfile)
            f.seek(0, 2)
            actual = f.tell()
            s = self._io.size
            i = max(0, actual // s)
            di = actual - (i * s)
            if self._inline:
                databytes = 0
                for r in self:
                    databytes += max(0, self.length(r))
                dd = 0
                di = actual - len(self) * s - databytes
        except IOError, inst:
            if inst.errno != errno.ENOENT:
                raise
            di = 0

        return (dd, di)

    def files(self):
        res = [self.indexfile]
        if not self._inline:
            res.append(self.datafile)
        return res
