# Copyright 2020 Joerg Sonnenberger <joerg@bec.de>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""enforce_draft_commits us a hook to ensure that all new changesets are
in the draft phase. This allows enforcing policies for work-in-progress
changes in overlay repositories, i.e. a shared hidden repositories with
different views for work-in-progress code and public history.

Usage:
  [hooks]
  pretxnclose-phase.enforce_draft_commits = \
    python:hgext.hooklib.enforce_draft_commits.hook
"""


from mercurial.i18n import _
from mercurial import (
    error,
    pycompat,
)


def hook(ui, repo, hooktype, node=None, **kwargs):
    if hooktype != b"pretxnclose-phase":
        raise error.Abort(
            _(b'Unsupported hook type %r') % pycompat.bytestr(hooktype)
        )
    ctx = repo.unfiltered()[node]
    if kwargs['oldphase']:
        raise error.Abort(
            _(b'Phase change from %r to %r for %s rejected')
            % (
                pycompat.bytestr(kwargs['oldphase']),
                pycompat.bytestr(kwargs['phase']),
                ctx,
            )
        )
    elif kwargs['phase'] != b'draft':
        raise error.Abort(
            _(b'New changeset %s in phase %r rejected')
            % (ctx, pycompat.bytestr(kwargs['phase']))
        )
