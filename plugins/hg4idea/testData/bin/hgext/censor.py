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
As having a censored version in a checkout is impractical. The current head
revisions of the repository are checked. If the revision to be censored is in
any of them the command will abort.

A few informative commands such as ``hg grep`` will unconditionally
ignore censored data and merely report that it was encountered.
"""


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
            [],
            _(b'censor file from specified revision'),
            _(b'REV'),
        ),
        (
            b'',
            b'check-heads',
            True,
            _(b'check that repository heads are not affected'),
        ),
        (b't', b'tombstone', b'', _(b'replacement tombstone data'), _(b'TEXT')),
    ],
    _(b'-r REV [-t TEXT] [FILE]'),
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def censor(ui, repo, path, rev=(), tombstone=b'', check_heads=True, **opts):
    with repo.wlock(), repo.lock():
        return _docensor(
            ui,
            repo,
            path,
            rev,
            tombstone,
            check_heads=check_heads,
            **opts,
        )


def _docensor(ui, repo, path, revs=(), tombstone=b'', check_heads=True, **opts):
    if not path:
        raise error.Abort(_(b'must specify file path to censor'))
    if not revs:
        raise error.Abort(_(b'must specify revisions to censor'))

    wctx = repo[None]

    m = scmutil.match(wctx, (path,))
    if m.anypats() or len(m.files()) != 1:
        raise error.Abort(_(b'can only specify an explicit filename'))
    path = m.files()[0]
    flog = repo.file(path)
    if not len(flog):
        raise error.Abort(_(b'cannot censor file with no history'))

    revs = scmutil.revrange(repo, revs)
    if not revs:
        raise error.Abort(_(b'no matching revisions'))
    file_nodes = set()
    for r in revs:
        try:
            ctx = repo[r]
            file_nodes.add(ctx.filectx(path).filenode())
        except error.LookupError:
            raise error.Abort(_(b'file does not exist at revision %s') % ctx)

    if check_heads:
        heads = []
        repo_heads = repo.heads()
        msg = b'checking for the censored content in %d heads\n'
        msg %= len(repo_heads)
        ui.status(msg)
        for headnode in repo_heads:
            hc = repo[headnode]
            if path in hc and hc.filenode(path) in file_nodes:
                heads.append(hc)
        if heads:
            headlist = b', '.join([short(c.node()) for c in heads])
            raise error.Abort(
                _(b'cannot censor file in heads (%s)') % headlist,
                hint=_(b'clean/delete and commit first'),
            )

    msg = b'checking for the censored content in the working directory\n'
    ui.status(msg)
    wp = wctx.parents()
    if ctx.node() in [p.node() for p in wp]:
        raise error.Abort(
            _(b'cannot censor working directory'),
            hint=_(b'clean/delete/update first'),
        )

    msg = b'censoring %d file revisions\n'
    msg %= len(file_nodes)
    ui.status(msg)
    with repo.transaction(b'censor') as tr:
        flog.censorrevision(tr, file_nodes, tombstone=tombstone)
