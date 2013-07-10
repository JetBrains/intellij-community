# ancestor.py - generic DAG ancestor algorithm for mercurial
#
# Copyright 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import heapq, util
from node import nullrev

def ancestors(pfunc, *orignodes):
    """
    Returns the common ancestors of a and b that are furthest from a
    root (as measured by longest path).

    pfunc must return a list of parent vertices for a given vertex.
    """
    if not isinstance(orignodes, set):
        orignodes = set(orignodes)
    if nullrev in orignodes:
        return set()
    if len(orignodes) <= 1:
        return orignodes

    def candidates(nodes):
        allseen = (1 << len(nodes)) - 1
        seen = [0] * (max(nodes) + 1)
        for i, n in enumerate(nodes):
            seen[n] = 1 << i
        poison = 1 << (i + 1)

        gca = set()
        interesting = left = len(nodes)
        nv = len(seen) - 1
        while nv >= 0 and interesting:
            v = nv
            nv -= 1
            if not seen[v]:
                continue
            sv = seen[v]
            if sv < poison:
                interesting -= 1
                if sv == allseen:
                    gca.add(v)
                    sv |= poison
                    if v in nodes:
                        left -= 1
                        if left <= 1:
                            # history is linear
                            return set([v])
            if sv < poison:
                for p in pfunc(v):
                    sp = seen[p]
                    if p == nullrev:
                        continue
                    if sp == 0:
                        seen[p] = sv
                        interesting += 1
                    elif sp != sv:
                        seen[p] |= sv
            else:
                for p in pfunc(v):
                    if p == nullrev:
                        continue
                    sp = seen[p]
                    if sp and sp < poison:
                        interesting -= 1
                    seen[p] = sv
        return gca

    def deepest(nodes):
        interesting = {}
        count = max(nodes) + 1
        depth = [0] * count
        seen = [0] * count
        mapping = []
        for (i, n) in enumerate(sorted(nodes)):
            depth[n] = 1
            b = 1 << i
            seen[n] = b
            interesting[b] = 1
            mapping.append((b, n))
        nv = count - 1
        while nv >= 0 and len(interesting) > 1:
            v = nv
            nv -= 1
            dv = depth[v]
            if dv == 0:
                continue
            sv = seen[v]
            for p in pfunc(v):
                if p == nullrev:
                    continue
                dp = depth[p]
                nsp = sp = seen[p]
                if dp <= dv:
                    depth[p] = dv + 1
                    if sp != sv:
                        interesting[sv] += 1
                        nsp = seen[p] = sv
                        if sp:
                            interesting[sp] -= 1
                            if interesting[sp] == 0:
                                del interesting[sp]
                elif dv == dp - 1:
                    nsp = sp | sv
                    if nsp == sp:
                        continue
                    seen[p] = nsp
                    interesting.setdefault(nsp, 0)
                    interesting[nsp] += 1
                    interesting[sp] -= 1
                    if interesting[sp] == 0:
                        del interesting[sp]
            interesting[sv] -= 1
            if interesting[sv] == 0:
                del interesting[sv]

        if len(interesting) != 1:
            return []

        k = 0
        for i in interesting:
            k |= i
        return set(n for (i, n) in mapping if k & i)

    gca = candidates(orignodes)

    if len(gca) <= 1:
        return gca
    return deepest(gca)

def genericancestor(a, b, pfunc):
    """
    Returns the common ancestor of a and b that is furthest from a
    root (as measured by longest path) or None if no ancestor is
    found. If there are multiple common ancestors at the same
    distance, the first one found is returned.

    pfunc must return a list of parent vertices for a given vertex
    """

    if a == b:
        return a

    a, b = sorted([a, b])

    # find depth from root of all ancestors
    # depth is stored as a negative for heapq
    parentcache = {}
    visit = [a, b]
    depth = {}
    while visit:
        vertex = visit[-1]
        pl = [p for p in pfunc(vertex) if p != nullrev]
        parentcache[vertex] = pl
        if not pl:
            depth[vertex] = 0
            visit.pop()
        else:
            for p in pl:
                if p == a or p == b: # did we find a or b as a parent?
                    return p # we're done
                if p not in depth:
                    visit.append(p)
            if visit[-1] == vertex:
                # -(maximum distance of parents + 1)
                depth[vertex] = min([depth[p] for p in pl]) - 1
                visit.pop()

    # traverse ancestors in order of decreasing distance from root
    def ancestors(vertex):
        h = [(depth[vertex], vertex)]
        seen = set()
        while h:
            d, n = heapq.heappop(h)
            if n not in seen:
                seen.add(n)
                yield (d, n)
                for p in parentcache[n]:
                    heapq.heappush(h, (depth[p], p))

    def generations(vertex):
        sg, s = None, set()
        for g, v in ancestors(vertex):
            if g != sg:
                if sg:
                    yield sg, s
                sg, s = g, set((v,))
            else:
                s.add(v)
        yield sg, s

    x = generations(a)
    y = generations(b)
    gx = x.next()
    gy = y.next()

    # increment each ancestor list until it is closer to root than
    # the other, or they match
    try:
        while True:
            if gx[0] == gy[0]:
                for v in gx[1]:
                    if v in gy[1]:
                        return v
                gy = y.next()
                gx = x.next()
            elif gx[0] > gy[0]:
                gy = y.next()
            else:
                gx = x.next()
    except StopIteration:
        return None

def missingancestors(revs, bases, pfunc):
    """Return all the ancestors of revs that are not ancestors of bases.

    This may include elements from revs.

    Equivalent to the revset (::revs - ::bases). Revs are returned in
    revision number order, which is a topological order.

    revs and bases should both be iterables. pfunc must return a list of
    parent revs for a given revs.
    """

    revsvisit = set(revs)
    basesvisit = set(bases)
    if not revsvisit:
        return []
    if not basesvisit:
        basesvisit.add(nullrev)
    start = max(max(revsvisit), max(basesvisit))
    bothvisit = revsvisit.intersection(basesvisit)
    revsvisit.difference_update(bothvisit)
    basesvisit.difference_update(bothvisit)
    # At this point, we hold the invariants that:
    # - revsvisit is the set of nodes we know are an ancestor of at least one
    #   of the nodes in revs
    # - basesvisit is the same for bases
    # - bothvisit is the set of nodes we know are ancestors of at least one of
    #   the nodes in revs and one of the nodes in bases
    # - a node may be in none or one, but not more, of revsvisit, basesvisit
    #   and bothvisit at any given time
    # Now we walk down in reverse topo order, adding parents of nodes already
    # visited to the sets while maintaining the invariants. When a node is
    # found in both revsvisit and basesvisit, it is removed from them and
    # added to bothvisit instead. When revsvisit becomes empty, there are no
    # more ancestors of revs that aren't also ancestors of bases, so exit.

    missing = []
    for curr in xrange(start, nullrev, -1):
        if not revsvisit:
            break

        if curr in bothvisit:
            bothvisit.remove(curr)
            # curr's parents might have made it into revsvisit or basesvisit
            # through another path
            for p in pfunc(curr):
                revsvisit.discard(p)
                basesvisit.discard(p)
                bothvisit.add(p)
            continue

        # curr will never be in both revsvisit and basesvisit, since if it
        # were it'd have been pushed to bothvisit
        if curr in revsvisit:
            missing.append(curr)
            thisvisit = revsvisit
            othervisit = basesvisit
        elif curr in basesvisit:
            thisvisit = basesvisit
            othervisit = revsvisit
        else:
            # not an ancestor of revs or bases: ignore
            continue

        thisvisit.remove(curr)
        for p in pfunc(curr):
            if p == nullrev:
                pass
            elif p in othervisit or p in bothvisit:
                # p is implicitly in thisvisit. This means p is or should be
                # in bothvisit
                revsvisit.discard(p)
                basesvisit.discard(p)
                bothvisit.add(p)
            else:
                # visit later
                thisvisit.add(p)

    missing.reverse()
    return missing

class lazyancestors(object):
    def __init__(self, cl, revs, stoprev=0, inclusive=False):
        """Create a new object generating ancestors for the given revs. Does
        not generate revs lower than stoprev.

        This is computed lazily starting from revs. The object supports
        iteration and membership.

        cl should be a changelog and revs should be an iterable. inclusive is
        a boolean that indicates whether revs should be included. Revs lower
        than stoprev will not be generated.

        Result does not include the null revision."""
        self._parentrevs = cl.parentrevs
        self._initrevs = revs
        self._stoprev = stoprev
        self._inclusive = inclusive

        # Initialize data structures for __contains__.
        # For __contains__, we use a heap rather than a deque because
        # (a) it minimizes the number of parentrevs calls made
        # (b) it makes the loop termination condition obvious
        # Python's heap is a min-heap. Multiply all values by -1 to convert it
        # into a max-heap.
        self._containsvisit = [-rev for rev in revs]
        heapq.heapify(self._containsvisit)
        if inclusive:
            self._containsseen = set(revs)
        else:
            self._containsseen = set()

    def __iter__(self):
        """Generate the ancestors of _initrevs in reverse topological order.

        If inclusive is False, yield a sequence of revision numbers starting
        with the parents of each revision in revs, i.e., each revision is *not*
        considered an ancestor of itself.  Results are in breadth-first order:
        parents of each rev in revs, then parents of those, etc.

        If inclusive is True, yield all the revs first (ignoring stoprev),
        then yield all the ancestors of revs as when inclusive is False.
        If an element in revs is an ancestor of a different rev it is not
        yielded again."""
        seen = set()
        revs = self._initrevs
        if self._inclusive:
            for rev in revs:
                yield rev
            seen.update(revs)

        parentrevs = self._parentrevs
        stoprev = self._stoprev
        visit = util.deque(revs)

        while visit:
            for parent in parentrevs(visit.popleft()):
                if parent >= stoprev and parent not in seen:
                    visit.append(parent)
                    seen.add(parent)
                    yield parent

    def __contains__(self, target):
        """Test whether target is an ancestor of self._initrevs."""
        # Trying to do both __iter__ and __contains__ using the same visit
        # heap and seen set is complex enough that it slows down both. Keep
        # them separate.
        seen = self._containsseen
        if target in seen:
            return True

        parentrevs = self._parentrevs
        visit = self._containsvisit
        stoprev = self._stoprev
        heappop = heapq.heappop
        heappush = heapq.heappush

        targetseen = False

        while visit and -visit[0] > target and not targetseen:
            for parent in parentrevs(-heappop(visit)):
                if parent < stoprev or parent in seen:
                    continue
                # We need to make sure we push all parents into the heap so
                # that we leave it in a consistent state for future calls.
                heappush(visit, -parent)
                seen.add(parent)
                if parent == target:
                    targetseen = True

        return targetseen
