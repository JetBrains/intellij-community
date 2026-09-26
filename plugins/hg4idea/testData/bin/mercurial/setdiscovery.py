# setdiscovery.py - improved discovery of common nodeset for mercurial
#
# Copyright 2010 Benoit Boissinot <bboissin@gmail.com>
# and Peter Arrenbrecht <peter@arrenbrecht.ch>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""
Algorithm works in the following way. You have two repository: local and
remote. They both contains a DAG of changelists.

The goal of the discovery protocol is to find one set of node *common*,
the set of nodes shared by local and remote.

One of the issue with the original protocol was latency, it could
potentially require lots of roundtrips to discover that the local repo was a
subset of remote (which is a very common case, you usually have few changes
compared to upstream, while upstream probably had lots of development).

The new protocol only requires one interface for the remote repo: `known()`,
which given a set of changelists tells you if they are present in the DAG.

The algorithm then works as follow:

 - We will be using three sets, `common`, `missing`, `unknown`. Originally
 all nodes are in `unknown`.
 - Take a sample from `unknown`, call `remote.known(sample)`
   - For each node that remote knows, move it and all its ancestors to `common`
   - For each node that remote doesn't know, move it and all its descendants
   to `missing`
 - Iterate until `unknown` is empty

There are a couple optimizations, first is instead of starting with a random
sample of missing, start by sending all heads, in the case where the local
repo is a subset, you computed the answer in one round trip.

Then you can do something similar to the bisecting strategy used when
finding faulty changesets. Instead of random samples, you can try picking
nodes that will maximize the number of nodes that will be
classified with it (since all ancestors or descendants will be marked as well).
"""


import collections
import random

from .i18n import _
from .node import nullrev
from . import (
    error,
    policy,
    util,
)


def _updatesample(revs, heads, sample, parentfn, quicksamplesize=0):
    """update an existing sample to match the expected size

    The sample is updated with revs exponentially distant from each head of the
    <revs> set. (H~1, H~2, H~4, H~8, etc).

    If a target size is specified, the sampling will stop once this size is
    reached. Otherwise sampling will happen until roots of the <revs> set are
    reached.

    :revs:  set of revs we want to discover (if None, assume the whole dag)
    :heads: set of DAG head revs
    :sample: a sample to update
    :parentfn: a callable to resolve parents for a revision
    :quicksamplesize: optional target size of the sample"""
    dist = {}
    visit = collections.deque(heads)
    seen = set()
    factor = 1
    while visit:
        curr = visit.popleft()
        if curr in seen:
            continue
        d = dist.setdefault(curr, 1)
        if d > factor:
            factor *= 2
        if d == factor:
            sample.add(curr)
            if quicksamplesize and (len(sample) >= quicksamplesize):
                return
        seen.add(curr)

        for p in parentfn(curr):
            if p != nullrev and (not revs or p in revs):
                dist.setdefault(p, d + 1)
                visit.append(p)


def _limitsample(sample, desiredlen, randomize=True):
    """return a random subset of sample of at most desiredlen item.

    If randomize is False, though, a deterministic subset is returned.
    This is meant for integration tests.
    """
    if len(sample) <= desiredlen:
        return sample
    sample = list(sample)
    if randomize:
        return set(random.sample(sample, desiredlen))
    sample.sort()
    return set(sample[:desiredlen])


class partialdiscovery:
    """an object representing ongoing discovery

    Feed with data from the remote repository, this object keep track of the
    current set of changeset in various states:

    - common:    revs also known remotely
    - undecided: revs we don't have information on yet
    - missing:   revs missing remotely
    (all tracked revisions are known locally)
    """

    def __init__(self, repo, targetheads, respectsize, randomize=True):
        self._repo = repo
        self._targetheads = targetheads
        self._common = repo.changelog.incrementalmissingrevs()
        self._undecided = None
        self.missing = set()
        self._childrenmap = None
        self._respectsize = respectsize
        self.randomize = randomize

    def addcommons(self, commons):
        """register nodes known as common"""
        self._common.addbases(commons)
        if self._undecided is not None:
            self._common.removeancestorsfrom(self._undecided)

    def addmissings(self, missings):
        """register some nodes as missing"""
        newmissing = self._repo.revs(b'%ld::%ld', missings, self.undecided)
        if newmissing:
            self.missing.update(newmissing)
            self.undecided.difference_update(newmissing)

    def addinfo(self, sample):
        """consume an iterable of (rev, known) tuples"""
        common = set()
        missing = set()
        for rev, known in sample:
            if known:
                common.add(rev)
            else:
                missing.add(rev)
        if common:
            self.addcommons(common)
        if missing:
            self.addmissings(missing)

    def hasinfo(self):
        """return True is we have any clue about the remote state"""
        return self._common.hasbases()

    def iscomplete(self):
        """True if all the necessary data have been gathered"""
        return self._undecided is not None and not self._undecided

    @property
    def undecided(self):
        if self._undecided is not None:
            return self._undecided
        self._undecided = set(self._common.missingancestors(self._targetheads))
        return self._undecided

    def stats(self):
        return {
            'undecided': len(self.undecided),
        }

    def commonheads(self):
        """the heads of the known common set"""
        # heads(common) == heads(common.bases) since common represents
        # common.bases and all its ancestors
        return self._common.basesheads()

    def _parentsgetter(self):
        getrev = self._repo.changelog.index.__getitem__

        def getparents(r):
            return getrev(r)[5:7]

        return getparents

    def _childrengetter(self):

        if self._childrenmap is not None:
            # During discovery, the `undecided` set keep shrinking.
            # Therefore, the map computed for an iteration N will be
            # valid for iteration N+1. Instead of computing the same
            # data over and over we cached it the first time.
            return self._childrenmap.__getitem__

        # _updatesample() essentially does interaction over revisions to look
        # up their children. This lookup is expensive and doing it in a loop is
        # quadratic. We precompute the children for all relevant revisions and
        # make the lookup in _updatesample() a simple dict lookup.
        self._childrenmap = children = {}

        parentrevs = self._parentsgetter()
        revs = self.undecided

        for rev in sorted(revs):
            # Always ensure revision has an entry so we don't need to worry
            # about missing keys.
            children[rev] = []
            for prev in parentrevs(rev):
                if prev == nullrev:
                    continue
                c = children.get(prev)
                if c is not None:
                    c.append(rev)
        return children.__getitem__

    def takequicksample(self, headrevs, size):
        """takes a quick sample of size <size>

        It is meant for initial sampling and focuses on querying heads and close
        ancestors of heads.

        :headrevs: set of head revisions in local DAG to consider
        :size: the maximum size of the sample"""
        revs = self.undecided
        if len(revs) <= size:
            return list(revs)
        sample = set(self._repo.revs(b'heads(%ld)', revs))

        if len(sample) >= size:
            return _limitsample(sample, size, randomize=self.randomize)

        _updatesample(
            None, headrevs, sample, self._parentsgetter(), quicksamplesize=size
        )
        return sample

    def takefullsample(self, headrevs, size):
        revs = self.undecided
        if len(revs) <= size:
            return list(revs)
        repo = self._repo
        sample = set(repo.revs(b'heads(%ld)', revs))
        parentrevs = self._parentsgetter()

        # update from heads
        revsheads = sample.copy()
        _updatesample(revs, revsheads, sample, parentrevs)

        # update from roots
        revsroots = set(repo.revs(b'roots(%ld)', revs))
        childrenrevs = self._childrengetter()
        _updatesample(revs, revsroots, sample, childrenrevs)
        assert sample

        if not self._respectsize:
            size = max(size, min(len(revsroots), len(revsheads)))

        sample = _limitsample(sample, size, randomize=self.randomize)
        if len(sample) < size:
            more = size - len(sample)
            takefrom = list(revs - sample)
            if self.randomize:
                sample.update(random.sample(takefrom, more))
            else:
                takefrom.sort()
                sample.update(takefrom[:more])
        return sample


pure_partialdiscovery = partialdiscovery

partialdiscovery = policy.importrust(
    'discovery', member='PartialDiscovery', default=partialdiscovery
)


def findcommonheads(
    ui,
    local,
    remote,
    abortwhenunrelated=True,
    ancestorsof=None,
    audit=None,
):
    """Return a tuple (common, anyincoming, remoteheads) used to identify
    missing nodes from or in remote.

    The audit argument is an optional dictionnary that a caller can pass. it
    will be updated with extra data about the discovery, this is useful for
    debug.
    """

    samplegrowth = float(ui.config(b'devel', b'discovery.grow-sample.rate'))

    if audit is not None:
        audit[b'total-queries'] = 0

    start = util.timer()

    roundtrips = 0
    cl = local.changelog
    clnode = cl.node
    clrev = cl.rev

    if ancestorsof is not None:
        ownheads = [clrev(n) for n in ancestorsof]
    else:
        ownheads = [rev for rev in cl.headrevs() if rev != nullrev]

    initial_head_exchange = ui.configbool(b'devel', b'discovery.exchange-heads')
    initialsamplesize = ui.configint(b'devel', b'discovery.sample-size.initial')
    fullsamplesize = ui.configint(b'devel', b'discovery.sample-size')
    # We also ask remote about all the local heads. That set can be arbitrarily
    # large, so we used to limit it size to `initialsamplesize`. We no longer
    # do as it proved counter productive. The skipped heads could lead to a
    # large "undecided" set, slower to be clarified than if we asked the
    # question for all heads right away.
    #
    # We are already fetching all server heads using the `heads` commands,
    # sending a equivalent number of heads the other way should not have a
    # significant impact.  In addition, it is very likely that we are going to
    # have to issue "known" request for an equivalent amount of revisions in
    # order to decide if theses heads are common or missing.
    #
    # find a detailled analysis below.
    #
    # Case A: local and server both has few heads
    #
    #     Ownheads is below initialsamplesize, limit would not have any effect.
    #
    # Case B: local has few heads and server has many
    #
    #     Ownheads is below initialsamplesize, limit would not have any effect.
    #
    # Case C: local and server both has many heads
    #
    #     We now transfert some more data, but not significantly more than is
    #     already transfered to carry the server heads.
    #
    # Case D: local has many heads, server has few
    #
    #   D.1 local heads are mostly known remotely
    #
    #     All the known head will have be part of a `known` request at some
    #     point for the discovery to finish. Sending them all earlier is
    #     actually helping.
    #
    #     (This case is fairly unlikely, it requires the numerous heads to all
    #     be merged server side in only a few heads)
    #
    #   D.2 local heads are mostly missing remotely
    #
    #     To determine that the heads are missing, we'll have to issue `known`
    #     request for them or one of their ancestors. This amount of `known`
    #     request will likely be in the same order of magnitude than the amount
    #     of local heads.
    #
    #     The only case where we can be more efficient using `known` request on
    #     ancestors are case were all the "missing" local heads are based on a
    #     few changeset, also "missing".  This means we would have a "complex"
    #     graph (with many heads) attached to, but very independant to a the
    #     "simple" graph on the server. This is a fairly usual case and have
    #     not been met in the wild so far.
    if initial_head_exchange:
        if remote.limitedarguments:
            sample = _limitsample(ownheads, initialsamplesize)
            # indices between sample and externalized version must match
            sample = list(sample)
        else:
            sample = ownheads

        ui.debug(b"query 1; heads\n")
        roundtrips += 1
        with remote.commandexecutor() as e:
            fheads = e.callcommand(b'heads', {})
            if audit is not None:
                audit[b'total-queries'] += len(sample)
            fknown = e.callcommand(
                b'known',
                {
                    b'nodes': [clnode(r) for r in sample],
                },
            )

        srvheadhashes, yesno = fheads.result(), fknown.result()

        if audit is not None:
            audit[b'total-roundtrips'] = 1

        if cl.tiprev() == nullrev:
            if srvheadhashes != [cl.nullid]:
                return [cl.nullid], True, srvheadhashes
            return [cl.nullid], False, []
    else:
        # we still need the remote head for the function return
        with remote.commandexecutor() as e:
            fheads = e.callcommand(b'heads', {})
        srvheadhashes = fheads.result()

    # start actual discovery (we note this before the next "if" for
    # compatibility reasons)
    ui.status(_(b"searching for changes\n"))

    knownsrvheads = []  # revnos of remote heads that are known locally
    for node in srvheadhashes:
        if node == cl.nullid:
            continue

        try:
            knownsrvheads.append(clrev(node))
        # Catches unknown and filtered nodes.
        except error.LookupError:
            continue

    if initial_head_exchange:
        # early exit if we know all the specified remote heads already
        if len(knownsrvheads) == len(srvheadhashes):
            ui.debug(b"all remote heads known locally\n")
            return srvheadhashes, False, srvheadhashes

        if len(sample) == len(ownheads) and all(yesno):
            ui.note(_(b"all local changesets known remotely\n"))
            ownheadhashes = [clnode(r) for r in ownheads]
            return ownheadhashes, True, srvheadhashes

    # full blown discovery

    # if the server has a limit to its arguments size, we can't grow the sample.
    configbool = local.ui.configbool
    grow_sample = configbool(b'devel', b'discovery.grow-sample')
    grow_sample = grow_sample and not remote.limitedarguments

    dynamic_sample = configbool(b'devel', b'discovery.grow-sample.dynamic')
    hard_limit_sample = not (dynamic_sample or remote.limitedarguments)

    randomize = ui.configbool(b'devel', b'discovery.randomize')
    if cl.index.rust_ext_compat:
        pd = partialdiscovery
    else:
        pd = pure_partialdiscovery
    disco = pd(local, ownheads, hard_limit_sample, randomize=randomize)
    if initial_head_exchange:
        # treat remote heads (and maybe own heads) as a first implicit sample
        # response
        disco.addcommons(knownsrvheads)
        disco.addinfo(zip(sample, yesno))

    full = not initial_head_exchange
    progress = ui.makeprogress(_(b'searching'), unit=_(b'queries'))
    while not disco.iscomplete():

        if full or disco.hasinfo():
            if full:
                ui.note(_(b"sampling from both directions\n"))
            else:
                ui.debug(b"taking initial sample\n")
            samplefunc = disco.takefullsample
            targetsize = fullsamplesize
            if grow_sample:
                fullsamplesize = int(fullsamplesize * samplegrowth)
        else:
            # use even cheaper initial sample
            ui.debug(b"taking quick initial sample\n")
            samplefunc = disco.takequicksample
            targetsize = initialsamplesize
        sample = samplefunc(ownheads, targetsize)

        roundtrips += 1
        progress.update(roundtrips)
        stats = disco.stats()
        ui.debug(
            b"query %i; still undecided: %i, sample size is: %i\n"
            % (roundtrips, stats['undecided'], len(sample))
        )

        # indices between sample and externalized version must match
        sample = list(sample)

        with remote.commandexecutor() as e:
            if audit is not None:
                audit[b'total-queries'] += len(sample)
            yesno = e.callcommand(
                b'known',
                {
                    b'nodes': [clnode(r) for r in sample],
                },
            ).result()

        full = True

        disco.addinfo(zip(sample, yesno))

    result = disco.commonheads()
    elapsed = util.timer() - start
    progress.complete()
    ui.debug(b"%d total queries in %.4fs\n" % (roundtrips, elapsed))
    msg = (
        b'found %d common and %d unknown server heads,'
        b' %d roundtrips in %.4fs\n'
    )
    missing = set(result) - set(knownsrvheads)
    ui.log(b'discovery', msg, len(result), len(missing), roundtrips, elapsed)

    if audit is not None:
        audit[b'total-roundtrips'] = roundtrips

    if not result and srvheadhashes != [cl.nullid]:
        if abortwhenunrelated:
            raise error.Abort(_(b"repository is unrelated"))
        else:
            ui.warn(_(b"warning: repository is unrelated\n"))
        return (
            {cl.nullid},
            True,
            srvheadhashes,
        )

    anyincoming = srvheadhashes != [cl.nullid]
    result = {clnode(r) for r in result}
    return result, anyincoming, srvheadhashes
