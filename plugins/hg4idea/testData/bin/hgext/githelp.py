# githelp.py - Try to map Git commands to Mercurial equivalents.
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""try mapping git commands to Mercurial commands

Tries to map a given git command to a Mercurial command:

  $ hg githelp -- git checkout master
  hg update master

If an unknown command or parameter combination is detected, an error is
produced.
"""

from __future__ import absolute_import

import getopt
import re

from mercurial.i18n import _
from mercurial import (
    encoding,
    error,
    fancyopts,
    pycompat,
    registrar,
    scmutil,
)
from mercurial.utils import procutil

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = {}
command = registrar.command(cmdtable)


def convert(s):
    if s.startswith(b"origin/"):
        return s[7:]
    if b'HEAD' in s:
        s = s.replace(b'HEAD', b'.')
    # HEAD~ in git is .~1 in mercurial
    s = re.sub(b'~$', b'~1', s)
    return s


@command(
    b'githelp|git',
    [],
    _(b'hg githelp'),
    helpcategory=command.CATEGORY_HELP,
    helpbasic=True,
)
def githelp(ui, repo, *args, **kwargs):
    """suggests the Mercurial equivalent of the given git command

    Usage: hg githelp -- <git command>
    """

    if len(args) == 0 or (len(args) == 1 and args[0] == b'git'):
        raise error.Abort(
            _(b'missing git command - usage: hg githelp -- <git command>')
        )

    if args[0] == b'git':
        args = args[1:]

    cmd = args[0]
    if not cmd in gitcommands:
        raise error.Abort(_(b"error: unknown git command %s") % cmd)

    ui.pager(b'githelp')
    args = args[1:]
    return gitcommands[cmd](ui, repo, *args, **kwargs)


def parseoptions(ui, cmdoptions, args):
    cmdoptions = list(cmdoptions)
    opts = {}
    args = list(args)
    while True:
        try:
            args = fancyopts.fancyopts(list(args), cmdoptions, opts, True)
            break
        except getopt.GetoptError as ex:
            if "requires argument" in ex.msg:
                raise
            if ('--' + ex.opt) in ex.msg:
                flag = b'--' + pycompat.bytestr(ex.opt)
            elif ('-' + ex.opt) in ex.msg:
                flag = b'-' + pycompat.bytestr(ex.opt)
            else:
                raise error.Abort(
                    _(b"unknown option %s") % pycompat.bytestr(ex.opt)
                )
            try:
                args.remove(flag)
            except Exception:
                msg = _(b"unknown option '%s' packed with other options")
                hint = _(b"please try passing the option as its own flag: -%s")
                raise error.Abort(
                    msg % pycompat.bytestr(ex.opt),
                    hint=hint % pycompat.bytestr(ex.opt),
                )

            ui.warn(_(b"ignoring unknown option %s\n") % flag)

    args = list([convert(x) for x in args])
    opts = dict(
        [
            (k, convert(v)) if isinstance(v, bytes) else (k, v)
            for k, v in pycompat.iteritems(opts)
        ]
    )

    return args, opts


class Command(object):
    def __init__(self, name):
        self.name = name
        self.args = []
        self.opts = {}

    def __bytes__(self):
        cmd = b"hg " + self.name
        if self.opts:
            for k, values in sorted(pycompat.iteritems(self.opts)):
                for v in values:
                    if v:
                        if isinstance(v, int):
                            fmt = b' %s %d'
                        else:
                            fmt = b' %s %s'

                        cmd += fmt % (k, v)
                    else:
                        cmd += b" %s" % (k,)
        if self.args:
            cmd += b" "
            cmd += b" ".join(self.args)
        return cmd

    __str__ = encoding.strmethod(__bytes__)

    def append(self, value):
        self.args.append(value)

    def extend(self, values):
        self.args.extend(values)

    def __setitem__(self, key, value):
        values = self.opts.setdefault(key, [])
        values.append(value)

    def __and__(self, other):
        return AndCommand(self, other)


class AndCommand(object):
    def __init__(self, left, right):
        self.left = left
        self.right = right

    def __str__(self):
        return b"%s && %s" % (self.left, self.right)

    def __and__(self, other):
        return AndCommand(self, other)


def add(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'A', b'all', None, b''),
        (b'p', b'patch', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    if opts.get(b'patch'):
        ui.status(
            _(
                b"note: Mercurial will commit when complete, "
                b"as there is no staging area in Mercurial\n\n"
            )
        )
        cmd = Command(b'commit --interactive')
    else:
        cmd = Command(b"add")

        if not opts.get(b'all'):
            cmd.extend(args)
        else:
            ui.status(
                _(
                    b"note: use hg addremove to remove files that have "
                    b"been deleted\n\n"
                )
            )

    ui.status((bytes(cmd)), b"\n")


def am(ui, repo, *args, **kwargs):
    cmdoptions = []
    parseoptions(ui, cmdoptions, args)
    cmd = Command(b'import')
    ui.status(bytes(cmd), b"\n")


def apply(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'p', b'p', int, b''),
        (b'', b'directory', b'', b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'import --no-commit')
    if opts.get(b'p'):
        cmd[b'-p'] = opts.get(b'p')
    if opts.get(b'directory'):
        cmd[b'--prefix'] = opts.get(b'directory')
    cmd.extend(args)

    ui.status((bytes(cmd)), b"\n")


def bisect(ui, repo, *args, **kwargs):
    ui.status(_(b"see 'hg help bisect' for how to use bisect\n\n"))


def blame(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)
    cmd = Command(b'annotate -udl')
    cmd.extend([convert(v) for v in args])
    ui.status((bytes(cmd)), b"\n")


def branch(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'set-upstream', None, b''),
        (b'', b'set-upstream-to', b'', b''),
        (b'd', b'delete', None, b''),
        (b'D', b'delete', None, b''),
        (b'm', b'move', None, b''),
        (b'M', b'move', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b"bookmark")

    if opts.get(b'set_upstream') or opts.get(b'set_upstream_to'):
        ui.status(_(b"Mercurial has no concept of upstream branches\n"))
        return
    elif opts.get(b'delete'):
        cmd = Command(b"strip")
        for branch in args:
            cmd[b'-B'] = branch
        else:
            cmd[b'-B'] = None
    elif opts.get(b'move'):
        if len(args) > 0:
            if len(args) > 1:
                old = args.pop(0)
            else:
                # shell command to output the active bookmark for the active
                # revision
                old = b'`hg log -T"{activebookmark}" -r .`'
        else:
            raise error.Abort(_(b'missing newbranch argument'))
        new = args[0]
        cmd[b'-m'] = old
        cmd.append(new)
    else:
        if len(args) > 1:
            cmd[b'-r'] = args[1]
            cmd.append(args[0])
        elif len(args) == 1:
            cmd.append(args[0])
    ui.status((bytes(cmd)), b"\n")


def ispath(repo, string):
    """
    The first argument to git checkout can either be a revision or a path. Let's
    generally assume it's a revision, unless it's obviously a path. There are
    too many ways to spell revisions in git for us to reasonably catch all of
    them, so let's be conservative.
    """
    if scmutil.isrevsymbol(repo, string):
        # if it's definitely a revision let's not even check if a file of the
        # same name exists.
        return False

    cwd = repo.getcwd()
    if cwd == b'':
        repopath = string
    else:
        repopath = cwd + b'/' + string

    exists = repo.wvfs.exists(repopath)
    if exists:
        return True

    manifest = repo[b'.'].manifest()

    didexist = (repopath in manifest) or manifest.hasdir(repopath)

    return didexist


def checkout(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'b', b'branch', b'', b''),
        (b'B', b'branch', b'', b''),
        (b'f', b'force', None, b''),
        (b'p', b'patch', None, b''),
    ]
    paths = []
    if b'--' in args:
        sepindex = args.index(b'--')
        paths.extend(args[sepindex + 1 :])
        args = args[:sepindex]

    args, opts = parseoptions(ui, cmdoptions, args)

    rev = None
    if args and ispath(repo, args[0]):
        paths = args + paths
    elif args:
        rev = args[0]
        paths = args[1:] + paths

    cmd = Command(b'update')

    if opts.get(b'force'):
        if paths or rev:
            cmd[b'-C'] = None

    if opts.get(b'patch'):
        cmd = Command(b'revert')
        cmd[b'-i'] = None

    if opts.get(b'branch'):
        if len(args) == 0:
            cmd = Command(b'bookmark')
            cmd.append(opts.get(b'branch'))
        else:
            cmd.append(args[0])
            bookcmd = Command(b'bookmark')
            bookcmd.append(opts.get(b'branch'))
            cmd = cmd & bookcmd
    # if there is any path argument supplied, use revert instead of update
    elif len(paths) > 0:
        ui.status(_(b"note: use --no-backup to avoid creating .orig files\n\n"))
        cmd = Command(b'revert')
        if opts.get(b'patch'):
            cmd[b'-i'] = None
        if rev:
            cmd[b'-r'] = rev
        cmd.extend(paths)
    elif rev:
        if opts.get(b'patch'):
            cmd[b'-r'] = rev
        else:
            cmd.append(rev)
    elif opts.get(b'force'):
        cmd = Command(b'revert')
        cmd[b'--all'] = None
    else:
        raise error.Abort(_(b"a commit must be specified"))

    ui.status((bytes(cmd)), b"\n")


def cherrypick(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'continue', None, b''),
        (b'', b'abort', None, b''),
        (b'e', b'edit', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'graft')

    if opts.get(b'edit'):
        cmd[b'--edit'] = None
    if opts.get(b'continue'):
        cmd[b'--continue'] = None
    elif opts.get(b'abort'):
        ui.status(_(b"note: hg graft does not have --abort\n\n"))
        return
    else:
        cmd.extend(args)

    ui.status((bytes(cmd)), b"\n")


def clean(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'd', b'd', None, b''),
        (b'f', b'force', None, b''),
        (b'x', b'x', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'purge')
    if opts.get(b'x'):
        cmd[b'--all'] = None
    cmd.extend(args)

    ui.status((bytes(cmd)), b"\n")


def clone(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'bare', None, b''),
        (b'n', b'no-checkout', None, b''),
        (b'b', b'branch', b'', b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    if len(args) == 0:
        raise error.Abort(_(b"a repository to clone must be specified"))

    cmd = Command(b'clone')
    cmd.append(args[0])
    if len(args) > 1:
        cmd.append(args[1])

    if opts.get(b'bare'):
        cmd[b'-U'] = None
        ui.status(
            _(
                b"note: Mercurial does not have bare clones. "
                b"-U will clone the repo without checking out a commit\n\n"
            )
        )
    elif opts.get(b'no_checkout'):
        cmd[b'-U'] = None

    if opts.get(b'branch'):
        cocmd = Command(b"update")
        cocmd.append(opts.get(b'branch'))
        cmd = cmd & cocmd

    ui.status((bytes(cmd)), b"\n")


def commit(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'a', b'all', None, b''),
        (b'm', b'message', b'', b''),
        (b'p', b'patch', None, b''),
        (b'C', b'reuse-message', b'', b''),
        (b'F', b'file', b'', b''),
        (b'', b'author', b'', b''),
        (b'', b'date', b'', b''),
        (b'', b'amend', None, b''),
        (b'', b'no-edit', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'commit')
    if opts.get(b'patch'):
        cmd = Command(b'commit --interactive')

    if opts.get(b'amend'):
        if opts.get(b'no_edit'):
            cmd = Command(b'amend')
        else:
            cmd[b'--amend'] = None

    if opts.get(b'reuse_message'):
        cmd[b'-M'] = opts.get(b'reuse_message')

    if opts.get(b'message'):
        cmd[b'-m'] = b"'%s'" % (opts.get(b'message'),)

    if opts.get(b'all'):
        ui.status(
            _(
                b"note: Mercurial doesn't have a staging area, "
                b"so there is no --all. -A will add and remove files "
                b"for you though.\n\n"
            )
        )

    if opts.get(b'file'):
        cmd[b'-l'] = opts.get(b'file')

    if opts.get(b'author'):
        cmd[b'-u'] = opts.get(b'author')

    if opts.get(b'date'):
        cmd[b'-d'] = opts.get(b'date')

    cmd.extend(args)

    ui.status((bytes(cmd)), b"\n")


def deprecated(ui, repo, *args, **kwargs):
    ui.warn(
        _(
            b'this command has been deprecated in the git project, '
            b'thus isn\'t supported by this tool\n\n'
        )
    )


def diff(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'a', b'all', None, b''),
        (b'', b'cached', None, b''),
        (b'R', b'reverse', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'diff')

    if opts.get(b'cached'):
        ui.status(
            _(
                b'note: Mercurial has no concept of a staging area, '
                b'so --cached does nothing\n\n'
            )
        )

    if opts.get(b'reverse'):
        cmd[b'--reverse'] = None

    for a in list(args):
        args.remove(a)
        try:
            repo.revs(a)
            cmd[b'-r'] = a
        except Exception:
            cmd.append(a)

    ui.status((bytes(cmd)), b"\n")


def difftool(ui, repo, *args, **kwargs):
    ui.status(
        _(
            b'Mercurial does not enable external difftool by default. You '
            b'need to enable the extdiff extension in your .hgrc file by adding\n'
            b'extdiff =\n'
            b'to the [extensions] section and then running\n\n'
            b'hg extdiff -p <program>\n\n'
            b'See \'hg help extdiff\' and \'hg help -e extdiff\' for more '
            b'information.\n'
        )
    )


def fetch(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'all', None, b''),
        (b'f', b'force', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'pull')

    if len(args) > 0:
        cmd.append(args[0])
        if len(args) > 1:
            ui.status(
                _(
                    b"note: Mercurial doesn't have refspecs. "
                    b"-r can be used to specify which commits you want to "
                    b"pull. -B can be used to specify which bookmark you "
                    b"want to pull.\n\n"
                )
            )
            for v in args[1:]:
                if v in repo._bookmarks:
                    cmd[b'-B'] = v
                else:
                    cmd[b'-r'] = v

    ui.status((bytes(cmd)), b"\n")


def grep(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'grep')

    # For basic usage, git grep and hg grep are the same. They both have the
    # pattern first, followed by paths.
    cmd.extend(args)

    ui.status((bytes(cmd)), b"\n")


def init(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'init')

    if len(args) > 0:
        cmd.append(args[0])

    ui.status((bytes(cmd)), b"\n")


def log(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'follow', None, b''),
        (b'', b'decorate', None, b''),
        (b'n', b'number', b'', b''),
        (b'1', b'1', None, b''),
        (b'', b'pretty', b'', b''),
        (b'', b'format', b'', b''),
        (b'', b'oneline', None, b''),
        (b'', b'stat', None, b''),
        (b'', b'graph', None, b''),
        (b'p', b'patch', None, b''),
        (b'G', b'grep-diff', b'', b''),
        (b'S', b'pickaxe-regex', b'', b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)
    grep_pat = opts.get(b'grep_diff') or opts.get(b'pickaxe_regex')
    if grep_pat:
        cmd = Command(b'grep')
        cmd[b'--diff'] = grep_pat
        ui.status(b'%s\n' % bytes(cmd))
        return

    ui.status(
        _(
            b'note: -v prints the entire commit message like Git does. To '
            b'print just the first line, drop the -v.\n\n'
        )
    )
    ui.status(
        _(
            b"note: see hg help revset for information on how to filter "
            b"log output\n\n"
        )
    )

    cmd = Command(b'log')
    cmd[b'-v'] = None

    if opts.get(b'number'):
        cmd[b'-l'] = opts.get(b'number')
    if opts.get(b'1'):
        cmd[b'-l'] = b'1'
    if opts.get(b'stat'):
        cmd[b'--stat'] = None
    if opts.get(b'graph'):
        cmd[b'-G'] = None
    if opts.get(b'patch'):
        cmd[b'-p'] = None

    if opts.get(b'pretty') or opts.get(b'format') or opts.get(b'oneline'):
        format = opts.get(b'format', b'')
        if b'format:' in format:
            ui.status(
                _(
                    b"note: --format format:??? equates to Mercurial's "
                    b"--template. See hg help templates for more info.\n\n"
                )
            )
            cmd[b'--template'] = b'???'
        else:
            ui.status(
                _(
                    b"note: --pretty/format/oneline equate to Mercurial's "
                    b"--style or --template. See hg help templates for "
                    b"more info.\n\n"
                )
            )
            cmd[b'--style'] = b'???'

    if len(args) > 0:
        if b'..' in args[0]:
            since, until = args[0].split(b'..')
            cmd[b'-r'] = b"'%s::%s'" % (since, until)
            del args[0]
        cmd.extend(args)

    ui.status((bytes(cmd)), b"\n")


def lsfiles(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'c', b'cached', None, b''),
        (b'd', b'deleted', None, b''),
        (b'm', b'modified', None, b''),
        (b'o', b'others', None, b''),
        (b'i', b'ignored', None, b''),
        (b's', b'stage', None, b''),
        (b'z', b'_zero', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    if (
        opts.get(b'modified')
        or opts.get(b'deleted')
        or opts.get(b'others')
        or opts.get(b'ignored')
    ):
        cmd = Command(b'status')
        if opts.get(b'deleted'):
            cmd[b'-d'] = None
        if opts.get(b'modified'):
            cmd[b'-m'] = None
        if opts.get(b'others'):
            cmd[b'-o'] = None
        if opts.get(b'ignored'):
            cmd[b'-i'] = None
    else:
        cmd = Command(b'files')
    if opts.get(b'stage'):
        ui.status(
            _(
                b"note: Mercurial doesn't have a staging area, ignoring "
                b"--stage\n"
            )
        )
    if opts.get(b'_zero'):
        cmd[b'-0'] = None
    cmd.append(b'.')
    for include in args:
        cmd[b'-I'] = procutil.shellquote(include)

    ui.status((bytes(cmd)), b"\n")


def merge(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'merge')

    if len(args) > 0:
        cmd.append(args[len(args) - 1])

    ui.status((bytes(cmd)), b"\n")


def mergebase(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)

    if len(args) != 2:
        args = [b'A', b'B']

    cmd = Command(
        b"log -T '{node}\\n' -r 'ancestor(%s,%s)'" % (args[0], args[1])
    )

    ui.status(
        _(b'note: ancestors() is part of the revset language\n'),
        _(b"(learn more about revsets with 'hg help revsets')\n\n"),
    )
    ui.status((bytes(cmd)), b"\n")


def mergetool(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b"resolve")

    if len(args) == 0:
        cmd[b'--all'] = None
    cmd.extend(args)
    ui.status((bytes(cmd)), b"\n")


def mv(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'f', b'force', None, b''),
        (b'n', b'dry-run', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'mv')
    cmd.extend(args)

    if opts.get(b'force'):
        cmd[b'-f'] = None
    if opts.get(b'dry_run'):
        cmd[b'-n'] = None

    ui.status((bytes(cmd)), b"\n")


def pull(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'all', None, b''),
        (b'f', b'force', None, b''),
        (b'r', b'rebase', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'pull')
    cmd[b'--rebase'] = None

    if len(args) > 0:
        cmd.append(args[0])
        if len(args) > 1:
            ui.status(
                _(
                    b"note: Mercurial doesn't have refspecs. "
                    b"-r can be used to specify which commits you want to "
                    b"pull. -B can be used to specify which bookmark you "
                    b"want to pull.\n\n"
                )
            )
            for v in args[1:]:
                if v in repo._bookmarks:
                    cmd[b'-B'] = v
                else:
                    cmd[b'-r'] = v

    ui.status((bytes(cmd)), b"\n")


def push(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'all', None, b''),
        (b'f', b'force', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'push')

    if len(args) > 0:
        cmd.append(args[0])
        if len(args) > 1:
            ui.status(
                _(
                    b"note: Mercurial doesn't have refspecs. "
                    b"-r can be used to specify which commits you want "
                    b"to push. -B can be used to specify which bookmark "
                    b"you want to push.\n\n"
                )
            )
            for v in args[1:]:
                if v in repo._bookmarks:
                    cmd[b'-B'] = v
                else:
                    cmd[b'-r'] = v

    if opts.get(b'force'):
        cmd[b'-f'] = None

    ui.status((bytes(cmd)), b"\n")


def rebase(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'all', None, b''),
        (b'i', b'interactive', None, b''),
        (b'', b'onto', b'', b''),
        (b'', b'abort', None, b''),
        (b'', b'continue', None, b''),
        (b'', b'skip', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    if opts.get(b'interactive'):
        ui.status(
            _(
                b"note: hg histedit does not perform a rebase. "
                b"It just edits history.\n\n"
            )
        )
        cmd = Command(b'histedit')
        if len(args) > 0:
            ui.status(
                _(
                    b"also note: 'hg histedit' will automatically detect"
                    b" your stack, so no second argument is necessary\n\n"
                )
            )
        ui.status((bytes(cmd)), b"\n")
        return

    if opts.get(b'skip'):
        cmd = Command(b'revert --all -r .')
        ui.status((bytes(cmd)), b"\n")

    cmd = Command(b'rebase')

    if opts.get(b'continue') or opts.get(b'skip'):
        cmd[b'--continue'] = None
    if opts.get(b'abort'):
        cmd[b'--abort'] = None

    if opts.get(b'onto'):
        ui.status(
            _(
                b"note: if you're trying to lift a commit off one branch, "
                b"try hg rebase -d <destination commit> -s <commit to be "
                b"lifted>\n\n"
            )
        )
        cmd[b'-d'] = convert(opts.get(b'onto'))
        if len(args) < 2:
            raise error.Abort(_(b"expected format: git rebase --onto X Y Z"))
        cmd[b'-s'] = b"'::%s - ::%s'" % (convert(args[1]), convert(args[0]))
    else:
        if len(args) == 1:
            cmd[b'-d'] = convert(args[0])
        elif len(args) == 2:
            cmd[b'-d'] = convert(args[0])
            cmd[b'-b'] = convert(args[1])

    ui.status((bytes(cmd)), b"\n")


def reflog(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'all', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'journal')
    if opts.get(b'all'):
        cmd[b'--all'] = None
    if len(args) > 0:
        cmd.append(args[0])

    ui.status(bytes(cmd), b"\n\n")
    ui.status(
        _(
            b"note: in hg commits can be deleted from repo but we always"
            b" have backups\n"
        )
    )


def reset(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'soft', None, b''),
        (b'', b'hard', None, b''),
        (b'', b'mixed', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    commit = convert(args[0] if len(args) > 0 else b'.')
    hard = opts.get(b'hard')

    if opts.get(b'mixed'):
        ui.status(
            _(
                b'note: --mixed has no meaning since Mercurial has no '
                b'staging area\n\n'
            )
        )
    if opts.get(b'soft'):
        ui.status(
            _(
                b'note: --soft has no meaning since Mercurial has no '
                b'staging area\n\n'
            )
        )

    cmd = Command(b'update')
    if hard:
        cmd.append(b'--clean')

    cmd.append(commit)

    ui.status((bytes(cmd)), b"\n")


def revert(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)

    if len(args) > 1:
        ui.status(
            _(
                b"note: hg backout doesn't support multiple commits at "
                b"once\n\n"
            )
        )

    cmd = Command(b'backout')
    if args:
        cmd.append(args[0])

    ui.status((bytes(cmd)), b"\n")


def revparse(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'show-cdup', None, b''),
        (b'', b'show-toplevel', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    if opts.get(b'show_cdup') or opts.get(b'show_toplevel'):
        cmd = Command(b'root')
        if opts.get(b'show_cdup'):
            ui.status(_(b"note: hg root prints the root of the repository\n\n"))
        ui.status((bytes(cmd)), b"\n")
    else:
        ui.status(_(b"note: see hg help revset for how to refer to commits\n"))


def rm(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'f', b'force', None, b''),
        (b'n', b'dry-run', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'rm')
    cmd.extend(args)

    if opts.get(b'force'):
        cmd[b'-f'] = None
    if opts.get(b'dry_run'):
        cmd[b'-n'] = None

    ui.status((bytes(cmd)), b"\n")


def show(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'name-status', None, b''),
        (b'', b'pretty', b'', b''),
        (b'U', b'unified', int, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    if opts.get(b'name_status'):
        if opts.get(b'pretty') == b'format:':
            cmd = Command(b'status')
            cmd[b'--change'] = b'.'
        else:
            cmd = Command(b'log')
            cmd.append(b'--style status')
            cmd.append(b'-r .')
    elif len(args) > 0:
        if ispath(repo, args[0]):
            cmd = Command(b'cat')
        else:
            cmd = Command(b'export')
        cmd.extend(args)
        if opts.get(b'unified'):
            cmd.append(b'--config diff.unified=%d' % (opts[b'unified'],))
    elif opts.get(b'unified'):
        cmd = Command(b'export')
        cmd.append(b'--config diff.unified=%d' % (opts[b'unified'],))
    else:
        cmd = Command(b'export')

    ui.status((bytes(cmd)), b"\n")


def stash(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'p', b'patch', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'shelve')
    action = args[0] if len(args) > 0 else None

    if action == b'list':
        cmd[b'-l'] = None
        if opts.get(b'patch'):
            cmd[b'-p'] = None
    elif action == b'show':
        if opts.get(b'patch'):
            cmd[b'-p'] = None
        else:
            cmd[b'--stat'] = None
        if len(args) > 1:
            cmd.append(args[1])
    elif action == b'clear':
        cmd[b'--cleanup'] = None
    elif action == b'drop':
        cmd[b'-d'] = None
        if len(args) > 1:
            cmd.append(args[1])
        else:
            cmd.append(b'<shelve name>')
    elif action == b'pop' or action == b'apply':
        cmd = Command(b'unshelve')
        if len(args) > 1:
            cmd.append(args[1])
        if action == b'apply':
            cmd[b'--keep'] = None
    elif action == b'branch' or action == b'create':
        ui.status(
            _(
                b"note: Mercurial doesn't have equivalents to the "
                b"git stash branch or create actions\n\n"
            )
        )
        return
    else:
        if len(args) > 0:
            if args[0] != b'save':
                cmd[b'--name'] = args[0]
            elif len(args) > 1:
                cmd[b'--name'] = args[1]

    ui.status((bytes(cmd)), b"\n")


def status(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'', b'ignored', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    cmd = Command(b'status')
    cmd.extend(args)

    if opts.get(b'ignored'):
        cmd[b'-i'] = None

    ui.status((bytes(cmd)), b"\n")


def svn(ui, repo, *args, **kwargs):
    if not args:
        raise error.Abort(_(b'missing svn command'))
    svncmd = args[0]
    if svncmd not in gitsvncommands:
        raise error.Abort(_(b'unknown git svn command "%s"') % svncmd)

    args = args[1:]
    return gitsvncommands[svncmd](ui, repo, *args, **kwargs)


def svndcommit(ui, repo, *args, **kwargs):
    cmdoptions = []
    parseoptions(ui, cmdoptions, args)

    cmd = Command(b'push')

    ui.status((bytes(cmd)), b"\n")


def svnfetch(ui, repo, *args, **kwargs):
    cmdoptions = []
    parseoptions(ui, cmdoptions, args)

    cmd = Command(b'pull')
    cmd.append(b'default-push')

    ui.status((bytes(cmd)), b"\n")


def svnfindrev(ui, repo, *args, **kwargs):
    cmdoptions = []
    args, opts = parseoptions(ui, cmdoptions, args)

    if not args:
        raise error.Abort(_(b'missing find-rev argument'))

    cmd = Command(b'log')
    cmd[b'-r'] = args[0]

    ui.status((bytes(cmd)), b"\n")


def svnrebase(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'l', b'local', None, b''),
    ]
    parseoptions(ui, cmdoptions, args)

    pullcmd = Command(b'pull')
    pullcmd.append(b'default-push')
    rebasecmd = Command(b'rebase')
    rebasecmd.append(b'tip')

    cmd = pullcmd & rebasecmd

    ui.status((bytes(cmd)), b"\n")


def tag(ui, repo, *args, **kwargs):
    cmdoptions = [
        (b'f', b'force', None, b''),
        (b'l', b'list', None, b''),
        (b'd', b'delete', None, b''),
    ]
    args, opts = parseoptions(ui, cmdoptions, args)

    if opts.get(b'list'):
        cmd = Command(b'tags')
    else:
        cmd = Command(b'tag')

        if not args:
            raise error.Abort(_(b'missing tag argument'))

        cmd.append(args[0])
        if len(args) > 1:
            cmd[b'-r'] = args[1]

        if opts.get(b'delete'):
            cmd[b'--remove'] = None

        if opts.get(b'force'):
            cmd[b'-f'] = None

    ui.status((bytes(cmd)), b"\n")


gitcommands = {
    b'add': add,
    b'am': am,
    b'apply': apply,
    b'bisect': bisect,
    b'blame': blame,
    b'branch': branch,
    b'checkout': checkout,
    b'cherry-pick': cherrypick,
    b'clean': clean,
    b'clone': clone,
    b'commit': commit,
    b'diff': diff,
    b'difftool': difftool,
    b'fetch': fetch,
    b'grep': grep,
    b'init': init,
    b'log': log,
    b'ls-files': lsfiles,
    b'merge': merge,
    b'merge-base': mergebase,
    b'mergetool': mergetool,
    b'mv': mv,
    b'pull': pull,
    b'push': push,
    b'rebase': rebase,
    b'reflog': reflog,
    b'reset': reset,
    b'revert': revert,
    b'rev-parse': revparse,
    b'rm': rm,
    b'show': show,
    b'stash': stash,
    b'status': status,
    b'svn': svn,
    b'tag': tag,
    b'whatchanged': deprecated,
}

gitsvncommands = {
    b'dcommit': svndcommit,
    b'fetch': svnfetch,
    b'find-rev': svnfindrev,
    b'rebase': svnrebase,
}
