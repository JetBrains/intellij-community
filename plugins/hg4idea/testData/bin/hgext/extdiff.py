# extdiff.py - external diff program support for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''command to allow external programs to compare revisions

The extdiff Mercurial extension allows you to use external programs
to compare revisions, or revision with working directory. The external
diff programs are called with a configurable set of options and two
non-option arguments: paths to directories containing snapshots of
files to compare.

If there is more than one file being compared and the "child" revision
is the working directory, any modifications made in the external diff
program will be copied back to the working directory from the temporary
directory.

The extdiff extension also allows you to configure new diff commands, so
you do not need to type :hg:`extdiff -p kdiff3` always. ::

  [extdiff]
  # add new command that runs GNU diff(1) in 'context diff' mode
  cdiff = gdiff -Nprc5
  ## or the old way:
  #cmd.cdiff = gdiff
  #opts.cdiff = -Nprc5

  # add new command called meld, runs meld (no need to name twice).  If
  # the meld executable is not available, the meld tool in [merge-tools]
  # will be used, if available
  meld =

  # add new command called vimdiff, runs gvimdiff with DirDiff plugin
  # (see http://www.vim.org/scripts/script.php?script_id=102) Non
  # English user, be sure to put "let g:DirDiffDynamicDiffText = 1" in
  # your .vimrc
  vimdiff = gvim -f "+next" \\
            "+execute 'DirDiff' fnameescape(argv(0)) fnameescape(argv(1))"

Tool arguments can include variables that are expanded at runtime::

  $parent1, $plabel1 - filename, descriptive label of first parent
  $child,   $clabel  - filename, descriptive label of child revision
  $parent2, $plabel2 - filename, descriptive label of second parent
  $root              - repository root
  $parent is an alias for $parent1.

The extdiff extension will look in your [diff-tools] and [merge-tools]
sections for diff tool arguments, when none are specified in [extdiff].

::

  [extdiff]
  kdiff3 =

  [diff-tools]
  kdiff3.diffargs=--L1 '$plabel1' --L2 '$clabel' $parent $child

If a program has a graphical interface, it might be interesting to tell
Mercurial about it. It will prevent the program from being mistakenly
used in a terminal-only environment (such as an SSH terminal session),
and will make :hg:`extdiff --per-file` open multiple file diffs at once
instead of one by one (if you still want to open file diffs one by one,
you can use the --confirm option).

Declaring that a tool has a graphical interface can be done with the
``gui`` flag next to where ``diffargs`` are specified:

::

  [diff-tools]
  kdiff3.diffargs=--L1 '$plabel1' --L2 '$clabel' $parent $child
  kdiff3.gui = true

You can use -I/-X and list of file or directory names like normal
:hg:`diff` command. The extdiff extension makes snapshots of only
needed files, so running the external diff program will actually be
pretty fast (at least faster than having to compare the entire tree).
'''


import os
import re
import shutil
import stat
import subprocess

from mercurial.i18n import _
from mercurial.node import (
    nullrev,
    short,
)
from mercurial import (
    archival,
    cmdutil,
    encoding,
    error,
    filemerge,
    formatter,
    logcmdutil,
    pycompat,
    registrar,
    scmutil,
    util,
)
from mercurial.utils import (
    procutil,
    stringutil,
)

cmdtable = {}
command = registrar.command(cmdtable)

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'extdiff',
    br'opts\..*',
    default=b'',
    generic=True,
)

configitem(
    b'extdiff',
    br'gui\..*',
    generic=True,
)

configitem(
    b'diff-tools',
    br'.*\.diffargs$',
    default=None,
    generic=True,
)

configitem(
    b'diff-tools',
    br'.*\.gui$',
    generic=True,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


def snapshot(ui, repo, files, node, tmproot, listsubrepos):
    """snapshot files as of some revision
    if not using snapshot, -I/-X does not work and recursive diff
    in tools like kdiff3 and meld displays too many files."""
    dirname = os.path.basename(repo.root)
    if dirname == b"":
        dirname = b"root"
    if node is not None:
        dirname = b'%s.%s' % (dirname, short(node))
    base = os.path.join(tmproot, dirname)
    os.mkdir(base)
    fnsandstat = []

    if node is not None:
        ui.note(
            _(b'making snapshot of %d files from rev %s\n')
            % (len(files), short(node))
        )
    else:
        ui.note(
            _(b'making snapshot of %d files from working directory\n')
            % (len(files))
        )

    if files:
        repo.ui.setconfig(b"ui", b"archivemeta", False)

        archival.archive(
            repo,
            base,
            node,
            b'files',
            match=scmutil.matchfiles(repo, files),
            subrepos=listsubrepos,
        )

        for fn in sorted(files):
            wfn = util.pconvert(fn)
            ui.note(b'  %s\n' % wfn)

            if node is None:
                dest = os.path.join(base, wfn)

                fnsandstat.append((dest, repo.wjoin(fn), os.lstat(dest)))
    return dirname, fnsandstat


def formatcmdline(
    cmdline,
    repo_root,
    do3way,
    parent1,
    plabel1,
    parent2,
    plabel2,
    child,
    clabel,
):
    # Function to quote file/dir names in the argument string.
    # When not operating in 3-way mode, an empty string is
    # returned for parent2
    replace = {
        b'parent': parent1,
        b'parent1': parent1,
        b'parent2': parent2,
        b'plabel1': plabel1,
        b'plabel2': plabel2,
        b'child': child,
        b'clabel': clabel,
        b'root': repo_root,
    }

    def quote(match):
        pre = match.group(2)
        key = match.group(3)
        if not do3way and key == b'parent2':
            return pre
        return pre + procutil.shellquote(replace[key])

    # Match parent2 first, so 'parent1?' will match both parent1 and parent
    regex = (
        br'''(['"]?)([^\s'"$]*)'''
        br'\$(parent2|parent1?|child|plabel1|plabel2|clabel|root)\1'
    )
    if not do3way and not re.search(regex, cmdline):
        cmdline += b' $parent1 $child'
    return re.sub(regex, quote, cmdline)


def _systembackground(cmd, environ=None, cwd=None):
    """like 'procutil.system', but returns the Popen object directly
    so we don't have to wait on it.
    """
    env = procutil.shellenviron(environ)
    proc = subprocess.Popen(
        procutil.tonativestr(cmd),
        shell=True,
        close_fds=procutil.closefds,
        env=procutil.tonativeenv(env),
        cwd=pycompat.rapply(procutil.tonativestr, cwd),
    )
    return proc


def _runperfilediff(
    cmdline,
    repo_root,
    ui,
    guitool,
    do3way,
    confirm,
    commonfiles,
    tmproot,
    dir1a,
    dir1b,
    dir2,
    rev1a,
    rev1b,
    rev2,
):
    # Note that we need to sort the list of files because it was
    # built in an "unstable" way and it's annoying to get files in a
    # random order, especially when "confirm" mode is enabled.
    waitprocs = []
    totalfiles = len(commonfiles)
    for idx, commonfile in enumerate(sorted(commonfiles)):
        path1a = os.path.join(dir1a, commonfile)
        label1a = commonfile + rev1a
        if not os.path.isfile(path1a):
            path1a = pycompat.osdevnull

        path1b = b''
        label1b = b''
        if do3way:
            path1b = os.path.join(dir1b, commonfile)
            label1b = commonfile + rev1b
            if not os.path.isfile(path1b):
                path1b = pycompat.osdevnull

        path2 = os.path.join(dir2, commonfile)
        label2 = commonfile + rev2

        if confirm:
            # Prompt before showing this diff
            difffiles = _(b'diff %s (%d of %d)') % (
                commonfile,
                idx + 1,
                totalfiles,
            )
            responses = _(
                b'[Yns?]'
                b'$$ &Yes, show diff'
                b'$$ &No, skip this diff'
                b'$$ &Skip remaining diffs'
                b'$$ &? (display help)'
            )
            r = ui.promptchoice(b'%s %s' % (difffiles, responses))
            if r == 3:  # ?
                while r == 3:
                    for c, t in ui.extractchoices(responses)[1]:
                        ui.write(b'%s - %s\n' % (c, encoding.lower(t)))
                    r = ui.promptchoice(b'%s %s' % (difffiles, responses))
            if r == 0:  # yes
                pass
            elif r == 1:  # no
                continue
            elif r == 2:  # skip
                break

        curcmdline = formatcmdline(
            cmdline,
            repo_root,
            do3way=do3way,
            parent1=path1a,
            plabel1=label1a,
            parent2=path1b,
            plabel2=label1b,
            child=path2,
            clabel=label2,
        )

        if confirm or not guitool:
            # Run the comparison program and wait for it to exit
            # before we show the next file.
            # This is because either we need to wait for confirmation
            # from the user between each invocation, or because, as far
            # as we know, the tool doesn't have a GUI, in which case
            # we can't run multiple CLI programs at the same time.
            ui.debug(
                b'running %r in %s\n' % (pycompat.bytestr(curcmdline), tmproot)
            )
            ui.system(curcmdline, cwd=tmproot, blockedtag=b'extdiff')
        else:
            # Run the comparison program but don't wait, as we're
            # going to rapid-fire each file diff and then wait on
            # the whole group.
            ui.debug(
                b'running %r in %s (backgrounded)\n'
                % (pycompat.bytestr(curcmdline), tmproot)
            )
            proc = _systembackground(curcmdline, cwd=tmproot)
            waitprocs.append(proc)

    if waitprocs:
        with ui.timeblockedsection(b'extdiff'):
            for proc in waitprocs:
                proc.wait()


def diffpatch(ui, repo, node1, node2, tmproot, matcher, cmdline):
    template = b'hg-%h.patch'
    # write patches to temporary files
    with formatter.nullformatter(ui, b'extdiff', {}) as fm:
        cmdutil.export(
            repo,
            [repo[node1].rev(), repo[node2].rev()],
            fm,
            fntemplate=repo.vfs.reljoin(tmproot, template),
            match=matcher,
        )
    label1 = cmdutil.makefilename(repo[node1], template)
    label2 = cmdutil.makefilename(repo[node2], template)
    file1 = repo.vfs.reljoin(tmproot, label1)
    file2 = repo.vfs.reljoin(tmproot, label2)
    cmdline = formatcmdline(
        cmdline,
        repo.root,
        # no 3way while comparing patches
        do3way=False,
        parent1=file1,
        plabel1=label1,
        # while comparing patches, there is no second parent
        parent2=None,
        plabel2=None,
        child=file2,
        clabel=label2,
    )
    ui.debug(b'running %r in %s\n' % (pycompat.bytestr(cmdline), tmproot))
    ui.system(cmdline, cwd=tmproot, blockedtag=b'extdiff')
    return 1


def diffrevs(
    ui,
    repo,
    ctx1a,
    ctx1b,
    ctx2,
    matcher,
    tmproot,
    cmdline,
    do3way,
    guitool,
    opts,
):

    subrepos = opts.get(b'subrepos')

    # calculate list of files changed between both revs
    st = ctx1a.status(ctx2, matcher, listsubrepos=subrepos)
    mod_a, add_a, rem_a = set(st.modified), set(st.added), set(st.removed)
    if do3way:
        stb = ctx1b.status(ctx2, matcher, listsubrepos=subrepos)
        mod_b, add_b, rem_b = (
            set(stb.modified),
            set(stb.added),
            set(stb.removed),
        )
    else:
        mod_b, add_b, rem_b = set(), set(), set()
    modadd = mod_a | add_a | mod_b | add_b
    common = modadd | rem_a | rem_b
    if not common:
        return 0

    # Always make a copy of ctx1a (and ctx1b, if applicable)
    # dir1a should contain files which are:
    #   * modified or removed from ctx1a to ctx2
    #   * modified or added from ctx1b to ctx2
    #     (except file added from ctx1a to ctx2 as they were not present in
    #     ctx1a)
    dir1a_files = mod_a | rem_a | ((mod_b | add_b) - add_a)
    dir1a = snapshot(ui, repo, dir1a_files, ctx1a.node(), tmproot, subrepos)[0]
    rev1a = b'' if ctx1a.rev() is None else b'@%d' % ctx1a.rev()
    if do3way:
        # file calculation criteria same as dir1a
        dir1b_files = mod_b | rem_b | ((mod_a | add_a) - add_b)
        dir1b = snapshot(
            ui, repo, dir1b_files, ctx1b.node(), tmproot, subrepos
        )[0]
        rev1b = b'@%d' % ctx1b.rev()
    else:
        dir1b = None
        rev1b = b''

    fnsandstat = []

    # If ctx2 is not the wc or there is >1 change, copy it
    dir2root = b''
    rev2 = b''
    if ctx2.node() is not None:
        dir2 = snapshot(ui, repo, modadd, ctx2.node(), tmproot, subrepos)[0]
        rev2 = b'@%d' % ctx2.rev()
    elif len(common) > 1:
        # we only actually need to get the files to copy back to
        # the working dir in this case (because the other cases
        # are: diffing 2 revisions or single file -- in which case
        # the file is already directly passed to the diff tool).
        dir2, fnsandstat = snapshot(ui, repo, modadd, None, tmproot, subrepos)
    else:
        # This lets the diff tool open the changed file directly
        dir2 = b''
        dir2root = repo.root

    label1a = rev1a
    label1b = rev1b
    label2 = rev2

    if not opts.get(b'per_file'):
        # If only one change, diff the files instead of the directories
        # Handle bogus modifies correctly by checking if the files exist
        if len(common) == 1:
            common_file = util.localpath(common.pop())
            dir1a = os.path.join(tmproot, dir1a, common_file)
            label1a = common_file + rev1a
            if not os.path.isfile(dir1a):
                dir1a = pycompat.osdevnull
            if do3way:
                dir1b = os.path.join(tmproot, dir1b, common_file)
                label1b = common_file + rev1b
                if not os.path.isfile(dir1b):
                    dir1b = pycompat.osdevnull
            dir2 = os.path.join(dir2root, dir2, common_file)
            label2 = common_file + rev2

        # Run the external tool on the 2 temp directories or the patches
        cmdline = formatcmdline(
            cmdline,
            repo.root,
            do3way=do3way,
            parent1=dir1a,
            plabel1=label1a,
            parent2=dir1b,
            plabel2=label1b,
            child=dir2,
            clabel=label2,
        )
        ui.debug(b'running %r in %s\n' % (pycompat.bytestr(cmdline), tmproot))
        ui.system(cmdline, cwd=tmproot, blockedtag=b'extdiff')
    else:
        # Run the external tool once for each pair of files
        _runperfilediff(
            cmdline,
            repo.root,
            ui,
            guitool=guitool,
            do3way=do3way,
            confirm=opts.get(b'confirm'),
            commonfiles=common,
            tmproot=tmproot,
            dir1a=os.path.join(tmproot, dir1a),
            dir1b=os.path.join(tmproot, dir1b) if do3way else None,
            dir2=os.path.join(dir2root, dir2),
            rev1a=rev1a,
            rev1b=rev1b,
            rev2=rev2,
        )

    for copy_fn, working_fn, st in fnsandstat:
        cpstat = os.lstat(copy_fn)
        # Some tools copy the file and attributes, so mtime may not detect
        # all changes.  A size check will detect more cases, but not all.
        # The only certain way to detect every case is to diff all files,
        # which could be expensive.
        # copyfile() carries over the permission, so the mode check could
        # be in an 'elif' branch, but for the case where the file has
        # changed without affecting mtime or size.
        if (
            cpstat[stat.ST_MTIME] != st[stat.ST_MTIME]
            or cpstat.st_size != st.st_size
            or (cpstat.st_mode & 0o100) != (st.st_mode & 0o100)
        ):
            ui.debug(
                b'file changed while diffing. '
                b'Overwriting: %s (src: %s)\n' % (working_fn, copy_fn)
            )
            util.copyfile(copy_fn, working_fn)

    return 1


def dodiff(ui, repo, cmdline, pats, opts, guitool=False):
    """Do the actual diff:

    - copy to a temp structure if diffing 2 internal revisions
    - copy to a temp structure if diffing working revision with
      another one and more than 1 file is changed
    - just invoke the diff for a single file in the working dir
    """

    cmdutil.check_at_most_one_arg(opts, b'rev', b'change')
    revs = opts.get(b'rev')
    from_rev = opts.get(b'from')
    to_rev = opts.get(b'to')
    change = opts.get(b'change')
    do3way = b'$parent2' in cmdline

    if change:
        ctx2 = logcmdutil.revsingle(repo, change, None)
        ctx1a, ctx1b = ctx2.p1(), ctx2.p2()
    elif from_rev or to_rev:
        repo = scmutil.unhidehashlikerevs(
            repo, [from_rev] + [to_rev], b'nowarn'
        )
        ctx1a = logcmdutil.revsingle(repo, from_rev, None)
        ctx1b = repo[nullrev]
        ctx2 = logcmdutil.revsingle(repo, to_rev, None)
    else:
        ctx1a, ctx2 = logcmdutil.revpair(repo, revs)
        if not revs:
            ctx1b = repo[None].p2()
        else:
            ctx1b = repo[nullrev]

    # Disable 3-way merge if there is only one parent
    if do3way:
        if ctx1b.rev() == nullrev:
            do3way = False

    matcher = scmutil.match(ctx2, pats, opts)

    if opts.get(b'patch'):
        if opts.get(b'subrepos'):
            raise error.Abort(_(b'--patch cannot be used with --subrepos'))
        if opts.get(b'per_file'):
            raise error.Abort(_(b'--patch cannot be used with --per-file'))
        if ctx2.node() is None:
            raise error.Abort(_(b'--patch requires two revisions'))

    tmproot = pycompat.mkdtemp(prefix=b'extdiff.')
    try:
        if opts.get(b'patch'):
            return diffpatch(
                ui, repo, ctx1a.node(), ctx2.node(), tmproot, matcher, cmdline
            )

        return diffrevs(
            ui,
            repo,
            ctx1a,
            ctx1b,
            ctx2,
            matcher,
            tmproot,
            cmdline,
            do3way,
            guitool,
            opts,
        )

    finally:
        ui.note(_(b'cleaning up temp directory\n'))
        shutil.rmtree(tmproot)


extdiffopts = (
    [
        (
            b'o',
            b'option',
            [],
            _(b'pass option to comparison program'),
            _(b'OPT'),
        ),
        (b'r', b'rev', [], _(b'revision (DEPRECATED)'), _(b'REV')),
        (b'', b'from', b'', _(b'revision to diff from'), _(b'REV1')),
        (b'', b'to', b'', _(b'revision to diff to'), _(b'REV2')),
        (b'c', b'change', b'', _(b'change made by revision'), _(b'REV')),
        (
            b'',
            b'per-file',
            False,
            _(b'compare each file instead of revision snapshots'),
        ),
        (
            b'',
            b'confirm',
            False,
            _(b'prompt user before each external program invocation'),
        ),
        (b'', b'patch', None, _(b'compare patches for two revisions')),
    ]
    + cmdutil.walkopts
    + cmdutil.subrepoopts
)


@command(
    b'extdiff',
    [
        (b'p', b'program', b'', _(b'comparison program to run'), _(b'CMD')),
    ]
    + extdiffopts,
    _(b'hg extdiff [OPT]... [FILE]...'),
    helpcategory=command.CATEGORY_FILE_CONTENTS,
    inferrepo=True,
)
def extdiff(ui, repo, *pats, **opts):
    """use external program to diff repository (or selected files)

    Show differences between revisions for the specified files, using
    an external program. The default program used is diff, with
    default options "-Npru".

    To select a different program, use the -p/--program option. The
    program will be passed the names of two directories to compare,
    unless the --per-file option is specified (see below). To pass
    additional options to the program, use -o/--option. These will be
    passed before the names of the directories or files to compare.

    The --from, --to, and --change options work the same way they do for
    :hg:`diff`.

    The --per-file option runs the external program repeatedly on each
    file to diff, instead of once on two directories. By default,
    this happens one by one, where the next file diff is open in the
    external program only once the previous external program (for the
    previous file diff) has exited. If the external program has a
    graphical interface, it can open all the file diffs at once instead
    of one by one. See :hg:`help -e extdiff` for information about how
    to tell Mercurial that a given program has a graphical interface.

    The --confirm option will prompt the user before each invocation of
    the external program. It is ignored if --per-file isn't specified.
    """
    opts = pycompat.byteskwargs(opts)
    program = opts.get(b'program')
    option = opts.get(b'option')
    if not program:
        program = b'diff'
        option = option or [b'-Npru']
    cmdline = b' '.join(map(procutil.shellquote, [program] + option))
    return dodiff(ui, repo, cmdline, pats, opts)


class savedcmd:
    """use external program to diff repository (or selected files)

    Show differences between revisions for the specified files, using
    the following program::

        %(path)s

    When two revision arguments are given, then changes are shown
    between those revisions. If only one revision is specified then
    that revision is compared to the working directory, and, when no
    revisions are specified, the working directory files are compared
    to its parent.
    """

    def __init__(self, path, cmdline, isgui):
        # We can't pass non-ASCII through docstrings (and path is
        # in an unknown encoding anyway), but avoid double separators on
        # Windows
        docpath = stringutil.escapestr(path).replace(b'\\\\', b'\\')
        self.__doc__ %= {'path': pycompat.sysstr(stringutil.uirepr(docpath))}
        self._cmdline = cmdline
        self._isgui = isgui

    def __call__(self, ui, repo, *pats, **opts):
        opts = pycompat.byteskwargs(opts)
        options = b' '.join(map(procutil.shellquote, opts[b'option']))
        if options:
            options = b' ' + options
        return dodiff(
            ui, repo, self._cmdline + options, pats, opts, guitool=self._isgui
        )


def _gettooldetails(ui, cmd, path):
    """
    returns following things for a
    ```
    [extdiff]
    <cmd> = <path>
    ```
    entry:

    cmd: command/tool name
    path: path to the tool
    cmdline: the command which should be run
    isgui: whether the tool uses GUI or not

    Reads all external tools related configs, whether it be extdiff section,
    diff-tools or merge-tools section, or its specified in an old format or
    the latest format.
    """
    path = util.expandpath(path)
    if cmd.startswith(b'cmd.'):
        cmd = cmd[4:]
        if not path:
            path = procutil.findexe(cmd)
            if path is None:
                path = filemerge.findexternaltool(ui, cmd) or cmd
        diffopts = ui.config(b'extdiff', b'opts.' + cmd)
        cmdline = procutil.shellquote(path)
        if diffopts:
            cmdline += b' ' + diffopts
        isgui = ui.configbool(b'extdiff', b'gui.' + cmd)
    else:
        if path:
            # case "cmd = path opts"
            cmdline = path
            diffopts = len(pycompat.shlexsplit(cmdline)) > 1
        else:
            # case "cmd ="
            path = procutil.findexe(cmd)
            if path is None:
                path = filemerge.findexternaltool(ui, cmd) or cmd
            cmdline = procutil.shellquote(path)
            diffopts = False
        isgui = ui.configbool(b'extdiff', b'gui.' + cmd)
    # look for diff arguments in [diff-tools] then [merge-tools]
    if not diffopts:
        key = cmd + b'.diffargs'
        for section in (b'diff-tools', b'merge-tools'):
            args = ui.config(section, key)
            if args:
                cmdline += b' ' + args
                if isgui is None:
                    isgui = ui.configbool(section, cmd + b'.gui') or False
                break
    return cmd, path, cmdline, isgui


def uisetup(ui):
    for cmd, path in ui.configitems(b'extdiff'):
        if cmd.startswith(b'opts.') or cmd.startswith(b'gui.'):
            continue
        cmd, path, cmdline, isgui = _gettooldetails(ui, cmd, path)
        command(
            cmd,
            extdiffopts[:],
            _(b'hg %s [OPTION]... [FILE]...') % cmd,
            helpcategory=command.CATEGORY_FILE_CONTENTS,
            inferrepo=True,
        )(savedcmd(path, cmdline, isgui))


# tell hggettext to extract docstrings from these functions:
i18nfunctions = [savedcmd]
