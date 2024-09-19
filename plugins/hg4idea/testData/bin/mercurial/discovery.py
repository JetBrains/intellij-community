# discovery.py - protocol changeset discovery functions
#
# Copyright 2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import functools

from .i18n import _
from .node import (
    hex,
    short,
)

from . import (
    bookmarks,
    branchmap,
    error,
    phases,
    pycompat,
    scmutil,
    setdiscovery,
    treediscovery,
    util,
)


def findcommonincoming(repo, remote, heads=None, force=False, ancestorsof=None):
    """Return a tuple (common, anyincoming, heads) used to identify the common
    subset of nodes between repo and remote.

    "common" is a list of (at least) the heads of the common subset.
    "anyincoming" is testable as a boolean indicating if any nodes are missing
      locally. If remote does not support getbundle, this actually is a list of
      roots of the nodes that would be incoming, to be supplied to
      changegroupsubset. No code except for pull should be relying on this fact
      any longer.
    "heads" is either the supplied heads, or else the remote's heads.
    "ancestorsof" if not None, restrict the discovery to a subset defined by
      these nodes. Changeset outside of this set won't be considered (but may
      still appear in "common").

    If you pass heads and they are all known locally, the response lists just
    these heads in "common" and in "heads".

    Please use findcommonoutgoing to compute the set of outgoing nodes to give
    extensions a good hook into outgoing.
    """

    if not remote.capable(b'getbundle'):
        return treediscovery.findcommonincoming(repo, remote, heads, force)

    if heads:
        knownnode = repo.changelog.hasnode  # no nodemap until it is filtered
        if all(knownnode(h) for h in heads):
            return (heads, False, heads)

    res = setdiscovery.findcommonheads(
        repo.ui,
        repo,
        remote,
        abortwhenunrelated=not force,
        ancestorsof=ancestorsof,
    )
    common, anyinc, srvheads = res
    if heads and not anyinc:
        # server could be lying on the advertised heads
        has_node = repo.changelog.hasnode
        anyinc = any(not has_node(n) for n in heads)
    return (list(common), anyinc, heads or list(srvheads))


class outgoing(object):
    """Represents the result of a findcommonoutgoing() call.

    Members:

      ancestorsof is a list of the nodes whose ancestors are included in the
      outgoing operation.

      missing is a list of those ancestors of ancestorsof that are present in
      local but not in remote.

      common is a set containing revs common between the local and the remote
      repository (at least all of those that are ancestors of ancestorsof).

      commonheads is the list of heads of common.

      excluded is the list of missing changeset that shouldn't be sent
      remotely.

    Some members are computed on demand from the heads, unless provided upfront
    by discovery."""

    def __init__(
        self, repo, commonheads=None, ancestorsof=None, missingroots=None
    ):
        # at least one of them must not be set
        assert None in (commonheads, missingroots)
        cl = repo.changelog
        if ancestorsof is None:
            ancestorsof = cl.heads()
        if missingroots:
            discbases = []
            for n in missingroots:
                discbases.extend([p for p in cl.parents(n) if p != repo.nullid])
            # TODO remove call to nodesbetween.
            # TODO populate attributes on outgoing instance instead of setting
            # discbases.
            csets, roots, heads = cl.nodesbetween(missingroots, ancestorsof)
            included = set(csets)
            ancestorsof = heads
            commonheads = [n for n in discbases if n not in included]
        elif not commonheads:
            commonheads = [repo.nullid]
        self.commonheads = commonheads
        self.ancestorsof = ancestorsof
        self._revlog = cl
        self._common = None
        self._missing = None
        self.excluded = []

    def _computecommonmissing(self):
        sets = self._revlog.findcommonmissing(
            self.commonheads, self.ancestorsof
        )
        self._common, self._missing = sets

    @util.propertycache
    def common(self):
        if self._common is None:
            self._computecommonmissing()
        return self._common

    @util.propertycache
    def missing(self):
        if self._missing is None:
            self._computecommonmissing()
        return self._missing

    @property
    def missingheads(self):
        util.nouideprecwarn(
            b'outgoing.missingheads never contained what the name suggests and '
            b'was renamed to outgoing.ancestorsof. check your code for '
            b'correctness.',
            b'5.5',
            stacklevel=2,
        )
        return self.ancestorsof


def findcommonoutgoing(
    repo, other, onlyheads=None, force=False, commoninc=None, portable=False
):
    """Return an outgoing instance to identify the nodes present in repo but
    not in other.

    If onlyheads is given, only nodes ancestral to nodes in onlyheads
    (inclusive) are included. If you already know the local repo's heads,
    passing them in onlyheads is faster than letting them be recomputed here.

    If commoninc is given, it must be the result of a prior call to
    findcommonincoming(repo, other, force) to avoid recomputing it here.

    If portable is given, compute more conservative common and ancestorsof,
    to make bundles created from the instance more portable."""
    # declare an empty outgoing object to be filled later
    og = outgoing(repo, None, None)

    # get common set if not provided
    if commoninc is None:
        commoninc = findcommonincoming(
            repo, other, force=force, ancestorsof=onlyheads
        )
    og.commonheads, _any, _hds = commoninc

    # compute outgoing
    mayexclude = repo._phasecache.phaseroots[phases.secret] or repo.obsstore
    if not mayexclude:
        og.ancestorsof = onlyheads or repo.heads()
    elif onlyheads is None:
        # use visible heads as it should be cached
        og.ancestorsof = repo.filtered(b"served").heads()
        og.excluded = [ctx.node() for ctx in repo.set(b'secret() or extinct()')]
    else:
        # compute common, missing and exclude secret stuff
        sets = repo.changelog.findcommonmissing(og.commonheads, onlyheads)
        og._common, allmissing = sets
        og._missing = missing = []
        og.excluded = excluded = []
        for node in allmissing:
            ctx = repo[node]
            if ctx.phase() >= phases.secret or ctx.extinct():
                excluded.append(node)
            else:
                missing.append(node)
        if len(missing) == len(allmissing):
            ancestorsof = onlyheads
        else:  # update missing heads
            ancestorsof = phases.newheads(repo, onlyheads, excluded)
        og.ancestorsof = ancestorsof
    if portable:
        # recompute common and ancestorsof as if -r<rev> had been given for
        # each head of missing, and --base <rev> for each head of the proper
        # ancestors of missing
        og._computecommonmissing()
        cl = repo.changelog
        missingrevs = {cl.rev(n) for n in og._missing}
        og._common = set(cl.ancestors(missingrevs)) - missingrevs
        commonheads = set(og.commonheads)
        og.ancestorsof = [h for h in og.ancestorsof if h not in commonheads]

    return og


def _headssummary(pushop):
    """compute a summary of branch and heads status before and after push

    return {'branch': ([remoteheads], [newheads],
                       [unsyncedheads], [discardedheads])} mapping

    - branch: the branch name,
    - remoteheads: the list of remote heads known locally
                   None if the branch is new,
    - newheads: the new remote heads (known locally) with outgoing pushed,
    - unsyncedheads: the list of remote heads unknown locally,
    - discardedheads: the list of heads made obsolete by the push.
    """
    repo = pushop.repo.unfiltered()
    remote = pushop.remote
    outgoing = pushop.outgoing
    cl = repo.changelog
    headssum = {}
    missingctx = set()
    # A. Create set of branches involved in the push.
    branches = set()
    for n in outgoing.missing:
        ctx = repo[n]
        missingctx.add(ctx)
        branches.add(ctx.branch())

    with remote.commandexecutor() as e:
        remotemap = e.callcommand(b'branchmap', {}).result()

    knownnode = cl.hasnode  # do not use nodemap until it is filtered
    # A. register remote heads of branches which are in outgoing set
    for branch, heads in pycompat.iteritems(remotemap):
        # don't add head info about branches which we don't have locally
        if branch not in branches:
            continue
        known = []
        unsynced = []
        for h in heads:
            if knownnode(h):
                known.append(h)
            else:
                unsynced.append(h)
        headssum[branch] = (known, list(known), unsynced)

    # B. add new branch data
    for branch in branches:
        if branch not in headssum:
            headssum[branch] = (None, [], [])

    # C. Update newmap with outgoing changes.
    # This will possibly add new heads and remove existing ones.
    newmap = branchmap.remotebranchcache(
        repo,
        (
            (branch, heads[1])
            for branch, heads in pycompat.iteritems(headssum)
            if heads[0] is not None
        ),
    )
    newmap.update(repo, (ctx.rev() for ctx in missingctx))
    for branch, newheads in pycompat.iteritems(newmap):
        headssum[branch][1][:] = newheads
    for branch, items in pycompat.iteritems(headssum):
        for l in items:
            if l is not None:
                l.sort()
        headssum[branch] = items + ([],)

    # If there are no obsstore, no post processing are needed.
    if repo.obsstore:
        torev = repo.changelog.rev
        futureheads = {torev(h) for h in outgoing.ancestorsof}
        futureheads |= {torev(h) for h in outgoing.commonheads}
        allfuturecommon = repo.changelog.ancestors(futureheads, inclusive=True)
        for branch, heads in sorted(pycompat.iteritems(headssum)):
            remoteheads, newheads, unsyncedheads, placeholder = heads
            result = _postprocessobsolete(pushop, allfuturecommon, newheads)
            headssum[branch] = (
                remoteheads,
                sorted(result[0]),
                unsyncedheads,
                sorted(result[1]),
            )
    return headssum


def _oldheadssummary(repo, remoteheads, outgoing, inc=False):
    """Compute branchmapsummary for repo without branchmap support"""

    # 1-4b. old servers: Check for new topological heads.
    # Construct {old,new}map with branch = None (topological branch).
    # (code based on update)
    knownnode = repo.changelog.hasnode  # no nodemap until it is filtered
    oldheads = sorted(h for h in remoteheads if knownnode(h))
    # all nodes in outgoing.missing are children of either:
    # - an element of oldheads
    # - another element of outgoing.missing
    # - nullrev
    # This explains why the new head are very simple to compute.
    r = repo.set(b'heads(%ln + %ln)', oldheads, outgoing.missing)
    newheads = sorted(c.node() for c in r)
    # set some unsynced head to issue the "unsynced changes" warning
    if inc:
        unsynced = [None]
    else:
        unsynced = []
    return {None: (oldheads, newheads, unsynced, [])}


def _nowarnheads(pushop):
    # Compute newly pushed bookmarks. We don't warn about bookmarked heads.
    repo = pushop.repo.unfiltered()
    remote = pushop.remote
    localbookmarks = repo._bookmarks

    with remote.commandexecutor() as e:
        remotebookmarks = e.callcommand(
            b'listkeys',
            {
                b'namespace': b'bookmarks',
            },
        ).result()

    bookmarkedheads = set()

    # internal config: bookmarks.pushing
    newbookmarks = [
        localbookmarks.expandname(b)
        for b in pushop.ui.configlist(b'bookmarks', b'pushing')
    ]

    for bm in localbookmarks:
        rnode = remotebookmarks.get(bm)
        if rnode and rnode in repo:
            lctx, rctx = repo[localbookmarks[bm]], repo[rnode]
            if bookmarks.validdest(repo, rctx, lctx):
                bookmarkedheads.add(lctx.node())
        else:
            if bm in newbookmarks and bm not in remotebookmarks:
                bookmarkedheads.add(localbookmarks[bm])

    return bookmarkedheads


def checkheads(pushop):
    """Check that a push won't add any outgoing head

    raise StateError error and display ui message as needed.
    """

    repo = pushop.repo.unfiltered()
    remote = pushop.remote
    outgoing = pushop.outgoing
    remoteheads = pushop.remoteheads
    newbranch = pushop.newbranch
    inc = bool(pushop.incoming)

    # Check for each named branch if we're creating new remote heads.
    # To be a remote head after push, node must be either:
    # - unknown locally
    # - a local outgoing head descended from update
    # - a remote head that's known locally and not
    #   ancestral to an outgoing head
    if remoteheads == [repo.nullid]:
        # remote is empty, nothing to check.
        return

    if remote.capable(b'branchmap'):
        headssum = _headssummary(pushop)
    else:
        headssum = _oldheadssummary(repo, remoteheads, outgoing, inc)
    pushop.pushbranchmap = headssum
    newbranches = [
        branch
        for branch, heads in pycompat.iteritems(headssum)
        if heads[0] is None
    ]
    # 1. Check for new branches on the remote.
    if newbranches and not newbranch:  # new branch requires --new-branch
        branchnames = b', '.join(sorted(newbranches))
        # Calculate how many of the new branches are closed branches
        closedbranches = set()
        for tag, heads, tip, isclosed in repo.branchmap().iterbranches():
            if isclosed:
                closedbranches.add(tag)
        closedbranches = closedbranches & set(newbranches)
        if closedbranches:
            errmsg = _(b"push creates new remote branches: %s (%d closed)") % (
                branchnames,
                len(closedbranches),
            )
        else:
            errmsg = _(b"push creates new remote branches: %s") % branchnames
        hint = _(b"use 'hg push --new-branch' to create new remote branches")
        raise error.StateError(errmsg, hint=hint)

    # 2. Find heads that we need not warn about
    nowarnheads = _nowarnheads(pushop)

    # 3. Check for new heads.
    # If there are more heads after the push than before, a suitable
    # error message, depending on unsynced status, is displayed.
    errormsg = None
    for branch, heads in sorted(pycompat.iteritems(headssum)):
        remoteheads, newheads, unsyncedheads, discardedheads = heads
        # add unsynced data
        if remoteheads is None:
            oldhs = set()
        else:
            oldhs = set(remoteheads)
        oldhs.update(unsyncedheads)
        dhs = None  # delta heads, the new heads on branch
        newhs = set(newheads)
        newhs.update(unsyncedheads)
        if unsyncedheads:
            if None in unsyncedheads:
                # old remote, no heads data
                heads = None
            else:
                heads = scmutil.nodesummaries(repo, unsyncedheads)
            if heads is None:
                repo.ui.status(
                    _(b"remote has heads that are not known locally\n")
                )
            elif branch is None:
                repo.ui.status(
                    _(b"remote has heads that are not known locally: %s\n")
                    % heads
                )
            else:
                repo.ui.status(
                    _(
                        b"remote has heads on branch '%s' that are "
                        b"not known locally: %s\n"
                    )
                    % (branch, heads)
                )
        if remoteheads is None:
            if len(newhs) > 1:
                dhs = list(newhs)
                if errormsg is None:
                    errormsg = (
                        _(b"push creates new branch '%s' with multiple heads")
                        % branch
                    )
                    hint = _(
                        b"merge or"
                        b" see 'hg help push' for details about"
                        b" pushing new heads"
                    )
        elif len(newhs) > len(oldhs):
            # remove bookmarked or existing remote heads from the new heads list
            dhs = sorted(newhs - nowarnheads - oldhs)
        if dhs:
            if errormsg is None:
                if branch not in (b'default', None):
                    errormsg = _(
                        b"push creates new remote head %s on branch '%s'"
                    ) % (
                        short(dhs[0]),
                        branch,
                    )
                elif repo[dhs[0]].bookmarks():
                    errormsg = _(
                        b"push creates new remote head %s "
                        b"with bookmark '%s'"
                    ) % (short(dhs[0]), repo[dhs[0]].bookmarks()[0])
                else:
                    errormsg = _(b"push creates new remote head %s") % short(
                        dhs[0]
                    )
                if unsyncedheads:
                    hint = _(
                        b"pull and merge or"
                        b" see 'hg help push' for details about"
                        b" pushing new heads"
                    )
                else:
                    hint = _(
                        b"merge or"
                        b" see 'hg help push' for details about"
                        b" pushing new heads"
                    )
            if branch is None:
                repo.ui.note(_(b"new remote heads:\n"))
            else:
                repo.ui.note(_(b"new remote heads on branch '%s':\n") % branch)
            for h in dhs:
                repo.ui.note(b" %s\n" % short(h))
    if errormsg:
        raise error.StateError(errormsg, hint=hint)


def _postprocessobsolete(pushop, futurecommon, candidate_newhs):
    """post process the list of new heads with obsolescence information

    Exists as a sub-function to contain the complexity and allow extensions to
    experiment with smarter logic.

    Returns (newheads, discarded_heads) tuple
    """
    # known issue
    #
    # * We "silently" skip processing on all changeset unknown locally
    #
    # * if <nh> is public on the remote, it won't be affected by obsolete
    #     marker and a new is created

    # define various utilities and containers
    repo = pushop.repo
    unfi = repo.unfiltered()
    torev = unfi.changelog.index.get_rev
    public = phases.public
    getphase = unfi._phasecache.phase
    ispublic = lambda r: getphase(unfi, r) == public
    ispushed = lambda n: torev(n) in futurecommon
    hasoutmarker = functools.partial(pushingmarkerfor, unfi.obsstore, ispushed)
    successorsmarkers = unfi.obsstore.successors
    newhs = set()  # final set of new heads
    discarded = set()  # new head of fully replaced branch

    localcandidate = set()  # candidate heads known locally
    unknownheads = set()  # candidate heads unknown locally
    for h in candidate_newhs:
        if h in unfi:
            localcandidate.add(h)
        else:
            if successorsmarkers.get(h) is not None:
                msg = (
                    b'checkheads: remote head unknown locally has'
                    b' local marker: %s\n'
                )
                repo.ui.debug(msg % hex(h))
            unknownheads.add(h)

    # fast path the simple case
    if len(localcandidate) == 1:
        return unknownheads | set(candidate_newhs), set()

    # actually process branch replacement
    while localcandidate:
        nh = localcandidate.pop()
        current_branch = unfi[nh].branch()
        # run this check early to skip the evaluation of the whole branch
        if torev(nh) in futurecommon or ispublic(torev(nh)):
            newhs.add(nh)
            continue

        # Get all revs/nodes on the branch exclusive to this head
        # (already filtered heads are "ignored"))
        branchrevs = unfi.revs(
            b'only(%n, (%ln+%ln))', nh, localcandidate, newhs
        )

        branchnodes = []
        for r in branchrevs:
            c = unfi[r]
            if c.branch() == current_branch:
                branchnodes.append(c.node())

        # The branch won't be hidden on the remote if
        # * any part of it is public,
        # * any part of it is considered part of the result by previous logic,
        # * if we have no markers to push to obsolete it.
        if (
            any(ispublic(r) for r in branchrevs)
            or any(torev(n) in futurecommon for n in branchnodes)
            or any(not hasoutmarker(n) for n in branchnodes)
        ):
            newhs.add(nh)
        else:
            # note: there is a corner case if there is a merge in the branch.
            # we might end up with -more- heads.  However, these heads are not
            # "added" by the push, but more by the "removal" on the remote so I
            # think is a okay to ignore them,
            discarded.add(nh)
    newhs |= unknownheads
    return newhs, discarded


def pushingmarkerfor(obsstore, ispushed, node):
    """true if some markers are to be pushed for node

    We cannot just look in to the pushed obsmarkers from the pushop because
    discovery might have filtered relevant markers. In addition listing all
    markers relevant to all changesets in the pushed set would be too expensive
    (O(len(repo)))

    (note: There are cache opportunity in this function. but it would requires
    a two dimensional stack.)
    """
    successorsmarkers = obsstore.successors
    stack = [node]
    seen = set(stack)
    while stack:
        current = stack.pop()
        if ispushed(current):
            return True
        markers = successorsmarkers.get(current, ())
        # markers fields = ('prec', 'succs', 'flag', 'meta', 'date', 'parents')
        for m in markers:
            nexts = m[1]  # successors
            if not nexts:  # this is a prune marker
                nexts = m[5] or ()  # parents
            for n in nexts:
                if n not in seen:
                    seen.add(n)
                    stack.append(n)
    return False
