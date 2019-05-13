# dagutil.py - dag utilities for mercurial
#
# Copyright 2010 Benoit Boissinot <bboissin@gmail.com>
# and Peter Arrenbrecht <peter@arrenbrecht.ch>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import nullrev
from i18n import _


class basedag(object):
    '''generic interface for DAGs

    terms:
    "ix" (short for index) identifies a nodes internally,
    "id" identifies one externally.

    All params are ixs unless explicitly suffixed otherwise.
    Pluralized params are lists or sets.
    '''

    def __init__(self):
        self._inverse = None

    def nodeset(self):
        '''set of all node idxs'''
        raise NotImplementedError

    def heads(self):
        '''list of head ixs'''
        raise NotImplementedError

    def parents(self, ix):
        '''list of parents ixs of ix'''
        raise NotImplementedError

    def inverse(self):
        '''inverse DAG, where parents becomes children, etc.'''
        raise NotImplementedError

    def ancestorset(self, starts, stops=None):
        '''
        set of all ancestors of starts (incl), but stop walk at stops (excl)
        '''
        raise NotImplementedError

    def descendantset(self, starts, stops=None):
        '''
        set of all descendants of starts (incl), but stop walk at stops (excl)
        '''
        return self.inverse().ancestorset(starts, stops)

    def headsetofconnecteds(self, ixs):
        '''
        subset of connected list of ixs so that no node has a descendant in it

        By "connected list" we mean that if an ancestor and a descendant are in
        the list, then so is at least one path connecting them.
        '''
        raise NotImplementedError

    def externalize(self, ix):
        '''return a list of (or set if given a set) of node ids'''
        return self._externalize(ix)

    def externalizeall(self, ixs):
        '''return a list of (or set if given a set) of node ids'''
        ids = self._externalizeall(ixs)
        if isinstance(ixs, set):
            return set(ids)
        return list(ids)

    def internalize(self, id):
        '''return a list of (or set if given a set) of node ixs'''
        return self._internalize(id)

    def internalizeall(self, ids, filterunknown=False):
        '''return a list of (or set if given a set) of node ids'''
        ixs = self._internalizeall(ids, filterunknown)
        if isinstance(ids, set):
            return set(ixs)
        return list(ixs)


class genericdag(basedag):
    '''generic implementations for DAGs'''

    def ancestorset(self, starts, stops=None):
        stops = stops and set(stops) or set()
        seen = set()
        pending = list(starts)
        while pending:
            n = pending.pop()
            if n not in seen and n not in stops:
                seen.add(n)
                pending.extend(self.parents(n))
        return seen

    def headsetofconnecteds(self, ixs):
        hds = set(ixs)
        if not hds:
            return hds
        for n in ixs:
            for p in self.parents(n):
                hds.discard(p)
        assert hds
        return hds


class revlogbaseddag(basedag):
    '''generic dag interface to a revlog'''

    def __init__(self, revlog, nodeset):
        basedag.__init__(self)
        self._revlog = revlog
        self._heads = None
        self._nodeset = nodeset

    def nodeset(self):
        return self._nodeset

    def heads(self):
        if self._heads is None:
            self._heads = self._getheads()
        return self._heads

    def _externalize(self, ix):
        return self._revlog.index[ix][7]
    def _externalizeall(self, ixs):
        idx = self._revlog.index
        return [idx[i][7] for i in ixs]

    def _internalize(self, id):
        ix = self._revlog.rev(id)
        if ix == nullrev:
            raise LookupError(id, self._revlog.indexfile, _('nullid'))
        return ix
    def _internalizeall(self, ids, filterunknown):
        rl = self._revlog
        if filterunknown:
            return [r for r in map(rl.nodemap.get, ids)
                    if r is not None and r != nullrev]
        return map(self._internalize, ids)


class revlogdag(revlogbaseddag):
    '''dag interface to a revlog'''

    def __init__(self, revlog):
        revlogbaseddag.__init__(self, revlog, set(xrange(len(revlog))))

    def _getheads(self):
        return [r for r in self._revlog.headrevs() if r != nullrev]

    def parents(self, ix):
        rlog = self._revlog
        idx = rlog.index
        revdata = idx[ix]
        prev = revdata[5]
        if prev != nullrev:
            prev2 = revdata[6]
            if prev2 == nullrev:
                return [prev]
            return [prev, prev2]
        prev2 = revdata[6]
        if prev2 != nullrev:
            return [prev2]
        return []

    def inverse(self):
        if self._inverse is None:
            self._inverse = inverserevlogdag(self)
        return self._inverse

    def ancestorset(self, starts, stops=None):
        rlog = self._revlog
        idx = rlog.index
        stops = stops and set(stops) or set()
        seen = set()
        pending = list(starts)
        while pending:
            rev = pending.pop()
            if rev not in seen and rev not in stops:
                seen.add(rev)
                revdata = idx[rev]
                for i in [5, 6]:
                    prev = revdata[i]
                    if prev != nullrev:
                        pending.append(prev)
        return seen

    def headsetofconnecteds(self, ixs):
        if not ixs:
            return set()
        rlog = self._revlog
        idx = rlog.index
        headrevs = set(ixs)
        for rev in ixs:
            revdata = idx[rev]
            for i in [5, 6]:
                prev = revdata[i]
                if prev != nullrev:
                    headrevs.discard(prev)
        assert headrevs
        return headrevs

    def linearize(self, ixs):
        '''linearize and topologically sort a list of revisions

        The linearization process tries to create long runs of revs where
        a child rev comes immediately after its first parent. This is done by
        visiting the heads of the given revs in inverse topological order,
        and for each visited rev, visiting its second parent, then its first
        parent, then adding the rev itself to the output list.
        '''
        sorted = []
        visit = list(self.headsetofconnecteds(ixs))
        visit.sort(reverse=True)
        finished = set()

        while visit:
            cur = visit.pop()
            if cur < 0:
                cur = -cur - 1
                if cur not in finished:
                    sorted.append(cur)
                    finished.add(cur)
            else:
                visit.append(-cur - 1)
                visit += [p for p in self.parents(cur)
                          if p in ixs and p not in finished]
        assert len(sorted) == len(ixs)
        return sorted


class inverserevlogdag(revlogbaseddag, genericdag):
    '''inverse of an existing revlog dag; see revlogdag.inverse()'''

    def __init__(self, orig):
        revlogbaseddag.__init__(self, orig._revlog, orig._nodeset)
        self._orig = orig
        self._children = {}
        self._roots = []
        self._walkfrom = len(self._revlog) - 1

    def _walkto(self, walkto):
        rev = self._walkfrom
        cs = self._children
        roots = self._roots
        idx = self._revlog.index
        while rev >= walkto:
            data = idx[rev]
            isroot = True
            for prev in [data[5], data[6]]: # parent revs
                if prev != nullrev:
                    cs.setdefault(prev, []).append(rev)
                    isroot = False
            if isroot:
                roots.append(rev)
            rev -= 1
        self._walkfrom = rev

    def _getheads(self):
        self._walkto(nullrev)
        return self._roots

    def parents(self, ix):
        if ix is None:
            return []
        if ix <= self._walkfrom:
            self._walkto(ix)
        return self._children.get(ix, [])

    def inverse(self):
        return self._orig
