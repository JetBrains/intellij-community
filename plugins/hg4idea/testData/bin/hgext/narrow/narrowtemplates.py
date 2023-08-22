# narrowtemplates.py - added template keywords for narrow clones
#
# Copyright 2017 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from mercurial import (
    registrar,
    revlog,
)

keywords = {}
templatekeyword = registrar.templatekeyword(keywords)
revsetpredicate = registrar.revsetpredicate()


def _isellipsis(repo, rev):
    if repo.changelog.flags(rev) & revlog.REVIDX_ELLIPSIS:
        return True
    return False


@templatekeyword(b'ellipsis', requires={b'repo', b'ctx'})
def ellipsis(context, mapping):
    """String. 'ellipsis' if the change is an ellipsis node, else ''."""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    if _isellipsis(repo, ctx.rev()):
        return b'ellipsis'
    return b''


@templatekeyword(b'outsidenarrow', requires={b'repo', b'ctx'})
def outsidenarrow(context, mapping):
    """String. 'outsidenarrow' if the change affects no tracked files,
    else ''."""
    repo = context.resource(mapping, b'repo')
    ctx = context.resource(mapping, b'ctx')
    m = repo.narrowmatch()
    if ctx.files() and not m.always():
        if not any(m(f) for f in ctx.files()):
            return b'outsidenarrow'
    return b''


@revsetpredicate(b'ellipsis()')
def ellipsisrevset(repo, subset, x):
    """Changesets that are ellipsis nodes."""
    return subset.filter(lambda r: _isellipsis(repo, r))
