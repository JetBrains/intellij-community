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


import heapq
import struct
import typing
import weakref

from typing import (
    Any,
    Callable,
    Collection,
    Dict,
    Iterable,
    List,
    Optional,
    Set,
    Tuple,
)

from .i18n import _
from .node import (
    bin,
    hex,
    nullrev,
    short,
    wdirrev,
)
from . import (
    error,
    requirements,
    smartset,
    txnutil,
    util,
)

Phaseroots = Dict[int, Set[int]]
PhaseSets = Dict[int, Set[int]]

if typing.TYPE_CHECKING:
    from . import (
        localrepo,
        ui as uimod,
    )

    # keeps pyflakes happy
    assert [uimod]

    Phasedefaults = List[
        Callable[[localrepo.localrepository, Phaseroots], Phaseroots]
    ]


_fphasesentry = struct.Struct(b'>i20s')

# record phase index
public: int = 0
draft: int = 1
secret: int = 2
archived = 32  # non-continuous for compatibility
internal = 96  # non-continuous for compatibility
allphases = (public, draft, secret, archived, internal)
trackedphases = (draft, secret, archived, internal)
not_public_phases = trackedphases
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
relevant_mutable_phases = (draft, secret)  # could be obsolete or unstable
remotehiddenphases = (secret, archived, internal)
localhiddenphases = (internal, archived)

all_internal_phases = tuple(p for p in allphases if p & internal)
# We do not want any internal content to exit the repository, ever.
no_bundle_phases = all_internal_phases


def supportinternal(repo: "localrepo.localrepository") -> bool:
    """True if the internal phase can be used on a repository"""
    return requirements.INTERNAL_PHASE_REQUIREMENT in repo.requirements


def supportarchived(repo: "localrepo.localrepository") -> bool:
    """True if the archived phase can be used on a repository"""
    return requirements.ARCHIVED_PHASE_REQUIREMENT in repo.requirements


def _readroots(
    repo: "localrepo.localrepository",
    phasedefaults: Optional["Phasedefaults"] = None,
) -> Tuple[Phaseroots, bool]:
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
    to_rev = repo.changelog.index.get_rev
    unknown_msg = b'removing unknown node %s from %i-phase boundary\n'
    try:
        f, pending = txnutil.trypending(repo.root, repo.svfs, b'phaseroots')
        try:
            for line in f:
                str_phase, hex_node = line.split()
                phase = int(str_phase)
                node = bin(hex_node)
                rev = to_rev(node)
                if rev is None:
                    repo.ui.debug(unknown_msg % (short(hex_node), phase))
                    dirty = True
                else:
                    roots[phase].add(rev)
        finally:
            f.close()
    except FileNotFoundError:
        if phasedefaults:
            for f in phasedefaults:
                roots = f(repo, roots)
        dirty = True
    return roots, dirty


def binaryencode(phasemapping: Dict[int, List[bytes]]) -> bytes:
    """encode a 'phase -> nodes' mapping into a binary stream

    The revision lists are encoded as (phase, root) pairs.
    """
    binarydata = []
    for phase, nodes in phasemapping.items():
        for head in nodes:
            binarydata.append(_fphasesentry.pack(phase, head))
    return b''.join(binarydata)


def binarydecode(stream) -> Dict[int, List[bytes]]:
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
        data[idx - 1] = (range(r1[0], r2[-1] + 1), t)
        data.pop(idx)
    elif merge_before:
        data[idx - 1] = (range(r1[0], rev + 1), t)
    elif merge_after:
        data[idx] = (range(rev, r2[-1] + 1), t)
    else:
        data.insert(idx, (range(rev, rev + 1), t))


def _sortedrange_split(data, idx, rev, t):
    r1, t1 = data[idx]
    if t == t1:
        return
    t = (t1[0], t[1])
    if len(r1) == 1:
        data.pop(idx)
        _sortedrange_insert(data, idx, rev, t)
    elif r1[0] == rev:
        data[idx] = (range(rev + 1, r1[-1] + 1), t1)
        _sortedrange_insert(data, idx, rev, t)
    elif r1[-1] == rev:
        data[idx] = (range(r1[0], rev), t1)
        _sortedrange_insert(data, idx + 1, rev, t)
    else:
        data[idx : idx + 1] = [
            (range(r1[0], rev), t1),
            (range(rev, rev + 1), t),
            (range(rev + 1, r1[-1] + 1), t1),
        ]


def _trackphasechange(data, rev, old, new):
    """add a phase move to the <data> list of ranges

    If data is None, nothing happens.
    """
    if data is None:
        return

    # If data is empty, create a one-revision range and done
    if not data:
        data.insert(0, (range(rev, rev + 1), (old, new)))
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
        data.append((range(rev, rev + 1), t))
        return

    r1, t1 = data[low]
    if r1[0] > rev:
        data.insert(low, (range(rev, rev + 1), t))
    else:
        data.insert(low + 1, (range(rev, rev + 1), t))


# consider incrementaly updating the phase set the update set is not bigger
# than this size
#
# Be warned, this number is picked arbitrarily, without any benchmark. It
# should blindly pickup "small update"
INCREMENTAL_PHASE_SETS_UPDATE_MAX_UPDATE = 100


class phasecache:
    def __init__(
        self,
        repo: "localrepo.localrepository",
        phasedefaults: Optional["Phasedefaults"],
        _load: bool = True,
    ):
        if _load:
            # Cheap trick to allow shallow-copy without copy module
            loaded = _readroots(repo, phasedefaults)
            self._phaseroots: Phaseroots = loaded[0]
            self.dirty: bool = loaded[1]
            self._loadedrevslen = 0
            self._phasesets: PhaseSets = None

    def hasnonpublicphases(self, repo: "localrepo.localrepository") -> bool:
        """detect if there are revisions with non-public phase"""
        # XXX deprecate the unused repo argument
        return any(
            revs for phase, revs in self._phaseroots.items() if phase != public
        )

    def nonpublicphaseroots(
        self, repo: "localrepo.localrepository"
    ) -> Set[int]:
        """returns the roots of all non-public phases

        The roots are not minimized, so if the secret revisions are
        descendants of draft revisions, their roots will still be present.
        """
        repo = repo.unfiltered()
        self._ensure_phase_sets(repo)
        return set().union(
            *[
                revs
                for phase, revs in self._phaseroots.items()
                if phase != public
            ]
        )

    def get_raw_set(
        self,
        repo: "localrepo.localrepository",
        phase: int,
    ) -> Set[int]:
        """return the set of revision in that phase

        The returned set is not filtered and might contains revision filtered
        for the passed repoview.

        The returned set might be the internal one and MUST NOT be mutated to
        avoid side effect.
        """
        if phase == public:
            raise error.ProgrammingError("cannot get_set for public phase")
        self._ensure_phase_sets(repo.unfiltered())
        revs = self._phasesets.get(phase)
        if revs is None:
            return set()
        return revs

    def getrevset(
        self,
        repo: "localrepo.localrepository",
        phases: Iterable[int],
        subset: Optional[Any] = None,
    ) -> Any:
        # TODO: finish typing this
        """return a smartset for the given phases"""
        self._ensure_phase_sets(repo.unfiltered())
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
        ph._phaseroots = self._phaseroots.copy()
        ph.dirty = self.dirty
        ph._loadedrevslen = self._loadedrevslen
        if self._phasesets is None:
            ph._phasesets = None
        else:
            ph._phasesets = self._phasesets.copy()
        return ph

    def replace(self, phcache):
        """replace all values in 'self' with content of phcache"""
        for a in (
            '_phaseroots',
            'dirty',
            '_loadedrevslen',
            '_phasesets',
        ):
            setattr(self, a, getattr(phcache, a))

    def _getphaserevsnative(self, repo):
        repo = repo.unfiltered()
        return repo.changelog.computephases(self._phaseroots)

    def _computephaserevspure(self, repo):
        repo = repo.unfiltered()
        cl = repo.changelog
        self._phasesets = {phase: set() for phase in allphases}
        lowerroots = set()
        for phase in reversed(trackedphases):
            roots = self._phaseroots[phase]
            if roots:
                ps = set(cl.descendants(roots))
                for root in roots:
                    ps.add(root)
                ps.difference_update(lowerroots)
                lowerroots.update(ps)
                self._phasesets[phase] = ps
        self._loadedrevslen = len(cl)

    def _ensure_phase_sets(self, repo: "localrepo.localrepository") -> None:
        """ensure phase information is loaded in the object"""
        assert repo.filtername is None
        update = -1
        cl = repo.changelog
        cl_size = len(cl)
        if self._phasesets is None:
            update = 0
        else:
            if cl_size > self._loadedrevslen:
                # check if an incremental update is worth it.
                # note we need a tradeoff here because the whole logic is not
                # stored and implemented in native code nd datastructure.
                # Otherwise the incremental update woul always be a win.
                missing = cl_size - self._loadedrevslen
                if missing <= INCREMENTAL_PHASE_SETS_UPDATE_MAX_UPDATE:
                    update = self._loadedrevslen
                else:
                    update = 0

        if update == 0:
            try:
                res = self._getphaserevsnative(repo)
                self._loadedrevslen, self._phasesets = res
            except AttributeError:
                self._computephaserevspure(repo)
            assert self._loadedrevslen == len(repo.changelog)
        elif update > 0:
            # good candidate for native code
            assert update == self._loadedrevslen
            if self.hasnonpublicphases(repo):
                start = self._loadedrevslen
                get_phase = self.phase
                rev_phases = [0] * missing
                parents = cl.parentrevs
                sets = {phase: set() for phase in self._phasesets}
                for phase, roots in self._phaseroots.items():
                    # XXX should really store the max somewhere
                    for r in roots:
                        if r >= start:
                            rev_phases[r - start] = phase
                for rev in range(start, cl_size):
                    phase = rev_phases[rev - start]
                    p1, p2 = parents(rev)
                    if p1 == nullrev:
                        p1_phase = public
                    elif p1 >= start:
                        p1_phase = rev_phases[p1 - start]
                    else:
                        p1_phase = max(phase, get_phase(repo, p1))
                    if p2 == nullrev:
                        p2_phase = public
                    elif p2 >= start:
                        p2_phase = rev_phases[p2 - start]
                    else:
                        p2_phase = max(phase, get_phase(repo, p2))
                    phase = max(phase, p1_phase, p2_phase)
                    if phase > public:
                        rev_phases[rev - start] = phase
                        sets[phase].add(rev)

                # Be careful to preserve shallow-copied values: do not update
                # phaseroots values, replace them.
                for phase, extra in sets.items():
                    if extra:
                        self._phasesets[phase] = self._phasesets[phase] | extra
            self._loadedrevslen = cl_size

    def invalidate(self):
        self._loadedrevslen = 0
        self._phasesets = None

    def phase(self, repo: "localrepo.localrepository", rev: int) -> int:
        # We need a repo argument here to be able to build _phasesets
        # if necessary. The repository instance is not stored in
        # phasecache to avoid reference cycles. The changelog instance
        # is not stored because it is a filecache() property and can
        # be replaced without us being notified.
        if rev == nullrev:
            return public
        if rev < nullrev:
            raise ValueError(_(b'cannot lookup negative revision'))
        # double check self._loadedrevslen to avoid an extra method call as
        # python is slow for that.
        if rev >= self._loadedrevslen:
            self._ensure_phase_sets(repo.unfiltered())
        for phase in trackedphases:
            if rev in self._phasesets[phase]:
                return phase
        return public

    def write(self, repo):
        if not self.dirty:
            return
        f = repo.svfs(b'phaseroots', b'w', atomictemp=True, checkambig=True)
        try:
            self._write(repo.unfiltered(), f)
        finally:
            f.close()

    def _write(self, repo, fp):
        assert repo.filtername is None
        to_node = repo.changelog.node
        for phase, roots in self._phaseroots.items():
            for r in sorted(roots):
                h = to_node(r)
                fp.write(b'%i %s\n' % (phase, hex(h)))
        self.dirty = False

    def _updateroots(self, repo, phase, newroots, tr, invalidate=True):
        self._phaseroots[phase] = newroots
        self.dirty = True
        if invalidate:
            self.invalidate()

        assert repo.filtername is None
        wrepo = weakref.ref(repo)

        def tr_write(fp):
            repo = wrepo()
            assert repo is not None
            self._write(repo, fp)

        tr.addfilegenerator(b'phase', (b'phaseroots',), tr_write)
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
        self, repo, tr, targetphase, nodes=None, revs=None, dryrun=None
    ):
        """Set all 'nodes' to phase 'targetphase'

        Nodes with a phase lower than 'targetphase' are not affected.

        If dryrun is True, no actions will be performed

        Returns a set of revs whose phase is changed or should be changed
        """
        if targetphase == public and not self.hasnonpublicphases(repo):
            return set()
        repo = repo.unfiltered()
        cl = repo.changelog
        torev = cl.index.rev
        # Be careful to preserve shallow-copied values: do not update
        # phaseroots values, replace them.
        new_revs = set()
        if revs is not None:
            new_revs.update(revs)
        if nodes is not None:
            new_revs.update(torev(node) for node in nodes)
        if not new_revs:  # bail out early to avoid the loadphaserevs call
            return (
                set()
            )  # note: why do people call advanceboundary with nothing?

        if tr is None:
            phasetracking = None
        else:
            phasetracking = tr.changes.get(b'phases')

        affectable_phases = sorted(
            p for p in allphases if p > targetphase and self._phaseroots[p]
        )
        # filter revision already in the right phases
        candidates = new_revs
        new_revs = set()
        self._ensure_phase_sets(repo)
        for phase in affectable_phases:
            found = candidates & self._phasesets[phase]
            new_revs |= found
            candidates -= found
            if not candidates:
                break
        if not new_revs:
            return set()

        # search for affected high phase changesets and roots
        seen = set(new_revs)
        push = heapq.heappush
        pop = heapq.heappop
        parents = cl.parentrevs
        get_phase = self.phase
        changed = {}  # set of revisions to be changed
        # set of root deleted by this path
        delroots = set()
        new_roots = {p: set() for p in affectable_phases}
        new_target_roots = set()
        # revision to walk down
        revs = [-r for r in new_revs]
        heapq.heapify(revs)
        while revs:
            current = -pop(revs)
            current_phase = get_phase(repo, current)
            changed[current] = current_phase
            p1, p2 = parents(current)
            if p1 == nullrev:
                p1_phase = public
            else:
                p1_phase = get_phase(repo, p1)
            if p2 == nullrev:
                p2_phase = public
            else:
                p2_phase = get_phase(repo, p2)
            # do we have a root ?
            if current_phase != p1_phase and current_phase != p2_phase:
                # do not record phase, because we could have "duplicated"
                # roots, were one root is shadowed by the very same roots of an
                # higher phases
                delroots.add(current)
            # schedule a walk down if needed
            if p1_phase > targetphase and p1 not in seen:
                seen.add(p1)
                push(revs, -p1)
            if p2_phase > targetphase and p2 not in seen:
                seen.add(p2)
                push(revs, -p2)
            if p1_phase < targetphase and p2_phase < targetphase:
                new_target_roots.add(current)

        # the last iteration was done with the smallest value
        min_current = current
        # do we have unwalked children that might be new roots
        if (min_current + len(changed)) < len(cl):
            for r in range(min_current, len(cl)):
                if r in changed:
                    continue
                phase = get_phase(repo, r)
                if phase <= targetphase:
                    continue
                p1, p2 = parents(r)
                if not (p1 in changed or p2 in changed):
                    continue  # not affected
                if p1 != nullrev and p1 not in changed:
                    p1_phase = get_phase(repo, p1)
                    if p1_phase == phase:
                        continue  # not a root
                if p2 != nullrev and p2 not in changed:
                    p2_phase = get_phase(repo, p2)
                    if p2_phase == phase:
                        continue  # not a root
                new_roots[phase].add(r)

        # apply the changes
        if not dryrun:
            for r, p in changed.items():
                _trackphasechange(phasetracking, r, p, targetphase)
            if targetphase > public:
                self._phasesets[targetphase].update(changed)
            for phase in affectable_phases:
                roots = self._phaseroots[phase]
                removed = roots & delroots
                if removed or new_roots[phase]:
                    self._phasesets[phase].difference_update(changed)
                    # Be careful to preserve shallow-copied values: do not
                    # update phaseroots values, replace them.
                    final_roots = roots - delroots | new_roots[phase]
                    self._updateroots(
                        repo, phase, final_roots, tr, invalidate=False
                    )
            if new_target_roots:
                # Thanks for previous filtering, we can't replace existing
                # roots
                new_target_roots |= self._phaseroots[targetphase]
                self._updateroots(
                    repo, targetphase, new_target_roots, tr, invalidate=False
                )
            repo.invalidatevolatilesets()
        return changed

    def retractboundary(self, repo, tr, targetphase, nodes):
        if tr is None:
            phasetracking = None
        else:
            phasetracking = tr.changes.get(b'phases')
        repo = repo.unfiltered()
        retracted = self._retractboundary(repo, tr, targetphase, nodes)
        if retracted and phasetracking is not None:
            for r, old_phase in sorted(retracted.items()):
                _trackphasechange(phasetracking, r, old_phase, targetphase)
        repo.invalidatevolatilesets()

    def _retractboundary(self, repo, tr, targetphase, nodes=None, revs=None):
        if targetphase == public:
            return {}
        if (
            targetphase == internal
            and not supportinternal(repo)
            or targetphase == archived
            and not supportarchived(repo)
        ):
            name = phasenames[targetphase]
            msg = b'this repository does not support the %s phase' % name
            raise error.ProgrammingError(msg)
        assert repo.filtername is None
        cl = repo.changelog
        torev = cl.index.rev
        new_revs = set()
        if revs is not None:
            new_revs.update(revs)
        if nodes is not None:
            new_revs.update(torev(node) for node in nodes)
        if not new_revs:  # bail out early to avoid the loadphaserevs call
            return {}  # note: why do people call retractboundary with nothing ?

        if nullrev in new_revs:
            raise error.Abort(_(b'cannot change null revision phase'))

        # Filter revision that are already in the right phase
        self._ensure_phase_sets(repo)
        for phase, revs in self._phasesets.items():
            if phase >= targetphase:
                new_revs -= revs
        if not new_revs:  # all revisions already in the right phases
            return {}

        # Compute change in phase roots by walking the graph
        #
        # note: If we had a cheap parent → children mapping we could do
        # something even cheaper/more-bounded
        #
        # The idea would be to walk from item in new_revs stopping at
        # descendant with phases >= target_phase.
        #
        # 1) This detect new_revs that are not new_roots (either already >=
        #    target_phase or reachable though another new_revs
        # 2) This detect replaced current_roots as we reach them
        # 3) This can avoid walking to the tip if we retract over a small
        #    branch.
        #
        # So instead, we do a variation of this, we walk from the smaller new
        # revision to the tip to avoid missing any potential children.
        #
        # The following code would be a good candidate for native code… if only
        # we could knew the phase of a changeset efficiently in native code.
        parents = cl.parentrevs
        phase = self.phase
        new_roots = set()  # roots added by this phases
        changed_revs = {}  # revision affected by this call
        replaced_roots = set()  # older roots replaced by this call
        currentroots = self._phaseroots[targetphase]
        start = min(new_revs)
        end = len(cl)
        rev_phases = [None] * (end - start)

        this_phase_set = self._phasesets[targetphase]
        for r in range(start, end):

            # gather information about the current_rev
            r_phase = phase(repo, r)
            p_phase = None  # phase inherited from parents
            p1, p2 = parents(r)
            if p1 >= start:
                p1_phase = rev_phases[p1 - start]
                if p1_phase is not None:
                    p_phase = p1_phase
            if p2 >= start:
                p2_phase = rev_phases[p2 - start]
                if p2_phase is not None:
                    if p_phase is not None:
                        p_phase = max(p_phase, p2_phase)
                    else:
                        p_phase = p2_phase

            # assess the situation
            if r in new_revs and r_phase < targetphase:
                if p_phase is None or p_phase < targetphase:
                    new_roots.add(r)
                rev_phases[r - start] = targetphase
                changed_revs[r] = r_phase
                this_phase_set.add(r)
            elif p_phase is None:
                rev_phases[r - start] = r_phase
            else:
                if p_phase > r_phase:
                    rev_phases[r - start] = p_phase
                else:
                    rev_phases[r - start] = r_phase
                if p_phase == targetphase:
                    if p_phase > r_phase:
                        changed_revs[r] = r_phase
                        this_phase_set.add(r)
                    elif r in currentroots:
                        replaced_roots.add(r)
            sets = self._phasesets
            if targetphase > draft:
                for r, old in changed_revs.items():
                    if old > public:
                        sets[old].discard(r)

        if new_roots:
            assert changed_revs

            final_roots = new_roots | currentroots - replaced_roots
            self._updateroots(
                repo,
                targetphase,
                final_roots,
                tr,
                invalidate=False,
            )
            if targetphase > 1:
                retracted = set(changed_revs)
                for lower_phase in range(1, targetphase):
                    lower_roots = self._phaseroots.get(lower_phase)
                    if lower_roots is None:
                        continue
                    if lower_roots & retracted:
                        simpler_roots = lower_roots - retracted
                        self._updateroots(
                            repo,
                            lower_phase,
                            simpler_roots,
                            tr,
                            invalidate=False,
                        )
            return changed_revs
        else:
            assert not changed_revs
            assert not replaced_roots
            return {}

    def register_strip(
        self,
        repo,
        tr,
        strip_rev: int,
    ):
        """announce a strip to the phase cache

        Any roots higher than the stripped revision should be dropped.
        """
        for targetphase, roots in list(self._phaseroots.items()):
            filtered = {r for r in roots if r >= strip_rev}
            if filtered:
                self._updateroots(repo, targetphase, roots - filtered, tr)
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


def listphases(repo: "localrepo.localrepository") -> Dict[bytes, bytes]:
    """List phases root for serialization over pushkey"""
    # Use ordered dictionary so behavior is deterministic.
    keys = util.sortdict()
    value = b'%i' % draft
    cl = repo.unfiltered().changelog
    to_node = cl.node
    for root in repo._phasecache._phaseroots[draft]:
        if repo._phasecache.phase(repo, root) <= draft:
            keys[hex(to_node(root))] = value

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


def pushphase(
    repo: "localrepo.localrepository",
    nhex: bytes,
    oldphasestr: bytes,
    newphasestr: bytes,
) -> bool:
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
    for phase in allphases:
        revset = b"heads(%%ln & _phase(%d))" % phase
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


def analyze_remote_phases(
    repo,
    subset: Collection[int],
    roots: Dict[bytes, bytes],
) -> Tuple[Collection[int], Collection[int]]:
    """Compute phases heads and root in a subset of node from root dict

    * subset is heads of the subset
    * roots is {<nodeid> => phase} mapping. key and value are string.

    Accept unknown element input
    """
    repo = repo.unfiltered()
    # build list from dictionary
    draft_roots = []
    to_rev = repo.changelog.index.get_rev
    for nhex, phase in roots.items():
        if nhex == b'publishing':  # ignore data related to publish option
            continue
        node = bin(nhex)
        phase = int(phase)
        if phase == public:
            if node != repo.nullid:
                msg = _(b'ignoring inconsistent public root from remote: %s\n')
                repo.ui.warn(msg % nhex)
        elif phase == draft:
            rev = to_rev(node)
            if rev is not None:  # to filter unknown nodes
                draft_roots.append(rev)
        else:
            msg = _(b'ignoring unexpected root from remote: %i %s\n')
            repo.ui.warn(msg % (phase, nhex))
    # compute heads
    public_heads = new_heads(repo, subset, draft_roots)
    return public_heads, draft_roots


class RemotePhasesSummary:
    """summarize phase information on the remote side

    :publishing: True is the remote is publishing
    :public_heads: list of remote public phase heads (revs)
    :draft_heads: list of remote draft phase heads (revs)
    :draft_roots: list of remote draft phase root (revs)
    """

    def __init__(
        self,
        repo,
        remote_subset: Collection[int],
        remote_roots: Dict[bytes, bytes],
    ):
        unfi = repo.unfiltered()
        self._allremoteroots: Dict[bytes, bytes] = remote_roots

        self.publishing: bool = bool(remote_roots.get(b'publishing', False))

        heads, roots = analyze_remote_phases(repo, remote_subset, remote_roots)
        self.public_heads: Collection[int] = heads
        self.draft_roots: Collection[int] = roots
        # Get the list of all "heads" revs draft on remote
        dheads = unfi.revs(b'heads(%ld::%ld)', roots, remote_subset)
        self.draft_heads: Collection[int] = dheads


def new_heads(
    repo,
    heads: Collection[int],
    roots: Collection[int],
) -> Collection[int]:
    """compute new head of a subset minus another

    * `heads`: define the first subset
    * `roots`: define the second we subtract from the first"""
    # prevent an import cycle
    # phases > dagop > patch > copies > scmutil > obsolete > obsutil > phases
    from . import dagop

    if not roots:
        return heads
    if not heads or heads == [nullrev]:
        return []
    # The logic operated on revisions, convert arguments early for convenience
    # PERF-XXX: maybe heads could directly comes as a set without impacting
    # other user of that value
    new_heads = set(heads)
    new_heads.discard(nullrev)
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

    # PERF-XXX: do we actually need a sorted list here? Could we simply return
    # a set?
    return sorted(new_heads)


def newcommitphase(ui: "uimod.ui") -> int:
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


def hassecret(repo: "localrepo.localrepository") -> bool:
    """utility function that check if a repo have any secret changeset."""
    return bool(repo._phasecache._phaseroots[secret])


def preparehookargs(
    node: bytes,
    old: Optional[int],
    new: Optional[int],
) -> Dict[bytes, bytes]:
    if old is None:
        old = b''
    else:
        old = phasenames[old]
    return {b'node': node, b'oldphase': old, b'phase': phasenames[new]}
