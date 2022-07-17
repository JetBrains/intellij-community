# branchmap.py - logic to computes, maintain and stores branchmap for local repo
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import struct

from .node import (
    bin,
    hex,
    nullrev,
)
from . import (
    encoding,
    error,
    pycompat,
    scmutil,
    util,
)
from .utils import (
    repoviewutil,
    stringutil,
)

if pycompat.TYPE_CHECKING:
    from typing import (
        Any,
        Callable,
        Dict,
        Iterable,
        List,
        Optional,
        Set,
        Tuple,
        Union,
    )
    from . import localrepo

    assert any(
        (
            Any,
            Callable,
            Dict,
            Iterable,
            List,
            Optional,
            Set,
            Tuple,
            Union,
            localrepo,
        )
    )

subsettable = repoviewutil.subsettable

calcsize = struct.calcsize
pack_into = struct.pack_into
unpack_from = struct.unpack_from


class BranchMapCache(object):
    """mapping of filtered views of repo with their branchcache"""

    def __init__(self):
        self._per_filter = {}

    def __getitem__(self, repo):
        self.updatecache(repo)
        return self._per_filter[repo.filtername]

    def updatecache(self, repo):
        """Update the cache for the given filtered view on a repository"""
        # This can trigger updates for the caches for subsets of the filtered
        # view, e.g. when there is no cache for this filtered view or the cache
        # is stale.

        cl = repo.changelog
        filtername = repo.filtername
        bcache = self._per_filter.get(filtername)
        if bcache is None or not bcache.validfor(repo):
            # cache object missing or cache object stale? Read from disk
            bcache = branchcache.fromfile(repo)

        revs = []
        if bcache is None:
            # no (fresh) cache available anymore, perhaps we can re-use
            # the cache for a subset, then extend that to add info on missing
            # revisions.
            subsetname = subsettable.get(filtername)
            if subsetname is not None:
                subset = repo.filtered(subsetname)
                bcache = self[subset].copy()
                extrarevs = subset.changelog.filteredrevs - cl.filteredrevs
                revs.extend(r for r in extrarevs if r <= bcache.tiprev)
            else:
                # nothing to fall back on, start empty.
                bcache = branchcache(repo)

        revs.extend(cl.revs(start=bcache.tiprev + 1))
        if revs:
            bcache.update(repo, revs)

        assert bcache.validfor(repo), filtername
        self._per_filter[repo.filtername] = bcache

    def replace(self, repo, remotebranchmap):
        """Replace the branchmap cache for a repo with a branch mapping.

        This is likely only called during clone with a branch map from a
        remote.

        """
        cl = repo.changelog
        clrev = cl.rev
        clbranchinfo = cl.branchinfo
        rbheads = []
        closed = set()
        for bheads in pycompat.itervalues(remotebranchmap):
            rbheads += bheads
            for h in bheads:
                r = clrev(h)
                b, c = clbranchinfo(r)
                if c:
                    closed.add(h)

        if rbheads:
            rtiprev = max((int(clrev(node)) for node in rbheads))
            cache = branchcache(
                repo,
                remotebranchmap,
                repo[rtiprev].node(),
                rtiprev,
                closednodes=closed,
            )

            # Try to stick it as low as possible
            # filter above served are unlikely to be fetch from a clone
            for candidate in (b'base', b'immutable', b'served'):
                rview = repo.filtered(candidate)
                if cache.validfor(rview):
                    self._per_filter[candidate] = cache
                    cache.write(rview)
                    return

    def clear(self):
        self._per_filter.clear()


def _unknownnode(node):
    """raises ValueError when branchcache found a node which does not exists"""
    raise ValueError('node %s does not exist' % pycompat.sysstr(hex(node)))


def _branchcachedesc(repo):
    if repo.filtername is not None:
        return b'branch cache (%s)' % repo.filtername
    else:
        return b'branch cache'


class branchcache(object):
    """A dict like object that hold branches heads cache.

    This cache is used to avoid costly computations to determine all the
    branch heads of a repo.

    The cache is serialized on disk in the following format:

    <tip hex node> <tip rev number> [optional filtered repo hex hash]
    <branch head hex node> <open/closed state> <branch name>
    <branch head hex node> <open/closed state> <branch name>
    ...

    The first line is used to check if the cache is still valid. If the
    branch cache is for a filtered repo view, an optional third hash is
    included that hashes the hashes of all filtered revisions.

    The open/closed state is represented by a single letter 'o' or 'c'.
    This field can be used to avoid changelog reads when determining if a
    branch head closes a branch or not.
    """

    def __init__(
        self,
        repo,
        entries=(),
        tipnode=None,
        tiprev=nullrev,
        filteredhash=None,
        closednodes=None,
        hasnode=None,
    ):
        # type: (localrepo.localrepository, Union[Dict[bytes, List[bytes]], Iterable[Tuple[bytes, List[bytes]]]], bytes,  int, Optional[bytes], Optional[Set[bytes]], Optional[Callable[[bytes], bool]]) -> None
        """hasnode is a function which can be used to verify whether changelog
        has a given node or not. If it's not provided, we assume that every node
        we have exists in changelog"""
        self._repo = repo
        if tipnode is None:
            self.tipnode = repo.nullid
        else:
            self.tipnode = tipnode
        self.tiprev = tiprev
        self.filteredhash = filteredhash
        # closednodes is a set of nodes that close their branch. If the branch
        # cache has been updated, it may contain nodes that are no longer
        # heads.
        if closednodes is None:
            self._closednodes = set()
        else:
            self._closednodes = closednodes
        self._entries = dict(entries)
        # whether closed nodes are verified or not
        self._closedverified = False
        # branches for which nodes are verified
        self._verifiedbranches = set()
        self._hasnode = hasnode
        if self._hasnode is None:
            self._hasnode = lambda x: True

    def _verifyclosed(self):
        """verify the closed nodes we have"""
        if self._closedverified:
            return
        for node in self._closednodes:
            if not self._hasnode(node):
                _unknownnode(node)

        self._closedverified = True

    def _verifybranch(self, branch):
        """verify head nodes for the given branch."""
        if branch not in self._entries or branch in self._verifiedbranches:
            return
        for n in self._entries[branch]:
            if not self._hasnode(n):
                _unknownnode(n)

        self._verifiedbranches.add(branch)

    def _verifyall(self):
        """verifies nodes of all the branches"""
        needverification = set(self._entries.keys()) - self._verifiedbranches
        for b in needverification:
            self._verifybranch(b)

    def __iter__(self):
        return iter(self._entries)

    def __setitem__(self, key, value):
        self._entries[key] = value

    def __getitem__(self, key):
        self._verifybranch(key)
        return self._entries[key]

    def __contains__(self, key):
        self._verifybranch(key)
        return key in self._entries

    def iteritems(self):
        for k, v in pycompat.iteritems(self._entries):
            self._verifybranch(k)
            yield k, v

    items = iteritems

    def hasbranch(self, label):
        """checks whether a branch of this name exists or not"""
        self._verifybranch(label)
        return label in self._entries

    @classmethod
    def fromfile(cls, repo):
        f = None
        try:
            f = repo.cachevfs(cls._filename(repo))
            lineiter = iter(f)
            cachekey = next(lineiter).rstrip(b'\n').split(b" ", 2)
            last, lrev = cachekey[:2]
            last, lrev = bin(last), int(lrev)
            filteredhash = None
            hasnode = repo.changelog.hasnode
            if len(cachekey) > 2:
                filteredhash = bin(cachekey[2])
            bcache = cls(
                repo,
                tipnode=last,
                tiprev=lrev,
                filteredhash=filteredhash,
                hasnode=hasnode,
            )
            if not bcache.validfor(repo):
                # invalidate the cache
                raise ValueError('tip differs')
            bcache.load(repo, lineiter)
        except (IOError, OSError):
            return None

        except Exception as inst:
            if repo.ui.debugflag:
                msg = b'invalid %s: %s\n'
                repo.ui.debug(
                    msg
                    % (
                        _branchcachedesc(repo),
                        stringutil.forcebytestr(inst),
                    )
                )
            bcache = None

        finally:
            if f:
                f.close()

        return bcache

    def load(self, repo, lineiter):
        """fully loads the branchcache by reading from the file using the line
        iterator passed"""
        for line in lineiter:
            line = line.rstrip(b'\n')
            if not line:
                continue
            node, state, label = line.split(b" ", 2)
            if state not in b'oc':
                raise ValueError('invalid branch state')
            label = encoding.tolocal(label.strip())
            node = bin(node)
            self._entries.setdefault(label, []).append(node)
            if state == b'c':
                self._closednodes.add(node)

    @staticmethod
    def _filename(repo):
        """name of a branchcache file for a given repo or repoview"""
        filename = b"branch2"
        if repo.filtername:
            filename = b'%s-%s' % (filename, repo.filtername)
        return filename

    def validfor(self, repo):
        """Is the cache content valid regarding a repo

        - False when cached tipnode is unknown or if we detect a strip.
        - True when cache is up to date or a subset of current repo."""
        try:
            return (self.tipnode == repo.changelog.node(self.tiprev)) and (
                self.filteredhash == scmutil.filteredhash(repo, self.tiprev)
            )
        except IndexError:
            return False

    def _branchtip(self, heads):
        """Return tuple with last open head in heads and false,
        otherwise return last closed head and true."""
        tip = heads[-1]
        closed = True
        for h in reversed(heads):
            if h not in self._closednodes:
                tip = h
                closed = False
                break
        return tip, closed

    def branchtip(self, branch):
        """Return the tipmost open head on branch head, otherwise return the
        tipmost closed head on branch.
        Raise KeyError for unknown branch."""
        return self._branchtip(self[branch])[0]

    def iteropen(self, nodes):
        return (n for n in nodes if n not in self._closednodes)

    def branchheads(self, branch, closed=False):
        self._verifybranch(branch)
        heads = self._entries[branch]
        if not closed:
            heads = list(self.iteropen(heads))
        return heads

    def iterbranches(self):
        for bn, heads in pycompat.iteritems(self):
            yield (bn, heads) + self._branchtip(heads)

    def iterheads(self):
        """returns all the heads"""
        self._verifyall()
        return pycompat.itervalues(self._entries)

    def copy(self):
        """return an deep copy of the branchcache object"""
        return type(self)(
            self._repo,
            self._entries,
            self.tipnode,
            self.tiprev,
            self.filteredhash,
            self._closednodes,
        )

    def write(self, repo):
        try:
            f = repo.cachevfs(self._filename(repo), b"w", atomictemp=True)
            cachekey = [hex(self.tipnode), b'%d' % self.tiprev]
            if self.filteredhash is not None:
                cachekey.append(hex(self.filteredhash))
            f.write(b" ".join(cachekey) + b'\n')
            nodecount = 0
            for label, nodes in sorted(pycompat.iteritems(self._entries)):
                label = encoding.fromlocal(label)
                for node in nodes:
                    nodecount += 1
                    if node in self._closednodes:
                        state = b'c'
                    else:
                        state = b'o'
                    f.write(b"%s %s %s\n" % (hex(node), state, label))
            f.close()
            repo.ui.log(
                b'branchcache',
                b'wrote %s with %d labels and %d nodes\n',
                _branchcachedesc(repo),
                len(self._entries),
                nodecount,
            )
        except (IOError, OSError, error.Abort) as inst:
            # Abort may be raised by read only opener, so log and continue
            repo.ui.debug(
                b"couldn't write branch cache: %s\n"
                % stringutil.forcebytestr(inst)
            )

    def update(self, repo, revgen):
        """Given a branchhead cache, self, that may have extra nodes or be
        missing heads, and a generator of nodes that are strictly a superset of
        heads missing, this function updates self to be correct.
        """
        starttime = util.timer()
        cl = repo.changelog
        # collect new branch entries
        newbranches = {}
        getbranchinfo = repo.revbranchcache().branchinfo
        for r in revgen:
            branch, closesbranch = getbranchinfo(r)
            newbranches.setdefault(branch, []).append(r)
            if closesbranch:
                self._closednodes.add(cl.node(r))

        # new tip revision which we found after iterating items from new
        # branches
        ntiprev = self.tiprev

        # Delay fetching the topological heads until they are needed.
        # A repository without non-continous branches can skip this part.
        topoheads = None

        # If a changeset is visible, its parents must be visible too, so
        # use the faster unfiltered parent accessor.
        parentrevs = repo.unfiltered().changelog.parentrevs

        for branch, newheadrevs in pycompat.iteritems(newbranches):
            # For every branch, compute the new branchheads.
            # A branchhead is a revision such that no descendant is on
            # the same branch.
            #
            # The branchheads are computed iteratively in revision order.
            # This ensures topological order, i.e. parents are processed
            # before their children. Ancestors are inclusive here, i.e.
            # any revision is an ancestor of itself.
            #
            # Core observations:
            # - The current revision is always a branchhead for the
            #   repository up to that point.
            # - It is the first revision of the branch if and only if
            #   there was no branchhead before. In that case, it is the
            #   only branchhead as there are no possible ancestors on
            #   the same branch.
            # - If a parent is on the same branch, a branchhead can
            #   only be an ancestor of that parent, if it is parent
            #   itself. Otherwise it would have been removed as ancestor
            #   of that parent before.
            # - Therefore, if all parents are on the same branch, they
            #   can just be removed from the branchhead set.
            # - If one parent is on the same branch and the other is not
            #   and there was exactly one branchhead known, the existing
            #   branchhead can only be an ancestor if it is the parent.
            #   Otherwise it would have been removed as ancestor of
            #   the parent before. The other parent therefore can't have
            #   a branchhead as ancestor.
            # - In all other cases, the parents on different branches
            #   could have a branchhead as ancestor. Those parents are
            #   kept in the "uncertain" set. If all branchheads are also
            #   topological heads, they can't have descendants and further
            #   checks can be skipped. Otherwise, the ancestors of the
            #   "uncertain" set are removed from branchheads.
            #   This computation is heavy and avoided if at all possible.
            bheads = self._entries.setdefault(branch, [])
            bheadset = {cl.rev(node) for node in bheads}
            uncertain = set()
            for newrev in sorted(newheadrevs):
                if not bheadset:
                    bheadset.add(newrev)
                    continue

                parents = [p for p in parentrevs(newrev) if p != nullrev]
                samebranch = set()
                otherbranch = set()
                for p in parents:
                    if p in bheadset or getbranchinfo(p)[0] == branch:
                        samebranch.add(p)
                    else:
                        otherbranch.add(p)
                if otherbranch and not (len(bheadset) == len(samebranch) == 1):
                    uncertain.update(otherbranch)
                bheadset.difference_update(samebranch)
                bheadset.add(newrev)

            if uncertain:
                if topoheads is None:
                    topoheads = set(cl.headrevs())
                if bheadset - topoheads:
                    floorrev = min(bheadset)
                    ancestors = set(cl.ancestors(newheadrevs, floorrev))
                    bheadset -= ancestors
            bheadrevs = sorted(bheadset)
            self[branch] = [cl.node(rev) for rev in bheadrevs]
            tiprev = bheadrevs[-1]
            if tiprev > ntiprev:
                ntiprev = tiprev

        if ntiprev > self.tiprev:
            self.tiprev = ntiprev
            self.tipnode = cl.node(ntiprev)

        if not self.validfor(repo):
            # cache key are not valid anymore
            self.tipnode = repo.nullid
            self.tiprev = nullrev
            for heads in self.iterheads():
                tiprev = max(cl.rev(node) for node in heads)
                if tiprev > self.tiprev:
                    self.tipnode = cl.node(tiprev)
                    self.tiprev = tiprev
        self.filteredhash = scmutil.filteredhash(repo, self.tiprev)

        duration = util.timer() - starttime
        repo.ui.log(
            b'branchcache',
            b'updated %s in %.4f seconds\n',
            _branchcachedesc(repo),
            duration,
        )

        self.write(repo)


class remotebranchcache(branchcache):
    """Branchmap info for a remote connection, should not write locally"""

    def write(self, repo):
        pass


# Revision branch info cache

_rbcversion = b'-v1'
_rbcnames = b'rbc-names' + _rbcversion
_rbcrevs = b'rbc-revs' + _rbcversion
# [4 byte hash prefix][4 byte branch name number with sign bit indicating open]
_rbcrecfmt = b'>4sI'
_rbcrecsize = calcsize(_rbcrecfmt)
_rbcmininc = 64 * _rbcrecsize
_rbcnodelen = 4
_rbcbranchidxmask = 0x7FFFFFFF
_rbccloseflag = 0x80000000


class revbranchcache(object):
    """Persistent cache, mapping from revision number to branch name and close.
    This is a low level cache, independent of filtering.

    Branch names are stored in rbc-names in internal encoding separated by 0.
    rbc-names is append-only, and each branch name is only stored once and will
    thus have a unique index.

    The branch info for each revision is stored in rbc-revs as constant size
    records. The whole file is read into memory, but it is only 'parsed' on
    demand. The file is usually append-only but will be truncated if repo
    modification is detected.
    The record for each revision contains the first 4 bytes of the
    corresponding node hash, and the record is only used if it still matches.
    Even a completely trashed rbc-revs fill thus still give the right result
    while converging towards full recovery ... assuming no incorrectly matching
    node hashes.
    The record also contains 4 bytes where 31 bits contains the index of the
    branch and the last bit indicate that it is a branch close commit.
    The usage pattern for rbc-revs is thus somewhat similar to 00changelog.i
    and will grow with it but be 1/8th of its size.
    """

    def __init__(self, repo, readonly=True):
        assert repo.filtername is None
        self._repo = repo
        self._names = []  # branch names in local encoding with static index
        self._rbcrevs = bytearray()
        self._rbcsnameslen = 0  # length of names read at _rbcsnameslen
        try:
            bndata = repo.cachevfs.read(_rbcnames)
            self._rbcsnameslen = len(bndata)  # for verification before writing
            if bndata:
                self._names = [
                    encoding.tolocal(bn) for bn in bndata.split(b'\0')
                ]
        except (IOError, OSError):
            if readonly:
                # don't try to use cache - fall back to the slow path
                self.branchinfo = self._branchinfo

        if self._names:
            try:
                data = repo.cachevfs.read(_rbcrevs)
                self._rbcrevs[:] = data
            except (IOError, OSError) as inst:
                repo.ui.debug(
                    b"couldn't read revision branch cache: %s\n"
                    % stringutil.forcebytestr(inst)
                )
        # remember number of good records on disk
        self._rbcrevslen = min(
            len(self._rbcrevs) // _rbcrecsize, len(repo.changelog)
        )
        if self._rbcrevslen == 0:
            self._names = []
        self._rbcnamescount = len(self._names)  # number of names read at
        # _rbcsnameslen

    def _clear(self):
        self._rbcsnameslen = 0
        del self._names[:]
        self._rbcnamescount = 0
        self._rbcrevslen = len(self._repo.changelog)
        self._rbcrevs = bytearray(self._rbcrevslen * _rbcrecsize)
        util.clearcachedproperty(self, b'_namesreverse')

    @util.propertycache
    def _namesreverse(self):
        return {b: r for r, b in enumerate(self._names)}

    def branchinfo(self, rev):
        """Return branch name and close flag for rev, using and updating
        persistent cache."""
        changelog = self._repo.changelog
        rbcrevidx = rev * _rbcrecsize

        # avoid negative index, changelog.read(nullrev) is fast without cache
        if rev == nullrev:
            return changelog.branchinfo(rev)

        # if requested rev isn't allocated, grow and cache the rev info
        if len(self._rbcrevs) < rbcrevidx + _rbcrecsize:
            return self._branchinfo(rev)

        # fast path: extract data from cache, use it if node is matching
        reponode = changelog.node(rev)[:_rbcnodelen]
        cachenode, branchidx = unpack_from(
            _rbcrecfmt, util.buffer(self._rbcrevs), rbcrevidx
        )
        close = bool(branchidx & _rbccloseflag)
        if close:
            branchidx &= _rbcbranchidxmask
        if cachenode == b'\0\0\0\0':
            pass
        elif cachenode == reponode:
            try:
                return self._names[branchidx], close
            except IndexError:
                # recover from invalid reference to unknown branch
                self._repo.ui.debug(
                    b"referenced branch names not found"
                    b" - rebuilding revision branch cache from scratch\n"
                )
                self._clear()
        else:
            # rev/node map has changed, invalidate the cache from here up
            self._repo.ui.debug(
                b"history modification detected - truncating "
                b"revision branch cache to revision %d\n" % rev
            )
            truncate = rbcrevidx + _rbcrecsize
            del self._rbcrevs[truncate:]
            self._rbcrevslen = min(self._rbcrevslen, truncate)

        # fall back to slow path and make sure it will be written to disk
        return self._branchinfo(rev)

    def _branchinfo(self, rev):
        """Retrieve branch info from changelog and update _rbcrevs"""
        changelog = self._repo.changelog
        b, close = changelog.branchinfo(rev)
        if b in self._namesreverse:
            branchidx = self._namesreverse[b]
        else:
            branchidx = len(self._names)
            self._names.append(b)
            self._namesreverse[b] = branchidx
        reponode = changelog.node(rev)
        if close:
            branchidx |= _rbccloseflag
        self._setcachedata(rev, reponode, branchidx)
        return b, close

    def setdata(self, rev, changelogrevision):
        """add new data information to the cache"""
        branch, close = changelogrevision.branchinfo

        if branch in self._namesreverse:
            branchidx = self._namesreverse[branch]
        else:
            branchidx = len(self._names)
            self._names.append(branch)
            self._namesreverse[branch] = branchidx
        if close:
            branchidx |= _rbccloseflag
        self._setcachedata(rev, self._repo.changelog.node(rev), branchidx)
        # If no cache data were readable (non exists, bad permission, etc)
        # the cache was bypassing itself by setting:
        #
        #   self.branchinfo = self._branchinfo
        #
        # Since we now have data in the cache, we need to drop this bypassing.
        if 'branchinfo' in vars(self):
            del self.branchinfo

    def _setcachedata(self, rev, node, branchidx):
        """Writes the node's branch data to the in-memory cache data."""
        if rev == nullrev:
            return
        rbcrevidx = rev * _rbcrecsize
        requiredsize = rbcrevidx + _rbcrecsize
        rbccur = len(self._rbcrevs)
        if rbccur < requiredsize:
            # bytearray doesn't allocate extra space at least in Python 3.7.
            # When multiple changesets are added in a row, precise resize would
            # result in quadratic complexity. Overallocate to compensate by
            # use the classic doubling technique for dynamic arrays instead.
            # If there was a gap in the map before, less space will be reserved.
            self._rbcrevs.extend(b'\0' * max(_rbcmininc, requiredsize))
        pack_into(_rbcrecfmt, self._rbcrevs, rbcrevidx, node, branchidx)
        self._rbcrevslen = min(self._rbcrevslen, rev)

        tr = self._repo.currenttransaction()
        if tr:
            tr.addfinalize(b'write-revbranchcache', self.write)

    def write(self, tr=None):
        """Save branch cache if it is dirty."""
        repo = self._repo
        wlock = None
        step = b''
        try:
            # write the new names
            if self._rbcnamescount < len(self._names):
                wlock = repo.wlock(wait=False)
                step = b' names'
                self._writenames(repo)

            # write the new revs
            start = self._rbcrevslen * _rbcrecsize
            if start != len(self._rbcrevs):
                step = b''
                if wlock is None:
                    wlock = repo.wlock(wait=False)
                self._writerevs(repo, start)

        except (IOError, OSError, error.Abort, error.LockError) as inst:
            repo.ui.debug(
                b"couldn't write revision branch cache%s: %s\n"
                % (step, stringutil.forcebytestr(inst))
            )
        finally:
            if wlock is not None:
                wlock.release()

    def _writenames(self, repo):
        """write the new branch names to revbranchcache"""
        if self._rbcnamescount != 0:
            f = repo.cachevfs.open(_rbcnames, b'ab')
            if f.tell() == self._rbcsnameslen:
                f.write(b'\0')
            else:
                f.close()
                repo.ui.debug(b"%s changed - rewriting it\n" % _rbcnames)
                self._rbcnamescount = 0
                self._rbcrevslen = 0
        if self._rbcnamescount == 0:
            # before rewriting names, make sure references are removed
            repo.cachevfs.unlinkpath(_rbcrevs, ignoremissing=True)
            f = repo.cachevfs.open(_rbcnames, b'wb')
        f.write(
            b'\0'.join(
                encoding.fromlocal(b)
                for b in self._names[self._rbcnamescount :]
            )
        )
        self._rbcsnameslen = f.tell()
        f.close()
        self._rbcnamescount = len(self._names)

    def _writerevs(self, repo, start):
        """write the new revs to revbranchcache"""
        revs = min(len(repo.changelog), len(self._rbcrevs) // _rbcrecsize)
        with repo.cachevfs.open(_rbcrevs, b'ab') as f:
            if f.tell() != start:
                repo.ui.debug(
                    b"truncating cache/%s to %d\n" % (_rbcrevs, start)
                )
                f.seek(start)
                if f.tell() != start:
                    start = 0
                    f.seek(start)
                f.truncate()
            end = revs * _rbcrecsize
            f.write(self._rbcrevs[start:end])
        self._rbcrevslen = revs
