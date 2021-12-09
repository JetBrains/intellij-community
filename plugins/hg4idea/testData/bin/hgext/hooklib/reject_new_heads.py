# Copyright 2020 Joerg Sonnenberger <joerg@bec.de>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""reject_new_heads is a hook to check that branches touched by new changesets
have at most one open head. It can be used to enforce policies for
merge-before-push or rebase-before-push. It does not handle pre-existing
hydras.

Usage:
  [hooks]
  pretxnclose.reject_new_heads = \
    python:hgext.hooklib.reject_new_heads.hook
"""

from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import (
    error,
    pycompat,
)


def hook(ui, repo, hooktype, node=None, **kwargs):
    if hooktype != b"pretxnclose":
        raise error.Abort(
            _(b'Unsupported hook type %r') % pycompat.bytestr(hooktype)
        )
    ctx = repo.unfiltered()[node]
    branches = set()
    for rev in repo.changelog.revs(start=ctx.rev()):
        rev = repo[rev]
        branches.add(rev.branch())
    for branch in branches:
        if len(repo.revs("head() and not closed() and branch(%s)", branch)) > 1:
            raise error.Abort(
                _(b'Changes on branch %r resulted in multiple heads')
                % pycompat.bytestr(branch)
            )
