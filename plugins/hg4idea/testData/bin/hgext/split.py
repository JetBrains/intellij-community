# split.py - split a changeset into smaller ones
#
# Copyright 2015 Laurent Charignon <lcharignon@fb.com>
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""command to split a changeset into smaller ones (EXPERIMENTAL)"""

from __future__ import absolute_import

from mercurial.i18n import _

from mercurial.node import (
    nullrev,
    short,
)

from mercurial import (
    bookmarks,
    cmdutil,
    commands,
    error,
    hg,
    pycompat,
    registrar,
    revsetlang,
    rewriteutil,
    scmutil,
    util,
)

# allow people to use split without explicitly enabling rebase extension
from . import rebase

cmdtable = {}
command = registrar.command(cmdtable)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


@command(
    b'split',
    [
        (b'r', b'rev', b'', _(b"revision to split"), _(b'REV')),
        (b'', b'rebase', True, _(b'rebase descendants after split')),
    ]
    + cmdutil.commitopts2,
    _(b'hg split [--no-rebase] [[-r] REV]'),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
    helpbasic=True,
)
def split(ui, repo, *revs, **opts):
    """split a changeset into smaller ones

    Repeatedly prompt changes and commit message for new changesets until there
    is nothing left in the original changeset.

    If --rev was not given, split the working directory parent.

    By default, rebase connected non-obsoleted descendants onto the new
    changeset. Use --no-rebase to avoid the rebase.
    """
    opts = pycompat.byteskwargs(opts)
    revlist = []
    if opts.get(b'rev'):
        revlist.append(opts.get(b'rev'))
    revlist.extend(revs)
    with repo.wlock(), repo.lock():
        tr = repo.transaction(b'split')
        # If the rebase somehow runs into conflicts, make sure
        # we close the transaction so the user can continue it.
        with util.acceptintervention(tr):
            revs = scmutil.revrange(repo, revlist or [b'.'])
            if len(revs) > 1:
                raise error.InputError(_(b'cannot split multiple revisions'))

            rev = revs.first()
            # Handle nullrev specially here (instead of leaving for precheck()
            # below) so we get a nicer message and error code.
            if rev is None or rev == nullrev:
                ui.status(_(b'nothing to split\n'))
                return 1
            ctx = repo[rev]
            if ctx.node() is None:
                raise error.InputError(_(b'cannot split working directory'))

            if opts.get(b'rebase'):
                # Skip obsoleted descendants and their descendants so the rebase
                # won't cause conflicts for sure.
                descendants = list(repo.revs(b'(%d::) - (%d)', rev, rev))
                torebase = list(
                    repo.revs(
                        b'%ld - (%ld & obsolete())::', descendants, descendants
                    )
                )
            else:
                torebase = []
            rewriteutil.precheck(repo, [rev] + torebase, b'split')

            if len(ctx.parents()) > 1:
                raise error.InputError(_(b'cannot split a merge changeset'))

            cmdutil.bailifchanged(repo)

            # Deactivate bookmark temporarily so it won't get moved
            # unintentionally
            bname = repo._activebookmark
            if bname and repo._bookmarks[bname] != ctx.node():
                bookmarks.deactivate(repo)

            wnode = repo[b'.'].node()
            top = None
            try:
                top = dosplit(ui, repo, tr, ctx, opts)
            finally:
                # top is None: split failed, need update --clean recovery.
                # wnode == ctx.node(): wnode split, no need to update.
                if top is None or wnode != ctx.node():
                    hg.clean(repo, wnode, show_stats=False)
                if bname:
                    bookmarks.activate(repo, bname)
            if torebase and top:
                dorebase(ui, repo, torebase, top)


def dosplit(ui, repo, tr, ctx, opts):
    committed = []  # [ctx]

    # Set working parent to ctx.p1(), and keep working copy as ctx's content
    if ctx.node() != repo.dirstate.p1():
        hg.clean(repo, ctx.node(), show_stats=False)
    with repo.dirstate.parentchange():
        scmutil.movedirstate(repo, ctx.p1())

    # Any modified, added, removed, deleted result means split is incomplete
    def incomplete(repo):
        st = repo.status()
        return any((st.modified, st.added, st.removed, st.deleted))

    # Main split loop
    while incomplete(repo):
        if committed:
            header = _(
                b'HG: Splitting %s. So far it has been split into:\n'
            ) % short(ctx.node())
            # We don't want color codes in the commit message template, so
            # disable the label() template function while we render it.
            with ui.configoverride(
                {(b'templatealias', b'label(l,x)'): b"x"}, b'split'
            ):
                for c in committed:
                    summary = cmdutil.format_changeset_summary(ui, c, b'split')
                    header += _(b'HG: - %s\n') % summary
            header += _(
                b'HG: Write commit message for the next split changeset.\n'
            )
        else:
            header = _(
                b'HG: Splitting %s. Write commit message for the '
                b'first split changeset.\n'
            ) % short(ctx.node())
        opts.update(
            {
                b'edit': True,
                b'interactive': True,
                b'message': header + ctx.description(),
            }
        )
        origctx = repo[b'.']
        commands.commit(ui, repo, **pycompat.strkwargs(opts))
        newctx = repo[b'.']
        # Ensure user didn't do a "no-op" split (such as deselecting
        # everything).
        if origctx.node() != newctx.node():
            committed.append(newctx)

    if not committed:
        raise error.InputError(_(b'cannot split an empty revision'))

    if len(committed) != 1 or committed[0].node() != ctx.node():
        # Ensure we don't strip a node if we produce the same commit as already
        # exists
        scmutil.cleanupnodes(
            repo,
            {ctx.node(): [c.node() for c in committed]},
            operation=b'split',
            fixphase=True,
        )

    return committed[-1]


def dorebase(ui, repo, src, destctx):
    rebase.rebase(
        ui,
        repo,
        rev=[revsetlang.formatspec(b'%ld', src)],
        dest=revsetlang.formatspec(b'%d', destctx.rev()),
    )
