# cmdutil.py - help for command processing in mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import copy as copymod
import errno
import os
import re

from .i18n import _
from .node import (
    hex,
    nullrev,
    short,
)
from .pycompat import (
    getattr,
    open,
    setattr,
)
from .thirdparty import attr

from . import (
    bookmarks,
    changelog,
    copies,
    crecord as crecordmod,
    dirstateguard,
    encoding,
    error,
    formatter,
    logcmdutil,
    match as matchmod,
    merge as mergemod,
    mergestate as mergestatemod,
    mergeutil,
    obsolete,
    patch,
    pathutil,
    phases,
    pycompat,
    repair,
    revlog,
    rewriteutil,
    scmutil,
    state as statemod,
    subrepoutil,
    templatekw,
    templater,
    util,
    vfs as vfsmod,
)

from .utils import (
    dateutil,
    stringutil,
)

from .revlogutils import (
    constants as revlog_constants,
)

if pycompat.TYPE_CHECKING:
    from typing import (
        Any,
        Dict,
    )

    for t in (Any, Dict):
        assert t

stringio = util.stringio

# templates of common command options

dryrunopts = [
    (b'n', b'dry-run', None, _(b'do not perform actions, just print output')),
]

confirmopts = [
    (b'', b'confirm', None, _(b'ask before applying actions')),
]

remoteopts = [
    (b'e', b'ssh', b'', _(b'specify ssh command to use'), _(b'CMD')),
    (
        b'',
        b'remotecmd',
        b'',
        _(b'specify hg command to run on the remote side'),
        _(b'CMD'),
    ),
    (
        b'',
        b'insecure',
        None,
        _(b'do not verify server certificate (ignoring web.cacerts config)'),
    ),
]

walkopts = [
    (
        b'I',
        b'include',
        [],
        _(b'include names matching the given patterns'),
        _(b'PATTERN'),
    ),
    (
        b'X',
        b'exclude',
        [],
        _(b'exclude names matching the given patterns'),
        _(b'PATTERN'),
    ),
]

commitopts = [
    (b'm', b'message', b'', _(b'use text as commit message'), _(b'TEXT')),
    (b'l', b'logfile', b'', _(b'read commit message from file'), _(b'FILE')),
]

commitopts2 = [
    (
        b'd',
        b'date',
        b'',
        _(b'record the specified date as commit date'),
        _(b'DATE'),
    ),
    (
        b'u',
        b'user',
        b'',
        _(b'record the specified user as committer'),
        _(b'USER'),
    ),
]

commitopts3 = [
    (b'D', b'currentdate', None, _(b'record the current date as commit date')),
    (b'U', b'currentuser', None, _(b'record the current user as committer')),
]

formatteropts = [
    (b'T', b'template', b'', _(b'display with template'), _(b'TEMPLATE')),
]

templateopts = [
    (
        b'',
        b'style',
        b'',
        _(b'display using template map file (DEPRECATED)'),
        _(b'STYLE'),
    ),
    (b'T', b'template', b'', _(b'display with template'), _(b'TEMPLATE')),
]

logopts = [
    (b'p', b'patch', None, _(b'show patch')),
    (b'g', b'git', None, _(b'use git extended diff format')),
    (b'l', b'limit', b'', _(b'limit number of changes displayed'), _(b'NUM')),
    (b'M', b'no-merges', None, _(b'do not show merges')),
    (b'', b'stat', None, _(b'output diffstat-style summary of changes')),
    (b'G', b'graph', None, _(b"show the revision DAG")),
] + templateopts

diffopts = [
    (b'a', b'text', None, _(b'treat all files as text')),
    (
        b'g',
        b'git',
        None,
        _(b'use git extended diff format (DEFAULT: diff.git)'),
    ),
    (b'', b'binary', None, _(b'generate binary diffs in git mode (default)')),
    (b'', b'nodates', None, _(b'omit dates from diff headers')),
]

diffwsopts = [
    (
        b'w',
        b'ignore-all-space',
        None,
        _(b'ignore white space when comparing lines'),
    ),
    (
        b'b',
        b'ignore-space-change',
        None,
        _(b'ignore changes in the amount of white space'),
    ),
    (
        b'B',
        b'ignore-blank-lines',
        None,
        _(b'ignore changes whose lines are all blank'),
    ),
    (
        b'Z',
        b'ignore-space-at-eol',
        None,
        _(b'ignore changes in whitespace at EOL'),
    ),
]

diffopts2 = (
    [
        (b'', b'noprefix', None, _(b'omit a/ and b/ prefixes from filenames')),
        (
            b'p',
            b'show-function',
            None,
            _(
                b'show which function each change is in (DEFAULT: diff.showfunc)'
            ),
        ),
        (b'', b'reverse', None, _(b'produce a diff that undoes the changes')),
    ]
    + diffwsopts
    + [
        (
            b'U',
            b'unified',
            b'',
            _(b'number of lines of context to show'),
            _(b'NUM'),
        ),
        (b'', b'stat', None, _(b'output diffstat-style summary of changes')),
        (
            b'',
            b'root',
            b'',
            _(b'produce diffs relative to subdirectory'),
            _(b'DIR'),
        ),
    ]
)

mergetoolopts = [
    (b't', b'tool', b'', _(b'specify merge tool'), _(b'TOOL')),
]

similarityopts = [
    (
        b's',
        b'similarity',
        b'',
        _(b'guess renamed files by similarity (0<=s<=100)'),
        _(b'SIMILARITY'),
    )
]

subrepoopts = [(b'S', b'subrepos', None, _(b'recurse into subrepositories'))]

debugrevlogopts = [
    (b'c', b'changelog', False, _(b'open changelog')),
    (b'm', b'manifest', False, _(b'open manifest')),
    (b'', b'dir', b'', _(b'open directory manifest')),
]

# special string such that everything below this line will be ingored in the
# editor text
_linebelow = b"^HG: ------------------------ >8 ------------------------$"


def check_at_most_one_arg(opts, *args):
    """abort if more than one of the arguments are in opts

    Returns the unique argument or None if none of them were specified.
    """

    def to_display(name):
        return pycompat.sysbytes(name).replace(b'_', b'-')

    previous = None
    for x in args:
        if opts.get(x):
            if previous:
                raise error.InputError(
                    _(b'cannot specify both --%s and --%s')
                    % (to_display(previous), to_display(x))
                )
            previous = x
    return previous


def check_incompatible_arguments(opts, first, others):
    """abort if the first argument is given along with any of the others

    Unlike check_at_most_one_arg(), `others` are not mutually exclusive
    among themselves, and they're passed as a single collection.
    """
    for other in others:
        check_at_most_one_arg(opts, first, other)


def resolve_commit_options(ui, opts):
    """modify commit options dict to handle related options

    The return value indicates that ``rewrite.update-timestamp`` is the reason
    the ``date`` option is set.
    """
    check_at_most_one_arg(opts, 'date', 'currentdate')
    check_at_most_one_arg(opts, 'user', 'currentuser')

    datemaydiffer = False  # date-only change should be ignored?

    if opts.get('currentdate'):
        opts['date'] = b'%d %d' % dateutil.makedate()
    elif (
        not opts.get('date')
        and ui.configbool(b'rewrite', b'update-timestamp')
        and opts.get('currentdate') is None
    ):
        opts['date'] = b'%d %d' % dateutil.makedate()
        datemaydiffer = True

    if opts.get('currentuser'):
        opts['user'] = ui.username()

    return datemaydiffer


def check_note_size(opts):
    """make sure note is of valid format"""

    note = opts.get('note')
    if not note:
        return

    if len(note) > 255:
        raise error.InputError(_(b"cannot store a note of more than 255 bytes"))
    if b'\n' in note:
        raise error.InputError(_(b"note cannot contain a newline"))


def ishunk(x):
    hunkclasses = (crecordmod.uihunk, patch.recordhunk)
    return isinstance(x, hunkclasses)


def isheader(x):
    headerclasses = (crecordmod.uiheader, patch.header)
    return isinstance(x, headerclasses)


def newandmodified(chunks):
    newlyaddedandmodifiedfiles = set()
    alsorestore = set()
    for chunk in chunks:
        if isheader(chunk) and chunk.isnewfile():
            newlyaddedandmodifiedfiles.add(chunk.filename())
            alsorestore.update(set(chunk.files()) - {chunk.filename()})
    return newlyaddedandmodifiedfiles, alsorestore


def parsealiases(cmd):
    base_aliases = cmd.split(b"|")
    all_aliases = set(base_aliases)
    extra_aliases = []
    for alias in base_aliases:
        if b'-' in alias:
            folded_alias = alias.replace(b'-', b'')
            if folded_alias not in all_aliases:
                all_aliases.add(folded_alias)
                extra_aliases.append(folded_alias)
    base_aliases.extend(extra_aliases)
    return base_aliases


def setupwrapcolorwrite(ui):
    # wrap ui.write so diff output can be labeled/colorized
    def wrapwrite(orig, *args, **kw):
        label = kw.pop('label', b'')
        for chunk, l in patch.difflabel(lambda: args):
            orig(chunk, label=label + l)

    oldwrite = ui.write

    def wrap(*args, **kwargs):
        return wrapwrite(oldwrite, *args, **kwargs)

    setattr(ui, 'write', wrap)
    return oldwrite


def filterchunks(ui, originalhunks, usecurses, testfile, match, operation=None):
    try:
        if usecurses:
            if testfile:
                recordfn = crecordmod.testdecorator(
                    testfile, crecordmod.testchunkselector
                )
            else:
                recordfn = crecordmod.chunkselector

            return crecordmod.filterpatch(
                ui, originalhunks, recordfn, operation
            )
    except crecordmod.fallbackerror as e:
        ui.warn(b'%s\n' % e)
        ui.warn(_(b'falling back to text mode\n'))

    return patch.filterpatch(ui, originalhunks, match, operation)


def recordfilter(ui, originalhunks, match, operation=None):
    """Prompts the user to filter the originalhunks and return a list of
    selected hunks.
    *operation* is used for to build ui messages to indicate the user what
    kind of filtering they are doing: reverting, committing, shelving, etc.
    (see patch.filterpatch).
    """
    usecurses = crecordmod.checkcurses(ui)
    testfile = ui.config(b'experimental', b'crecordtest')
    oldwrite = setupwrapcolorwrite(ui)
    try:
        newchunks, newopts = filterchunks(
            ui, originalhunks, usecurses, testfile, match, operation
        )
    finally:
        ui.write = oldwrite
    return newchunks, newopts


def dorecord(
    ui, repo, commitfunc, cmdsuggest, backupall, filterfn, *pats, **opts
):
    opts = pycompat.byteskwargs(opts)
    if not ui.interactive():
        if cmdsuggest:
            msg = _(b'running non-interactively, use %s instead') % cmdsuggest
        else:
            msg = _(b'running non-interactively')
        raise error.InputError(msg)

    # make sure username is set before going interactive
    if not opts.get(b'user'):
        ui.username()  # raise exception, username not provided

    def recordfunc(ui, repo, message, match, opts):
        """This is generic record driver.

        Its job is to interactively filter local changes, and
        accordingly prepare working directory into a state in which the
        job can be delegated to a non-interactive commit command such as
        'commit' or 'qrefresh'.

        After the actual job is done by non-interactive command, the
        working directory is restored to its original state.

        In the end we'll record interesting changes, and everything else
        will be left in place, so the user can continue working.
        """
        if not opts.get(b'interactive-unshelve'):
            checkunfinished(repo, commit=True)
        wctx = repo[None]
        merge = len(wctx.parents()) > 1
        if merge:
            raise error.InputError(
                _(
                    b'cannot partially commit a merge '
                    b'(use "hg commit" instead)'
                )
            )

        def fail(f, msg):
            raise error.InputError(b'%s: %s' % (f, msg))

        force = opts.get(b'force')
        if not force:
            match = matchmod.badmatch(match, fail)

        status = repo.status(match=match)

        overrides = {(b'ui', b'commitsubrepos'): True}

        with repo.ui.configoverride(overrides, b'record'):
            # subrepoutil.precommit() modifies the status
            tmpstatus = scmutil.status(
                copymod.copy(status.modified),
                copymod.copy(status.added),
                copymod.copy(status.removed),
                copymod.copy(status.deleted),
                copymod.copy(status.unknown),
                copymod.copy(status.ignored),
                copymod.copy(status.clean),  # pytype: disable=wrong-arg-count
            )

            # Force allows -X subrepo to skip the subrepo.
            subs, commitsubs, newstate = subrepoutil.precommit(
                repo.ui, wctx, tmpstatus, match, force=True
            )
            for s in subs:
                if s in commitsubs:
                    dirtyreason = wctx.sub(s).dirtyreason(True)
                    raise error.Abort(dirtyreason)

        if not force:
            repo.checkcommitpatterns(wctx, match, status, fail)
        diffopts = patch.difffeatureopts(
            ui,
            opts=opts,
            whitespace=True,
            section=b'commands',
            configprefix=b'commit.interactive.',
        )
        diffopts.nodates = True
        diffopts.git = True
        diffopts.showfunc = True
        originaldiff = patch.diff(repo, changes=status, opts=diffopts)
        original_headers = patch.parsepatch(originaldiff)
        match = scmutil.match(repo[None], pats)

        # 1. filter patch, since we are intending to apply subset of it
        try:
            chunks, newopts = filterfn(ui, original_headers, match)
        except error.PatchError as err:
            raise error.InputError(_(b'error parsing patch: %s') % err)
        opts.update(newopts)

        # We need to keep a backup of files that have been newly added and
        # modified during the recording process because there is a previous
        # version without the edit in the workdir. We also will need to restore
        # files that were the sources of renames so that the patch application
        # works.
        newlyaddedandmodifiedfiles, alsorestore = newandmodified(chunks)
        contenders = set()
        for h in chunks:
            if isheader(h):
                contenders.update(set(h.files()))

        changed = status.modified + status.added + status.removed
        newfiles = [f for f in changed if f in contenders]
        if not newfiles:
            ui.status(_(b'no changes to record\n'))
            return 0

        modified = set(status.modified)

        # 2. backup changed files, so we can restore them in the end

        if backupall:
            tobackup = changed
        else:
            tobackup = [
                f
                for f in newfiles
                if f in modified or f in newlyaddedandmodifiedfiles
            ]
        backups = {}
        if tobackup:
            backupdir = repo.vfs.join(b'record-backups')
            try:
                os.mkdir(backupdir)
            except OSError as err:
                if err.errno != errno.EEXIST:
                    raise
        try:
            # backup continues
            for f in tobackup:
                fd, tmpname = pycompat.mkstemp(
                    prefix=os.path.basename(f) + b'.', dir=backupdir
                )
                os.close(fd)
                ui.debug(b'backup %r as %r\n' % (f, tmpname))
                util.copyfile(repo.wjoin(f), tmpname, copystat=True)
                backups[f] = tmpname

            fp = stringio()
            for c in chunks:
                fname = c.filename()
                if fname in backups:
                    c.write(fp)
            dopatch = fp.tell()
            fp.seek(0)

            # 2.5 optionally review / modify patch in text editor
            if opts.get(b'review', False):
                patchtext = (
                    crecordmod.diffhelptext
                    + crecordmod.patchhelptext
                    + fp.read()
                )
                reviewedpatch = ui.edit(
                    patchtext, b"", action=b"diff", repopath=repo.path
                )
                fp.truncate(0)
                fp.write(reviewedpatch)
                fp.seek(0)

            [os.unlink(repo.wjoin(c)) for c in newlyaddedandmodifiedfiles]
            # 3a. apply filtered patch to clean repo  (clean)
            if backups:
                m = scmutil.matchfiles(repo, set(backups.keys()) | alsorestore)
                mergemod.revert_to(repo[b'.'], matcher=m)

            # 3b. (apply)
            if dopatch:
                try:
                    ui.debug(b'applying patch\n')
                    ui.debug(fp.getvalue())
                    patch.internalpatch(ui, repo, fp, 1, eolmode=None)
                except error.PatchError as err:
                    raise error.InputError(pycompat.bytestr(err))
            del fp

            # 4. We prepared working directory according to filtered
            #    patch. Now is the time to delegate the job to
            #    commit/qrefresh or the like!

            # Make all of the pathnames absolute.
            newfiles = [repo.wjoin(nf) for nf in newfiles]
            return commitfunc(ui, repo, *newfiles, **pycompat.strkwargs(opts))
        finally:
            # 5. finally restore backed-up files
            try:
                dirstate = repo.dirstate
                for realname, tmpname in pycompat.iteritems(backups):
                    ui.debug(b'restoring %r to %r\n' % (tmpname, realname))

                    if dirstate[realname] == b'n':
                        # without normallookup, restoring timestamp
                        # may cause partially committed files
                        # to be treated as unmodified

                        # XXX-PENDINGCHANGE: We should clarify the context in
                        # which this function is called  to make sure it
                        # already called within a `pendingchange`, However we
                        # are taking a shortcut here in order to be able to
                        # quickly deprecated the older API.
                        with dirstate.parentchange():
                            dirstate.update_file(
                                realname,
                                p1_tracked=True,
                                wc_tracked=True,
                                possibly_dirty=True,
                            )

                    # copystat=True here and above are a hack to trick any
                    # editors that have f open that we haven't modified them.
                    #
                    # Also note that this racy as an editor could notice the
                    # file's mtime before we've finished writing it.
                    util.copyfile(tmpname, repo.wjoin(realname), copystat=True)
                    os.unlink(tmpname)
                if tobackup:
                    os.rmdir(backupdir)
            except OSError:
                pass

    def recordinwlock(ui, repo, message, match, opts):
        with repo.wlock():
            return recordfunc(ui, repo, message, match, opts)

    return commit(ui, repo, recordinwlock, pats, opts)


class dirnode(object):
    """
    Represent a directory in user working copy with information required for
    the purpose of tersing its status.

    path is the path to the directory, without a trailing '/'

    statuses is a set of statuses of all files in this directory (this includes
    all the files in all the subdirectories too)

    files is a list of files which are direct child of this directory

    subdirs is a dictionary of sub-directory name as the key and it's own
    dirnode object as the value
    """

    def __init__(self, dirpath):
        self.path = dirpath
        self.statuses = set()
        self.files = []
        self.subdirs = {}

    def _addfileindir(self, filename, status):
        """Add a file in this directory as a direct child."""
        self.files.append((filename, status))

    def addfile(self, filename, status):
        """
        Add a file to this directory or to its direct parent directory.

        If the file is not direct child of this directory, we traverse to the
        directory of which this file is a direct child of and add the file
        there.
        """

        # the filename contains a path separator, it means it's not the direct
        # child of this directory
        if b'/' in filename:
            subdir, filep = filename.split(b'/', 1)

            # does the dirnode object for subdir exists
            if subdir not in self.subdirs:
                subdirpath = pathutil.join(self.path, subdir)
                self.subdirs[subdir] = dirnode(subdirpath)

            # try adding the file in subdir
            self.subdirs[subdir].addfile(filep, status)

        else:
            self._addfileindir(filename, status)

        if status not in self.statuses:
            self.statuses.add(status)

    def iterfilepaths(self):
        """Yield (status, path) for files directly under this directory."""
        for f, st in self.files:
            yield st, pathutil.join(self.path, f)

    def tersewalk(self, terseargs):
        """
        Yield (status, path) obtained by processing the status of this
        dirnode.

        terseargs is the string of arguments passed by the user with `--terse`
        flag.

        Following are the cases which can happen:

        1) All the files in the directory (including all the files in its
        subdirectories) share the same status and the user has asked us to terse
        that status. -> yield (status, dirpath).  dirpath will end in '/'.

        2) Otherwise, we do following:

                a) Yield (status, filepath)  for all the files which are in this
                    directory (only the ones in this directory, not the subdirs)

                b) Recurse the function on all the subdirectories of this
                   directory
        """

        if len(self.statuses) == 1:
            onlyst = self.statuses.pop()

            # Making sure we terse only when the status abbreviation is
            # passed as terse argument
            if onlyst in terseargs:
                yield onlyst, self.path + b'/'
                return

        # add the files to status list
        for st, fpath in self.iterfilepaths():
            yield st, fpath

        # recurse on the subdirs
        for dirobj in self.subdirs.values():
            for st, fpath in dirobj.tersewalk(terseargs):
                yield st, fpath


def tersedir(statuslist, terseargs):
    """
    Terse the status if all the files in a directory shares the same status.

    statuslist is scmutil.status() object which contains a list of files for
    each status.
    terseargs is string which is passed by the user as the argument to `--terse`
    flag.

    The function makes a tree of objects of dirnode class, and at each node it
    stores the information required to know whether we can terse a certain
    directory or not.
    """
    # the order matters here as that is used to produce final list
    allst = (b'm', b'a', b'r', b'd', b'u', b'i', b'c')

    # checking the argument validity
    for s in pycompat.bytestr(terseargs):
        if s not in allst:
            raise error.InputError(_(b"'%s' not recognized") % s)

    # creating a dirnode object for the root of the repo
    rootobj = dirnode(b'')
    pstatus = (
        b'modified',
        b'added',
        b'deleted',
        b'clean',
        b'unknown',
        b'ignored',
        b'removed',
    )

    tersedict = {}
    for attrname in pstatus:
        statuschar = attrname[0:1]
        for f in getattr(statuslist, attrname):
            rootobj.addfile(f, statuschar)
        tersedict[statuschar] = []

    # we won't be tersing the root dir, so add files in it
    for st, fpath in rootobj.iterfilepaths():
        tersedict[st].append(fpath)

    # process each sub-directory and build tersedict
    for subdir in rootobj.subdirs.values():
        for st, f in subdir.tersewalk(terseargs):
            tersedict[st].append(f)

    tersedlist = []
    for st in allst:
        tersedict[st].sort()
        tersedlist.append(tersedict[st])

    return scmutil.status(*tersedlist)


def _commentlines(raw):
    '''Surround lineswith a comment char and a new line'''
    lines = raw.splitlines()
    commentedlines = [b'# %s' % line for line in lines]
    return b'\n'.join(commentedlines) + b'\n'


@attr.s(frozen=True)
class morestatus(object):
    reporoot = attr.ib()
    unfinishedop = attr.ib()
    unfinishedmsg = attr.ib()
    activemerge = attr.ib()
    unresolvedpaths = attr.ib()
    _formattedpaths = attr.ib(init=False, default=set())
    _label = b'status.morestatus'

    def formatfile(self, path, fm):
        self._formattedpaths.add(path)
        if self.activemerge and path in self.unresolvedpaths:
            fm.data(unresolved=True)

    def formatfooter(self, fm):
        if self.unfinishedop or self.unfinishedmsg:
            fm.startitem()
            fm.data(itemtype=b'morestatus')

        if self.unfinishedop:
            fm.data(unfinished=self.unfinishedop)
            statemsg = (
                _(b'The repository is in an unfinished *%s* state.')
                % self.unfinishedop
            )
            fm.plain(b'%s\n' % _commentlines(statemsg), label=self._label)
        if self.unfinishedmsg:
            fm.data(unfinishedmsg=self.unfinishedmsg)

        # May also start new data items.
        self._formatconflicts(fm)

        if self.unfinishedmsg:
            fm.plain(
                b'%s\n' % _commentlines(self.unfinishedmsg), label=self._label
            )

    def _formatconflicts(self, fm):
        if not self.activemerge:
            return

        if self.unresolvedpaths:
            mergeliststr = b'\n'.join(
                [
                    b'    %s'
                    % util.pathto(self.reporoot, encoding.getcwd(), path)
                    for path in self.unresolvedpaths
                ]
            )
            msg = (
                _(
                    b'''Unresolved merge conflicts:

%s

To mark files as resolved:  hg resolve --mark FILE'''
                )
                % mergeliststr
            )

            # If any paths with unresolved conflicts were not previously
            # formatted, output them now.
            for f in self.unresolvedpaths:
                if f in self._formattedpaths:
                    # Already output.
                    continue
                fm.startitem()
                # We can't claim to know the status of the file - it may just
                # have been in one of the states that were not requested for
                # display, so it could be anything.
                fm.data(itemtype=b'file', path=f, unresolved=True)

        else:
            msg = _(b'No unresolved merge conflicts.')

        fm.plain(b'%s\n' % _commentlines(msg), label=self._label)


def readmorestatus(repo):
    """Returns a morestatus object if the repo has unfinished state."""
    statetuple = statemod.getrepostate(repo)
    mergestate = mergestatemod.mergestate.read(repo)
    activemerge = mergestate.active()
    if not statetuple and not activemerge:
        return None

    unfinishedop = unfinishedmsg = unresolved = None
    if statetuple:
        unfinishedop, unfinishedmsg = statetuple
    if activemerge:
        unresolved = sorted(mergestate.unresolved())
    return morestatus(
        repo.root, unfinishedop, unfinishedmsg, activemerge, unresolved
    )


def findpossible(cmd, table, strict=False):
    """
    Return cmd -> (aliases, command table entry)
    for each matching command.
    Return debug commands (or their aliases) only if no normal command matches.
    """
    choice = {}
    debugchoice = {}

    if cmd in table:
        # short-circuit exact matches, "log" alias beats "log|history"
        keys = [cmd]
    else:
        keys = table.keys()

    allcmds = []
    for e in keys:
        aliases = parsealiases(e)
        allcmds.extend(aliases)
        found = None
        if cmd in aliases:
            found = cmd
        elif not strict:
            for a in aliases:
                if a.startswith(cmd):
                    found = a
                    break
        if found is not None:
            if aliases[0].startswith(b"debug") or found.startswith(b"debug"):
                debugchoice[found] = (aliases, table[e])
            else:
                choice[found] = (aliases, table[e])

    if not choice and debugchoice:
        choice = debugchoice

    return choice, allcmds


def findcmd(cmd, table, strict=True):
    """Return (aliases, command table entry) for command string."""
    choice, allcmds = findpossible(cmd, table, strict)

    if cmd in choice:
        return choice[cmd]

    if len(choice) > 1:
        clist = sorted(choice)
        raise error.AmbiguousCommand(cmd, clist)

    if choice:
        return list(choice.values())[0]

    raise error.UnknownCommand(cmd, allcmds)


def changebranch(ui, repo, revs, label, opts):
    """Change the branch name of given revs to label"""

    with repo.wlock(), repo.lock(), repo.transaction(b'branches'):
        # abort in case of uncommitted merge or dirty wdir
        bailifchanged(repo)
        revs = scmutil.revrange(repo, revs)
        if not revs:
            raise error.InputError(b"empty revision set")
        roots = repo.revs(b'roots(%ld)', revs)
        if len(roots) > 1:
            raise error.InputError(
                _(b"cannot change branch of non-linear revisions")
            )
        rewriteutil.precheck(repo, revs, b'change branch of')

        root = repo[roots.first()]
        rpb = {parent.branch() for parent in root.parents()}
        if (
            not opts.get(b'force')
            and label not in rpb
            and label in repo.branchmap()
        ):
            raise error.InputError(
                _(b"a branch of the same name already exists")
            )

        # make sure only topological heads
        if repo.revs(b'heads(%ld) - head()', revs):
            raise error.InputError(
                _(b"cannot change branch in middle of a stack")
            )

        replacements = {}
        # avoid import cycle mercurial.cmdutil -> mercurial.context ->
        # mercurial.subrepo -> mercurial.cmdutil
        from . import context

        for rev in revs:
            ctx = repo[rev]
            oldbranch = ctx.branch()
            # check if ctx has same branch
            if oldbranch == label:
                continue

            def filectxfn(repo, newctx, path):
                try:
                    return ctx[path]
                except error.ManifestLookupError:
                    return None

            ui.debug(
                b"changing branch of '%s' from '%s' to '%s'\n"
                % (hex(ctx.node()), oldbranch, label)
            )
            extra = ctx.extra()
            extra[b'branch_change'] = hex(ctx.node())
            # While changing branch of set of linear commits, make sure that
            # we base our commits on new parent rather than old parent which
            # was obsoleted while changing the branch
            p1 = ctx.p1().node()
            p2 = ctx.p2().node()
            if p1 in replacements:
                p1 = replacements[p1][0]
            if p2 in replacements:
                p2 = replacements[p2][0]

            mc = context.memctx(
                repo,
                (p1, p2),
                ctx.description(),
                ctx.files(),
                filectxfn,
                user=ctx.user(),
                date=ctx.date(),
                extra=extra,
                branch=label,
            )

            newnode = repo.commitctx(mc)
            replacements[ctx.node()] = (newnode,)
            ui.debug(b'new node id is %s\n' % hex(newnode))

        # create obsmarkers and move bookmarks
        scmutil.cleanupnodes(
            repo, replacements, b'branch-change', fixphase=True
        )

        # move the working copy too
        wctx = repo[None]
        # in-progress merge is a bit too complex for now.
        if len(wctx.parents()) == 1:
            newid = replacements.get(wctx.p1().node())
            if newid is not None:
                # avoid import cycle mercurial.cmdutil -> mercurial.hg ->
                # mercurial.cmdutil
                from . import hg

                hg.update(repo, newid[0], quietempty=True)

        ui.status(_(b"changed branch on %d changesets\n") % len(replacements))


def findrepo(p):
    while not os.path.isdir(os.path.join(p, b".hg")):
        oldp, p = p, os.path.dirname(p)
        if p == oldp:
            return None

    return p


def bailifchanged(repo, merge=True, hint=None):
    """enforce the precondition that working directory must be clean.

    'merge' can be set to false if a pending uncommitted merge should be
    ignored (such as when 'update --check' runs).

    'hint' is the usual hint given to Abort exception.
    """

    if merge and repo.dirstate.p2() != repo.nullid:
        raise error.StateError(_(b'outstanding uncommitted merge'), hint=hint)
    st = repo.status()
    if st.modified or st.added or st.removed or st.deleted:
        raise error.StateError(_(b'uncommitted changes'), hint=hint)
    ctx = repo[None]
    for s in sorted(ctx.substate):
        ctx.sub(s).bailifchanged(hint=hint)


def logmessage(ui, opts):
    """get the log message according to -m and -l option"""

    check_at_most_one_arg(opts, b'message', b'logfile')

    message = opts.get(b'message')
    logfile = opts.get(b'logfile')

    if not message and logfile:
        try:
            if isstdiofilename(logfile):
                message = ui.fin.read()
            else:
                message = b'\n'.join(util.readfile(logfile).splitlines())
        except IOError as inst:
            raise error.Abort(
                _(b"can't read commit message '%s': %s")
                % (logfile, encoding.strtolocal(inst.strerror))
            )
    return message


def mergeeditform(ctxorbool, baseformname):
    """return appropriate editform name (referencing a committemplate)

    'ctxorbool' is either a ctx to be committed, or a bool indicating whether
    merging is committed.

    This returns baseformname with '.merge' appended if it is a merge,
    otherwise '.normal' is appended.
    """
    if isinstance(ctxorbool, bool):
        if ctxorbool:
            return baseformname + b".merge"
    elif len(ctxorbool.parents()) > 1:
        return baseformname + b".merge"

    return baseformname + b".normal"


def getcommiteditor(
    edit=False, finishdesc=None, extramsg=None, editform=b'', **opts
):
    """get appropriate commit message editor according to '--edit' option

    'finishdesc' is a function to be called with edited commit message
    (= 'description' of the new changeset) just after editing, but
    before checking empty-ness. It should return actual text to be
    stored into history. This allows to change description before
    storing.

    'extramsg' is a extra message to be shown in the editor instead of
    'Leave message empty to abort commit' line. 'HG: ' prefix and EOL
    is automatically added.

    'editform' is a dot-separated list of names, to distinguish
    the purpose of commit text editing.

    'getcommiteditor' returns 'commitforceeditor' regardless of
    'edit', if one of 'finishdesc' or 'extramsg' is specified, because
    they are specific for usage in MQ.
    """
    if edit or finishdesc or extramsg:
        return lambda r, c, s: commitforceeditor(
            r, c, s, finishdesc=finishdesc, extramsg=extramsg, editform=editform
        )
    elif editform:
        return lambda r, c, s: commiteditor(r, c, s, editform=editform)
    else:
        return commiteditor


def _escapecommandtemplate(tmpl):
    parts = []
    for typ, start, end in templater.scantemplate(tmpl, raw=True):
        if typ == b'string':
            parts.append(stringutil.escapestr(tmpl[start:end]))
        else:
            parts.append(tmpl[start:end])
    return b''.join(parts)


def rendercommandtemplate(ui, tmpl, props):
    r"""Expand a literal template 'tmpl' in a way suitable for command line

    '\' in outermost string is not taken as an escape character because it
    is a directory separator on Windows.

    >>> from . import ui as uimod
    >>> ui = uimod.ui()
    >>> rendercommandtemplate(ui, b'c:\\{path}', {b'path': b'foo'})
    'c:\\foo'
    >>> rendercommandtemplate(ui, b'{"c:\\{path}"}', {'path': b'foo'})
    'c:{path}'
    """
    if not tmpl:
        return tmpl
    t = formatter.maketemplater(ui, _escapecommandtemplate(tmpl))
    return t.renderdefault(props)


def rendertemplate(ctx, tmpl, props=None):
    """Expand a literal template 'tmpl' byte-string against one changeset

    Each props item must be a stringify-able value or a callable returning
    such value, i.e. no bare list nor dict should be passed.
    """
    repo = ctx.repo()
    tres = formatter.templateresources(repo.ui, repo)
    t = formatter.maketemplater(
        repo.ui, tmpl, defaults=templatekw.keywords, resources=tres
    )
    mapping = {b'ctx': ctx}
    if props:
        mapping.update(props)
    return t.renderdefault(mapping)


def format_changeset_summary(ui, ctx, command=None, default_spec=None):
    """Format a changeset summary (one line)."""
    spec = None
    if command:
        spec = ui.config(
            b'command-templates', b'oneline-summary.%s' % command, None
        )
    if not spec:
        spec = ui.config(b'command-templates', b'oneline-summary')
    if not spec:
        spec = default_spec
    if not spec:
        spec = (
            b'{separate(" ", '
            b'label("oneline-summary.changeset", "{rev}:{node|short}")'
            b', '
            b'join(filter(namespaces % "{ifeq(namespace, "branches", "", join(names % "{label("oneline-summary.{namespace}", name)}", " "))}"), " ")'
            b')} '
            b'"{label("oneline-summary.desc", desc|firstline)}"'
        )
    text = rendertemplate(ctx, spec)
    return text.split(b'\n')[0]


def _buildfntemplate(pat, total=None, seqno=None, revwidth=None, pathname=None):
    r"""Convert old-style filename format string to template string

    >>> _buildfntemplate(b'foo-%b-%n.patch', seqno=0)
    'foo-{reporoot|basename}-{seqno}.patch'
    >>> _buildfntemplate(b'%R{tags % "{tag}"}%H')
    '{rev}{tags % "{tag}"}{node}'

    '\' in outermost strings has to be escaped because it is a directory
    separator on Windows:

    >>> _buildfntemplate(b'c:\\tmp\\%R\\%n.patch', seqno=0)
    'c:\\\\tmp\\\\{rev}\\\\{seqno}.patch'
    >>> _buildfntemplate(b'\\\\foo\\bar.patch')
    '\\\\\\\\foo\\\\bar.patch'
    >>> _buildfntemplate(b'\\{tags % "{tag}"}')
    '\\\\{tags % "{tag}"}'

    but inner strings follow the template rules (i.e. '\' is taken as an
    escape character):

    >>> _buildfntemplate(br'{"c:\tmp"}', seqno=0)
    '{"c:\\tmp"}'
    """
    expander = {
        b'H': b'{node}',
        b'R': b'{rev}',
        b'h': b'{node|short}',
        b'm': br'{sub(r"[^\w]", "_", desc|firstline)}',
        b'r': b'{if(revwidth, pad(rev, revwidth, "0", left=True), rev)}',
        b'%': b'%',
        b'b': b'{reporoot|basename}',
    }
    if total is not None:
        expander[b'N'] = b'{total}'
    if seqno is not None:
        expander[b'n'] = b'{seqno}'
    if total is not None and seqno is not None:
        expander[b'n'] = b'{pad(seqno, total|stringify|count, "0", left=True)}'
    if pathname is not None:
        expander[b's'] = b'{pathname|basename}'
        expander[b'd'] = b'{if(pathname|dirname, pathname|dirname, ".")}'
        expander[b'p'] = b'{pathname}'

    newname = []
    for typ, start, end in templater.scantemplate(pat, raw=True):
        if typ != b'string':
            newname.append(pat[start:end])
            continue
        i = start
        while i < end:
            n = pat.find(b'%', i, end)
            if n < 0:
                newname.append(stringutil.escapestr(pat[i:end]))
                break
            newname.append(stringutil.escapestr(pat[i:n]))
            if n + 2 > end:
                raise error.Abort(
                    _(b"incomplete format spec in output filename")
                )
            c = pat[n + 1 : n + 2]
            i = n + 2
            try:
                newname.append(expander[c])
            except KeyError:
                raise error.Abort(
                    _(b"invalid format spec '%%%s' in output filename") % c
                )
    return b''.join(newname)


def makefilename(ctx, pat, **props):
    if not pat:
        return pat
    tmpl = _buildfntemplate(pat, **props)
    # BUG: alias expansion shouldn't be made against template fragments
    # rewritten from %-format strings, but we have no easy way to partially
    # disable the expansion.
    return rendertemplate(ctx, tmpl, pycompat.byteskwargs(props))


def isstdiofilename(pat):
    """True if the given pat looks like a filename denoting stdin/stdout"""
    return not pat or pat == b'-'


class _unclosablefile(object):
    def __init__(self, fp):
        self._fp = fp

    def close(self):
        pass

    def __iter__(self):
        return iter(self._fp)

    def __getattr__(self, attr):
        return getattr(self._fp, attr)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, exc_tb):
        pass


def makefileobj(ctx, pat, mode=b'wb', **props):
    writable = mode not in (b'r', b'rb')

    if isstdiofilename(pat):
        repo = ctx.repo()
        if writable:
            fp = repo.ui.fout
        else:
            fp = repo.ui.fin
        return _unclosablefile(fp)
    fn = makefilename(ctx, pat, **props)
    return open(fn, mode)


def openstorage(repo, cmd, file_, opts, returnrevlog=False):
    """opens the changelog, manifest, a filelog or a given revlog"""
    cl = opts[b'changelog']
    mf = opts[b'manifest']
    dir = opts[b'dir']
    msg = None
    if cl and mf:
        msg = _(b'cannot specify --changelog and --manifest at the same time')
    elif cl and dir:
        msg = _(b'cannot specify --changelog and --dir at the same time')
    elif cl or mf or dir:
        if file_:
            msg = _(b'cannot specify filename with --changelog or --manifest')
        elif not repo:
            msg = _(
                b'cannot specify --changelog or --manifest or --dir '
                b'without a repository'
            )
    if msg:
        raise error.InputError(msg)

    r = None
    if repo:
        if cl:
            r = repo.unfiltered().changelog
        elif dir:
            if not scmutil.istreemanifest(repo):
                raise error.InputError(
                    _(
                        b"--dir can only be used on repos with "
                        b"treemanifest enabled"
                    )
                )
            if not dir.endswith(b'/'):
                dir = dir + b'/'
            dirlog = repo.manifestlog.getstorage(dir)
            if len(dirlog):
                r = dirlog
        elif mf:
            r = repo.manifestlog.getstorage(b'')
        elif file_:
            filelog = repo.file(file_)
            if len(filelog):
                r = filelog

        # Not all storage may be revlogs. If requested, try to return an actual
        # revlog instance.
        if returnrevlog:
            if isinstance(r, revlog.revlog):
                pass
            elif util.safehasattr(r, b'_revlog'):
                r = r._revlog  # pytype: disable=attribute-error
            elif r is not None:
                raise error.InputError(
                    _(b'%r does not appear to be a revlog') % r
                )

    if not r:
        if not returnrevlog:
            raise error.InputError(_(b'cannot give path to non-revlog'))

        if not file_:
            raise error.CommandError(cmd, _(b'invalid arguments'))
        if not os.path.isfile(file_):
            raise error.InputError(_(b"revlog '%s' not found") % file_)

        target = (revlog_constants.KIND_OTHER, b'free-form:%s' % file_)
        r = revlog.revlog(
            vfsmod.vfs(encoding.getcwd(), audit=False),
            target=target,
            radix=file_[:-2],
        )
    return r


def openrevlog(repo, cmd, file_, opts):
    """Obtain a revlog backing storage of an item.

    This is similar to ``openstorage()`` except it always returns a revlog.

    In most cases, a caller cares about the main storage object - not the
    revlog backing it. Therefore, this function should only be used by code
    that needs to examine low-level revlog implementation details. e.g. debug
    commands.
    """
    return openstorage(repo, cmd, file_, opts, returnrevlog=True)


def copy(ui, repo, pats, opts, rename=False):
    check_incompatible_arguments(opts, b'forget', [b'dry_run'])

    # called with the repo lock held
    #
    # hgsep => pathname that uses "/" to separate directories
    # ossep => pathname that uses os.sep to separate directories
    cwd = repo.getcwd()
    targets = {}
    forget = opts.get(b"forget")
    after = opts.get(b"after")
    dryrun = opts.get(b"dry_run")
    rev = opts.get(b'at_rev')
    if rev:
        if not forget and not after:
            # TODO: Remove this restriction and make it also create the copy
            #       targets (and remove the rename source if rename==True).
            raise error.InputError(_(b'--at-rev requires --after'))
        ctx = scmutil.revsingle(repo, rev)
        if len(ctx.parents()) > 1:
            raise error.InputError(
                _(b'cannot mark/unmark copy in merge commit')
            )
    else:
        ctx = repo[None]

    pctx = ctx.p1()

    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)

    if forget:
        if ctx.rev() is None:
            new_ctx = ctx
        else:
            if len(ctx.parents()) > 1:
                raise error.InputError(_(b'cannot unmark copy in merge commit'))
            # avoid cycle context -> subrepo -> cmdutil
            from . import context

            rewriteutil.precheck(repo, [ctx.rev()], b'uncopy')
            new_ctx = context.overlayworkingctx(repo)
            new_ctx.setbase(ctx.p1())
            mergemod.graft(repo, ctx, wctx=new_ctx)

        match = scmutil.match(ctx, pats, opts)

        current_copies = ctx.p1copies()
        current_copies.update(ctx.p2copies())

        uipathfn = scmutil.getuipathfn(repo)
        for f in ctx.walk(match):
            if f in current_copies:
                new_ctx[f].markcopied(None)
            elif match.exact(f):
                ui.warn(
                    _(
                        b'%s: not unmarking as copy - file is not marked as copied\n'
                    )
                    % uipathfn(f)
                )

        if ctx.rev() is not None:
            with repo.lock():
                mem_ctx = new_ctx.tomemctx_for_amend(ctx)
                new_node = mem_ctx.commit()

                if repo.dirstate.p1() == ctx.node():
                    with repo.dirstate.parentchange():
                        scmutil.movedirstate(repo, repo[new_node])
                replacements = {ctx.node(): [new_node]}
                scmutil.cleanupnodes(
                    repo, replacements, b'uncopy', fixphase=True
                )

        return

    pats = scmutil.expandpats(pats)
    if not pats:
        raise error.InputError(_(b'no source or destination specified'))
    if len(pats) == 1:
        raise error.InputError(_(b'no destination specified'))
    dest = pats.pop()

    def walkpat(pat):
        srcs = []
        # TODO: Inline and simplify the non-working-copy version of this code
        # since it shares very little with the working-copy version of it.
        ctx_to_walk = ctx if ctx.rev() is None else pctx
        m = scmutil.match(ctx_to_walk, [pat], opts, globbed=True)
        for abs in ctx_to_walk.walk(m):
            rel = uipathfn(abs)
            exact = m.exact(abs)
            if abs not in ctx:
                if abs in pctx:
                    if not after:
                        if exact:
                            ui.warn(
                                _(
                                    b'%s: not copying - file has been marked '
                                    b'for remove\n'
                                )
                                % rel
                            )
                        continue
                else:
                    if exact:
                        ui.warn(
                            _(b'%s: not copying - file is not managed\n') % rel
                        )
                    continue

            # abs: hgsep
            # rel: ossep
            srcs.append((abs, rel, exact))
        return srcs

    if ctx.rev() is not None:
        rewriteutil.precheck(repo, [ctx.rev()], b'uncopy')
        absdest = pathutil.canonpath(repo.root, cwd, dest)
        if ctx.hasdir(absdest):
            raise error.InputError(
                _(b'%s: --at-rev does not support a directory as destination')
                % uipathfn(absdest)
            )
        if absdest not in ctx:
            raise error.InputError(
                _(b'%s: copy destination does not exist in %s')
                % (uipathfn(absdest), ctx)
            )

        # avoid cycle context -> subrepo -> cmdutil
        from . import context

        copylist = []
        for pat in pats:
            srcs = walkpat(pat)
            if not srcs:
                continue
            for abs, rel, exact in srcs:
                copylist.append(abs)

        if not copylist:
            raise error.InputError(_(b'no files to copy'))
        # TODO: Add support for `hg cp --at-rev . foo bar dir` and
        # `hg cp --at-rev . dir1 dir2`, preferably unifying the code with the
        # existing functions below.
        if len(copylist) != 1:
            raise error.InputError(_(b'--at-rev requires a single source'))

        new_ctx = context.overlayworkingctx(repo)
        new_ctx.setbase(ctx.p1())
        mergemod.graft(repo, ctx, wctx=new_ctx)

        new_ctx.markcopied(absdest, copylist[0])

        with repo.lock():
            mem_ctx = new_ctx.tomemctx_for_amend(ctx)
            new_node = mem_ctx.commit()

            if repo.dirstate.p1() == ctx.node():
                with repo.dirstate.parentchange():
                    scmutil.movedirstate(repo, repo[new_node])
            replacements = {ctx.node(): [new_node]}
            scmutil.cleanupnodes(repo, replacements, b'copy', fixphase=True)

        return

    # abssrc: hgsep
    # relsrc: ossep
    # otarget: ossep
    def copyfile(abssrc, relsrc, otarget, exact):
        abstarget = pathutil.canonpath(repo.root, cwd, otarget)
        if b'/' in abstarget:
            # We cannot normalize abstarget itself, this would prevent
            # case only renames, like a => A.
            abspath, absname = abstarget.rsplit(b'/', 1)
            abstarget = repo.dirstate.normalize(abspath) + b'/' + absname
        reltarget = repo.pathto(abstarget, cwd)
        target = repo.wjoin(abstarget)
        src = repo.wjoin(abssrc)
        state = repo.dirstate[abstarget]

        scmutil.checkportable(ui, abstarget)

        # check for collisions
        prevsrc = targets.get(abstarget)
        if prevsrc is not None:
            ui.warn(
                _(b'%s: not overwriting - %s collides with %s\n')
                % (
                    reltarget,
                    repo.pathto(abssrc, cwd),
                    repo.pathto(prevsrc, cwd),
                )
            )
            return True  # report a failure

        # check for overwrites
        exists = os.path.lexists(target)
        samefile = False
        if exists and abssrc != abstarget:
            if repo.dirstate.normalize(abssrc) == repo.dirstate.normalize(
                abstarget
            ):
                if not rename:
                    ui.warn(_(b"%s: can't copy - same file\n") % reltarget)
                    return True  # report a failure
                exists = False
                samefile = True

        if not after and exists or after and state in b'mn':
            if not opts[b'force']:
                if state in b'mn':
                    msg = _(b'%s: not overwriting - file already committed\n')
                    if after:
                        flags = b'--after --force'
                    else:
                        flags = b'--force'
                    if rename:
                        hint = (
                            _(
                                b"('hg rename %s' to replace the file by "
                                b'recording a rename)\n'
                            )
                            % flags
                        )
                    else:
                        hint = (
                            _(
                                b"('hg copy %s' to replace the file by "
                                b'recording a copy)\n'
                            )
                            % flags
                        )
                else:
                    msg = _(b'%s: not overwriting - file exists\n')
                    if rename:
                        hint = _(
                            b"('hg rename --after' to record the rename)\n"
                        )
                    else:
                        hint = _(b"('hg copy --after' to record the copy)\n")
                ui.warn(msg % reltarget)
                ui.warn(hint)
                return True  # report a failure

        if after:
            if not exists:
                if rename:
                    ui.warn(
                        _(b'%s: not recording move - %s does not exist\n')
                        % (relsrc, reltarget)
                    )
                else:
                    ui.warn(
                        _(b'%s: not recording copy - %s does not exist\n')
                        % (relsrc, reltarget)
                    )
                return True  # report a failure
        elif not dryrun:
            try:
                if exists:
                    os.unlink(target)
                targetdir = os.path.dirname(target) or b'.'
                if not os.path.isdir(targetdir):
                    os.makedirs(targetdir)
                if samefile:
                    tmp = target + b"~hgrename"
                    os.rename(src, tmp)
                    os.rename(tmp, target)
                else:
                    # Preserve stat info on renames, not on copies; this matches
                    # Linux CLI behavior.
                    util.copyfile(src, target, copystat=rename)
                srcexists = True
            except IOError as inst:
                if inst.errno == errno.ENOENT:
                    ui.warn(_(b'%s: deleted in working directory\n') % relsrc)
                    srcexists = False
                else:
                    ui.warn(
                        _(b'%s: cannot copy - %s\n')
                        % (relsrc, encoding.strtolocal(inst.strerror))
                    )
                    return True  # report a failure

        if ui.verbose or not exact:
            if rename:
                ui.status(_(b'moving %s to %s\n') % (relsrc, reltarget))
            else:
                ui.status(_(b'copying %s to %s\n') % (relsrc, reltarget))

        targets[abstarget] = abssrc

        # fix up dirstate
        scmutil.dirstatecopy(
            ui, repo, ctx, abssrc, abstarget, dryrun=dryrun, cwd=cwd
        )
        if rename and not dryrun:
            if not after and srcexists and not samefile:
                rmdir = repo.ui.configbool(b'experimental', b'removeemptydirs')
                repo.wvfs.unlinkpath(abssrc, rmdir=rmdir)
            ctx.forget([abssrc])

    # pat: ossep
    # dest ossep
    # srcs: list of (hgsep, hgsep, ossep, bool)
    # return: function that takes hgsep and returns ossep
    def targetpathfn(pat, dest, srcs):
        if os.path.isdir(pat):
            abspfx = pathutil.canonpath(repo.root, cwd, pat)
            abspfx = util.localpath(abspfx)
            if destdirexists:
                striplen = len(os.path.split(abspfx)[0])
            else:
                striplen = len(abspfx)
            if striplen:
                striplen += len(pycompat.ossep)
            res = lambda p: os.path.join(dest, util.localpath(p)[striplen:])
        elif destdirexists:
            res = lambda p: os.path.join(
                dest, os.path.basename(util.localpath(p))
            )
        else:
            res = lambda p: dest
        return res

    # pat: ossep
    # dest ossep
    # srcs: list of (hgsep, hgsep, ossep, bool)
    # return: function that takes hgsep and returns ossep
    def targetpathafterfn(pat, dest, srcs):
        if matchmod.patkind(pat):
            # a mercurial pattern
            res = lambda p: os.path.join(
                dest, os.path.basename(util.localpath(p))
            )
        else:
            abspfx = pathutil.canonpath(repo.root, cwd, pat)
            if len(abspfx) < len(srcs[0][0]):
                # A directory. Either the target path contains the last
                # component of the source path or it does not.
                def evalpath(striplen):
                    score = 0
                    for s in srcs:
                        t = os.path.join(dest, util.localpath(s[0])[striplen:])
                        if os.path.lexists(t):
                            score += 1
                    return score

                abspfx = util.localpath(abspfx)
                striplen = len(abspfx)
                if striplen:
                    striplen += len(pycompat.ossep)
                if os.path.isdir(os.path.join(dest, os.path.split(abspfx)[1])):
                    score = evalpath(striplen)
                    striplen1 = len(os.path.split(abspfx)[0])
                    if striplen1:
                        striplen1 += len(pycompat.ossep)
                    if evalpath(striplen1) > score:
                        striplen = striplen1
                res = lambda p: os.path.join(dest, util.localpath(p)[striplen:])
            else:
                # a file
                if destdirexists:
                    res = lambda p: os.path.join(
                        dest, os.path.basename(util.localpath(p))
                    )
                else:
                    res = lambda p: dest
        return res

    destdirexists = os.path.isdir(dest) and not os.path.islink(dest)
    if not destdirexists:
        if len(pats) > 1 or matchmod.patkind(pats[0]):
            raise error.InputError(
                _(
                    b'with multiple sources, destination must be an '
                    b'existing directory'
                )
            )
        if util.endswithsep(dest):
            raise error.InputError(
                _(b'destination %s is not a directory') % dest
            )

    tfn = targetpathfn
    if after:
        tfn = targetpathafterfn
    copylist = []
    for pat in pats:
        srcs = walkpat(pat)
        if not srcs:
            continue
        copylist.append((tfn(pat, dest, srcs), srcs))
    if not copylist:
        hint = None
        if rename:
            hint = _(b'maybe you meant to use --after --at-rev=.')
        raise error.InputError(_(b'no files to copy'), hint=hint)

    errors = 0
    for targetpath, srcs in copylist:
        for abssrc, relsrc, exact in srcs:
            if copyfile(abssrc, relsrc, targetpath(abssrc), exact):
                errors += 1

    return errors != 0


## facility to let extension process additional data into an import patch
# list of identifier to be executed in order
extrapreimport = []  # run before commit
extrapostimport = []  # run after commit
# mapping from identifier to actual import function
#
# 'preimport' are run before the commit is made and are provided the following
# arguments:
# - repo: the localrepository instance,
# - patchdata: data extracted from patch header (cf m.patch.patchheadermap),
# - extra: the future extra dictionary of the changeset, please mutate it,
# - opts: the import options.
# XXX ideally, we would just pass an ctx ready to be computed, that would allow
# mutation of in memory commit and more. Feel free to rework the code to get
# there.
extrapreimportmap = {}
# 'postimport' are run after the commit is made and are provided the following
# argument:
# - ctx: the changectx created by import.
extrapostimportmap = {}


def tryimportone(ui, repo, patchdata, parents, opts, msgs, updatefunc):
    """Utility function used by commands.import to import a single patch

    This function is explicitly defined here to help the evolve extension to
    wrap this part of the import logic.

    The API is currently a bit ugly because it a simple code translation from
    the import command. Feel free to make it better.

    :patchdata: a dictionary containing parsed patch data (such as from
                ``patch.extract()``)
    :parents: nodes that will be parent of the created commit
    :opts: the full dict of option passed to the import command
    :msgs: list to save commit message to.
           (used in case we need to save it when failing)
    :updatefunc: a function that update a repo to a given node
                 updatefunc(<repo>, <node>)
    """
    # avoid cycle context -> subrepo -> cmdutil
    from . import context

    tmpname = patchdata.get(b'filename')
    message = patchdata.get(b'message')
    user = opts.get(b'user') or patchdata.get(b'user')
    date = opts.get(b'date') or patchdata.get(b'date')
    branch = patchdata.get(b'branch')
    nodeid = patchdata.get(b'nodeid')
    p1 = patchdata.get(b'p1')
    p2 = patchdata.get(b'p2')

    nocommit = opts.get(b'no_commit')
    importbranch = opts.get(b'import_branch')
    update = not opts.get(b'bypass')
    strip = opts[b"strip"]
    prefix = opts[b"prefix"]
    sim = float(opts.get(b'similarity') or 0)

    if not tmpname:
        return None, None, False

    rejects = False

    cmdline_message = logmessage(ui, opts)
    if cmdline_message:
        # pickup the cmdline msg
        message = cmdline_message
    elif message:
        # pickup the patch msg
        message = message.strip()
    else:
        # launch the editor
        message = None
    ui.debug(b'message:\n%s\n' % (message or b''))

    if len(parents) == 1:
        parents.append(repo[nullrev])
    if opts.get(b'exact'):
        if not nodeid or not p1:
            raise error.InputError(_(b'not a Mercurial patch'))
        p1 = repo[p1]
        p2 = repo[p2 or nullrev]
    elif p2:
        try:
            p1 = repo[p1]
            p2 = repo[p2]
            # Without any options, consider p2 only if the
            # patch is being applied on top of the recorded
            # first parent.
            if p1 != parents[0]:
                p1 = parents[0]
                p2 = repo[nullrev]
        except error.RepoError:
            p1, p2 = parents
        if p2.rev() == nullrev:
            ui.warn(
                _(
                    b"warning: import the patch as a normal revision\n"
                    b"(use --exact to import the patch as a merge)\n"
                )
            )
    else:
        p1, p2 = parents

    n = None
    if update:
        if p1 != parents[0]:
            updatefunc(repo, p1.node())
        if p2 != parents[1]:
            repo.setparents(p1.node(), p2.node())

        if opts.get(b'exact') or importbranch:
            repo.dirstate.setbranch(branch or b'default')

        partial = opts.get(b'partial', False)
        files = set()
        try:
            patch.patch(
                ui,
                repo,
                tmpname,
                strip=strip,
                prefix=prefix,
                files=files,
                eolmode=None,
                similarity=sim / 100.0,
            )
        except error.PatchError as e:
            if not partial:
                raise error.Abort(pycompat.bytestr(e))
            if partial:
                rejects = True

        files = list(files)
        if nocommit:
            if message:
                msgs.append(message)
        else:
            if opts.get(b'exact') or p2:
                # If you got here, you either use --force and know what
                # you are doing or used --exact or a merge patch while
                # being updated to its first parent.
                m = None
            else:
                m = scmutil.matchfiles(repo, files or [])
            editform = mergeeditform(repo[None], b'import.normal')
            if opts.get(b'exact'):
                editor = None
            else:
                editor = getcommiteditor(
                    editform=editform, **pycompat.strkwargs(opts)
                )
            extra = {}
            for idfunc in extrapreimport:
                extrapreimportmap[idfunc](repo, patchdata, extra, opts)
            overrides = {}
            if partial:
                overrides[(b'ui', b'allowemptycommit')] = True
            if opts.get(b'secret'):
                overrides[(b'phases', b'new-commit')] = b'secret'
            with repo.ui.configoverride(overrides, b'import'):
                n = repo.commit(
                    message, user, date, match=m, editor=editor, extra=extra
                )
                for idfunc in extrapostimport:
                    extrapostimportmap[idfunc](repo[n])
    else:
        if opts.get(b'exact') or importbranch:
            branch = branch or b'default'
        else:
            branch = p1.branch()
        store = patch.filestore()
        try:
            files = set()
            try:
                patch.patchrepo(
                    ui,
                    repo,
                    p1,
                    store,
                    tmpname,
                    strip,
                    prefix,
                    files,
                    eolmode=None,
                )
            except error.PatchError as e:
                raise error.Abort(stringutil.forcebytestr(e))
            if opts.get(b'exact'):
                editor = None
            else:
                editor = getcommiteditor(editform=b'import.bypass')
            memctx = context.memctx(
                repo,
                (p1.node(), p2.node()),
                message,
                files=files,
                filectxfn=store,
                user=user,
                date=date,
                branch=branch,
                editor=editor,
            )

            overrides = {}
            if opts.get(b'secret'):
                overrides[(b'phases', b'new-commit')] = b'secret'
            with repo.ui.configoverride(overrides, b'import'):
                n = memctx.commit()
        finally:
            store.close()
    if opts.get(b'exact') and nocommit:
        # --exact with --no-commit is still useful in that it does merge
        # and branch bits
        ui.warn(_(b"warning: can't check exact import with --no-commit\n"))
    elif opts.get(b'exact') and (not n or hex(n) != nodeid):
        raise error.Abort(_(b'patch is damaged or loses information'))
    msg = _(b'applied to working directory')
    if n:
        # i18n: refers to a short changeset id
        msg = _(b'created %s') % short(n)
    return msg, n, rejects


# facility to let extensions include additional data in an exported patch
# list of identifiers to be executed in order
extraexport = []
# mapping from identifier to actual export function
# function as to return a string to be added to the header or None
# it is given two arguments (sequencenumber, changectx)
extraexportmap = {}


def _exportsingle(repo, ctx, fm, match, switch_parent, seqno, diffopts):
    node = scmutil.binnode(ctx)
    parents = [p.node() for p in ctx.parents() if p]
    branch = ctx.branch()
    if switch_parent:
        parents.reverse()

    if parents:
        prev = parents[0]
    else:
        prev = repo.nullid

    fm.context(ctx=ctx)
    fm.plain(b'# HG changeset patch\n')
    fm.write(b'user', b'# User %s\n', ctx.user())
    fm.plain(b'# Date %d %d\n' % ctx.date())
    fm.write(b'date', b'#      %s\n', fm.formatdate(ctx.date()))
    fm.condwrite(
        branch and branch != b'default', b'branch', b'# Branch %s\n', branch
    )
    fm.write(b'node', b'# Node ID %s\n', hex(node))
    fm.plain(b'# Parent  %s\n' % hex(prev))
    if len(parents) > 1:
        fm.plain(b'# Parent  %s\n' % hex(parents[1]))
    fm.data(parents=fm.formatlist(pycompat.maplist(hex, parents), name=b'node'))

    # TODO: redesign extraexportmap function to support formatter
    for headerid in extraexport:
        header = extraexportmap[headerid](seqno, ctx)
        if header is not None:
            fm.plain(b'# %s\n' % header)

    fm.write(b'desc', b'%s\n', ctx.description().rstrip())
    fm.plain(b'\n')

    if fm.isplain():
        chunkiter = patch.diffui(repo, prev, node, match, opts=diffopts)
        for chunk, label in chunkiter:
            fm.plain(chunk, label=label)
    else:
        chunkiter = patch.diff(repo, prev, node, match, opts=diffopts)
        # TODO: make it structured?
        fm.data(diff=b''.join(chunkiter))


def _exportfile(repo, revs, fm, dest, switch_parent, diffopts, match):
    """Export changesets to stdout or a single file"""
    for seqno, rev in enumerate(revs, 1):
        ctx = repo[rev]
        if not dest.startswith(b'<'):
            repo.ui.note(b"%s\n" % dest)
        fm.startitem()
        _exportsingle(repo, ctx, fm, match, switch_parent, seqno, diffopts)


def _exportfntemplate(
    repo, revs, basefm, fntemplate, switch_parent, diffopts, match
):
    """Export changesets to possibly multiple files"""
    total = len(revs)
    revwidth = max(len(str(rev)) for rev in revs)
    filemap = util.sortdict()  # filename: [(seqno, rev), ...]

    for seqno, rev in enumerate(revs, 1):
        ctx = repo[rev]
        dest = makefilename(
            ctx, fntemplate, total=total, seqno=seqno, revwidth=revwidth
        )
        filemap.setdefault(dest, []).append((seqno, rev))

    for dest in filemap:
        with formatter.maybereopen(basefm, dest) as fm:
            repo.ui.note(b"%s\n" % dest)
            for seqno, rev in filemap[dest]:
                fm.startitem()
                ctx = repo[rev]
                _exportsingle(
                    repo, ctx, fm, match, switch_parent, seqno, diffopts
                )


def _prefetchchangedfiles(repo, revs, match):
    allfiles = set()
    for rev in revs:
        for file in repo[rev].files():
            if not match or match(file):
                allfiles.add(file)
    match = scmutil.matchfiles(repo, allfiles)
    revmatches = [(rev, match) for rev in revs]
    scmutil.prefetchfiles(repo, revmatches)


def export(
    repo,
    revs,
    basefm,
    fntemplate=b'hg-%h.patch',
    switch_parent=False,
    opts=None,
    match=None,
):
    """export changesets as hg patches

    Args:
      repo: The repository from which we're exporting revisions.
      revs: A list of revisions to export as revision numbers.
      basefm: A formatter to which patches should be written.
      fntemplate: An optional string to use for generating patch file names.
      switch_parent: If True, show diffs against second parent when not nullid.
                     Default is false, which always shows diff against p1.
      opts: diff options to use for generating the patch.
      match: If specified, only export changes to files matching this matcher.

    Returns:
      Nothing.

    Side Effect:
      "HG Changeset Patch" data is emitted to one of the following
      destinations:
        fntemplate specified: Each rev is written to a unique file named using
                            the given template.
        Otherwise: All revs will be written to basefm.
    """
    _prefetchchangedfiles(repo, revs, match)

    if not fntemplate:
        _exportfile(
            repo, revs, basefm, b'<unnamed>', switch_parent, opts, match
        )
    else:
        _exportfntemplate(
            repo, revs, basefm, fntemplate, switch_parent, opts, match
        )


def exportfile(repo, revs, fp, switch_parent=False, opts=None, match=None):
    """Export changesets to the given file stream"""
    _prefetchchangedfiles(repo, revs, match)

    dest = getattr(fp, 'name', b'<unnamed>')
    with formatter.formatter(repo.ui, fp, b'export', {}) as fm:
        _exportfile(repo, revs, fm, dest, switch_parent, opts, match)


def showmarker(fm, marker, index=None):
    """utility function to display obsolescence marker in a readable way

    To be used by debug function."""
    if index is not None:
        fm.write(b'index', b'%i ', index)
    fm.write(b'prednode', b'%s ', hex(marker.prednode()))
    succs = marker.succnodes()
    fm.condwrite(
        succs,
        b'succnodes',
        b'%s ',
        fm.formatlist(map(hex, succs), name=b'node'),
    )
    fm.write(b'flag', b'%X ', marker.flags())
    parents = marker.parentnodes()
    if parents is not None:
        fm.write(
            b'parentnodes',
            b'{%s} ',
            fm.formatlist(map(hex, parents), name=b'node', sep=b', '),
        )
    fm.write(b'date', b'(%s) ', fm.formatdate(marker.date()))
    meta = marker.metadata().copy()
    meta.pop(b'date', None)
    smeta = pycompat.rapply(pycompat.maybebytestr, meta)
    fm.write(
        b'metadata', b'{%s}', fm.formatdict(smeta, fmt=b'%r: %r', sep=b', ')
    )
    fm.plain(b'\n')


def finddate(ui, repo, date):
    """Find the tipmost changeset that matches the given date spec"""
    mrevs = repo.revs(b'date(%s)', date)
    try:
        rev = mrevs.max()
    except ValueError:
        raise error.InputError(_(b"revision matching date not found"))

    ui.status(
        _(b"found revision %d from %s\n")
        % (rev, dateutil.datestr(repo[rev].date()))
    )
    return b'%d' % rev


def add(ui, repo, match, prefix, uipathfn, explicitonly, **opts):
    bad = []

    badfn = lambda x, y: bad.append(x) or match.bad(x, y)
    names = []
    wctx = repo[None]
    cca = None
    abort, warn = scmutil.checkportabilityalert(ui)
    if abort or warn:
        cca = scmutil.casecollisionauditor(ui, abort, repo.dirstate)

    match = repo.narrowmatch(match, includeexact=True)
    badmatch = matchmod.badmatch(match, badfn)
    dirstate = repo.dirstate
    # We don't want to just call wctx.walk here, since it would return a lot of
    # clean files, which we aren't interested in and takes time.
    for f in sorted(
        dirstate.walk(
            badmatch,
            subrepos=sorted(wctx.substate),
            unknown=True,
            ignored=False,
            full=False,
        )
    ):
        exact = match.exact(f)
        if exact or not explicitonly and f not in wctx and repo.wvfs.lexists(f):
            if cca:
                cca(f)
            names.append(f)
            if ui.verbose or not exact:
                ui.status(
                    _(b'adding %s\n') % uipathfn(f), label=b'ui.addremove.added'
                )

    for subpath in sorted(wctx.substate):
        sub = wctx.sub(subpath)
        try:
            submatch = matchmod.subdirmatcher(subpath, match)
            subprefix = repo.wvfs.reljoin(prefix, subpath)
            subuipathfn = scmutil.subdiruipathfn(subpath, uipathfn)
            if opts.get('subrepos'):
                bad.extend(
                    sub.add(ui, submatch, subprefix, subuipathfn, False, **opts)
                )
            else:
                bad.extend(
                    sub.add(ui, submatch, subprefix, subuipathfn, True, **opts)
                )
        except error.LookupError:
            ui.status(
                _(b"skipping missing subrepository: %s\n") % uipathfn(subpath)
            )

    if not opts.get('dry_run'):
        rejected = wctx.add(names, prefix)
        bad.extend(f for f in rejected if f in match.files())
    return bad


def addwebdirpath(repo, serverpath, webconf):
    webconf[serverpath] = repo.root
    repo.ui.debug(b'adding %s = %s\n' % (serverpath, repo.root))

    for r in repo.revs(b'filelog("path:.hgsub")'):
        ctx = repo[r]
        for subpath in ctx.substate:
            ctx.sub(subpath).addwebdirpath(serverpath, webconf)


def forget(
    ui, repo, match, prefix, uipathfn, explicitonly, dryrun, interactive
):
    if dryrun and interactive:
        raise error.InputError(
            _(b"cannot specify both --dry-run and --interactive")
        )
    bad = []
    badfn = lambda x, y: bad.append(x) or match.bad(x, y)
    wctx = repo[None]
    forgot = []

    s = repo.status(match=matchmod.badmatch(match, badfn), clean=True)
    forget = sorted(s.modified + s.added + s.deleted + s.clean)
    if explicitonly:
        forget = [f for f in forget if match.exact(f)]

    for subpath in sorted(wctx.substate):
        sub = wctx.sub(subpath)
        submatch = matchmod.subdirmatcher(subpath, match)
        subprefix = repo.wvfs.reljoin(prefix, subpath)
        subuipathfn = scmutil.subdiruipathfn(subpath, uipathfn)
        try:
            subbad, subforgot = sub.forget(
                submatch,
                subprefix,
                subuipathfn,
                dryrun=dryrun,
                interactive=interactive,
            )
            bad.extend([subpath + b'/' + f for f in subbad])
            forgot.extend([subpath + b'/' + f for f in subforgot])
        except error.LookupError:
            ui.status(
                _(b"skipping missing subrepository: %s\n") % uipathfn(subpath)
            )

    if not explicitonly:
        for f in match.files():
            if f not in repo.dirstate and not repo.wvfs.isdir(f):
                if f not in forgot:
                    if repo.wvfs.exists(f):
                        # Don't complain if the exact case match wasn't given.
                        # But don't do this until after checking 'forgot', so
                        # that subrepo files aren't normalized, and this op is
                        # purely from data cached by the status walk above.
                        if repo.dirstate.normalize(f) in repo.dirstate:
                            continue
                        ui.warn(
                            _(
                                b'not removing %s: '
                                b'file is already untracked\n'
                            )
                            % uipathfn(f)
                        )
                    bad.append(f)

    if interactive:
        responses = _(
            b'[Ynsa?]'
            b'$$ &Yes, forget this file'
            b'$$ &No, skip this file'
            b'$$ &Skip remaining files'
            b'$$ Include &all remaining files'
            b'$$ &? (display help)'
        )
        for filename in forget[:]:
            r = ui.promptchoice(
                _(b'forget %s %s') % (uipathfn(filename), responses)
            )
            if r == 4:  # ?
                while r == 4:
                    for c, t in ui.extractchoices(responses)[1]:
                        ui.write(b'%s - %s\n' % (c, encoding.lower(t)))
                    r = ui.promptchoice(
                        _(b'forget %s %s') % (uipathfn(filename), responses)
                    )
            if r == 0:  # yes
                continue
            elif r == 1:  # no
                forget.remove(filename)
            elif r == 2:  # Skip
                fnindex = forget.index(filename)
                del forget[fnindex:]
                break
            elif r == 3:  # All
                break

    for f in forget:
        if ui.verbose or not match.exact(f) or interactive:
            ui.status(
                _(b'removing %s\n') % uipathfn(f), label=b'ui.addremove.removed'
            )

    if not dryrun:
        rejected = wctx.forget(forget, prefix)
        bad.extend(f for f in rejected if f in match.files())
        forgot.extend(f for f in forget if f not in rejected)
    return bad, forgot


def files(ui, ctx, m, uipathfn, fm, fmt, subrepos):
    ret = 1

    needsfctx = ui.verbose or {b'size', b'flags'} & fm.datahint()
    if fm.isplain() and not needsfctx:
        # Fast path. The speed-up comes from skipping the formatter, and batching
        # calls to ui.write.
        buf = []
        for f in ctx.matches(m):
            buf.append(fmt % uipathfn(f))
            if len(buf) > 100:
                ui.write(b''.join(buf))
                del buf[:]
            ret = 0
        if buf:
            ui.write(b''.join(buf))
    else:
        for f in ctx.matches(m):
            fm.startitem()
            fm.context(ctx=ctx)
            if needsfctx:
                fc = ctx[f]
                fm.write(b'size flags', b'% 10d % 1s ', fc.size(), fc.flags())
            fm.data(path=f)
            fm.plain(fmt % uipathfn(f))
            ret = 0

    for subpath in sorted(ctx.substate):
        submatch = matchmod.subdirmatcher(subpath, m)
        subuipathfn = scmutil.subdiruipathfn(subpath, uipathfn)
        if subrepos or m.exact(subpath) or any(submatch.files()):
            sub = ctx.sub(subpath)
            try:
                recurse = m.exact(subpath) or subrepos
                if (
                    sub.printfiles(ui, submatch, subuipathfn, fm, fmt, recurse)
                    == 0
                ):
                    ret = 0
            except error.LookupError:
                ui.status(
                    _(b"skipping missing subrepository: %s\n")
                    % uipathfn(subpath)
                )

    return ret


def remove(
    ui, repo, m, prefix, uipathfn, after, force, subrepos, dryrun, warnings=None
):
    ret = 0
    s = repo.status(match=m, clean=True)
    modified, added, deleted, clean = s.modified, s.added, s.deleted, s.clean

    wctx = repo[None]

    if warnings is None:
        warnings = []
        warn = True
    else:
        warn = False

    subs = sorted(wctx.substate)
    progress = ui.makeprogress(
        _(b'searching'), total=len(subs), unit=_(b'subrepos')
    )
    for subpath in subs:
        submatch = matchmod.subdirmatcher(subpath, m)
        subprefix = repo.wvfs.reljoin(prefix, subpath)
        subuipathfn = scmutil.subdiruipathfn(subpath, uipathfn)
        if subrepos or m.exact(subpath) or any(submatch.files()):
            progress.increment()
            sub = wctx.sub(subpath)
            try:
                if sub.removefiles(
                    submatch,
                    subprefix,
                    subuipathfn,
                    after,
                    force,
                    subrepos,
                    dryrun,
                    warnings,
                ):
                    ret = 1
            except error.LookupError:
                warnings.append(
                    _(b"skipping missing subrepository: %s\n")
                    % uipathfn(subpath)
                )
    progress.complete()

    # warn about failure to delete explicit files/dirs
    deleteddirs = pathutil.dirs(deleted)
    files = m.files()
    progress = ui.makeprogress(
        _(b'deleting'), total=len(files), unit=_(b'files')
    )
    for f in files:

        def insubrepo():
            for subpath in wctx.substate:
                if f.startswith(subpath + b'/'):
                    return True
            return False

        progress.increment()
        isdir = f in deleteddirs or wctx.hasdir(f)
        if f in repo.dirstate or isdir or f == b'.' or insubrepo() or f in subs:
            continue

        if repo.wvfs.exists(f):
            if repo.wvfs.isdir(f):
                warnings.append(
                    _(b'not removing %s: no tracked files\n') % uipathfn(f)
                )
            else:
                warnings.append(
                    _(b'not removing %s: file is untracked\n') % uipathfn(f)
                )
        # missing files will generate a warning elsewhere
        ret = 1
    progress.complete()

    if force:
        list = modified + deleted + clean + added
    elif after:
        list = deleted
        remaining = modified + added + clean
        progress = ui.makeprogress(
            _(b'skipping'), total=len(remaining), unit=_(b'files')
        )
        for f in remaining:
            progress.increment()
            if ui.verbose or (f in files):
                warnings.append(
                    _(b'not removing %s: file still exists\n') % uipathfn(f)
                )
            ret = 1
        progress.complete()
    else:
        list = deleted + clean
        progress = ui.makeprogress(
            _(b'skipping'), total=(len(modified) + len(added)), unit=_(b'files')
        )
        for f in modified:
            progress.increment()
            warnings.append(
                _(
                    b'not removing %s: file is modified (use -f'
                    b' to force removal)\n'
                )
                % uipathfn(f)
            )
            ret = 1
        for f in added:
            progress.increment()
            warnings.append(
                _(
                    b"not removing %s: file has been marked for add"
                    b" (use 'hg forget' to undo add)\n"
                )
                % uipathfn(f)
            )
            ret = 1
        progress.complete()

    list = sorted(list)
    progress = ui.makeprogress(
        _(b'deleting'), total=len(list), unit=_(b'files')
    )
    for f in list:
        if ui.verbose or not m.exact(f):
            progress.increment()
            ui.status(
                _(b'removing %s\n') % uipathfn(f), label=b'ui.addremove.removed'
            )
    progress.complete()

    if not dryrun:
        with repo.wlock():
            if not after:
                for f in list:
                    if f in added:
                        continue  # we never unlink added files on remove
                    rmdir = repo.ui.configbool(
                        b'experimental', b'removeemptydirs'
                    )
                    repo.wvfs.unlinkpath(f, ignoremissing=True, rmdir=rmdir)
            repo[None].forget(list)

    if warn:
        for warning in warnings:
            ui.warn(warning)

    return ret


def _catfmtneedsdata(fm):
    return not fm.datahint() or b'data' in fm.datahint()


def _updatecatformatter(fm, ctx, matcher, path, decode):
    """Hook for adding data to the formatter used by ``hg cat``.

    Extensions (e.g., lfs) can wrap this to inject keywords/data, but must call
    this method first."""

    # data() can be expensive to fetch (e.g. lfs), so don't fetch it if it
    # wasn't requested.
    data = b''
    if _catfmtneedsdata(fm):
        data = ctx[path].data()
        if decode:
            data = ctx.repo().wwritedata(path, data)
    fm.startitem()
    fm.context(ctx=ctx)
    fm.write(b'data', b'%s', data)
    fm.data(path=path)


def cat(ui, repo, ctx, matcher, basefm, fntemplate, prefix, **opts):
    err = 1
    opts = pycompat.byteskwargs(opts)

    def write(path):
        filename = None
        if fntemplate:
            filename = makefilename(
                ctx, fntemplate, pathname=os.path.join(prefix, path)
            )
            # attempt to create the directory if it does not already exist
            try:
                os.makedirs(os.path.dirname(filename))
            except OSError:
                pass
        with formatter.maybereopen(basefm, filename) as fm:
            _updatecatformatter(fm, ctx, matcher, path, opts.get(b'decode'))

    # Automation often uses hg cat on single files, so special case it
    # for performance to avoid the cost of parsing the manifest.
    if len(matcher.files()) == 1 and not matcher.anypats():
        file = matcher.files()[0]
        mfl = repo.manifestlog
        mfnode = ctx.manifestnode()
        try:
            if mfnode and mfl[mfnode].find(file)[0]:
                if _catfmtneedsdata(basefm):
                    scmutil.prefetchfiles(repo, [(ctx.rev(), matcher)])
                write(file)
                return 0
        except KeyError:
            pass

    if _catfmtneedsdata(basefm):
        scmutil.prefetchfiles(repo, [(ctx.rev(), matcher)])

    for abs in ctx.walk(matcher):
        write(abs)
        err = 0

    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)
    for subpath in sorted(ctx.substate):
        sub = ctx.sub(subpath)
        try:
            submatch = matchmod.subdirmatcher(subpath, matcher)
            subprefix = os.path.join(prefix, subpath)
            if not sub.cat(
                submatch,
                basefm,
                fntemplate,
                subprefix,
                **pycompat.strkwargs(opts)
            ):
                err = 0
        except error.RepoLookupError:
            ui.status(
                _(b"skipping missing subrepository: %s\n") % uipathfn(subpath)
            )

    return err


def commit(ui, repo, commitfunc, pats, opts):
    '''commit the specified files or all outstanding changes'''
    date = opts.get(b'date')
    if date:
        opts[b'date'] = dateutil.parsedate(date)
    message = logmessage(ui, opts)
    matcher = scmutil.match(repo[None], pats, opts)

    dsguard = None
    # extract addremove carefully -- this function can be called from a command
    # that doesn't support addremove
    if opts.get(b'addremove'):
        dsguard = dirstateguard.dirstateguard(repo, b'commit')
    with dsguard or util.nullcontextmanager():
        if dsguard:
            relative = scmutil.anypats(pats, opts)
            uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=relative)
            if scmutil.addremove(repo, matcher, b"", uipathfn, opts) != 0:
                raise error.Abort(
                    _(b"failed to mark all new/missing files as added/removed")
                )

        return commitfunc(ui, repo, message, matcher, opts)


def samefile(f, ctx1, ctx2):
    if f in ctx1.manifest():
        a = ctx1.filectx(f)
        if f in ctx2.manifest():
            b = ctx2.filectx(f)
            return not a.cmp(b) and a.flags() == b.flags()
        else:
            return False
    else:
        return f not in ctx2.manifest()


def amend(ui, repo, old, extra, pats, opts):
    # avoid cycle context -> subrepo -> cmdutil
    from . import context

    # amend will reuse the existing user if not specified, but the obsolete
    # marker creation requires that the current user's name is specified.
    if obsolete.isenabled(repo, obsolete.createmarkersopt):
        ui.username()  # raise exception if username not set

    ui.note(_(b'amending changeset %s\n') % old)
    base = old.p1()

    with repo.wlock(), repo.lock(), repo.transaction(b'amend'):
        # Participating changesets:
        #
        # wctx     o - workingctx that contains changes from working copy
        #          |   to go into amending commit
        #          |
        # old      o - changeset to amend
        #          |
        # base     o - first parent of the changeset to amend
        wctx = repo[None]

        # Copy to avoid mutating input
        extra = extra.copy()
        # Update extra dict from amended commit (e.g. to preserve graft
        # source)
        extra.update(old.extra())

        # Also update it from the from the wctx
        extra.update(wctx.extra())

        # date-only change should be ignored?
        datemaydiffer = resolve_commit_options(ui, opts)
        opts = pycompat.byteskwargs(opts)

        date = old.date()
        if opts.get(b'date'):
            date = dateutil.parsedate(opts.get(b'date'))
        user = opts.get(b'user') or old.user()

        if len(old.parents()) > 1:
            # ctx.files() isn't reliable for merges, so fall back to the
            # slower repo.status() method
            st = base.status(old)
            files = set(st.modified) | set(st.added) | set(st.removed)
        else:
            files = set(old.files())

        # add/remove the files to the working copy if the "addremove" option
        # was specified.
        matcher = scmutil.match(wctx, pats, opts)
        relative = scmutil.anypats(pats, opts)
        uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=relative)
        if opts.get(b'addremove') and scmutil.addremove(
            repo, matcher, b"", uipathfn, opts
        ):
            raise error.Abort(
                _(b"failed to mark all new/missing files as added/removed")
            )

        # Check subrepos. This depends on in-place wctx._status update in
        # subrepo.precommit(). To minimize the risk of this hack, we do
        # nothing if .hgsub does not exist.
        if b'.hgsub' in wctx or b'.hgsub' in old:
            subs, commitsubs, newsubstate = subrepoutil.precommit(
                ui, wctx, wctx._status, matcher
            )
            # amend should abort if commitsubrepos is enabled
            assert not commitsubs
            if subs:
                subrepoutil.writestate(repo, newsubstate)

        ms = mergestatemod.mergestate.read(repo)
        mergeutil.checkunresolved(ms)

        filestoamend = {f for f in wctx.files() if matcher(f)}

        changes = len(filestoamend) > 0
        if changes:
            # Recompute copies (avoid recording a -> b -> a)
            copied = copies.pathcopies(base, wctx, matcher)
            if old.p2:
                copied.update(copies.pathcopies(old.p2(), wctx, matcher))

            # Prune files which were reverted by the updates: if old
            # introduced file X and the file was renamed in the working
            # copy, then those two files are the same and
            # we can discard X from our list of files. Likewise if X
            # was removed, it's no longer relevant. If X is missing (aka
            # deleted), old X must be preserved.
            files.update(filestoamend)
            files = [
                f
                for f in files
                if (f not in filestoamend or not samefile(f, wctx, base))
            ]

            def filectxfn(repo, ctx_, path):
                try:
                    # If the file being considered is not amongst the files
                    # to be amended, we should return the file context from the
                    # old changeset. This avoids issues when only some files in
                    # the working copy are being amended but there are also
                    # changes to other files from the old changeset.
                    if path not in filestoamend:
                        return old.filectx(path)

                    # Return None for removed files.
                    if path in wctx.removed():
                        return None

                    fctx = wctx[path]
                    flags = fctx.flags()
                    mctx = context.memfilectx(
                        repo,
                        ctx_,
                        fctx.path(),
                        fctx.data(),
                        islink=b'l' in flags,
                        isexec=b'x' in flags,
                        copysource=copied.get(path),
                    )
                    return mctx
                except KeyError:
                    return None

        else:
            ui.note(_(b'copying changeset %s to %s\n') % (old, base))

            # Use version of files as in the old cset
            def filectxfn(repo, ctx_, path):
                try:
                    return old.filectx(path)
                except KeyError:
                    return None

        # See if we got a message from -m or -l, if not, open the editor with
        # the message of the changeset to amend.
        message = logmessage(ui, opts)

        editform = mergeeditform(old, b'commit.amend')

        if not message:
            message = old.description()
            # Default if message isn't provided and --edit is not passed is to
            # invoke editor, but allow --no-edit. If somehow we don't have any
            # description, let's always start the editor.
            doedit = not message or opts.get(b'edit') in [True, None]
        else:
            # Default if message is provided is to not invoke editor, but allow
            # --edit.
            doedit = opts.get(b'edit') is True
        editor = getcommiteditor(edit=doedit, editform=editform)

        pureextra = extra.copy()
        extra[b'amend_source'] = old.hex()

        new = context.memctx(
            repo,
            parents=[base.node(), old.p2().node()],
            text=message,
            files=files,
            filectxfn=filectxfn,
            user=user,
            date=date,
            extra=extra,
            editor=editor,
        )

        newdesc = changelog.stripdesc(new.description())
        if (
            (not changes)
            and newdesc == old.description()
            and user == old.user()
            and (date == old.date() or datemaydiffer)
            and pureextra == old.extra()
        ):
            # nothing changed. continuing here would create a new node
            # anyway because of the amend_source noise.
            #
            # This not what we expect from amend.
            return old.node()

        commitphase = None
        if opts.get(b'secret'):
            commitphase = phases.secret
        newid = repo.commitctx(new)
        ms.reset()

        with repo.dirstate.parentchange():
            # Reroute the working copy parent to the new changeset
            repo.setparents(newid, repo.nullid)

            # Fixing the dirstate because localrepo.commitctx does not update
            # it. This is rather convenient because we did not need to update
            # the dirstate for all the files in the new commit which commitctx
            # could have done if it updated the dirstate. Now, we can
            # selectively update the dirstate only for the amended files.
            dirstate = repo.dirstate

            # Update the state of the files which were added and modified in the
            # amend to "normal" in the dirstate. We need to use "normallookup" since
            # the files may have changed since the command started; using "normal"
            # would mark them as clean but with uncommitted contents.
            normalfiles = set(wctx.modified() + wctx.added()) & filestoamend
            for f in normalfiles:
                dirstate.update_file(
                    f, p1_tracked=True, wc_tracked=True, possibly_dirty=True
                )

            # Update the state of files which were removed in the amend
            # to "removed" in the dirstate.
            removedfiles = set(wctx.removed()) & filestoamend
            for f in removedfiles:
                dirstate.update_file(f, p1_tracked=False, wc_tracked=False)

        mapping = {old.node(): (newid,)}
        obsmetadata = None
        if opts.get(b'note'):
            obsmetadata = {b'note': encoding.fromlocal(opts[b'note'])}
        backup = ui.configbool(b'rewrite', b'backup-bundle')
        scmutil.cleanupnodes(
            repo,
            mapping,
            b'amend',
            metadata=obsmetadata,
            fixphase=True,
            targetphase=commitphase,
            backup=backup,
        )

    return newid


def commiteditor(repo, ctx, subs, editform=b''):
    if ctx.description():
        return ctx.description()
    return commitforceeditor(
        repo, ctx, subs, editform=editform, unchangedmessagedetection=True
    )


def commitforceeditor(
    repo,
    ctx,
    subs,
    finishdesc=None,
    extramsg=None,
    editform=b'',
    unchangedmessagedetection=False,
):
    if not extramsg:
        extramsg = _(b"Leave message empty to abort commit.")

    forms = [e for e in editform.split(b'.') if e]
    forms.insert(0, b'changeset')
    templatetext = None
    while forms:
        ref = b'.'.join(forms)
        if repo.ui.config(b'committemplate', ref):
            templatetext = committext = buildcommittemplate(
                repo, ctx, subs, extramsg, ref
            )
            break
        forms.pop()
    else:
        committext = buildcommittext(repo, ctx, subs, extramsg)

    # run editor in the repository root
    olddir = encoding.getcwd()
    os.chdir(repo.root)

    # make in-memory changes visible to external process
    tr = repo.currenttransaction()
    repo.dirstate.write(tr)
    pending = tr and tr.writepending() and repo.root

    editortext = repo.ui.edit(
        committext,
        ctx.user(),
        ctx.extra(),
        editform=editform,
        pending=pending,
        repopath=repo.path,
        action=b'commit',
    )
    text = editortext

    # strip away anything below this special string (used for editors that want
    # to display the diff)
    stripbelow = re.search(_linebelow, text, flags=re.MULTILINE)
    if stripbelow:
        text = text[: stripbelow.start()]

    text = re.sub(b"(?m)^HG:.*(\n|$)", b"", text)
    os.chdir(olddir)

    if finishdesc:
        text = finishdesc(text)
    if not text.strip():
        raise error.InputError(_(b"empty commit message"))
    if unchangedmessagedetection and editortext == templatetext:
        raise error.InputError(_(b"commit message unchanged"))

    return text


def buildcommittemplate(repo, ctx, subs, extramsg, ref):
    ui = repo.ui
    spec = formatter.reference_templatespec(ref)
    t = logcmdutil.changesettemplater(ui, repo, spec)
    t.t.cache.update(
        (k, templater.unquotestring(v))
        for k, v in repo.ui.configitems(b'committemplate')
    )

    if not extramsg:
        extramsg = b''  # ensure that extramsg is string

    ui.pushbuffer()
    t.show(ctx, extramsg=extramsg)
    return ui.popbuffer()


def hgprefix(msg):
    return b"\n".join([b"HG: %s" % a for a in msg.split(b"\n") if a])


def buildcommittext(repo, ctx, subs, extramsg):
    edittext = []
    modified, added, removed = ctx.modified(), ctx.added(), ctx.removed()
    if ctx.description():
        edittext.append(ctx.description())
    edittext.append(b"")
    edittext.append(b"")  # Empty line between message and comments.
    edittext.append(
        hgprefix(
            _(
                b"Enter commit message."
                b"  Lines beginning with 'HG:' are removed."
            )
        )
    )
    edittext.append(hgprefix(extramsg))
    edittext.append(b"HG: --")
    edittext.append(hgprefix(_(b"user: %s") % ctx.user()))
    if ctx.p2():
        edittext.append(hgprefix(_(b"branch merge")))
    if ctx.branch():
        edittext.append(hgprefix(_(b"branch '%s'") % ctx.branch()))
    if bookmarks.isactivewdirparent(repo):
        edittext.append(hgprefix(_(b"bookmark '%s'") % repo._activebookmark))
    edittext.extend([hgprefix(_(b"subrepo %s") % s) for s in subs])
    edittext.extend([hgprefix(_(b"added %s") % f) for f in added])
    edittext.extend([hgprefix(_(b"changed %s") % f) for f in modified])
    edittext.extend([hgprefix(_(b"removed %s") % f) for f in removed])
    if not added and not modified and not removed:
        edittext.append(hgprefix(_(b"no files changed")))
    edittext.append(b"")

    return b"\n".join(edittext)


def commitstatus(repo, node, branch, bheads=None, tip=None, opts=None):
    if opts is None:
        opts = {}
    ctx = repo[node]
    parents = ctx.parents()

    if tip is not None and repo.changelog.tip() == tip:
        # avoid reporting something like "committed new head" when
        # recommitting old changesets, and issue a helpful warning
        # for most instances
        repo.ui.warn(_(b"warning: commit already existed in the repository!\n"))
    elif (
        not opts.get(b'amend')
        and bheads
        and node not in bheads
        and not any(
            p.node() in bheads and p.branch() == branch for p in parents
        )
    ):
        repo.ui.status(_(b'created new head\n'))
        # The message is not printed for initial roots. For the other
        # changesets, it is printed in the following situations:
        #
        # Par column: for the 2 parents with ...
        #   N: null or no parent
        #   B: parent is on another named branch
        #   C: parent is a regular non head changeset
        #   H: parent was a branch head of the current branch
        # Msg column: whether we print "created new head" message
        # In the following, it is assumed that there already exists some
        # initial branch heads of the current branch, otherwise nothing is
        # printed anyway.
        #
        # Par Msg Comment
        # N N  y  additional topo root
        #
        # B N  y  additional branch root
        # C N  y  additional topo head
        # H N  n  usual case
        #
        # B B  y  weird additional branch root
        # C B  y  branch merge
        # H B  n  merge with named branch
        #
        # C C  y  additional head from merge
        # C H  n  merge with a head
        #
        # H H  n  head merge: head count decreases

    if not opts.get(b'close_branch'):
        for r in parents:
            if r.closesbranch() and r.branch() == branch:
                repo.ui.status(
                    _(b'reopening closed branch head %d\n') % r.rev()
                )

    if repo.ui.debugflag:
        repo.ui.write(
            _(b'committed changeset %d:%s\n') % (ctx.rev(), ctx.hex())
        )
    elif repo.ui.verbose:
        repo.ui.write(_(b'committed changeset %d:%s\n') % (ctx.rev(), ctx))


def postcommitstatus(repo, pats, opts):
    return repo.status(match=scmutil.match(repo[None], pats, opts))


def revert(ui, repo, ctx, *pats, **opts):
    opts = pycompat.byteskwargs(opts)
    parent, p2 = repo.dirstate.parents()
    node = ctx.node()

    mf = ctx.manifest()
    if node == p2:
        parent = p2

    # need all matching names in dirstate and manifest of target rev,
    # so have to walk both. do not print errors if files exist in one
    # but not other. in both cases, filesets should be evaluated against
    # workingctx to get consistent result (issue4497). this means 'set:**'
    # cannot be used to select missing files from target rev.

    # `names` is a mapping for all elements in working copy and target revision
    # The mapping is in the form:
    #   <abs path in repo> -> (<path from CWD>, <exactly specified by matcher?>)
    names = {}
    uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)

    with repo.wlock():
        ## filling of the `names` mapping
        # walk dirstate to fill `names`

        interactive = opts.get(b'interactive', False)
        wctx = repo[None]
        m = scmutil.match(wctx, pats, opts)

        # we'll need this later
        targetsubs = sorted(s for s in wctx.substate if m(s))

        if not m.always():
            matcher = matchmod.badmatch(m, lambda x, y: False)
            for abs in wctx.walk(matcher):
                names[abs] = m.exact(abs)

            # walk target manifest to fill `names`

            def badfn(path, msg):
                if path in names:
                    return
                if path in ctx.substate:
                    return
                path_ = path + b'/'
                for f in names:
                    if f.startswith(path_):
                        return
                ui.warn(b"%s: %s\n" % (uipathfn(path), msg))

            for abs in ctx.walk(matchmod.badmatch(m, badfn)):
                if abs not in names:
                    names[abs] = m.exact(abs)

            # Find status of all file in `names`.
            m = scmutil.matchfiles(repo, names)

            changes = repo.status(
                node1=node, match=m, unknown=True, ignored=True, clean=True
            )
        else:
            changes = repo.status(node1=node, match=m)
            for kind in changes:
                for abs in kind:
                    names[abs] = m.exact(abs)

            m = scmutil.matchfiles(repo, names)

        modified = set(changes.modified)
        added = set(changes.added)
        removed = set(changes.removed)
        _deleted = set(changes.deleted)
        unknown = set(changes.unknown)
        unknown.update(changes.ignored)
        clean = set(changes.clean)
        modadded = set()

        # We need to account for the state of the file in the dirstate,
        # even when we revert against something else than parent. This will
        # slightly alter the behavior of revert (doing back up or not, delete
        # or just forget etc).
        if parent == node:
            dsmodified = modified
            dsadded = added
            dsremoved = removed
            # store all local modifications, useful later for rename detection
            localchanges = dsmodified | dsadded
            modified, added, removed = set(), set(), set()
        else:
            changes = repo.status(node1=parent, match=m)
            dsmodified = set(changes.modified)
            dsadded = set(changes.added)
            dsremoved = set(changes.removed)
            # store all local modifications, useful later for rename detection
            localchanges = dsmodified | dsadded

            # only take into account for removes between wc and target
            clean |= dsremoved - removed
            dsremoved &= removed
            # distinct between dirstate remove and other
            removed -= dsremoved

            modadded = added & dsmodified
            added -= modadded

            # tell newly modified apart.
            dsmodified &= modified
            dsmodified |= modified & dsadded  # dirstate added may need backup
            modified -= dsmodified

            # We need to wait for some post-processing to update this set
            # before making the distinction. The dirstate will be used for
            # that purpose.
            dsadded = added

        # in case of merge, files that are actually added can be reported as
        # modified, we need to post process the result
        if p2 != repo.nullid:
            mergeadd = set(dsmodified)
            for path in dsmodified:
                if path in mf:
                    mergeadd.remove(path)
            dsadded |= mergeadd
            dsmodified -= mergeadd

        # if f is a rename, update `names` to also revert the source
        for f in localchanges:
            src = repo.dirstate.copied(f)
            # XXX should we check for rename down to target node?
            if src and src not in names and repo.dirstate[src] == b'r':
                dsremoved.add(src)
                names[src] = True

        # determine the exact nature of the deleted changesets
        deladded = set(_deleted)
        for path in _deleted:
            if path in mf:
                deladded.remove(path)
        deleted = _deleted - deladded

        # distinguish between file to forget and the other
        added = set()
        for abs in dsadded:
            if repo.dirstate[abs] != b'a':
                added.add(abs)
        dsadded -= added

        for abs in deladded:
            if repo.dirstate[abs] == b'a':
                dsadded.add(abs)
        deladded -= dsadded

        # For files marked as removed, we check if an unknown file is present at
        # the same path. If a such file exists it may need to be backed up.
        # Making the distinction at this stage helps have simpler backup
        # logic.
        removunk = set()
        for abs in removed:
            target = repo.wjoin(abs)
            if os.path.lexists(target):
                removunk.add(abs)
        removed -= removunk

        dsremovunk = set()
        for abs in dsremoved:
            target = repo.wjoin(abs)
            if os.path.lexists(target):
                dsremovunk.add(abs)
        dsremoved -= dsremovunk

        # action to be actually performed by revert
        # (<list of file>, message>) tuple
        actions = {
            b'revert': ([], _(b'reverting %s\n')),
            b'add': ([], _(b'adding %s\n')),
            b'remove': ([], _(b'removing %s\n')),
            b'drop': ([], _(b'removing %s\n')),
            b'forget': ([], _(b'forgetting %s\n')),
            b'undelete': ([], _(b'undeleting %s\n')),
            b'noop': (None, _(b'no changes needed to %s\n')),
            b'unknown': (None, _(b'file not managed: %s\n')),
        }

        # "constant" that convey the backup strategy.
        # All set to `discard` if `no-backup` is set do avoid checking
        # no_backup lower in the code.
        # These values are ordered for comparison purposes
        backupinteractive = 3  # do backup if interactively modified
        backup = 2  # unconditionally do backup
        check = 1  # check if the existing file differs from target
        discard = 0  # never do backup
        if opts.get(b'no_backup'):
            backupinteractive = backup = check = discard
        if interactive:
            dsmodifiedbackup = backupinteractive
        else:
            dsmodifiedbackup = backup
        tobackup = set()

        backupanddel = actions[b'remove']
        if not opts.get(b'no_backup'):
            backupanddel = actions[b'drop']

        disptable = (
            # dispatch table:
            #   file state
            #   action
            #   make backup
            ## Sets that results that will change file on disk
            # Modified compared to target, no local change
            (modified, actions[b'revert'], discard),
            # Modified compared to target, but local file is deleted
            (deleted, actions[b'revert'], discard),
            # Modified compared to target, local change
            (dsmodified, actions[b'revert'], dsmodifiedbackup),
            # Added since target
            (added, actions[b'remove'], discard),
            # Added in working directory
            (dsadded, actions[b'forget'], discard),
            # Added since target, have local modification
            (modadded, backupanddel, backup),
            # Added since target but file is missing in working directory
            (deladded, actions[b'drop'], discard),
            # Removed since  target, before working copy parent
            (removed, actions[b'add'], discard),
            # Same as `removed` but an unknown file exists at the same path
            (removunk, actions[b'add'], check),
            # Removed since targe, marked as such in working copy parent
            (dsremoved, actions[b'undelete'], discard),
            # Same as `dsremoved` but an unknown file exists at the same path
            (dsremovunk, actions[b'undelete'], check),
            ## the following sets does not result in any file changes
            # File with no modification
            (clean, actions[b'noop'], discard),
            # Existing file, not tracked anywhere
            (unknown, actions[b'unknown'], discard),
        )

        for abs, exact in sorted(names.items()):
            # target file to be touch on disk (relative to cwd)
            target = repo.wjoin(abs)
            # search the entry in the dispatch table.
            # if the file is in any of these sets, it was touched in the working
            # directory parent and we are sure it needs to be reverted.
            for table, (xlist, msg), dobackup in disptable:
                if abs not in table:
                    continue
                if xlist is not None:
                    xlist.append(abs)
                    if dobackup:
                        # If in interactive mode, don't automatically create
                        # .orig files (issue4793)
                        if dobackup == backupinteractive:
                            tobackup.add(abs)
                        elif backup <= dobackup or wctx[abs].cmp(ctx[abs]):
                            absbakname = scmutil.backuppath(ui, repo, abs)
                            bakname = os.path.relpath(
                                absbakname, start=repo.root
                            )
                            ui.note(
                                _(b'saving current version of %s as %s\n')
                                % (uipathfn(abs), uipathfn(bakname))
                            )
                            if not opts.get(b'dry_run'):
                                if interactive:
                                    util.copyfile(target, absbakname)
                                else:
                                    util.rename(target, absbakname)
                    if opts.get(b'dry_run'):
                        if ui.verbose or not exact:
                            ui.status(msg % uipathfn(abs))
                elif exact:
                    ui.warn(msg % uipathfn(abs))
                break

        if not opts.get(b'dry_run'):
            needdata = (b'revert', b'add', b'undelete')
            oplist = [actions[name][0] for name in needdata]
            prefetch = scmutil.prefetchfiles
            matchfiles = scmutil.matchfiles(
                repo, [f for sublist in oplist for f in sublist]
            )
            prefetch(
                repo,
                [(ctx.rev(), matchfiles)],
            )
            match = scmutil.match(repo[None], pats)
            _performrevert(
                repo,
                ctx,
                names,
                uipathfn,
                actions,
                match,
                interactive,
                tobackup,
            )

        if targetsubs:
            # Revert the subrepos on the revert list
            for sub in targetsubs:
                try:
                    wctx.sub(sub).revert(
                        ctx.substate[sub], *pats, **pycompat.strkwargs(opts)
                    )
                except KeyError:
                    raise error.Abort(
                        b"subrepository '%s' does not exist in %s!"
                        % (sub, short(ctx.node()))
                    )


def _performrevert(
    repo,
    ctx,
    names,
    uipathfn,
    actions,
    match,
    interactive=False,
    tobackup=None,
):
    """function that actually perform all the actions computed for revert

    This is an independent function to let extension to plug in and react to
    the imminent revert.

    Make sure you have the working directory locked when calling this function.
    """
    parent, p2 = repo.dirstate.parents()
    node = ctx.node()
    excluded_files = []

    def checkout(f):
        fc = ctx[f]
        repo.wwrite(f, fc.data(), fc.flags())

    def doremove(f):
        try:
            rmdir = repo.ui.configbool(b'experimental', b'removeemptydirs')
            repo.wvfs.unlinkpath(f, rmdir=rmdir)
        except OSError:
            pass
        repo.dirstate.set_untracked(f)

    def prntstatusmsg(action, f):
        exact = names[f]
        if repo.ui.verbose or not exact:
            repo.ui.status(actions[action][1] % uipathfn(f))

    audit_path = pathutil.pathauditor(repo.root, cached=True)
    for f in actions[b'forget'][0]:
        if interactive:
            choice = repo.ui.promptchoice(
                _(b"forget added file %s (Yn)?$$ &Yes $$ &No") % uipathfn(f)
            )
            if choice == 0:
                prntstatusmsg(b'forget', f)
                repo.dirstate.set_untracked(f)
            else:
                excluded_files.append(f)
        else:
            prntstatusmsg(b'forget', f)
            repo.dirstate.set_untracked(f)
    for f in actions[b'remove'][0]:
        audit_path(f)
        if interactive:
            choice = repo.ui.promptchoice(
                _(b"remove added file %s (Yn)?$$ &Yes $$ &No") % uipathfn(f)
            )
            if choice == 0:
                prntstatusmsg(b'remove', f)
                doremove(f)
            else:
                excluded_files.append(f)
        else:
            prntstatusmsg(b'remove', f)
            doremove(f)
    for f in actions[b'drop'][0]:
        audit_path(f)
        prntstatusmsg(b'drop', f)
        repo.dirstate.set_untracked(f)

    normal = None
    if node == parent:
        # We're reverting to our parent. If possible, we'd like status
        # to report the file as clean. We have to use normallookup for
        # merges to avoid losing information about merged/dirty files.
        if p2 != repo.nullid:
            normal = repo.dirstate.set_tracked
        else:
            normal = repo.dirstate.set_clean

    newlyaddedandmodifiedfiles = set()
    if interactive:
        # Prompt the user for changes to revert
        torevert = [f for f in actions[b'revert'][0] if f not in excluded_files]
        m = scmutil.matchfiles(repo, torevert)
        diffopts = patch.difffeatureopts(
            repo.ui,
            whitespace=True,
            section=b'commands',
            configprefix=b'revert.interactive.',
        )
        diffopts.nodates = True
        diffopts.git = True
        operation = b'apply'
        if node == parent:
            if repo.ui.configbool(
                b'experimental', b'revert.interactive.select-to-keep'
            ):
                operation = b'keep'
            else:
                operation = b'discard'

        if operation == b'apply':
            diff = patch.diff(repo, None, ctx.node(), m, opts=diffopts)
        else:
            diff = patch.diff(repo, ctx.node(), None, m, opts=diffopts)
        original_headers = patch.parsepatch(diff)

        try:

            chunks, opts = recordfilter(
                repo.ui, original_headers, match, operation=operation
            )
            if operation == b'discard':
                chunks = patch.reversehunks(chunks)

        except error.PatchError as err:
            raise error.Abort(_(b'error parsing patch: %s') % err)

        # FIXME: when doing an interactive revert of a copy, there's no way of
        # performing a partial revert of the added file, the only option is
        # "remove added file <name> (Yn)?", so we don't need to worry about the
        # alsorestore value. Ideally we'd be able to partially revert
        # copied/renamed files.
        newlyaddedandmodifiedfiles, unusedalsorestore = newandmodified(chunks)
        if tobackup is None:
            tobackup = set()
        # Apply changes
        fp = stringio()
        # chunks are serialized per file, but files aren't sorted
        for f in sorted({c.header.filename() for c in chunks if ishunk(c)}):
            prntstatusmsg(b'revert', f)
        files = set()
        for c in chunks:
            if ishunk(c):
                abs = c.header.filename()
                # Create a backup file only if this hunk should be backed up
                if c.header.filename() in tobackup:
                    target = repo.wjoin(abs)
                    bakname = scmutil.backuppath(repo.ui, repo, abs)
                    util.copyfile(target, bakname)
                    tobackup.remove(abs)
                if abs not in files:
                    files.add(abs)
                    if operation == b'keep':
                        checkout(abs)
            c.write(fp)
        dopatch = fp.tell()
        fp.seek(0)
        if dopatch:
            try:
                patch.internalpatch(repo.ui, repo, fp, 1, eolmode=None)
            except error.PatchError as err:
                raise error.Abort(pycompat.bytestr(err))
        del fp
    else:
        for f in actions[b'revert'][0]:
            prntstatusmsg(b'revert', f)
            checkout(f)
            if normal:
                normal(f)

    for f in actions[b'add'][0]:
        # Don't checkout modified files, they are already created by the diff
        if f not in newlyaddedandmodifiedfiles:
            prntstatusmsg(b'add', f)
            checkout(f)
            repo.dirstate.set_tracked(f)

    normal = repo.dirstate.set_tracked
    if node == parent and p2 == repo.nullid:
        normal = repo.dirstate.set_clean
    for f in actions[b'undelete'][0]:
        if interactive:
            choice = repo.ui.promptchoice(
                _(b"add back removed file %s (Yn)?$$ &Yes $$ &No") % f
            )
            if choice == 0:
                prntstatusmsg(b'undelete', f)
                checkout(f)
                normal(f)
            else:
                excluded_files.append(f)
        else:
            prntstatusmsg(b'undelete', f)
            checkout(f)
            normal(f)

    copied = copies.pathcopies(repo[parent], ctx)

    for f in (
        actions[b'add'][0] + actions[b'undelete'][0] + actions[b'revert'][0]
    ):
        if f in copied:
            repo.dirstate.copy(copied[f], f)


# a list of (ui, repo, otherpeer, opts, missing) functions called by
# commands.outgoing.  "missing" is "missing" of the result of
# "findcommonoutgoing()"
outgoinghooks = util.hooks()

# a list of (ui, repo) functions called by commands.summary
summaryhooks = util.hooks()

# a list of (ui, repo, opts, changes) functions called by commands.summary.
#
# functions should return tuple of booleans below, if 'changes' is None:
#  (whether-incomings-are-needed, whether-outgoings-are-needed)
#
# otherwise, 'changes' is a tuple of tuples below:
#  - (sourceurl, sourcebranch, sourcepeer, incoming)
#  - (desturl,   destbranch,   destpeer,   outgoing)
summaryremotehooks = util.hooks()


def checkunfinished(repo, commit=False, skipmerge=False):
    """Look for an unfinished multistep operation, like graft, and abort
    if found. It's probably good to check this right before
    bailifchanged().
    """
    # Check for non-clearable states first, so things like rebase will take
    # precedence over update.
    for state in statemod._unfinishedstates:
        if (
            state._clearable
            or (commit and state._allowcommit)
            or state._reportonly
        ):
            continue
        if state.isunfinished(repo):
            raise error.StateError(state.msg(), hint=state.hint())

    for s in statemod._unfinishedstates:
        if (
            not s._clearable
            or (commit and s._allowcommit)
            or (s._opname == b'merge' and skipmerge)
            or s._reportonly
        ):
            continue
        if s.isunfinished(repo):
            raise error.StateError(s.msg(), hint=s.hint())


def clearunfinished(repo):
    """Check for unfinished operations (as above), and clear the ones
    that are clearable.
    """
    for state in statemod._unfinishedstates:
        if state._reportonly:
            continue
        if not state._clearable and state.isunfinished(repo):
            raise error.StateError(state.msg(), hint=state.hint())

    for s in statemod._unfinishedstates:
        if s._opname == b'merge' or s._reportonly:
            continue
        if s._clearable and s.isunfinished(repo):
            util.unlink(repo.vfs.join(s._fname))


def getunfinishedstate(repo):
    """Checks for unfinished operations and returns statecheck object
    for it"""
    for state in statemod._unfinishedstates:
        if state.isunfinished(repo):
            return state
    return None


def howtocontinue(repo):
    """Check for an unfinished operation and return the command to finish
    it.

    statemod._unfinishedstates list is checked for an unfinished operation
    and the corresponding message to finish it is generated if a method to
    continue is supported by the operation.

    Returns a (msg, warning) tuple. 'msg' is a string and 'warning' is
    a boolean.
    """
    contmsg = _(b"continue: %s")
    for state in statemod._unfinishedstates:
        if not state._continueflag:
            continue
        if state.isunfinished(repo):
            return contmsg % state.continuemsg(), True
    if repo[None].dirty(missing=True, merge=False, branch=False):
        return contmsg % _(b"hg commit"), False
    return None, None


def checkafterresolved(repo):
    """Inform the user about the next action after completing hg resolve

    If there's a an unfinished operation that supports continue flag,
    howtocontinue will yield repo.ui.warn as the reporter.

    Otherwise, it will yield repo.ui.note.
    """
    msg, warning = howtocontinue(repo)
    if msg is not None:
        if warning:
            repo.ui.warn(b"%s\n" % msg)
        else:
            repo.ui.note(b"%s\n" % msg)


def wrongtooltocontinue(repo, task):
    """Raise an abort suggesting how to properly continue if there is an
    active task.

    Uses howtocontinue() to find the active task.

    If there's no task (repo.ui.note for 'hg commit'), it does not offer
    a hint.
    """
    after = howtocontinue(repo)
    hint = None
    if after[1]:
        hint = after[0]
    raise error.StateError(_(b'no %s in progress') % task, hint=hint)


def abortgraft(ui, repo, graftstate):
    """abort the interrupted graft and rollbacks to the state before interrupted
    graft"""
    if not graftstate.exists():
        raise error.StateError(_(b"no interrupted graft to abort"))
    statedata = readgraftstate(repo, graftstate)
    newnodes = statedata.get(b'newnodes')
    if newnodes is None:
        # and old graft state which does not have all the data required to abort
        # the graft
        raise error.Abort(_(b"cannot abort using an old graftstate"))

    # changeset from which graft operation was started
    if len(newnodes) > 0:
        startctx = repo[newnodes[0]].p1()
    else:
        startctx = repo[b'.']
    # whether to strip or not
    cleanup = False

    if newnodes:
        newnodes = [repo[r].rev() for r in newnodes]
        cleanup = True
        # checking that none of the newnodes turned public or is public
        immutable = [c for c in newnodes if not repo[c].mutable()]
        if immutable:
            repo.ui.warn(
                _(b"cannot clean up public changesets %s\n")
                % b', '.join(bytes(repo[r]) for r in immutable),
                hint=_(b"see 'hg help phases' for details"),
            )
            cleanup = False

        # checking that no new nodes are created on top of grafted revs
        desc = set(repo.changelog.descendants(newnodes))
        if desc - set(newnodes):
            repo.ui.warn(
                _(
                    b"new changesets detected on destination "
                    b"branch, can't strip\n"
                )
            )
            cleanup = False

        if cleanup:
            with repo.wlock(), repo.lock():
                mergemod.clean_update(startctx)
                # stripping the new nodes created
                strippoints = [
                    c.node() for c in repo.set(b"roots(%ld)", newnodes)
                ]
                repair.strip(repo.ui, repo, strippoints, backup=False)

    if not cleanup:
        # we don't update to the startnode if we can't strip
        startctx = repo[b'.']
        mergemod.clean_update(startctx)

    ui.status(_(b"graft aborted\n"))
    ui.status(_(b"working directory is now at %s\n") % startctx.hex()[:12])
    graftstate.delete()
    return 0


def readgraftstate(repo, graftstate):
    # type: (Any, statemod.cmdstate) -> Dict[bytes, Any]
    """read the graft state file and return a dict of the data stored in it"""
    try:
        return graftstate.read()
    except error.CorruptedState:
        nodes = repo.vfs.read(b'graftstate').splitlines()
        return {b'nodes': nodes}


def hgabortgraft(ui, repo):
    """abort logic for aborting graft using 'hg abort'"""
    with repo.wlock():
        graftstate = statemod.cmdstate(repo, b'graftstate')
        return abortgraft(ui, repo, graftstate)
