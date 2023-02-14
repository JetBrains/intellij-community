# closehead.py - Close arbitrary heads without checking them out first
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''close arbitrary heads without checking them out first'''

from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import (
    bookmarks,
    cmdutil,
    context,
    error,
    pycompat,
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

commitopts = cmdutil.commitopts
commitopts2 = cmdutil.commitopts2
commitopts3 = [(b'r', b'rev', [], _(b'revision to check'), _(b'REV'))]


@command(
    b'close-head|close-heads',
    commitopts + commitopts2 + commitopts3,
    _(b'[OPTION]... [REV]...'),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
    inferrepo=True,
)
def close_branch(ui, repo, *revs, **opts):
    """close the given head revisions

    This is equivalent to checking out each revision in a clean tree and running
    ``hg commit --close-branch``, except that it doesn't change the working
    directory.

    The commit message must be specified with -l or -m.
    """

    def docommit(rev):
        cctx = context.memctx(
            repo,
            parents=[rev, None],
            text=message,
            files=[],
            filectxfn=None,
            user=opts.get(b'user'),
            date=opts.get(b'date'),
            extra=extra,
        )
        tr = repo.transaction(b'commit')
        ret = repo.commitctx(cctx, True)
        bookmarks.update(repo, [rev, None], ret)
        cctx.markcommitted(ret)
        tr.close()

    opts = pycompat.byteskwargs(opts)

    revs += tuple(opts.get(b'rev', []))
    revs = scmutil.revrange(repo, revs)

    if not revs:
        raise error.Abort(_(b'no revisions specified'))

    heads = []
    for branch in repo.branchmap():
        heads.extend(repo.branchheads(branch))
    heads = {repo[h].rev() for h in heads}
    for rev in revs:
        if rev not in heads:
            raise error.Abort(_(b'revision is not an open head: %d') % rev)

    message = cmdutil.logmessage(ui, opts)
    if not message:
        raise error.Abort(_(b"no commit message specified with -l or -m"))
    extra = {b'close': b'1'}

    with repo.wlock(), repo.lock():
        for rev in revs:
            r = repo[rev]
            branch = r.branch()
            extra[b'branch'] = branch
            docommit(r)
    return 0
