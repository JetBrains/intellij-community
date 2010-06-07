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

The extdiff extension also allows to configure new diff commands, so
you do not need to type "hg extdiff -p kdiff3" always. ::

  [extdiff]
  # add new command that runs GNU diff(1) in 'context diff' mode
  cdiff = gdiff -Nprc5
  ## or the old way:
  #cmd.cdiff = gdiff
  #opts.cdiff = -Nprc5

  # add new command called vdiff, runs kdiff3
  vdiff = kdiff3

  # add new command called meld, runs meld (no need to name twice)
  meld =

  # add new command called vimdiff, runs gvimdiff with DirDiff plugin
  # (see http://www.vim.org/scripts/script.php?script_id=102) Non
  # English user, be sure to put "let g:DirDiffDynamicDiffText = 1" in
  # your .vimrc
  vimdiff = gvim -f '+next' '+execute "DirDiff" argv(0) argv(1)'

You can use -I/-X and list of file or directory names like normal "hg
diff" command. The extdiff extension makes snapshots of only needed
files, so running the external diff program will actually be pretty
fast (at least faster than having to compare the entire tree).
'''

from mercurial.i18n import _
from mercurial.node import short, nullid
from mercurial import cmdutil, util, commands, encoding
import os, shlex, shutil, tempfile, re

def snapshot(ui, repo, files, node, tmproot):
    '''snapshot files as of some revision
    if not using snapshot, -I/-X does not work and recursive diff
    in tools like kdiff3 and meld displays too many files.'''
    dirname = os.path.basename(repo.root)
    if dirname == "":
        dirname = "root"
    if node is not None:
        dirname = '%s.%s' % (dirname, short(node))
    base = os.path.join(tmproot, dirname)
    os.mkdir(base)
    if node is not None:
        ui.note(_('making snapshot of %d files from rev %s\n') %
                (len(files), short(node)))
    else:
        ui.note(_('making snapshot of %d files from working directory\n') %
            (len(files)))
    wopener = util.opener(base)
    fns_and_mtime = []
    ctx = repo[node]
    for fn in files:
        wfn = util.pconvert(fn)
        if not wfn in ctx:
            # File doesn't exist; could be a bogus modify
            continue
        ui.note('  %s\n' % wfn)
        dest = os.path.join(base, wfn)
        fctx = ctx[wfn]
        data = repo.wwritedata(wfn, fctx.data())
        if 'l' in fctx.flags():
            wopener.symlink(data, wfn)
        else:
            wopener(wfn, 'w').write(data)
            if 'x' in fctx.flags():
                util.set_flags(dest, False, True)
        if node is None:
            fns_and_mtime.append((dest, repo.wjoin(fn), os.path.getmtime(dest)))
    return dirname, fns_and_mtime

def dodiff(ui, repo, diffcmd, diffopts, pats, opts):
    '''Do the actuall diff:

    - copy to a temp structure if diffing 2 internal revisions
    - copy to a temp structure if diffing working revision with
      another one and more than 1 file is changed
    - just invoke the diff for a single file in the working dir
    '''

    revs = opts.get('rev')
    change = opts.get('change')
    args = ' '.join(diffopts)
    do3way = '$parent2' in args

    if revs and change:
        msg = _('cannot specify --rev and --change at the same time')
        raise util.Abort(msg)
    elif change:
        node2 = repo.lookup(change)
        node1a, node1b = repo.changelog.parents(node2)
    else:
        node1a, node2 = cmdutil.revpair(repo, revs)
        if not revs:
            node1b = repo.dirstate.parents()[1]
        else:
            node1b = nullid

    # Disable 3-way merge if there is only one parent
    if do3way:
        if node1b == nullid:
            do3way = False

    matcher = cmdutil.match(repo, pats, opts)
    mod_a, add_a, rem_a = map(set, repo.status(node1a, node2, matcher)[:3])
    if do3way:
        mod_b, add_b, rem_b = map(set, repo.status(node1b, node2, matcher)[:3])
    else:
        mod_b, add_b, rem_b = set(), set(), set()
    modadd = mod_a | add_a | mod_b | add_b
    common = modadd | rem_a | rem_b
    if not common:
        return 0

    tmproot = tempfile.mkdtemp(prefix='extdiff.')
    try:
        # Always make a copy of node1a (and node1b, if applicable)
        dir1a_files = mod_a | rem_a | ((mod_b | add_b) - add_a)
        dir1a = snapshot(ui, repo, dir1a_files, node1a, tmproot)[0]
        if do3way:
            dir1b_files = mod_b | rem_b | ((mod_a | add_a) - add_b)
            dir1b = snapshot(ui, repo, dir1b_files, node1b, tmproot)[0]
        else:
            dir1b = None

        fns_and_mtime = []

        # If node2 in not the wc or there is >1 change, copy it
        dir2root = ''
        if node2:
            dir2 = snapshot(ui, repo, modadd, node2, tmproot)[0]
        elif len(common) > 1:
            #we only actually need to get the files to copy back to
            #the working dir in this case (because the other cases
            #are: diffing 2 revisions or single file -- in which case
            #the file is already directly passed to the diff tool).
            dir2, fns_and_mtime = snapshot(ui, repo, modadd, None, tmproot)
        else:
            # This lets the diff tool open the changed file directly
            dir2 = ''
            dir2root = repo.root

        # If only one change, diff the files instead of the directories
        # Handle bogus modifies correctly by checking if the files exist
        if len(common) == 1:
            common_file = util.localpath(common.pop())
            dir1a = os.path.join(dir1a, common_file)
            if not os.path.isfile(os.path.join(tmproot, dir1a)):
                dir1a = os.devnull
            if do3way:
                dir1b = os.path.join(dir1b, common_file)
                if not os.path.isfile(os.path.join(tmproot, dir1b)):
                    dir1b = os.devnull
            dir2 = os.path.join(dir2root, dir2, common_file)

        # Function to quote file/dir names in the argument string.
        # When not operating in 3-way mode, an empty string is
        # returned for parent2
        replace = dict(parent=dir1a, parent1=dir1a, parent2=dir1b, child=dir2)
        def quote(match):
            key = match.group()[1:]
            if not do3way and key == 'parent2':
                return ''
            return util.shellquote(replace[key])

        # Match parent2 first, so 'parent1?' will match both parent1 and parent
        regex = '\$(parent2|parent1?|child)'
        if not do3way and not re.search(regex, args):
            args += ' $parent1 $child'
        args = re.sub(regex, quote, args)
        cmdline = util.shellquote(diffcmd) + ' ' + args

        ui.debug('running %r in %s\n' % (cmdline, tmproot))
        util.system(cmdline, cwd=tmproot)

        for copy_fn, working_fn, mtime in fns_and_mtime:
            if os.path.getmtime(copy_fn) != mtime:
                ui.debug('file changed while diffing. '
                         'Overwriting: %s (src: %s)\n' % (working_fn, copy_fn))
                util.copyfile(copy_fn, working_fn)

        return 1
    finally:
        ui.note(_('cleaning up temp directory\n'))
        shutil.rmtree(tmproot)

def extdiff(ui, repo, *pats, **opts):
    '''use external program to diff repository (or selected files)

    Show differences between revisions for the specified files, using
    an external program. The default program used is diff, with
    default options "-Npru".

    To select a different program, use the -p/--program option. The
    program will be passed the names of two directories to compare. To
    pass additional options to the program, use -o/--option. These
    will be passed before the names of the directories to compare.

    When two revision arguments are given, then changes are shown
    between those revisions. If only one revision is specified then
    that revision is compared to the working directory, and, when no
    revisions are specified, the working directory files are compared
    to its parent.'''
    program = opts.get('program')
    option = opts.get('option')
    if not program:
        program = 'diff'
        option = option or ['-Npru']
    return dodiff(ui, repo, program, option, pats, opts)

cmdtable = {
    "extdiff":
    (extdiff,
     [('p', 'program', '', _('comparison program to run')),
      ('o', 'option', [], _('pass option to comparison program')),
      ('r', 'rev', [], _('revision')),
      ('c', 'change', '', _('change made by revision')),
     ] + commands.walkopts,
     _('hg extdiff [OPT]... [FILE]...')),
    }

def uisetup(ui):
    for cmd, path in ui.configitems('extdiff'):
        if cmd.startswith('cmd.'):
            cmd = cmd[4:]
            if not path:
                path = cmd
            diffopts = ui.config('extdiff', 'opts.' + cmd, '')
            diffopts = diffopts and [diffopts] or []
        elif cmd.startswith('opts.'):
            continue
        else:
            # command = path opts
            if path:
                diffopts = shlex.split(path)
                path = diffopts.pop(0)
            else:
                path, diffopts = cmd, []
        def save(cmd, path, diffopts):
            '''use closure to save diff command to use'''
            def mydiff(ui, repo, *pats, **opts):
                return dodiff(ui, repo, path, diffopts + opts['option'],
                              pats, opts)
            doc = _('''\
use %(path)s to diff repository (or selected files)

    Show differences between revisions for the specified files, using
    the %(path)s program.

    When two revision arguments are given, then changes are shown
    between those revisions. If only one revision is specified then
    that revision is compared to the working directory, and, when no
    revisions are specified, the working directory files are compared
    to its parent.\
''') % dict(path=util.uirepr(path))

            # We must translate the docstring right away since it is
            # used as a format string. The string will unfortunately
            # be translated again in commands.helpcmd and this will
            # fail when the docstring contains non-ASCII characters.
            # Decoding the string to a Unicode string here (using the
            # right encoding) prevents that.
            mydiff.__doc__ = doc.decode(encoding.encoding)
            return mydiff
        cmdtable[cmd] = (save(cmd, path, diffopts),
                         cmdtable['extdiff'][1][1:],
                         _('hg %s [OPTION]... [FILE]...') % cmd)
