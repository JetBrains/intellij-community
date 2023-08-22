# amend.py - provide the amend command
#
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""provide the amend command (EXPERIMENTAL)

This extension provides an ``amend`` command that is similar to
``commit --amend`` but does not prompt an editor.
"""

from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import (
    cmdutil,
    commands,
    registrar,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = {}
command = registrar.command(cmdtable)


@command(
    b'amend',
    [
        (
            b'A',
            b'addremove',
            None,
            _(b'mark new/missing files as added/removed before committing'),
        ),
        (b'e', b'edit', None, _(b'invoke editor on commit messages')),
        (b'i', b'interactive', None, _(b'use interactive mode')),
        (
            b'',
            b'close-branch',
            None,
            _(b'mark a branch as closed, hiding it from the branch list'),
        ),
        (b's', b'secret', None, _(b'use the secret phase for committing')),
        (b'n', b'note', b'', _(b'store a note on the amend')),
    ]
    + cmdutil.walkopts
    + cmdutil.commitopts
    + cmdutil.commitopts2
    + cmdutil.commitopts3,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
    inferrepo=True,
)
def amend(ui, repo, *pats, **opts):
    """amend the working copy parent with all or specified outstanding changes

    Similar to :hg:`commit --amend`, but reuse the commit message without
    invoking editor, unless ``--edit`` was set.

    See :hg:`help commit` for more details.
    """
    cmdutil.check_note_size(opts)

    with repo.wlock(), repo.lock():
        if not opts.get('logfile'):
            opts['message'] = opts.get('message') or repo[b'.'].description()
        opts['amend'] = True
        return commands._docommit(ui, repo, *pats, **opts)
