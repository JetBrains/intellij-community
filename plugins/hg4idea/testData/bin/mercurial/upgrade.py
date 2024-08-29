# upgrade.py - functions for in place upgrade of Mercurial repository
#
# Copyright (c) 2016-present, Gregory Szorc
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from .i18n import _
from . import (
    error,
    hg,
    localrepo,
    lock as lockmod,
    pycompat,
    requirements as requirementsmod,
    scmutil,
)

from .upgrade_utils import (
    actions as upgrade_actions,
    auto_upgrade,
    engine as upgrade_engine,
)

from .utils import (
    stringutil,
)

may_auto_upgrade = auto_upgrade.may_auto_upgrade
allformatvariant = upgrade_actions.allformatvariant


def upgraderepo(
    ui,
    repo,
    run=False,
    optimize=None,
    backup=True,
    manifest=None,
    changelog=None,
    filelogs=None,
):
    """Upgrade a repository in place."""
    if optimize is None:
        optimize = set()
    repo = repo.unfiltered()

    specified_revlogs = {}
    if changelog is not None:
        specified_revlogs[upgrade_engine.UPGRADE_CHANGELOG] = changelog
    if manifest is not None:
        specified_revlogs[upgrade_engine.UPGRADE_MANIFEST] = manifest
    if filelogs is not None:
        specified_revlogs[upgrade_engine.UPGRADE_FILELOGS] = filelogs

    # Ensure the repository can be upgraded.
    upgrade_actions.check_source_requirements(repo)

    default_options = localrepo.defaultcreateopts(repo.ui)
    newreqs = localrepo.newreporequirements(repo.ui, default_options)
    newreqs.update(upgrade_actions.preservedrequirements(repo))

    upgrade_actions.check_requirements_changes(repo, newreqs)

    # Find and validate all improvements that can be made.
    alloptimizations = upgrade_actions.findoptimizations(repo)

    # Apply and Validate arguments.
    optimizations = []
    for o in alloptimizations:
        if o.name in optimize:
            optimizations.append(o)
            optimize.discard(o.name)

    if optimize:  # anything left is unknown
        raise error.Abort(
            _(b'unknown optimization action requested: %s')
            % b', '.join(sorted(optimize)),
            hint=_(b'run without arguments to see valid optimizations'),
        )

    format_upgrades = upgrade_actions.find_format_upgrades(repo)
    up_actions = upgrade_actions.determine_upgrade_actions(
        repo, format_upgrades, optimizations, repo.requirements, newreqs
    )
    removed_actions = upgrade_actions.find_format_downgrades(repo)

    # check if we need to touch revlog and if so, which ones

    touched_revlogs = set()
    overwrite_msg = _(b'warning: ignoring %14s, as upgrade is changing: %s\n')
    select_msg = _(b'note:    selecting %s for processing to change: %s\n')
    msg_issued = 0

    FL = upgrade_engine.UPGRADE_FILELOGS
    MN = upgrade_engine.UPGRADE_MANIFEST
    CL = upgrade_engine.UPGRADE_CHANGELOG

    if optimizations:
        if any(specified_revlogs.values()):
            # we have some limitation on revlogs to be recloned
            for rl, enabled in specified_revlogs.items():
                if enabled:
                    touched_revlogs.add(rl)
        else:
            touched_revlogs = set(upgrade_engine.UPGRADE_ALL_REVLOGS)
            for rl, enabled in specified_revlogs.items():
                if not enabled:
                    touched_revlogs.discard(rl)

    if repo.shared():
        unsafe_actions = set()
        unsafe_actions.update(up_actions)
        unsafe_actions.update(removed_actions)
        unsafe_actions.update(optimizations)
        unsafe_actions = [
            a for a in unsafe_actions if not a.compatible_with_share
        ]
        unsafe_actions.sort(key=lambda a: a.name)
        if unsafe_actions:
            m = _(b'cannot use these actions on a share repository: %s')
            h = _(b'upgrade the main repository directly')
            actions = b', '.join(a.name for a in unsafe_actions)
            m %= actions
            raise error.Abort(m, hint=h)

    for action in sorted(up_actions + removed_actions, key=lambda a: a.name):
        # optimisation does not "requires anything, they just needs it.
        if action.type != upgrade_actions.FORMAT_VARIANT:
            continue

        if action.touches_filelogs and FL not in touched_revlogs:
            if FL in specified_revlogs:
                if not specified_revlogs[FL]:
                    msg = overwrite_msg % (b'--no-filelogs', action.name)
                    ui.warn(msg)
                    msg_issued = 2
            else:
                msg = select_msg % (b'all-filelogs', action.name)
                ui.status(msg)
                if not ui.quiet:
                    msg_issued = 1
            touched_revlogs.add(FL)

        if action.touches_manifests and MN not in touched_revlogs:
            if MN in specified_revlogs:
                if not specified_revlogs[MN]:
                    msg = overwrite_msg % (b'--no-manifest', action.name)
                    ui.warn(msg)
                    msg_issued = 2
            else:
                msg = select_msg % (b'all-manifestlogs', action.name)
                ui.status(msg)
                if not ui.quiet:
                    msg_issued = 1
            touched_revlogs.add(MN)

        if action.touches_changelog and CL not in touched_revlogs:
            if CL in specified_revlogs:
                if not specified_revlogs[CL]:
                    msg = overwrite_msg % (b'--no-changelog', action.name)
                    ui.warn(msg)
                    msg_issued = True
            else:
                msg = select_msg % (b'changelog', action.name)
                ui.status(msg)
                if not ui.quiet:
                    msg_issued = 1
            touched_revlogs.add(CL)
    if msg_issued >= 2:
        ui.warn((b"\n"))
    elif msg_issued >= 1:
        ui.status((b"\n"))

    upgrade_op = upgrade_actions.UpgradeOperation(
        ui,
        newreqs,
        repo.requirements,
        up_actions,
        removed_actions,
        touched_revlogs,
        backup,
    )

    if not run:
        fromconfig = []
        onlydefault = []

        for d in format_upgrades:
            if d.fromconfig(repo):
                fromconfig.append(d)
            elif d.default:
                onlydefault.append(d)

        if fromconfig or onlydefault:

            if fromconfig:
                ui.status(
                    _(
                        b'repository lacks features recommended by '
                        b'current config options:\n\n'
                    )
                )
                for i in fromconfig:
                    ui.status(b'%s\n   %s\n\n' % (i.name, i.description))

            if onlydefault:
                ui.status(
                    _(
                        b'repository lacks features used by the default '
                        b'config options:\n\n'
                    )
                )
                for i in onlydefault:
                    ui.status(b'%s\n   %s\n\n' % (i.name, i.description))

            ui.status(b'\n')
        else:
            ui.status(_(b'(no format upgrades found in existing repository)\n'))

        ui.status(
            _(
                b'performing an upgrade with "--run" will make the following '
                b'changes:\n\n'
            )
        )

        upgrade_op.print_requirements()
        upgrade_op.print_optimisations()
        upgrade_op.print_upgrade_actions()
        upgrade_op.print_affected_revlogs()

        if upgrade_op.unused_optimizations:
            ui.status(
                _(
                    b'additional optimizations are available by specifying '
                    b'"--optimize <name>":\n\n'
                )
            )
            upgrade_op.print_unused_optimizations()
        return

    if not (upgrade_op.upgrade_actions or upgrade_op.removed_actions):
        ui.status(_(b'nothing to do\n'))
        return
    # Else we're in the run=true case.
    ui.write(_(b'upgrade will perform the following actions:\n\n'))
    upgrade_op.print_requirements()
    upgrade_op.print_optimisations()
    upgrade_op.print_upgrade_actions()
    upgrade_op.print_affected_revlogs()

    ui.status(_(b'beginning upgrade...\n'))
    with repo.wlock(), repo.lock():
        ui.status(_(b'repository locked and read-only\n'))
        # Our strategy for upgrading the repository is to create a new,
        # temporary repository, write data to it, then do a swap of the
        # data. There are less heavyweight ways to do this, but it is easier
        # to create a new repo object than to instantiate all the components
        # (like the store) separately.
        tmppath = pycompat.mkdtemp(prefix=b'upgrade.', dir=repo.path)
        backuppath = None
        try:
            ui.status(
                _(
                    b'creating temporary repository to stage upgraded '
                    b'data: %s\n'
                )
                % tmppath
            )

            # clone ui without using ui.copy because repo.ui is protected
            repoui = repo.ui.__class__(repo.ui)
            dstrepo = hg.repository(repoui, path=tmppath, create=True)

            with dstrepo.wlock(), dstrepo.lock():
                backuppath = upgrade_engine.upgrade(
                    ui, repo, dstrepo, upgrade_op
                )

        finally:
            ui.status(_(b'removing temporary repository %s\n') % tmppath)
            repo.vfs.rmtree(tmppath, forcibly=True)

            if backuppath and not ui.quiet:
                ui.warn(
                    _(b'copy of old repository backed up at %s\n') % backuppath
                )
                ui.warn(
                    _(
                        b'the old repository will not be deleted; remove '
                        b'it to free up disk space once the upgraded '
                        b'repository is verified\n'
                    )
                )

            upgrade_op.print_post_op_messages()


def upgrade_share_to_safe(
    ui,
    hgvfs,
    storevfs,
    current_requirements,
    mismatch_config,
    mismatch_warn,
    mismatch_verbose_upgrade,
):
    """Upgrades a share to use share-safe mechanism"""
    wlock = None
    store_requirements = localrepo._readrequires(storevfs, False)
    original_crequirements = current_requirements.copy()
    # after upgrade, store requires will be shared, so lets find
    # the requirements which are not present in store and
    # write them to share's .hg/requires
    diffrequires = current_requirements - store_requirements
    # add share-safe requirement as it will mark the share as share-safe
    diffrequires.add(requirementsmod.SHARESAFE_REQUIREMENT)
    current_requirements.add(requirementsmod.SHARESAFE_REQUIREMENT)
    # in `allow` case, we don't try to upgrade, we just respect the source
    # state, update requirements and continue
    if mismatch_config == b'allow':
        return
    try:
        wlock = lockmod.trylock(ui, hgvfs, b'wlock', 0, 0)
        # some process might change the requirement in between, re-read
        # and update current_requirements
        locked_requirements = localrepo._readrequires(hgvfs, True)
        if locked_requirements != original_crequirements:
            removed = current_requirements - locked_requirements
            # update current_requirements in place because it's passed
            # as reference
            current_requirements -= removed
            current_requirements |= locked_requirements
            diffrequires = current_requirements - store_requirements
            # add share-safe requirement as it will mark the share as share-safe
            diffrequires.add(requirementsmod.SHARESAFE_REQUIREMENT)
            current_requirements.add(requirementsmod.SHARESAFE_REQUIREMENT)
        scmutil.writerequires(hgvfs, diffrequires)
        if mismatch_verbose_upgrade:
            ui.warn(_(b'repository upgraded to use share-safe mode\n'))
    except error.LockError as e:
        hint = _(
            b"see `hg help config.format.use-share-safe` for more information"
        )
        if mismatch_config == b'upgrade-abort':
            raise error.Abort(
                _(b'failed to upgrade share, got error: %s')
                % stringutil.forcebytestr(e.strerror),
                hint=hint,
            )
        elif mismatch_warn:
            ui.warn(
                _(b'failed to upgrade share, got error: %s\n')
                % stringutil.forcebytestr(e.strerror),
                hint=hint,
            )
    finally:
        if wlock:
            wlock.release()


def downgrade_share_to_non_safe(
    ui,
    hgvfs,
    sharedvfs,
    current_requirements,
    mismatch_config,
    mismatch_warn,
    mismatch_verbose_upgrade,
):
    """Downgrades a share which use share-safe to not use it"""
    wlock = None
    source_requirements = localrepo._readrequires(sharedvfs, True)
    original_crequirements = current_requirements.copy()
    # we cannot be 100% sure on which requirements were present in store when
    # the source supported share-safe. However, we do know that working
    # directory requirements were not there. Hence we remove them
    source_requirements -= requirementsmod.WORKING_DIR_REQUIREMENTS
    current_requirements |= source_requirements
    current_requirements.remove(requirementsmod.SHARESAFE_REQUIREMENT)
    if mismatch_config == b'allow':
        return

    try:
        wlock = lockmod.trylock(ui, hgvfs, b'wlock', 0, 0)
        # some process might change the requirement in between, re-read
        # and update current_requirements
        locked_requirements = localrepo._readrequires(hgvfs, True)
        if locked_requirements != original_crequirements:
            removed = current_requirements - locked_requirements
            # update current_requirements in place because it's passed
            # as reference
            current_requirements -= removed
            current_requirements |= locked_requirements
            current_requirements |= source_requirements
            current_requirements -= set(requirementsmod.SHARESAFE_REQUIREMENT)
        scmutil.writerequires(hgvfs, current_requirements)
        if mismatch_verbose_upgrade:
            ui.warn(_(b'repository downgraded to not use share-safe mode\n'))
    except error.LockError as e:
        hint = _(
            b"see `hg help config.format.use-share-safe` for more information"
        )
        # If upgrade-abort is set, abort when upgrade fails, else let the
        # process continue as `upgrade-allow` is set
        if mismatch_config == b'downgrade-abort':
            raise error.Abort(
                _(b'failed to downgrade share, got error: %s')
                % stringutil.forcebytestr(e.strerror),
                hint=hint,
            )
        elif mismatch_warn:
            ui.warn(
                _(b'failed to downgrade share, got error: %s\n')
                % stringutil.forcebytestr(e.strerror),
                hint=hint,
            )
    finally:
        if wlock:
            wlock.release()
