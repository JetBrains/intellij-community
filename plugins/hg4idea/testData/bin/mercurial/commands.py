# commands.py - command processing for mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import errno
import os
import re
import sys

from .i18n import _
from .node import (
    hex,
    nullrev,
    short,
    wdirrev,
)
from .pycompat import open
from . import (
    archival,
    bookmarks,
    bundle2,
    bundlecaches,
    changegroup,
    cmdutil,
    copies,
    debugcommands as debugcommandsmod,
    destutil,
    dirstateguard,
    discovery,
    encoding,
    error,
    exchange,
    extensions,
    filemerge,
    formatter,
    graphmod,
    grep as grepmod,
    hbisect,
    help,
    hg,
    logcmdutil,
    merge as mergemod,
    mergestate as mergestatemod,
    narrowspec,
    obsolete,
    obsutil,
    patch,
    phases,
    pycompat,
    rcutil,
    registrar,
    requirements,
    revsetlang,
    rewriteutil,
    scmutil,
    server,
    shelve as shelvemod,
    state as statemod,
    streamclone,
    tags as tagsmod,
    ui as uimod,
    util,
    verify as verifymod,
    vfs as vfsmod,
    wireprotoserver,
)
from .utils import (
    dateutil,
    stringutil,
    urlutil,
)

table = {}
table.update(debugcommandsmod.command._table)

command = registrar.command(table)
INTENT_READONLY = registrar.INTENT_READONLY

# common command options

globalopts = [
    (
        b'R',
        b'repository',
        b'',
        _(b'repository root directory or name of overlay bundle file'),
        _(b'REPO'),
    ),
    (b'', b'cwd', b'', _(b'change working directory'), _(b'DIR')),
    (
        b'y',
        b'noninteractive',
        None,
        _(
            b'do not prompt, automatically pick the first choice for all prompts'
        ),
    ),
    (b'q', b'quiet', None, _(b'suppress output')),
    (b'v', b'verbose', None, _(b'enable additional output')),
    (
        b'',
        b'color',
        b'',
        # i18n: 'always', 'auto', 'never', and 'debug' are keywords
        # and should not be translated
        _(b"when to colorize (boolean, always, auto, never, or debug)"),
        _(b'TYPE'),
    ),
    (
        b'',
        b'config',
        [],
        _(b'set/override config option (use \'section.name=value\')'),
        _(b'CONFIG'),
    ),
    (b'', b'debug', None, _(b'enable debugging output')),
    (b'', b'debugger', None, _(b'start debugger')),
    (
        b'',
        b'encoding',
        encoding.encoding,
        _(b'set the charset encoding'),
        _(b'ENCODE'),
    ),
    (
        b'',
        b'encodingmode',
        encoding.encodingmode,
        _(b'set the charset encoding mode'),
        _(b'MODE'),
    ),
    (b'', b'traceback', None, _(b'always print a traceback on exception')),
    (b'', b'time', None, _(b'time how long the command takes')),
    (b'', b'profile', None, _(b'print command execution profile')),
    (b'', b'version', None, _(b'output version information and exit')),
    (b'h', b'help', None, _(b'display help and exit')),
    (b'', b'hidden', False, _(b'consider hidden changesets')),
    (
        b'',
        b'pager',
        b'auto',
        _(b"when to paginate (boolean, always, auto, or never)"),
        _(b'TYPE'),
    ),
]

dryrunopts = cmdutil.dryrunopts
remoteopts = cmdutil.remoteopts
walkopts = cmdutil.walkopts
commitopts = cmdutil.commitopts
commitopts2 = cmdutil.commitopts2
commitopts3 = cmdutil.commitopts3
formatteropts = cmdutil.formatteropts
templateopts = cmdutil.templateopts
logopts = cmdutil.logopts
diffopts = cmdutil.diffopts
diffwsopts = cmdutil.diffwsopts
diffopts2 = cmdutil.diffopts2
mergetoolopts = cmdutil.mergetoolopts
similarityopts = cmdutil.similarityopts
subrepoopts = cmdutil.subrepoopts
debugrevlogopts = cmdutil.debugrevlogopts

# Commands start here, listed alphabetically


@command(
    b'abort',
    dryrunopts,
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
    helpbasic=True,
)
def abort(ui, repo, **opts):
    """abort an unfinished operation (EXPERIMENTAL)

    Aborts a multistep operation like graft, histedit, rebase, merge,
    and unshelve if they are in an unfinished state.

    use --dry-run/-n to dry run the command.
    """
    dryrun = opts.get('dry_run')
    abortstate = cmdutil.getunfinishedstate(repo)
    if not abortstate:
        raise error.StateError(_(b'no operation in progress'))
    if not abortstate.abortfunc:
        raise error.InputError(
            (
                _(b"%s in progress but does not support 'hg abort'")
                % (abortstate._opname)
            ),
            hint=abortstate.hint(),
        )
    if dryrun:
        ui.status(
            _(b'%s in progress, will be aborted\n') % (abortstate._opname)
        )
        return
    return abortstate.abortfunc(ui, repo)


@command(
    b'add',
    walkopts + subrepoopts + dryrunopts,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    helpbasic=True,
    inferrepo=True,
)
def add(ui, repo, *pats, **opts):
    """add the specified files on the next commit

    Schedule files to be version controlled and added to the
    repository.

    The files will be added to the repository at the next commit. To
    undo an add before that, see :hg:`forget`.

    If no names are given, add all files to the repository (except
    files matching ``.hgignore``).

    .. container:: verbose

       Examples:

         - New (unknown) files are added
           automatically by :hg:`add`::

             $ ls
             foo.c
             $ hg status
             ? foo.c
             $ hg add
             adding foo.c
             $ hg status
             A foo.c

         - Specific files to be added can be specified::

             $ ls
             bar.c  foo.c
             $ hg status
             ? bar.c
             ? foo.c
             $ hg add bar.c
             $ hg status
             A bar.c
             ? foo.c

    Returns 0 if all files are successfully added.
    """

    m = scmutil.match(repo[None], pats, pycompat.byteskwargs(opts))
    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)
    rejected = cmdutil.add(ui, repo, m, b"", uipathfn, False, **opts)
    return rejected and 1 or 0


@command(
    b'addremove',
    similarityopts + subrepoopts + walkopts + dryrunopts,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    inferrepo=True,
)
def addremove(ui, repo, *pats, **opts):
    """add all new files, delete all missing files

    Add all new files and remove all missing files from the
    repository.

    Unless names are given, new files are ignored if they match any of
    the patterns in ``.hgignore``. As with add, these changes take
    effect at the next commit.

    Use the -s/--similarity option to detect renamed files. This
    option takes a percentage between 0 (disabled) and 100 (files must
    be identical) as its parameter. With a parameter greater than 0,
    this compares every removed file with every added file and records
    those similar enough as renames. Detecting renamed files this way
    can be expensive. After using this option, :hg:`status -C` can be
    used to check which files were identified as moved or renamed. If
    not specified, -s/--similarity defaults to 100 and only renames of
    identical files are detected.

    .. container:: verbose

       Examples:

         - A number of files (bar.c and foo.c) are new,
           while foobar.c has been removed (without using :hg:`remove`)
           from the repository::

             $ ls
             bar.c foo.c
             $ hg status
             ! foobar.c
             ? bar.c
             ? foo.c
             $ hg addremove
             adding bar.c
             adding foo.c
             removing foobar.c
             $ hg status
             A bar.c
             A foo.c
             R foobar.c

         - A file foobar.c was moved to foo.c without using :hg:`rename`.
           Afterwards, it was edited slightly::

             $ ls
             foo.c
             $ hg status
             ! foobar.c
             ? foo.c
             $ hg addremove --similarity 90
             removing foobar.c
             adding foo.c
             recording removal of foobar.c as rename to foo.c (94% similar)
             $ hg status -C
             A foo.c
               foobar.c
             R foobar.c

    Returns 0 if all files are successfully added.
    """
    opts = pycompat.byteskwargs(opts)
    if not opts.get(b'similarity'):
        opts[b'similarity'] = b'100'
    matcher = scmutil.match(repo[None], pats, opts)
    relative = scmutil.anypats(pats, opts)
    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=relative)
    return scmutil.addremove(repo, matcher, b"", uipathfn, opts)


@command(
    b'annotate|blame',
    [
        (b'r', b'rev', b'', _(b'annotate the specified revision'), _(b'REV')),
        (
            b'',
            b'follow',
            None,
            _(b'follow copies/renames and list the filename (DEPRECATED)'),
        ),
        (b'', b'no-follow', None, _(b"don't follow copies and renames")),
        (b'a', b'text', None, _(b'treat all files as text')),
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
            b'',
            b'skip',
            [],
            _(b'revset to not display (EXPERIMENTAL)'),
            _(b'REV'),
        ),
    ]
    + diffwsopts
    + walkopts
    + formatteropts,
    _(b'[-r REV] [-f] [-a] [-u] [-d] [-n] [-c] [-l] FILE...'),
    helpcategory=command.CATEGORY_FILE_CONTENTS,
    helpbasic=True,
    inferrepo=True,
)
def annotate(ui, repo, *pats, **opts):
    """show changeset information by line for each file

    List changes in files, showing the revision id responsible for
    each line.

    This command is useful for discovering when a change was made and
    by whom.

    If you include --file, --user, or --date, the revision number is
    suppressed unless you also include --number.

    Without the -a/--text option, annotate will avoid processing files
    it detects as binary. With -a, annotate will annotate the file
    anyway, although the results will probably be neither useful
    nor desirable.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :lines:   List of lines with annotation data.
      :path:    String. Repository-absolute path of the specified file.

      And each entry of ``{lines}`` provides the following sub-keywords in
      addition to ``{date}``, ``{node}``, ``{rev}``, ``{user}``, etc.

      :line:    String. Line content.
      :lineno:  Integer. Line number at that revision.
      :path:    String. Repository-absolute path of the file at that revision.

      See :hg:`help templates.operators` for the list expansion syntax.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    if not pats:
        raise error.InputError(
            _(b'at least one filename or pattern is required')
        )

    if opts.get(b'follow'):
        # --follow is deprecated and now just an alias for -f/--file
        # to mimic the behavior of Mercurial before version 1.5
        opts[b'file'] = True

    if (
        not opts.get(b'user')
        and not opts.get(b'changeset')
        and not opts.get(b'date')
        and not opts.get(b'file')
    ):
        opts[b'number'] = True

    linenumber = opts.get(b'line_number') is not None
    if (
        linenumber
        and (not opts.get(b'changeset'))
        and (not opts.get(b'number'))
    ):
        raise error.InputError(_(b'at least one of -n/-c is required for -l'))

    rev = opts.get(b'rev')
    if rev:
        repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
    ctx = scmutil.revsingle(repo, rev)

    ui.pager(b'annotate')
    rootfm = ui.formatter(b'annotate', opts)
    if ui.debugflag:
        shorthex = pycompat.identity
    else:

        def shorthex(h):
            return h[:12]

    if ui.quiet:
        datefunc = dateutil.shortdate
    else:
        datefunc = dateutil.datestr
    if ctx.rev() is None:
        if opts.get(b'changeset'):
            # omit "+" suffix which is appended to node hex
            def formatrev(rev):
                if rev == wdirrev:
                    return b'%d' % ctx.p1().rev()
                else:
                    return b'%d' % rev

        else:

            def formatrev(rev):
                if rev == wdirrev:
                    return b'%d+' % ctx.p1().rev()
                else:
                    return b'%d ' % rev

        def formathex(h):
            if h == repo.nodeconstants.wdirhex:
                return b'%s+' % shorthex(hex(ctx.p1().node()))
            else:
                return b'%s ' % shorthex(h)

    else:
        formatrev = b'%d'.__mod__
        formathex = shorthex

    opmap = [
        (b'user', b' ', lambda x: x.fctx.user(), ui.shortuser),
        (b'rev', b' ', lambda x: scmutil.intrev(x.fctx), formatrev),
        (b'node', b' ', lambda x: hex(scmutil.binnode(x.fctx)), formathex),
        (b'date', b' ', lambda x: x.fctx.date(), util.cachefunc(datefunc)),
        (b'path', b' ', lambda x: x.fctx.path(), pycompat.bytestr),
        (b'lineno', b':', lambda x: x.lineno, pycompat.bytestr),
    ]
    opnamemap = {
        b'rev': b'number',
        b'node': b'changeset',
        b'path': b'file',
        b'lineno': b'line_number',
    }

    if rootfm.isplain():

        def makefunc(get, fmt):
            return lambda x: fmt(get(x))

    else:

        def makefunc(get, fmt):
            return get

    datahint = rootfm.datahint()
    funcmap = [
        (makefunc(get, fmt), sep)
        for fn, sep, get, fmt in opmap
        if opts.get(opnamemap.get(fn, fn)) or fn in datahint
    ]
    funcmap[0] = (funcmap[0][0], b'')  # no separator in front of first column
    fields = b' '.join(
        fn
        for fn, sep, get, fmt in opmap
        if opts.get(opnamemap.get(fn, fn)) or fn in datahint
    )

    def bad(x, y):
        raise error.Abort(b"%s: %s" % (x, y))

    m = scmutil.match(ctx, pats, opts, badfn=bad)

    follow = not opts.get(b'no_follow')
    diffopts = patch.difffeatureopts(
        ui, opts, section=b'annotate', whitespace=True
    )
    skiprevs = opts.get(b'skip')
    if skiprevs:
        skiprevs = scmutil.revrange(repo, skiprevs)

    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)
    for abs in ctx.walk(m):
        fctx = ctx[abs]
        rootfm.startitem()
        rootfm.data(path=abs)
        if not opts.get(b'text') and fctx.isbinary():
            rootfm.plain(_(b"%s: binary file\n") % uipathfn(abs))
            continue

        fm = rootfm.nested(b'lines', tmpl=b'{rev}: {line}')
        lines = fctx.annotate(
            follow=follow, skiprevs=skiprevs, diffopts=diffopts
        )
        if not lines:
            fm.end()
            continue
        formats = []
        pieces = []

        for f, sep in funcmap:
            l = [f(n) for n in lines]
            if fm.isplain():
                sizes = [encoding.colwidth(x) for x in l]
                ml = max(sizes)
                formats.append([sep + b' ' * (ml - w) + b'%s' for w in sizes])
            else:
                formats.append([b'%s'] * len(l))
            pieces.append(l)

        for f, p, n in zip(zip(*formats), zip(*pieces), lines):
            fm.startitem()
            fm.context(fctx=n.fctx)
            fm.write(fields, b"".join(f), *p)
            if n.skip:
                fmt = b"* %s"
            else:
                fmt = b": %s"
            fm.write(b'line', fmt, n.text)

        if not lines[-1].text.endswith(b'\n'):
            fm.plain(b'\n')
        fm.end()

    rootfm.end()


@command(
    b'archive',
    [
        (b'', b'no-decode', None, _(b'do not pass files through decoders')),
        (
            b'p',
            b'prefix',
            b'',
            _(b'directory prefix for files in archive'),
            _(b'PREFIX'),
        ),
        (b'r', b'rev', b'', _(b'revision to distribute'), _(b'REV')),
        (b't', b'type', b'', _(b'type of distribution to create'), _(b'TYPE')),
    ]
    + subrepoopts
    + walkopts,
    _(b'[OPTION]... DEST'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def archive(ui, repo, dest, **opts):
    """create an unversioned archive of a repository revision

    By default, the revision used is the parent of the working
    directory; use -r/--rev to specify a different revision.

    The archive type is automatically detected based on file
    extension (to override, use -t/--type).

    .. container:: verbose

      Examples:

      - create a zip file containing the 1.0 release::

          hg archive -r 1.0 project-1.0.zip

      - create a tarball excluding .hg files::

          hg archive project.tar.gz -X ".hg*"

    Valid types are:

    :``files``: a directory full of files (default)
    :``tar``:   tar archive, uncompressed
    :``tbz2``:  tar archive, compressed using bzip2
    :``tgz``:   tar archive, compressed using gzip
    :``txz``:   tar archive, compressed using lzma (only in Python 3)
    :``uzip``:  zip archive, uncompressed
    :``zip``:   zip archive, compressed using deflate

    The exact name of the destination archive or directory is given
    using a format string; see :hg:`help export` for details.

    Each member added to an archive file has a directory prefix
    prepended. Use -p/--prefix to specify a format string for the
    prefix. The default is the basename of the archive, with suffixes
    removed.

    Returns 0 on success.
    """

    opts = pycompat.byteskwargs(opts)
    rev = opts.get(b'rev')
    if rev:
        repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
    ctx = scmutil.revsingle(repo, rev)
    if not ctx:
        raise error.InputError(
            _(b'no working directory: please specify a revision')
        )
    node = ctx.node()
    dest = cmdutil.makefilename(ctx, dest)
    if os.path.realpath(dest) == repo.root:
        raise error.InputError(_(b'repository root cannot be destination'))

    kind = opts.get(b'type') or archival.guesskind(dest) or b'files'
    prefix = opts.get(b'prefix')

    if dest == b'-':
        if kind == b'files':
            raise error.InputError(_(b'cannot archive plain files to stdout'))
        dest = cmdutil.makefileobj(ctx, dest)
        if not prefix:
            prefix = os.path.basename(repo.root) + b'-%h'

    prefix = cmdutil.makefilename(ctx, prefix)
    match = scmutil.match(ctx, [], opts)
    archival.archive(
        repo,
        dest,
        node,
        kind,
        not opts.get(b'no_decode'),
        match,
        prefix,
        subrepos=opts.get(b'subrepos'),
    )


@command(
    b'backout',
    [
        (
            b'',
            b'merge',
            None,
            _(b'merge with old dirstate parent after backout'),
        ),
        (
            b'',
            b'commit',
            None,
            _(b'commit if no conflicts were encountered (DEPRECATED)'),
        ),
        (b'', b'no-commit', None, _(b'do not commit')),
        (
            b'',
            b'parent',
            b'',
            _(b'parent to choose when backing out merge (DEPRECATED)'),
            _(b'REV'),
        ),
        (b'r', b'rev', b'', _(b'revision to backout'), _(b'REV')),
        (b'e', b'edit', False, _(b'invoke editor on commit messages')),
    ]
    + mergetoolopts
    + walkopts
    + commitopts
    + commitopts2,
    _(b'[OPTION]... [-r] REV'),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
)
def backout(ui, repo, node=None, rev=None, **opts):
    """reverse effect of earlier changeset

    Prepare a new changeset with the effect of REV undone in the
    current working directory. If no conflicts were encountered,
    it will be committed immediately.

    If REV is the parent of the working directory, then this new changeset
    is committed automatically (unless --no-commit is specified).

    .. note::

       :hg:`backout` cannot be used to fix either an unwanted or
       incorrect merge.

    .. container:: verbose

      Examples:

      - Reverse the effect of the parent of the working directory.
        This backout will be committed immediately::

          hg backout -r .

      - Reverse the effect of previous bad revision 23::

          hg backout -r 23

      - Reverse the effect of previous bad revision 23 and
        leave changes uncommitted::

          hg backout -r 23 --no-commit
          hg commit -m "Backout revision 23"

      By default, the pending changeset will have one parent,
      maintaining a linear history. With --merge, the pending
      changeset will instead have two parents: the old parent of the
      working directory and a new child of REV that simply undoes REV.

      Before version 1.7, the behavior without --merge was equivalent
      to specifying --merge followed by :hg:`update --clean .` to
      cancel the merge and leave the child of REV as a head to be
      merged separately.

    See :hg:`help dates` for a list of formats valid for -d/--date.

    See :hg:`help revert` for a way to restore files to the state
    of another revision.

    Returns 0 on success, 1 if nothing to backout or there are unresolved
    files.
    """
    with repo.wlock(), repo.lock():
        return _dobackout(ui, repo, node, rev, **opts)


def _dobackout(ui, repo, node=None, rev=None, **opts):
    cmdutil.check_incompatible_arguments(opts, 'no_commit', ['commit', 'merge'])
    opts = pycompat.byteskwargs(opts)

    if rev and node:
        raise error.InputError(_(b"please specify just one revision"))

    if not rev:
        rev = node

    if not rev:
        raise error.InputError(_(b"please specify a revision to backout"))

    date = opts.get(b'date')
    if date:
        opts[b'date'] = dateutil.parsedate(date)

    cmdutil.checkunfinished(repo)
    cmdutil.bailifchanged(repo)
    ctx = scmutil.revsingle(repo, rev)
    node = ctx.node()

    op1, op2 = repo.dirstate.parents()
    if not repo.changelog.isancestor(node, op1):
        raise error.InputError(
            _(b'cannot backout change that is not an ancestor')
        )

    p1, p2 = repo.changelog.parents(node)
    if p1 == repo.nullid:
        raise error.InputError(_(b'cannot backout a change with no parents'))
    if p2 != repo.nullid:
        if not opts.get(b'parent'):
            raise error.InputError(_(b'cannot backout a merge changeset'))
        p = repo.lookup(opts[b'parent'])
        if p not in (p1, p2):
            raise error.InputError(
                _(b'%s is not a parent of %s') % (short(p), short(node))
            )
        parent = p
    else:
        if opts.get(b'parent'):
            raise error.InputError(
                _(b'cannot use --parent on non-merge changeset')
            )
        parent = p1

    # the backout should appear on the same branch
    branch = repo.dirstate.branch()
    bheads = repo.branchheads(branch)
    rctx = scmutil.revsingle(repo, hex(parent))
    if not opts.get(b'merge') and op1 != node:
        with dirstateguard.dirstateguard(repo, b'backout'):
            overrides = {(b'ui', b'forcemerge'): opts.get(b'tool', b'')}
            with ui.configoverride(overrides, b'backout'):
                stats = mergemod.back_out(ctx, parent=repo[parent])
            repo.setparents(op1, op2)
        hg._showstats(repo, stats)
        if stats.unresolvedcount:
            repo.ui.status(
                _(b"use 'hg resolve' to retry unresolved file merges\n")
            )
            return 1
    else:
        hg.clean(repo, node, show_stats=False)
        repo.dirstate.setbranch(branch)
        cmdutil.revert(ui, repo, rctx)

    if opts.get(b'no_commit'):
        msg = _(b"changeset %s backed out, don't forget to commit.\n")
        ui.status(msg % short(node))
        return 0

    def commitfunc(ui, repo, message, match, opts):
        editform = b'backout'
        e = cmdutil.getcommiteditor(
            editform=editform, **pycompat.strkwargs(opts)
        )
        if not message:
            # we don't translate commit messages
            message = b"Backed out changeset %s" % short(node)
            e = cmdutil.getcommiteditor(edit=True, editform=editform)
        return repo.commit(
            message, opts.get(b'user'), opts.get(b'date'), match, editor=e
        )

    # save to detect changes
    tip = repo.changelog.tip()

    newnode = cmdutil.commit(ui, repo, commitfunc, [], opts)
    if not newnode:
        ui.status(_(b"nothing changed\n"))
        return 1
    cmdutil.commitstatus(repo, newnode, branch, bheads, tip)

    def nice(node):
        return b'%d:%s' % (repo.changelog.rev(node), short(node))

    ui.status(
        _(b'changeset %s backs out changeset %s\n')
        % (nice(newnode), nice(node))
    )
    if opts.get(b'merge') and op1 != node:
        hg.clean(repo, op1, show_stats=False)
        ui.status(_(b'merging with changeset %s\n') % nice(newnode))
        overrides = {(b'ui', b'forcemerge'): opts.get(b'tool', b'')}
        with ui.configoverride(overrides, b'backout'):
            return hg.merge(repo[b'tip'])
    return 0


@command(
    b'bisect',
    [
        (b'r', b'reset', False, _(b'reset bisect state')),
        (b'g', b'good', False, _(b'mark changeset good')),
        (b'b', b'bad', False, _(b'mark changeset bad')),
        (b's', b'skip', False, _(b'skip testing changeset')),
        (b'e', b'extend', False, _(b'extend the bisect range')),
        (
            b'c',
            b'command',
            b'',
            _(b'use command to check changeset state'),
            _(b'CMD'),
        ),
        (b'U', b'noupdate', False, _(b'do not update to target')),
    ],
    _(b"[-gbsr] [-U] [-c CMD] [REV]"),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
)
def bisect(
    ui,
    repo,
    positional_1=None,
    positional_2=None,
    command=None,
    reset=None,
    good=None,
    bad=None,
    skip=None,
    extend=None,
    noupdate=None,
):
    """subdivision search of changesets

    This command helps to find changesets which introduce problems. To
    use, mark the earliest changeset you know exhibits the problem as
    bad, then mark the latest changeset which is free from the problem
    as good. Bisect will update your working directory to a revision
    for testing (unless the -U/--noupdate option is specified). Once
    you have performed tests, mark the working directory as good or
    bad, and bisect will either update to another candidate changeset
    or announce that it has found the bad revision.

    As a shortcut, you can also use the revision argument to mark a
    revision as good or bad without checking it out first.

    If you supply a command, it will be used for automatic bisection.
    The environment variable HG_NODE will contain the ID of the
    changeset being tested. The exit status of the command will be
    used to mark revisions as good or bad: status 0 means good, 125
    means to skip the revision, 127 (command not found) will abort the
    bisection, and any other non-zero exit status means the revision
    is bad.

    .. container:: verbose

      Some examples:

      - start a bisection with known bad revision 34, and good revision 12::

          hg bisect --bad 34
          hg bisect --good 12

      - advance the current bisection by marking current revision as good or
        bad::

          hg bisect --good
          hg bisect --bad

      - mark the current revision, or a known revision, to be skipped (e.g. if
        that revision is not usable because of another issue)::

          hg bisect --skip
          hg bisect --skip 23

      - skip all revisions that do not touch directories ``foo`` or ``bar``::

          hg bisect --skip "!( file('path:foo') & file('path:bar') )"

      - forget the current bisection::

          hg bisect --reset

      - use 'make && make tests' to automatically find the first broken
        revision::

          hg bisect --reset
          hg bisect --bad 34
          hg bisect --good 12
          hg bisect --command "make && make tests"

      - see all changesets whose states are already known in the current
        bisection::

          hg log -r "bisect(pruned)"

      - see the changeset currently being bisected (especially useful
        if running with -U/--noupdate)::

          hg log -r "bisect(current)"

      - see all changesets that took part in the current bisection::

          hg log -r "bisect(range)"

      - you can even get a nice graph::

          hg log --graph -r "bisect(range)"

      See :hg:`help revisions.bisect` for more about the `bisect()` predicate.

    Returns 0 on success.
    """
    rev = []
    # backward compatibility
    if positional_1 in (b"good", b"bad", b"reset", b"init"):
        ui.warn(_(b"(use of 'hg bisect <cmd>' is deprecated)\n"))
        cmd = positional_1
        rev.append(positional_2)
        if cmd == b"good":
            good = True
        elif cmd == b"bad":
            bad = True
        else:
            reset = True
    elif positional_2:
        raise error.InputError(_(b'incompatible arguments'))
    elif positional_1 is not None:
        rev.append(positional_1)

    incompatibles = {
        b'--bad': bad,
        b'--command': bool(command),
        b'--extend': extend,
        b'--good': good,
        b'--reset': reset,
        b'--skip': skip,
    }

    enabled = [x for x in incompatibles if incompatibles[x]]

    if len(enabled) > 1:
        raise error.InputError(
            _(b'%s and %s are incompatible') % tuple(sorted(enabled)[0:2])
        )

    if reset:
        hbisect.resetstate(repo)
        return

    state = hbisect.load_state(repo)

    if rev:
        nodes = [repo[i].node() for i in scmutil.revrange(repo, rev)]
    else:
        nodes = [repo.lookup(b'.')]

    # update state
    if good or bad or skip:
        if good:
            state[b'good'] += nodes
        elif bad:
            state[b'bad'] += nodes
        elif skip:
            state[b'skip'] += nodes
        hbisect.save_state(repo, state)
        if not (state[b'good'] and state[b'bad']):
            return

    def mayupdate(repo, node, show_stats=True):
        """common used update sequence"""
        if noupdate:
            return
        cmdutil.checkunfinished(repo)
        cmdutil.bailifchanged(repo)
        return hg.clean(repo, node, show_stats=show_stats)

    displayer = logcmdutil.changesetdisplayer(ui, repo, {})

    if command:
        changesets = 1
        if noupdate:
            try:
                node = state[b'current'][0]
            except LookupError:
                raise error.StateError(
                    _(
                        b'current bisect revision is unknown - '
                        b'start a new bisect to fix'
                    )
                )
        else:
            node, p2 = repo.dirstate.parents()
            if p2 != repo.nullid:
                raise error.StateError(_(b'current bisect revision is a merge'))
        if rev:
            if not nodes:
                raise error.Abort(_(b'empty revision set'))
            node = repo[nodes[-1]].node()
        with hbisect.restore_state(repo, state, node):
            while changesets:
                # update state
                state[b'current'] = [node]
                hbisect.save_state(repo, state)
                status = ui.system(
                    command,
                    environ={b'HG_NODE': hex(node)},
                    blockedtag=b'bisect_check',
                )
                if status == 125:
                    transition = b"skip"
                elif status == 0:
                    transition = b"good"
                # status < 0 means process was killed
                elif status == 127:
                    raise error.Abort(_(b"failed to execute %s") % command)
                elif status < 0:
                    raise error.Abort(_(b"%s killed") % command)
                else:
                    transition = b"bad"
                state[transition].append(node)
                ctx = repo[node]
                summary = cmdutil.format_changeset_summary(ui, ctx, b'bisect')
                ui.status(_(b'changeset %s: %s\n') % (summary, transition))
                hbisect.checkstate(state)
                # bisect
                nodes, changesets, bgood = hbisect.bisect(repo, state)
                # update to next check
                node = nodes[0]
                mayupdate(repo, node, show_stats=False)
        hbisect.printresult(ui, repo, state, displayer, nodes, bgood)
        return

    hbisect.checkstate(state)

    # actually bisect
    nodes, changesets, good = hbisect.bisect(repo, state)
    if extend:
        if not changesets:
            extendctx = hbisect.extendrange(repo, state, nodes, good)
            if extendctx is not None:
                ui.write(
                    _(b"Extending search to changeset %s\n")
                    % cmdutil.format_changeset_summary(ui, extendctx, b'bisect')
                )
                state[b'current'] = [extendctx.node()]
                hbisect.save_state(repo, state)
                return mayupdate(repo, extendctx.node())
        raise error.StateError(_(b"nothing to extend"))

    if changesets == 0:
        hbisect.printresult(ui, repo, state, displayer, nodes, good)
    else:
        assert len(nodes) == 1  # only a single node can be tested next
        node = nodes[0]
        # compute the approximate number of remaining tests
        tests, size = 0, 2
        while size <= changesets:
            tests, size = tests + 1, size * 2
        rev = repo.changelog.rev(node)
        summary = cmdutil.format_changeset_summary(ui, repo[rev], b'bisect')
        ui.write(
            _(
                b"Testing changeset %s "
                b"(%d changesets remaining, ~%d tests)\n"
            )
            % (summary, changesets, tests)
        )
        state[b'current'] = [node]
        hbisect.save_state(repo, state)
        return mayupdate(repo, node)


@command(
    b'bookmarks|bookmark',
    [
        (b'f', b'force', False, _(b'force')),
        (b'r', b'rev', b'', _(b'revision for bookmark action'), _(b'REV')),
        (b'd', b'delete', False, _(b'delete a given bookmark')),
        (b'm', b'rename', b'', _(b'rename a given bookmark'), _(b'OLD')),
        (b'i', b'inactive', False, _(b'mark a bookmark inactive')),
        (b'l', b'list', False, _(b'list existing bookmarks')),
    ]
    + formatteropts,
    _(b'hg bookmarks [OPTIONS]... [NAME]...'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def bookmark(ui, repo, *names, **opts):
    """create a new bookmark or list existing bookmarks

    Bookmarks are labels on changesets to help track lines of development.
    Bookmarks are unversioned and can be moved, renamed and deleted.
    Deleting or moving a bookmark has no effect on the associated changesets.

    Creating or updating to a bookmark causes it to be marked as 'active'.
    The active bookmark is indicated with a '*'.
    When a commit is made, the active bookmark will advance to the new commit.
    A plain :hg:`update` will also advance an active bookmark, if possible.
    Updating away from a bookmark will cause it to be deactivated.

    Bookmarks can be pushed and pulled between repositories (see
    :hg:`help push` and :hg:`help pull`). If a shared bookmark has
    diverged, a new 'divergent bookmark' of the form 'name@path' will
    be created. Using :hg:`merge` will resolve the divergence.

    Specifying bookmark as '.' to -m/-d/-l options is equivalent to specifying
    the active bookmark's name.

    A bookmark named '@' has the special property that :hg:`clone` will
    check it out by default if it exists.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions such as ``{bookmark}``. See also
      :hg:`help templates`.

      :active:  Boolean. True if the bookmark is active.

      Examples:

      - create an active bookmark for a new line of development::

          hg book new-feature

      - create an inactive bookmark as a place marker::

          hg book -i reviewed

      - create an inactive bookmark on another changeset::

          hg book -r .^ tested

      - rename bookmark turkey to dinner::

          hg book -m turkey dinner

      - move the '@' bookmark from another branch::

          hg book -f @

      - print only the active bookmark name::

          hg book -ql .
    """
    opts = pycompat.byteskwargs(opts)
    force = opts.get(b'force')
    rev = opts.get(b'rev')
    inactive = opts.get(b'inactive')  # meaning add/rename to inactive bookmark

    action = cmdutil.check_at_most_one_arg(opts, b'delete', b'rename', b'list')
    if action:
        cmdutil.check_incompatible_arguments(opts, action, [b'rev'])
    elif names or rev:
        action = b'add'
    elif inactive:
        action = b'inactive'  # meaning deactivate
    else:
        action = b'list'

    cmdutil.check_incompatible_arguments(
        opts, b'inactive', [b'delete', b'list']
    )
    if not names and action in {b'add', b'delete'}:
        raise error.InputError(_(b"bookmark name required"))

    if action in {b'add', b'delete', b'rename', b'inactive'}:
        with repo.wlock(), repo.lock(), repo.transaction(b'bookmark') as tr:
            if action == b'delete':
                names = pycompat.maplist(repo._bookmarks.expandname, names)
                bookmarks.delete(repo, tr, names)
            elif action == b'rename':
                if not names:
                    raise error.InputError(_(b"new bookmark name required"))
                elif len(names) > 1:
                    raise error.InputError(
                        _(b"only one new bookmark name allowed")
                    )
                oldname = repo._bookmarks.expandname(opts[b'rename'])
                bookmarks.rename(repo, tr, oldname, names[0], force, inactive)
            elif action == b'add':
                bookmarks.addbookmarks(repo, tr, names, rev, force, inactive)
            elif action == b'inactive':
                if len(repo._bookmarks) == 0:
                    ui.status(_(b"no bookmarks set\n"))
                elif not repo._activebookmark:
                    ui.status(_(b"no active bookmark\n"))
                else:
                    bookmarks.deactivate(repo)
    elif action == b'list':
        names = pycompat.maplist(repo._bookmarks.expandname, names)
        with ui.formatter(b'bookmarks', opts) as fm:
            bookmarks.printbookmarks(ui, repo, fm, names)
    else:
        raise error.ProgrammingError(b'invalid action: %s' % action)


@command(
    b'branch',
    [
        (
            b'f',
            b'force',
            None,
            _(b'set branch name even if it shadows an existing branch'),
        ),
        (b'C', b'clean', None, _(b'reset branch name to parent branch name')),
        (
            b'r',
            b'rev',
            [],
            _(b'change branches of the given revs (EXPERIMENTAL)'),
        ),
    ],
    _(b'[-fC] [NAME]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def branch(ui, repo, label=None, **opts):
    """set or show the current branch name

    .. note::

       Branch names are permanent and global. Use :hg:`bookmark` to create a
       light-weight bookmark instead. See :hg:`help glossary` for more
       information about named branches and bookmarks.

    With no argument, show the current branch name. With one argument,
    set the working directory branch name (the branch will not exist
    in the repository until the next commit). Standard practice
    recommends that primary development take place on the 'default'
    branch.

    Unless -f/--force is specified, branch will not let you set a
    branch name that already exists.

    Use -C/--clean to reset the working directory branch to that of
    the parent of the working directory, negating a previous branch
    change.

    Use the command :hg:`update` to switch to an existing branch. Use
    :hg:`commit --close-branch` to mark this branch head as closed.
    When all heads of a branch are closed, the branch will be
    considered closed.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    revs = opts.get(b'rev')
    if label:
        label = label.strip()

    if not opts.get(b'clean') and not label:
        if revs:
            raise error.InputError(
                _(b"no branch name specified for the revisions")
            )
        ui.write(b"%s\n" % repo.dirstate.branch())
        return

    with repo.wlock():
        if opts.get(b'clean'):
            label = repo[b'.'].branch()
            repo.dirstate.setbranch(label)
            ui.status(_(b'reset working directory to branch %s\n') % label)
        elif label:

            scmutil.checknewlabel(repo, label, b'branch')
            if revs:
                return cmdutil.changebranch(ui, repo, revs, label, opts)

            if not opts.get(b'force') and label in repo.branchmap():
                if label not in [p.branch() for p in repo[None].parents()]:
                    raise error.InputError(
                        _(b'a branch of the same name already exists'),
                        # i18n: "it" refers to an existing branch
                        hint=_(b"use 'hg update' to switch to it"),
                    )

            repo.dirstate.setbranch(label)
            ui.status(_(b'marked working directory as branch %s\n') % label)

            # find any open named branches aside from default
            for n, h, t, c in repo.branchmap().iterbranches():
                if n != b"default" and not c:
                    return 0
            ui.status(
                _(
                    b'(branches are permanent and global, '
                    b'did you want a bookmark?)\n'
                )
            )


@command(
    b'branches',
    [
        (
            b'a',
            b'active',
            False,
            _(b'show only branches that have unmerged heads (DEPRECATED)'),
        ),
        (b'c', b'closed', False, _(b'show normal and closed branches')),
        (b'r', b'rev', [], _(b'show branch name(s) of the given rev')),
    ]
    + formatteropts,
    _(b'[-c]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
    intents={INTENT_READONLY},
)
def branches(ui, repo, active=False, closed=False, **opts):
    """list repository named branches

    List the repository's named branches, indicating which ones are
    inactive. If -c/--closed is specified, also list branches which have
    been marked closed (see :hg:`commit --close-branch`).

    Use the command :hg:`update` to switch to an existing branch.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions such as ``{branch}``. See also
      :hg:`help templates`.

      :active:  Boolean. True if the branch is active.
      :closed:  Boolean. True if the branch is closed.
      :current: Boolean. True if it is the current branch.

    Returns 0.
    """

    opts = pycompat.byteskwargs(opts)
    revs = opts.get(b'rev')
    selectedbranches = None
    if revs:
        revs = scmutil.revrange(repo, revs)
        getbi = repo.revbranchcache().branchinfo
        selectedbranches = {getbi(r)[0] for r in revs}

    ui.pager(b'branches')
    fm = ui.formatter(b'branches', opts)
    hexfunc = fm.hexfunc

    allheads = set(repo.heads())
    branches = []
    for tag, heads, tip, isclosed in repo.branchmap().iterbranches():
        if selectedbranches is not None and tag not in selectedbranches:
            continue
        isactive = False
        if not isclosed:
            openheads = set(repo.branchmap().iteropen(heads))
            isactive = bool(openheads & allheads)
        branches.append((tag, repo[tip], isactive, not isclosed))
    branches.sort(key=lambda i: (i[2], i[1].rev(), i[0], i[3]), reverse=True)

    for tag, ctx, isactive, isopen in branches:
        if active and not isactive:
            continue
        if isactive:
            label = b'branches.active'
            notice = b''
        elif not isopen:
            if not closed:
                continue
            label = b'branches.closed'
            notice = _(b' (closed)')
        else:
            label = b'branches.inactive'
            notice = _(b' (inactive)')
        current = tag == repo.dirstate.branch()
        if current:
            label = b'branches.current'

        fm.startitem()
        fm.write(b'branch', b'%s', tag, label=label)
        rev = ctx.rev()
        padsize = max(31 - len(b"%d" % rev) - encoding.colwidth(tag), 0)
        fmt = b' ' * padsize + b' %d:%s'
        fm.condwrite(
            not ui.quiet,
            b'rev node',
            fmt,
            rev,
            hexfunc(ctx.node()),
            label=b'log.changeset changeset.%s' % ctx.phasestr(),
        )
        fm.context(ctx=ctx)
        fm.data(active=isactive, closed=not isopen, current=current)
        if not ui.quiet:
            fm.plain(notice)
        fm.plain(b'\n')
    fm.end()


@command(
    b'bundle',
    [
        (
            b'f',
            b'force',
            None,
            _(b'run even when the destination is unrelated'),
        ),
        (
            b'r',
            b'rev',
            [],
            _(b'a changeset intended to be added to the destination'),
            _(b'REV'),
        ),
        (
            b'b',
            b'branch',
            [],
            _(b'a specific branch you would like to bundle'),
            _(b'BRANCH'),
        ),
        (
            b'',
            b'base',
            [],
            _(b'a base changeset assumed to be available at the destination'),
            _(b'REV'),
        ),
        (b'a', b'all', None, _(b'bundle all changesets in the repository')),
        (
            b't',
            b'type',
            b'bzip2',
            _(b'bundle compression type to use'),
            _(b'TYPE'),
        ),
    ]
    + remoteopts,
    _(b'[-f] [-t BUNDLESPEC] [-a] [-r REV]... [--base REV]... FILE [DEST]...'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def bundle(ui, repo, fname, *dests, **opts):
    """create a bundle file

    Generate a bundle file containing data to be transferred to another
    repository.

    To create a bundle containing all changesets, use -a/--all
    (or --base null). Otherwise, hg assumes the destination will have
    all the nodes you specify with --base parameters. Otherwise, hg
    will assume the repository has all the nodes in destination, or
    default-push/default if no destination is specified, where destination
    is the repositories you provide through DEST option.

    You can change bundle format with the -t/--type option. See
    :hg:`help bundlespec` for documentation on this format. By default,
    the most appropriate format is used and compression defaults to
    bzip2.

    The bundle file can then be transferred using conventional means
    and applied to another repository with the unbundle or pull
    command. This is useful when direct push and pull are not
    available or when exporting an entire repository is undesirable.

    Applying bundles preserves all changeset contents including
    permissions, copy/rename information, and revision history.

    Returns 0 on success, 1 if no changes found.
    """
    opts = pycompat.byteskwargs(opts)
    revs = None
    if b'rev' in opts:
        revstrings = opts[b'rev']
        revs = scmutil.revrange(repo, revstrings)
        if revstrings and not revs:
            raise error.InputError(_(b'no commits to bundle'))

    bundletype = opts.get(b'type', b'bzip2').lower()
    try:
        bundlespec = bundlecaches.parsebundlespec(
            repo, bundletype, strict=False
        )
    except error.UnsupportedBundleSpecification as e:
        raise error.InputError(
            pycompat.bytestr(e),
            hint=_(b"see 'hg help bundlespec' for supported values for --type"),
        )
    cgversion = bundlespec.contentopts[b"cg.version"]

    # Packed bundles are a pseudo bundle format for now.
    if cgversion == b's1':
        raise error.InputError(
            _(b'packed bundles cannot be produced by "hg bundle"'),
            hint=_(b"use 'hg debugcreatestreamclonebundle'"),
        )

    if opts.get(b'all'):
        if dests:
            raise error.InputError(
                _(b"--all is incompatible with specifying destinations")
            )
        if opts.get(b'base'):
            ui.warn(_(b"ignoring --base because --all was specified\n"))
        base = [nullrev]
    else:
        base = scmutil.revrange(repo, opts.get(b'base'))
    if cgversion not in changegroup.supportedoutgoingversions(repo):
        raise error.Abort(
            _(b"repository does not support bundle version %s") % cgversion
        )

    if base:
        if dests:
            raise error.InputError(
                _(b"--base is incompatible with specifying destinations")
            )
        common = [repo[rev].node() for rev in base]
        heads = [repo[r].node() for r in revs] if revs else None
        outgoing = discovery.outgoing(repo, common, heads)
        missing = outgoing.missing
        excluded = outgoing.excluded
    else:
        missing = set()
        excluded = set()
        for path in urlutil.get_push_paths(repo, ui, dests):
            other = hg.peer(repo, opts, path.rawloc)
            if revs is not None:
                hex_revs = [repo[r].hex() for r in revs]
            else:
                hex_revs = None
            branches = (path.branch, [])
            head_revs, checkout = hg.addbranchrevs(
                repo, repo, branches, hex_revs
            )
            heads = (
                head_revs
                and pycompat.maplist(repo.lookup, head_revs)
                or head_revs
            )
            outgoing = discovery.findcommonoutgoing(
                repo,
                other,
                onlyheads=heads,
                force=opts.get(b'force'),
                portable=True,
            )
            missing.update(outgoing.missing)
            excluded.update(outgoing.excluded)

    if not missing:
        scmutil.nochangesfound(ui, repo, not base and excluded)
        return 1

    if heads:
        outgoing = discovery.outgoing(
            repo, missingroots=missing, ancestorsof=heads
        )
    else:
        outgoing = discovery.outgoing(repo, missingroots=missing)
    outgoing.excluded = sorted(excluded)

    if cgversion == b'01':  # bundle1
        bversion = b'HG10' + bundlespec.wirecompression
        bcompression = None
    elif cgversion in (b'02', b'03'):
        bversion = b'HG20'
        bcompression = bundlespec.wirecompression
    else:
        raise error.ProgrammingError(
            b'bundle: unexpected changegroup version %s' % cgversion
        )

    # TODO compression options should be derived from bundlespec parsing.
    # This is a temporary hack to allow adjusting bundle compression
    # level without a) formalizing the bundlespec changes to declare it
    # b) introducing a command flag.
    compopts = {}
    complevel = ui.configint(
        b'experimental', b'bundlecomplevel.' + bundlespec.compression
    )
    if complevel is None:
        complevel = ui.configint(b'experimental', b'bundlecomplevel')
    if complevel is not None:
        compopts[b'level'] = complevel

    compthreads = ui.configint(
        b'experimental', b'bundlecompthreads.' + bundlespec.compression
    )
    if compthreads is None:
        compthreads = ui.configint(b'experimental', b'bundlecompthreads')
    if compthreads is not None:
        compopts[b'threads'] = compthreads

    # Bundling of obsmarker and phases is optional as not all clients
    # support the necessary features.
    cfg = ui.configbool
    contentopts = {
        b'obsolescence': cfg(b'experimental', b'evolution.bundle-obsmarker'),
        b'obsolescence-mandatory': cfg(
            b'experimental', b'evolution.bundle-obsmarker:mandatory'
        ),
        b'phases': cfg(b'experimental', b'bundle-phases'),
    }
    bundlespec.contentopts.update(contentopts)

    bundle2.writenewbundle(
        ui,
        repo,
        b'bundle',
        fname,
        bversion,
        outgoing,
        bundlespec.contentopts,
        compression=bcompression,
        compopts=compopts,
    )


@command(
    b'cat',
    [
        (
            b'o',
            b'output',
            b'',
            _(b'print output to file with formatted name'),
            _(b'FORMAT'),
        ),
        (b'r', b'rev', b'', _(b'print the given revision'), _(b'REV')),
        (b'', b'decode', None, _(b'apply any matching decode filter')),
    ]
    + walkopts
    + formatteropts,
    _(b'[OPTION]... FILE...'),
    helpcategory=command.CATEGORY_FILE_CONTENTS,
    inferrepo=True,
    intents={INTENT_READONLY},
)
def cat(ui, repo, file1, *pats, **opts):
    """output the current or given revision of files

    Print the specified files as they were at the given revision. If
    no revision is given, the parent of the working directory is used.

    Output may be to a file, in which case the name of the file is
    given using a template string. See :hg:`help templates`. In addition
    to the common template keywords, the following formatting rules are
    supported:

    :``%%``: literal "%" character
    :``%s``: basename of file being printed
    :``%d``: dirname of file being printed, or '.' if in repository root
    :``%p``: root-relative path name of file being printed
    :``%H``: changeset hash (40 hexadecimal digits)
    :``%R``: changeset revision number
    :``%h``: short-form changeset hash (12 hexadecimal digits)
    :``%r``: zero-padded changeset revision number
    :``%b``: basename of the exporting repository
    :``\\``: literal "\\" character

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :data:    String. File content.
      :path:    String. Repository-absolute path of the file.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    rev = opts.get(b'rev')
    if rev:
        repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
    ctx = scmutil.revsingle(repo, rev)
    m = scmutil.match(ctx, (file1,) + pats, opts)
    fntemplate = opts.pop(b'output', b'')
    if cmdutil.isstdiofilename(fntemplate):
        fntemplate = b''

    if fntemplate:
        fm = formatter.nullformatter(ui, b'cat', opts)
    else:
        ui.pager(b'cat')
        fm = ui.formatter(b'cat', opts)
    with fm:
        return cmdutil.cat(
            ui, repo, ctx, m, fm, fntemplate, b'', **pycompat.strkwargs(opts)
        )


@command(
    b'clone',
    [
        (
            b'U',
            b'noupdate',
            None,
            _(
                b'the clone will include an empty working '
                b'directory (only a repository)'
            ),
        ),
        (
            b'u',
            b'updaterev',
            b'',
            _(b'revision, tag, or branch to check out'),
            _(b'REV'),
        ),
        (
            b'r',
            b'rev',
            [],
            _(
                b'do not clone everything, but include this changeset'
                b' and its ancestors'
            ),
            _(b'REV'),
        ),
        (
            b'b',
            b'branch',
            [],
            _(
                b'do not clone everything, but include this branch\'s'
                b' changesets and their ancestors'
            ),
            _(b'BRANCH'),
        ),
        (b'', b'pull', None, _(b'use pull protocol to copy metadata')),
        (b'', b'uncompressed', None, _(b'an alias to --stream (DEPRECATED)')),
        (b'', b'stream', None, _(b'clone with minimal data processing')),
    ]
    + remoteopts,
    _(b'[OPTION]... SOURCE [DEST]'),
    helpcategory=command.CATEGORY_REPO_CREATION,
    helpbasic=True,
    norepo=True,
)
def clone(ui, source, dest=None, **opts):
    """make a copy of an existing repository

    Create a copy of an existing repository in a new directory.

    If no destination directory name is specified, it defaults to the
    basename of the source.

    The location of the source is added to the new repository's
    ``.hg/hgrc`` file, as the default to be used for future pulls.

    Only local paths and ``ssh://`` URLs are supported as
    destinations. For ``ssh://`` destinations, no working directory or
    ``.hg/hgrc`` will be created on the remote side.

    If the source repository has a bookmark called '@' set, that
    revision will be checked out in the new repository by default.

    To check out a particular version, use -u/--update, or
    -U/--noupdate to create a clone with no working directory.

    To pull only a subset of changesets, specify one or more revisions
    identifiers with -r/--rev or branches with -b/--branch. The
    resulting clone will contain only the specified changesets and
    their ancestors. These options (or 'clone src#rev dest') imply
    --pull, even for local source repositories.

    In normal clone mode, the remote normalizes repository data into a common
    exchange format and the receiving end translates this data into its local
    storage format. --stream activates a different clone mode that essentially
    copies repository files from the remote with minimal data processing. This
    significantly reduces the CPU cost of a clone both remotely and locally.
    However, it often increases the transferred data size by 30-40%. This can
    result in substantially faster clones where I/O throughput is plentiful,
    especially for larger repositories. A side-effect of --stream clones is
    that storage settings and requirements on the remote are applied locally:
    a modern client may inherit legacy or inefficient storage used by the
    remote or a legacy Mercurial client may not be able to clone from a
    modern Mercurial remote.

    .. note::

       Specifying a tag will include the tagged changeset but not the
       changeset containing the tag.

    .. container:: verbose

      For efficiency, hardlinks are used for cloning whenever the
      source and destination are on the same filesystem (note this
      applies only to the repository data, not to the working
      directory). Some filesystems, such as AFS, implement hardlinking
      incorrectly, but do not report errors. In these cases, use the
      --pull option to avoid hardlinking.

      Mercurial will update the working directory to the first applicable
      revision from this list:

      a) null if -U or the source repository has no changesets
      b) if -u . and the source repository is local, the first parent of
         the source repository's working directory
      c) the changeset specified with -u (if a branch name, this means the
         latest head of that branch)
      d) the changeset specified with -r
      e) the tipmost head specified with -b
      f) the tipmost head specified with the url#branch source syntax
      g) the revision marked with the '@' bookmark, if present
      h) the tipmost head of the default branch
      i) tip

      When cloning from servers that support it, Mercurial may fetch
      pre-generated data from a server-advertised URL or inline from the
      same stream. When this is done, hooks operating on incoming changesets
      and changegroups may fire more than once, once for each pre-generated
      bundle and as well as for any additional remaining data. In addition,
      if an error occurs, the repository may be rolled back to a partial
      clone. This behavior may change in future releases.
      See :hg:`help -e clonebundles` for more.

      Examples:

      - clone a remote repository to a new directory named hg/::

          hg clone https://www.mercurial-scm.org/repo/hg/

      - create a lightweight local clone::

          hg clone project/ project-feature/

      - clone from an absolute path on an ssh server (note double-slash)::

          hg clone ssh://user@server//home/projects/alpha/

      - do a streaming clone while checking out a specified version::

          hg clone --stream http://server/repo -u 1.5

      - create a repository without changesets after a particular revision::

          hg clone -r 04e544 experimental/ good/

      - clone (and track) a particular named branch::

          hg clone https://www.mercurial-scm.org/repo/hg/#stable

    See :hg:`help urls` for details on specifying URLs.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    cmdutil.check_at_most_one_arg(opts, b'noupdate', b'updaterev')

    # --include/--exclude can come from narrow or sparse.
    includepats, excludepats = None, None

    # hg.clone() differentiates between None and an empty set. So make sure
    # patterns are sets if narrow is requested without patterns.
    if opts.get(b'narrow'):
        includepats = set()
        excludepats = set()

        if opts.get(b'include'):
            includepats = narrowspec.parsepatterns(opts.get(b'include'))
        if opts.get(b'exclude'):
            excludepats = narrowspec.parsepatterns(opts.get(b'exclude'))

    r = hg.clone(
        ui,
        opts,
        source,
        dest,
        pull=opts.get(b'pull'),
        stream=opts.get(b'stream') or opts.get(b'uncompressed'),
        revs=opts.get(b'rev'),
        update=opts.get(b'updaterev') or not opts.get(b'noupdate'),
        branch=opts.get(b'branch'),
        shareopts=opts.get(b'shareopts'),
        storeincludepats=includepats,
        storeexcludepats=excludepats,
        depth=opts.get(b'depth') or None,
    )

    return r is None


@command(
    b'commit|ci',
    [
        (
            b'A',
            b'addremove',
            None,
            _(b'mark new/missing files as added/removed before committing'),
        ),
        (b'', b'close-branch', None, _(b'mark a branch head as closed')),
        (b'', b'amend', None, _(b'amend the parent of the working directory')),
        (b's', b'secret', None, _(b'use the secret phase for committing')),
        (b'e', b'edit', None, _(b'invoke editor on commit messages')),
        (
            b'',
            b'force-close-branch',
            None,
            _(b'forcibly close branch from a non-head changeset (ADVANCED)'),
        ),
        (b'i', b'interactive', None, _(b'use interactive mode')),
    ]
    + walkopts
    + commitopts
    + commitopts2
    + subrepoopts,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
    helpbasic=True,
    inferrepo=True,
)
def commit(ui, repo, *pats, **opts):
    """commit the specified files or all outstanding changes

    Commit changes to the given files into the repository. Unlike a
    centralized SCM, this operation is a local operation. See
    :hg:`push` for a way to actively distribute your changes.

    If a list of files is omitted, all changes reported by :hg:`status`
    will be committed.

    If you are committing the result of a merge, do not provide any
    filenames or -I/-X filters.

    If no commit message is specified, Mercurial starts your
    configured editor where you can enter a message. In case your
    commit fails, you will find a backup of your message in
    ``.hg/last-message.txt``.

    The --close-branch flag can be used to mark the current branch
    head closed. When all heads of a branch are closed, the branch
    will be considered closed and no longer listed.

    The --amend flag can be used to amend the parent of the
    working directory with a new commit that contains the changes
    in the parent in addition to those currently reported by :hg:`status`,
    if there are any. The old commit is stored in a backup bundle in
    ``.hg/strip-backup`` (see :hg:`help bundle` and :hg:`help unbundle`
    on how to restore it).

    Message, user and date are taken from the amended commit unless
    specified. When a message isn't specified on the command line,
    the editor will open with the message of the amended commit.

    It is not possible to amend public changesets (see :hg:`help phases`)
    or changesets that have children.

    See :hg:`help dates` for a list of formats valid for -d/--date.

    Returns 0 on success, 1 if nothing changed.

    .. container:: verbose

      Examples:

      - commit all files ending in .py::

          hg commit --include "set:**.py"

      - commit all non-binary files::

          hg commit --exclude "set:binary()"

      - amend the current commit and set the date to now::

          hg commit --amend --date now
    """
    with repo.wlock(), repo.lock():
        return _docommit(ui, repo, *pats, **opts)


def _docommit(ui, repo, *pats, **opts):
    if opts.get('interactive'):
        opts.pop('interactive')
        ret = cmdutil.dorecord(
            ui, repo, commit, None, False, cmdutil.recordfilter, *pats, **opts
        )
        # ret can be 0 (no changes to record) or the value returned by
        # commit(), 1 if nothing changed or None on success.
        return 1 if ret == 0 else ret

    if opts.get('subrepos'):
        cmdutil.check_incompatible_arguments(opts, 'subrepos', ['amend'])
        # Let --subrepos on the command line override config setting.
        ui.setconfig(b'ui', b'commitsubrepos', True, b'commit')

    cmdutil.checkunfinished(repo, commit=True)

    branch = repo[None].branch()
    bheads = repo.branchheads(branch)
    tip = repo.changelog.tip()

    extra = {}
    if opts.get('close_branch') or opts.get('force_close_branch'):
        extra[b'close'] = b'1'

        if repo[b'.'].closesbranch():
            raise error.InputError(
                _(b'current revision is already a branch closing head')
            )
        elif not bheads:
            raise error.InputError(
                _(b'branch "%s" has no heads to close') % branch
            )
        elif (
            branch == repo[b'.'].branch()
            and repo[b'.'].node() not in bheads
            and not opts.get('force_close_branch')
        ):
            hint = _(
                b'use --force-close-branch to close branch from a non-head'
                b' changeset'
            )
            raise error.InputError(_(b'can only close branch heads'), hint=hint)
        elif opts.get('amend'):
            if (
                repo[b'.'].p1().branch() != branch
                and repo[b'.'].p2().branch() != branch
            ):
                raise error.InputError(_(b'can only close branch heads'))

    if opts.get('amend'):
        if ui.configbool(b'ui', b'commitsubrepos'):
            raise error.InputError(
                _(b'cannot amend with ui.commitsubrepos enabled')
            )

        old = repo[b'.']
        rewriteutil.precheck(repo, [old.rev()], b'amend')

        # Currently histedit gets confused if an amend happens while histedit
        # is in progress. Since we have a checkunfinished command, we are
        # temporarily honoring it.
        #
        # Note: eventually this guard will be removed. Please do not expect
        # this behavior to remain.
        if not obsolete.isenabled(repo, obsolete.createmarkersopt):
            cmdutil.checkunfinished(repo)

        node = cmdutil.amend(ui, repo, old, extra, pats, opts)
        opts = pycompat.byteskwargs(opts)
        if node == old.node():
            ui.status(_(b"nothing changed\n"))
            return 1
    else:

        def commitfunc(ui, repo, message, match, opts):
            overrides = {}
            if opts.get(b'secret'):
                overrides[(b'phases', b'new-commit')] = b'secret'

            baseui = repo.baseui
            with baseui.configoverride(overrides, b'commit'):
                with ui.configoverride(overrides, b'commit'):
                    editform = cmdutil.mergeeditform(
                        repo[None], b'commit.normal'
                    )
                    editor = cmdutil.getcommiteditor(
                        editform=editform, **pycompat.strkwargs(opts)
                    )
                    return repo.commit(
                        message,
                        opts.get(b'user'),
                        opts.get(b'date'),
                        match,
                        editor=editor,
                        extra=extra,
                    )

        opts = pycompat.byteskwargs(opts)
        node = cmdutil.commit(ui, repo, commitfunc, pats, opts)

        if not node:
            stat = cmdutil.postcommitstatus(repo, pats, opts)
            if stat.deleted:
                ui.status(
                    _(
                        b"nothing changed (%d missing files, see "
                        b"'hg status')\n"
                    )
                    % len(stat.deleted)
                )
            else:
                ui.status(_(b"nothing changed\n"))
            return 1

    cmdutil.commitstatus(repo, node, branch, bheads, tip, opts)

    if not ui.quiet and ui.configbool(b'commands', b'commit.post-status'):
        status(
            ui,
            repo,
            modified=True,
            added=True,
            removed=True,
            deleted=True,
            unknown=True,
            subrepos=opts.get(b'subrepos'),
        )


@command(
    b'config|showconfig|debugconfig',
    [
        (b'u', b'untrusted', None, _(b'show untrusted configuration options')),
        # This is experimental because we need
        # * reasonable behavior around aliases,
        # * decide if we display [debug] [experimental] and [devel] section par
        #   default
        # * some way to display "generic" config entry (the one matching
        #   regexp,
        # * proper display of the different value type
        # * a better way to handle <DYNAMIC> values (and variable types),
        # * maybe some type information ?
        (
            b'',
            b'exp-all-known',
            None,
            _(b'show all known config option (EXPERIMENTAL)'),
        ),
        (b'e', b'edit', None, _(b'edit user config')),
        (b'l', b'local', None, _(b'edit repository config')),
        (b'', b'source', None, _(b'show source of configuration value')),
        (
            b'',
            b'shared',
            None,
            _(b'edit shared source repository config (EXPERIMENTAL)'),
        ),
        (b'', b'non-shared', None, _(b'edit non shared config (EXPERIMENTAL)')),
        (b'g', b'global', None, _(b'edit global config')),
    ]
    + formatteropts,
    _(b'[-u] [NAME]...'),
    helpcategory=command.CATEGORY_HELP,
    optionalrepo=True,
    intents={INTENT_READONLY},
)
def config(ui, repo, *values, **opts):
    """show combined config settings from all hgrc files

    With no arguments, print names and values of all config items.

    With one argument of the form section.name, print just the value
    of that config item.

    With multiple arguments, print names and values of all config
    items with matching section names or section.names.

    With --edit, start an editor on the user-level config file. With
    --global, edit the system-wide config file. With --local, edit the
    repository-level config file.

    With --source, the source (filename and line number) is printed
    for each config item.

    See :hg:`help config` for more information about config files.

    .. container:: verbose

      --non-shared flag is used to edit `.hg/hgrc-not-shared` config file.
      This file is not shared across shares when in share-safe mode.

      Template:

      The following keywords are supported. See also :hg:`help templates`.

      :name:    String. Config name.
      :source:  String. Filename and line number where the item is defined.
      :value:   String. Config value.

      The --shared flag can be used to edit the config file of shared source
      repository. It only works when you have shared using the experimental
      share safe feature.

    Returns 0 on success, 1 if NAME does not exist.

    """

    opts = pycompat.byteskwargs(opts)
    editopts = (b'edit', b'local', b'global', b'shared', b'non_shared')
    if any(opts.get(o) for o in editopts):
        cmdutil.check_at_most_one_arg(opts, *editopts[1:])
        if opts.get(b'local'):
            if not repo:
                raise error.InputError(
                    _(b"can't use --local outside a repository")
                )
            paths = [repo.vfs.join(b'hgrc')]
        elif opts.get(b'global'):
            paths = rcutil.systemrcpath()
        elif opts.get(b'shared'):
            if not repo.shared():
                raise error.InputError(
                    _(b"repository is not shared; can't use --shared")
                )
            if requirements.SHARESAFE_REQUIREMENT not in repo.requirements:
                raise error.InputError(
                    _(
                        b"share safe feature not enabled; "
                        b"unable to edit shared source repository config"
                    )
                )
            paths = [vfsmod.vfs(repo.sharedpath).join(b'hgrc')]
        elif opts.get(b'non_shared'):
            paths = [repo.vfs.join(b'hgrc-not-shared')]
        else:
            paths = rcutil.userrcpath()

        for f in paths:
            if os.path.exists(f):
                break
        else:
            if opts.get(b'global'):
                samplehgrc = uimod.samplehgrcs[b'global']
            elif opts.get(b'local'):
                samplehgrc = uimod.samplehgrcs[b'local']
            else:
                samplehgrc = uimod.samplehgrcs[b'user']

            f = paths[0]
            fp = open(f, b"wb")
            fp.write(util.tonativeeol(samplehgrc))
            fp.close()

        editor = ui.geteditor()
        ui.system(
            b"%s \"%s\"" % (editor, f),
            onerr=error.InputError,
            errprefix=_(b"edit failed"),
            blockedtag=b'config_edit',
        )
        return
    ui.pager(b'config')
    fm = ui.formatter(b'config', opts)
    for t, f in rcutil.rccomponents():
        if t == b'path':
            ui.debug(b'read config from: %s\n' % f)
        elif t == b'resource':
            ui.debug(b'read config from: resource:%s.%s\n' % (f[0], f[1]))
        elif t == b'items':
            # Don't print anything for 'items'.
            pass
        else:
            raise error.ProgrammingError(b'unknown rctype: %s' % t)
    untrusted = bool(opts.get(b'untrusted'))

    selsections = selentries = []
    if values:
        selsections = [v for v in values if b'.' not in v]
        selentries = [v for v in values if b'.' in v]
    uniquesel = len(selentries) == 1 and not selsections
    selsections = set(selsections)
    selentries = set(selentries)

    matched = False
    all_known = opts[b'exp_all_known']
    show_source = ui.debugflag or opts.get(b'source')
    entries = ui.walkconfig(untrusted=untrusted, all_known=all_known)
    for section, name, value in entries:
        source = ui.configsource(section, name, untrusted)
        value = pycompat.bytestr(value)
        defaultvalue = ui.configdefault(section, name)
        if fm.isplain():
            source = source or b'none'
            value = value.replace(b'\n', b'\\n')
        entryname = section + b'.' + name
        if values and not (section in selsections or entryname in selentries):
            continue
        fm.startitem()
        fm.condwrite(show_source, b'source', b'%s: ', source)
        if uniquesel:
            fm.data(name=entryname)
            fm.write(b'value', b'%s\n', value)
        else:
            fm.write(b'name value', b'%s=%s\n', entryname, value)
        if formatter.isprintable(defaultvalue):
            fm.data(defaultvalue=defaultvalue)
        elif isinstance(defaultvalue, list) and all(
            formatter.isprintable(e) for e in defaultvalue
        ):
            fm.data(defaultvalue=fm.formatlist(defaultvalue, name=b'value'))
        # TODO: no idea how to process unsupported defaultvalue types
        matched = True
    fm.end()
    if matched:
        return 0
    return 1


@command(
    b'continue',
    dryrunopts,
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
    helpbasic=True,
)
def continuecmd(ui, repo, **opts):
    """resumes an interrupted operation (EXPERIMENTAL)

    Finishes a multistep operation like graft, histedit, rebase, merge,
    and unshelve if they are in an interrupted state.

    use --dry-run/-n to dry run the command.
    """
    dryrun = opts.get('dry_run')
    contstate = cmdutil.getunfinishedstate(repo)
    if not contstate:
        raise error.StateError(_(b'no operation in progress'))
    if not contstate.continuefunc:
        raise error.StateError(
            (
                _(b"%s in progress but does not support 'hg continue'")
                % (contstate._opname)
            ),
            hint=contstate.continuemsg(),
        )
    if dryrun:
        ui.status(_(b'%s in progress, will be resumed\n') % (contstate._opname))
        return
    return contstate.continuefunc(ui, repo)


@command(
    b'copy|cp',
    [
        (b'', b'forget', None, _(b'unmark a destination file as copied')),
        (b'A', b'after', None, _(b'record a copy that has already occurred')),
        (
            b'',
            b'at-rev',
            b'',
            _(b'(un)mark copies in the given revision (EXPERIMENTAL)'),
            _(b'REV'),
        ),
        (
            b'f',
            b'force',
            None,
            _(b'forcibly copy over an existing managed file'),
        ),
    ]
    + walkopts
    + dryrunopts,
    _(b'[OPTION]... (SOURCE... DEST | --forget DEST...)'),
    helpcategory=command.CATEGORY_FILE_CONTENTS,
)
def copy(ui, repo, *pats, **opts):
    """mark files as copied for the next commit

    Mark dest as having copies of source files. If dest is a
    directory, copies are put in that directory. If dest is a file,
    the source must be a single file.

    By default, this command copies the contents of files as they
    exist in the working directory. If invoked with -A/--after, the
    operation is recorded, but no copying is performed.

    To undo marking a destination file as copied, use --forget. With that
    option, all given (positional) arguments are unmarked as copies. The
    destination file(s) will be left in place (still tracked). Note that
    :hg:`copy --forget` behaves the same way as :hg:`rename --forget`.

    This command takes effect with the next commit by default.

    Returns 0 on success, 1 if errors are encountered.
    """
    opts = pycompat.byteskwargs(opts)
    with repo.wlock():
        return cmdutil.copy(ui, repo, pats, opts)


@command(
    b'debugcommands',
    [],
    _(b'[COMMAND]'),
    helpcategory=command.CATEGORY_HELP,
    norepo=True,
)
def debugcommands(ui, cmd=b'', *args):
    """list all available commands and options"""
    for cmd, vals in sorted(pycompat.iteritems(table)):
        cmd = cmd.split(b'|')[0]
        opts = b', '.join([i[1] for i in vals[1]])
        ui.write(b'%s: %s\n' % (cmd, opts))


@command(
    b'debugcomplete',
    [(b'o', b'options', None, _(b'show the command options'))],
    _(b'[-o] CMD'),
    helpcategory=command.CATEGORY_HELP,
    norepo=True,
)
def debugcomplete(ui, cmd=b'', **opts):
    """returns the completion list associated with the given command"""

    if opts.get('options'):
        options = []
        otables = [globalopts]
        if cmd:
            aliases, entry = cmdutil.findcmd(cmd, table, False)
            otables.append(entry[1])
        for t in otables:
            for o in t:
                if b"(DEPRECATED)" in o[3]:
                    continue
                if o[0]:
                    options.append(b'-%s' % o[0])
                options.append(b'--%s' % o[1])
        ui.write(b"%s\n" % b"\n".join(options))
        return

    cmdlist, unused_allcmds = cmdutil.findpossible(cmd, table)
    if ui.verbose:
        cmdlist = [b' '.join(c[0]) for c in cmdlist.values()]
    ui.write(b"%s\n" % b"\n".join(sorted(cmdlist)))


@command(
    b'diff',
    [
        (b'r', b'rev', [], _(b'revision (DEPRECATED)'), _(b'REV')),
        (b'', b'from', b'', _(b'revision to diff from'), _(b'REV1')),
        (b'', b'to', b'', _(b'revision to diff to'), _(b'REV2')),
        (b'c', b'change', b'', _(b'change made by revision'), _(b'REV')),
    ]
    + diffopts
    + diffopts2
    + walkopts
    + subrepoopts,
    _(b'[OPTION]... ([-c REV] | [--from REV1] [--to REV2]) [FILE]...'),
    helpcategory=command.CATEGORY_FILE_CONTENTS,
    helpbasic=True,
    inferrepo=True,
    intents={INTENT_READONLY},
)
def diff(ui, repo, *pats, **opts):
    """diff repository (or selected files)

    Show differences between revisions for the specified files.

    Differences between files are shown using the unified diff format.

    .. note::

       :hg:`diff` may generate unexpected results for merges, as it will
       default to comparing against the working directory's first
       parent changeset if no revisions are specified.

    By default, the working directory files are compared to its first parent. To
    see the differences from another revision, use --from. To see the difference
    to another revision, use --to. For example, :hg:`diff --from .^` will show
    the differences from the working copy's grandparent to the working copy,
    :hg:`diff --to .` will show the diff from the working copy to its parent
    (i.e. the reverse of the default), and :hg:`diff --from 1.0 --to 1.2` will
    show the diff between those two revisions.

    Alternatively you can specify -c/--change with a revision to see the changes
    in that changeset relative to its first parent (i.e. :hg:`diff -c 42` is
    equivalent to :hg:`diff --from 42^ --to 42`)

    Without the -a/--text option, diff will avoid generating diffs of
    files it detects as binary. With -a, diff will generate a diff
    anyway, probably with undesirable results.

    Use the -g/--git option to generate diffs in the git extended diff
    format. For more information, read :hg:`help diffs`.

    .. container:: verbose

      Examples:

      - compare a file in the current working directory to its parent::

          hg diff foo.c

      - compare two historical versions of a directory, with rename info::

          hg diff --git --from 1.0 --to 1.2 lib/

      - get change stats relative to the last change on some date::

          hg diff --stat --from "date('may 2')"

      - diff all newly-added files that contain a keyword::

          hg diff "set:added() and grep(GNU)"

      - compare a revision and its parents::

          hg diff -c 9353                  # compare against first parent
          hg diff --from 9353^ --to 9353   # same using revset syntax
          hg diff --from 9353^2 --to 9353  # compare against the second parent

    Returns 0 on success.
    """

    cmdutil.check_at_most_one_arg(opts, 'rev', 'change')
    opts = pycompat.byteskwargs(opts)
    revs = opts.get(b'rev')
    change = opts.get(b'change')
    from_rev = opts.get(b'from')
    to_rev = opts.get(b'to')
    stat = opts.get(b'stat')
    reverse = opts.get(b'reverse')

    cmdutil.check_incompatible_arguments(opts, b'from', [b'rev', b'change'])
    cmdutil.check_incompatible_arguments(opts, b'to', [b'rev', b'change'])
    if change:
        repo = scmutil.unhidehashlikerevs(repo, [change], b'nowarn')
        ctx2 = scmutil.revsingle(repo, change, None)
        ctx1 = logcmdutil.diff_parent(ctx2)
    elif from_rev or to_rev:
        repo = scmutil.unhidehashlikerevs(
            repo, [from_rev] + [to_rev], b'nowarn'
        )
        ctx1 = scmutil.revsingle(repo, from_rev, None)
        ctx2 = scmutil.revsingle(repo, to_rev, None)
    else:
        repo = scmutil.unhidehashlikerevs(repo, revs, b'nowarn')
        ctx1, ctx2 = scmutil.revpair(repo, revs)

    if reverse:
        ctxleft = ctx2
        ctxright = ctx1
    else:
        ctxleft = ctx1
        ctxright = ctx2

    diffopts = patch.diffallopts(ui, opts)
    m = scmutil.match(ctx2, pats, opts)
    m = repo.narrowmatch(m)
    ui.pager(b'diff')
    logcmdutil.diffordiffstat(
        ui,
        repo,
        diffopts,
        ctxleft,
        ctxright,
        m,
        stat=stat,
        listsubrepos=opts.get(b'subrepos'),
        root=opts.get(b'root'),
    )


@command(
    b'export',
    [
        (
            b'B',
            b'bookmark',
            b'',
            _(b'export changes only reachable by given bookmark'),
            _(b'BOOKMARK'),
        ),
        (
            b'o',
            b'output',
            b'',
            _(b'print output to file with formatted name'),
            _(b'FORMAT'),
        ),
        (b'', b'switch-parent', None, _(b'diff against the second parent')),
        (b'r', b'rev', [], _(b'revisions to export'), _(b'REV')),
    ]
    + diffopts
    + formatteropts,
    _(b'[OPTION]... [-o OUTFILESPEC] [-r] [REV]...'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
    helpbasic=True,
    intents={INTENT_READONLY},
)
def export(ui, repo, *changesets, **opts):
    """dump the header and diffs for one or more changesets

    Print the changeset header and diffs for one or more revisions.
    If no revision is given, the parent of the working directory is used.

    The information shown in the changeset header is: author, date,
    branch name (if non-default), changeset hash, parent(s) and commit
    comment.

    .. note::

       :hg:`export` may generate unexpected diff output for merge
       changesets, as it will compare the merge changeset against its
       first parent only.

    Output may be to a file, in which case the name of the file is
    given using a template string. See :hg:`help templates`. In addition
    to the common template keywords, the following formatting rules are
    supported:

    :``%%``: literal "%" character
    :``%H``: changeset hash (40 hexadecimal digits)
    :``%N``: number of patches being generated
    :``%R``: changeset revision number
    :``%b``: basename of the exporting repository
    :``%h``: short-form changeset hash (12 hexadecimal digits)
    :``%m``: first line of the commit message (only alphanumeric characters)
    :``%n``: zero-padded sequence number, starting at 1
    :``%r``: zero-padded changeset revision number
    :``\\``: literal "\\" character

    Without the -a/--text option, export will avoid generating diffs
    of files it detects as binary. With -a, export will generate a
    diff anyway, probably with undesirable results.

    With -B/--bookmark changesets reachable by the given bookmark are
    selected.

    Use the -g/--git option to generate diffs in the git extended diff
    format. See :hg:`help diffs` for more information.

    With the --switch-parent option, the diff will be against the
    second parent. It can be useful to review a merge.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :diff:    String. Diff content.
      :parents: List of strings. Parent nodes of the changeset.

      Examples:

      - use export and import to transplant a bugfix to the current
        branch::

          hg export -r 9353 | hg import -

      - export all the changesets between two revisions to a file with
        rename information::

          hg export --git -r 123:150 > changes.txt

      - split outgoing changes into a series of patches with
        descriptive names::

          hg export -r "outgoing()" -o "%n-%m.patch"

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    bookmark = opts.get(b'bookmark')
    changesets += tuple(opts.get(b'rev', []))

    cmdutil.check_at_most_one_arg(opts, b'rev', b'bookmark')

    if bookmark:
        if bookmark not in repo._bookmarks:
            raise error.InputError(_(b"bookmark '%s' not found") % bookmark)

        revs = scmutil.bookmarkrevs(repo, bookmark)
    else:
        if not changesets:
            changesets = [b'.']

        repo = scmutil.unhidehashlikerevs(repo, changesets, b'nowarn')
        revs = scmutil.revrange(repo, changesets)

    if not revs:
        raise error.InputError(_(b"export requires at least one changeset"))
    if len(revs) > 1:
        ui.note(_(b'exporting patches:\n'))
    else:
        ui.note(_(b'exporting patch:\n'))

    fntemplate = opts.get(b'output')
    if cmdutil.isstdiofilename(fntemplate):
        fntemplate = b''

    if fntemplate:
        fm = formatter.nullformatter(ui, b'export', opts)
    else:
        ui.pager(b'export')
        fm = ui.formatter(b'export', opts)
    with fm:
        cmdutil.export(
            repo,
            revs,
            fm,
            fntemplate=fntemplate,
            switch_parent=opts.get(b'switch_parent'),
            opts=patch.diffallopts(ui, opts),
        )


@command(
    b'files',
    [
        (
            b'r',
            b'rev',
            b'',
            _(b'search the repository as it is in REV'),
            _(b'REV'),
        ),
        (
            b'0',
            b'print0',
            None,
            _(b'end filenames with NUL, for use with xargs'),
        ),
    ]
    + walkopts
    + formatteropts
    + subrepoopts,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    intents={INTENT_READONLY},
)
def files(ui, repo, *pats, **opts):
    """list tracked files

    Print files under Mercurial control in the working directory or
    specified revision for given files (excluding removed files).
    Files can be specified as filenames or filesets.

    If no files are given to match, this command prints the names
    of all files under Mercurial control.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :flags:   String. Character denoting file's symlink and executable bits.
      :path:    String. Repository-absolute path of the file.
      :size:    Integer. Size of the file in bytes.

      Examples:

      - list all files under the current directory::

          hg files .

      - shows sizes and flags for current revision::

          hg files -vr .

      - list all files named README::

          hg files -I "**/README"

      - list all binary files::

          hg files "set:binary()"

      - find files containing a regular expression::

          hg files "set:grep('bob')"

      - search tracked file contents with xargs and grep::

          hg files -0 | xargs -0 grep foo

    See :hg:`help patterns` and :hg:`help filesets` for more information
    on specifying file patterns.

    Returns 0 if a match is found, 1 otherwise.

    """

    opts = pycompat.byteskwargs(opts)
    rev = opts.get(b'rev')
    if rev:
        repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
    ctx = scmutil.revsingle(repo, rev, None)

    end = b'\n'
    if opts.get(b'print0'):
        end = b'\0'
    fmt = b'%s' + end

    m = scmutil.match(ctx, pats, opts)
    ui.pager(b'files')
    uipathfn = scmutil.getuipathfn(ctx.repo(), legacyrelativevalue=True)
    with ui.formatter(b'files', opts) as fm:
        return cmdutil.files(
            ui, ctx, m, uipathfn, fm, fmt, opts.get(b'subrepos')
        )


@command(
    b'forget',
    [
        (b'i', b'interactive', None, _(b'use interactive mode')),
    ]
    + walkopts
    + dryrunopts,
    _(b'[OPTION]... FILE...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    helpbasic=True,
    inferrepo=True,
)
def forget(ui, repo, *pats, **opts):
    """forget the specified files on the next commit

    Mark the specified files so they will no longer be tracked
    after the next commit.

    This only removes files from the current branch, not from the
    entire project history, and it does not delete them from the
    working directory.

    To delete the file from the working directory, see :hg:`remove`.

    To undo a forget before the next commit, see :hg:`add`.

    .. container:: verbose

      Examples:

      - forget newly-added binary files::

          hg forget "set:added() and binary()"

      - forget files that would be excluded by .hgignore::

          hg forget "set:hgignore()"

    Returns 0 on success.
    """

    opts = pycompat.byteskwargs(opts)
    if not pats:
        raise error.InputError(_(b'no files specified'))

    m = scmutil.match(repo[None], pats, opts)
    dryrun, interactive = opts.get(b'dry_run'), opts.get(b'interactive')
    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)
    rejected = cmdutil.forget(
        ui,
        repo,
        m,
        prefix=b"",
        uipathfn=uipathfn,
        explicitonly=False,
        dryrun=dryrun,
        interactive=interactive,
    )[0]
    return rejected and 1 or 0


@command(
    b'graft',
    [
        (b'r', b'rev', [], _(b'revisions to graft'), _(b'REV')),
        (
            b'',
            b'base',
            b'',
            _(b'base revision when doing the graft merge (ADVANCED)'),
            _(b'REV'),
        ),
        (b'c', b'continue', False, _(b'resume interrupted graft')),
        (b'', b'stop', False, _(b'stop interrupted graft')),
        (b'', b'abort', False, _(b'abort interrupted graft')),
        (b'e', b'edit', False, _(b'invoke editor on commit messages')),
        (b'', b'log', None, _(b'append graft info to log message')),
        (
            b'',
            b'no-commit',
            None,
            _(b"don't commit, just apply the changes in working directory"),
        ),
        (b'f', b'force', False, _(b'force graft')),
        (
            b'D',
            b'currentdate',
            False,
            _(b'record the current date as commit date'),
        ),
        (
            b'U',
            b'currentuser',
            False,
            _(b'record the current user as committer'),
        ),
    ]
    + commitopts2
    + mergetoolopts
    + dryrunopts,
    _(b'[OPTION]... [-r REV]... REV...'),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
)
def graft(ui, repo, *revs, **opts):
    """copy changes from other branches onto the current branch

    This command uses Mercurial's merge logic to copy individual
    changes from other branches without merging branches in the
    history graph. This is sometimes known as 'backporting' or
    'cherry-picking'. By default, graft will copy user, date, and
    description from the source changesets.

    Changesets that are ancestors of the current revision, that have
    already been grafted, or that are merges will be skipped.

    If --log is specified, log messages will have a comment appended
    of the form::

      (grafted from CHANGESETHASH)

    If --force is specified, revisions will be grafted even if they
    are already ancestors of, or have been grafted to, the destination.
    This is useful when the revisions have since been backed out.

    If a graft merge results in conflicts, the graft process is
    interrupted so that the current merge can be manually resolved.
    Once all conflicts are addressed, the graft process can be
    continued with the -c/--continue option.

    The -c/--continue option reapplies all the earlier options.

    .. container:: verbose

      The --base option exposes more of how graft internally uses merge with a
      custom base revision. --base can be used to specify another ancestor than
      the first and only parent.

      The command::

        hg graft -r 345 --base 234

      is thus pretty much the same as::

        hg diff --from 234 --to 345 | hg import

      but using merge to resolve conflicts and track moved files.

      The result of a merge can thus be backported as a single commit by
      specifying one of the merge parents as base, and thus effectively
      grafting the changes from the other side.

      It is also possible to collapse multiple changesets and clean up history
      by specifying another ancestor as base, much like rebase --collapse
      --keep.

      The commit message can be tweaked after the fact using commit --amend .

      For using non-ancestors as the base to backout changes, see the backout
      command and the hidden --parent option.

    .. container:: verbose

      Examples:

      - copy a single change to the stable branch and edit its description::

          hg update stable
          hg graft --edit 9393

      - graft a range of changesets with one exception, updating dates::

          hg graft -D "2085::2093 and not 2091"

      - continue a graft after resolving conflicts::

          hg graft -c

      - show the source of a grafted changeset::

          hg log --debug -r .

      - show revisions sorted by date::

          hg log -r "sort(all(), date)"

      - backport the result of a merge as a single commit::

          hg graft -r 123 --base 123^

      - land a feature branch as one changeset::

          hg up -cr default
          hg graft -r featureX --base "ancestor('featureX', 'default')"

    See :hg:`help revisions` for more about specifying revisions.

    Returns 0 on successful completion, 1 if there are unresolved files.
    """
    with repo.wlock():
        return _dograft(ui, repo, *revs, **opts)


def _dograft(ui, repo, *revs, **opts):
    if revs and opts.get('rev'):
        ui.warn(
            _(
                b'warning: inconsistent use of --rev might give unexpected '
                b'revision ordering!\n'
            )
        )

    revs = list(revs)
    revs.extend(opts.get('rev'))
    # a dict of data to be stored in state file
    statedata = {}
    # list of new nodes created by ongoing graft
    statedata[b'newnodes'] = []

    cmdutil.resolve_commit_options(ui, opts)

    editor = cmdutil.getcommiteditor(editform=b'graft', **opts)

    cmdutil.check_at_most_one_arg(opts, 'abort', 'stop', 'continue')

    cont = False
    if opts.get('no_commit'):
        cmdutil.check_incompatible_arguments(
            opts,
            'no_commit',
            ['edit', 'currentuser', 'currentdate', 'log'],
        )

    graftstate = statemod.cmdstate(repo, b'graftstate')

    if opts.get('stop'):
        cmdutil.check_incompatible_arguments(
            opts,
            'stop',
            [
                'edit',
                'log',
                'user',
                'date',
                'currentdate',
                'currentuser',
                'rev',
            ],
        )
        return _stopgraft(ui, repo, graftstate)
    elif opts.get('abort'):
        cmdutil.check_incompatible_arguments(
            opts,
            'abort',
            [
                'edit',
                'log',
                'user',
                'date',
                'currentdate',
                'currentuser',
                'rev',
            ],
        )
        return cmdutil.abortgraft(ui, repo, graftstate)
    elif opts.get('continue'):
        cont = True
        if revs:
            raise error.InputError(_(b"can't specify --continue and revisions"))
        # read in unfinished revisions
        if graftstate.exists():
            statedata = cmdutil.readgraftstate(repo, graftstate)
            if statedata.get(b'date'):
                opts['date'] = statedata[b'date']
            if statedata.get(b'user'):
                opts['user'] = statedata[b'user']
            if statedata.get(b'log'):
                opts['log'] = True
            if statedata.get(b'no_commit'):
                opts['no_commit'] = statedata.get(b'no_commit')
            if statedata.get(b'base'):
                opts['base'] = statedata.get(b'base')
            nodes = statedata[b'nodes']
            revs = [repo[node].rev() for node in nodes]
        else:
            cmdutil.wrongtooltocontinue(repo, _(b'graft'))
    else:
        if not revs:
            raise error.InputError(_(b'no revisions specified'))
        cmdutil.checkunfinished(repo)
        cmdutil.bailifchanged(repo)
        revs = scmutil.revrange(repo, revs)

    skipped = set()
    basectx = None
    if opts.get('base'):
        basectx = scmutil.revsingle(repo, opts['base'], None)
    if basectx is None:
        # check for merges
        for rev in repo.revs(b'%ld and merge()', revs):
            ui.warn(_(b'skipping ungraftable merge revision %d\n') % rev)
            skipped.add(rev)
    revs = [r for r in revs if r not in skipped]
    if not revs:
        return -1
    if basectx is not None and len(revs) != 1:
        raise error.InputError(_(b'only one revision allowed with --base '))

    # Don't check in the --continue case, in effect retaining --force across
    # --continues. That's because without --force, any revisions we decided to
    # skip would have been filtered out here, so they wouldn't have made their
    # way to the graftstate. With --force, any revisions we would have otherwise
    # skipped would not have been filtered out, and if they hadn't been applied
    # already, they'd have been in the graftstate.
    if not (cont or opts.get('force')) and basectx is None:
        # check for ancestors of dest branch
        ancestors = repo.revs(b'%ld & (::.)', revs)
        for rev in ancestors:
            ui.warn(_(b'skipping ancestor revision %d:%s\n') % (rev, repo[rev]))

        revs = [r for r in revs if r not in ancestors]

        if not revs:
            return -1

        # analyze revs for earlier grafts
        ids = {}
        for ctx in repo.set(b"%ld", revs):
            ids[ctx.hex()] = ctx.rev()
            n = ctx.extra().get(b'source')
            if n:
                ids[n] = ctx.rev()

        # check ancestors for earlier grafts
        ui.debug(b'scanning for duplicate grafts\n')

        # The only changesets we can be sure doesn't contain grafts of any
        # revs, are the ones that are common ancestors of *all* revs:
        for rev in repo.revs(b'only(%d,ancestor(%ld))', repo[b'.'].rev(), revs):
            ctx = repo[rev]
            n = ctx.extra().get(b'source')
            if n in ids:
                try:
                    r = repo[n].rev()
                except error.RepoLookupError:
                    r = None
                if r in revs:
                    ui.warn(
                        _(
                            b'skipping revision %d:%s '
                            b'(already grafted to %d:%s)\n'
                        )
                        % (r, repo[r], rev, ctx)
                    )
                    revs.remove(r)
                elif ids[n] in revs:
                    if r is None:
                        ui.warn(
                            _(
                                b'skipping already grafted revision %d:%s '
                                b'(%d:%s also has unknown origin %s)\n'
                            )
                            % (ids[n], repo[ids[n]], rev, ctx, n[:12])
                        )
                    else:
                        ui.warn(
                            _(
                                b'skipping already grafted revision %d:%s '
                                b'(%d:%s also has origin %d:%s)\n'
                            )
                            % (ids[n], repo[ids[n]], rev, ctx, r, n[:12])
                        )
                    revs.remove(ids[n])
            elif ctx.hex() in ids:
                r = ids[ctx.hex()]
                if r in revs:
                    ui.warn(
                        _(
                            b'skipping already grafted revision %d:%s '
                            b'(was grafted from %d:%s)\n'
                        )
                        % (r, repo[r], rev, ctx)
                    )
                    revs.remove(r)
        if not revs:
            return -1

    if opts.get('no_commit'):
        statedata[b'no_commit'] = True
    if opts.get('base'):
        statedata[b'base'] = opts['base']
    for pos, ctx in enumerate(repo.set(b"%ld", revs)):
        desc = b'%d:%s "%s"' % (
            ctx.rev(),
            ctx,
            ctx.description().split(b'\n', 1)[0],
        )
        names = repo.nodetags(ctx.node()) + repo.nodebookmarks(ctx.node())
        if names:
            desc += b' (%s)' % b' '.join(names)
        ui.status(_(b'grafting %s\n') % desc)
        if opts.get('dry_run'):
            continue

        source = ctx.extra().get(b'source')
        extra = {}
        if source:
            extra[b'source'] = source
            extra[b'intermediate-source'] = ctx.hex()
        else:
            extra[b'source'] = ctx.hex()
        user = ctx.user()
        if opts.get('user'):
            user = opts['user']
            statedata[b'user'] = user
        date = ctx.date()
        if opts.get('date'):
            date = opts['date']
            statedata[b'date'] = date
        message = ctx.description()
        if opts.get('log'):
            message += b'\n(grafted from %s)' % ctx.hex()
            statedata[b'log'] = True

        # we don't merge the first commit when continuing
        if not cont:
            # perform the graft merge with p1(rev) as 'ancestor'
            overrides = {(b'ui', b'forcemerge'): opts.get('tool', b'')}
            base = ctx.p1() if basectx is None else basectx
            with ui.configoverride(overrides, b'graft'):
                stats = mergemod.graft(repo, ctx, base, [b'local', b'graft'])
            # report any conflicts
            if stats.unresolvedcount > 0:
                # write out state for --continue
                nodes = [repo[rev].hex() for rev in revs[pos:]]
                statedata[b'nodes'] = nodes
                stateversion = 1
                graftstate.save(stateversion, statedata)
                ui.error(_(b"abort: unresolved conflicts, can't continue\n"))
                ui.error(_(b"(use 'hg resolve' and 'hg graft --continue')\n"))
                return 1
        else:
            cont = False

        # commit if --no-commit is false
        if not opts.get('no_commit'):
            node = repo.commit(
                text=message, user=user, date=date, extra=extra, editor=editor
            )
            if node is None:
                ui.warn(
                    _(b'note: graft of %d:%s created no changes to commit\n')
                    % (ctx.rev(), ctx)
                )
            # checking that newnodes exist because old state files won't have it
            elif statedata.get(b'newnodes') is not None:
                nn = statedata[b'newnodes']
                assert isinstance(nn, list)  # list of bytes
                nn.append(node)

    # remove state when we complete successfully
    if not opts.get('dry_run'):
        graftstate.delete()

    return 0


def _stopgraft(ui, repo, graftstate):
    """stop the interrupted graft"""
    if not graftstate.exists():
        raise error.StateError(_(b"no interrupted graft found"))
    pctx = repo[b'.']
    mergemod.clean_update(pctx)
    graftstate.delete()
    ui.status(_(b"stopped the interrupted graft\n"))
    ui.status(_(b"working directory is now at %s\n") % pctx.hex()[:12])
    return 0


statemod.addunfinished(
    b'graft',
    fname=b'graftstate',
    clearable=True,
    stopflag=True,
    continueflag=True,
    abortfunc=cmdutil.hgabortgraft,
    cmdhint=_(b"use 'hg graft --continue' or 'hg graft --stop' to stop"),
)


@command(
    b'grep',
    [
        (b'0', b'print0', None, _(b'end fields with NUL')),
        (b'', b'all', None, _(b'an alias to --diff (DEPRECATED)')),
        (
            b'',
            b'diff',
            None,
            _(
                b'search revision differences for when the pattern was added '
                b'or removed'
            ),
        ),
        (b'a', b'text', None, _(b'treat all files as text')),
        (
            b'f',
            b'follow',
            None,
            _(
                b'follow changeset history,'
                b' or file history across copies and renames'
            ),
        ),
        (b'i', b'ignore-case', None, _(b'ignore case when matching')),
        (
            b'l',
            b'files-with-matches',
            None,
            _(b'print only filenames and revisions that match'),
        ),
        (b'n', b'line-number', None, _(b'print matching line numbers')),
        (
            b'r',
            b'rev',
            [],
            _(b'search files changed within revision range'),
            _(b'REV'),
        ),
        (
            b'',
            b'all-files',
            None,
            _(
                b'include all files in the changeset while grepping (DEPRECATED)'
            ),
        ),
        (b'u', b'user', None, _(b'list the author (long with -v)')),
        (b'd', b'date', None, _(b'list the date (short with -q)')),
    ]
    + formatteropts
    + walkopts,
    _(b'[--diff] [OPTION]... PATTERN [FILE]...'),
    helpcategory=command.CATEGORY_FILE_CONTENTS,
    inferrepo=True,
    intents={INTENT_READONLY},
)
def grep(ui, repo, pattern, *pats, **opts):
    """search for a pattern in specified files

    Search the working directory or revision history for a regular
    expression in the specified files for the entire repository.

    By default, grep searches the repository files in the working
    directory and prints the files where it finds a match. To specify
    historical revisions instead of the working directory, use the
    --rev flag.

    To search instead historical revision differences that contains a
    change in match status ("-" for a match that becomes a non-match,
    or "+" for a non-match that becomes a match), use the --diff flag.

    PATTERN can be any Python (roughly Perl-compatible) regular
    expression.

    If no FILEs are specified and the --rev flag isn't supplied, all
    files in the working directory are searched. When using the --rev
    flag and specifying FILEs, use the --follow argument to also
    follow the specified FILEs across renames and copies.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :change:  String. Character denoting insertion ``+`` or removal ``-``.
                Available if ``--diff`` is specified.
      :lineno:  Integer. Line number of the match.
      :path:    String. Repository-absolute path of the file.
      :texts:   List of text chunks.

      And each entry of ``{texts}`` provides the following sub-keywords.

      :matched: Boolean. True if the chunk matches the specified pattern.
      :text:    String. Chunk content.

      See :hg:`help templates.operators` for the list expansion syntax.

    Returns 0 if a match is found, 1 otherwise.

    """
    cmdutil.check_incompatible_arguments(opts, 'all_files', ['all', 'diff'])
    opts = pycompat.byteskwargs(opts)
    diff = opts.get(b'all') or opts.get(b'diff')
    follow = opts.get(b'follow')
    if opts.get(b'all_files') is None and not diff:
        opts[b'all_files'] = True
    plaingrep = (
        opts.get(b'all_files')
        and not opts.get(b'rev')
        and not opts.get(b'follow')
    )
    all_files = opts.get(b'all_files')
    if plaingrep:
        opts[b'rev'] = [b'wdir()']

    reflags = re.M
    if opts.get(b'ignore_case'):
        reflags |= re.I
    try:
        regexp = util.re.compile(pattern, reflags)
    except re.error as inst:
        ui.warn(
            _(b"grep: invalid match pattern: %s\n")
            % stringutil.forcebytestr(inst)
        )
        return 1
    sep, eol = b':', b'\n'
    if opts.get(b'print0'):
        sep = eol = b'\0'

    searcher = grepmod.grepsearcher(
        ui, repo, regexp, all_files=all_files, diff=diff, follow=follow
    )

    getfile = searcher._getfile

    uipathfn = scmutil.getuipathfn(repo)

    def display(fm, fn, ctx, pstates, states):
        rev = scmutil.intrev(ctx)
        if fm.isplain():
            formatuser = ui.shortuser
        else:
            formatuser = pycompat.bytestr
        if ui.quiet:
            datefmt = b'%Y-%m-%d'
        else:
            datefmt = b'%a %b %d %H:%M:%S %Y %1%2'
        found = False

        @util.cachefunc
        def binary():
            flog = getfile(fn)
            try:
                return stringutil.binary(flog.read(ctx.filenode(fn)))
            except error.WdirUnsupported:
                return ctx[fn].isbinary()

        fieldnamemap = {b'linenumber': b'lineno'}
        if diff:
            iter = grepmod.difflinestates(pstates, states)
        else:
            iter = [(b'', l) for l in states]
        for change, l in iter:
            fm.startitem()
            fm.context(ctx=ctx)
            fm.data(node=fm.hexfunc(scmutil.binnode(ctx)), path=fn)
            fm.plain(uipathfn(fn), label=b'grep.filename')

            cols = [
                (b'rev', b'%d', rev, not plaingrep, b''),
                (
                    b'linenumber',
                    b'%d',
                    l.linenum,
                    opts.get(b'line_number'),
                    b'',
                ),
            ]
            if diff:
                cols.append(
                    (
                        b'change',
                        b'%s',
                        change,
                        True,
                        b'grep.inserted '
                        if change == b'+'
                        else b'grep.deleted ',
                    )
                )
            cols.extend(
                [
                    (
                        b'user',
                        b'%s',
                        formatuser(ctx.user()),
                        opts.get(b'user'),
                        b'',
                    ),
                    (
                        b'date',
                        b'%s',
                        fm.formatdate(ctx.date(), datefmt),
                        opts.get(b'date'),
                        b'',
                    ),
                ]
            )
            for name, fmt, data, cond, extra_label in cols:
                if cond:
                    fm.plain(sep, label=b'grep.sep')
                field = fieldnamemap.get(name, name)
                label = extra_label + (b'grep.%s' % name)
                fm.condwrite(cond, field, fmt, data, label=label)
            if not opts.get(b'files_with_matches'):
                fm.plain(sep, label=b'grep.sep')
                if not opts.get(b'text') and binary():
                    fm.plain(_(b" Binary file matches"))
                else:
                    displaymatches(fm.nested(b'texts', tmpl=b'{text}'), l)
            fm.plain(eol)
            found = True
            if opts.get(b'files_with_matches'):
                break
        return found

    def displaymatches(fm, l):
        p = 0
        for s, e in l.findpos(regexp):
            if p < s:
                fm.startitem()
                fm.write(b'text', b'%s', l.line[p:s])
                fm.data(matched=False)
            fm.startitem()
            fm.write(b'text', b'%s', l.line[s:e], label=b'grep.match')
            fm.data(matched=True)
            p = e
        if p < len(l.line):
            fm.startitem()
            fm.write(b'text', b'%s', l.line[p:])
            fm.data(matched=False)
        fm.end()

    found = False

    wopts = logcmdutil.walkopts(
        pats=pats,
        opts=opts,
        revspec=opts[b'rev'],
        include_pats=opts[b'include'],
        exclude_pats=opts[b'exclude'],
        follow=follow,
        force_changelog_traversal=all_files,
        filter_revisions_by_pats=not all_files,
    )
    revs, makefilematcher = logcmdutil.makewalker(repo, wopts)

    ui.pager(b'grep')
    fm = ui.formatter(b'grep', opts)
    for fn, ctx, pstates, states in searcher.searchfiles(revs, makefilematcher):
        r = display(fm, fn, ctx, pstates, states)
        found = found or r
        if r and not diff and not all_files:
            searcher.skipfile(fn, ctx.rev())
    fm.end()

    return not found


@command(
    b'heads',
    [
        (
            b'r',
            b'rev',
            b'',
            _(b'show only heads which are descendants of STARTREV'),
            _(b'STARTREV'),
        ),
        (b't', b'topo', False, _(b'show topological heads only')),
        (
            b'a',
            b'active',
            False,
            _(b'show active branchheads only (DEPRECATED)'),
        ),
        (b'c', b'closed', False, _(b'show normal and closed branch heads')),
    ]
    + templateopts,
    _(b'[-ct] [-r STARTREV] [REV]...'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
    intents={INTENT_READONLY},
)
def heads(ui, repo, *branchrevs, **opts):
    """show branch heads

    With no arguments, show all open branch heads in the repository.
    Branch heads are changesets that have no descendants on the
    same branch. They are where development generally takes place and
    are the usual targets for update and merge operations.

    If one or more REVs are given, only open branch heads on the
    branches associated with the specified changesets are shown. This
    means that you can use :hg:`heads .` to see the heads on the
    currently checked-out branch.

    If -c/--closed is specified, also show branch heads marked closed
    (see :hg:`commit --close-branch`).

    If STARTREV is specified, only those heads that are descendants of
    STARTREV will be displayed.

    If -t/--topo is specified, named branch mechanics will be ignored and only
    topological heads (changesets with no children) will be shown.

    Returns 0 if matching heads are found, 1 if not.
    """

    opts = pycompat.byteskwargs(opts)
    start = None
    rev = opts.get(b'rev')
    if rev:
        repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
        start = scmutil.revsingle(repo, rev, None).node()

    if opts.get(b'topo'):
        heads = [repo[h] for h in repo.heads(start)]
    else:
        heads = []
        for branch in repo.branchmap():
            heads += repo.branchheads(branch, start, opts.get(b'closed'))
        heads = [repo[h] for h in heads]

    if branchrevs:
        branches = {
            repo[r].branch() for r in scmutil.revrange(repo, branchrevs)
        }
        heads = [h for h in heads if h.branch() in branches]

    if opts.get(b'active') and branchrevs:
        dagheads = repo.heads(start)
        heads = [h for h in heads if h.node() in dagheads]

    if branchrevs:
        haveheads = {h.branch() for h in heads}
        if branches - haveheads:
            headless = b', '.join(b for b in branches - haveheads)
            msg = _(b'no open branch heads found on branches %s')
            if opts.get(b'rev'):
                msg += _(b' (started at %s)') % opts[b'rev']
            ui.warn((msg + b'\n') % headless)

    if not heads:
        return 1

    ui.pager(b'heads')
    heads = sorted(heads, key=lambda x: -(x.rev()))
    displayer = logcmdutil.changesetdisplayer(ui, repo, opts)
    for ctx in heads:
        displayer.show(ctx)
    displayer.close()


@command(
    b'help',
    [
        (b'e', b'extension', None, _(b'show only help for extensions')),
        (b'c', b'command', None, _(b'show only help for commands')),
        (b'k', b'keyword', None, _(b'show topics matching keyword')),
        (
            b's',
            b'system',
            [],
            _(b'show help for specific platform(s)'),
            _(b'PLATFORM'),
        ),
    ],
    _(b'[-eck] [-s PLATFORM] [TOPIC]'),
    helpcategory=command.CATEGORY_HELP,
    norepo=True,
    intents={INTENT_READONLY},
)
def help_(ui, name=None, **opts):
    """show help for a given topic or a help overview

    With no arguments, print a list of commands with short help messages.

    Given a topic, extension, or command name, print help for that
    topic.

    Returns 0 if successful.
    """

    keep = opts.get('system') or []
    if len(keep) == 0:
        if pycompat.sysplatform.startswith(b'win'):
            keep.append(b'windows')
        elif pycompat.sysplatform == b'OpenVMS':
            keep.append(b'vms')
        elif pycompat.sysplatform == b'plan9':
            keep.append(b'plan9')
        else:
            keep.append(b'unix')
            keep.append(pycompat.sysplatform.lower())
    if ui.verbose:
        keep.append(b'verbose')

    commands = sys.modules[__name__]
    formatted = help.formattedhelp(ui, commands, name, keep=keep, **opts)
    ui.pager(b'help')
    ui.write(formatted)


@command(
    b'identify|id',
    [
        (b'r', b'rev', b'', _(b'identify the specified revision'), _(b'REV')),
        (b'n', b'num', None, _(b'show local revision number')),
        (b'i', b'id', None, _(b'show global revision id')),
        (b'b', b'branch', None, _(b'show branch')),
        (b't', b'tags', None, _(b'show tags')),
        (b'B', b'bookmarks', None, _(b'show bookmarks')),
    ]
    + remoteopts
    + formatteropts,
    _(b'[-nibtB] [-r REV] [SOURCE]'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
    optionalrepo=True,
    intents={INTENT_READONLY},
)
def identify(
    ui,
    repo,
    source=None,
    rev=None,
    num=None,
    id=None,
    branch=None,
    tags=None,
    bookmarks=None,
    **opts
):
    """identify the working directory or specified revision

    Print a summary identifying the repository state at REV using one or
    two parent hash identifiers, followed by a "+" if the working
    directory has uncommitted changes, the branch name (if not default),
    a list of tags, and a list of bookmarks.

    When REV is not given, print a summary of the current state of the
    repository including the working directory. Specify -r. to get information
    of the working directory parent without scanning uncommitted changes.

    Specifying a path to a repository root or Mercurial bundle will
    cause lookup to operate on that repository/bundle.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :dirty:   String. Character ``+`` denoting if the working directory has
                uncommitted changes.
      :id:      String. One or two nodes, optionally followed by ``+``.
      :parents: List of strings. Parent nodes of the changeset.

      Examples:

      - generate a build identifier for the working directory::

          hg id --id > build-id.dat

      - find the revision corresponding to a tag::

          hg id -n -r 1.3

      - check the most recent revision of a remote repository::

          hg id -r tip https://www.mercurial-scm.org/repo/hg/

    See :hg:`log` for generating more information about specific revisions,
    including full hash identifiers.

    Returns 0 if successful.
    """

    opts = pycompat.byteskwargs(opts)
    if not repo and not source:
        raise error.InputError(
            _(b"there is no Mercurial repository here (.hg not found)")
        )

    default = not (num or id or branch or tags or bookmarks)
    output = []
    revs = []

    peer = None
    try:
        if source:
            source, branches = urlutil.get_unique_pull_path(
                b'identify', repo, ui, source
            )
            # only pass ui when no repo
            peer = hg.peer(repo or ui, opts, source)
            repo = peer.local()
            revs, checkout = hg.addbranchrevs(repo, peer, branches, None)

        fm = ui.formatter(b'identify', opts)
        fm.startitem()

        if not repo:
            if num or branch or tags:
                raise error.InputError(
                    _(b"can't query remote revision number, branch, or tags")
                )
            if not rev and revs:
                rev = revs[0]
            if not rev:
                rev = b"tip"

            remoterev = peer.lookup(rev)
            hexrev = fm.hexfunc(remoterev)
            if default or id:
                output = [hexrev]
            fm.data(id=hexrev)

            @util.cachefunc
            def getbms():
                bms = []

                if b'bookmarks' in peer.listkeys(b'namespaces'):
                    hexremoterev = hex(remoterev)
                    bms = [
                        bm
                        for bm, bmr in pycompat.iteritems(
                            peer.listkeys(b'bookmarks')
                        )
                        if bmr == hexremoterev
                    ]

                return sorted(bms)

            if fm.isplain():
                if bookmarks:
                    output.extend(getbms())
                elif default and not ui.quiet:
                    # multiple bookmarks for a single parent separated by '/'
                    bm = b'/'.join(getbms())
                    if bm:
                        output.append(bm)
            else:
                fm.data(node=hex(remoterev))
                if bookmarks or b'bookmarks' in fm.datahint():
                    fm.data(bookmarks=fm.formatlist(getbms(), name=b'bookmark'))
        else:
            if rev:
                repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
            ctx = scmutil.revsingle(repo, rev, None)

            if ctx.rev() is None:
                ctx = repo[None]
                parents = ctx.parents()
                taglist = []
                for p in parents:
                    taglist.extend(p.tags())

                dirty = b""
                if ctx.dirty(missing=True, merge=False, branch=False):
                    dirty = b'+'
                fm.data(dirty=dirty)

                hexoutput = [fm.hexfunc(p.node()) for p in parents]
                if default or id:
                    output = [b"%s%s" % (b'+'.join(hexoutput), dirty)]
                fm.data(id=b"%s%s" % (b'+'.join(hexoutput), dirty))

                if num:
                    numoutput = [b"%d" % p.rev() for p in parents]
                    output.append(b"%s%s" % (b'+'.join(numoutput), dirty))

                fm.data(
                    parents=fm.formatlist(
                        [fm.hexfunc(p.node()) for p in parents], name=b'node'
                    )
                )
            else:
                hexoutput = fm.hexfunc(ctx.node())
                if default or id:
                    output = [hexoutput]
                fm.data(id=hexoutput)

                if num:
                    output.append(pycompat.bytestr(ctx.rev()))
                taglist = ctx.tags()

            if default and not ui.quiet:
                b = ctx.branch()
                if b != b'default':
                    output.append(b"(%s)" % b)

                # multiple tags for a single parent separated by '/'
                t = b'/'.join(taglist)
                if t:
                    output.append(t)

                # multiple bookmarks for a single parent separated by '/'
                bm = b'/'.join(ctx.bookmarks())
                if bm:
                    output.append(bm)
            else:
                if branch:
                    output.append(ctx.branch())

                if tags:
                    output.extend(taglist)

                if bookmarks:
                    output.extend(ctx.bookmarks())

            fm.data(node=ctx.hex())
            fm.data(branch=ctx.branch())
            fm.data(tags=fm.formatlist(taglist, name=b'tag', sep=b':'))
            fm.data(bookmarks=fm.formatlist(ctx.bookmarks(), name=b'bookmark'))
            fm.context(ctx=ctx)

        fm.plain(b"%s\n" % b' '.join(output))
        fm.end()
    finally:
        if peer:
            peer.close()


@command(
    b'import|patch',
    [
        (
            b'p',
            b'strip',
            1,
            _(
                b'directory strip option for patch. This has the same '
                b'meaning as the corresponding patch option'
            ),
            _(b'NUM'),
        ),
        (b'b', b'base', b'', _(b'base path (DEPRECATED)'), _(b'PATH')),
        (b'', b'secret', None, _(b'use the secret phase for committing')),
        (b'e', b'edit', False, _(b'invoke editor on commit messages')),
        (
            b'f',
            b'force',
            None,
            _(b'skip check for outstanding uncommitted changes (DEPRECATED)'),
        ),
        (
            b'',
            b'no-commit',
            None,
            _(b"don't commit, just update the working directory"),
        ),
        (
            b'',
            b'bypass',
            None,
            _(b"apply patch without touching the working directory"),
        ),
        (b'', b'partial', None, _(b'commit even if some hunks fail')),
        (b'', b'exact', None, _(b'abort if patch would apply lossily')),
        (b'', b'prefix', b'', _(b'apply patch to subdirectory'), _(b'DIR')),
        (
            b'',
            b'import-branch',
            None,
            _(b'use any branch information in patch (implied by --exact)'),
        ),
    ]
    + commitopts
    + commitopts2
    + similarityopts,
    _(b'[OPTION]... PATCH...'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def import_(ui, repo, patch1=None, *patches, **opts):
    """import an ordered set of patches

    Import a list of patches and commit them individually (unless
    --no-commit is specified).

    To read a patch from standard input (stdin), use "-" as the patch
    name. If a URL is specified, the patch will be downloaded from
    there.

    Import first applies changes to the working directory (unless
    --bypass is specified), import will abort if there are outstanding
    changes.

    Use --bypass to apply and commit patches directly to the
    repository, without affecting the working directory. Without
    --exact, patches will be applied on top of the working directory
    parent revision.

    You can import a patch straight from a mail message. Even patches
    as attachments work (to use the body part, it must have type
    text/plain or text/x-patch). From and Subject headers of email
    message are used as default committer and commit message. All
    text/plain body parts before first diff are added to the commit
    message.

    If the imported patch was generated by :hg:`export`, user and
    description from patch override values from message headers and
    body. Values given on command line with -m/--message and -u/--user
    override these.

    If --exact is specified, import will set the working directory to
    the parent of each patch before applying it, and will abort if the
    resulting changeset has a different ID than the one recorded in
    the patch. This will guard against various ways that portable
    patch formats and mail systems might fail to transfer Mercurial
    data or metadata. See :hg:`bundle` for lossless transmission.

    Use --partial to ensure a changeset will be created from the patch
    even if some hunks fail to apply. Hunks that fail to apply will be
    written to a <target-file>.rej file. Conflicts can then be resolved
    by hand before :hg:`commit --amend` is run to update the created
    changeset. This flag exists to let people import patches that
    partially apply without losing the associated metadata (author,
    date, description, ...).

    .. note::

       When no hunks apply cleanly, :hg:`import --partial` will create
       an empty changeset, importing only the patch metadata.

    With -s/--similarity, hg will attempt to discover renames and
    copies in the patch in the same way as :hg:`addremove`.

    It is possible to use external patch programs to perform the patch
    by setting the ``ui.patch`` configuration option. For the default
    internal tool, the fuzz can also be configured via ``patch.fuzz``.
    See :hg:`help config` for more information about configuration
    files and how to use these options.

    See :hg:`help dates` for a list of formats valid for -d/--date.

    .. container:: verbose

      Examples:

      - import a traditional patch from a website and detect renames::

          hg import -s 80 http://example.com/bugfix.patch

      - import a changeset from an hgweb server::

          hg import https://www.mercurial-scm.org/repo/hg/rev/5ca8c111e9aa

      - import all the patches in an Unix-style mbox::

          hg import incoming-patches.mbox

      - import patches from stdin::

          hg import -

      - attempt to exactly restore an exported changeset (not always
        possible)::

          hg import --exact proposed-fix.patch

      - use an external tool to apply a patch which is too fuzzy for
        the default internal tool.

          hg import --config ui.patch="patch --merge" fuzzy.patch

      - change the default fuzzing from 2 to a less strict 7

          hg import --config ui.fuzz=7 fuzz.patch

    Returns 0 on success, 1 on partial success (see --partial).
    """

    cmdutil.check_incompatible_arguments(
        opts, 'no_commit', ['bypass', 'secret']
    )
    cmdutil.check_incompatible_arguments(opts, 'exact', ['edit', 'prefix'])
    opts = pycompat.byteskwargs(opts)
    if not patch1:
        raise error.InputError(_(b'need at least one patch to import'))

    patches = (patch1,) + patches

    date = opts.get(b'date')
    if date:
        opts[b'date'] = dateutil.parsedate(date)

    exact = opts.get(b'exact')
    update = not opts.get(b'bypass')
    try:
        sim = float(opts.get(b'similarity') or 0)
    except ValueError:
        raise error.InputError(_(b'similarity must be a number'))
    if sim < 0 or sim > 100:
        raise error.InputError(_(b'similarity must be between 0 and 100'))
    if sim and not update:
        raise error.InputError(_(b'cannot use --similarity with --bypass'))

    base = opts[b"base"]
    msgs = []
    ret = 0

    with repo.wlock():
        if update:
            cmdutil.checkunfinished(repo)
            if exact or not opts.get(b'force'):
                cmdutil.bailifchanged(repo)

        if not opts.get(b'no_commit'):
            lock = repo.lock
            tr = lambda: repo.transaction(b'import')
            dsguard = util.nullcontextmanager
        else:
            lock = util.nullcontextmanager
            tr = util.nullcontextmanager
            dsguard = lambda: dirstateguard.dirstateguard(repo, b'import')
        with lock(), tr(), dsguard():
            parents = repo[None].parents()
            for patchurl in patches:
                if patchurl == b'-':
                    ui.status(_(b'applying patch from stdin\n'))
                    patchfile = ui.fin
                    patchurl = b'stdin'  # for error message
                else:
                    patchurl = os.path.join(base, patchurl)
                    ui.status(_(b'applying %s\n') % patchurl)
                    patchfile = hg.openpath(ui, patchurl, sendaccept=False)

                haspatch = False
                for hunk in patch.split(patchfile):
                    with patch.extract(ui, hunk) as patchdata:
                        msg, node, rej = cmdutil.tryimportone(
                            ui, repo, patchdata, parents, opts, msgs, hg.clean
                        )
                    if msg:
                        haspatch = True
                        ui.note(msg + b'\n')
                    if update or exact:
                        parents = repo[None].parents()
                    else:
                        parents = [repo[node]]
                    if rej:
                        ui.write_err(_(b"patch applied partially\n"))
                        ui.write_err(
                            _(
                                b"(fix the .rej files and run "
                                b"`hg commit --amend`)\n"
                            )
                        )
                        ret = 1
                        break

                if not haspatch:
                    raise error.InputError(_(b'%s: no diffs found') % patchurl)

            if msgs:
                repo.savecommitmessage(b'\n* * *\n'.join(msgs))
        return ret


@command(
    b'incoming|in',
    [
        (
            b'f',
            b'force',
            None,
            _(b'run even if remote repository is unrelated'),
        ),
        (b'n', b'newest-first', None, _(b'show newest record first')),
        (b'', b'bundle', b'', _(b'file to store the bundles into'), _(b'FILE')),
        (
            b'r',
            b'rev',
            [],
            _(b'a remote changeset intended to be added'),
            _(b'REV'),
        ),
        (b'B', b'bookmarks', False, _(b"compare bookmarks")),
        (
            b'b',
            b'branch',
            [],
            _(b'a specific branch you would like to pull'),
            _(b'BRANCH'),
        ),
    ]
    + logopts
    + remoteopts
    + subrepoopts,
    _(b'[-p] [-n] [-M] [-f] [-r REV]... [--bundle FILENAME] [SOURCE]'),
    helpcategory=command.CATEGORY_REMOTE_REPO_MANAGEMENT,
)
def incoming(ui, repo, source=b"default", **opts):
    """show new changesets found in source

    Show new changesets found in the specified path/URL or the default
    pull location. These are the changesets that would have been pulled
    by :hg:`pull` at the time you issued this command.

    See pull for valid source format details.

    .. container:: verbose

      With -B/--bookmarks, the result of bookmark comparison between
      local and remote repositories is displayed. With -v/--verbose,
      status is also displayed for each bookmark like below::

        BM1               01234567890a added
        BM2               1234567890ab advanced
        BM3               234567890abc diverged
        BM4               34567890abcd changed

      The action taken locally when pulling depends on the
      status of each bookmark:

      :``added``: pull will create it
      :``advanced``: pull will update it
      :``diverged``: pull will create a divergent bookmark
      :``changed``: result depends on remote changesets

      From the point of view of pulling behavior, bookmark
      existing only in the remote repository are treated as ``added``,
      even if it is in fact locally deleted.

    .. container:: verbose

      For remote repository, using --bundle avoids downloading the
      changesets twice if the incoming is followed by a pull.

      Examples:

      - show incoming changes with patches and full description::

          hg incoming -vp

      - show incoming changes excluding merges, store a bundle::

          hg in -vpM --bundle incoming.hg
          hg pull incoming.hg

      - briefly list changes inside a bundle::

          hg in changes.hg -T "{desc|firstline}\\n"

    Returns 0 if there are incoming changes, 1 otherwise.
    """
    opts = pycompat.byteskwargs(opts)
    if opts.get(b'graph'):
        logcmdutil.checkunsupportedgraphflags([], opts)

        def display(other, chlist, displayer):
            revdag = logcmdutil.graphrevs(other, chlist, opts)
            logcmdutil.displaygraph(
                ui, repo, revdag, displayer, graphmod.asciiedges
            )

        hg._incoming(display, lambda: 1, ui, repo, source, opts, buffered=True)
        return 0

    cmdutil.check_incompatible_arguments(opts, b'subrepos', [b'bundle'])

    if opts.get(b'bookmarks'):
        srcs = urlutil.get_pull_paths(repo, ui, [source], opts.get(b'branch'))
        for source, branches in srcs:
            other = hg.peer(repo, opts, source)
            try:
                if b'bookmarks' not in other.listkeys(b'namespaces'):
                    ui.warn(_(b"remote doesn't support bookmarks\n"))
                    return 0
                ui.pager(b'incoming')
                ui.status(
                    _(b'comparing with %s\n') % urlutil.hidepassword(source)
                )
                return bookmarks.incoming(ui, repo, other)
            finally:
                other.close()

    return hg.incoming(ui, repo, source, opts)


@command(
    b'init',
    remoteopts,
    _(b'[-e CMD] [--remotecmd CMD] [DEST]'),
    helpcategory=command.CATEGORY_REPO_CREATION,
    helpbasic=True,
    norepo=True,
)
def init(ui, dest=b".", **opts):
    """create a new repository in the given directory

    Initialize a new repository in the given directory. If the given
    directory does not exist, it will be created.

    If no directory is given, the current directory is used.

    It is possible to specify an ``ssh://`` URL as the destination.
    See :hg:`help urls` for more information.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    path = urlutil.get_clone_path(ui, dest)[1]
    peer = hg.peer(ui, opts, path, create=True)
    peer.close()


@command(
    b'locate',
    [
        (
            b'r',
            b'rev',
            b'',
            _(b'search the repository as it is in REV'),
            _(b'REV'),
        ),
        (
            b'0',
            b'print0',
            None,
            _(b'end filenames with NUL, for use with xargs'),
        ),
        (
            b'f',
            b'fullpath',
            None,
            _(b'print complete paths from the filesystem root'),
        ),
    ]
    + walkopts,
    _(b'[OPTION]... [PATTERN]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
)
def locate(ui, repo, *pats, **opts):
    """locate files matching specific patterns (DEPRECATED)

    Print files under Mercurial control in the working directory whose
    names match the given patterns.

    By default, this command searches all directories in the working
    directory. To search just the current directory and its
    subdirectories, use "--include .".

    If no patterns are given to match, this command prints the names
    of all files under Mercurial control in the working directory.

    If you want to feed the output of this command into the "xargs"
    command, use the -0 option to both this command and "xargs". This
    will avoid the problem of "xargs" treating single filenames that
    contain whitespace as multiple filenames.

    See :hg:`help files` for a more versatile command.

    Returns 0 if a match is found, 1 otherwise.
    """
    opts = pycompat.byteskwargs(opts)
    if opts.get(b'print0'):
        end = b'\0'
    else:
        end = b'\n'
    ctx = scmutil.revsingle(repo, opts.get(b'rev'), None)

    ret = 1
    m = scmutil.match(
        ctx, pats, opts, default=b'relglob', badfn=lambda x, y: False
    )

    ui.pager(b'locate')
    if ctx.rev() is None:
        # When run on the working copy, "locate" includes removed files, so
        # we get the list of files from the dirstate.
        filesgen = sorted(repo.dirstate.matches(m))
    else:
        filesgen = ctx.matches(m)
    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=bool(pats))
    for abs in filesgen:
        if opts.get(b'fullpath'):
            ui.write(repo.wjoin(abs), end)
        else:
            ui.write(uipathfn(abs), end)
        ret = 0

    return ret


@command(
    b'log|history',
    [
        (
            b'f',
            b'follow',
            None,
            _(
                b'follow changeset history, or file history across copies and renames'
            ),
        ),
        (
            b'',
            b'follow-first',
            None,
            _(b'only follow the first parent of merge changesets (DEPRECATED)'),
        ),
        (
            b'd',
            b'date',
            b'',
            _(b'show revisions matching date spec'),
            _(b'DATE'),
        ),
        (b'C', b'copies', None, _(b'show copied files')),
        (
            b'k',
            b'keyword',
            [],
            _(b'do case-insensitive search for a given text'),
            _(b'TEXT'),
        ),
        (
            b'r',
            b'rev',
            [],
            _(b'revisions to select or follow from'),
            _(b'REV'),
        ),
        (
            b'L',
            b'line-range',
            [],
            _(b'follow line range of specified file (EXPERIMENTAL)'),
            _(b'FILE,RANGE'),
        ),
        (
            b'',
            b'removed',
            None,
            _(b'include revisions where files were removed'),
        ),
        (
            b'm',
            b'only-merges',
            None,
            _(b'show only merges (DEPRECATED) (use -r "merge()" instead)'),
        ),
        (b'u', b'user', [], _(b'revisions committed by user'), _(b'USER')),
        (
            b'',
            b'only-branch',
            [],
            _(
                b'show only changesets within the given named branch (DEPRECATED)'
            ),
            _(b'BRANCH'),
        ),
        (
            b'b',
            b'branch',
            [],
            _(b'show changesets within the given named branch'),
            _(b'BRANCH'),
        ),
        (
            b'B',
            b'bookmark',
            [],
            _(b"show changesets within the given bookmark"),
            _(b'BOOKMARK'),
        ),
        (
            b'P',
            b'prune',
            [],
            _(b'do not display revision or any of its ancestors'),
            _(b'REV'),
        ),
    ]
    + logopts
    + walkopts,
    _(b'[OPTION]... [FILE]'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
    helpbasic=True,
    inferrepo=True,
    intents={INTENT_READONLY},
)
def log(ui, repo, *pats, **opts):
    """show revision history of entire repository or files

    Print the revision history of the specified files or the entire
    project.

    If no revision range is specified, the default is ``tip:0`` unless
    --follow is set.

    File history is shown without following rename or copy history of
    files. Use -f/--follow with a filename to follow history across
    renames and copies. --follow without a filename will only show
    ancestors of the starting revisions. The starting revisions can be
    specified by -r/--rev, which default to the working directory parent.

    By default this command prints revision number and changeset id,
    tags, non-trivial parents, user, date and time, and a summary for
    each commit. When the -v/--verbose switch is used, the list of
    changed files and full commit message are shown.

    With --graph the revisions are shown as an ASCII art DAG with the most
    recent changeset at the top.
    'o' is a changeset, '@' is a working directory parent, '%' is a changeset
    involved in an unresolved merge conflict, '_' closes a branch,
    'x' is obsolete, '*' is unstable, and '+' represents a fork where the
    changeset from the lines below is a parent of the 'o' merge on the same
    line.
    Paths in the DAG are represented with '|', '/' and so forth. ':' in place
    of a '|' indicates one or more revisions in a path are omitted.

    .. container:: verbose

       Use -L/--line-range FILE,M:N options to follow the history of lines
       from M to N in FILE. With -p/--patch only diff hunks affecting
       specified line range will be shown. This option requires --follow;
       it can be specified multiple times. Currently, this option is not
       compatible with --graph. This option is experimental.

    .. note::

       :hg:`log --patch` may generate unexpected diff output for merge
       changesets, as it will only compare the merge changeset against
       its first parent. Also, only files different from BOTH parents
       will appear in files:.

    .. note::

       For performance reasons, :hg:`log FILE` may omit duplicate changes
       made on branches and will not show removals or mode changes. To
       see all such changes, use the --removed switch.

    .. container:: verbose

       .. note::

          The history resulting from -L/--line-range options depends on diff
          options; for instance if white-spaces are ignored, respective changes
          with only white-spaces in specified line range will not be listed.

    .. container:: verbose

      Some examples:

      - changesets with full descriptions and file lists::

          hg log -v

      - changesets ancestral to the working directory::

          hg log -f

      - last 10 commits on the current branch::

          hg log -l 10 -b .

      - changesets showing all modifications of a file, including removals::

          hg log --removed file.c

      - all changesets that touch a directory, with diffs, excluding merges::

          hg log -Mp lib/

      - all revision numbers that match a keyword::

          hg log -k bug --template "{rev}\\n"

      - the full hash identifier of the working directory parent::

          hg log -r . --template "{node}\\n"

      - list available log templates::

          hg log -T list

      - check if a given changeset is included in a tagged release::

          hg log -r "a21ccf and ancestor(1.9)"

      - find all changesets by some user in a date range::

          hg log -k alice -d "may 2008 to jul 2008"

      - summary of all changesets after the last tag::

          hg log -r "last(tagged())::" --template "{desc|firstline}\\n"

      - changesets touching lines 13 to 23 for file.c::

          hg log -L file.c,13:23

      - changesets touching lines 13 to 23 for file.c and lines 2 to 6 of
        main.c with patch::

          hg log -L file.c,13:23 -L main.c,2:6 -p

    See :hg:`help dates` for a list of formats valid for -d/--date.

    See :hg:`help revisions` for more about specifying and ordering
    revisions.

    See :hg:`help templates` for more about pre-packaged styles and
    specifying custom templates. The default template used by the log
    command can be customized via the ``command-templates.log`` configuration
    setting.

    Returns 0 on success.

    """
    opts = pycompat.byteskwargs(opts)
    linerange = opts.get(b'line_range')

    if linerange and not opts.get(b'follow'):
        raise error.InputError(_(b'--line-range requires --follow'))

    if linerange and pats:
        # TODO: take pats as patterns with no line-range filter
        raise error.InputError(
            _(b'FILE arguments are not compatible with --line-range option')
        )

    repo = scmutil.unhidehashlikerevs(repo, opts.get(b'rev'), b'nowarn')
    walk_opts = logcmdutil.parseopts(ui, pats, opts)
    revs, differ = logcmdutil.getrevs(repo, walk_opts)
    if linerange:
        # TODO: should follow file history from logcmdutil._initialrevs(),
        # then filter the result by logcmdutil._makerevset() and --limit
        revs, differ = logcmdutil.getlinerangerevs(repo, revs, opts)

    getcopies = None
    if opts.get(b'copies'):
        endrev = None
        if revs:
            endrev = revs.max() + 1
        getcopies = scmutil.getcopiesfn(repo, endrev=endrev)

    ui.pager(b'log')
    displayer = logcmdutil.changesetdisplayer(
        ui, repo, opts, differ, buffered=True
    )
    if opts.get(b'graph'):
        displayfn = logcmdutil.displaygraphrevs
    else:
        displayfn = logcmdutil.displayrevs
    displayfn(ui, repo, revs, displayer, getcopies)


@command(
    b'manifest',
    [
        (b'r', b'rev', b'', _(b'revision to display'), _(b'REV')),
        (b'', b'all', False, _(b"list files from all revisions")),
    ]
    + formatteropts,
    _(b'[-r REV]'),
    helpcategory=command.CATEGORY_MAINTENANCE,
    intents={INTENT_READONLY},
)
def manifest(ui, repo, node=None, rev=None, **opts):
    """output the current or given revision of the project manifest

    Print a list of version controlled files for the given revision.
    If no revision is given, the first parent of the working directory
    is used, or the null revision if no revision is checked out.

    With -v, print file permissions, symlink and executable bits.
    With --debug, print file revision hashes.

    If option --all is specified, the list of all files from all revisions
    is printed. This includes deleted and renamed files.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    fm = ui.formatter(b'manifest', opts)

    if opts.get(b'all'):
        if rev or node:
            raise error.InputError(_(b"can't specify a revision with --all"))

        res = set()
        for rev in repo:
            ctx = repo[rev]
            res |= set(ctx.files())

        ui.pager(b'manifest')
        for f in sorted(res):
            fm.startitem()
            fm.write(b"path", b'%s\n', f)
        fm.end()
        return

    if rev and node:
        raise error.InputError(_(b"please specify just one revision"))

    if not node:
        node = rev

    char = {b'l': b'@', b'x': b'*', b'': b'', b't': b'd'}
    mode = {b'l': b'644', b'x': b'755', b'': b'644', b't': b'755'}
    if node:
        repo = scmutil.unhidehashlikerevs(repo, [node], b'nowarn')
    ctx = scmutil.revsingle(repo, node)
    mf = ctx.manifest()
    ui.pager(b'manifest')
    for f in ctx:
        fm.startitem()
        fm.context(ctx=ctx)
        fl = ctx[f].flags()
        fm.condwrite(ui.debugflag, b'hash', b'%s ', hex(mf[f]))
        fm.condwrite(ui.verbose, b'mode type', b'%s %1s ', mode[fl], char[fl])
        fm.write(b'path', b'%s\n', f)
    fm.end()


@command(
    b'merge',
    [
        (
            b'f',
            b'force',
            None,
            _(b'force a merge including outstanding changes (DEPRECATED)'),
        ),
        (b'r', b'rev', b'', _(b'revision to merge'), _(b'REV')),
        (
            b'P',
            b'preview',
            None,
            _(b'review revisions to merge (no merge is performed)'),
        ),
        (b'', b'abort', None, _(b'abort the ongoing merge')),
    ]
    + mergetoolopts,
    _(b'[-P] [[-r] REV]'),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
    helpbasic=True,
)
def merge(ui, repo, node=None, **opts):
    """merge another revision into working directory

    The current working directory is updated with all changes made in
    the requested revision since the last common predecessor revision.

    Files that changed between either parent are marked as changed for
    the next commit and a commit must be performed before any further
    updates to the repository are allowed. The next commit will have
    two parents.

    ``--tool`` can be used to specify the merge tool used for file
    merges. It overrides the HGMERGE environment variable and your
    configuration files. See :hg:`help merge-tools` for options.

    If no revision is specified, the working directory's parent is a
    head revision, and the current branch contains exactly one other
    head, the other head is merged with by default. Otherwise, an
    explicit revision with which to merge must be provided.

    See :hg:`help resolve` for information on handling file conflicts.

    To undo an uncommitted merge, use :hg:`merge --abort` which
    will check out a clean copy of the original merge parent, losing
    all changes.

    Returns 0 on success, 1 if there are unresolved files.
    """

    opts = pycompat.byteskwargs(opts)
    abort = opts.get(b'abort')
    if abort and repo.dirstate.p2() == repo.nullid:
        cmdutil.wrongtooltocontinue(repo, _(b'merge'))
    cmdutil.check_incompatible_arguments(opts, b'abort', [b'rev', b'preview'])
    if abort:
        state = cmdutil.getunfinishedstate(repo)
        if state and state._opname != b'merge':
            raise error.StateError(
                _(b'cannot abort merge with %s in progress') % (state._opname),
                hint=state.hint(),
            )
        if node:
            raise error.InputError(_(b"cannot specify a node with --abort"))
        return hg.abortmerge(repo.ui, repo)

    if opts.get(b'rev') and node:
        raise error.InputError(_(b"please specify just one revision"))
    if not node:
        node = opts.get(b'rev')

    if node:
        ctx = scmutil.revsingle(repo, node)
    else:
        if ui.configbool(b'commands', b'merge.require-rev'):
            raise error.InputError(
                _(
                    b'configuration requires specifying revision to merge '
                    b'with'
                )
            )
        ctx = repo[destutil.destmerge(repo)]

    if ctx.node() is None:
        raise error.InputError(
            _(b'merging with the working copy has no effect')
        )

    if opts.get(b'preview'):
        # find nodes that are ancestors of p2 but not of p1
        p1 = repo[b'.'].node()
        p2 = ctx.node()
        nodes = repo.changelog.findmissing(common=[p1], heads=[p2])

        displayer = logcmdutil.changesetdisplayer(ui, repo, opts)
        for node in nodes:
            displayer.show(repo[node])
        displayer.close()
        return 0

    # ui.forcemerge is an internal variable, do not document
    overrides = {(b'ui', b'forcemerge'): opts.get(b'tool', b'')}
    with ui.configoverride(overrides, b'merge'):
        force = opts.get(b'force')
        labels = [b'working copy', b'merge rev']
        return hg.merge(ctx, force=force, labels=labels)


statemod.addunfinished(
    b'merge',
    fname=None,
    clearable=True,
    allowcommit=True,
    cmdmsg=_(b'outstanding uncommitted merge'),
    abortfunc=hg.abortmerge,
    statushint=_(
        b'To continue:    hg commit\nTo abort:       hg merge --abort'
    ),
    cmdhint=_(b"use 'hg commit' or 'hg merge --abort'"),
)


@command(
    b'outgoing|out',
    [
        (
            b'f',
            b'force',
            None,
            _(b'run even when the destination is unrelated'),
        ),
        (
            b'r',
            b'rev',
            [],
            _(b'a changeset intended to be included in the destination'),
            _(b'REV'),
        ),
        (b'n', b'newest-first', None, _(b'show newest record first')),
        (b'B', b'bookmarks', False, _(b'compare bookmarks')),
        (
            b'b',
            b'branch',
            [],
            _(b'a specific branch you would like to push'),
            _(b'BRANCH'),
        ),
    ]
    + logopts
    + remoteopts
    + subrepoopts,
    _(b'[-M] [-p] [-n] [-f] [-r REV]... [DEST]...'),
    helpcategory=command.CATEGORY_REMOTE_REPO_MANAGEMENT,
)
def outgoing(ui, repo, *dests, **opts):
    """show changesets not found in the destination

    Show changesets not found in the specified destination repository
    or the default push location. These are the changesets that would
    be pushed if a push was requested.

    See pull for details of valid destination formats.

    .. container:: verbose

      With -B/--bookmarks, the result of bookmark comparison between
      local and remote repositories is displayed. With -v/--verbose,
      status is also displayed for each bookmark like below::

        BM1               01234567890a added
        BM2                            deleted
        BM3               234567890abc advanced
        BM4               34567890abcd diverged
        BM5               4567890abcde changed

      The action taken when pushing depends on the
      status of each bookmark:

      :``added``: push with ``-B`` will create it
      :``deleted``: push with ``-B`` will delete it
      :``advanced``: push will update it
      :``diverged``: push with ``-B`` will update it
      :``changed``: push with ``-B`` will update it

      From the point of view of pushing behavior, bookmarks
      existing only in the remote repository are treated as
      ``deleted``, even if it is in fact added remotely.

    Returns 0 if there are outgoing changes, 1 otherwise.
    """
    opts = pycompat.byteskwargs(opts)
    if opts.get(b'bookmarks'):
        for path in urlutil.get_push_paths(repo, ui, dests):
            dest = path.pushloc or path.loc
            other = hg.peer(repo, opts, dest)
            try:
                if b'bookmarks' not in other.listkeys(b'namespaces'):
                    ui.warn(_(b"remote doesn't support bookmarks\n"))
                    return 0
                ui.status(
                    _(b'comparing with %s\n') % urlutil.hidepassword(dest)
                )
                ui.pager(b'outgoing')
                return bookmarks.outgoing(ui, repo, other)
            finally:
                other.close()

    return hg.outgoing(ui, repo, dests, opts)


@command(
    b'parents',
    [
        (
            b'r',
            b'rev',
            b'',
            _(b'show parents of the specified revision'),
            _(b'REV'),
        ),
    ]
    + templateopts,
    _(b'[-r REV] [FILE]'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
    inferrepo=True,
)
def parents(ui, repo, file_=None, **opts):
    """show the parents of the working directory or revision (DEPRECATED)

    Print the working directory's parent revisions. If a revision is
    given via -r/--rev, the parent of that revision will be printed.
    If a file argument is given, the revision in which the file was
    last changed (before the working directory revision or the
    argument to --rev if given) is printed.

    This command is equivalent to::

        hg log -r "p1()+p2()" or
        hg log -r "p1(REV)+p2(REV)" or
        hg log -r "max(::p1() and file(FILE))+max(::p2() and file(FILE))" or
        hg log -r "max(::p1(REV) and file(FILE))+max(::p2(REV) and file(FILE))"

    See :hg:`summary` and :hg:`help revsets` for related information.

    Returns 0 on success.
    """

    opts = pycompat.byteskwargs(opts)
    rev = opts.get(b'rev')
    if rev:
        repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
    ctx = scmutil.revsingle(repo, rev, None)

    if file_:
        m = scmutil.match(ctx, (file_,), opts)
        if m.anypats() or len(m.files()) != 1:
            raise error.InputError(_(b'can only specify an explicit filename'))
        file_ = m.files()[0]
        filenodes = []
        for cp in ctx.parents():
            if not cp:
                continue
            try:
                filenodes.append(cp.filenode(file_))
            except error.LookupError:
                pass
        if not filenodes:
            raise error.InputError(_(b"'%s' not found in manifest") % file_)
        p = []
        for fn in filenodes:
            fctx = repo.filectx(file_, fileid=fn)
            p.append(fctx.node())
    else:
        p = [cp.node() for cp in ctx.parents()]

    displayer = logcmdutil.changesetdisplayer(ui, repo, opts)
    for n in p:
        if n != repo.nullid:
            displayer.show(repo[n])
    displayer.close()


@command(
    b'paths',
    formatteropts,
    _(b'[NAME]'),
    helpcategory=command.CATEGORY_REMOTE_REPO_MANAGEMENT,
    optionalrepo=True,
    intents={INTENT_READONLY},
)
def paths(ui, repo, search=None, **opts):
    """show aliases for remote repositories

    Show definition of symbolic path name NAME. If no name is given,
    show definition of all available names.

    Option -q/--quiet suppresses all output when searching for NAME
    and shows only the path names when listing all definitions.

    Path names are defined in the [paths] section of your
    configuration file and in ``/etc/mercurial/hgrc``. If run inside a
    repository, ``.hg/hgrc`` is used, too.

    The path names ``default`` and ``default-push`` have a special
    meaning.  When performing a push or pull operation, they are used
    as fallbacks if no location is specified on the command-line.
    When ``default-push`` is set, it will be used for push and
    ``default`` will be used for pull; otherwise ``default`` is used
    as the fallback for both.  When cloning a repository, the clone
    source is written as ``default`` in ``.hg/hgrc``.

    .. note::

       ``default`` and ``default-push`` apply to all inbound (e.g.
       :hg:`incoming`) and outbound (e.g. :hg:`outgoing`, :hg:`email`
       and :hg:`bundle`) operations.

    See :hg:`help urls` for more information.

    .. container:: verbose

      Template:

      The following keywords are supported. See also :hg:`help templates`.

      :name:    String. Symbolic name of the path alias.
      :pushurl: String. URL for push operations.
      :url:     String. URL or directory path for the other operations.

    Returns 0 on success.
    """

    opts = pycompat.byteskwargs(opts)

    pathitems = urlutil.list_paths(ui, search)
    ui.pager(b'paths')

    fm = ui.formatter(b'paths', opts)
    if fm.isplain():
        hidepassword = urlutil.hidepassword
    else:
        hidepassword = bytes
    if ui.quiet:
        namefmt = b'%s\n'
    else:
        namefmt = b'%s = '
    showsubopts = not search and not ui.quiet

    for name, path in pathitems:
        fm.startitem()
        fm.condwrite(not search, b'name', namefmt, name)
        fm.condwrite(not ui.quiet, b'url', b'%s\n', hidepassword(path.rawloc))
        for subopt, value in sorted(path.suboptions.items()):
            assert subopt not in (b'name', b'url')
            if showsubopts:
                fm.plain(b'%s:%s = ' % (name, subopt))
            if isinstance(value, bool):
                if value:
                    value = b'yes'
                else:
                    value = b'no'
            fm.condwrite(showsubopts, subopt, b'%s\n', value)

    fm.end()

    if search and not pathitems:
        if not ui.quiet:
            ui.warn(_(b"not found!\n"))
        return 1
    else:
        return 0


@command(
    b'phase',
    [
        (b'p', b'public', False, _(b'set changeset phase to public')),
        (b'd', b'draft', False, _(b'set changeset phase to draft')),
        (b's', b'secret', False, _(b'set changeset phase to secret')),
        (b'f', b'force', False, _(b'allow to move boundary backward')),
        (b'r', b'rev', [], _(b'target revision'), _(b'REV')),
    ],
    _(b'[-p|-d|-s] [-f] [-r] [REV...]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def phase(ui, repo, *revs, **opts):
    """set or show the current phase name

    With no argument, show the phase name of the current revision(s).

    With one of -p/--public, -d/--draft or -s/--secret, change the
    phase value of the specified revisions.

    Unless -f/--force is specified, :hg:`phase` won't move changesets from a
    lower phase to a higher phase. Phases are ordered as follows::

        public < draft < secret

    Returns 0 on success, 1 if some phases could not be changed.

    (For more information about the phases concept, see :hg:`help phases`.)
    """
    opts = pycompat.byteskwargs(opts)
    # search for a unique phase argument
    targetphase = None
    for idx, name in enumerate(phases.cmdphasenames):
        if opts[name]:
            if targetphase is not None:
                raise error.InputError(_(b'only one phase can be specified'))
            targetphase = idx

    # look for specified revision
    revs = list(revs)
    revs.extend(opts[b'rev'])
    if not revs:
        # display both parents as the second parent phase can influence
        # the phase of a merge commit
        revs = [c.rev() for c in repo[None].parents()]

    revs = scmutil.revrange(repo, revs)

    ret = 0
    if targetphase is None:
        # display
        for r in revs:
            ctx = repo[r]
            ui.write(b'%i: %s\n' % (ctx.rev(), ctx.phasestr()))
    else:
        with repo.lock(), repo.transaction(b"phase") as tr:
            # set phase
            if not revs:
                raise error.InputError(_(b'empty revision set'))
            nodes = [repo[r].node() for r in revs]
            # moving revision from public to draft may hide them
            # We have to check result on an unfiltered repository
            unfi = repo.unfiltered()
            getphase = unfi._phasecache.phase
            olddata = [getphase(unfi, r) for r in unfi]
            phases.advanceboundary(repo, tr, targetphase, nodes)
            if opts[b'force']:
                phases.retractboundary(repo, tr, targetphase, nodes)
        getphase = unfi._phasecache.phase
        newdata = [getphase(unfi, r) for r in unfi]
        changes = sum(newdata[r] != olddata[r] for r in unfi)
        cl = unfi.changelog
        rejected = [n for n in nodes if newdata[cl.rev(n)] < targetphase]
        if rejected:
            ui.warn(
                _(
                    b'cannot move %i changesets to a higher '
                    b'phase, use --force\n'
                )
                % len(rejected)
            )
            ret = 1
        if changes:
            msg = _(b'phase changed for %i changesets\n') % changes
            if ret:
                ui.status(msg)
            else:
                ui.note(msg)
        else:
            ui.warn(_(b'no phases changed\n'))
    return ret


def postincoming(ui, repo, modheads, optupdate, checkout, brev):
    """Run after a changegroup has been added via pull/unbundle

    This takes arguments below:

    :modheads: change of heads by pull/unbundle
    :optupdate: updating working directory is needed or not
    :checkout: update destination revision (or None to default destination)
    :brev: a name, which might be a bookmark to be activated after updating

    return True if update raise any conflict, False otherwise.
    """
    if modheads == 0:
        return False
    if optupdate:
        try:
            return hg.updatetotally(ui, repo, checkout, brev)
        except error.UpdateAbort as inst:
            msg = _(b"not updating: %s") % stringutil.forcebytestr(inst)
            hint = inst.hint
            raise error.UpdateAbort(msg, hint=hint)
    if modheads is not None and modheads > 1:
        currentbranchheads = len(repo.branchheads())
        if currentbranchheads == modheads:
            ui.status(
                _(b"(run 'hg heads' to see heads, 'hg merge' to merge)\n")
            )
        elif currentbranchheads > 1:
            ui.status(
                _(b"(run 'hg heads .' to see heads, 'hg merge' to merge)\n")
            )
        else:
            ui.status(_(b"(run 'hg heads' to see heads)\n"))
    elif not ui.configbool(b'commands', b'update.requiredest'):
        ui.status(_(b"(run 'hg update' to get a working copy)\n"))
    return False


@command(
    b'pull',
    [
        (
            b'u',
            b'update',
            None,
            _(b'update to new branch head if new descendants were pulled'),
        ),
        (
            b'f',
            b'force',
            None,
            _(b'run even when remote repository is unrelated'),
        ),
        (
            b'',
            b'confirm',
            None,
            _(b'confirm pull before applying changes'),
        ),
        (
            b'r',
            b'rev',
            [],
            _(b'a remote changeset intended to be added'),
            _(b'REV'),
        ),
        (b'B', b'bookmark', [], _(b"bookmark to pull"), _(b'BOOKMARK')),
        (
            b'b',
            b'branch',
            [],
            _(b'a specific branch you would like to pull'),
            _(b'BRANCH'),
        ),
    ]
    + remoteopts,
    _(b'[-u] [-f] [-r REV]... [-e CMD] [--remotecmd CMD] [SOURCE]...'),
    helpcategory=command.CATEGORY_REMOTE_REPO_MANAGEMENT,
    helpbasic=True,
)
def pull(ui, repo, *sources, **opts):
    """pull changes from the specified source

    Pull changes from a remote repository to a local one.

    This finds all changes from the repository at the specified path
    or URL and adds them to a local repository (the current one unless
    -R is specified). By default, this does not update the copy of the
    project in the working directory.

    When cloning from servers that support it, Mercurial may fetch
    pre-generated data. When this is done, hooks operating on incoming
    changesets and changegroups may fire more than once, once for each
    pre-generated bundle and as well as for any additional remaining
    data. See :hg:`help -e clonebundles` for more.

    Use :hg:`incoming` if you want to see what would have been added
    by a pull at the time you issued this command. If you then decide
    to add those changes to the repository, you should use :hg:`pull
    -r X` where ``X`` is the last changeset listed by :hg:`incoming`.

    If SOURCE is omitted, the 'default' path will be used.
    See :hg:`help urls` for more information.

    If multiple sources are specified, they will be pulled sequentially as if
    the command was run multiple time. If --update is specify and the command
    will stop at the first failed --update.

    Specifying bookmark as ``.`` is equivalent to specifying the active
    bookmark's name.

    Returns 0 on success, 1 if an update had unresolved files.
    """

    opts = pycompat.byteskwargs(opts)
    if ui.configbool(b'commands', b'update.requiredest') and opts.get(
        b'update'
    ):
        msg = _(b'update destination required by configuration')
        hint = _(b'use hg pull followed by hg update DEST')
        raise error.InputError(msg, hint=hint)

    sources = urlutil.get_pull_paths(repo, ui, sources, opts.get(b'branch'))
    for source, branches in sources:
        ui.status(_(b'pulling from %s\n') % urlutil.hidepassword(source))
        ui.flush()
        other = hg.peer(repo, opts, source)
        update_conflict = None
        try:
            revs, checkout = hg.addbranchrevs(
                repo, other, branches, opts.get(b'rev')
            )

            pullopargs = {}

            nodes = None
            if opts.get(b'bookmark') or revs:
                # The list of bookmark used here is the same used to actually update
                # the bookmark names, to avoid the race from issue 4689 and we do
                # all lookup and bookmark queries in one go so they see the same
                # version of the server state (issue 4700).
                nodes = []
                fnodes = []
                revs = revs or []
                if revs and not other.capable(b'lookup'):
                    err = _(
                        b"other repository doesn't support revision lookup, "
                        b"so a rev cannot be specified."
                    )
                    raise error.Abort(err)
                with other.commandexecutor() as e:
                    fremotebookmarks = e.callcommand(
                        b'listkeys', {b'namespace': b'bookmarks'}
                    )
                    for r in revs:
                        fnodes.append(e.callcommand(b'lookup', {b'key': r}))
                remotebookmarks = fremotebookmarks.result()
                remotebookmarks = bookmarks.unhexlifybookmarks(remotebookmarks)
                pullopargs[b'remotebookmarks'] = remotebookmarks
                for b in opts.get(b'bookmark', []):
                    b = repo._bookmarks.expandname(b)
                    if b not in remotebookmarks:
                        raise error.InputError(
                            _(b'remote bookmark %s not found!') % b
                        )
                    nodes.append(remotebookmarks[b])
                for i, rev in enumerate(revs):
                    node = fnodes[i].result()
                    nodes.append(node)
                    if rev == checkout:
                        checkout = node

            wlock = util.nullcontextmanager()
            if opts.get(b'update'):
                wlock = repo.wlock()
            with wlock:
                pullopargs.update(opts.get(b'opargs', {}))
                modheads = exchange.pull(
                    repo,
                    other,
                    heads=nodes,
                    force=opts.get(b'force'),
                    bookmarks=opts.get(b'bookmark', ()),
                    opargs=pullopargs,
                    confirm=opts.get(b'confirm'),
                ).cgresult

                # brev is a name, which might be a bookmark to be activated at
                # the end of the update. In other words, it is an explicit
                # destination of the update
                brev = None

                if checkout:
                    checkout = repo.unfiltered().changelog.rev(checkout)

                    # order below depends on implementation of
                    # hg.addbranchrevs(). opts['bookmark'] is ignored,
                    # because 'checkout' is determined without it.
                    if opts.get(b'rev'):
                        brev = opts[b'rev'][0]
                    elif opts.get(b'branch'):
                        brev = opts[b'branch'][0]
                    else:
                        brev = branches[0]
                repo._subtoppath = source
                try:
                    update_conflict = postincoming(
                        ui, repo, modheads, opts.get(b'update'), checkout, brev
                    )
                except error.FilteredRepoLookupError as exc:
                    msg = _(b'cannot update to target: %s') % exc.args[0]
                    exc.args = (msg,) + exc.args[1:]
                    raise
                finally:
                    del repo._subtoppath

        finally:
            other.close()
        # skip the remaining pull source if they are some conflict.
        if update_conflict:
            break
    if update_conflict:
        return 1
    else:
        return 0


@command(
    b'purge|clean',
    [
        (b'a', b'abort-on-err', None, _(b'abort if an error occurs')),
        (b'', b'all', None, _(b'purge ignored files too')),
        (b'i', b'ignored', None, _(b'purge only ignored files')),
        (b'', b'dirs', None, _(b'purge empty directories')),
        (b'', b'files', None, _(b'purge files')),
        (b'p', b'print', None, _(b'print filenames instead of deleting them')),
        (
            b'0',
            b'print0',
            None,
            _(
                b'end filenames with NUL, for use with xargs'
                b' (implies -p/--print)'
            ),
        ),
        (b'', b'confirm', None, _(b'ask before permanently deleting files')),
    ]
    + cmdutil.walkopts,
    _(b'hg purge [OPTION]... [DIR]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
)
def purge(ui, repo, *dirs, **opts):
    """removes files not tracked by Mercurial

    Delete files not known to Mercurial. This is useful to test local
    and uncommitted changes in an otherwise-clean source tree.

    This means that purge will delete the following by default:

    - Unknown files: files marked with "?" by :hg:`status`
    - Empty directories: in fact Mercurial ignores directories unless
      they contain files under source control management

    But it will leave untouched:

    - Modified and unmodified tracked files
    - Ignored files (unless -i or --all is specified)
    - New files added to the repository (with :hg:`add`)

    The --files and --dirs options can be used to direct purge to delete
    only files, only directories, or both. If neither option is given,
    both will be deleted.

    If directories are given on the command line, only files in these
    directories are considered.

    Be careful with purge, as you could irreversibly delete some files
    you forgot to add to the repository. If you only want to print the
    list of files that this program would delete, use the --print
    option.
    """
    opts = pycompat.byteskwargs(opts)
    cmdutil.check_at_most_one_arg(opts, b'all', b'ignored')

    act = not opts.get(b'print')
    eol = b'\n'
    if opts.get(b'print0'):
        eol = b'\0'
        act = False  # --print0 implies --print
    if opts.get(b'all', False):
        ignored = True
        unknown = True
    else:
        ignored = opts.get(b'ignored', False)
        unknown = not ignored

    removefiles = opts.get(b'files')
    removedirs = opts.get(b'dirs')
    confirm = opts.get(b'confirm')
    if confirm is None:
        try:
            extensions.find(b'purge')
            confirm = False
        except KeyError:
            confirm = True

    if not removefiles and not removedirs:
        removefiles = True
        removedirs = True

    match = scmutil.match(repo[None], dirs, opts)

    paths = mergemod.purge(
        repo,
        match,
        unknown=unknown,
        ignored=ignored,
        removeemptydirs=removedirs,
        removefiles=removefiles,
        abortonerror=opts.get(b'abort_on_err'),
        noop=not act,
        confirm=confirm,
    )

    for path in paths:
        if not act:
            ui.write(b'%s%s' % (path, eol))


@command(
    b'push',
    [
        (b'f', b'force', None, _(b'force push')),
        (
            b'r',
            b'rev',
            [],
            _(b'a changeset intended to be included in the destination'),
            _(b'REV'),
        ),
        (b'B', b'bookmark', [], _(b"bookmark to push"), _(b'BOOKMARK')),
        (b'', b'all-bookmarks', None, _(b"push all bookmarks (EXPERIMENTAL)")),
        (
            b'b',
            b'branch',
            [],
            _(b'a specific branch you would like to push'),
            _(b'BRANCH'),
        ),
        (b'', b'new-branch', False, _(b'allow pushing a new branch')),
        (
            b'',
            b'pushvars',
            [],
            _(b'variables that can be sent to server (ADVANCED)'),
        ),
        (
            b'',
            b'publish',
            False,
            _(b'push the changeset as public (EXPERIMENTAL)'),
        ),
    ]
    + remoteopts,
    _(b'[-f] [-r REV]... [-e CMD] [--remotecmd CMD] [DEST]...'),
    helpcategory=command.CATEGORY_REMOTE_REPO_MANAGEMENT,
    helpbasic=True,
)
def push(ui, repo, *dests, **opts):
    """push changes to the specified destination

    Push changesets from the local repository to the specified
    destination.

    This operation is symmetrical to pull: it is identical to a pull
    in the destination repository from the current one.

    By default, push will not allow creation of new heads at the
    destination, since multiple heads would make it unclear which head
    to use. In this situation, it is recommended to pull and merge
    before pushing.

    Use --new-branch if you want to allow push to create a new named
    branch that is not present at the destination. This allows you to
    only create a new branch without forcing other changes.

    .. note::

       Extra care should be taken with the -f/--force option,
       which will push all new heads on all branches, an action which will
       almost always cause confusion for collaborators.

    If -r/--rev is used, the specified revision and all its ancestors
    will be pushed to the remote repository.

    If -B/--bookmark is used, the specified bookmarked revision, its
    ancestors, and the bookmark will be pushed to the remote
    repository. Specifying ``.`` is equivalent to specifying the active
    bookmark's name. Use the --all-bookmarks option for pushing all
    current bookmarks.

    Please see :hg:`help urls` for important details about ``ssh://``
    URLs. If DESTINATION is omitted, a default path will be used.

    When passed multiple destinations, push will process them one after the
    other, but stop should an error occur.

    .. container:: verbose

        The --pushvars option sends strings to the server that become
        environment variables prepended with ``HG_USERVAR_``. For example,
        ``--pushvars ENABLE_FEATURE=true``, provides the server side hooks with
        ``HG_USERVAR_ENABLE_FEATURE=true`` as part of their environment.

        pushvars can provide for user-overridable hooks as well as set debug
        levels. One example is having a hook that blocks commits containing
        conflict markers, but enables the user to override the hook if the file
        is using conflict markers for testing purposes or the file format has
        strings that look like conflict markers.

        By default, servers will ignore `--pushvars`. To enable it add the
        following to your configuration file::

            [push]
            pushvars.server = true

    Returns 0 if push was successful, 1 if nothing to push.
    """

    opts = pycompat.byteskwargs(opts)

    if opts.get(b'all_bookmarks'):
        cmdutil.check_incompatible_arguments(
            opts,
            b'all_bookmarks',
            [b'bookmark', b'rev'],
        )
        opts[b'bookmark'] = list(repo._bookmarks)

    if opts.get(b'bookmark'):
        ui.setconfig(b'bookmarks', b'pushing', opts[b'bookmark'], b'push')
        for b in opts[b'bookmark']:
            # translate -B options to -r so changesets get pushed
            b = repo._bookmarks.expandname(b)
            if b in repo._bookmarks:
                opts.setdefault(b'rev', []).append(b)
            else:
                # if we try to push a deleted bookmark, translate it to null
                # this lets simultaneous -r, -b options continue working
                opts.setdefault(b'rev', []).append(b"null")

    some_pushed = False
    result = 0
    for path in urlutil.get_push_paths(repo, ui, dests):
        dest = path.pushloc or path.loc
        branches = (path.branch, opts.get(b'branch') or [])
        ui.status(_(b'pushing to %s\n') % urlutil.hidepassword(dest))
        revs, checkout = hg.addbranchrevs(
            repo, repo, branches, opts.get(b'rev')
        )
        other = hg.peer(repo, opts, dest)

        try:
            if revs:
                revs = [repo[r].node() for r in scmutil.revrange(repo, revs)]
                if not revs:
                    raise error.InputError(
                        _(b"specified revisions evaluate to an empty set"),
                        hint=_(b"use different revision arguments"),
                    )
            elif path.pushrev:
                # It doesn't make any sense to specify ancestor revisions. So limit
                # to DAG heads to make discovery simpler.
                expr = revsetlang.formatspec(b'heads(%r)', path.pushrev)
                revs = scmutil.revrange(repo, [expr])
                revs = [repo[rev].node() for rev in revs]
                if not revs:
                    raise error.InputError(
                        _(
                            b'default push revset for path evaluates to an empty set'
                        )
                    )
            elif ui.configbool(b'commands', b'push.require-revs'):
                raise error.InputError(
                    _(b'no revisions specified to push'),
                    hint=_(b'did you mean "hg push -r ."?'),
                )

            repo._subtoppath = dest
            try:
                # push subrepos depth-first for coherent ordering
                c = repo[b'.']
                subs = c.substate  # only repos that are committed
                for s in sorted(subs):
                    sub_result = c.sub(s).push(opts)
                    if sub_result == 0:
                        return 1
            finally:
                del repo._subtoppath

            opargs = dict(
                opts.get(b'opargs', {})
            )  # copy opargs since we may mutate it
            opargs.setdefault(b'pushvars', []).extend(opts.get(b'pushvars', []))

            pushop = exchange.push(
                repo,
                other,
                opts.get(b'force'),
                revs=revs,
                newbranch=opts.get(b'new_branch'),
                bookmarks=opts.get(b'bookmark', ()),
                publish=opts.get(b'publish'),
                opargs=opargs,
            )

            if pushop.cgresult == 0:
                result = 1
            elif pushop.cgresult is not None:
                some_pushed = True

            if pushop.bkresult is not None:
                if pushop.bkresult == 2:
                    result = 2
                elif not result and pushop.bkresult:
                    result = 2

            if result:
                break

        finally:
            other.close()
    if result == 0 and not some_pushed:
        result = 1
    return result


@command(
    b'recover',
    [
        (b'', b'verify', False, b"run `hg verify` after successful recover"),
    ],
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def recover(ui, repo, **opts):
    """roll back an interrupted transaction

    Recover from an interrupted commit or pull.

    This command tries to fix the repository status after an
    interrupted operation. It should only be necessary when Mercurial
    suggests it.

    Returns 0 if successful, 1 if nothing to recover or verify fails.
    """
    ret = repo.recover()
    if ret:
        if opts['verify']:
            return hg.verify(repo)
        else:
            msg = _(
                b"(verify step skipped, run `hg verify` to check your "
                b"repository content)\n"
            )
            ui.warn(msg)
            return 0
    return 1


@command(
    b'remove|rm',
    [
        (b'A', b'after', None, _(b'record delete for missing files')),
        (b'f', b'force', None, _(b'forget added files, delete modified files')),
    ]
    + subrepoopts
    + walkopts
    + dryrunopts,
    _(b'[OPTION]... FILE...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    helpbasic=True,
    inferrepo=True,
)
def remove(ui, repo, *pats, **opts):
    """remove the specified files on the next commit

    Schedule the indicated files for removal from the current branch.

    This command schedules the files to be removed at the next commit.
    To undo a remove before that, see :hg:`revert`. To undo added
    files, see :hg:`forget`.

    .. container:: verbose

      -A/--after can be used to remove only files that have already
      been deleted, -f/--force can be used to force deletion, and -Af
      can be used to remove files from the next revision without
      deleting them from the working directory.

      The following table details the behavior of remove for different
      file states (columns) and option combinations (rows). The file
      states are Added [A], Clean [C], Modified [M] and Missing [!]
      (as reported by :hg:`status`). The actions are Warn, Remove
      (from branch) and Delete (from disk):

      ========= == == == ==
      opt/state A  C  M  !
      ========= == == == ==
      none      W  RD W  R
      -f        R  RD RD R
      -A        W  W  W  R
      -Af       R  R  R  R
      ========= == == == ==

      .. note::

         :hg:`remove` never deletes files in Added [A] state from the
         working directory, not even if ``--force`` is specified.

    Returns 0 on success, 1 if any warnings encountered.
    """

    opts = pycompat.byteskwargs(opts)
    after, force = opts.get(b'after'), opts.get(b'force')
    dryrun = opts.get(b'dry_run')
    if not pats and not after:
        raise error.InputError(_(b'no files specified'))

    m = scmutil.match(repo[None], pats, opts)
    subrepos = opts.get(b'subrepos')
    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)
    return cmdutil.remove(
        ui, repo, m, b"", uipathfn, after, force, subrepos, dryrun=dryrun
    )


@command(
    b'rename|move|mv',
    [
        (b'', b'forget', None, _(b'unmark a destination file as renamed')),
        (b'A', b'after', None, _(b'record a rename that has already occurred')),
        (
            b'',
            b'at-rev',
            b'',
            _(b'(un)mark renames in the given revision (EXPERIMENTAL)'),
            _(b'REV'),
        ),
        (
            b'f',
            b'force',
            None,
            _(b'forcibly move over an existing managed file'),
        ),
    ]
    + walkopts
    + dryrunopts,
    _(b'[OPTION]... SOURCE... DEST'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
)
def rename(ui, repo, *pats, **opts):
    """rename files; equivalent of copy + remove

    Mark dest as copies of sources; mark sources for deletion. If dest
    is a directory, copies are put in that directory. If dest is a
    file, there can only be one source.

    By default, this command copies the contents of files as they
    exist in the working directory. If invoked with -A/--after, the
    operation is recorded, but no copying is performed.

    To undo marking a destination file as renamed, use --forget. With that
    option, all given (positional) arguments are unmarked as renames. The
    destination file(s) will be left in place (still tracked). The source
    file(s) will not be restored. Note that :hg:`rename --forget` behaves
    the same way as :hg:`copy --forget`.

    This command takes effect with the next commit by default.

    Returns 0 on success, 1 if errors are encountered.
    """
    opts = pycompat.byteskwargs(opts)
    with repo.wlock():
        return cmdutil.copy(ui, repo, pats, opts, rename=True)


@command(
    b'resolve',
    [
        (b'a', b'all', None, _(b'select all unresolved files')),
        (b'l', b'list', None, _(b'list state of files needing merge')),
        (b'm', b'mark', None, _(b'mark files as resolved')),
        (b'u', b'unmark', None, _(b'mark files as unresolved')),
        (b'n', b'no-status', None, _(b'hide status prefix')),
        (b'', b're-merge', None, _(b're-merge files')),
    ]
    + mergetoolopts
    + walkopts
    + formatteropts,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    inferrepo=True,
)
def resolve(ui, repo, *pats, **opts):
    """redo merges or set/view the merge status of files

    Merges with unresolved conflicts are often the result of
    non-interactive merging using the ``internal:merge`` configuration
    setting, or a command-line merge tool like ``diff3``. The resolve
    command is used to manage the files involved in a merge, after
    :hg:`merge` has been run, and before :hg:`commit` is run (i.e. the
    working directory must have two parents). See :hg:`help
    merge-tools` for information on configuring merge tools.

    The resolve command can be used in the following ways:

    - :hg:`resolve [--re-merge] [--tool TOOL] FILE...`: attempt to re-merge
      the specified files, discarding any previous merge attempts. Re-merging
      is not performed for files already marked as resolved. Use ``--all/-a``
      to select all unresolved files. ``--tool`` can be used to specify
      the merge tool used for the given files. It overrides the HGMERGE
      environment variable and your configuration files.  Previous file
      contents are saved with a ``.orig`` suffix.

    - :hg:`resolve -m [FILE]`: mark a file as having been resolved
      (e.g. after having manually fixed-up the files). The default is
      to mark all unresolved files.

    - :hg:`resolve -u [FILE]...`: mark a file as unresolved. The
      default is to mark all resolved files.

    - :hg:`resolve -l`: list files which had or still have conflicts.
      In the printed list, ``U`` = unresolved and ``R`` = resolved.
      You can use ``set:unresolved()`` or ``set:resolved()`` to filter
      the list. See :hg:`help filesets` for details.

    .. note::

       Mercurial will not let you commit files with unresolved merge
       conflicts. You must use :hg:`resolve -m ...` before you can
       commit after a conflicting merge.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :mergestatus: String. Character denoting merge conflicts, ``U`` or ``R``.
      :path:    String. Repository-absolute path of the file.

    Returns 0 on success, 1 if any files fail a resolve attempt.
    """

    opts = pycompat.byteskwargs(opts)
    confirm = ui.configbool(b'commands', b'resolve.confirm')
    flaglist = b'all mark unmark list no_status re_merge'.split()
    all, mark, unmark, show, nostatus, remerge = [opts.get(o) for o in flaglist]

    actioncount = len(list(filter(None, [show, mark, unmark, remerge])))
    if actioncount > 1:
        raise error.InputError(_(b"too many actions specified"))
    elif actioncount == 0 and ui.configbool(
        b'commands', b'resolve.explicit-re-merge'
    ):
        hint = _(b'use --mark, --unmark, --list or --re-merge')
        raise error.InputError(_(b'no action specified'), hint=hint)
    if pats and all:
        raise error.InputError(_(b"can't specify --all and patterns"))
    if not (all or pats or show or mark or unmark):
        raise error.InputError(
            _(b'no files or directories specified'),
            hint=b'use --all to re-merge all unresolved files',
        )

    if confirm:
        if all:
            if ui.promptchoice(
                _(b're-merge all unresolved files (yn)?$$ &Yes $$ &No')
            ):
                raise error.CanceledError(_(b'user quit'))
        if mark and not pats:
            if ui.promptchoice(
                _(
                    b'mark all unresolved files as resolved (yn)?'
                    b'$$ &Yes $$ &No'
                )
            ):
                raise error.CanceledError(_(b'user quit'))
        if unmark and not pats:
            if ui.promptchoice(
                _(
                    b'mark all resolved files as unresolved (yn)?'
                    b'$$ &Yes $$ &No'
                )
            ):
                raise error.CanceledError(_(b'user quit'))

    uipathfn = scmutil.getuipathfn(repo)

    if show:
        ui.pager(b'resolve')
        fm = ui.formatter(b'resolve', opts)
        ms = mergestatemod.mergestate.read(repo)
        wctx = repo[None]
        m = scmutil.match(wctx, pats, opts)

        # Labels and keys based on merge state.  Unresolved path conflicts show
        # as 'P'.  Resolved path conflicts show as 'R', the same as normal
        # resolved conflicts.
        mergestateinfo = {
            mergestatemod.MERGE_RECORD_UNRESOLVED: (
                b'resolve.unresolved',
                b'U',
            ),
            mergestatemod.MERGE_RECORD_RESOLVED: (b'resolve.resolved', b'R'),
            mergestatemod.MERGE_RECORD_UNRESOLVED_PATH: (
                b'resolve.unresolved',
                b'P',
            ),
            mergestatemod.MERGE_RECORD_RESOLVED_PATH: (
                b'resolve.resolved',
                b'R',
            ),
        }

        for f in ms:
            if not m(f):
                continue

            label, key = mergestateinfo[ms[f]]
            fm.startitem()
            fm.context(ctx=wctx)
            fm.condwrite(not nostatus, b'mergestatus', b'%s ', key, label=label)
            fm.data(path=f)
            fm.plain(b'%s\n' % uipathfn(f), label=label)
        fm.end()
        return 0

    with repo.wlock():
        ms = mergestatemod.mergestate.read(repo)

        if not (ms.active() or repo.dirstate.p2() != repo.nullid):
            raise error.StateError(
                _(b'resolve command not applicable when not merging')
            )

        wctx = repo[None]
        m = scmutil.match(wctx, pats, opts)
        ret = 0
        didwork = False

        tocomplete = []
        hasconflictmarkers = []
        if mark:
            markcheck = ui.config(b'commands', b'resolve.mark-check')
            if markcheck not in [b'warn', b'abort']:
                # Treat all invalid / unrecognized values as 'none'.
                markcheck = False
        for f in ms:
            if not m(f):
                continue

            didwork = True

            # path conflicts must be resolved manually
            if ms[f] in (
                mergestatemod.MERGE_RECORD_UNRESOLVED_PATH,
                mergestatemod.MERGE_RECORD_RESOLVED_PATH,
            ):
                if mark:
                    ms.mark(f, mergestatemod.MERGE_RECORD_RESOLVED_PATH)
                elif unmark:
                    ms.mark(f, mergestatemod.MERGE_RECORD_UNRESOLVED_PATH)
                elif ms[f] == mergestatemod.MERGE_RECORD_UNRESOLVED_PATH:
                    ui.warn(
                        _(b'%s: path conflict must be resolved manually\n')
                        % uipathfn(f)
                    )
                continue

            if mark:
                if markcheck:
                    fdata = repo.wvfs.tryread(f)
                    if (
                        filemerge.hasconflictmarkers(fdata)
                        and ms[f] != mergestatemod.MERGE_RECORD_RESOLVED
                    ):
                        hasconflictmarkers.append(f)
                ms.mark(f, mergestatemod.MERGE_RECORD_RESOLVED)
            elif unmark:
                ms.mark(f, mergestatemod.MERGE_RECORD_UNRESOLVED)
            else:
                # backup pre-resolve (merge uses .orig for its own purposes)
                a = repo.wjoin(f)
                try:
                    util.copyfile(a, a + b".resolve")
                except (IOError, OSError) as inst:
                    if inst.errno != errno.ENOENT:
                        raise

                try:
                    # preresolve file
                    overrides = {(b'ui', b'forcemerge'): opts.get(b'tool', b'')}
                    with ui.configoverride(overrides, b'resolve'):
                        complete, r = ms.preresolve(f, wctx)
                    if not complete:
                        tocomplete.append(f)
                    elif r:
                        ret = 1
                finally:
                    ms.commit()

                # replace filemerge's .orig file with our resolve file, but only
                # for merges that are complete
                if complete:
                    try:
                        util.rename(
                            a + b".resolve", scmutil.backuppath(ui, repo, f)
                        )
                    except OSError as inst:
                        if inst.errno != errno.ENOENT:
                            raise

        if hasconflictmarkers:
            ui.warn(
                _(
                    b'warning: the following files still have conflict '
                    b'markers:\n'
                )
                + b''.join(
                    b'  ' + uipathfn(f) + b'\n' for f in hasconflictmarkers
                )
            )
            if markcheck == b'abort' and not all and not pats:
                raise error.StateError(
                    _(b'conflict markers detected'),
                    hint=_(b'use --all to mark anyway'),
                )

        for f in tocomplete:
            try:
                # resolve file
                overrides = {(b'ui', b'forcemerge'): opts.get(b'tool', b'')}
                with ui.configoverride(overrides, b'resolve'):
                    r = ms.resolve(f, wctx)
                if r:
                    ret = 1
            finally:
                ms.commit()

            # replace filemerge's .orig file with our resolve file
            a = repo.wjoin(f)
            try:
                util.rename(a + b".resolve", scmutil.backuppath(ui, repo, f))
            except OSError as inst:
                if inst.errno != errno.ENOENT:
                    raise

        ms.commit()
        branchmerge = repo.dirstate.p2() != repo.nullid
        # resolve is not doing a parent change here, however, `record updates`
        # will call some dirstate API that at intended for parent changes call.
        # Ideally we would not need this and could implement a lighter version
        # of the recordupdateslogic that will not have to deal with the part
        # related to parent changes. However this would requires that:
        # - we are sure we passed around enough information at update/merge
        #   time to no longer needs it at `hg resolve time`
        # - we are sure we store that information well enough to be able to reuse it
        # - we are the necessary logic to reuse it right.
        #
        # All this should eventually happens, but in the mean time, we use this
        # context manager slightly out of the context it should be.
        with repo.dirstate.parentchange():
            mergestatemod.recordupdates(repo, ms.actions(), branchmerge, None)

        if not didwork and pats:
            hint = None
            if not any([p for p in pats if p.find(b':') >= 0]):
                pats = [b'path:%s' % p for p in pats]
                m = scmutil.match(wctx, pats, opts)
                for f in ms:
                    if not m(f):
                        continue

                    def flag(o):
                        if o == b're_merge':
                            return b'--re-merge '
                        return b'-%s ' % o[0:1]

                    flags = b''.join([flag(o) for o in flaglist if opts.get(o)])
                    hint = _(b"(try: hg resolve %s%s)\n") % (
                        flags,
                        b' '.join(pats),
                    )
                    break
            ui.warn(_(b"arguments do not match paths that need resolving\n"))
            if hint:
                ui.warn(hint)

    unresolvedf = ms.unresolvedcount()
    if not unresolvedf:
        ui.status(_(b'(no more unresolved files)\n'))
        cmdutil.checkafterresolved(repo)

    return ret


@command(
    b'revert',
    [
        (b'a', b'all', None, _(b'revert all changes when no arguments given')),
        (b'd', b'date', b'', _(b'tipmost revision matching date'), _(b'DATE')),
        (b'r', b'rev', b'', _(b'revert to the specified revision'), _(b'REV')),
        (b'C', b'no-backup', None, _(b'do not save backup copies of files')),
        (b'i', b'interactive', None, _(b'interactively select the changes')),
    ]
    + walkopts
    + dryrunopts,
    _(b'[OPTION]... [-r REV] [NAME]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
)
def revert(ui, repo, *pats, **opts):
    """restore files to their checkout state

    .. note::

       To check out earlier revisions, you should use :hg:`update REV`.
       To cancel an uncommitted merge (and lose your changes),
       use :hg:`merge --abort`.

    With no revision specified, revert the specified files or directories
    to the contents they had in the parent of the working directory.
    This restores the contents of files to an unmodified
    state and unschedules adds, removes, copies, and renames. If the
    working directory has two parents, you must explicitly specify a
    revision.

    Using the -r/--rev or -d/--date options, revert the given files or
    directories to their states as of a specific revision. Because
    revert does not change the working directory parents, this will
    cause these files to appear modified. This can be helpful to "back
    out" some or all of an earlier change. See :hg:`backout` for a
    related method.

    Modified files are saved with a .orig suffix before reverting.
    To disable these backups, use --no-backup. It is possible to store
    the backup files in a custom directory relative to the root of the
    repository by setting the ``ui.origbackuppath`` configuration
    option.

    See :hg:`help dates` for a list of formats valid for -d/--date.

    See :hg:`help backout` for a way to reverse the effect of an
    earlier changeset.

    Returns 0 on success.
    """

    opts = pycompat.byteskwargs(opts)
    if opts.get(b"date"):
        cmdutil.check_incompatible_arguments(opts, b'date', [b'rev'])
        opts[b"rev"] = cmdutil.finddate(ui, repo, opts[b"date"])

    parent, p2 = repo.dirstate.parents()
    if not opts.get(b'rev') and p2 != repo.nullid:
        # revert after merge is a trap for new users (issue2915)
        raise error.InputError(
            _(b'uncommitted merge with no revision specified'),
            hint=_(b"use 'hg update' or see 'hg help revert'"),
        )

    rev = opts.get(b'rev')
    if rev:
        repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
    ctx = scmutil.revsingle(repo, rev)

    if not (
        pats
        or opts.get(b'include')
        or opts.get(b'exclude')
        or opts.get(b'all')
        or opts.get(b'interactive')
    ):
        msg = _(b"no files or directories specified")
        if p2 != repo.nullid:
            hint = _(
                b"uncommitted merge, use --all to discard all changes,"
                b" or 'hg update -C .' to abort the merge"
            )
            raise error.InputError(msg, hint=hint)
        dirty = any(repo.status())
        node = ctx.node()
        if node != parent:
            if dirty:
                hint = (
                    _(
                        b"uncommitted changes, use --all to discard all"
                        b" changes, or 'hg update %d' to update"
                    )
                    % ctx.rev()
                )
            else:
                hint = (
                    _(
                        b"use --all to revert all files,"
                        b" or 'hg update %d' to update"
                    )
                    % ctx.rev()
                )
        elif dirty:
            hint = _(b"uncommitted changes, use --all to discard all changes")
        else:
            hint = _(b"use --all to revert all files")
        raise error.InputError(msg, hint=hint)

    return cmdutil.revert(ui, repo, ctx, *pats, **pycompat.strkwargs(opts))


@command(
    b'rollback',
    dryrunopts + [(b'f', b'force', False, _(b'ignore safety measures'))],
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def rollback(ui, repo, **opts):
    """roll back the last transaction (DANGEROUS) (DEPRECATED)

    Please use :hg:`commit --amend` instead of rollback to correct
    mistakes in the last commit.

    This command should be used with care. There is only one level of
    rollback, and there is no way to undo a rollback. It will also
    restore the dirstate at the time of the last transaction, losing
    any dirstate changes since that time. This command does not alter
    the working directory.

    Transactions are used to encapsulate the effects of all commands
    that create new changesets or propagate existing changesets into a
    repository.

    .. container:: verbose

      For example, the following commands are transactional, and their
      effects can be rolled back:

      - commit
      - import
      - pull
      - push (with this repository as the destination)
      - unbundle

      To avoid permanent data loss, rollback will refuse to rollback a
      commit transaction if it isn't checked out. Use --force to
      override this protection.

      The rollback command can be entirely disabled by setting the
      ``ui.rollback`` configuration setting to false. If you're here
      because you want to use rollback and it's disabled, you can
      re-enable the command by setting ``ui.rollback`` to true.

    This command is not intended for use on public repositories. Once
    changes are visible for pull by other users, rolling a transaction
    back locally is ineffective (someone else may already have pulled
    the changes). Furthermore, a race is possible with readers of the
    repository; for example an in-progress pull from the repository
    may fail if a rollback is performed.

    Returns 0 on success, 1 if no rollback data is available.
    """
    if not ui.configbool(b'ui', b'rollback'):
        raise error.Abort(
            _(b'rollback is disabled because it is unsafe'),
            hint=b'see `hg help -v rollback` for information',
        )
    return repo.rollback(dryrun=opts.get('dry_run'), force=opts.get('force'))


@command(
    b'root',
    [] + formatteropts,
    intents={INTENT_READONLY},
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
)
def root(ui, repo, **opts):
    """print the root (top) of the current working directory

    Print the root directory of the current repository.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :hgpath:    String. Path to the .hg directory.
      :storepath: String. Path to the directory holding versioned data.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    with ui.formatter(b'root', opts) as fm:
        fm.startitem()
        fm.write(b'reporoot', b'%s\n', repo.root)
        fm.data(hgpath=repo.path, storepath=repo.spath)


@command(
    b'serve',
    [
        (
            b'A',
            b'accesslog',
            b'',
            _(b'name of access log file to write to'),
            _(b'FILE'),
        ),
        (b'd', b'daemon', None, _(b'run server in background')),
        (b'', b'daemon-postexec', [], _(b'used internally by daemon mode')),
        (
            b'E',
            b'errorlog',
            b'',
            _(b'name of error log file to write to'),
            _(b'FILE'),
        ),
        # use string type, then we can check if something was passed
        (
            b'p',
            b'port',
            b'',
            _(b'port to listen on (default: 8000)'),
            _(b'PORT'),
        ),
        (
            b'a',
            b'address',
            b'',
            _(b'address to listen on (default: all interfaces)'),
            _(b'ADDR'),
        ),
        (
            b'',
            b'prefix',
            b'',
            _(b'prefix path to serve from (default: server root)'),
            _(b'PREFIX'),
        ),
        (
            b'n',
            b'name',
            b'',
            _(b'name to show in web pages (default: working directory)'),
            _(b'NAME'),
        ),
        (
            b'',
            b'web-conf',
            b'',
            _(b"name of the hgweb config file (see 'hg help hgweb')"),
            _(b'FILE'),
        ),
        (
            b'',
            b'webdir-conf',
            b'',
            _(b'name of the hgweb config file (DEPRECATED)'),
            _(b'FILE'),
        ),
        (
            b'',
            b'pid-file',
            b'',
            _(b'name of file to write process ID to'),
            _(b'FILE'),
        ),
        (b'', b'stdio', None, _(b'for remote clients (ADVANCED)')),
        (
            b'',
            b'cmdserver',
            b'',
            _(b'for remote clients (ADVANCED)'),
            _(b'MODE'),
        ),
        (b't', b'templates', b'', _(b'web templates to use'), _(b'TEMPLATE')),
        (b'', b'style', b'', _(b'template style to use'), _(b'STYLE')),
        (b'6', b'ipv6', None, _(b'use IPv6 in addition to IPv4')),
        (b'', b'certificate', b'', _(b'SSL certificate file'), _(b'FILE')),
        (b'', b'print-url', None, _(b'start and print only the URL')),
    ]
    + subrepoopts,
    _(b'[OPTION]...'),
    helpcategory=command.CATEGORY_REMOTE_REPO_MANAGEMENT,
    helpbasic=True,
    optionalrepo=True,
)
def serve(ui, repo, **opts):
    """start stand-alone webserver

    Start a local HTTP repository browser and pull server. You can use
    this for ad-hoc sharing and browsing of repositories. It is
    recommended to use a real web server to serve a repository for
    longer periods of time.

    Please note that the server does not implement access control.
    This means that, by default, anybody can read from the server and
    nobody can write to it by default. Set the ``web.allow-push``
    option to ``*`` to allow everybody to push to the server. You
    should use a real web server if you need to authenticate users.

    By default, the server logs accesses to stdout and errors to
    stderr. Use the -A/--accesslog and -E/--errorlog options to log to
    files.

    To have the server choose a free port number to listen on, specify
    a port number of 0; in this case, the server will print the port
    number it uses.

    Returns 0 on success.
    """

    cmdutil.check_incompatible_arguments(opts, 'stdio', ['cmdserver'])
    opts = pycompat.byteskwargs(opts)
    if opts[b"print_url"] and ui.verbose:
        raise error.InputError(_(b"cannot use --print-url with --verbose"))

    if opts[b"stdio"]:
        if repo is None:
            raise error.RepoError(
                _(b"there is no Mercurial repository here (.hg not found)")
            )
        s = wireprotoserver.sshserver(ui, repo)
        s.serve_forever()
        return

    service = server.createservice(ui, repo, opts)
    return server.runservice(opts, initfn=service.init, runfn=service.run)


@command(
    b'shelve',
    [
        (
            b'A',
            b'addremove',
            None,
            _(b'mark new/missing files as added/removed before shelving'),
        ),
        (b'u', b'unknown', None, _(b'store unknown files in the shelve')),
        (b'', b'cleanup', None, _(b'delete all shelved changes')),
        (
            b'',
            b'date',
            b'',
            _(b'shelve with the specified commit date'),
            _(b'DATE'),
        ),
        (b'd', b'delete', None, _(b'delete the named shelved change(s)')),
        (b'e', b'edit', False, _(b'invoke editor on commit messages')),
        (
            b'k',
            b'keep',
            False,
            _(b'shelve, but keep changes in the working directory'),
        ),
        (b'l', b'list', None, _(b'list current shelves')),
        (b'm', b'message', b'', _(b'use text as shelve message'), _(b'TEXT')),
        (
            b'n',
            b'name',
            b'',
            _(b'use the given name for the shelved commit'),
            _(b'NAME'),
        ),
        (
            b'p',
            b'patch',
            None,
            _(
                b'output patches for changes (provide the names of the shelved '
                b'changes as positional arguments)'
            ),
        ),
        (b'i', b'interactive', None, _(b'interactive mode')),
        (
            b'',
            b'stat',
            None,
            _(
                b'output diffstat-style summary of changes (provide the names of '
                b'the shelved changes as positional arguments)'
            ),
        ),
    ]
    + cmdutil.walkopts,
    _(b'hg shelve [OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
)
def shelve(ui, repo, *pats, **opts):
    """save and set aside changes from the working directory

    Shelving takes files that "hg status" reports as not clean, saves
    the modifications to a bundle (a shelved change), and reverts the
    files so that their state in the working directory becomes clean.

    To restore these changes to the working directory, using "hg
    unshelve"; this will work even if you switch to a different
    commit.

    When no files are specified, "hg shelve" saves all not-clean
    files. If specific files or directories are named, only changes to
    those files are shelved.

    In bare shelve (when no files are specified, without interactive,
    include and exclude option), shelving remembers information if the
    working directory was on newly created branch, in other words working
    directory was on different branch than its first parent. In this
    situation unshelving restores branch information to the working directory.

    Each shelved change has a name that makes it easier to find later.
    The name of a shelved change defaults to being based on the active
    bookmark, or if there is no active bookmark, the current named
    branch.  To specify a different name, use ``--name``.

    To see a list of existing shelved changes, use the ``--list``
    option. For each shelved change, this will print its name, age,
    and description; use ``--patch`` or ``--stat`` for more details.

    To delete specific shelved changes, use ``--delete``. To delete
    all shelved changes, use ``--cleanup``.
    """
    opts = pycompat.byteskwargs(opts)
    allowables = [
        (b'addremove', {b'create'}),  # 'create' is pseudo action
        (b'unknown', {b'create'}),
        (b'cleanup', {b'cleanup'}),
        #       ('date', {'create'}), # ignored for passing '--date "0 0"' in tests
        (b'delete', {b'delete'}),
        (b'edit', {b'create'}),
        (b'keep', {b'create'}),
        (b'list', {b'list'}),
        (b'message', {b'create'}),
        (b'name', {b'create'}),
        (b'patch', {b'patch', b'list'}),
        (b'stat', {b'stat', b'list'}),
    ]

    def checkopt(opt):
        if opts.get(opt):
            for i, allowable in allowables:
                if opts[i] and opt not in allowable:
                    raise error.InputError(
                        _(
                            b"options '--%s' and '--%s' may not be "
                            b"used together"
                        )
                        % (opt, i)
                    )
            return True

    if checkopt(b'cleanup'):
        if pats:
            raise error.InputError(
                _(b"cannot specify names when using '--cleanup'")
            )
        return shelvemod.cleanupcmd(ui, repo)
    elif checkopt(b'delete'):
        return shelvemod.deletecmd(ui, repo, pats)
    elif checkopt(b'list'):
        return shelvemod.listcmd(ui, repo, pats, opts)
    elif checkopt(b'patch') or checkopt(b'stat'):
        return shelvemod.patchcmds(ui, repo, pats, opts)
    else:
        return shelvemod.createcmd(ui, repo, pats, opts)


_NOTTERSE = b'nothing'


@command(
    b'status|st',
    [
        (b'A', b'all', None, _(b'show status of all files')),
        (b'm', b'modified', None, _(b'show only modified files')),
        (b'a', b'added', None, _(b'show only added files')),
        (b'r', b'removed', None, _(b'show only removed files')),
        (b'd', b'deleted', None, _(b'show only missing files')),
        (b'c', b'clean', None, _(b'show only files without changes')),
        (b'u', b'unknown', None, _(b'show only unknown (not tracked) files')),
        (b'i', b'ignored', None, _(b'show only ignored files')),
        (b'n', b'no-status', None, _(b'hide status prefix')),
        (b't', b'terse', _NOTTERSE, _(b'show the terse output (EXPERIMENTAL)')),
        (
            b'C',
            b'copies',
            None,
            _(b'show source of copied files (DEFAULT: ui.statuscopies)'),
        ),
        (
            b'0',
            b'print0',
            None,
            _(b'end filenames with NUL, for use with xargs'),
        ),
        (b'', b'rev', [], _(b'show difference from revision'), _(b'REV')),
        (
            b'',
            b'change',
            b'',
            _(b'list the changed files of a revision'),
            _(b'REV'),
        ),
    ]
    + walkopts
    + subrepoopts
    + formatteropts,
    _(b'[OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    helpbasic=True,
    inferrepo=True,
    intents={INTENT_READONLY},
)
def status(ui, repo, *pats, **opts):
    """show changed files in the working directory

    Show status of files in the repository. If names are given, only
    files that match are shown. Files that are clean or ignored or
    the source of a copy/move operation, are not listed unless
    -c/--clean, -i/--ignored, -C/--copies or -A/--all are given.
    Unless options described with "show only ..." are given, the
    options -mardu are used.

    Option -q/--quiet hides untracked (unknown and ignored) files
    unless explicitly requested with -u/--unknown or -i/--ignored.

    .. note::

       :hg:`status` may appear to disagree with diff if permissions have
       changed or a merge has occurred. The standard diff format does
       not report permission changes and diff only reports changes
       relative to one merge parent.

    If one revision is given, it is used as the base revision.
    If two revisions are given, the differences between them are
    shown. The --change option can also be used as a shortcut to list
    the changed files of a revision from its first parent.

    The codes used to show the status of files are::

      M = modified
      A = added
      R = removed
      C = clean
      ! = missing (deleted by non-hg command, but still tracked)
      ? = not tracked
      I = ignored
        = origin of the previous file (with --copies)

    .. container:: verbose

      The -t/--terse option abbreviates the output by showing only the directory
      name if all the files in it share the same status. The option takes an
      argument indicating the statuses to abbreviate: 'm' for 'modified', 'a'
      for 'added', 'r' for 'removed', 'd' for 'deleted', 'u' for 'unknown', 'i'
      for 'ignored' and 'c' for clean.

      It abbreviates only those statuses which are passed. Note that clean and
      ignored files are not displayed with '--terse ic' unless the -c/--clean
      and -i/--ignored options are also used.

      The -v/--verbose option shows information when the repository is in an
      unfinished merge, shelve, rebase state etc. You can have this behavior
      turned on by default by enabling the ``commands.status.verbose`` option.

      You can skip displaying some of these states by setting
      ``commands.status.skipstates`` to one or more of: 'bisect', 'graft',
      'histedit', 'merge', 'rebase', or 'unshelve'.

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions. See also :hg:`help templates`.

      :path:    String. Repository-absolute path of the file.
      :source:  String. Repository-absolute path of the file originated from.
                Available if ``--copies`` is specified.
      :status:  String. Character denoting file's status.

      Examples:

      - show changes in the working directory relative to a
        changeset::

          hg status --rev 9353

      - show changes in the working directory relative to the
        current directory (see :hg:`help patterns` for more information)::

          hg status re:

      - show all changes including copies in an existing changeset::

          hg status --copies --change 9353

      - get a NUL separated list of added files, suitable for xargs::

          hg status -an0

      - show more information about the repository status, abbreviating
        added, removed, modified, deleted, and untracked paths::

          hg status -v -t mardu

    Returns 0 on success.

    """

    cmdutil.check_at_most_one_arg(opts, 'rev', 'change')
    opts = pycompat.byteskwargs(opts)
    revs = opts.get(b'rev')
    change = opts.get(b'change')
    terse = opts.get(b'terse')
    if terse is _NOTTERSE:
        if revs:
            terse = b''
        else:
            terse = ui.config(b'commands', b'status.terse')

    if revs and terse:
        msg = _(b'cannot use --terse with --rev')
        raise error.InputError(msg)
    elif change:
        repo = scmutil.unhidehashlikerevs(repo, [change], b'nowarn')
        ctx2 = scmutil.revsingle(repo, change, None)
        ctx1 = ctx2.p1()
    else:
        repo = scmutil.unhidehashlikerevs(repo, revs, b'nowarn')
        ctx1, ctx2 = scmutil.revpair(repo, revs)

    forcerelativevalue = None
    if ui.hasconfig(b'commands', b'status.relative'):
        forcerelativevalue = ui.configbool(b'commands', b'status.relative')
    uipathfn = scmutil.getuipathfn(
        repo,
        legacyrelativevalue=bool(pats),
        forcerelativevalue=forcerelativevalue,
    )

    if opts.get(b'print0'):
        end = b'\0'
    else:
        end = b'\n'
    states = b'modified added removed deleted unknown ignored clean'.split()
    show = [k for k in states if opts.get(k)]
    if opts.get(b'all'):
        show += ui.quiet and (states[:4] + [b'clean']) or states

    if not show:
        if ui.quiet:
            show = states[:4]
        else:
            show = states[:5]

    m = scmutil.match(ctx2, pats, opts)
    if terse:
        # we need to compute clean and unknown to terse
        stat = repo.status(
            ctx1.node(),
            ctx2.node(),
            m,
            b'ignored' in show or b'i' in terse,
            clean=True,
            unknown=True,
            listsubrepos=opts.get(b'subrepos'),
        )

        stat = cmdutil.tersedir(stat, terse)
    else:
        stat = repo.status(
            ctx1.node(),
            ctx2.node(),
            m,
            b'ignored' in show,
            b'clean' in show,
            b'unknown' in show,
            opts.get(b'subrepos'),
        )

    changestates = zip(
        states,
        pycompat.iterbytestr(b'MAR!?IC'),
        [getattr(stat, s.decode('utf8')) for s in states],
    )

    copy = {}
    if (
        opts.get(b'all')
        or opts.get(b'copies')
        or ui.configbool(b'ui', b'statuscopies')
    ) and not opts.get(b'no_status'):
        copy = copies.pathcopies(ctx1, ctx2, m)

    morestatus = None
    if (
        (ui.verbose or ui.configbool(b'commands', b'status.verbose'))
        and not ui.plain()
        and not opts.get(b'print0')
    ):
        morestatus = cmdutil.readmorestatus(repo)

    ui.pager(b'status')
    fm = ui.formatter(b'status', opts)
    fmt = b'%s' + end
    showchar = not opts.get(b'no_status')

    for state, char, files in changestates:
        if state in show:
            label = b'status.' + state
            for f in files:
                fm.startitem()
                fm.context(ctx=ctx2)
                fm.data(itemtype=b'file', path=f)
                fm.condwrite(showchar, b'status', b'%s ', char, label=label)
                fm.plain(fmt % uipathfn(f), label=label)
                if f in copy:
                    fm.data(source=copy[f])
                    fm.plain(
                        (b'  %s' + end) % uipathfn(copy[f]),
                        label=b'status.copied',
                    )
                if morestatus:
                    morestatus.formatfile(f, fm)

    if morestatus:
        morestatus.formatfooter(fm)
    fm.end()


@command(
    b'summary|sum',
    [(b'', b'remote', None, _(b'check for push and pull'))],
    b'[--remote]',
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    helpbasic=True,
    intents={INTENT_READONLY},
)
def summary(ui, repo, **opts):
    """summarize working directory state

    This generates a brief summary of the working directory state,
    including parents, branch, commit status, phase and available updates.

    With the --remote option, this will check the default paths for
    incoming and outgoing changes. This can be time-consuming.

    Returns 0 on success.
    """

    opts = pycompat.byteskwargs(opts)
    ui.pager(b'summary')
    ctx = repo[None]
    parents = ctx.parents()
    pnode = parents[0].node()
    marks = []

    try:
        ms = mergestatemod.mergestate.read(repo)
    except error.UnsupportedMergeRecords as e:
        s = b' '.join(e.recordtypes)
        ui.warn(
            _(b'warning: merge state has unsupported record types: %s\n') % s
        )
        unresolved = []
    else:
        unresolved = list(ms.unresolved())

    for p in parents:
        # label with log.changeset (instead of log.parent) since this
        # shows a working directory parent *changeset*:
        # i18n: column positioning for "hg summary"
        ui.write(
            _(b'parent: %d:%s ') % (p.rev(), p),
            label=logcmdutil.changesetlabels(p),
        )
        ui.write(b' '.join(p.tags()), label=b'log.tag')
        if p.bookmarks():
            marks.extend(p.bookmarks())
        if p.rev() == -1:
            if not len(repo):
                ui.write(_(b' (empty repository)'))
            else:
                ui.write(_(b' (no revision checked out)'))
        if p.obsolete():
            ui.write(_(b' (obsolete)'))
        if p.isunstable():
            instabilities = (
                ui.label(instability, b'trouble.%s' % instability)
                for instability in p.instabilities()
            )
            ui.write(b' (' + b', '.join(instabilities) + b')')
        ui.write(b'\n')
        if p.description():
            ui.status(
                b' ' + p.description().splitlines()[0].strip() + b'\n',
                label=b'log.summary',
            )

    branch = ctx.branch()
    bheads = repo.branchheads(branch)
    # i18n: column positioning for "hg summary"
    m = _(b'branch: %s\n') % branch
    if branch != b'default':
        ui.write(m, label=b'log.branch')
    else:
        ui.status(m, label=b'log.branch')

    if marks:
        active = repo._activebookmark
        # i18n: column positioning for "hg summary"
        ui.write(_(b'bookmarks:'), label=b'log.bookmark')
        if active is not None:
            if active in marks:
                ui.write(b' *' + active, label=bookmarks.activebookmarklabel)
                marks.remove(active)
            else:
                ui.write(b' [%s]' % active, label=bookmarks.activebookmarklabel)
        for m in marks:
            ui.write(b' ' + m, label=b'log.bookmark')
        ui.write(b'\n', label=b'log.bookmark')

    status = repo.status(unknown=True)

    c = repo.dirstate.copies()
    copied, renamed = [], []
    for d, s in pycompat.iteritems(c):
        if s in status.removed:
            status.removed.remove(s)
            renamed.append(d)
        else:
            copied.append(d)
        if d in status.added:
            status.added.remove(d)

    subs = [s for s in ctx.substate if ctx.sub(s).dirty()]

    labels = [
        (ui.label(_(b'%d modified'), b'status.modified'), status.modified),
        (ui.label(_(b'%d added'), b'status.added'), status.added),
        (ui.label(_(b'%d removed'), b'status.removed'), status.removed),
        (ui.label(_(b'%d renamed'), b'status.copied'), renamed),
        (ui.label(_(b'%d copied'), b'status.copied'), copied),
        (ui.label(_(b'%d deleted'), b'status.deleted'), status.deleted),
        (ui.label(_(b'%d unknown'), b'status.unknown'), status.unknown),
        (ui.label(_(b'%d unresolved'), b'resolve.unresolved'), unresolved),
        (ui.label(_(b'%d subrepos'), b'status.modified'), subs),
    ]
    t = []
    for l, s in labels:
        if s:
            t.append(l % len(s))

    t = b', '.join(t)
    cleanworkdir = False

    if repo.vfs.exists(b'graftstate'):
        t += _(b' (graft in progress)')
    if repo.vfs.exists(b'updatestate'):
        t += _(b' (interrupted update)')
    elif len(parents) > 1:
        t += _(b' (merge)')
    elif branch != parents[0].branch():
        t += _(b' (new branch)')
    elif parents[0].closesbranch() and pnode in repo.branchheads(
        branch, closed=True
    ):
        t += _(b' (head closed)')
    elif not (
        status.modified
        or status.added
        or status.removed
        or renamed
        or copied
        or subs
    ):
        t += _(b' (clean)')
        cleanworkdir = True
    elif pnode not in bheads:
        t += _(b' (new branch head)')

    if parents:
        pendingphase = max(p.phase() for p in parents)
    else:
        pendingphase = phases.public

    if pendingphase > phases.newcommitphase(ui):
        t += b' (%s)' % phases.phasenames[pendingphase]

    if cleanworkdir:
        # i18n: column positioning for "hg summary"
        ui.status(_(b'commit: %s\n') % t.strip())
    else:
        # i18n: column positioning for "hg summary"
        ui.write(_(b'commit: %s\n') % t.strip())

    # all ancestors of branch heads - all ancestors of parent = new csets
    new = len(
        repo.changelog.findmissing([pctx.node() for pctx in parents], bheads)
    )

    if new == 0:
        # i18n: column positioning for "hg summary"
        ui.status(_(b'update: (current)\n'))
    elif pnode not in bheads:
        # i18n: column positioning for "hg summary"
        ui.write(_(b'update: %d new changesets (update)\n') % new)
    else:
        # i18n: column positioning for "hg summary"
        ui.write(
            _(b'update: %d new changesets, %d branch heads (merge)\n')
            % (new, len(bheads))
        )

    t = []
    draft = len(repo.revs(b'draft()'))
    if draft:
        t.append(_(b'%d draft') % draft)
    secret = len(repo.revs(b'secret()'))
    if secret:
        t.append(_(b'%d secret') % secret)

    if draft or secret:
        ui.status(_(b'phases: %s\n') % b', '.join(t))

    if obsolete.isenabled(repo, obsolete.createmarkersopt):
        for trouble in (b"orphan", b"contentdivergent", b"phasedivergent"):
            numtrouble = len(repo.revs(trouble + b"()"))
            # We write all the possibilities to ease translation
            troublemsg = {
                b"orphan": _(b"orphan: %d changesets"),
                b"contentdivergent": _(b"content-divergent: %d changesets"),
                b"phasedivergent": _(b"phase-divergent: %d changesets"),
            }
            if numtrouble > 0:
                ui.status(troublemsg[trouble] % numtrouble + b"\n")

    cmdutil.summaryhooks(ui, repo)

    if opts.get(b'remote'):
        needsincoming, needsoutgoing = True, True
    else:
        needsincoming, needsoutgoing = False, False
        for i, o in cmdutil.summaryremotehooks(ui, repo, opts, None):
            if i:
                needsincoming = True
            if o:
                needsoutgoing = True
        if not needsincoming and not needsoutgoing:
            return

    def getincoming():
        # XXX We should actually skip this if no default is specified, instead
        # of passing "default" which will resolve as "./default/" if no default
        # path is defined.
        source, branches = urlutil.get_unique_pull_path(
            b'summary', repo, ui, b'default'
        )
        sbranch = branches[0]
        try:
            other = hg.peer(repo, {}, source)
        except error.RepoError:
            if opts.get(b'remote'):
                raise
            return source, sbranch, None, None, None
        revs, checkout = hg.addbranchrevs(repo, other, branches, None)
        if revs:
            revs = [other.lookup(rev) for rev in revs]
        ui.debug(b'comparing with %s\n' % urlutil.hidepassword(source))
        with repo.ui.silent():
            commoninc = discovery.findcommonincoming(repo, other, heads=revs)
        return source, sbranch, other, commoninc, commoninc[1]

    if needsincoming:
        source, sbranch, sother, commoninc, incoming = getincoming()
    else:
        source = sbranch = sother = commoninc = incoming = None

    def getoutgoing():
        # XXX We should actually skip this if no default is specified, instead
        # of passing "default" which will resolve as "./default/" if no default
        # path is defined.
        d = None
        if b'default-push' in ui.paths:
            d = b'default-push'
        elif b'default' in ui.paths:
            d = b'default'
        if d is not None:
            path = urlutil.get_unique_push_path(b'summary', repo, ui, d)
            dest = path.pushloc or path.loc
            dbranch = path.branch
        else:
            dest = b'default'
            dbranch = None
        revs, checkout = hg.addbranchrevs(repo, repo, (dbranch, []), None)
        if source != dest:
            try:
                dother = hg.peer(repo, {}, dest)
            except error.RepoError:
                if opts.get(b'remote'):
                    raise
                return dest, dbranch, None, None
            ui.debug(b'comparing with %s\n' % urlutil.hidepassword(dest))
        elif sother is None:
            # there is no explicit destination peer, but source one is invalid
            return dest, dbranch, None, None
        else:
            dother = sother
        if source != dest or (sbranch is not None and sbranch != dbranch):
            common = None
        else:
            common = commoninc
        if revs:
            revs = [repo.lookup(rev) for rev in revs]
        with repo.ui.silent():
            outgoing = discovery.findcommonoutgoing(
                repo, dother, onlyheads=revs, commoninc=common
            )
        return dest, dbranch, dother, outgoing

    if needsoutgoing:
        dest, dbranch, dother, outgoing = getoutgoing()
    else:
        dest = dbranch = dother = outgoing = None

    if opts.get(b'remote'):
        # Help pytype.  --remote sets both `needsincoming` and `needsoutgoing`.
        # The former always sets `sother` (or raises an exception if it can't);
        # the latter always sets `outgoing`.
        assert sother is not None
        assert outgoing is not None

        t = []
        if incoming:
            t.append(_(b'1 or more incoming'))
        o = outgoing.missing
        if o:
            t.append(_(b'%d outgoing') % len(o))
        other = dother or sother
        if b'bookmarks' in other.listkeys(b'namespaces'):
            counts = bookmarks.summary(repo, other)
            if counts[0] > 0:
                t.append(_(b'%d incoming bookmarks') % counts[0])
            if counts[1] > 0:
                t.append(_(b'%d outgoing bookmarks') % counts[1])

        if t:
            # i18n: column positioning for "hg summary"
            ui.write(_(b'remote: %s\n') % (b', '.join(t)))
        else:
            # i18n: column positioning for "hg summary"
            ui.status(_(b'remote: (synced)\n'))

    cmdutil.summaryremotehooks(
        ui,
        repo,
        opts,
        (
            (source, sbranch, sother, commoninc),
            (dest, dbranch, dother, outgoing),
        ),
    )


@command(
    b'tag',
    [
        (b'f', b'force', None, _(b'force tag')),
        (b'l', b'local', None, _(b'make the tag local')),
        (b'r', b'rev', b'', _(b'revision to tag'), _(b'REV')),
        (b'', b'remove', None, _(b'remove a tag')),
        # -l/--local is already there, commitopts cannot be used
        (b'e', b'edit', None, _(b'invoke editor on commit messages')),
        (b'm', b'message', b'', _(b'use text as commit message'), _(b'TEXT')),
    ]
    + commitopts2,
    _(b'[-f] [-l] [-m TEXT] [-d DATE] [-u USER] [-r REV] NAME...'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def tag(ui, repo, name1, *names, **opts):
    """add one or more tags for the current or given revision

    Name a particular revision using <name>.

    Tags are used to name particular revisions of the repository and are
    very useful to compare different revisions, to go back to significant
    earlier versions or to mark branch points as releases, etc. Changing
    an existing tag is normally disallowed; use -f/--force to override.

    If no revision is given, the parent of the working directory is
    used.

    To facilitate version control, distribution, and merging of tags,
    they are stored as a file named ".hgtags" which is managed similarly
    to other project files and can be hand-edited if necessary. This
    also means that tagging creates a new commit. The file
    ".hg/localtags" is used for local tags (not shared among
    repositories).

    Tag commits are usually made at the head of a branch. If the parent
    of the working directory is not a branch head, :hg:`tag` aborts; use
    -f/--force to force the tag commit to be based on a non-head
    changeset.

    See :hg:`help dates` for a list of formats valid for -d/--date.

    Since tag names have priority over branch names during revision
    lookup, using an existing branch name as a tag name is discouraged.

    Returns 0 on success.
    """
    cmdutil.check_incompatible_arguments(opts, 'remove', ['rev'])
    opts = pycompat.byteskwargs(opts)
    with repo.wlock(), repo.lock():
        rev_ = b"."
        names = [t.strip() for t in (name1,) + names]
        if len(names) != len(set(names)):
            raise error.InputError(_(b'tag names must be unique'))
        for n in names:
            scmutil.checknewlabel(repo, n, b'tag')
            if not n:
                raise error.InputError(
                    _(b'tag names cannot consist entirely of whitespace')
                )
        if opts.get(b'rev'):
            rev_ = opts[b'rev']
        message = opts.get(b'message')
        if opts.get(b'remove'):
            if opts.get(b'local'):
                expectedtype = b'local'
            else:
                expectedtype = b'global'

            for n in names:
                if repo.tagtype(n) == b'global':
                    alltags = tagsmod.findglobaltags(ui, repo)
                    if alltags[n][0] == repo.nullid:
                        raise error.InputError(
                            _(b"tag '%s' is already removed") % n
                        )
                if not repo.tagtype(n):
                    raise error.InputError(_(b"tag '%s' does not exist") % n)
                if repo.tagtype(n) != expectedtype:
                    if expectedtype == b'global':
                        raise error.InputError(
                            _(b"tag '%s' is not a global tag") % n
                        )
                    else:
                        raise error.InputError(
                            _(b"tag '%s' is not a local tag") % n
                        )
            rev_ = b'null'
            if not message:
                # we don't translate commit messages
                message = b'Removed tag %s' % b', '.join(names)
        elif not opts.get(b'force'):
            for n in names:
                if n in repo.tags():
                    raise error.InputError(
                        _(b"tag '%s' already exists (use -f to force)") % n
                    )
        if not opts.get(b'local'):
            p1, p2 = repo.dirstate.parents()
            if p2 != repo.nullid:
                raise error.StateError(_(b'uncommitted merge'))
            bheads = repo.branchheads()
            if not opts.get(b'force') and bheads and p1 not in bheads:
                raise error.InputError(
                    _(
                        b'working directory is not at a branch head '
                        b'(use -f to force)'
                    )
                )
        node = scmutil.revsingle(repo, rev_).node()

        if not message:
            # we don't translate commit messages
            message = b'Added tag %s for changeset %s' % (
                b', '.join(names),
                short(node),
            )

        date = opts.get(b'date')
        if date:
            date = dateutil.parsedate(date)

        if opts.get(b'remove'):
            editform = b'tag.remove'
        else:
            editform = b'tag.add'
        editor = cmdutil.getcommiteditor(
            editform=editform, **pycompat.strkwargs(opts)
        )

        # don't allow tagging the null rev
        if (
            not opts.get(b'remove')
            and scmutil.revsingle(repo, rev_).rev() == nullrev
        ):
            raise error.InputError(_(b"cannot tag null revision"))

        tagsmod.tag(
            repo,
            names,
            node,
            message,
            opts.get(b'local'),
            opts.get(b'user'),
            date,
            editor=editor,
        )


@command(
    b'tags',
    formatteropts,
    b'',
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
    intents={INTENT_READONLY},
)
def tags(ui, repo, **opts):
    """list repository tags

    This lists both regular and local tags. When the -v/--verbose
    switch is used, a third column "local" is printed for local tags.
    When the -q/--quiet switch is used, only the tag name is printed.

    .. container:: verbose

      Template:

      The following keywords are supported in addition to the common template
      keywords and functions such as ``{tag}``. See also
      :hg:`help templates`.

      :type:    String. ``local`` for local tags.

    Returns 0 on success.
    """

    opts = pycompat.byteskwargs(opts)
    ui.pager(b'tags')
    fm = ui.formatter(b'tags', opts)
    hexfunc = fm.hexfunc

    for t, n in reversed(repo.tagslist()):
        hn = hexfunc(n)
        label = b'tags.normal'
        tagtype = repo.tagtype(t)
        if not tagtype or tagtype == b'global':
            tagtype = b''
        else:
            label = b'tags.' + tagtype

        fm.startitem()
        fm.context(repo=repo)
        fm.write(b'tag', b'%s', t, label=label)
        fmt = b" " * (30 - encoding.colwidth(t)) + b' %5d:%s'
        fm.condwrite(
            not ui.quiet,
            b'rev node',
            fmt,
            repo.changelog.rev(n),
            hn,
            label=label,
        )
        fm.condwrite(
            ui.verbose and tagtype, b'type', b' %s', tagtype, label=label
        )
        fm.plain(b'\n')
    fm.end()


@command(
    b'tip',
    [
        (b'p', b'patch', None, _(b'show patch')),
        (b'g', b'git', None, _(b'use git extended diff format')),
    ]
    + templateopts,
    _(b'[-p] [-g]'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
)
def tip(ui, repo, **opts):
    """show the tip revision (DEPRECATED)

    The tip revision (usually just called the tip) is the changeset
    most recently added to the repository (and therefore the most
    recently changed head).

    If you have just made a commit, that commit will be the tip. If
    you have just pulled changes from another repository, the tip of
    that repository becomes the current tip. The "tip" tag is special
    and cannot be renamed or assigned to a different changeset.

    This command is deprecated, please use :hg:`heads` instead.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    displayer = logcmdutil.changesetdisplayer(ui, repo, opts)
    displayer.show(repo[b'tip'])
    displayer.close()


@command(
    b'unbundle',
    [
        (
            b'u',
            b'update',
            None,
            _(b'update to new branch head if changesets were unbundled'),
        )
    ],
    _(b'[-u] FILE...'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def unbundle(ui, repo, fname1, *fnames, **opts):
    """apply one or more bundle files

    Apply one or more bundle files generated by :hg:`bundle`.

    Returns 0 on success, 1 if an update has unresolved files.
    """
    fnames = (fname1,) + fnames

    with repo.lock():
        for fname in fnames:
            f = hg.openpath(ui, fname)
            gen = exchange.readbundle(ui, f, fname)
            if isinstance(gen, streamclone.streamcloneapplier):
                raise error.InputError(
                    _(
                        b'packed bundles cannot be applied with '
                        b'"hg unbundle"'
                    ),
                    hint=_(b'use "hg debugapplystreamclonebundle"'),
                )
            url = b'bundle:' + fname
            try:
                txnname = b'unbundle'
                if not isinstance(gen, bundle2.unbundle20):
                    txnname = b'unbundle\n%s' % urlutil.hidepassword(url)
                with repo.transaction(txnname) as tr:
                    op = bundle2.applybundle(
                        repo, gen, tr, source=b'unbundle', url=url
                    )
            except error.BundleUnknownFeatureError as exc:
                raise error.Abort(
                    _(b'%s: unknown bundle feature, %s') % (fname, exc),
                    hint=_(
                        b"see https://mercurial-scm.org/"
                        b"wiki/BundleFeature for more "
                        b"information"
                    ),
                )
            modheads = bundle2.combinechangegroupresults(op)

    if postincoming(ui, repo, modheads, opts.get('update'), None, None):
        return 1
    else:
        return 0


@command(
    b'unshelve',
    [
        (b'a', b'abort', None, _(b'abort an incomplete unshelve operation')),
        (
            b'c',
            b'continue',
            None,
            _(b'continue an incomplete unshelve operation'),
        ),
        (b'i', b'interactive', None, _(b'use interactive mode (EXPERIMENTAL)')),
        (b'k', b'keep', None, _(b'keep shelve after unshelving')),
        (
            b'n',
            b'name',
            b'',
            _(b'restore shelved change with given name'),
            _(b'NAME'),
        ),
        (b't', b'tool', b'', _(b'specify merge tool')),
        (
            b'',
            b'date',
            b'',
            _(b'set date for temporary commits (DEPRECATED)'),
            _(b'DATE'),
        ),
    ],
    _(b'hg unshelve [OPTION]... [[-n] SHELVED]'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
)
def unshelve(ui, repo, *shelved, **opts):
    """restore a shelved change to the working directory

    This command accepts an optional name of a shelved change to
    restore. If none is given, the most recent shelved change is used.

    If a shelved change is applied successfully, the bundle that
    contains the shelved changes is moved to a backup location
    (.hg/shelve-backup).

    Since you can restore a shelved change on top of an arbitrary
    commit, it is possible that unshelving will result in a conflict
    between your changes and the commits you are unshelving onto. If
    this occurs, you must resolve the conflict, then use
    ``--continue`` to complete the unshelve operation. (The bundle
    will not be moved until you successfully complete the unshelve.)

    (Alternatively, you can use ``--abort`` to abandon an unshelve
    that causes a conflict. This reverts the unshelved changes, and
    leaves the bundle in place.)

    If bare shelved change (without interactive, include and exclude
    option) was done on newly created branch it would restore branch
    information to the working directory.

    After a successful unshelve, the shelved changes are stored in a
    backup directory. Only the N most recent backups are kept. N
    defaults to 10 but can be overridden using the ``shelve.maxbackups``
    configuration option.

    .. container:: verbose

       Timestamp in seconds is used to decide order of backups. More
       than ``maxbackups`` backups are kept, if same timestamp
       prevents from deciding exact order of them, for safety.

       Selected changes can be unshelved with ``--interactive`` flag.
       The working directory is updated with the selected changes, and
       only the unselected changes remain shelved.
       Note: The whole shelve is applied to working directory first before
       running interactively. So, this will bring up all the conflicts between
       working directory and the shelve, irrespective of which changes will be
       unshelved.
    """
    with repo.wlock():
        return shelvemod.unshelvecmd(ui, repo, *shelved, **opts)


statemod.addunfinished(
    b'unshelve',
    fname=b'shelvedstate',
    continueflag=True,
    abortfunc=shelvemod.hgabortunshelve,
    continuefunc=shelvemod.hgcontinueunshelve,
    cmdmsg=_(b'unshelve already in progress'),
)


@command(
    b'update|up|checkout|co',
    [
        (b'C', b'clean', None, _(b'discard uncommitted changes (no backup)')),
        (b'c', b'check', None, _(b'require clean working directory')),
        (b'm', b'merge', None, _(b'merge uncommitted changes')),
        (b'd', b'date', b'', _(b'tipmost revision matching date'), _(b'DATE')),
        (b'r', b'rev', b'', _(b'revision'), _(b'REV')),
    ]
    + mergetoolopts,
    _(b'[-C|-c|-m] [-d DATE] [[-r] REV]'),
    helpcategory=command.CATEGORY_WORKING_DIRECTORY,
    helpbasic=True,
)
def update(ui, repo, node=None, **opts):
    """update working directory (or switch revisions)

    Update the repository's working directory to the specified
    changeset. If no changeset is specified, update to the tip of the
    current named branch and move the active bookmark (see :hg:`help
    bookmarks`).

    Update sets the working directory's parent revision to the specified
    changeset (see :hg:`help parents`).

    If the changeset is not a descendant or ancestor of the working
    directory's parent and there are uncommitted changes, the update is
    aborted. With the -c/--check option, the working directory is checked
    for uncommitted changes; if none are found, the working directory is
    updated to the specified changeset.

    .. container:: verbose

      The -C/--clean, -c/--check, and -m/--merge options control what
      happens if the working directory contains uncommitted changes.
      At most of one of them can be specified.

      1. If no option is specified, and if
         the requested changeset is an ancestor or descendant of
         the working directory's parent, the uncommitted changes
         are merged into the requested changeset and the merged
         result is left uncommitted. If the requested changeset is
         not an ancestor or descendant (that is, it is on another
         branch), the update is aborted and the uncommitted changes
         are preserved.

      2. With the -m/--merge option, the update is allowed even if the
         requested changeset is not an ancestor or descendant of
         the working directory's parent.

      3. With the -c/--check option, the update is aborted and the
         uncommitted changes are preserved.

      4. With the -C/--clean option, uncommitted changes are discarded and
         the working directory is updated to the requested changeset.

    To cancel an uncommitted merge (and lose your changes), use
    :hg:`merge --abort`.

    Use null as the changeset to remove the working directory (like
    :hg:`clone -U`).

    If you want to revert just one file to an older revision, use
    :hg:`revert [-r REV] NAME`.

    See :hg:`help dates` for a list of formats valid for -d/--date.

    Returns 0 on success, 1 if there are unresolved files.
    """
    cmdutil.check_at_most_one_arg(opts, 'clean', 'check', 'merge')
    rev = opts.get('rev')
    date = opts.get('date')
    clean = opts.get('clean')
    check = opts.get('check')
    merge = opts.get('merge')
    if rev and node:
        raise error.InputError(_(b"please specify just one revision"))

    if ui.configbool(b'commands', b'update.requiredest'):
        if not node and not rev and not date:
            raise error.InputError(
                _(b'you must specify a destination'),
                hint=_(b'for example: hg update ".::"'),
            )

    if rev is None or rev == b'':
        rev = node

    if date and rev is not None:
        raise error.InputError(_(b"you can't specify a revision and a date"))

    updatecheck = None
    if check:
        updatecheck = b'abort'
    elif merge:
        updatecheck = b'none'

    with repo.wlock():
        cmdutil.clearunfinished(repo)
        if date:
            rev = cmdutil.finddate(ui, repo, date)

        # if we defined a bookmark, we have to remember the original name
        brev = rev
        if rev:
            repo = scmutil.unhidehashlikerevs(repo, [rev], b'nowarn')
        ctx = scmutil.revsingle(repo, rev, default=None)
        rev = ctx.rev()
        hidden = ctx.hidden()
        overrides = {(b'ui', b'forcemerge'): opts.get('tool', b'')}
        with ui.configoverride(overrides, b'update'):
            ret = hg.updatetotally(
                ui, repo, rev, brev, clean=clean, updatecheck=updatecheck
            )
        if hidden:
            ctxstr = ctx.hex()[:12]
            ui.warn(_(b"updated to hidden changeset %s\n") % ctxstr)

            if ctx.obsolete():
                obsfatemsg = obsutil._getfilteredreason(repo, ctxstr, ctx)
                ui.warn(b"(%s)\n" % obsfatemsg)
        return ret


@command(
    b'verify',
    [(b'', b'full', False, b'perform more checks (EXPERIMENTAL)')],
    helpcategory=command.CATEGORY_MAINTENANCE,
)
def verify(ui, repo, **opts):
    """verify the integrity of the repository

    Verify the integrity of the current repository.

    This will perform an extensive check of the repository's
    integrity, validating the hashes and checksums of each entry in
    the changelog, manifest, and tracked files, as well as the
    integrity of their crosslinks and indices.

    Please see https://mercurial-scm.org/wiki/RepositoryCorruption
    for more information about recovery from corruption of the
    repository.

    Returns 0 on success, 1 if errors are encountered.
    """
    opts = pycompat.byteskwargs(opts)

    level = None
    if opts[b'full']:
        level = verifymod.VERIFY_FULL
    return hg.verify(repo, level)


@command(
    b'version',
    [] + formatteropts,
    helpcategory=command.CATEGORY_HELP,
    norepo=True,
    intents={INTENT_READONLY},
)
def version_(ui, **opts):
    """output version and copyright information

    .. container:: verbose

      Template:

      The following keywords are supported. See also :hg:`help templates`.

      :extensions: List of extensions.
      :ver:     String. Version number.

      And each entry of ``{extensions}`` provides the following sub-keywords
      in addition to ``{ver}``.

      :bundled: Boolean. True if included in the release.
      :name:    String. Extension name.
    """
    opts = pycompat.byteskwargs(opts)
    if ui.verbose:
        ui.pager(b'version')
    fm = ui.formatter(b"version", opts)
    fm.startitem()
    fm.write(
        b"ver", _(b"Mercurial Distributed SCM (version %s)\n"), util.version()
    )
    license = _(
        b"(see https://mercurial-scm.org for more information)\n"
        b"\nCopyright (C) 2005-2021 Olivia Mackall and others\n"
        b"This is free software; see the source for copying conditions. "
        b"There is NO\nwarranty; "
        b"not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.\n"
    )
    if not ui.quiet:
        fm.plain(license)

    if ui.verbose:
        fm.plain(_(b"\nEnabled extensions:\n\n"))
    # format names and versions into columns
    names = []
    vers = []
    isinternals = []
    for name, module in sorted(extensions.extensions()):
        names.append(name)
        vers.append(extensions.moduleversion(module) or None)
        isinternals.append(extensions.ismoduleinternal(module))
    fn = fm.nested(b"extensions", tmpl=b'{name}\n')
    if names:
        namefmt = b"  %%-%ds  " % max(len(n) for n in names)
        places = [_(b"external"), _(b"internal")]
        for n, v, p in zip(names, vers, isinternals):
            fn.startitem()
            fn.condwrite(ui.verbose, b"name", namefmt, n)
            if ui.verbose:
                fn.plain(b"%s  " % places[p])
            fn.data(bundled=p)
            fn.condwrite(ui.verbose and v, b"ver", b"%s", v)
            if ui.verbose:
                fn.plain(b"\n")
    fn.end()
    fm.end()


def loadcmdtable(ui, name, cmdtable):
    """Load command functions from specified cmdtable"""
    overrides = [cmd for cmd in cmdtable if cmd in table]
    if overrides:
        ui.warn(
            _(b"extension '%s' overrides commands: %s\n")
            % (name, b" ".join(overrides))
        )
    table.update(cmdtable)
