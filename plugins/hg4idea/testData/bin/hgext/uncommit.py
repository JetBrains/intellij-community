# uncommit - undo the actions of a commit
#
# Copyright 2011 Peter Arrenbrecht <peter.arrenbrecht@gmail.com>
#                Logilab SA        <contact@logilab.fr>
#                Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#                Patrick Mezard <patrick@mezard.eu>
# Copyright 2016 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""uncommit part or all of a local changeset (EXPERIMENTAL)

This command undoes the effect of a local commit, returning the affected
files to their uncommitted state. This means that files modified, added or
removed in the changeset will be left unchanged, and so will remain modified,
added and removed in the working directory.
"""


from mercurial.i18n import _

from mercurial import (
    cmdutil,
    commands,
    context,
    copies as copiesmod,
    error,
    obsutil,
    pathutil,
    pycompat,
    registrar,
    rewriteutil,
    scmutil,
)

cmdtable = {}
command = registrar.command(cmdtable)

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'experimental',
    b'uncommitondirtywdir',
    default=False,
)
configitem(
    b'experimental',
    b'uncommit.keep',
    default=False,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


def _commitfiltered(
    repo, ctx, match, keepcommit, message=None, user=None, date=None
):
    """Recommit ctx with changed files not in match. Return the new
    node identifier, or None if nothing changed.
    """
    base = ctx.p1()
    # ctx
    initialfiles = set(ctx.files())
    exclude = {f for f in initialfiles if match(f)}

    # No files matched commit, so nothing excluded
    if not exclude:
        return None

    # return the p1 so that we don't create an obsmarker later
    if not keepcommit:
        return ctx.p1().node()

    files = initialfiles - exclude
    # Filter copies
    copied = copiesmod.pathcopies(base, ctx)
    copied = {dst: src for dst, src in copied.items() if dst in files}

    def filectxfn(repo, memctx, path, contentctx=ctx, redirect=()):
        if path not in contentctx:
            return None
        fctx = contentctx[path]
        mctx = context.memfilectx(
            repo,
            memctx,
            fctx.path(),
            fctx.data(),
            fctx.islink(),
            fctx.isexec(),
            copysource=copied.get(path),
        )
        return mctx

    if not files:
        repo.ui.status(_(b"note: keeping empty commit\n"))

    if message is None:
        message = ctx.description()
    if not user:
        user = ctx.user()
    if not date:
        date = ctx.date()

    new = context.memctx(
        repo,
        parents=[base.node(), repo.nullid],
        text=message,
        files=files,
        filectxfn=filectxfn,
        user=user,
        date=date,
        extra=ctx.extra(),
    )
    return repo.commitctx(new)


@command(
    b'uncommit',
    [
        (b'', b'keep', None, _(b'allow an empty commit after uncommitting')),
        (
            b'',
            b'allow-dirty-working-copy',
            False,
            _(b'allow uncommit with outstanding changes'),
        ),
        (b'n', b'note', b'', _(b'store a note on uncommit'), _(b'TEXT')),
    ]
    + commands.walkopts
    + commands.commitopts
    + commands.commitopts2
    + commands.commitopts3,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
)
def uncommit(ui, repo, *pats, **opts):
    """uncommit part or all of a local changeset

    This command undoes the effect of a local commit, returning the affected
    files to their uncommitted state. This means that files modified or
    deleted in the changeset will be left unchanged, and so will remain
    modified in the working directory.

    If no files are specified, the commit will be pruned, unless --keep is
    given.
    """
    cmdutil.check_note_size(opts)
    cmdutil.resolve_commit_options(ui, opts)

    with repo.wlock(), repo.lock():

        st = repo.status()
        m, a, r, d = st.modified, st.added, st.removed, st.deleted
        isdirtypath = any(set(m + a + r + d) & set(pats))
        allowdirtywcopy = opts[
            'allow_dirty_working_copy'
        ] or repo.ui.configbool(b'experimental', b'uncommitondirtywdir')
        if not allowdirtywcopy and (not pats or isdirtypath):
            cmdutil.bailifchanged(
                repo,
                hint=_(b'requires --allow-dirty-working-copy to uncommit'),
            )
        old = repo[b'.']
        rewriteutil.precheck(repo, [old.rev()], b'uncommit')
        if len(old.parents()) > 1:
            raise error.InputError(_(b"cannot uncommit merge changeset"))

        match = scmutil.match(old, pats, pycompat.byteskwargs(opts))

        # Check all explicitly given files; abort if there's a problem.
        if match.files():
            s = old.status(old.p1(), match, listclean=True)
            eligible = set(s.added) | set(s.modified) | set(s.removed)

            badfiles = set(match.files()) - eligible

            # Naming a parent directory of an eligible file is OK, even
            # if not everything tracked in that directory can be
            # uncommitted.
            if badfiles:
                badfiles -= {f for f in pathutil.dirs(eligible)}

            for f in sorted(badfiles):
                if f in s.clean:
                    hint = _(
                        b"file was not changed in working directory parent"
                    )
                elif repo.wvfs.exists(f):
                    hint = _(b"file was untracked in working directory parent")
                else:
                    hint = _(b"file does not exist")

                raise error.InputError(
                    _(b'cannot uncommit "%s"') % scmutil.getuipathfn(repo)(f),
                    hint=hint,
                )

        with repo.transaction(b'uncommit'):
            if not (opts['message'] or opts['logfile']):
                opts['message'] = old.description()
            message = cmdutil.logmessage(ui, pycompat.byteskwargs(opts))

            keepcommit = pats
            if not keepcommit:
                if opts.get('keep') is not None:
                    keepcommit = opts.get('keep')
                else:
                    keepcommit = ui.configbool(
                        b'experimental', b'uncommit.keep'
                    )
            newid = _commitfiltered(
                repo,
                old,
                match,
                keepcommit,
                message=message,
                user=opts.get('user'),
                date=opts.get('date'),
            )
            if newid is None:
                ui.status(_(b"nothing to uncommit\n"))
                return 1

            mapping = {}
            if newid != old.p1().node():
                # Move local changes on filtered changeset
                mapping[old.node()] = (newid,)
            else:
                # Fully removed the old commit
                mapping[old.node()] = ()

            with repo.dirstate.changing_parents(repo):
                scmutil.movedirstate(repo, repo[newid], match)

            scmutil.cleanupnodes(repo, mapping, b'uncommit', fixphase=True)


def predecessormarkers(ctx):
    """yields the obsolete markers marking the given changeset as a successor"""
    for data in ctx.repo().obsstore.predecessors.get(ctx.node(), ()):
        yield obsutil.marker(ctx.repo(), data)


@command(
    b'unamend',
    [],
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
    helpbasic=True,
)
def unamend(ui, repo, **opts):
    """undo the most recent amend operation on a current changeset

    This command will roll back to the previous version of a changeset,
    leaving working directory in state in which it was before running
    `hg amend` (e.g. files modified as part of an amend will be
    marked as modified `hg status`)
    """

    unfi = repo.unfiltered()
    with repo.wlock(), repo.lock(), repo.transaction(b'unamend'):

        # identify the commit from which to unamend
        curctx = repo[b'.']

        rewriteutil.precheck(repo, [curctx.rev()], b'unamend')
        if len(curctx.parents()) > 1:
            raise error.InputError(_(b"cannot unamend merge changeset"))

        expected_keys = (b'amend_source', b'unamend_source')
        if not any(key in curctx.extra() for key in expected_keys):
            raise error.InputError(
                _(
                    b"working copy parent was not created by 'hg amend' or "
                    b"'hg unamend'"
                )
            )

        # identify the commit to which to unamend
        markers = list(predecessormarkers(curctx))
        if len(markers) != 1:
            e = _(b"changeset must have one predecessor, found %i predecessors")
            raise error.InputError(e % len(markers))

        prednode = markers[0].prednode()
        predctx = unfi[prednode]

        # add an extra so that we get a new hash
        # note: allowing unamend to undo an unamend is an intentional feature
        extras = predctx.extra()
        extras[b'unamend_source'] = curctx.hex()

        def filectxfn(repo, ctx_, path):
            try:
                return predctx.filectx(path)
            except KeyError:
                return None

        # Make a new commit same as predctx
        newctx = context.memctx(
            repo,
            parents=(predctx.p1(), predctx.p2()),
            text=predctx.description(),
            files=predctx.files(),
            filectxfn=filectxfn,
            user=predctx.user(),
            date=predctx.date(),
            extra=extras,
        )
        newprednode = repo.commitctx(newctx)
        newpredctx = repo[newprednode]
        dirstate = repo.dirstate

        with dirstate.changing_parents(repo):
            scmutil.movedirstate(repo, newpredctx)

        mapping = {curctx.node(): (newprednode,)}
        scmutil.cleanupnodes(repo, mapping, b'unamend', fixphase=True)
