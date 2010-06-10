# Copyright 2006, 2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''share a common history between several working directories'''

from mercurial.i18n import _
from mercurial import hg, commands

def share(ui, source, dest=None, noupdate=False):
    """create a new shared repository

    Initialize a new repository and working directory that shares its
    history with another repository.

    NOTE: using rollback or extensions that destroy/modify history
    (mq, rebase, etc.) can cause considerable confusion with shared
    clones. In particular, if two shared clones are both updated to
    the same changeset, and one of them destroys that changeset with
    rollback, the other clone will suddenly stop working: all
    operations will fail with "abort: working directory has unknown
    parent". The only known workaround is to use debugsetparents on
    the broken clone to reset it to a changeset that still exists
    (e.g. tip).
    """

    return hg.share(ui, source, dest, not noupdate)

cmdtable = {
    "share":
    (share,
     [('U', 'noupdate', None, _('do not create a working copy'))],
     _('[-U] SOURCE [DEST]')),
}

commands.norepo += " share"
