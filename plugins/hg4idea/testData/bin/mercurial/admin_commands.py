# admin_commands.py - command processing for admin* commands
#
# Copyright 2022 Mercurial Developers
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from .i18n import _
from .admin import chainsaw, verify
from . import error, registrar, transaction


table = {}
table.update(chainsaw.command._table)
command = registrar.command(table)


@command(
    b'admin::verify',
    [
        (b'c', b'check', [], _(b'add a check'), _(b'CHECK')),
        (b'o', b'option', [], _(b'pass an option to a check'), _(b'OPTION')),
    ],
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def admin_verify(ui, repo, **opts):
    """verify the integrity of the repository

    Alternative UI to `hg verify` with a lot more control over the
    verification process and better error reporting.
    """

    if not repo.url().startswith(b'file:'):
        raise error.Abort(_(b"cannot verify bundle or remote repos"))

    if transaction.has_abandoned_transaction(repo):
        ui.warn(_(b"abandoned transaction found - run hg recover\n"))

    checks = opts.get("check", [])
    options = opts.get("option", [])

    funcs = verify.get_checks(repo, ui, names=checks, options=options)

    ui.status(_(b"running %d checks\n") % len(funcs))
    # Done in two times so the execution is separated from the resolving step
    for name, func in sorted(funcs.items(), key=lambda x: x[0]):
        ui.status(_(b"running %s\n") % name)
        errors = func()
        if errors:
            ui.warn(_(b"found %d errors\n") % errors)
