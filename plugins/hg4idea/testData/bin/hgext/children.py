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

from mercurial import cmdutil, commands
from mercurial.commands import templateopts
from mercurial.i18n import _

testedwith = 'internal'

def children(ui, repo, file_=None, **opts):
    """show the children of the given or working directory revision

    Print the children of the working directory's revisions. If a
    revision is given via -r/--rev, the children of that revision will
    be printed. If a file argument is given, revision in which the
    file was last changed (after the working directory revision or the
    argument to --rev if given) is printed.
    """
    rev = opts.get('rev')
    if file_:
        ctx = repo.filectx(file_, changeid=rev)
    else:
        ctx = repo[rev]

    displayer = cmdutil.show_changeset(ui, repo, opts)
    for cctx in ctx.children():
        displayer.show(cctx)
    displayer.close()

cmdtable = {
    "children":
        (children,
         [('r', 'rev', '',
           _('show children of the specified revision'), _('REV')),
         ] + templateopts,
         _('hg children [-r REV] [FILE]')),
}

commands.inferrepo += " children"
