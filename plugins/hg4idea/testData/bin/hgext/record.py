# record.py
#
# Copyright 2007 Bryan O'Sullivan <bos@serpentine.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''commands to interactively select changes for commit/qrefresh (DEPRECATED)

The feature provided by this extension has been moved into core Mercurial as
:hg:`commit --interactive`.'''


from mercurial.i18n import _
from mercurial import (
    cmdutil,
    commands,
    error,
    extensions,
    registrar,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


@command(
    b"record",
    # same options as commit + white space diff options
    [c for c in commands.table[b'commit|ci'][1][:] if c[1] != b"interactive"]
    + cmdutil.diffwsopts,
    _(b'hg record [OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
)
def record(ui, repo, *pats, **opts):
    """interactively select changes to commit

    If a list of files is omitted, all changes reported by :hg:`status`
    will be candidates for recording.

    See :hg:`help dates` for a list of formats valid for -d/--date.

    If using the text interface (see :hg:`help config`),
    you will be prompted for whether to record changes to each
    modified file, and for files with multiple changes, for each
    change to use. For each query, the following responses are
    possible::

      y - record this change
      n - skip this change
      e - edit this change manually

      s - skip remaining changes to this file
      f - record remaining changes to this file

      d - done, skip remaining changes and files
      a - record all changes to all remaining files
      q - quit, recording no changes

      ? - display help

    This command is not available when committing a merge."""

    if not ui.interactive():
        raise error.Abort(
            _(b'running non-interactively, use %s instead') % b'commit'
        )

    opts["interactive"] = True
    overrides = {(b'experimental', b'crecord'): False}
    with ui.configoverride(overrides, b'record'):
        return commands.commit(ui, repo, *pats, **opts)


def qrefresh(origfn, ui, repo, *pats, **opts):
    if not opts['interactive']:
        return origfn(ui, repo, *pats, **opts)

    mq = extensions.find(b'mq')

    def committomq(ui, repo, *pats, **opts):
        # At this point the working copy contains only changes that
        # were accepted. All other changes were reverted.
        # We can't pass *pats here since qrefresh will undo all other
        # changed files in the patch that aren't in pats.
        mq.refresh(ui, repo, **opts)

    # backup all changed files
    cmdutil.dorecord(
        ui, repo, committomq, None, True, cmdutil.recordfilter, *pats, **opts
    )


# This command registration is replaced during uisetup().
@command(
    b'qrecord',
    [],
    _(b'hg qrecord [OPTION]... PATCH [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
    inferrepo=True,
)
def qrecord(ui, repo, patch, *pats, **opts):
    """interactively record a new patch

    See :hg:`help qnew` & :hg:`help record` for more information and
    usage.
    """
    return _qrecord(b'qnew', ui, repo, patch, *pats, **opts)


def _qrecord(cmdsuggest, ui, repo, patch, *pats, **opts):
    try:
        mq = extensions.find(b'mq')
    except KeyError:
        raise error.Abort(_(b"'mq' extension not loaded"))

    repo.mq.checkpatchname(patch)

    def committomq(ui, repo, *pats, **opts):
        opts['checkname'] = False
        mq.new(ui, repo, patch, *pats, **opts)

    overrides = {(b'experimental', b'crecord'): False}
    with ui.configoverride(overrides, b'record'):
        cmdutil.checkunfinished(repo)
        cmdutil.dorecord(
            ui,
            repo,
            committomq,
            cmdsuggest,
            False,
            cmdutil.recordfilter,
            *pats,
            **opts
        )


def qnew(origfn, ui, repo, patch, *args, **opts):
    if opts['interactive']:
        return _qrecord(None, ui, repo, patch, *args, **opts)
    return origfn(ui, repo, patch, *args, **opts)


def uisetup(ui):
    try:
        mq = extensions.find(b'mq')
    except KeyError:
        return

    cmdtable[b"qrecord"] = (
        qrecord,
        # same options as qnew, but copy them so we don't get
        # -i/--interactive for qrecord and add white space diff options
        mq.cmdtable[b'qnew'][1][:] + cmdutil.diffwsopts,
        _(b'hg qrecord [OPTION]... PATCH [FILE]...'),
    )

    _wrapcmd(b'qnew', mq.cmdtable, qnew, _(b"interactively record a new patch"))
    _wrapcmd(
        b'qrefresh',
        mq.cmdtable,
        qrefresh,
        _(b"interactively select changes to refresh"),
    )


def _wrapcmd(cmd, table, wrapfn, msg):
    entry = extensions.wrapcommand(table, cmd, wrapfn)
    entry[1].append((b'i', b'interactive', None, msg))
