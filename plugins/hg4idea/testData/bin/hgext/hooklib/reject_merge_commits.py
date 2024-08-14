# Copyright 2020 Joerg Sonnenberger <joerg@bec.de>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""reject_merge_commits is a hook to check new changesets for merge commits.
Merge commits are allowed only between different branches, i.e. merging
a feature branch into the main development branch. This can be used to
enforce policies for linear commit histories.

Usage:
  [hooks]
  pretxnchangegroup.reject_merge_commits = \
    python:hgext.hooklib.reject_merge_commits.hook
"""


from mercurial.i18n import _
from mercurial import (
    error,
    pycompat,
)


def hook(ui, repo, hooktype, node=None, **kwargs):
    if hooktype != b"pretxnchangegroup":
        raise error.Abort(
            _(b'Unsupported hook type %r') % pycompat.bytestr(hooktype)
        )

    ctx = repo.unfiltered()[node]
    for rev in repo.changelog.revs(start=ctx.rev()):
        rev = repo[rev]
        parents = rev.parents()
        if len(parents) < 2:
            continue
        if all(repo[p].branch() == rev.branch() for p in parents):
            raise error.Abort(
                _(
                    b'%s rejected as merge on the same branch. '
                    b'Please consider rebase.'
                )
                % rev
            )
