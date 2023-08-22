""" Mercurial phases support code

    ---

    Copyright 2011 Pierre-Yves David <pierre-yves.david@ens-lyon.org>
                   Logilab SA        <contact@logilab.fr>
                   Augie Fackler     <durin42@gmail.com>

    This software may be used and distributed according to the terms
    of the GNU General Public License version 2 or any later version.

    ---

This module implements most phase logic in mercurial.


Basic Concept
=============

A 'changeset phase' is an indicator that tells us how a changeset is
manipulated and communicated. The details of each phase is described
below, here we describe the properties they have in common.

Like bookmarks, phases are not stored in history and thus are not
permanent and leave no audit trail.

First, no changeset can be in two phases at once. Phases are ordered,
so they can be considered from lowest to highest. The default, lowest
phase is 'public' - this is the normal phase of existing changesets. A
child changeset can not be in a lower phase than its parents.

These phases share a hierarchy of traits:

            immutable shared
    public:     X        X
    draft:               X
    secret:

Local commits are draft by default.

Phase Movement and Exchange
===========================

Phase data is exchanged by pushkey on pull and push. Some servers have
a publish option set, we call such a server a "publishing server".
Pushing a draft changeset to a publishing server changes the phase to
public.

A small list of fact/rules define the exchange of phase:

* old client never changes server states
* pull never changes server states
* publish and old server changesets are seen as public by client
* any secret changeset seen in another repository is lowered to at
  least draft

Here is the final table summing up the 49 possible use cases of phase
exchange:

                           server
                  old     publish      non-publish
                 N   X    N   D   P    N   D   P
    old client
    pull
     N           -   X/X  -   X/D X/P  -   X/D X/P
     X           -   X/X  -   X/D X/P  -   X/D X/P
    push
     X           X/X X/X  X/P X/P X/P  X/D X/D X/P
    new client
    pull
     N           -   P/X  -   P/D P/P  -   D/D P/P
     D           -   P/X  -   P/D P/P  -   D/D P/P
     P           -   P/X  -   P/D P/P  -   P/D P/P
    push
     D           P/X P/X  P/P P/P P/P  D/D D/D P/P
     P           P/X P/X  P/P P/P P/P  P/P P/P P/P

Legend:

    A/B = final state on client / state on server

    * N = new/not present,
    * P = public,
    * D = draft,
    * X = not tracked (i.e., the old client or server has no internal
          way of recording the phase.)

    passive = only pushes


    A cell here can be read like this:

    "When a new client pushes a draft changeset (D) to a publishing
    server where it's not present (N), it's marked public on both
    sides (P/P)."

Note: old client behave as a publishing server with draft only content
- other people see it as public
- content is pushed as draft

"""

from __future__ import absolute_import

import errno
import struct

from .i18n import _
from .node import (
    bin,
    hex,
    nullrev,
    short,
    wdirrev,
)
from .pycompat import (
    getattr,
    setattr,
)
from . import (
    error,
    pycompat,
    requirements,
    smartset,
    txnutil,
    util,
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
    )
    from . import (
        localrepo,
        ui as uimod,
    )

    Phaseroots = Dict[int, Set[bytes]]
    Phasedefaults = List[
        Callable[[localrepo.localrepository, Phaseroots], Phaseroots]
    ]


_fphasesentry = struct.Struct(b'>i20s')

# record phase index
public, draft, secret = range(3)  # type: int
archived = 32  # non-continuous for compatibility
internal = 96  # non-continuous for compatibility
allphases = (public, draft, secret, archived, internal)
trackedphases = (draft, secret, archived, internal)
# record phase names
cmdphasenames = [b'public', b'draft', b'secret']  # known to `hg phase` command
phasenames = dict(enumerate(cmdphasenames))
phasenames[archived] = b'archived'
phasenames[internal] = b'internal'
# map phase name to phase number
phasenumber = {name: phase for phase, name in phasenames.items()}
# like phasenumber, but also include maps for the numeric and binary
# phase number to the phase number
phasenumber2 = phasenumber.copy()
phasenumber2.update({phase: phase for phase in phasenames})
phasenumber2.update({b'%i' % phase: phase for phase in phasenames})
# record phase property
mutablephases = (draft, secret, archived, internal)
remotehiddenphases = (secret, archived, internal)
localhiddenphases = (internal, archived)


def supportinternal(repo):
    # type: (localrepo.localrepository) -> bool
    """True if the internal phase can be used on a repository"""
    return requirements.INTERNAL_PHASE_REQUIREMENT in repo.requirements


def _readroots(repo, phasedefaults=None):
    # type: (localrepo.localrepository, Optional[Phasedefaults]) -> Tuple[Phaseroots, bool]
    """Read phase roots from disk

    phasedefaults is a list of fn(repo, roots) callable, which are
    executed if the phase roots file does not exist. When phases are
    being initialized on an existing repository, this could be used to
    set selected changesets phase to something else than public.

    Return (roots, dirty) where dirty is true if roots differ from
    what is being stored.
    """
    repo = repo.unfiltered()
    dirty = False
    roots = {i: set() for i in allphases}
    try:
        f, pending = txnutil.trypending(repo.root, repo.svfs, b'phaseroots')
        try:
            for line in f:
                phase, nh = line.split()
                roots[int(phase)].add(bin(nh))
        finally:
            f.close()
    except IOError as inst:
        if inst.errno != errno.ENOENT:
            raise
        if phasedefaults:
            for f in phasedefaults:
                roots = f(repo, roots)
        dirty = True
    return roots, dirty


def binaryencode(phasemapping):
    # type: (Dict[int, List[bytes]]) -> bytes
    """encode a 'phase -> nodes' mapping into a binary stream

    The revision lists are encoded as (phase, root) pairs.
    """
    binarydata = []
    for phase, nodes in pycompat.iteritems(phasemapping):
        for head in nodes:
            binarydata.append(_fphasesentry.pack(phase, head))
    return b''.join(binarydata)


def binarydecode(stream):
    # type: (...) -> Dict[int, List[bytes]]
    """decode a binary stream into a 'phase -> nodes' mapping

    The (phase, root) pairs are turned back into a dictionary with
    the phase as index and the aggregated roots of that phase as value."""
    headsbyphase = {i: [] for i in allphases}
    entrysize = _fphasesentry.size
    while True:
        entry = stream.read(entrysize)
        if len(entry) < entrysize:
            if entry:
                raise error.Abort(_(b'bad phase-heads stream'))
            break
        phase, node = _fphasesentry.unpack(entry)
        headsbyphase[phase].append(node)
    return headsbyphase


def _sortedrange_insert(data, idx, rev, t):
    merge_before = False
    if idx:
        r1, t1 = data[idx - 1]
        merge_before = r1[-1] + 1 == rev and t1 == t
    merge_after = False
    if idx < len(data):
        r2, t2 = data[idx]
        merge_after = r2[0] == rev + 1 and t2 == t

    if merge_before and merge_after:
        data[idx - 1] = (pycompat.xrange(r1[0], r2[-1] + 1), t)
        data.pop(idx)
    elif merge_before:
        data[idx - 1] = (pycompat.xrange(r1[0], rev + 1), t)
    elif merge_after:
        data[idx] = (pycompat.xrange(rev, r2[-1] + 1), t)
    else:
        data.insert(idx, (pycompat.xrange(rev, rev + 1), t))


def _sortedrange_split(data, idx, rev, t):
    r1, t1 = data[idx]
    if t == t1:
        return
    t = (t1[0], t[1])
    if len(r1) == 1:
        data.pop(idx)
        _sortedrange_insert(data, idx, rev, t)
    elif r1[0] == rev:
        data[idx] = (pycompat.xrange(rev + 1, r1[-1] + 1), t1)
        _sortedrange_insert(data, idx, rev, t)
    elif r1[-1] == rev:
        data[idx] = (pycompat.xrange(r1[0], rev), t1)
        _sortedrange_insert(data, idx + 1, rev, t)
    else:
        data[idx : idx + 1] = [
            (pycompat.xrange(r1[0], rev), t1),
            (pycompat.xrange(rev, rev + 1), t),
            (pycompat.xrange(rev + 1, r1[-1] + 1), t1),
        ]


def _trackphasechange(data, rev, old, new):
    """add a phase move to the <data> list of ranges

    If data is None, nothing happens.
    """
    if data is None:
        return

    # If data is empty, create a one-revision range and done
    if not data:
        data.insert(0, (pycompat.xrange(rev, rev + 1), (old, new)))
        return

    low = 0
    high = len(data)
    t = (old, new)
    while low < high:
        mid = (low + high) // 2
        revs = data[mid][0]
        revs_low = revs[0]
        revs_high = revs[-1]

        if rev >= revs_low and rev <= revs_high:
            _sortedrange_split(data, mid, rev, t)
            return

        if revs_low == rev + 1:
            if mid and data[mid - 1][0][-1] == rev:
                _sortedrange_split(data, mid - 1, rev, t)
            else:
                _sortedrange_insert(data, mid, rev, t)
            return

        if revs_high == rev - 1:
            if mid + 1 < len(data) and data[mid + 1][0][0] == rev:
                _sortedrange_split(data, mid + 1, rev, t)
            else:
                _sortedrange_insert(data, mid + 1, rev, t)
            return

        if revs_low > rev:
            high = mid
        else:
            low = mid + 1

    if low == len(data):
        data.append((pycompat.xrange(rev, rev + 1), t))
        return

    r1, t1 = data[low]
    if r1[0] > rev:
        data.insert(low, (pycompat.xrange(rev, rev + 1), t))
    else:
        data.insert(low + 1, (pycompat.xrange(rev, rev + 1), t))


class phasecache(object):
    def __init__(self, repo, phasedefaults, _load=True):
        # type: (localrepo.localrepository, Optional[Phasedefaults], bool) -> None
        if _load:
            # Cheap trick to allow shallow-copy without copy module
            self.phaseroots, self.dirty = _readroots(repo, phasedefaults)
            self._loadedrevslen = 0
            self._phasesets = None
            self.filterunknown(repo)
            self.opener = repo.svfs

    def hasnonpublicphases(self, repo):
        # type: (localrepo.localrepository) -> bool
        """detect if there are revisions with non-public phase"""
        repo = repo.unfiltered()
        cl = repo.changelog
        if len(cl) >= self._loadedrevslen:
            self.invalidate()
            self.loadphaserevs(repo)
        return any(
            revs
            for phase, revs in pycompat.iteritems(self.phaseroots)
            if phase != public
        )

    def nonpublicphaseroots(self, repo):
        # type: (localrepo.localrepository) -> Set[bytes]
        """returns the roots of all non-public phases

        The roots are not minimized, so if the secret revisions are
        descendants of draft revisions, their roots will still be present.
        """
        repo = repo.unfiltered()
        cl = repo.changelog
        if len(cl) >= self._loadedrevslen:
            self.invalidate()
            self.loadphaserevs(repo)
        return set().union(
            *[
                revs
                for phase, revs in pycompat.iteritems(self.phaseroots)
                if phase != public
            ]
        )

    def getrevset(self, repo, phases, subset=None):
        # type: (localrepo.localrepository, Iterable[int], Optional[Any]) -> Any
        # TODO: finish typing this
        """return a smartset for the given phases"""
        self.loadphaserevs(repo)  # ensure phase's sets are loaded
        phases = set(phases)
        publicphase = public in phases

        if publicphase:
            # In this case, phases keeps all the *other* phases.
            phases = set(allphases).difference(phases)
            if not phases:
                return smartset.fullreposet(repo)

        # fast path: _phasesets contains the interesting sets,
        # might only need a union and post-filtering.
        revsneedscopy = False
        if len(phases) == 1:
            [p] = phases
            revs = self._phasesets[p]
            revsneedscopy = True  # Don't modify _phasesets
        else:
            # revs has the revisions in all *other* phases.
            revs = set.union(*[self._phasesets[p] for p in phases])

        def _addwdir(wdirsubset, wdirrevs):
            if wdirrev in wdirsubset and repo[None].phase() in phases:
                if revsneedscopy:
                    wdirrevs = wdirrevs.copy()
                # The working dir would never be in the # cache, but it was in
                # the subset being filtered for its phase (or filtered out,
                # depending on publicphase), so add it to the output to be
                # included (or filtered out).
                wdirrevs.add(wdirrev)
            return wdirrevs

        if not publicphase:
            if repo.changelog.filteredrevs:
                revs = revs - repo.changelog.filteredrevs

            if subset is None:
                return smartset.baseset(revs)
            else:
                revs = _addwdir(subset, revs)
                return subset & smartset.baseset(revs)
        else:
            if subset is None:
                subset = smartset.fullreposet(repo)

            revs = _addwdir(subset, revs)

            if not revs:
                return subset
            return subset.filter(lambda r: r not in revs)

    def copy(self):
        # Shallow copy meant to ensure isolation in
        # advance/retractboundary(), nothing more.
        ph = self.__class__(None, None, _load=False)
        ph.phaseroots = self.phaseroots.copy()
        ph.dirty = self.dirty
        ph.opener = self.opener
        ph._loadedrevslen = self._loadedrevslen
        ph._phasesets = self._phasesets
        return ph

    def replace(self, phcache):
        """replace all values in 'self' with content of phcache"""
        for a in (
            b'phaseroots',
            b'dirty',
            b'opener',
            b'_loadedrevslen',
            b'_phasesets',
        ):
            setattr(self, a, getattr(phcache, a))

    def _getphaserevsnative(self, repo):
        repo = repo.unfiltered()
        return repo.changelog.computephases(self.phaseroots)

    def _computephaserevspure(self, repo):
        repo = repo.unfiltered()
        cl = repo.changelog
        self._phasesets = {phase: set() for phase in allphases}
        lowerroots = set()
        for phase in reversed(trackedphases):
            roots = pycompat.maplist(cl.rev, self.phaseroots[phase])
            if roots:
                ps = set(cl.descendants(roots))
                for root in roots:
                    ps.add(root)
                ps.difference_update(lowerroots)
                lowerroots.update(ps)
                self._phasesets[phase] = ps
        self._loadedrevslen = len(cl)

    def loadphaserevs(self, repo):
        # type: (localrepo.localrepository) -> None
        """ensure phase information is loaded in the object"""
        if self._phasesets is None:
            try:
                res = self._getphaserevsnative(repo)
                self._loadedrevslen, self._phasesets = res
            except AttributeError:
                self._computephaserevspure(repo)

    def invalidate(self):
        self._loadedrevslen = 0
        self._phasesets = None

    def phase(self, repo, rev):
        # type: (localrepo.localrepository, int) -> int
        # We need a repo argument here to be able to build _phasesets
        # if necessary. The repository instance is not stored in
        # phasecache to avoid reference cycles. The changelog instance
        # is not stored because it is a filecache() property and can
        # be replaced without us being notified.
        if rev == nullrev:
            return public
        if rev < nullrev:
            raise ValueError(_(b'cannot lookup negative revision'))
        if rev >= self._loadedrevslen:
            self.invalidate()
            self.loadphaserevs(repo)
        for phase in trackedphases:
            if rev in self._phasesets[phase]:
                return phase
        return public

    def write(self):
        if not self.dirty:
            return
        f = self.opener(b'phaseroots', b'w', atomictemp=True, checkambig=True)
        try:
            self._write(f)
        finally:
            f.close()

    def _write(self, fp):
        for phase, roots in pycompat.iteritems(self.phaseroots):
            for h in sorted(roots):
                fp.write(b'%i %s\n' % (phase, hex(h)))
        self.dirty = False

    def _updateroots(self, phase, newroots, tr):
        self.phaseroots[phase] = newroots
        self.invalidate()
        self.dirty = True

        tr.addfilegenerator(b'phase', (b'phaseroots',), self._write)
        tr.hookargs[b'phases_moved'] = b'1'

    def registernew(self, repo, tr, targetphase, revs):
        repo = repo.unfiltered()
        self._retractboundary(repo, tr, targetphase, [], revs=revs)
        if tr is not None and b'phases' in tr.changes:
            phasetracking = tr.changes[b'phases']
            phase = self.phase
            for rev in sorted(revs):
                revphase = phase(repo, rev)
                _trackphasechange(phasetracking, rev, None, revphase)
        repo.invalidatevolatilesets()

    def advanceboundary(
        self, repo, tr, targetphase, nodes, revs=None, dryrun=None
    ):
        """Set all 'nodes' to phase 'targetphase'

        Nodes with a phase lower than 'targetphase' are not affected.

        If dryrun is True, no actions will be performed

        Returns a set of revs whose phase is changed or should be changed
        """
        # Be careful to preserve shallow-copied values: do not update
        # phaseroots values, replace them.
        if revs is None:
            revs = []
        if tr is None:
            phasetracking = None
        else:
            phasetracking = tr.changes.get(b'phases')

        repo = repo.unfiltered()
        revs = [repo[n].rev() for n in nodes] + [r for r in revs]

        changes = set()  # set of revisions to be changed
        delroots = []  # set of root deleted by this path
        for phase in (phase for phase in allphases if phase > targetphase):
            # filter nodes that are not in a compatible phase already
            revs = [rev for rev in revs if self.phase(repo, rev) >= phase]
            if not revs:
                break  # no roots to move anymore

            olds = self.phaseroots[phase]

            affected = repo.revs(b'%ln::%ld', olds, revs)
            changes.update(affected)
            if dryrun:
                continue
            for r in affected:
                _trackphasechange(
                    phasetracking, r, self.phase(repo, r), targetphase
                )

            roots = {
                ctx.node()
                for ctx in repo.set(b'roots((%ln::) - %ld)', olds, affected)
            }
            if olds != roots:
                self._updateroots(phase, roots, tr)
                # some roots may need to be declared for lower phases
                delroots.extend(olds - roots)
        if not dryrun:
            # declare deleted root in the target phase
            if targetphase != 0:
                self._retractboundary(repo, tr, targetphase, delroots)
            repo.invalidatevolatilesets()
        return changes

    def retractboundary(self, repo, tr, targetphase, nodes):
        oldroots = {
            phase: revs
            for phase, revs in pycompat.iteritems(self.phaseroots)
            if phase <= targetphase
        }
        if tr is None:
            phasetracking = None
        else:
            phasetracking = tr.changes.get(b'phases')
        repo = repo.unfiltered()
        if (
            self._retractboundary(repo, tr, targetphase, nodes)
            and phasetracking is not None
        ):

            # find the affected revisions
            new = self.phaseroots[targetphase]
            old = oldroots[targetphase]
            affected = set(repo.revs(b'(%ln::) - (%ln::)', new, old))

            # find the phase of the affected revision
            for phase in pycompat.xrange(targetphase, -1, -1):
                if phase:
                    roots = oldroots.get(phase, [])
                    revs = set(repo.revs(b'%ln::%ld', roots, affected))
                    affected -= revs
                else:  # public phase
                    revs = affected
                for r in sorted(revs):
                    _trackphasechange(phasetracking, r, phase, targetphase)
        repo.invalidatevolatilesets()

    def _retractboundary(self, repo, tr, targetphase, nodes, revs=None):
        # Be careful to preserve shallow-copied values: do not update
        # phaseroots values, replace them.
        if revs is None:
            revs = []
        if targetphase in (archived, internal) and not supportinternal(repo):
            name = phasenames[targetphase]
            msg = b'this repository does not support the %s phase' % name
            raise error.ProgrammingError(msg)

        repo = repo.unfiltered()
        torev = repo.changelog.rev
        tonode = repo.changelog.node
        currentroots = {torev(node) for node in self.phaseroots[targetphase]}
        finalroots = oldroots = set(currentroots)
        newroots = [torev(node) for node in nodes] + [r for r in revs]
        newroots = [
            rev for rev in newroots if self.phase(repo, rev) < targetphase
        ]

        if newroots:
            if nullrev in newroots:
                raise error.Abort(_(b'cannot change null revision phase'))
            currentroots.update(newroots)

            # Only compute new roots for revs above the roots that are being
            # retracted.
            minnewroot = min(newroots)
            aboveroots = [rev for rev in currentroots if rev >= minnewroot]
            updatedroots = repo.revs(b'roots(%ld::)', aboveroots)

            finalroots = {rev for rev in currentroots if rev < minnewroot}
            finalroots.update(updatedroots)
        if finalroots != oldroots:
            self._updateroots(
                targetphase, {tonode(rev) for rev in finalroots}, tr
            )
            return True
        return False

    def filterunknown(self, repo):
        # type: (localrepo.localrepository) -> None
        """remove unknown nodes from the phase boundary

        Nothing is lost as unknown nodes only hold data for their descendants.
        """
        filtered = False
        has_node = repo.changelog.index.has_node  # to filter unknown nodes
        for phase, nodes in pycompat.iteritems(self.phaseroots):
            missing = sorted(node for node in nodes if not has_node(node))
            if missing:
                for mnode in missing:
                    repo.ui.debug(
                        b'removing unknown node %s from %i-phase boundary\n'
                        % (short(mnode), phase)
                    )
                nodes.symmetric_difference_update(missing)
                filtered = True
        if filtered:
            self.dirty = True
        # filterunknown is called by repo.destroyed, we may have no changes in
        # root but _phasesets contents is certainly invalid (or at least we
        # have not proper way to check that). related to issue 3858.
        #
        # The other caller is __init__ that have no _phasesets initialized
        # anyway. If this change we should consider adding a dedicated
        # "destroyed" function to phasecache or a proper cache key mechanism
        # (see branchmap one)
        self.invalidate()


def advanceboundary(repo, tr, targetphase, nodes, revs=None, dryrun=None):
    """Add nodes to a phase changing other nodes phases if necessary.

    This function move boundary *forward* this means that all nodes
    are set in the target phase or kept in a *lower* phase.

    Simplify boundary to contains phase roots only.

    If dryrun is True, no actions will be performed

    Returns a set of revs whose phase is changed or should be changed
    """
    if revs is None:
        revs = []
    phcache = repo._phasecache.copy()
    changes = phcache.advanceboundary(
        repo, tr, targetphase, nodes, revs=revs, dryrun=dryrun
    )
    if not dryrun:
        repo._phasecache.replace(phcache)
    return changes


def retractboundary(repo, tr, targetphase, nodes):
    """Set nodes back to a phase changing other nodes phases if
    necessary.

    This function move boundary *backward* this means that all nodes
    are set in the target phase or kept in a *higher* phase.

    Simplify boundary to contains phase roots only."""
    phcache = repo._phasecache.copy()
    phcache.retractboundary(repo, tr, targetphase, nodes)
    repo._phasecache.replace(phcache)


def registernew(repo, tr, targetphase, revs):
    """register a new revision and its phase

    Code adding revisions to the repository should use this function to
    set new changeset in their target phase (or higher).
    """
    phcache = repo._phasecache.copy()
    phcache.registernew(repo, tr, targetphase, revs)
    repo._phasecache.replace(phcache)


def listphases(repo):
    # type: (localrepo.localrepository) -> Dict[bytes, bytes]
    """List phases root for serialization over pushkey"""
    # Use ordered dictionary so behavior is deterministic.
    keys = util.sortdict()
    value = b'%i' % draft
    cl = repo.unfiltered().changelog
    for root in repo._phasecache.phaseroots[draft]:
        if repo._phasecache.phase(repo, cl.rev(root)) <= draft:
            keys[hex(root)] = value

    if repo.publishing():
        # Add an extra data to let remote know we are a publishing
        # repo. Publishing repo can't just pretend they are old repo.
        # When pushing to a publishing repo, the client still need to
        # push phase boundary
        #
        # Push do not only push changeset. It also push phase data.
        # New phase data may apply to common changeset which won't be
        # push (as they are common). Here is a very simple example:
        #
        # 1) repo A push changeset X as draft to repo B
        # 2) repo B make changeset X public
        # 3) repo B push to repo A. X is not pushed but the data that
        #    X as now public should
        #
        # The server can't handle it on it's own as it has no idea of
        # client phase data.
        keys[b'publishing'] = b'True'
    return keys


def pushphase(repo, nhex, oldphasestr, newphasestr):
    # type: (localrepo.localrepository, bytes, bytes, bytes) -> bool
    """List phases root for serialization over pushkey"""
    repo = repo.unfiltered()
    with repo.lock():
        currentphase = repo[nhex].phase()
        newphase = abs(int(newphasestr))  # let's avoid negative index surprise
        oldphase = abs(int(oldphasestr))  # let's avoid negative index surprise
        if currentphase == oldphase and newphase < oldphase:
            with repo.transaction(b'pushkey-phase') as tr:
                advanceboundary(repo, tr, newphase, [bin(nhex)])
            return True
        elif currentphase == newphase:
            # raced, but got correct result
            return True
        else:
            return False


def subsetphaseheads(repo, subset):
    """Finds the phase heads for a subset of a history

    Returns a list indexed by phase number where each item is a list of phase
    head nodes.
    """
    cl = repo.changelog

    headsbyphase = {i: [] for i in allphases}
    # No need to keep track of secret phase; any heads in the subset that
    # are not mentioned are implicitly secret.
    for phase in allphases[:secret]:
        revset = b"heads(%%ln & %s())" % phasenames[phase]
        headsbyphase[phase] = [cl.node(r) for r in repo.revs(revset, subset)]
    return headsbyphase


def updatephases(repo, trgetter, headsbyphase):
    """Updates the repo with the given phase heads"""
    # Now advance phase boundaries of all phases
    #
    # run the update (and fetch transaction) only if there are actually things
    # to update. This avoid creating empty transaction during no-op operation.

    for phase in allphases:
        revset = b'%ln - _phase(%s)'
        heads = [c.node() for c in repo.set(revset, headsbyphase[phase], phase)]
        if heads:
            advanceboundary(repo, trgetter(), phase, heads)


def analyzeremotephases(repo, subset, roots):
    """Compute phases heads and root in a subset of node from root dict

    * subset is heads of the subset
    * roots is {<nodeid> => phase} mapping. key and value are string.

    Accept unknown element input
    """
    repo = repo.unfiltered()
    # build list from dictionary
    draftroots = []
    has_node = repo.changelog.index.has_node  # to filter unknown nodes
    for nhex, phase in pycompat.iteritems(roots):
        if nhex == b'publishing':  # ignore data related to publish option
            continue
        node = bin(nhex)
        phase = int(phase)
        if phase == public:
            if node != repo.nullid:
                repo.ui.warn(
                    _(
                        b'ignoring inconsistent public root'
                        b' from remote: %s\n'
                    )
                    % nhex
                )
        elif phase == draft:
            if has_node(node):
                draftroots.append(node)
        else:
            repo.ui.warn(
                _(b'ignoring unexpected root from remote: %i %s\n')
                % (phase, nhex)
            )
    # compute heads
    publicheads = newheads(repo, subset, draftroots)
    return publicheads, draftroots


class remotephasessummary(object):
    """summarize phase information on the remote side

    :publishing: True is the remote is publishing
    :publicheads: list of remote public phase heads (nodes)
    :draftheads: list of remote draft phase heads (nodes)
    :draftroots: list of remote draft phase root (nodes)
    """

    def __init__(self, repo, remotesubset, remoteroots):
        unfi = repo.unfiltered()
        self._allremoteroots = remoteroots

        self.publishing = remoteroots.get(b'publishing', False)

        ana = analyzeremotephases(repo, remotesubset, remoteroots)
        self.publicheads, self.draftroots = ana
        # Get the list of all "heads" revs draft on remote
        dheads = unfi.set(b'heads(%ln::%ln)', self.draftroots, remotesubset)
        self.draftheads = [c.node() for c in dheads]


def newheads(repo, heads, roots):
    """compute new head of a subset minus another

    * `heads`: define the first subset
    * `roots`: define the second we subtract from the first"""
    # prevent an import cycle
    # phases > dagop > patch > copies > scmutil > obsolete > obsutil > phases
    from . import dagop

    repo = repo.unfiltered()
    cl = repo.changelog
    rev = cl.index.get_rev
    if not roots:
        return heads
    if not heads or heads == [repo.nullid]:
        return []
    # The logic operated on revisions, convert arguments early for convenience
    new_heads = {rev(n) for n in heads if n != repo.nullid}
    roots = [rev(n) for n in roots]
    # compute the area we need to remove
    affected_zone = repo.revs(b"(%ld::%ld)", roots, new_heads)
    # heads in the area are no longer heads
    new_heads.difference_update(affected_zone)
    # revisions in the area have children outside of it,
    # They might be new heads
    candidates = repo.revs(
        b"parents(%ld + (%ld and merge())) and not null", roots, affected_zone
    )
    candidates -= affected_zone
    if new_heads or candidates:
        # remove candidate that are ancestors of other heads
        new_heads.update(candidates)
        prunestart = repo.revs(b"parents(%ld) and not null", new_heads)
        pruned = dagop.reachableroots(repo, candidates, prunestart)
        new_heads.difference_update(pruned)

    return pycompat.maplist(cl.node, sorted(new_heads))


def newcommitphase(ui):
    # type: (uimod.ui) -> int
    """helper to get the target phase of new commit

    Handle all possible values for the phases.new-commit options.

    """
    v = ui.config(b'phases', b'new-commit')
    try:
        return phasenumber2[v]
    except KeyError:
        raise error.ConfigError(
            _(b"phases.new-commit: not a valid phase name ('%s')") % v
        )


def hassecret(repo):
    # type: (localrepo.localrepository) -> bool
    """utility function that check if a repo have any secret changeset."""
    return bool(repo._phasecache.phaseroots[secret])


def preparehookargs(node, old, new):
    # type: (bytes, Optional[int], Optional[int]) -> Dict[bytes, bytes]
    if old is None:
        old = b''
    else:
        old = phasenames[old]
    return {b'node': node, b'oldphase': old, b'phase': phasenames[new]}
