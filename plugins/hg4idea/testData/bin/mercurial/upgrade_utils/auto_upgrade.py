# upgrade.py - functions for automatic upgrade of Mercurial repository
#
# Copyright (c) 2022-present, Pierre-Yves David
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from ..i18n import _

from .. import (
    error,
    requirements as requirementsmod,
    scmutil,
)

from . import (
    actions,
    engine,
)


class AutoUpgradeOperation(actions.BaseOperation):
    """A limited Upgrade Operation used to run simple auto upgrade task

    (Expand it as needed in the future)
    """

    def __init__(self, req):
        super().__init__(
            new_requirements=req,
            backup_store=False,
        )


def get_share_safe_action(repo):
    """return an automatic-upgrade action for `share-safe` if applicable

    If no action is needed, return None, otherwise return a callback to upgrade
    or downgrade the repository according the configuration and repository
    format.
    """
    ui = repo.ui
    requirements = repo.requirements
    auto_upgrade_share_source = ui.configbool(
        b'format',
        b'use-share-safe.automatic-upgrade-of-mismatching-repositories',
    )
    auto_upgrade_quiet = ui.configbool(
        b'format',
        b'use-share-safe.automatic-upgrade-of-mismatching-repositories:quiet',
    )

    action = None

    if (
        auto_upgrade_share_source
        and requirementsmod.SHARED_REQUIREMENT not in requirements
    ):
        sf_config = ui.configbool(b'format', b'use-share-safe')
        sf_local = requirementsmod.SHARESAFE_REQUIREMENT in requirements
        if sf_config and not sf_local:
            msg = _(
                b"automatically upgrading repository to the `share-safe`"
                b" feature\n"
            )
            hint = b"(see `hg help config.format.use-share-safe` for details)\n"

            def action():
                if not (ui.quiet or auto_upgrade_quiet):
                    ui.write_err(msg)
                    ui.write_err(hint)
                requirements.add(requirementsmod.SHARESAFE_REQUIREMENT)
                scmutil.writereporequirements(repo, requirements)

        elif sf_local and not sf_config:
            msg = _(
                b"automatically downgrading repository from the `share-safe`"
                b" feature\n"
            )
            hint = b"(see `hg help config.format.use-share-safe` for details)\n"

            def action():
                if not (ui.quiet or auto_upgrade_quiet):
                    ui.write_err(msg)
                    ui.write_err(hint)
                requirements.discard(requirementsmod.SHARESAFE_REQUIREMENT)
                scmutil.writereporequirements(repo, requirements)

    return action


def get_tracked_hint_action(repo):
    """return an automatic-upgrade action for `tracked-hint` if applicable

    If no action is needed, return None, otherwise return a callback to upgrade
    or downgrade the repository according the configuration and repository
    format.
    """
    ui = repo.ui
    requirements = set(repo.requirements)
    auto_upgrade_tracked_hint = ui.configbool(
        b'format',
        b'use-dirstate-tracked-hint.automatic-upgrade-of-mismatching-repositories',
    )
    auto_upgrade_quiet = ui.configbool(
        b'format',
        b'use-dirstate-tracked-hint.automatic-upgrade-of-mismatching-repositories:quiet',
    )

    action = None

    if auto_upgrade_tracked_hint:
        th_config = ui.configbool(b'format', b'use-dirstate-tracked-hint')
        th_local = requirementsmod.DIRSTATE_TRACKED_HINT_V1 in requirements
        if th_config and not th_local:
            msg = _(
                b"automatically upgrading repository to the `tracked-hint`"
                b" feature\n"
            )
            hint = b"(see `hg help config.format.use-dirstate-tracked-hint` for details)\n"

            def action():
                if not (ui.quiet or auto_upgrade_quiet):
                    ui.write_err(msg)
                    ui.write_err(hint)
                requirements.add(requirementsmod.DIRSTATE_TRACKED_HINT_V1)
                op = AutoUpgradeOperation(requirements)
                engine.upgrade_tracked_hint(ui, repo, op, add=True)

        elif th_local and not th_config:
            msg = _(
                b"automatically downgrading repository from the `tracked-hint`"
                b" feature\n"
            )
            hint = b"(see `hg help config.format.use-dirstate-tracked-hint` for details)\n"

            def action():
                if not (ui.quiet or auto_upgrade_quiet):
                    ui.write_err(msg)
                    ui.write_err(hint)
                requirements.discard(requirementsmod.DIRSTATE_TRACKED_HINT_V1)
                op = AutoUpgradeOperation(requirements)
                engine.upgrade_tracked_hint(ui, repo, op, add=False)

    return action


def get_dirstate_v2_action(repo):
    """return an automatic-upgrade action for `dirstate-v2` if applicable

    If no action is needed, return None, otherwise return a callback to upgrade
    or downgrade the repository according the configuration and repository
    format.
    """
    ui = repo.ui
    requirements = set(repo.requirements)
    auto_upgrade_dv2 = ui.configbool(
        b'format',
        b'use-dirstate-v2.automatic-upgrade-of-mismatching-repositories',
    )
    auto_upgrade_dv2_quiet = ui.configbool(
        b'format',
        b'use-dirstate-v2.automatic-upgrade-of-mismatching-repositories:quiet',
    )

    action = None

    if auto_upgrade_dv2:
        d2_config = ui.configbool(b'format', b'use-dirstate-v2')
        d2_local = requirementsmod.DIRSTATE_V2_REQUIREMENT in requirements
        if d2_config and not d2_local:
            msg = _(
                b"automatically upgrading repository to the `dirstate-v2`"
                b" feature\n"
            )
            hint = (
                b"(see `hg help config.format.use-dirstate-v2` for details)\n"
            )

            def action():
                if not (ui.quiet or auto_upgrade_dv2_quiet):
                    ui.write_err(msg)
                    ui.write_err(hint)
                requirements.add(requirementsmod.DIRSTATE_V2_REQUIREMENT)
                fake_op = AutoUpgradeOperation(requirements)
                engine.upgrade_dirstate(repo.ui, repo, fake_op, b'v1', b'v2')

        elif d2_local and not d2_config:
            msg = _(
                b"automatically downgrading repository from the `dirstate-v2`"
                b" feature\n"
            )
            hint = (
                b"(see `hg help config.format.use-dirstate-v2` for details)\n"
            )

            def action():
                if not (ui.quiet or auto_upgrade_dv2_quiet):
                    ui.write_err(msg)
                    ui.write_err(hint)
                requirements.discard(requirementsmod.DIRSTATE_V2_REQUIREMENT)
                fake_op = AutoUpgradeOperation(requirements)
                engine.upgrade_dirstate(repo.ui, repo, fake_op, b'v2', b'v1')

    return action


AUTO_UPGRADE_ACTIONS = [
    get_dirstate_v2_action,
    get_share_safe_action,
    get_tracked_hint_action,
]


def may_auto_upgrade(repo, maker_func):
    """potentially perform auto-upgrade and return the final repository to use

    Auto-upgrade are "quick" repository upgrade that might automatically be run
    by "any" repository access. See `hg help config.format` for automatic
    upgrade documentation.

    note: each relevant upgrades are done one after the other for simplicity.
    This avoid having repository is partially inconsistent state while
    upgrading.

    repo: the current repository instance
    maker_func: a factory function that can recreate a repository after an upgrade
    """
    clear = False

    loop = 0

    try:
        while not clear:
            loop += 1
            if loop > 100:
                # XXX basic protection against infinite loop, make it better.
                raise error.ProgrammingError("Too many auto upgrade loops")
            clear = True
            for get_action in AUTO_UPGRADE_ACTIONS:
                action = get_action(repo)
                if action is not None:
                    clear = False
                    with repo.wlock(wait=False), repo.lock(wait=False):
                        action = get_action(repo)
                        if action is not None:
                            action()
                        repo = maker_func()
    except error.LockError:
        # if we cannot get the lock, ignore the auto-upgrade attemps and
        # proceed. We might want to make this behavior configurable in the
        # future.
        pass

    return repo
