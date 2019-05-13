# discovery.py - protocol changeset discovery functions
#
# Copyright 2010 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import nullid, short
from i18n import _
import util, setdiscovery, treediscovery, phases, obsolete, bookmarks
import branchmap

def findcommonincoming(repo, remote, heads=None, force=False):
    """Return a tuple (common, anyincoming, heads) used to identify the common
    subset of nodes between repo and remote.

    "common" is a list of (at least) the heads of the common subset.
    "anyincoming" is testable as a boolean indicating if any nodes are missing
      locally. If remote does not support getbundle, this actually is a list of
      roots of the nodes that would be incoming, to be supplied to
      changegroupsubset. No code except for pull should be relying on this fact
      any longer.
    "heads" is either the supplied heads, or else the remote's heads.

    If you pass heads and they are all known locally, the response lists just
    these heads in "common" and in "heads".

    Please use findcommonoutgoing to compute the set of outgoing nodes to give
    extensions a good hook into outgoing.
    """

    if not remote.capable('getbundle'):
        return treediscovery.findcommonincoming(repo, remote, heads, force)

    if heads:
        allknown = True
        nm = repo.changelog.nodemap
        for h in heads:
            if nm.get(h) is None:
                allknown = False
                break
        if allknown:
            return (heads, False, heads)

    res = setdiscovery.findcommonheads(repo.ui, repo, remote,
                                       abortwhenunrelated=not force)
    common, anyinc, srvheads = res
    return (list(common), anyinc, heads or list(srvheads))

class outgoing(object):
    '''Represents the set of nodes present in a local repo but not in a
    (possibly) remote one.

    Members:

      missing is a list of all nodes present in local but not in remote.
      common is a list of all nodes shared between the two repos.
      excluded is the list of missing changeset that shouldn't be sent remotely.
      missingheads is the list of heads of missing.
      commonheads is the list of heads of common.

    The sets are computed on demand from the heads, unless provided upfront
    by discovery.'''

    def __init__(self, revlog, commonheads, missingheads):
        self.commonheads = commonheads
        self.missingheads = missingheads
        self._revlog = revlog
        self._common = None
        self._missing = None
        self.excluded = []

    def _computecommonmissing(self):
        sets = self._revlog.findcommonmissing(self.commonheads,
                                              self.missingheads)
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

def findcommonoutgoing(repo, other, onlyheads=None, force=False,
                       commoninc=None, portable=False):
    '''Return an outgoing instance to identify the nodes present in repo but
    not in other.

    If onlyheads is given, only nodes ancestral to nodes in onlyheads
    (inclusive) are included. If you already know the local repo's heads,
    passing them in onlyheads is faster than letting them be recomputed here.

    If commoninc is given, it must be the result of a prior call to
    findcommonincoming(repo, other, force) to avoid recomputing it here.

    If portable is given, compute more conservative common and missingheads,
    to make bundles created from the instance more portable.'''
    # declare an empty outgoing object to be filled later
    og = outgoing(repo.changelog, None, None)

    # get common set if not provided
    if commoninc is None:
        commoninc = findcommonincoming(repo, other, force=force)
    og.commonheads, _any, _hds = commoninc

    # compute outgoing
    mayexclude = (repo._phasecache.phaseroots[phases.secret] or repo.obsstore)
    if not mayexclude:
        og.missingheads = onlyheads or repo.heads()
    elif onlyheads is None:
        # use visible heads as it should be cached
        og.missingheads = repo.filtered("served").heads()
        og.excluded = [ctx.node() for ctx in repo.set('secret() or extinct()')]
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
            missingheads = onlyheads
        else: # update missing heads
            missingheads = phases.newheads(repo, onlyheads, excluded)
        og.missingheads = missingheads
    if portable:
        # recompute common and missingheads as if -r<rev> had been given for
        # each head of missing, and --base <rev> for each head of the proper
        # ancestors of missing
        og._computecommonmissing()
        cl = repo.changelog
        missingrevs = set(cl.rev(n) for n in og._missing)
        og._common = set(cl.ancestors(missingrevs)) - missingrevs
        commonheads = set(og.commonheads)
        og.missingheads = [h for h in og.missingheads if h not in commonheads]

    return og

def _headssummary(repo, remote, outgoing):
    """compute a summary of branch and heads status before and after push

    return {'branch': ([remoteheads], [newheads], [unsyncedheads])} mapping

    - branch: the branch name
    - remoteheads: the list of remote heads known locally
                   None is the branch is new
    - newheads: the new remote heads (known locally) with outgoing pushed
    - unsyncedheads: the list of remote heads unknown locally.
    """
    cl = repo.changelog
    headssum = {}
    # A. Create set of branches involved in the push.
    branches = set(repo[n].branch() for n in outgoing.missing)
    remotemap = remote.branchmap()
    newbranches = branches - set(remotemap)
    branches.difference_update(newbranches)

    # A. register remote heads
    remotebranches = set()
    for branch, heads in remote.branchmap().iteritems():
        remotebranches.add(branch)
        known = []
        unsynced = []
        for h in heads:
            if h in cl.nodemap:
                known.append(h)
            else:
                unsynced.append(h)
        headssum[branch] = (known, list(known), unsynced)
    # B. add new branch data
    missingctx = list(repo[n] for n in outgoing.missing)
    touchedbranches = set()
    for ctx in missingctx:
        branch = ctx.branch()
        touchedbranches.add(branch)
        if branch not in headssum:
            headssum[branch] = (None, [], [])

    # C drop data about untouched branches:
    for branch in remotebranches - touchedbranches:
        del headssum[branch]

    # D. Update newmap with outgoing changes.
    # This will possibly add new heads and remove existing ones.
    newmap = branchmap.branchcache((branch, heads[1])
                                 for branch, heads in headssum.iteritems()
                                 if heads[0] is not None)
    newmap.update(repo, (ctx.rev() for ctx in missingctx))
    for branch, newheads in newmap.iteritems():
        headssum[branch][1][:] = newheads
    return headssum

def _oldheadssummary(repo, remoteheads, outgoing, inc=False):
    """Compute branchmapsummary for repo without branchmap support"""

    cl = repo.changelog
    # 1-4b. old servers: Check for new topological heads.
    # Construct {old,new}map with branch = None (topological branch).
    # (code based on update)
    oldheads = set(h for h in remoteheads if h in cl.nodemap)
    # all nodes in outgoing.missing are children of either:
    # - an element of oldheads
    # - another element of outgoing.missing
    # - nullrev
    # This explains why the new head are very simple to compute.
    r = repo.set('heads(%ln + %ln)', oldheads, outgoing.missing)
    newheads = list(c.node() for c in r)
    unsynced = inc and set([None]) or set()
    return {None: (oldheads, newheads, unsynced)}

def checkheads(repo, remote, outgoing, remoteheads, newbranch=False, inc=False):
    """Check that a push won't add any outgoing head

    raise Abort error and display ui message as needed.
    """
    # Check for each named branch if we're creating new remote heads.
    # To be a remote head after push, node must be either:
    # - unknown locally
    # - a local outgoing head descended from update
    # - a remote head that's known locally and not
    #   ancestral to an outgoing head
    if remoteheads == [nullid]:
        # remote is empty, nothing to check.
        return

    if remote.capable('branchmap'):
        headssum = _headssummary(repo, remote, outgoing)
    else:
        headssum = _oldheadssummary(repo, remoteheads, outgoing, inc)
    newbranches = [branch for branch, heads in headssum.iteritems()
                   if heads[0] is None]
    # 1. Check for new branches on the remote.
    if newbranches and not newbranch:  # new branch requires --new-branch
        branchnames = ', '.join(sorted(newbranches))
        raise util.Abort(_("push creates new remote branches: %s!")
                           % branchnames,
                         hint=_("use 'hg push --new-branch' to create"
                                " new remote branches"))

    # 2 compute newly pushed bookmarks. We
    # we don't warned about bookmarked heads.
    localbookmarks = repo._bookmarks
    remotebookmarks = remote.listkeys('bookmarks')
    bookmarkedheads = set()
    for bm in localbookmarks:
        rnode = remotebookmarks.get(bm)
        if rnode and rnode in repo:
            lctx, rctx = repo[bm], repo[rnode]
            if bookmarks.validdest(repo, rctx, lctx):
                bookmarkedheads.add(lctx.node())

    # 3. Check for new heads.
    # If there are more heads after the push than before, a suitable
    # error message, depending on unsynced status, is displayed.
    error = None
    unsynced = False
    allmissing = set(outgoing.missing)
    allfuturecommon = set(c.node() for c in repo.set('%ld', outgoing.common))
    allfuturecommon.update(allmissing)
    for branch, heads in sorted(headssum.iteritems()):
        if heads[0] is None:
            # Maybe we should abort if we push more that one head
            # for new branches ?
            continue
        candidate_newhs = set(heads[1])
        # add unsynced data
        oldhs = set(heads[0])
        oldhs.update(heads[2])
        candidate_newhs.update(heads[2])
        dhs = None
        discardedheads = set()
        if repo.obsstore:
            # remove future heads which are actually obsolete by another
            # pushed element:
            #
            # XXX as above, There are several cases this case does not handle
            # XXX properly
            #
            # (1) if <nh> is public, it won't be affected by obsolete marker
            #     and a new is created
            #
            # (2) if the new heads have ancestors which are not obsolete and
            #     not ancestors of any other heads we will have a new head too.
            #
            # This two case will be easy to handle for know changeset but much
            # more tricky for unsynced changes.
            newhs = set()
            for nh in candidate_newhs:
                if nh in repo and repo[nh].phase() <= phases.public:
                    newhs.add(nh)
                else:
                    for suc in obsolete.allsuccessors(repo.obsstore, [nh]):
                        if suc != nh and suc in allfuturecommon:
                            discardedheads.add(nh)
                            break
                    else:
                        newhs.add(nh)
        else:
            newhs = candidate_newhs
        if [h for h in heads[2] if h not in discardedheads]:
            unsynced = True
        if len(newhs) > len(oldhs):
            # strip updates to existing remote heads from the new heads list
            dhs = sorted(newhs - bookmarkedheads - oldhs)
        if dhs:
            if error is None:
                if branch not in ('default', None):
                    error = _("push creates new remote head %s "
                              "on branch '%s'!") % (short(dhs[0]), branch)
                else:
                    error = _("push creates new remote head %s!"
                              ) % short(dhs[0])
                if heads[2]: # unsynced
                    hint = _("you should pull and merge or "
                             "use push -f to force")
                else:
                    hint = _("did you forget to merge? "
                             "use push -f to force")
            if branch is not None:
                repo.ui.note(_("new remote heads on branch '%s'\n") % branch)
            for h in dhs:
                repo.ui.note(_("new remote head %s\n") % short(h))
    if error:
        raise util.Abort(error, hint=hint)

    # 6. Check for unsynced changes on involved branches.
    if unsynced:
        repo.ui.warn(_("note: unsynced remote changes!\n"))
