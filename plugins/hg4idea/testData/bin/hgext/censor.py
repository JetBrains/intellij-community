# Copyright (C) 2015 - Mike Edgar <adgar@google.com>
#
# This extension enables removal of file content at a given revision,
# rewriting the data/metadata of successive revisions to preserve revision log
# integrity.

"""erase file content at a given revision

The censor command instructs Mercurial to erase all content of a file at a given
revision *without updating the changeset hash.* This allows existing history to
remain valid while preventing future clones/pulls from receiving the erased
data.

Typical uses for censor are due to security or legal requirements, including::

 * Passwords, private keys, cryptographic material
 * Licensed data/code/libraries for which the license has expired
 * Personally Identifiable Information or other private data

Censored nodes can interrupt mercurial's typical operation whenever the excised
data needs to be materialized. Some commands, like ``hg cat``/``hg revert``,
simply fail when asked to produce censored data. Others, like ``hg verify`` and
``hg update``, must be capable of tolerating censored data to continue to
function in a meaningful way. Such commands only tolerate censored file
revisions if they are allowed by the "censor.policy=ignore" config option.

A few informative commands such as ``hg grep`` will unconditionally
ignore censored data and merely report that it was encountered.
"""

from __future__ import absolute_import

from mercurial.i18n import _
from mercurial.node import short

from mercurial import (
    error,
    registrar,
    scmutil,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


@command(
    b'censor',
    [
        (
            b'r',
            b'rev',
            b'',
            _(b'censor file from specified revision'),
            _(b'REV'),
        ),
        (b't', b'tombstone', b'', _(b'replacement tombstone data'), _(b'TEXT')),
    ],
    _(b'-r REV [-t TEXT] [FILE]'),
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def censor(ui, repo, path, rev=b'', tombstone=b'', **opts):
    with repo.wlock(), repo.lock():
        return _docensor(ui, repo, path, rev, tombstone, **opts)


def _docensor(ui, repo, path, rev=b'', tombstone=b'', **opts):
    if not path:
        raise error.Abort(_(b'must specify file path to censor'))
    if not rev:
        raise error.Abort(_(b'must specify revision to censor'))

    wctx = repo[None]

    m = scmutil.match(wctx, (path,))
    if m.anypats() or len(m.files()) != 1:
        raise error.Abort(_(b'can only specify an explicit filename'))
    path = m.files()[0]
    flog = repo.file(path)
    if not len(flog):
        raise error.Abort(_(b'cannot censor file with no history'))

    rev = scmutil.revsingle(repo, rev, rev).rev()
    try:
        ctx = repo[rev]
    except KeyError:
        raise error.Abort(_(b'invalid revision identifier %s') % rev)

    try:
        fctx = ctx.filectx(path)
    except error.LookupError:
        raise error.Abort(_(b'file does not exist at revision %s') % rev)

    fnode = fctx.filenode()
    heads = []
    for headnode in repo.heads():
        hc = repo[headnode]
        if path in hc and hc.filenode(path) == fnode:
            heads.append(hc)
    if heads:
        headlist = b', '.join([short(c.node()) for c in heads])
        raise error.Abort(
            _(b'cannot censor file in heads (%s)') % headlist,
            hint=_(b'clean/delete and commit first'),
        )

    wp = wctx.parents()
    if ctx.node() in [p.node() for p in wp]:
        raise error.Abort(
            _(b'cannot censor working directory'),
            hint=_(b'clean/delete/update first'),
        )

    with repo.transaction(b'censor') as tr:
        flog.censorrevision(tr, fnode, tombstone=tombstone)
