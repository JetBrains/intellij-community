"""implements bookmark-based branching (EXPERIMENTAL)

 - Disables creation of new branches (config: enable_branches=False).
 - Requires an active bookmark on commit (config: require_bookmark=True).
 - Doesn't move the active bookmark on update, only on commit.
 - Requires '--rev' for moving an existing bookmark.
 - Protects special bookmarks (config: protect=@).

 flow related commands

    :hg book NAME: create a new bookmark
    :hg book NAME -r REV: move bookmark to revision (fast-forward)
    :hg up|co NAME: switch to bookmark
    :hg push -B .: push active bookmark
"""
from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import (
    bookmarks,
    commands,
    error,
    extensions,
    registrar,
)

MY_NAME = b'bookflow'

configtable = {}
configitem = registrar.configitem(configtable)

configitem(MY_NAME, b'protect', [b'@'])
configitem(MY_NAME, b'require-bookmark', True)
configitem(MY_NAME, b'enable-branches', False)

cmdtable = {}
command = registrar.command(cmdtable)


def commit_hook(ui, repo, **kwargs):
    active = repo._bookmarks.active
    if active:
        if active in ui.configlist(MY_NAME, b'protect'):
            raise error.Abort(
                _(b'cannot commit, bookmark %s is protected') % active
            )
        if not cwd_at_bookmark(repo, active):
            raise error.Abort(
                _(
                    b'cannot commit, working directory out of sync with active bookmark'
                ),
                hint=_(b"run 'hg up %s'") % active,
            )
    elif ui.configbool(MY_NAME, b'require-bookmark', True):
        raise error.Abort(_(b'cannot commit without an active bookmark'))
    return 0


def bookmarks_update(orig, repo, parents, node):
    if len(parents) == 2:
        # called during commit
        return orig(repo, parents, node)
    else:
        # called during update
        return False


def bookmarks_addbookmarks(
    orig, repo, tr, names, rev=None, force=False, inactive=False
):
    if not rev:
        marks = repo._bookmarks
        for name in names:
            if name in marks:
                raise error.Abort(
                    _(
                        b"bookmark %s already exists, to move use the --rev option"
                    )
                    % name
                )
    return orig(repo, tr, names, rev, force, inactive)


def commands_commit(orig, ui, repo, *args, **opts):
    commit_hook(ui, repo)
    return orig(ui, repo, *args, **opts)


def commands_pull(orig, ui, repo, *args, **opts):
    rc = orig(ui, repo, *args, **opts)
    active = repo._bookmarks.active
    if active and not cwd_at_bookmark(repo, active):
        ui.warn(
            _(
                b"working directory out of sync with active bookmark, run "
                b"'hg up %s'"
            )
            % active
        )
    return rc


def commands_branch(orig, ui, repo, label=None, **opts):
    if label and not opts.get('clean') and not opts.get('rev'):
        raise error.Abort(
            _(
                b"creating named branches is disabled and you should use bookmarks"
            ),
            hint=b"see 'hg help bookflow'",
        )
    return orig(ui, repo, label, **opts)


def cwd_at_bookmark(repo, mark):
    mark_id = repo._bookmarks[mark]
    cur_id = repo.lookup(b'.')
    return cur_id == mark_id


def uisetup(ui):
    extensions.wrapfunction(bookmarks, b'update', bookmarks_update)
    extensions.wrapfunction(bookmarks, b'addbookmarks', bookmarks_addbookmarks)
    extensions.wrapcommand(commands.table, b'commit', commands_commit)
    extensions.wrapcommand(commands.table, b'pull', commands_pull)
    if not ui.configbool(MY_NAME, b'enable-branches'):
        extensions.wrapcommand(commands.table, b'branch', commands_branch)
