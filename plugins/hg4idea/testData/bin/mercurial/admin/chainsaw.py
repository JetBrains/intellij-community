# chainsaw.py
#
# Copyright 2022 Georges Racinet <georges.racinet@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""chainsaw is a collection of single-minded and dangerous tools. (EXPERIMENTAL)

  "Don't use a chainsaw to cut your food!"

The chainsaw is a collection of commands that are so much geared towards a
specific use case in a specific context or environment that they are totally
inappropriate and **really dangerous** in other contexts.

The help text of each command explicitly summarizes its context of application
and the wanted end result.

It is recommended to run these commands with the ``HGPLAIN`` environment
variable (see :hg:`help scripting`).
"""

import shutil

from ..i18n import _
from .. import (
    cmdutil,
    commands,
    error,
    localrepo,
    registrar,
)
from ..utils import (
    urlutil,
)

cmdtable = {}
command = registrar.command(cmdtable)


@command(
    b'admin::chainsaw-update',
    [
        (
            b'',
            b'purge-unknown',
            True,
            _(
                b'Remove unversioned files before update. Disabling this can '
                b'in some cases interfere with the update.'
                b'See also :hg:`purge`.'
            ),
        ),
        (
            b'',
            b'purge-ignored',
            True,
            _(
                b'Remove ignored files before update. Disable this for '
                b'instance to reuse previous compiler object files. '
                b'See also :hg:`purge`.'
            ),
        ),
        (
            b'',
            b'rev',
            b'',
            _(b'revision to update to'),
        ),
        (
            b'',
            b'source',
            b'',
            _(b'repository to clone from'),
        ),
        (
            b'',
            b'dest',
            b'',
            _(b'repository to update to REV (possibly cloning)'),
        ),
        (
            b'',
            b'initial-clone-minimal',
            False,
            _(
                b'Pull only the prescribed revision upon initial cloning. '
                b'This has the side effect of ignoring clone-bundles, '
                b'which if often slower on the client side and stressful '
                b'to the server than applying available clone bundles.'
            ),
        ),
    ],
    _(
        b'hg admin::chainsaw-update [OPTION] --rev REV --source SOURCE --dest DEST'
    ),
    helpbasic=True,
    norepo=True,
)
def update(ui, **opts):
    """pull and update to a given revision, no matter what, (EXPERIMENTAL)

    Context of application: *some* Continuous Integration (CI) systems,
    packaging or deployment tools.

    Wanted end result: local repository at the given REPO_PATH, having the
    latest changes to the given revision and with a clean working directory
    updated at the given revision.

    chainsaw-update pulls from one source, then updates the working directory
    to the given revision, overcoming anything that would stand in the way.

    By default, it will:

    - clone if the local repo does not exist yet, **removing any directory
      at the given path** that would not be a Mercurial repository.
      The initial clone is full by default, so that clonebundles can be
      applied. Use the --initial-clone-minimal flag to avoid this.
    - break locks if needed, leading to possible corruption if there
      is a concurrent write access.
    - perform recovery actions if needed
    - revert any local modification.
    - purge unknown and ignored files.
    - go as far as to reclone if everything else failed (not implemented yet).

    DO NOT use it for anything else than performing a series
    of unattended updates, with full exclusive repository access each time
    and without any other local work than running build scripts.
    In case the local repository is a share (see :hg:`help share`), exclusive
    write access to the share source is also mandatory.

    It is recommended to run these commands with the ``HGPLAIN`` environment
    variable (see :hg:`scripting`).

    Motivation: in Continuous Integration and Delivery systems (CI/CD), the
    occasional remnant or bogus lock are common sources of waste of time (both
    working time and calendar time). CI/CD scripts tend to grow with counter-
    measures, often done in urgency. Also, whilst it is neat to keep
    repositories from one job to the next (especially with large
    repositories), an exceptional recloning is better than missing a release
    deadline.
    """
    rev = opts['rev']
    source = opts['source']
    repo_path = opts['dest']
    if not rev:
        raise error.InputError(_(b'specify a target revision with --rev'))
    if not source:
        raise error.InputError(_(b'specify a pull path with --source'))
    if not repo_path:
        raise error.InputError(_(b'specify a repo path with --dest'))
    repo_path = urlutil.urllocalpath(repo_path)

    try:
        repo = localrepo.instance(ui, repo_path, create=False)
        repo_created = False
        ui.status(_(b'loaded repository at "%s"\n' % repo_path))
    except error.RepoError:
        try:
            shutil.rmtree(repo_path)
        except FileNotFoundError:
            ui.status(_(b'no such directory: "%s"\n' % repo_path))
        else:
            ui.status(
                _(
                    b'removed non-repository file or directory '
                    b'at "%s"' % repo_path
                )
            )

        ui.status(_(b'creating repository at "%s"\n' % repo_path))
        repo = localrepo.instance(ui, repo_path, create=True)
        repo_created = True

    if repo.svfs.tryunlink(b'lock'):
        ui.status(_(b'had to break store lock\n'))
    if repo.vfs.tryunlink(b'wlock'):
        ui.status(_(b'had to break working copy lock\n'))
    # If another process relock after the breacking above, the next locking
    # will have to wait.
    with repo.wlock(), repo.lock():
        ui.status(_(b'recovering after interrupted transaction, if any\n'))
        repo.recover()

        ui.status(_(b'pulling from %s\n') % source)
        if repo_created and not opts.get('initial_clone_minimal'):
            pull_revs = []
        else:
            pull_revs = [rev]
        overrides = {(b'ui', b'quiet'): True}
        with repo.ui.configoverride(overrides, b'chainsaw-update'):
            pull = cmdutil.findcmd(b'pull', commands.table)[1][0]
            ret = pull(
                repo.ui,
                repo,
                source,
                rev=pull_revs,
                remote_hidden=False,
            )
            if ret:
                return ret

        purge = cmdutil.findcmd(b'purge', commands.table)[1][0]
        ret = purge(
            ui,
            repo,
            dirs=True,
            all=opts.get('purge_ignored'),
            files=opts.get('purge_unknown'),
            confirm=False,
        )
        if ret:
            return ret

        ui.status(_(b'updating to revision \'%s\'\n') % rev)
        update = cmdutil.findcmd(b'update', commands.table)[1][0]
        ret = update(ui, repo, rev=rev, clean=True)
        if ret:
            return ret

        ui.status(
            _(
                b'chainsaw-update to revision \'%s\' '
                b'for repository at \'%s\' done\n'
            )
            % (rev, repo.root)
        )
