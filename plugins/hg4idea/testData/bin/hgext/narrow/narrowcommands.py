# narrowcommands.py - command modifications for narrowhg extension
#
# Copyright 2017 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import itertools
import os

from mercurial.i18n import _
from mercurial.node import (
    hex,
    short,
)
from mercurial import (
    bundle2,
    cmdutil,
    commands,
    discovery,
    encoding,
    error,
    exchange,
    extensions,
    hg,
    narrowspec,
    pathutil,
    pycompat,
    registrar,
    repair,
    repoview,
    requirements,
    sparse,
    util,
    wireprototypes,
)
from mercurial.utils import (
    urlutil,
)

table = {}
command = registrar.command(table)


def setup():
    """Wraps user-facing mercurial commands with narrow-aware versions."""

    entry = extensions.wrapcommand(commands.table, b'clone', clonenarrowcmd)
    entry[1].append(
        (b'', b'narrow', None, _(b"create a narrow clone of select files"))
    )
    entry[1].append(
        (
            b'',
            b'depth',
            b'',
            _(b"limit the history fetched by distance from heads"),
        )
    )
    entry[1].append((b'', b'narrowspec', b'', _(b"read narrowspecs from file")))
    # TODO(durin42): unify sparse/narrow --include/--exclude logic a bit
    if b'sparse' not in extensions.enabled():
        entry[1].append(
            (b'', b'include', [], _(b"specifically fetch this file/directory"))
        )
        entry[1].append(
            (
                b'',
                b'exclude',
                [],
                _(b"do not fetch this file/directory, even if included"),
            )
        )

    entry = extensions.wrapcommand(commands.table, b'pull', pullnarrowcmd)
    entry[1].append(
        (
            b'',
            b'depth',
            b'',
            _(b"limit the history fetched by distance from heads"),
        )
    )

    extensions.wrapcommand(commands.table, b'archive', archivenarrowcmd)


def clonenarrowcmd(orig, ui, repo, *args, **opts):
    """Wraps clone command, so 'hg clone' first wraps localrepo.clone()."""
    wrappedextraprepare = util.nullcontextmanager()
    narrowspecfile = opts['narrowspec']

    if narrowspecfile:
        filepath = os.path.join(encoding.getcwd(), narrowspecfile)
        ui.status(_(b"reading narrowspec from '%s'\n") % filepath)
        try:
            fdata = util.readfile(filepath)
        except IOError as inst:
            raise error.Abort(
                _(b"cannot read narrowspecs from '%s': %s")
                % (filepath, encoding.strtolocal(inst.strerror))
            )

        includes, excludes, profiles = sparse.parseconfig(ui, fdata, b'narrow')
        if profiles:
            raise error.ConfigError(
                _(
                    b"cannot specify other files using '%include' in"
                    b" narrowspec"
                )
            )

        narrowspec.validatepatterns(includes)
        narrowspec.validatepatterns(excludes)

        # narrowspec is passed so we should assume that user wants narrow clone
        opts['narrow'] = True
        opts['include'].extend(includes)
        opts['exclude'].extend(excludes)

    if opts['narrow']:

        def pullbundle2extraprepare_widen(orig, pullop, kwargs):
            orig(pullop, kwargs)

            if opts.get('depth'):
                # TODO: fix exchange._pullbundle2extraprepare()
                kwargs[b'depth'] = opts['depth']

        wrappedextraprepare = extensions.wrappedfunction(
            exchange, '_pullbundle2extraprepare', pullbundle2extraprepare_widen
        )

    with wrappedextraprepare:
        return orig(ui, repo, *args, **opts)


def pullnarrowcmd(orig, ui, repo, *args, **opts):
    """Wraps pull command to allow modifying narrow spec."""
    wrappedextraprepare = util.nullcontextmanager()
    if requirements.NARROW_REQUIREMENT in repo.requirements:

        def pullbundle2extraprepare_widen(orig, pullop, kwargs):
            orig(pullop, kwargs)
            if opts.get('depth'):
                kwargs[b'depth'] = opts['depth']

        wrappedextraprepare = extensions.wrappedfunction(
            exchange, '_pullbundle2extraprepare', pullbundle2extraprepare_widen
        )

    with wrappedextraprepare:
        return orig(ui, repo, *args, **opts)


def archivenarrowcmd(orig, ui, repo, *args, **opts):
    """Wraps archive command to narrow the default includes."""
    if requirements.NARROW_REQUIREMENT in repo.requirements:
        repo_includes, repo_excludes = repo.narrowpats
        includes = set(opts.get('include', []))
        excludes = set(opts.get('exclude', []))
        includes, excludes, unused_invalid = narrowspec.restrictpatterns(
            includes, excludes, repo_includes, repo_excludes
        )
        if includes:
            opts['include'] = includes
        if excludes:
            opts['exclude'] = excludes
    return orig(ui, repo, *args, **opts)


def pullbundle2extraprepare(orig, pullop, kwargs):
    repo = pullop.repo
    if requirements.NARROW_REQUIREMENT not in repo.requirements:
        return orig(pullop, kwargs)

    if wireprototypes.NARROWCAP not in pullop.remote.capabilities():
        raise error.Abort(_(b"server does not support narrow clones"))
    orig(pullop, kwargs)
    kwargs[b'narrow'] = True
    include, exclude = repo.narrowpats
    kwargs[b'oldincludepats'] = include
    kwargs[b'oldexcludepats'] = exclude
    if include:
        kwargs[b'includepats'] = include
    if exclude:
        kwargs[b'excludepats'] = exclude
    # calculate known nodes only in ellipses cases because in non-ellipses cases
    # we have all the nodes
    if wireprototypes.ELLIPSESCAP1 in pullop.remote.capabilities():
        kwargs[b'known'] = [
            hex(ctx.node())
            for ctx in repo.set(b'::%ln', pullop.common)
            if ctx.node() != repo.nullid
        ]
        if not kwargs[b'known']:
            # Mercurial serializes an empty list as '' and deserializes it as
            # [''], so delete it instead to avoid handling the empty string on
            # the server.
            del kwargs[b'known']


extensions.wrapfunction(
    exchange, '_pullbundle2extraprepare', pullbundle2extraprepare
)


def _narrow(
    ui,
    repo,
    remote,
    commoninc,
    oldincludes,
    oldexcludes,
    newincludes,
    newexcludes,
    force,
    backup,
):
    oldmatch = narrowspec.match(repo.root, oldincludes, oldexcludes)
    newmatch = narrowspec.match(repo.root, newincludes, newexcludes)

    # This is essentially doing "hg outgoing" to find all local-only
    # commits. We will then check that the local-only commits don't
    # have any changes to files that will be untracked.
    unfi = repo.unfiltered()
    outgoing = discovery.findcommonoutgoing(unfi, remote, commoninc=commoninc)
    ui.status(_(b'looking for local changes to affected paths\n'))
    progress = ui.makeprogress(
        topic=_(b'changesets'),
        unit=_(b'changesets'),
        total=len(outgoing.missing) + len(outgoing.excluded),
    )
    localnodes = []
    with progress:
        for n in itertools.chain(outgoing.missing, outgoing.excluded):
            progress.increment()
            if any(oldmatch(f) and not newmatch(f) for f in unfi[n].files()):
                localnodes.append(n)
    revstostrip = unfi.revs(b'descendants(%ln)', localnodes)
    hiddenrevs = repoview.filterrevs(repo, b'visible')
    visibletostrip = list(
        repo.changelog.node(r) for r in (revstostrip - hiddenrevs)
    )
    if visibletostrip:
        ui.status(
            _(
                b'The following changeset(s) or their ancestors have '
                b'local changes not on the remote:\n'
            )
        )
        maxnodes = 10
        if ui.verbose or len(visibletostrip) <= maxnodes:
            for n in visibletostrip:
                ui.status(b'%s\n' % short(n))
        else:
            for n in visibletostrip[:maxnodes]:
                ui.status(b'%s\n' % short(n))
            ui.status(
                _(b'...and %d more, use --verbose to list all\n')
                % (len(visibletostrip) - maxnodes)
            )
        if not force:
            raise error.StateError(
                _(b'local changes found'),
                hint=_(b'use --force-delete-local-changes to ignore'),
            )

    with ui.uninterruptible():
        if revstostrip:
            tostrip = [unfi.changelog.node(r) for r in revstostrip]
            if repo[b'.'].node() in tostrip:
                # stripping working copy, so move to a different commit first
                urev = max(
                    repo.revs(
                        b'(::%n) - %ln + null',
                        repo[b'.'].node(),
                        visibletostrip,
                    )
                )
                hg.clean(repo, urev)
            overrides = {(b'devel', b'strip-obsmarkers'): False}
            if backup:
                ui.status(_(b'moving unwanted changesets to backup\n'))
            else:
                ui.status(_(b'deleting unwanted changesets\n'))
            with ui.configoverride(overrides, b'narrow'):
                repair.strip(ui, unfi, tostrip, topic=b'narrow', backup=backup)

        todelete = []
        for entry in repo.store.data_entries():
            if not entry.is_revlog:
                continue
            if entry.is_filelog:
                if not newmatch(entry.target_id):
                    for file_ in entry.files():
                        todelete.append(file_.unencoded_path)
            elif entry.is_manifestlog:
                dir = entry.target_id[:-1]
                dirs = sorted(pathutil.dirs({dir})) + [dir]
                include = True
                for d in dirs:
                    visit = newmatch.visitdir(d)
                    if not visit:
                        include = False
                        break
                    if visit == b'all':
                        break
                if not include:
                    for file_ in entry.files():
                        todelete.append(file_.unencoded_path)

        repo.destroying()

        with repo.transaction(b'narrowing'):
            # Update narrowspec before removing revlogs, so repo won't be
            # corrupt in case of crash
            repo.setnarrowpats(newincludes, newexcludes)

            for f in todelete:
                ui.status(_(b'deleting %s\n') % f)
                util.unlinkpath(repo.svfs.join(f))
                repo.store.markremoved(f)

            ui.status(_(b'deleting unwanted files from working copy\n'))
            with repo.dirstate.changing_parents(repo):
                narrowspec.updateworkingcopy(repo, assumeclean=True)
                narrowspec.copytoworkingcopy(repo)

        repo.destroyed()


def _widen(
    ui,
    repo,
    remote,
    commoninc,
    oldincludes,
    oldexcludes,
    newincludes,
    newexcludes,
):
    # for now we assume that if a server has ellipses enabled, we will be
    # exchanging ellipses nodes. In future we should add ellipses as a client
    # side requirement (maybe) to distinguish a client is shallow or not and
    # then send that information to server whether we want ellipses or not.
    # Theoretically a non-ellipses repo should be able to use narrow
    # functionality from an ellipses enabled server
    remotecap = remote.capabilities()
    ellipsesremote = any(
        cap in remotecap for cap in wireprototypes.SUPPORTED_ELLIPSESCAP
    )

    # check whether we are talking to a server which supports old version of
    # ellipses capabilities
    isoldellipses = (
        ellipsesremote
        and wireprototypes.ELLIPSESCAP1 in remotecap
        and wireprototypes.ELLIPSESCAP not in remotecap
    )

    def pullbundle2extraprepare_widen(orig, pullop, kwargs):
        orig(pullop, kwargs)
        # The old{in,ex}cludepats have already been set by orig()
        kwargs[b'includepats'] = newincludes
        kwargs[b'excludepats'] = newexcludes

    wrappedextraprepare = extensions.wrappedfunction(
        exchange, '_pullbundle2extraprepare', pullbundle2extraprepare_widen
    )

    # define a function that narrowbundle2 can call after creating the
    # backup bundle, but before applying the bundle from the server
    def setnewnarrowpats():
        repo.setnarrowpats(newincludes, newexcludes)

    repo.setnewnarrowpats = setnewnarrowpats
    # silence the devel-warning of applying an empty changegroup
    overrides = {(b'devel', b'all-warnings'): False}

    common = commoninc[0]
    with ui.uninterruptible():
        if ellipsesremote:
            ds = repo.dirstate
            p1, p2 = ds.p1(), ds.p2()
            with ds.changing_parents(repo):
                ds.setparents(repo.nullid, repo.nullid)
        if isoldellipses:
            with wrappedextraprepare:
                exchange.pull(repo, remote, heads=common)
        else:
            known = []
            if ellipsesremote:
                known = [
                    ctx.node()
                    for ctx in repo.set(b'::%ln', common)
                    if ctx.node() != repo.nullid
                ]
            with remote.commandexecutor() as e:
                bundle = e.callcommand(
                    b'narrow_widen',
                    {
                        b'oldincludes': oldincludes,
                        b'oldexcludes': oldexcludes,
                        b'newincludes': newincludes,
                        b'newexcludes': newexcludes,
                        b'cgversion': b'03',
                        b'commonheads': common,
                        b'known': known,
                        b'ellipses': ellipsesremote,
                    },
                ).result()

            trmanager = exchange.transactionmanager(
                repo, b'widen', remote.url()
            )
            with trmanager, repo.ui.configoverride(overrides, b'widen'):
                op = bundle2.bundleoperation(
                    repo, trmanager.transaction, source=b'widen'
                )
                # TODO: we should catch error.Abort here
                bundle2.processbundle(repo, bundle, op=op, remote=remote)

        if ellipsesremote:
            with ds.changing_parents(repo):
                ds.setparents(p1, p2)

        with repo.transaction(b'widening'), repo.dirstate.changing_parents(
            repo
        ):
            repo.setnewnarrowpats()
            narrowspec.updateworkingcopy(repo)
            narrowspec.copytoworkingcopy(repo)


# TODO(rdamazio): Make new matcher format and update description
@command(
    b'tracked',
    [
        (b'', b'addinclude', [], _(b'new paths to include')),
        (b'', b'removeinclude', [], _(b'old paths to no longer include')),
        (
            b'',
            b'auto-remove-includes',
            False,
            _(b'automatically choose unused includes to remove'),
        ),
        (b'', b'addexclude', [], _(b'new paths to exclude')),
        (b'', b'import-rules', b'', _(b'import narrowspecs from a file')),
        (b'', b'removeexclude', [], _(b'old paths to no longer exclude')),
        (
            b'',
            b'clear',
            False,
            _(b'whether to replace the existing narrowspec'),
        ),
        (
            b'',
            b'force-delete-local-changes',
            False,
            _(b'forces deletion of local changes when narrowing'),
        ),
        (
            b'',
            b'backup',
            True,
            _(b'back up local changes when narrowing'),
        ),
        (
            b'',
            b'update-working-copy',
            False,
            _(b'update working copy when the store has changed'),
        ),
    ]
    + commands.remoteopts,
    _(b'[OPTIONS]... [REMOTE]'),
    inferrepo=True,
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def trackedcmd(ui, repo, remotepath=None, *pats, **opts):
    """show or change the current narrowspec

    With no argument, shows the current narrowspec entries, one per line. Each
    line will be prefixed with 'I' or 'X' for included or excluded patterns,
    respectively.

    The narrowspec is comprised of expressions to match remote files and/or
    directories that should be pulled into your client.
    The narrowspec has *include* and *exclude* expressions, with excludes always
    trumping includes: that is, if a file matches an exclude expression, it will
    be excluded even if it also matches an include expression.
    Excluding files that were never included has no effect.

    Each included or excluded entry is in the format described by
    'hg help patterns'.

    The options allow you to add or remove included and excluded expressions.

    If --clear is specified, then all previous includes and excludes are DROPPED
    and replaced by the new ones specified to --addinclude and --addexclude.
    If --clear is specified without any further options, the narrowspec will be
    empty and will not match any files.

    If --auto-remove-includes is specified, then those includes that don't match
    any files modified by currently visible local commits (those not shared by
    the remote) will be added to the set of explicitly specified includes to
    remove.

    --import-rules accepts a path to a file containing rules, allowing you to
    add --addinclude, --addexclude rules in bulk. Like the other include and
    exclude switches, the changes are applied immediately.
    """
    if requirements.NARROW_REQUIREMENT not in repo.requirements:
        raise error.InputError(
            _(
                b'the tracked command is only supported on '
                b'repositories cloned with --narrow'
            )
        )

    # Before supporting, decide whether it "hg tracked --clear" should mean
    # tracking no paths or all paths.
    if opts['clear']:
        raise error.InputError(_(b'the --clear option is not yet supported'))

    # import rules from a file
    newrules = opts.get('import_rules')
    if newrules:
        filepath = os.path.join(encoding.getcwd(), newrules)
        try:
            fdata = util.readfile(filepath)
        except IOError as inst:
            raise error.StorageError(
                _(b"cannot read narrowspecs from '%s': %s")
                % (filepath, encoding.strtolocal(inst.strerror))
            )
        includepats, excludepats, profiles = sparse.parseconfig(
            ui, fdata, b'narrow'
        )
        if profiles:
            raise error.InputError(
                _(
                    b"including other spec files using '%include' "
                    b"is not supported in narrowspec"
                )
            )
        opts['addinclude'].extend(includepats)
        opts['addexclude'].extend(excludepats)

    addedincludes = narrowspec.parsepatterns(opts['addinclude'])
    removedincludes = narrowspec.parsepatterns(opts['removeinclude'])
    addedexcludes = narrowspec.parsepatterns(opts['addexclude'])
    removedexcludes = narrowspec.parsepatterns(opts['removeexclude'])
    autoremoveincludes = opts['auto_remove_includes']

    update_working_copy = opts['update_working_copy']
    only_show = not (
        addedincludes
        or removedincludes
        or addedexcludes
        or removedexcludes
        or newrules
        or autoremoveincludes
        or update_working_copy
    )

    # Only print the current narrowspec.
    if only_show:
        oldincludes, oldexcludes = repo.narrowpats
        ui.pager(b'tracked')
        fm = ui.formatter(b'narrow', pycompat.byteskwargs(opts))
        for i in sorted(oldincludes):
            fm.startitem()
            fm.write(b'status', b'%s ', b'I', label=b'narrow.included')
            fm.write(b'pat', b'%s\n', i, label=b'narrow.included')
        for i in sorted(oldexcludes):
            fm.startitem()
            fm.write(b'status', b'%s ', b'X', label=b'narrow.excluded')
            fm.write(b'pat', b'%s\n', i, label=b'narrow.excluded')
        fm.end()
        return 0

    with repo.wlock(), repo.lock():
        oldincludes, oldexcludes = repo.narrowpats

        # filter the user passed additions and deletions into actual additions and
        # deletions of excludes and includes
        addedincludes -= oldincludes
        removedincludes &= oldincludes
        addedexcludes -= oldexcludes
        removedexcludes &= oldexcludes

        widening = addedincludes or removedexcludes
        narrowing = removedincludes or addedexcludes

        if update_working_copy:
            with repo.transaction(b'narrow-wc'), repo.dirstate.changing_parents(
                repo
            ):
                narrowspec.updateworkingcopy(repo)
                narrowspec.copytoworkingcopy(repo)
            return 0

        if not (widening or narrowing or autoremoveincludes):
            ui.status(_(b"nothing to widen or narrow\n"))
            return 0

        cmdutil.bailifchanged(repo)

        # Find the revisions we have in common with the remote. These will
        # be used for finding local-only changes for narrowing. They will
        # also define the set of revisions to update for widening.
        path = urlutil.get_unique_pull_path_obj(b'tracked', ui, remotepath)
        ui.status(_(b'comparing with %s\n') % urlutil.hidepassword(path.loc))
        remote = hg.peer(repo, pycompat.byteskwargs(opts), path)

        try:
            # check narrow support before doing anything if widening needs to be
            # performed. In future we should also abort if client is ellipses and
            # server does not support ellipses
            if (
                widening
                and wireprototypes.NARROWCAP not in remote.capabilities()
            ):
                raise error.Abort(_(b"server does not support narrow clones"))

            commoninc = discovery.findcommonincoming(repo, remote)

            if autoremoveincludes:
                outgoing = discovery.findcommonoutgoing(
                    repo, remote, commoninc=commoninc
                )
                ui.status(_(b'looking for unused includes to remove\n'))
                localfiles = set()
                for n in itertools.chain(outgoing.missing, outgoing.excluded):
                    localfiles.update(repo[n].files())
                suggestedremovals = []
                for include in sorted(oldincludes):
                    match = narrowspec.match(repo.root, [include], oldexcludes)
                    if not any(match(f) for f in localfiles):
                        suggestedremovals.append(include)
                if suggestedremovals:
                    for s in suggestedremovals:
                        ui.status(b'%s\n' % s)
                    if (
                        ui.promptchoice(
                            _(
                                b'remove these unused includes (Yn)?'
                                b'$$ &Yes $$ &No'
                            )
                        )
                        == 0
                    ):
                        removedincludes.update(suggestedremovals)
                        narrowing = True
                else:
                    ui.status(_(b'found no unused includes\n'))

            if narrowing:
                newincludes = oldincludes - removedincludes
                newexcludes = oldexcludes | addedexcludes
                _narrow(
                    ui,
                    repo,
                    remote,
                    commoninc,
                    oldincludes,
                    oldexcludes,
                    newincludes,
                    newexcludes,
                    opts['force_delete_local_changes'],
                    opts['backup'],
                )
                # _narrow() updated the narrowspec and _widen() below needs to
                # use the updated values as its base (otherwise removed includes
                # and addedexcludes will be lost in the resulting narrowspec)
                oldincludes = newincludes
                oldexcludes = newexcludes

            if widening:
                newincludes = oldincludes | addedincludes
                newexcludes = oldexcludes - removedexcludes
                _widen(
                    ui,
                    repo,
                    remote,
                    commoninc,
                    oldincludes,
                    oldexcludes,
                    newincludes,
                    newexcludes,
                )
        finally:
            remote.close()

    return 0
