from __future__ import absolute_import

from .i18n import _
from .pycompat import getattr
from . import (
    bookmarks as bookmarksmod,
    cmdutil,
    error,
    hg,
    lock as lockmod,
    mergestate as mergestatemod,
    pycompat,
    registrar,
    repair,
    scmutil,
    util,
)

release = lockmod.release

cmdtable = {}
command = registrar.command(cmdtable)


def checklocalchanges(repo, force=False):
    s = repo.status()
    if not force:
        cmdutil.checkunfinished(repo)
        cmdutil.bailifchanged(repo)
    else:
        cmdutil.checkunfinished(repo, skipmerge=True)
    return s


def _findupdatetarget(repo, nodes):
    unode, p2 = repo.changelog.parents(nodes[0])
    currentbranch = repo[None].branch()

    if (
        util.safehasattr(repo, b'mq')
        and p2 != repo.nullid
        and p2 in [x.node for x in repo.mq.applied]
    ):
        unode = p2
    elif currentbranch != repo[unode].branch():
        pwdir = b'parents(wdir())'
        revset = b'max(((parents(%ln::%r) + %r) - %ln::%r) and branch(%s))'
        branchtarget = repo.revs(
            revset, nodes, pwdir, pwdir, nodes, pwdir, currentbranch
        )
        if branchtarget:
            cl = repo.changelog
            unode = cl.node(branchtarget.first())

    return unode


def strip(
    ui,
    repo,
    revs,
    update=True,
    backup=True,
    force=None,
    bookmarks=None,
    soft=False,
):
    with repo.wlock(), repo.lock():

        if update:
            checklocalchanges(repo, force=force)
            urev = _findupdatetarget(repo, revs)
            hg.clean(repo, urev)
            repo.dirstate.write(repo.currenttransaction())

        if soft:
            repair.softstrip(ui, repo, revs, backup)
        else:
            repair.strip(ui, repo, revs, backup)

        repomarks = repo._bookmarks
        if bookmarks:
            with repo.transaction(b'strip') as tr:
                if repo._activebookmark in bookmarks:
                    bookmarksmod.deactivate(repo)
                repomarks.applychanges(repo, tr, [(b, None) for b in bookmarks])
            for bookmark in sorted(bookmarks):
                ui.write(_(b"bookmark '%s' deleted\n") % bookmark)


@command(
    b"debugstrip",
    [
        (
            b'r',
            b'rev',
            [],
            _(
                b'strip specified revision (optional, '
                b'can specify revisions without this '
                b'option)'
            ),
            _(b'REV'),
        ),
        (
            b'f',
            b'force',
            None,
            _(
                b'force removal of changesets, discard '
                b'uncommitted changes (no backup)'
            ),
        ),
        (b'', b'no-backup', None, _(b'do not save backup bundle')),
        (
            b'',
            b'nobackup',
            None,
            _(b'do not save backup bundle (DEPRECATED)'),
        ),
        (b'n', b'', None, _(b'ignored  (DEPRECATED)')),
        (
            b'k',
            b'keep',
            None,
            _(b"do not modify working directory during strip"),
        ),
        (
            b'B',
            b'bookmark',
            [],
            _(b"remove revs only reachable from given bookmark"),
            _(b'BOOKMARK'),
        ),
        (
            b'',
            b'soft',
            None,
            _(b"simply drop changesets from visible history (EXPERIMENTAL)"),
        ),
    ],
    _(b'hg debugstrip [-k] [-f] [-B bookmark] [-r] REV...'),
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def debugstrip(ui, repo, *revs, **opts):
    """strip changesets and all their descendants from the repository

    The strip command removes the specified changesets and all their
    descendants. If the working directory has uncommitted changes, the
    operation is aborted unless the --force flag is supplied, in which
    case changes will be discarded.

    If a parent of the working directory is stripped, then the working
    directory will automatically be updated to the most recent
    available ancestor of the stripped parent after the operation
    completes.

    Any stripped changesets are stored in ``.hg/strip-backup`` as a
    bundle (see :hg:`help bundle` and :hg:`help unbundle`). They can
    be restored by running :hg:`unbundle .hg/strip-backup/BUNDLE`,
    where BUNDLE is the bundle file created by the strip. Note that
    the local revision numbers will in general be different after the
    restore.

    Use the --no-backup option to discard the backup bundle once the
    operation completes.

    Strip is not a history-rewriting operation and can be used on
    changesets in the public phase. But if the stripped changesets have
    been pushed to a remote repository you will likely pull them again.

    Return 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    backup = True
    if opts.get(b'no_backup') or opts.get(b'nobackup'):
        backup = False

    cl = repo.changelog
    revs = list(revs) + opts.get(b'rev')
    revs = set(scmutil.revrange(repo, revs))

    with repo.wlock():
        bookmarks = set(opts.get(b'bookmark'))
        if bookmarks:
            repomarks = repo._bookmarks
            if not bookmarks.issubset(repomarks):
                raise error.Abort(
                    _(b"bookmark '%s' not found")
                    % b','.join(sorted(bookmarks - set(repomarks.keys())))
                )

            # If the requested bookmark is not the only one pointing to a
            # a revision we have to only delete the bookmark and not strip
            # anything. revsets cannot detect that case.
            nodetobookmarks = {}
            for mark, node in pycompat.iteritems(repomarks):
                nodetobookmarks.setdefault(node, []).append(mark)
            for marks in nodetobookmarks.values():
                if bookmarks.issuperset(marks):
                    rsrevs = scmutil.bookmarkrevs(repo, marks[0])
                    revs.update(set(rsrevs))
            if not revs:
                with repo.lock(), repo.transaction(b'bookmark') as tr:
                    bmchanges = [(b, None) for b in bookmarks]
                    repomarks.applychanges(repo, tr, bmchanges)
                for bookmark in sorted(bookmarks):
                    ui.write(_(b"bookmark '%s' deleted\n") % bookmark)

        if not revs:
            raise error.Abort(_(b'empty revision set'))

        descendants = set(cl.descendants(revs))
        strippedrevs = revs.union(descendants)
        roots = revs.difference(descendants)

        # if one of the wdir parent is stripped we'll need
        # to update away to an earlier revision
        update = any(
            p != repo.nullid and cl.rev(p) in strippedrevs
            for p in repo.dirstate.parents()
        )

        rootnodes = {cl.node(r) for r in roots}

        q = getattr(repo, 'mq', None)
        if q is not None and q.applied:
            # refresh queue state if we're about to strip
            # applied patches
            if cl.rev(repo.lookup(b'qtip')) in strippedrevs:
                q.applieddirty = True
                start = 0
                end = len(q.applied)
                for i, statusentry in enumerate(q.applied):
                    if statusentry.node in rootnodes:
                        # if one of the stripped roots is an applied
                        # patch, only part of the queue is stripped
                        start = i
                        break
                del q.applied[start:end]
                q.savedirty()

        revs = sorted(rootnodes)
        if update and opts.get(b'keep'):
            urev = _findupdatetarget(repo, revs)
            uctx = repo[urev]

            # only reset the dirstate for files that would actually change
            # between the working context and uctx
            descendantrevs = repo.revs(b"only(., %d)", uctx.rev())
            changedfiles = []
            for rev in descendantrevs:
                # blindly reset the files, regardless of what actually changed
                changedfiles.extend(repo[rev].files())

            # reset files that only changed in the dirstate too
            dirstate = repo.dirstate
            dirchanges = [f for f in dirstate if dirstate[f] != b'n']
            changedfiles.extend(dirchanges)

            repo.dirstate.rebuild(urev, uctx.manifest(), changedfiles)
            repo.dirstate.write(repo.currenttransaction())

            # clear resolve state
            mergestatemod.mergestate.clean(repo)

            update = False

        strip(
            ui,
            repo,
            revs,
            backup=backup,
            update=update,
            force=opts.get(b'force'),
            bookmarks=bookmarks,
            soft=opts[b'soft'],
        )

    return 0
