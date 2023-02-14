# Mercurial extension to provide the 'hg children' command
#
# Copyright 2007 by Intevation GmbH <intevation@intevation.de>
#
# Author(s):
# Thomas Arendsen Hein <thomas@intevation.de>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''command to display child changesets (DEPRECATED)

This extension is deprecated. You should use :hg:`log -r
"children(REV)"` instead.
'''

from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import (
    cmdutil,
    logcmdutil,
    pycompat,
    registrar,
    scmutil,
)

templateopts = cmdutil.templateopts

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


@command(
    b'children',
    [
        (
            b'r',
            b'rev',
            b'.',
            _(b'show children of the specified revision'),
            _(b'REV'),
        ),
    ]
    + templateopts,
    _(b'hg children [-r REV] [FILE]'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
    inferrepo=True,
)
def children(ui, repo, file_=None, **opts):
    """show the children of the given or working directory revision

    Print the children of the working directory's revisions. If a
    revision is given via -r/--rev, the children of that revision will
    be printed. If a file argument is given, revision in which the
    file was last changed (after the working directory revision or the
    argument to --rev if given) is printed.

    Please use :hg:`log` instead::

        hg children => hg log -r "children(.)"
        hg children -r REV => hg log -r "children(REV)"

    See :hg:`help log` and :hg:`help revsets.children`.

    """
    opts = pycompat.byteskwargs(opts)
    rev = opts.get(b'rev')
    ctx = scmutil.revsingle(repo, rev)
    if file_:
        fctx = repo.filectx(file_, changeid=ctx.rev())
        childctxs = [fcctx.changectx() for fcctx in fctx.children()]
    else:
        childctxs = ctx.children()

    displayer = logcmdutil.changesetdisplayer(ui, repo, opts)
    for cctx in childctxs:
        displayer.show(cctx)
    displayer.close()
