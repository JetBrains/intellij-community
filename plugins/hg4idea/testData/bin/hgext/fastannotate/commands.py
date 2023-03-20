# Copyright 2016-present Facebook. All Rights Reserved.
#
# commands: fastannotate commands
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import os

from mercurial.i18n import _
from mercurial import (
    commands,
    encoding,
    error,
    extensions,
    patch,
    pycompat,
    registrar,
    scmutil,
    util,
)

from . import (
    context as facontext,
    error as faerror,
    formatter as faformatter,
)

cmdtable = {}
command = registrar.command(cmdtable)


def _matchpaths(repo, rev, pats, opts, aopts=facontext.defaultopts):
    """generate paths matching given patterns"""
    perfhack = repo.ui.configbool(b'fastannotate', b'perfhack')

    # disable perfhack if:
    # a) any walkopt is used
    # b) if we treat pats as plain file names, some of them do not have
    #    corresponding linelog files
    if perfhack:
        # cwd related to reporoot
        reporoot = os.path.dirname(repo.path)
        reldir = os.path.relpath(encoding.getcwd(), reporoot)
        if reldir == b'.':
            reldir = b''
        if any(opts.get(o[1]) for o in commands.walkopts):  # a)
            perfhack = False
        else:  # b)
            relpats = [
                os.path.relpath(p, reporoot) if os.path.isabs(p) else p
                for p in pats
            ]
            # disable perfhack on '..' since it allows escaping from the repo
            if any(
                (
                    b'..' in f
                    or not os.path.isfile(
                        facontext.pathhelper(repo, f, aopts).linelogpath
                    )
                )
                for f in relpats
            ):
                perfhack = False

    # perfhack: emit paths directory without checking with manifest
    # this can be incorrect if the rev dos not have file.
    if perfhack:
        for p in relpats:
            yield os.path.join(reldir, p)
    else:

        def bad(x, y):
            raise error.Abort(b"%s: %s" % (x, y))

        ctx = scmutil.revsingle(repo, rev)
        m = scmutil.match(ctx, pats, opts, badfn=bad)
        for p in ctx.walk(m):
            yield p


fastannotatecommandargs = {
    'options': [
        (b'r', b'rev', b'.', _(b'annotate the specified revision'), _(b'REV')),
        (b'u', b'user', None, _(b'list the author (long with -v)')),
        (b'f', b'file', None, _(b'list the filename')),
        (b'd', b'date', None, _(b'list the date (short with -q)')),
        (b'n', b'number', None, _(b'list the revision number (default)')),
        (b'c', b'changeset', None, _(b'list the changeset')),
        (
            b'l',
            b'line-number',
            None,
            _(b'show line number at the first appearance'),
        ),
        (
            b'e',
            b'deleted',
            None,
            _(b'show deleted lines (slow) (EXPERIMENTAL)'),
        ),
        (
            b'',
            b'no-content',
            None,
            _(b'do not show file content (EXPERIMENTAL)'),
        ),
        (b'', b'no-follow', None, _(b"don't follow copies and renames")),
        (
            b'',
            b'linear',
            None,
            _(
                b'enforce linear history, ignore second parent '
                b'of merges (EXPERIMENTAL)'
            ),
        ),
        (
            b'',
            b'long-hash',
            None,
            _(b'show long changeset hash (EXPERIMENTAL)'),
        ),
        (
            b'',
            b'rebuild',
            None,
            _(b'rebuild cache even if it exists (EXPERIMENTAL)'),
        ),
    ]
    + commands.diffwsopts
    + commands.walkopts
    + commands.formatteropts,
    'synopsis': _(b'[-r REV] [-f] [-a] [-u] [-d] [-n] [-c] [-l] FILE...'),
    'inferrepo': True,
}


def fastannotate(ui, repo, *pats, **opts):
    """show changeset information by line for each file

    List changes in files, showing the revision id responsible for each line.

    This command is useful for discovering when a change was made and by whom.

    By default this command prints revision numbers. If you include --file,
    --user, or --date, the revision number is suppressed unless you also
    include --number. The default format can also be customized by setting
    fastannotate.defaultformat.

    Returns 0 on success.

    .. container:: verbose

        This command uses an implementation different from the vanilla annotate
        command, which may produce slightly different (while still reasonable)
        outputs for some cases.

        Unlike the vanilla anootate, fastannotate follows rename regardless of
        the existence of --file.

        For the best performance when running on a full repo, use -c, -l,
        avoid -u, -d, -n. Use --linear and --no-content to make it even faster.

        For the best performance when running on a shallow (remotefilelog)
        repo, avoid --linear, --no-follow, or any diff options. As the server
        won't be able to populate annotate cache when non-default options
        affecting results are used.
    """
    if not pats:
        raise error.Abort(_(b'at least one filename or pattern is required'))

    # performance hack: filtered repo can be slow. unfilter by default.
    if ui.configbool(b'fastannotate', b'unfilteredrepo'):
        repo = repo.unfiltered()

    opts = pycompat.byteskwargs(opts)

    rev = opts.get(b'rev', b'.')
    rebuild = opts.get(b'rebuild', False)

    diffopts = patch.difffeatureopts(
        ui, opts, section=b'annotate', whitespace=True
    )
    aopts = facontext.annotateopts(
        diffopts=diffopts,
        followmerge=not opts.get(b'linear', False),
        followrename=not opts.get(b'no_follow', False),
    )

    if not any(
        opts.get(s)
        for s in [b'user', b'date', b'file', b'number', b'changeset']
    ):
        # default 'number' for compatibility. but fastannotate is more
        # efficient with "changeset", "line-number" and "no-content".
        for name in ui.configlist(
            b'fastannotate', b'defaultformat', [b'number']
        ):
            opts[name] = True

    ui.pager(b'fastannotate')
    template = opts.get(b'template')
    if template == b'json':
        formatter = faformatter.jsonformatter(ui, repo, opts)
    else:
        formatter = faformatter.defaultformatter(ui, repo, opts)
    showdeleted = opts.get(b'deleted', False)
    showlines = not bool(opts.get(b'no_content'))
    showpath = opts.get(b'file', False)

    # find the head of the main (master) branch
    master = ui.config(b'fastannotate', b'mainbranch') or rev

    # paths will be used for prefetching and the real annotating
    paths = list(_matchpaths(repo, rev, pats, opts, aopts))

    # for client, prefetch from the server
    if util.safehasattr(repo, 'prefetchfastannotate'):
        repo.prefetchfastannotate(paths)

    for path in paths:
        result = lines = existinglines = None
        while True:
            try:
                with facontext.annotatecontext(repo, path, aopts, rebuild) as a:
                    result = a.annotate(
                        rev,
                        master=master,
                        showpath=showpath,
                        showlines=(showlines and not showdeleted),
                    )
                    if showdeleted:
                        existinglines = {(l[0], l[1]) for l in result}
                        result = a.annotatealllines(
                            rev, showpath=showpath, showlines=showlines
                        )
                break
            except (faerror.CannotReuseError, faerror.CorruptedFileError):
                # happens if master moves backwards, or the file was deleted
                # and readded, or renamed to an existing name, or corrupted.
                if rebuild:  # give up since we have tried rebuild already
                    raise
                else:  # try a second time rebuilding the cache (slow)
                    rebuild = True
                    continue

        if showlines:
            result, lines = result

        formatter.write(result, lines, existinglines=existinglines)
    formatter.end()


_newopts = set()
_knownopts = {
    opt[1].replace(b'-', b'_')
    for opt in (fastannotatecommandargs['options'] + commands.globalopts)
}


def _annotatewrapper(orig, ui, repo, *pats, **opts):
    """used by wrapdefault"""
    # we need this hack until the obsstore has 0.0 seconds perf impact
    if ui.configbool(b'fastannotate', b'unfilteredrepo'):
        repo = repo.unfiltered()

    # treat the file as text (skip the isbinary check)
    if ui.configbool(b'fastannotate', b'forcetext'):
        opts['text'] = True

    # check if we need to do prefetch (client-side)
    rev = opts.get('rev')
    if util.safehasattr(repo, 'prefetchfastannotate') and rev is not None:
        paths = list(_matchpaths(repo, rev, pats, pycompat.byteskwargs(opts)))
        repo.prefetchfastannotate(paths)

    return orig(ui, repo, *pats, **opts)


def registercommand():
    """register the fastannotate command"""
    name = b'fastannotate|fastblame|fa'
    command(name, helpbasic=True, **fastannotatecommandargs)(fastannotate)


def wrapdefault():
    """wrap the default annotate command, to be aware of the protocol"""
    extensions.wrapcommand(commands.table, b'annotate', _annotatewrapper)


@command(
    b'debugbuildannotatecache',
    [(b'r', b'rev', b'', _(b'build up to the specific revision'), _(b'REV'))]
    + commands.walkopts,
    _(b'[-r REV] FILE...'),
)
def debugbuildannotatecache(ui, repo, *pats, **opts):
    """incrementally build fastannotate cache up to REV for specified files

    If REV is not specified, use the config 'fastannotate.mainbranch'.

    If fastannotate.client is True, download the annotate cache from the
    server. Otherwise, build the annotate cache locally.

    The annotate cache will be built using the default diff and follow
    options and lives in '.hg/fastannotate/default'.
    """
    opts = pycompat.byteskwargs(opts)
    rev = opts.get(b'REV') or ui.config(b'fastannotate', b'mainbranch')
    if not rev:
        raise error.Abort(
            _(b'you need to provide a revision'),
            hint=_(b'set fastannotate.mainbranch or use --rev'),
        )
    if ui.configbool(b'fastannotate', b'unfilteredrepo'):
        repo = repo.unfiltered()
    ctx = scmutil.revsingle(repo, rev)
    m = scmutil.match(ctx, pats, opts)
    paths = list(ctx.walk(m))
    if util.safehasattr(repo, 'prefetchfastannotate'):
        # client
        if opts.get(b'REV'):
            raise error.Abort(_(b'--rev cannot be used for client'))
        repo.prefetchfastannotate(paths)
    else:
        # server, or full repo
        progress = ui.makeprogress(_(b'building'), total=len(paths))
        for i, path in enumerate(paths):
            progress.update(i)
            with facontext.annotatecontext(repo, path) as actx:
                try:
                    if actx.isuptodate(rev):
                        continue
                    actx.annotate(rev, rev)
                except (faerror.CannotReuseError, faerror.CorruptedFileError):
                    # the cache is broken (could happen with renaming so the
                    # file history gets invalidated). rebuild and try again.
                    ui.debug(
                        b'fastannotate: %s: rebuilding broken cache\n' % path
                    )
                    actx.rebuild()
                    try:
                        actx.annotate(rev, rev)
                    except Exception as ex:
                        # possibly a bug, but should not stop us from building
                        # cache for other files.
                        ui.warn(
                            _(
                                b'fastannotate: %s: failed to '
                                b'build cache: %r\n'
                            )
                            % (path, ex)
                        )
        progress.complete()
