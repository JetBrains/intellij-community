# stack.py - Mercurial functions for stack definition
#
#  Copyright Olivia Mackall <olivia@selenic.com> and other
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


def getstack(repo, rev=None):
    """return a sorted smartrev of the stack containing either rev if it is
    not None or the current working directory parent.

    The stack will always contain all drafts changesets which are ancestors to
    the revision and are not merges.
    """
    if rev is None:
        rev = b'.'

    revspec = b'only(%s) and not public() and not ::merge()'
    revisions = repo.revs(revspec, rev)
    revisions.sort()
    return revisions
