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

import errno
from node import nullid, nullrev, bin, hex, short
from i18n import _
import util, error

allphases = public, draft, secret = range(3)
trackedphases = allphases[1:]
phasenames = ['public', 'draft', 'secret']

def _readroots(repo, phasedefaults=None):
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
    roots = [set() for i in allphases]
    try:
        f = repo.sopener('phaseroots')
        try:
            for line in f:
                phase, nh = line.split()
                roots[int(phase)].add(bin(nh))
        finally:
            f.close()
    except IOError, inst:
        if inst.errno != errno.ENOENT:
            raise
        if phasedefaults:
            for f in phasedefaults:
                roots = f(repo, roots)
        dirty = True
    return roots, dirty

class phasecache(object):
    def __init__(self, repo, phasedefaults, _load=True):
        if _load:
            # Cheap trick to allow shallow-copy without copy module
            self.phaseroots, self.dirty = _readroots(repo, phasedefaults)
            self._phaserevs = None
            self.filterunknown(repo)
            self.opener = repo.sopener

    def copy(self):
        # Shallow copy meant to ensure isolation in
        # advance/retractboundary(), nothing more.
        ph = phasecache(None, None, _load=False)
        ph.phaseroots = self.phaseroots[:]
        ph.dirty = self.dirty
        ph.opener = self.opener
        ph._phaserevs = self._phaserevs
        return ph

    def replace(self, phcache):
        for a in 'phaseroots dirty opener _phaserevs'.split():
            setattr(self, a, getattr(phcache, a))

    def getphaserevs(self, repo, rebuild=False):
        if rebuild or self._phaserevs is None:
            repo = repo.unfiltered()
            revs = [public] * len(repo.changelog)
            for phase in trackedphases:
                roots = map(repo.changelog.rev, self.phaseroots[phase])
                if roots:
                    for rev in roots:
                        revs[rev] = phase
                    for rev in repo.changelog.descendants(roots):
                        revs[rev] = phase
            self._phaserevs = revs
        return self._phaserevs

    def phase(self, repo, rev):
        # We need a repo argument here to be able to build _phaserevs
        # if necessary. The repository instance is not stored in
        # phasecache to avoid reference cycles. The changelog instance
        # is not stored because it is a filecache() property and can
        # be replaced without us being notified.
        if rev == nullrev:
            return public
        if self._phaserevs is None or rev >= len(self._phaserevs):
            self._phaserevs = self.getphaserevs(repo, rebuild=True)
        return self._phaserevs[rev]

    def write(self):
        if not self.dirty:
            return
        f = self.opener('phaseroots', 'w', atomictemp=True)
        try:
            for phase, roots in enumerate(self.phaseroots):
                for h in roots:
                    f.write('%i %s\n' % (phase, hex(h)))
        finally:
            f.close()
        self.dirty = False

    def _updateroots(self, phase, newroots):
        self.phaseroots[phase] = newroots
        self._phaserevs = None
        self.dirty = True

    def advanceboundary(self, repo, targetphase, nodes):
        # Be careful to preserve shallow-copied values: do not update
        # phaseroots values, replace them.

        repo = repo.unfiltered()
        delroots = [] # set of root deleted by this path
        for phase in xrange(targetphase + 1, len(allphases)):
            # filter nodes that are not in a compatible phase already
            nodes = [n for n in nodes
                     if self.phase(repo, repo[n].rev()) >= phase]
            if not nodes:
                break # no roots to move anymore
            olds = self.phaseroots[phase]
            roots = set(ctx.node() for ctx in repo.set(
                    'roots((%ln::) - (%ln::%ln))', olds, olds, nodes))
            if olds != roots:
                self._updateroots(phase, roots)
                # some roots may need to be declared for lower phases
                delroots.extend(olds - roots)
            # declare deleted root in the target phase
            if targetphase != 0:
                self.retractboundary(repo, targetphase, delroots)
        repo.invalidatevolatilesets()

    def retractboundary(self, repo, targetphase, nodes):
        # Be careful to preserve shallow-copied values: do not update
        # phaseroots values, replace them.

        repo = repo.unfiltered()
        currentroots = self.phaseroots[targetphase]
        newroots = [n for n in nodes
                    if self.phase(repo, repo[n].rev()) < targetphase]
        if newroots:
            if nullid in newroots:
                raise util.Abort(_('cannot change null revision phase'))
            currentroots = currentroots.copy()
            currentroots.update(newroots)
            ctxs = repo.set('roots(%ln::)', currentroots)
            currentroots.intersection_update(ctx.node() for ctx in ctxs)
            self._updateroots(targetphase, currentroots)
        repo.invalidatevolatilesets()

    def filterunknown(self, repo):
        """remove unknown nodes from the phase boundary

        Nothing is lost as unknown nodes only hold data for their descendants.
        """
        filtered = False
        nodemap = repo.changelog.nodemap # to filter unknown nodes
        for phase, nodes in enumerate(self.phaseroots):
            missing = [node for node in nodes if node not in nodemap]
            if missing:
                for mnode in missing:
                    repo.ui.debug(
                        'removing unknown node %s from %i-phase boundary\n'
                        % (short(mnode), phase))
                nodes.symmetric_difference_update(missing)
                filtered = True
        if filtered:
            self.dirty = True
        # filterunknown is called by repo.destroyed, we may have no changes in
        # root but phaserevs contents is certainly invalide (or at least we
        # have not proper way to check that. related to issue 3858.
        #
        # The other caller is __init__ that have no _phaserevs initialized
        # anyway. If this change we should consider adding a dedicated
        # "destroyed" function to phasecache or a proper cache key mechanisme
        # (see branchmap one)
        self._phaserevs = None

def advanceboundary(repo, targetphase, nodes):
    """Add nodes to a phase changing other nodes phases if necessary.

    This function move boundary *forward* this means that all nodes
    are set in the target phase or kept in a *lower* phase.

    Simplify boundary to contains phase roots only."""
    phcache = repo._phasecache.copy()
    phcache.advanceboundary(repo, targetphase, nodes)
    repo._phasecache.replace(phcache)

def retractboundary(repo, targetphase, nodes):
    """Set nodes back to a phase changing other nodes phases if
    necessary.

    This function move boundary *backward* this means that all nodes
    are set in the target phase or kept in a *higher* phase.

    Simplify boundary to contains phase roots only."""
    phcache = repo._phasecache.copy()
    phcache.retractboundary(repo, targetphase, nodes)
    repo._phasecache.replace(phcache)

def listphases(repo):
    """List phases root for serialization over pushkey"""
    keys = {}
    value = '%i' % draft
    for root in repo._phasecache.phaseroots[draft]:
        keys[hex(root)] = value

    if repo.ui.configbool('phases', 'publish', True):
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
        keys['publishing'] = 'True'
    return keys

def pushphase(repo, nhex, oldphasestr, newphasestr):
    """List phases root for serialization over pushkey"""
    repo = repo.unfiltered()
    lock = repo.lock()
    try:
        currentphase = repo[nhex].phase()
        newphase = abs(int(newphasestr)) # let's avoid negative index surprise
        oldphase = abs(int(oldphasestr)) # let's avoid negative index surprise
        if currentphase == oldphase and newphase < oldphase:
            advanceboundary(repo, newphase, [bin(nhex)])
            return 1
        elif currentphase == newphase:
            # raced, but got correct result
            return 1
        else:
            return 0
    finally:
        lock.release()

def analyzeremotephases(repo, subset, roots):
    """Compute phases heads and root in a subset of node from root dict

    * subset is heads of the subset
    * roots is {<nodeid> => phase} mapping. key and value are string.

    Accept unknown element input
    """
    repo = repo.unfiltered()
    # build list from dictionary
    draftroots = []
    nodemap = repo.changelog.nodemap # to filter unknown nodes
    for nhex, phase in roots.iteritems():
        if nhex == 'publishing': # ignore data related to publish option
            continue
        node = bin(nhex)
        phase = int(phase)
        if phase == 0:
            if node != nullid:
                repo.ui.warn(_('ignoring inconsistent public root'
                               ' from remote: %s\n') % nhex)
        elif phase == 1:
            if node in nodemap:
                draftroots.append(node)
        else:
            repo.ui.warn(_('ignoring unexpected root from remote: %i %s\n')
                         % (phase, nhex))
    # compute heads
    publicheads = newheads(repo, subset, draftroots)
    return publicheads, draftroots

def newheads(repo, heads, roots):
    """compute new head of a subset minus another

    * `heads`: define the first subset
    * `roots`: define the second we subtract from the first"""
    repo = repo.unfiltered()
    revset = repo.set('heads((%ln + parents(%ln)) - (%ln::%ln))',
                      heads, roots, roots, heads)
    return [c.node() for c in revset]


def newcommitphase(ui):
    """helper to get the target phase of new commit

    Handle all possible values for the phases.new-commit options.

    """
    v = ui.config('phases', 'new-commit', draft)
    try:
        return phasenames.index(v)
    except ValueError:
        try:
            return int(v)
        except ValueError:
            msg = _("phases.new-commit: not a valid phase name ('%s')")
            raise error.ConfigError(msg % v)

def hassecret(repo):
    """utility function that check if a repo have any secret changeset."""
    return bool(repo._phasecache.phaseroots[2])
