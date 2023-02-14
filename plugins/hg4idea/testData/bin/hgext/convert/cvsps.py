# Mercurial built-in replacement for cvsps.
#
# Copyright 2008, Frank Kingswood <frank@kingswood-consulting.co.uk>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from __future__ import absolute_import

import functools
import os
import re

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial import (
    encoding,
    error,
    hook,
    pycompat,
    util,
)
from mercurial.utils import (
    dateutil,
    procutil,
    stringutil,
)

pickle = util.pickle


class logentry(object):
    """Class logentry has the following attributes:
    .author    - author name as CVS knows it
    .branch    - name of branch this revision is on
    .branches  - revision tuple of branches starting at this revision
    .comment   - commit message
    .commitid  - CVS commitid or None
    .date      - the commit date as a (time, tz) tuple
    .dead      - true if file revision is dead
    .file      - Name of file
    .lines     - a tuple (+lines, -lines) or None
    .parent    - Previous revision of this entry
    .rcs       - name of file as returned from CVS
    .revision  - revision number as tuple
    .tags      - list of tags on the file
    .synthetic - is this a synthetic "file ... added on ..." revision?
    .mergepoint - the branch that has been merged from (if present in
                  rlog output) or None
    .branchpoints - the branches that start at the current entry or empty
    """

    def __init__(self, **entries):
        self.synthetic = False
        self.__dict__.update(entries)

    def __repr__(self):
        items = ("%s=%r" % (k, self.__dict__[k]) for k in sorted(self.__dict__))
        return "%s(%s)" % (type(self).__name__, ", ".join(items))


class logerror(Exception):
    pass


def getrepopath(cvspath):
    """Return the repository path from a CVS path.

    >>> getrepopath(b'/foo/bar')
    '/foo/bar'
    >>> getrepopath(b'c:/foo/bar')
    '/foo/bar'
    >>> getrepopath(b':pserver:10/foo/bar')
    '/foo/bar'
    >>> getrepopath(b':pserver:10c:/foo/bar')
    '/foo/bar'
    >>> getrepopath(b':pserver:/foo/bar')
    '/foo/bar'
    >>> getrepopath(b':pserver:c:/foo/bar')
    '/foo/bar'
    >>> getrepopath(b':pserver:truc@foo.bar:/foo/bar')
    '/foo/bar'
    >>> getrepopath(b':pserver:truc@foo.bar:c:/foo/bar')
    '/foo/bar'
    >>> getrepopath(b'user@server/path/to/repository')
    '/path/to/repository'
    """
    # According to CVS manual, CVS paths are expressed like:
    # [:method:][[user][:password]@]hostname[:[port]]/path/to/repository
    #
    # CVSpath is splitted into parts and then position of the first occurrence
    # of the '/' char after the '@' is located. The solution is the rest of the
    # string after that '/' sign including it

    parts = cvspath.split(b':')
    atposition = parts[-1].find(b'@')
    start = 0

    if atposition != -1:
        start = atposition

    repopath = parts[-1][parts[-1].find(b'/', start) :]
    return repopath


def createlog(ui, directory=None, root=b"", rlog=True, cache=None):
    '''Collect the CVS rlog'''

    # Because we store many duplicate commit log messages, reusing strings
    # saves a lot of memory and pickle storage space.
    _scache = {}

    def scache(s):
        """return a shared version of a string"""
        return _scache.setdefault(s, s)

    ui.status(_(b'collecting CVS rlog\n'))

    log = []  # list of logentry objects containing the CVS state

    # patterns to match in CVS (r)log output, by state of use
    re_00 = re.compile(b'RCS file: (.+)$')
    re_01 = re.compile(b'cvs \\[r?log aborted\\]: (.+)$')
    re_02 = re.compile(b'cvs (r?log|server): (.+)\n$')
    re_03 = re.compile(
        b"(Cannot access.+CVSROOT)|(can't create temporary directory.+)$"
    )
    re_10 = re.compile(b'Working file: (.+)$')
    re_20 = re.compile(b'symbolic names:')
    re_30 = re.compile(b'\t(.+): ([\\d.]+)$')
    re_31 = re.compile(b'----------------------------$')
    re_32 = re.compile(
        b'======================================='
        b'======================================$'
    )
    re_50 = re.compile(br'revision ([\d.]+)(\s+locked by:\s+.+;)?$')
    re_60 = re.compile(
        br'date:\s+(.+);\s+author:\s+(.+);\s+state:\s+(.+?);'
        br'(\s+lines:\s+(\+\d+)?\s+(-\d+)?;)?'
        br'(\s+commitid:\s+([^;]+);)?'
        br'(.*mergepoint:\s+([^;]+);)?'
    )
    re_70 = re.compile(b'branches: (.+);$')

    file_added_re = re.compile(br'file [^/]+ was (initially )?added on branch')

    prefix = b''  # leading path to strip of what we get from CVS

    if directory is None:
        # Current working directory

        # Get the real directory in the repository
        try:
            with open(os.path.join(b'CVS', b'Repository'), b'rb') as f:
                prefix = f.read().strip()
            directory = prefix
            if prefix == b".":
                prefix = b""
        except IOError:
            raise logerror(_(b'not a CVS sandbox'))

        if prefix and not prefix.endswith(pycompat.ossep):
            prefix += pycompat.ossep

        # Use the Root file in the sandbox, if it exists
        try:
            root = open(os.path.join(b'CVS', b'Root'), b'rb').read().strip()
        except IOError:
            pass

    if not root:
        root = encoding.environ.get(b'CVSROOT', b'')

    # read log cache if one exists
    oldlog = []
    date = None

    if cache:
        cachedir = os.path.expanduser(b'~/.hg.cvsps')
        if not os.path.exists(cachedir):
            os.mkdir(cachedir)

        # The cvsps cache pickle needs a uniquified name, based on the
        # repository location. The address may have all sort of nasties
        # in it, slashes, colons and such. So here we take just the
        # alphanumeric characters, concatenated in a way that does not
        # mix up the various components, so that
        #    :pserver:user@server:/path
        # and
        #    /pserver/user/server/path
        # are mapped to different cache file names.
        cachefile = root.split(b":") + [directory, b"cache"]
        cachefile = [b'-'.join(re.findall(br'\w+', s)) for s in cachefile if s]
        cachefile = os.path.join(
            cachedir, b'.'.join([s for s in cachefile if s])
        )

    if cache == b'update':
        try:
            ui.note(_(b'reading cvs log cache %s\n') % cachefile)
            oldlog = pickle.load(open(cachefile, b'rb'))
            for e in oldlog:
                if not (
                    util.safehasattr(e, b'branchpoints')
                    and util.safehasattr(e, b'commitid')
                    and util.safehasattr(e, b'mergepoint')
                ):
                    ui.status(_(b'ignoring old cache\n'))
                    oldlog = []
                    break

            ui.note(_(b'cache has %d log entries\n') % len(oldlog))
        except Exception as e:
            ui.note(_(b'error reading cache: %r\n') % e)

        if oldlog:
            date = oldlog[-1].date  # last commit date as a (time,tz) tuple
            date = dateutil.datestr(date, b'%Y/%m/%d %H:%M:%S %1%2')

    # build the CVS commandline
    cmd = [b'cvs', b'-q']
    if root:
        cmd.append(b'-d%s' % root)
        p = util.normpath(getrepopath(root))
        if not p.endswith(b'/'):
            p += b'/'
        if prefix:
            # looks like normpath replaces "" by "."
            prefix = p + util.normpath(prefix)
        else:
            prefix = p
    cmd.append([b'log', b'rlog'][rlog])
    if date:
        # no space between option and date string
        cmd.append(b'-d>%s' % date)
    cmd.append(directory)

    # state machine begins here
    tags = {}  # dictionary of revisions on current file with their tags
    branchmap = {}  # mapping between branch names and revision numbers
    rcsmap = {}
    state = 0
    store = False  # set when a new record can be appended

    cmd = [procutil.shellquote(arg) for arg in cmd]
    ui.note(_(b"running %s\n") % (b' '.join(cmd)))
    ui.debug(b"prefix=%r directory=%r root=%r\n" % (prefix, directory, root))

    pfp = procutil.popen(b' '.join(cmd), b'rb')
    peek = util.fromnativeeol(pfp.readline())
    while True:
        line = peek
        if line == b'':
            break
        peek = util.fromnativeeol(pfp.readline())
        if line.endswith(b'\n'):
            line = line[:-1]
        # ui.debug('state=%d line=%r\n' % (state, line))

        if state == 0:
            # initial state, consume input until we see 'RCS file'
            match = re_00.match(line)
            if match:
                rcs = match.group(1)
                tags = {}
                if rlog:
                    filename = util.normpath(rcs[:-2])
                    if filename.startswith(prefix):
                        filename = filename[len(prefix) :]
                    if filename.startswith(b'/'):
                        filename = filename[1:]
                    if filename.startswith(b'Attic/'):
                        filename = filename[6:]
                    else:
                        filename = filename.replace(b'/Attic/', b'/')
                    state = 2
                    continue
                state = 1
                continue
            match = re_01.match(line)
            if match:
                raise logerror(match.group(1))
            match = re_02.match(line)
            if match:
                raise logerror(match.group(2))
            if re_03.match(line):
                raise logerror(line)

        elif state == 1:
            # expect 'Working file' (only when using log instead of rlog)
            match = re_10.match(line)
            assert match, _(b'RCS file must be followed by working file')
            filename = util.normpath(match.group(1))
            state = 2

        elif state == 2:
            # expect 'symbolic names'
            if re_20.match(line):
                branchmap = {}
                state = 3

        elif state == 3:
            # read the symbolic names and store as tags
            match = re_30.match(line)
            if match:
                rev = [int(x) for x in match.group(2).split(b'.')]

                # Convert magic branch number to an odd-numbered one
                revn = len(rev)
                if revn > 3 and (revn % 2) == 0 and rev[-2] == 0:
                    rev = rev[:-2] + rev[-1:]
                rev = tuple(rev)

                if rev not in tags:
                    tags[rev] = []
                tags[rev].append(match.group(1))
                branchmap[match.group(1)] = match.group(2)

            elif re_31.match(line):
                state = 5
            elif re_32.match(line):
                state = 0

        elif state == 4:
            # expecting '------' separator before first revision
            if re_31.match(line):
                state = 5
            else:
                assert not re_32.match(line), _(
                    b'must have at least some revisions'
                )

        elif state == 5:
            # expecting revision number and possibly (ignored) lock indication
            # we create the logentry here from values stored in states 0 to 4,
            # as this state is re-entered for subsequent revisions of a file.
            match = re_50.match(line)
            assert match, _(b'expected revision number')
            e = logentry(
                rcs=scache(rcs),
                file=scache(filename),
                revision=tuple([int(x) for x in match.group(1).split(b'.')]),
                branches=[],
                parent=None,
                commitid=None,
                mergepoint=None,
                branchpoints=set(),
            )

            state = 6

        elif state == 6:
            # expecting date, author, state, lines changed
            match = re_60.match(line)
            assert match, _(b'revision must be followed by date line')
            d = match.group(1)
            if d[2] == b'/':
                # Y2K
                d = b'19' + d

            if len(d.split()) != 3:
                # cvs log dates always in GMT
                d = d + b' UTC'
            e.date = dateutil.parsedate(
                d,
                [
                    b'%y/%m/%d %H:%M:%S',
                    b'%Y/%m/%d %H:%M:%S',
                    b'%Y-%m-%d %H:%M:%S',
                ],
            )
            e.author = scache(match.group(2))
            e.dead = match.group(3).lower() == b'dead'

            if match.group(5):
                if match.group(6):
                    e.lines = (int(match.group(5)), int(match.group(6)))
                else:
                    e.lines = (int(match.group(5)), 0)
            elif match.group(6):
                e.lines = (0, int(match.group(6)))
            else:
                e.lines = None

            if match.group(7):  # cvs 1.12 commitid
                e.commitid = match.group(8)

            if match.group(9):  # cvsnt mergepoint
                myrev = match.group(10).split(b'.')
                if len(myrev) == 2:  # head
                    e.mergepoint = b'HEAD'
                else:
                    myrev = b'.'.join(myrev[:-2] + [b'0', myrev[-2]])
                    branches = [b for b in branchmap if branchmap[b] == myrev]
                    assert len(branches) == 1, (
                        b'unknown branch: %s' % e.mergepoint
                    )
                    e.mergepoint = branches[0]

            e.comment = []
            state = 7

        elif state == 7:
            # read the revision numbers of branches that start at this revision
            # or store the commit log message otherwise
            m = re_70.match(line)
            if m:
                e.branches = [
                    tuple([int(y) for y in x.strip().split(b'.')])
                    for x in m.group(1).split(b';')
                ]
                state = 8
            elif re_31.match(line) and re_50.match(peek):
                state = 5
                store = True
            elif re_32.match(line):
                state = 0
                store = True
            else:
                e.comment.append(line)

        elif state == 8:
            # store commit log message
            if re_31.match(line):
                cpeek = peek
                if cpeek.endswith(b'\n'):
                    cpeek = cpeek[:-1]
                if re_50.match(cpeek):
                    state = 5
                    store = True
                else:
                    e.comment.append(line)
            elif re_32.match(line):
                state = 0
                store = True
            else:
                e.comment.append(line)

        # When a file is added on a branch B1, CVS creates a synthetic
        # dead trunk revision 1.1 so that the branch has a root.
        # Likewise, if you merge such a file to a later branch B2 (one
        # that already existed when the file was added on B1), CVS
        # creates a synthetic dead revision 1.1.x.1 on B2.  Don't drop
        # these revisions now, but mark them synthetic so
        # createchangeset() can take care of them.
        if (
            store
            and e.dead
            and e.revision[-1] == 1
            and len(e.comment) == 1  # 1.1 or 1.1.x.1
            and file_added_re.match(e.comment[0])
        ):
            ui.debug(
                b'found synthetic revision in %s: %r\n' % (e.rcs, e.comment[0])
            )
            e.synthetic = True

        if store:
            # clean up the results and save in the log.
            store = False
            e.tags = sorted([scache(x) for x in tags.get(e.revision, [])])
            e.comment = scache(b'\n'.join(e.comment))

            revn = len(e.revision)
            if revn > 3 and (revn % 2) == 0:
                e.branch = tags.get(e.revision[:-1], [None])[0]
            else:
                e.branch = None

            # find the branches starting from this revision
            branchpoints = set()
            for branch, revision in pycompat.iteritems(branchmap):
                revparts = tuple([int(i) for i in revision.split(b'.')])
                if len(revparts) < 2:  # bad tags
                    continue
                if revparts[-2] == 0 and revparts[-1] % 2 == 0:
                    # normal branch
                    if revparts[:-2] == e.revision:
                        branchpoints.add(branch)
                elif revparts == (1, 1, 1):  # vendor branch
                    if revparts in e.branches:
                        branchpoints.add(branch)
            e.branchpoints = branchpoints

            log.append(e)

            rcsmap[e.rcs.replace(b'/Attic/', b'/')] = e.rcs

            if len(log) % 100 == 0:
                ui.status(
                    stringutil.ellipsis(b'%d %s' % (len(log), e.file), 80)
                    + b'\n'
                )

    log.sort(key=lambda x: (x.rcs, x.revision))

    # find parent revisions of individual files
    versions = {}
    for e in sorted(oldlog, key=lambda x: (x.rcs, x.revision)):
        rcs = e.rcs.replace(b'/Attic/', b'/')
        if rcs in rcsmap:
            e.rcs = rcsmap[rcs]
        branch = e.revision[:-1]
        versions[(e.rcs, branch)] = e.revision

    for e in log:
        branch = e.revision[:-1]
        p = versions.get((e.rcs, branch), None)
        if p is None:
            p = e.revision[:-2]
        e.parent = p
        versions[(e.rcs, branch)] = e.revision

    # update the log cache
    if cache:
        if log:
            # join up the old and new logs
            log.sort(key=lambda x: x.date)

            if oldlog and oldlog[-1].date >= log[0].date:
                raise logerror(
                    _(
                        b'log cache overlaps with new log entries,'
                        b' re-run without cache.'
                    )
                )

            log = oldlog + log

            # write the new cachefile
            ui.note(_(b'writing cvs log cache %s\n') % cachefile)
            pickle.dump(log, open(cachefile, b'wb'))
        else:
            log = oldlog

    ui.status(_(b'%d log entries\n') % len(log))

    encodings = ui.configlist(b'convert', b'cvsps.logencoding')
    if encodings:

        def revstr(r):
            # this is needed, because logentry.revision is a tuple of "int"
            # (e.g. (1, 2) for "1.2")
            return b'.'.join(pycompat.maplist(pycompat.bytestr, r))

        for entry in log:
            comment = entry.comment
            for e in encodings:
                try:
                    entry.comment = comment.decode(pycompat.sysstr(e)).encode(
                        'utf-8'
                    )
                    if ui.debugflag:
                        ui.debug(
                            b"transcoding by %s: %s of %s\n"
                            % (e, revstr(entry.revision), entry.file)
                        )
                    break
                except UnicodeDecodeError:
                    pass  # try next encoding
                except LookupError as inst:  # unknown encoding, maybe
                    raise error.Abort(
                        pycompat.bytestr(inst),
                        hint=_(
                            b'check convert.cvsps.logencoding configuration'
                        ),
                    )
            else:
                raise error.Abort(
                    _(
                        b"no encoding can transcode"
                        b" CVS log message for %s of %s"
                    )
                    % (revstr(entry.revision), entry.file),
                    hint=_(b'check convert.cvsps.logencoding configuration'),
                )

    hook.hook(ui, None, b"cvslog", True, log=log)

    return log


class changeset(object):
    """Class changeset has the following attributes:
    .id        - integer identifying this changeset (list index)
    .author    - author name as CVS knows it
    .branch    - name of branch this changeset is on, or None
    .comment   - commit message
    .commitid  - CVS commitid or None
    .date      - the commit date as a (time,tz) tuple
    .entries   - list of logentry objects in this changeset
    .parents   - list of one or two parent changesets
    .tags      - list of tags on this changeset
    .synthetic - from synthetic revision "file ... added on branch ..."
    .mergepoint- the branch that has been merged from or None
    .branchpoints- the branches that start at the current entry or empty
    """

    def __init__(self, **entries):
        self.id = None
        self.synthetic = False
        self.__dict__.update(entries)

    def __repr__(self):
        items = (
            b"%s=%r" % (k, self.__dict__[k]) for k in sorted(self.__dict__)
        )
        return b"%s(%s)" % (type(self).__name__, b", ".join(items))


def createchangeset(ui, log, fuzz=60, mergefrom=None, mergeto=None):
    '''Convert log into changesets.'''

    ui.status(_(b'creating changesets\n'))

    # try to order commitids by date
    mindate = {}
    for e in log:
        if e.commitid:
            if e.commitid not in mindate:
                mindate[e.commitid] = e.date
            else:
                mindate[e.commitid] = min(e.date, mindate[e.commitid])

    # Merge changesets
    log.sort(
        key=lambda x: (
            mindate.get(x.commitid, (-1, 0)),
            x.commitid or b'',
            x.comment,
            x.author,
            x.branch or b'',
            x.date,
            x.branchpoints,
        )
    )

    changesets = []
    files = set()
    c = None
    for i, e in enumerate(log):

        # Check if log entry belongs to the current changeset or not.

        # Since CVS is file-centric, two different file revisions with
        # different branchpoints should be treated as belonging to two
        # different changesets (and the ordering is important and not
        # honoured by cvsps at this point).
        #
        # Consider the following case:
        # foo 1.1 branchpoints: [MYBRANCH]
        # bar 1.1 branchpoints: [MYBRANCH, MYBRANCH2]
        #
        # Here foo is part only of MYBRANCH, but not MYBRANCH2, e.g. a
        # later version of foo may be in MYBRANCH2, so foo should be the
        # first changeset and bar the next and MYBRANCH and MYBRANCH2
        # should both start off of the bar changeset. No provisions are
        # made to ensure that this is, in fact, what happens.
        if not (
            c
            and e.branchpoints == c.branchpoints
            and (  # cvs commitids
                (e.commitid is not None and e.commitid == c.commitid)
                or (  # no commitids, use fuzzy commit detection
                    (e.commitid is None or c.commitid is None)
                    and e.comment == c.comment
                    and e.author == c.author
                    and e.branch == c.branch
                    and (
                        (c.date[0] + c.date[1])
                        <= (e.date[0] + e.date[1])
                        <= (c.date[0] + c.date[1]) + fuzz
                    )
                    and e.file not in files
                )
            )
        ):
            c = changeset(
                comment=e.comment,
                author=e.author,
                branch=e.branch,
                date=e.date,
                entries=[],
                mergepoint=e.mergepoint,
                branchpoints=e.branchpoints,
                commitid=e.commitid,
            )
            changesets.append(c)

            files = set()
            if len(changesets) % 100 == 0:
                t = b'%d %s' % (len(changesets), repr(e.comment)[1:-1])
                ui.status(stringutil.ellipsis(t, 80) + b'\n')

        c.entries.append(e)
        files.add(e.file)
        c.date = e.date  # changeset date is date of latest commit in it

    # Mark synthetic changesets

    for c in changesets:
        # Synthetic revisions always get their own changeset, because
        # the log message includes the filename.  E.g. if you add file3
        # and file4 on a branch, you get four log entries and three
        # changesets:
        #   "File file3 was added on branch ..." (synthetic, 1 entry)
        #   "File file4 was added on branch ..." (synthetic, 1 entry)
        #   "Add file3 and file4 to fix ..."     (real, 2 entries)
        # Hence the check for 1 entry here.
        c.synthetic = len(c.entries) == 1 and c.entries[0].synthetic

    # Sort files in each changeset

    def entitycompare(l, r):
        """Mimic cvsps sorting order"""
        l = l.file.split(b'/')
        r = r.file.split(b'/')
        nl = len(l)
        nr = len(r)
        n = min(nl, nr)
        for i in range(n):
            if i + 1 == nl and nl < nr:
                return -1
            elif i + 1 == nr and nl > nr:
                return +1
            elif l[i] < r[i]:
                return -1
            elif l[i] > r[i]:
                return +1
        return 0

    for c in changesets:
        c.entries.sort(key=functools.cmp_to_key(entitycompare))

    # Sort changesets by date

    odd = set()

    def cscmp(l, r):
        d = sum(l.date) - sum(r.date)
        if d:
            return d

        # detect vendor branches and initial commits on a branch
        le = {}
        for e in l.entries:
            le[e.rcs] = e.revision
        re = {}
        for e in r.entries:
            re[e.rcs] = e.revision

        d = 0
        for e in l.entries:
            if re.get(e.rcs, None) == e.parent:
                assert not d
                d = 1
                break

        for e in r.entries:
            if le.get(e.rcs, None) == e.parent:
                if d:
                    odd.add((l, r))
                d = -1
                break
        # By this point, the changesets are sufficiently compared that
        # we don't really care about ordering. However, this leaves
        # some race conditions in the tests, so we compare on the
        # number of files modified, the files contained in each
        # changeset, and the branchpoints in the change to ensure test
        # output remains stable.

        # recommended replacement for cmp from
        # https://docs.python.org/3.0/whatsnew/3.0.html
        c = lambda x, y: (x > y) - (x < y)
        # Sort bigger changes first.
        if not d:
            d = c(len(l.entries), len(r.entries))
        # Try sorting by filename in the change.
        if not d:
            d = c([e.file for e in l.entries], [e.file for e in r.entries])
        # Try and put changes without a branch point before ones with
        # a branch point.
        if not d:
            d = c(len(l.branchpoints), len(r.branchpoints))
        return d

    changesets.sort(key=functools.cmp_to_key(cscmp))

    # Collect tags

    globaltags = {}
    for c in changesets:
        for e in c.entries:
            for tag in e.tags:
                # remember which is the latest changeset to have this tag
                globaltags[tag] = c

    for c in changesets:
        tags = set()
        for e in c.entries:
            tags.update(e.tags)
        # remember tags only if this is the latest changeset to have it
        c.tags = sorted(tag for tag in tags if globaltags[tag] is c)

    # Find parent changesets, handle {{mergetobranch BRANCHNAME}}
    # by inserting dummy changesets with two parents, and handle
    # {{mergefrombranch BRANCHNAME}} by setting two parents.

    if mergeto is None:
        mergeto = br'{{mergetobranch ([-\w]+)}}'
    if mergeto:
        mergeto = re.compile(mergeto)

    if mergefrom is None:
        mergefrom = br'{{mergefrombranch ([-\w]+)}}'
    if mergefrom:
        mergefrom = re.compile(mergefrom)

    versions = {}  # changeset index where we saw any particular file version
    branches = {}  # changeset index where we saw a branch
    n = len(changesets)
    i = 0
    while i < n:
        c = changesets[i]

        for f in c.entries:
            versions[(f.rcs, f.revision)] = i

        p = None
        if c.branch in branches:
            p = branches[c.branch]
        else:
            # first changeset on a new branch
            # the parent is a changeset with the branch in its
            # branchpoints such that it is the latest possible
            # commit without any intervening, unrelated commits.

            for candidate in pycompat.xrange(i):
                if c.branch not in changesets[candidate].branchpoints:
                    if p is not None:
                        break
                    continue
                p = candidate

        c.parents = []
        if p is not None:
            p = changesets[p]

            # Ensure no changeset has a synthetic changeset as a parent.
            while p.synthetic:
                assert len(p.parents) <= 1, _(
                    b'synthetic changeset cannot have multiple parents'
                )
                if p.parents:
                    p = p.parents[0]
                else:
                    p = None
                    break

            if p is not None:
                c.parents.append(p)

        if c.mergepoint:
            if c.mergepoint == b'HEAD':
                c.mergepoint = None
            c.parents.append(changesets[branches[c.mergepoint]])

        if mergefrom:
            m = mergefrom.search(c.comment)
            if m:
                m = m.group(1)
                if m == b'HEAD':
                    m = None
                try:
                    candidate = changesets[branches[m]]
                except KeyError:
                    ui.warn(
                        _(
                            b"warning: CVS commit message references "
                            b"non-existent branch %r:\n%s\n"
                        )
                        % (pycompat.bytestr(m), c.comment)
                    )
                if m in branches and c.branch != m and not candidate.synthetic:
                    c.parents.append(candidate)

        if mergeto:
            m = mergeto.search(c.comment)
            if m:
                if m.groups():
                    m = m.group(1)
                    if m == b'HEAD':
                        m = None
                else:
                    m = None  # if no group found then merge to HEAD
                if m in branches and c.branch != m:
                    # insert empty changeset for merge
                    cc = changeset(
                        author=c.author,
                        branch=m,
                        date=c.date,
                        comment=b'convert-repo: CVS merge from branch %s'
                        % c.branch,
                        entries=[],
                        tags=[],
                        parents=[changesets[branches[m]], c],
                    )
                    changesets.insert(i + 1, cc)
                    branches[m] = i + 1

                    # adjust our loop counters now we have inserted a new entry
                    n += 1
                    i += 2
                    continue

        branches[c.branch] = i
        i += 1

    # Drop synthetic changesets (safe now that we have ensured no other
    # changesets can have them as parents).
    i = 0
    while i < len(changesets):
        if changesets[i].synthetic:
            del changesets[i]
        else:
            i += 1

    # Number changesets

    for i, c in enumerate(changesets):
        c.id = i + 1

    if odd:
        for l, r in odd:
            if l.id is not None and r.id is not None:
                ui.warn(
                    _(b'changeset %d is both before and after %d\n')
                    % (l.id, r.id)
                )

    ui.status(_(b'%d changeset entries\n') % len(changesets))

    hook.hook(ui, None, b"cvschangesets", True, changesets=changesets)

    return changesets


def debugcvsps(ui, *args, **opts):
    """Read CVS rlog for current directory or named path in
    repository, and convert the log to changesets based on matching
    commit log entries and dates.
    """
    opts = pycompat.byteskwargs(opts)
    if opts[b"new_cache"]:
        cache = b"write"
    elif opts[b"update_cache"]:
        cache = b"update"
    else:
        cache = None

    revisions = opts[b"revisions"]

    try:
        if args:
            log = []
            for d in args:
                log += createlog(ui, d, root=opts[b"root"], cache=cache)
        else:
            log = createlog(ui, root=opts[b"root"], cache=cache)
    except logerror as e:
        ui.write(b"%r\n" % e)
        return

    changesets = createchangeset(ui, log, opts[b"fuzz"])
    del log

    # Print changesets (optionally filtered)

    off = len(revisions)
    branches = {}  # latest version number in each branch
    ancestors = {}  # parent branch
    for cs in changesets:

        if opts[b"ancestors"]:
            if cs.branch not in branches and cs.parents and cs.parents[0].id:
                ancestors[cs.branch] = (
                    changesets[cs.parents[0].id - 1].branch,
                    cs.parents[0].id,
                )
            branches[cs.branch] = cs.id

        # limit by branches
        if (
            opts[b"branches"]
            and (cs.branch or b'HEAD') not in opts[b"branches"]
        ):
            continue

        if not off:
            # Note: trailing spaces on several lines here are needed to have
            #       bug-for-bug compatibility with cvsps.
            ui.write(b'---------------------\n')
            ui.write((b'PatchSet %d \n' % cs.id))
            ui.write(
                (
                    b'Date: %s\n'
                    % dateutil.datestr(cs.date, b'%Y/%m/%d %H:%M:%S %1%2')
                )
            )
            ui.write((b'Author: %s\n' % cs.author))
            ui.write((b'Branch: %s\n' % (cs.branch or b'HEAD')))
            ui.write(
                (
                    b'Tag%s: %s \n'
                    % (
                        [b'', b's'][len(cs.tags) > 1],
                        b','.join(cs.tags) or b'(none)',
                    )
                )
            )
            if cs.branchpoints:
                ui.writenoi18n(
                    b'Branchpoints: %s \n' % b', '.join(sorted(cs.branchpoints))
                )
            if opts[b"parents"] and cs.parents:
                if len(cs.parents) > 1:
                    ui.write(
                        (
                            b'Parents: %s\n'
                            % (b','.join([(b"%d" % p.id) for p in cs.parents]))
                        )
                    )
                else:
                    ui.write((b'Parent: %d\n' % cs.parents[0].id))

            if opts[b"ancestors"]:
                b = cs.branch
                r = []
                while b:
                    b, c = ancestors[b]
                    r.append(b'%s:%d:%d' % (b or b"HEAD", c, branches[b]))
                if r:
                    ui.write((b'Ancestors: %s\n' % (b','.join(r))))

            ui.writenoi18n(b'Log:\n')
            ui.write(b'%s\n\n' % cs.comment)
            ui.writenoi18n(b'Members: \n')
            for f in cs.entries:
                fn = f.file
                if fn.startswith(opts[b"prefix"]):
                    fn = fn[len(opts[b"prefix"]) :]
                ui.write(
                    b'\t%s:%s->%s%s \n'
                    % (
                        fn,
                        b'.'.join([b"%d" % x for x in f.parent]) or b'INITIAL',
                        b'.'.join([(b"%d" % x) for x in f.revision]),
                        [b'', b'(DEAD)'][f.dead],
                    )
                )
            ui.write(b'\n')

        # have we seen the start tag?
        if revisions and off:
            if revisions[0] == (b"%d" % cs.id) or revisions[0] in cs.tags:
                off = False

        # see if we reached the end tag
        if len(revisions) > 1 and not off:
            if revisions[1] == (b"%d" % cs.id) or revisions[1] in cs.tags:
                break
