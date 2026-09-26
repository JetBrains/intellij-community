# destutil.py - Mercurial utility function for command destination
#
#  Copyright Olivia Mackall <olivia@selenic.com> and other
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from .i18n import _
from . import bookmarks, error, obsutil, scmutil, stack


def orphanpossibledestination(repo, rev):
    """Return all changesets that may be a new parent for orphan `rev`.

    This function works fine on non-orphan revisions, it's just silly
    because there's no destination implied by obsolete markers, so
    it'll return nothing.
    """
    tonode = repo.changelog.node
    parents = repo.changelog.parentrevs
    torev = repo.changelog.rev
    dest = set()
    tovisit = list(parents(rev))
    while tovisit:
        r = tovisit.pop()
        succsets = obsutil.successorssets(repo, tonode(r))
        if not succsets:
            # if there are no successors for r, r was probably pruned
            # and we should walk up to r's parents to try and find
            # some successors.
            tovisit.extend(parents(r))
        else:
            # We should probably pick only one destination from split
            # (case where '1 < len(ss)'), This could be the currently
            # tipmost, but the correct result is less clear when
            # results of the split have been moved such that they
            # reside on multiple branches.
            for ss in succsets:
                for n in ss:
                    dr = torev(n)
                    if dr != -1:
                        dest.add(dr)
    return dest


def _destupdateobs(repo, clean):
    """decide of an update destination from obsolescence markers"""
    node = None
    wc = repo[None]
    p1 = wc.p1()
    movemark = None

    if p1.obsolete() and not p1.children():
        # allow updating to successors
        successors = obsutil.successorssets(repo, p1.node())

        # behavior of certain cases is as follows,
        #
        # divergent changesets: update to highest rev, similar to what
        #     is currently done when there are more than one head
        #     (i.e. 'tip')
        #
        # replaced changesets: same as divergent except we know there
        # is no conflict
        #
        # pruned changeset: update to the closest non-obsolete ancestor,
        # similar to what 'hg prune' currently does

        if successors:
            # flatten the list here handles both divergent (len > 1)
            # and the usual case (len = 1)
            successors = [n for sub in successors for n in sub]

            # get the max revision for the given successors set,
            # i.e. the 'tip' of a set
            node = repo.revs(b'max(%ln)', successors).first()
        else:
            p1 = p1.p1()
            while p1.obsolete():
                p1 = p1.p1()
            node = p1.node()

        if node is not None and bookmarks.isactivewdirparent(repo):
            movemark = repo[b'.'].node()

    return node, movemark, None


def _destupdatebook(repo, clean):
    """decide on an update destination from active bookmark"""
    # we also move the active bookmark, if any
    node = None
    activemark, movemark = bookmarks.calculateupdate(repo.ui, repo)
    if activemark is not None:
        node = repo._bookmarks[activemark]
    return node, movemark, activemark


def _destupdatebranch(repo, clean):
    """decide on an update destination from current branch

    This ignores closed branch heads.
    """
    wc = repo[None]
    movemark = node = None
    currentbranch = wc.branch()

    if clean:
        currentbranch = repo[b'.'].branch()

    if currentbranch in repo.branchmap():
        heads = repo.branchheads(currentbranch)
        if heads:
            node = repo.revs(b'max(.::(%ln))', heads).first()
        if bookmarks.isactivewdirparent(repo):
            movemark = repo[b'.'].node()
    elif currentbranch == b'default' and not wc.p1():
        # "null" parent belongs to "default" branch, but it doesn't exist, so
        # update to the tipmost non-closed branch head
        node = repo.revs(b'max(head() and not closed())').first()
    else:
        node = repo[b'.'].node()
    return node, movemark, None


def _destupdatebranchfallback(repo, clean):
    """decide on an update destination from closed heads in current branch"""
    wc = repo[None]
    currentbranch = wc.branch()
    movemark = None
    if currentbranch in repo.branchmap():
        # here, all descendant branch heads are closed
        heads = repo.branchheads(currentbranch, closed=True)
        assert heads, b"any branch has at least one head"
        node = repo.revs(b'max(.::(%ln))', heads).first()
        assert (
            node is not None
        ), b"any revision has at least one descendant branch head"
        if bookmarks.isactivewdirparent(repo):
            movemark = repo[b'.'].node()
    else:
        # here, no "default" branch, and all branches are closed
        node = repo.lookup(b'tip')
        assert node is not None, b"'tip' exists even in empty repository"
    return node, movemark, None


# order in which each step should be evaluated
# steps are run until one finds a destination
destupdatesteps = [b'evolution', b'bookmark', b'branch', b'branchfallback']
# mapping to ease extension overriding steps.
destupdatestepmap = {
    b'evolution': _destupdateobs,
    b'bookmark': _destupdatebook,
    b'branch': _destupdatebranch,
    b'branchfallback': _destupdatebranchfallback,
}


def destupdate(repo, clean=False):
    """destination for bare update operation

    return (rev, movemark, activemark)

    - rev: the revision to update to,
    - movemark: node to move the active bookmark from
                (cf bookmark.calculate update),
    - activemark: a bookmark to activate at the end of the update.
    """
    node = movemark = activemark = None

    for step in destupdatesteps:
        node, movemark, activemark = destupdatestepmap[step](repo, clean)
        if node is not None:
            break
    rev = repo[node].rev()

    return rev, movemark, activemark


msgdestmerge = {
    # too many matching divergent bookmark
    b'toomanybookmarks': {
        b'merge': (
            _(
                b"multiple matching bookmarks to merge -"
                b" please merge with an explicit rev or bookmark"
            ),
            _(b"run 'hg heads' to see all heads, specify rev with -r"),
        ),
        b'rebase': (
            _(
                b"multiple matching bookmarks to rebase -"
                b" please rebase to an explicit rev or bookmark"
            ),
            _(b"run 'hg heads' to see all heads, specify destination with -d"),
        ),
    },
    # no other matching divergent bookmark
    b'nootherbookmarks': {
        b'merge': (
            _(
                b"no matching bookmark to merge - "
                b"please merge with an explicit rev or bookmark"
            ),
            _(b"run 'hg heads' to see all heads, specify rev with -r"),
        ),
        b'rebase': (
            _(
                b"no matching bookmark to rebase - "
                b"please rebase to an explicit rev or bookmark"
            ),
            _(b"run 'hg heads' to see all heads, specify destination with -d"),
        ),
    },
    # branch have too many unbookmarked heads, no obvious destination
    b'toomanyheads': {
        b'merge': (
            _(b"branch '%s' has %d heads - please merge with an explicit rev"),
            _(b"run 'hg heads .' to see heads, specify rev with -r"),
        ),
        b'rebase': (
            _(b"branch '%s' has %d heads - please rebase to an explicit rev"),
            _(b"run 'hg heads .' to see heads, specify destination with -d"),
        ),
    },
    # branch have no other unbookmarked heads
    b'bookmarkedheads': {
        b'merge': (
            _(b"heads are bookmarked - please merge with an explicit rev"),
            _(b"run 'hg heads' to see all heads, specify rev with -r"),
        ),
        b'rebase': (
            _(b"heads are bookmarked - please rebase to an explicit rev"),
            _(b"run 'hg heads' to see all heads, specify destination with -d"),
        ),
    },
    # branch have just a single heads, but there is other branches
    b'nootherbranchheads': {
        b'merge': (
            _(b"branch '%s' has one head - please merge with an explicit rev"),
            _(b"run 'hg heads' to see all heads, specify rev with -r"),
        ),
        b'rebase': (
            _(b"branch '%s' has one head - please rebase to an explicit rev"),
            _(b"run 'hg heads' to see all heads, specify destination with -d"),
        ),
    },
    # repository have a single head
    b'nootherheads': {
        b'merge': (_(b'nothing to merge'), None),
        b'rebase': (_(b'nothing to rebase'), None),
    },
    # repository have a single head and we are not on it
    b'nootherheadsbehind': {
        b'merge': (_(b'nothing to merge'), _(b"use 'hg update' instead")),
        b'rebase': (_(b'nothing to rebase'), _(b"use 'hg update' instead")),
    },
    # We are not on a head
    b'notatheads': {
        b'merge': (
            _(b'working directory not at a head revision'),
            _(b"use 'hg update' or merge with an explicit revision"),
        ),
        b'rebase': (
            _(b'working directory not at a head revision'),
            _(b"use 'hg update' or rebase to an explicit revision"),
        ),
    },
    b'emptysourceset': {
        b'merge': (_(b'source set is empty'), None),
        b'rebase': (_(b'source set is empty'), None),
    },
    b'multiplebranchessourceset': {
        b'merge': (_(b'source set is rooted in multiple branches'), None),
        b'rebase': (
            _(b'rebaseset is rooted in multiple named branches'),
            _(b'specify an explicit destination with --dest'),
        ),
    },
}


def _destmergebook(repo, action=b'merge', sourceset=None, destspace=None):
    """find merge destination in the active bookmark case"""
    node = None
    bmheads = bookmarks.headsforactive(repo)
    curhead = repo._bookmarks[repo._activebookmark]
    if len(bmheads) == 2:
        if curhead == bmheads[0]:
            node = bmheads[1]
        else:
            node = bmheads[0]
    elif len(bmheads) > 2:
        msg, hint = msgdestmerge[b'toomanybookmarks'][action]
        raise error.ManyMergeDestAbort(msg, hint=hint)
    elif len(bmheads) <= 1:
        msg, hint = msgdestmerge[b'nootherbookmarks'][action]
        raise error.NoMergeDestAbort(msg, hint=hint)
    assert node is not None
    return node


def _destmergebranch(
    repo, action=b'merge', sourceset=None, onheadcheck=True, destspace=None
):
    """find merge destination based on branch heads"""
    node = None

    if sourceset is None:
        sourceset = [repo[repo.dirstate.p1()].rev()]
        branch = repo.dirstate.branch()
    elif not sourceset:
        msg, hint = msgdestmerge[b'emptysourceset'][action]
        raise error.NoMergeDestAbort(msg, hint=hint)
    else:
        branch = None
        for ctx in repo.set(b'roots(%ld::%ld)', sourceset, sourceset):
            if branch is not None and ctx.branch() != branch:
                msg, hint = msgdestmerge[b'multiplebranchessourceset'][action]
                raise error.ManyMergeDestAbort(msg, hint=hint)
            branch = ctx.branch()

    bheads = repo.branchheads(branch)
    onhead = repo.revs(b'%ld and %ln', sourceset, bheads)
    if onheadcheck and not onhead:
        # Case A: working copy if not on a head. (merge only)
        #
        # This is probably a user mistake We bailout pointing at 'hg update'
        if len(repo.heads()) <= 1:
            msg, hint = msgdestmerge[b'nootherheadsbehind'][action]
        else:
            msg, hint = msgdestmerge[b'notatheads'][action]
        raise error.Abort(msg, hint=hint)
    # remove heads descendants of source from the set
    bheads = list(repo.revs(b'%ln - (%ld::)', bheads, sourceset))
    # filters out bookmarked heads
    nbhs = list(repo.revs(b'%ld - bookmark()', bheads))

    if destspace is not None:
        # restrict search space
        # used in the 'hg pull --rebase' case, see issue 5214.
        nbhs = list(repo.revs(b'%ld and %ld', destspace, nbhs))

    if len(nbhs) > 1:
        # Case B: There is more than 1 other anonymous heads
        #
        # This means that there will be more than 1 candidate. This is
        # ambiguous. We abort asking the user to pick as explicit destination
        # instead.
        msg, hint = msgdestmerge[b'toomanyheads'][action]
        msg %= (branch, len(bheads) + 1)
        raise error.ManyMergeDestAbort(msg, hint=hint)
    elif not nbhs:
        # Case B: There is no other anonymous heads
        #
        # This means that there is no natural candidate to merge with.
        # We abort, with various messages for various cases.
        if bheads:
            msg, hint = msgdestmerge[b'bookmarkedheads'][action]
        elif len(repo.heads()) > 1:
            msg, hint = msgdestmerge[b'nootherbranchheads'][action]
            msg %= branch
        elif not onhead:
            # if 'onheadcheck == False' (rebase case),
            # this was not caught in Case A.
            msg, hint = msgdestmerge[b'nootherheadsbehind'][action]
        else:
            msg, hint = msgdestmerge[b'nootherheads'][action]
        raise error.NoMergeDestAbort(msg, hint=hint)
    else:
        node = nbhs[0]
    assert node is not None
    return node


def destmerge(
    repo, action=b'merge', sourceset=None, onheadcheck=True, destspace=None
):
    """return the default destination for a merge

    (or raise exception about why it can't pick one)

    :action: the action being performed, controls emitted error message
    """
    # destspace is here to work around issues with `hg pull --rebase` see
    # issue5214 for details
    if repo._activebookmark:
        node = _destmergebook(
            repo, action=action, sourceset=sourceset, destspace=destspace
        )
    else:
        node = _destmergebranch(
            repo,
            action=action,
            sourceset=sourceset,
            onheadcheck=onheadcheck,
            destspace=destspace,
        )
    return repo[node].rev()


def desthistedit(ui, repo):
    """Default base revision to edit for `hg histedit`."""
    default = ui.config(b'histedit', b'defaultrev')

    if default is None:
        revs = stack.getstack(repo)
    elif default:
        revs = scmutil.revrange(repo, [default])
    else:
        raise error.ConfigError(
            _(b"config option histedit.defaultrev can't be empty")
        )

    if revs:
        # Take the first revision of the revset as the root
        return revs.min()

    return None


def stackbase(ui, repo):
    revs = stack.getstack(repo)
    return revs.first() if revs else None


def _statusotherbook(ui, repo):
    bmheads = bookmarks.headsforactive(repo)
    curhead = repo._bookmarks[repo._activebookmark]
    if repo.revs(b'%n and parents()', curhead):
        # we are on the active bookmark
        bmheads = [b for b in bmheads if curhead != b]
        if bmheads:
            msg = _(b'%i other divergent bookmarks for "%s"\n')
            ui.status(msg % (len(bmheads), repo._activebookmark))


def _statusotherbranchheads(ui, repo):
    currentbranch = repo.dirstate.branch()
    allheads = repo.branchheads(currentbranch, closed=True)
    heads = repo.branchheads(currentbranch)
    if repo.revs(b'%ln and parents()', allheads):
        # we are on a head, even though it might be closed
        #
        #  on closed otherheads
        #  ========= ==========
        #      o        0       all heads for current branch are closed
        #               N       only descendant branch heads are closed
        #      x        0       there is only one non-closed branch head
        #               N       there are some non-closed branch heads
        #  ========= ==========
        otherheads = repo.revs(b'%ln - parents()', heads)
        if repo[b'.'].closesbranch():
            ui.warn(
                _(
                    b'no open descendant heads on branch "%s", '
                    b'updating to a closed head\n'
                )
                % currentbranch
            )
            if otherheads:
                ui.warn(
                    _(
                        b"(committing will reopen the head, "
                        b"use 'hg heads .' to see %i other heads)\n"
                    )
                    % (len(otherheads))
                )
            else:
                ui.warn(
                    _(b'(committing will reopen branch "%s")\n') % currentbranch
                )
        elif otherheads:
            curhead = repo[b'.']
            ui.status(
                _(b'updated to "%s: %s"\n')
                % (curhead, curhead.description().split(b'\n')[0])
            )
            ui.status(
                _(b'%i other heads for branch "%s"\n')
                % (len(otherheads), currentbranch)
            )


def statusotherdests(ui, repo):
    """Print message about other head"""
    # XXX we should probably include a hint:
    # - about what to do
    # - how to see such heads
    if repo._activebookmark:
        _statusotherbook(ui, repo)
    else:
        _statusotherbranchheads(ui, repo)
