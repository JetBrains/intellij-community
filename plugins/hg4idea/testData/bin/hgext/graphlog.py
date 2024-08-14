# ASCII graph log extension for Mercurial
#
# Copyright 2007 Joel Rosdahl <joel@rosdahl.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''command to view revision graphs from a shell (DEPRECATED)

The functionality of this extension has been include in core Mercurial
since version 2.3. Please use :hg:`log -G ...` instead.

This extension adds a --graph option to the incoming, outgoing and log
commands. When this options is given, an ASCII representation of the
revision graph is also shown.
'''


from mercurial.i18n import _
from mercurial import (
    cmdutil,
    commands,
    registrar,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


@command(
    b'glog',
    [
        (
            b'f',
            b'follow',
            None,
            _(
                b'follow changeset history, or file history across copies and renames'
            ),
        ),
        (
            b'',
            b'follow-first',
            None,
            _(b'only follow the first parent of merge changesets (DEPRECATED)'),
        ),
        (
            b'd',
            b'date',
            b'',
            _(b'show revisions matching date spec'),
            _(b'DATE'),
        ),
        (b'C', b'copies', None, _(b'show copied files')),
        (
            b'k',
            b'keyword',
            [],
            _(b'do case-insensitive search for a given text'),
            _(b'TEXT'),
        ),
        (
            b'r',
            b'rev',
            [],
            _(b'show the specified revision or revset'),
            _(b'REV'),
        ),
        (
            b'',
            b'removed',
            None,
            _(b'include revisions where files were removed'),
        ),
        (b'm', b'only-merges', None, _(b'show only merges (DEPRECATED)')),
        (b'u', b'user', [], _(b'revisions committed by user'), _(b'USER')),
        (
            b'',
            b'only-branch',
            [],
            _(
                b'show only changesets within the given named branch (DEPRECATED)'
            ),
            _(b'BRANCH'),
        ),
        (
            b'b',
            b'branch',
            [],
            _(b'show changesets within the given named branch'),
            _(b'BRANCH'),
        ),
        (
            b'P',
            b'prune',
            [],
            _(b'do not display revision or any of its ancestors'),
            _(b'REV'),
        ),
    ]
    + cmdutil.logopts
    + cmdutil.walkopts,
    _(b'[OPTION]... [FILE]'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
    inferrepo=True,
)
def glog(ui, repo, *pats, **opts):
    """show revision history alongside an ASCII revision graph

    Print a revision history alongside a revision graph drawn with
    ASCII characters.

    Nodes printed as an @ character are parents of the working
    directory.

    This is an alias to :hg:`log -G`.
    """
    opts['graph'] = True
    return commands.log(ui, repo, *pats, **opts)
