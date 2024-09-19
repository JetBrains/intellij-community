# mq.py - patch queues for mercurial
#
# Copyright 2005, 2006 Chris Mason <mason@suse.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''manage a stack of patches

This extension lets you work with a stack of patches in a Mercurial
repository. It manages two stacks of patches - all known patches, and
applied patches (subset of known patches).

Known patches are represented as patch files in the .hg/patches
directory. Applied patches are both patch files and changesets.

Common tasks (use :hg:`help COMMAND` for more details)::

  create new patch                          qnew
  import existing patch                     qimport

  print patch series                        qseries
  print applied patches                     qapplied

  add known patch to applied stack          qpush
  remove patch from applied stack           qpop
  refresh contents of top applied patch     qrefresh

By default, mq will automatically use git patches when required to
avoid losing file mode changes, copy records, binary files or empty
files creations or deletions. This behavior can be configured with::

  [mq]
  git = auto/keep/yes/no

If set to 'keep', mq will obey the [diff] section configuration while
preserving existing git patches upon qrefresh. If set to 'yes' or
'no', mq will override the [diff] section and always generate git or
regular patches, possibly losing data in the second case.

It may be desirable for mq changesets to be kept in the secret phase (see
:hg:`help phases`), which can be enabled with the following setting::

  [mq]
  secret = True

You will by default be managing a patch queue named "patches". You can
create other, independent patch queues with the :hg:`qqueue` command.

If the working directory contains uncommitted files, qpush, qpop and
qgoto abort immediately. If -f/--force is used, the changes are
discarded. Setting::

  [mq]
  keepchanges = True

make them behave as if --keep-changes were passed, and non-conflicting
local changes will be tolerated and preserved. If incompatible options
such as -f/--force or --exact are passed, this setting is ignored.

This extension used to provide a strip command. This command now lives
in the strip extension.
'''

from __future__ import absolute_import, print_function

import errno
import os
import re
import shutil
import sys
from mercurial.i18n import _
from mercurial.node import (
    bin,
    hex,
    nullrev,
    short,
)
from mercurial.pycompat import (
    delattr,
    getattr,
    open,
)
from mercurial import (
    cmdutil,
    commands,
    dirstateguard,
    encoding,
    error,
    extensions,
    hg,
    localrepo,
    lock as lockmod,
    logcmdutil,
    patch as patchmod,
    phases,
    pycompat,
    registrar,
    revsetlang,
    scmutil,
    smartset,
    strip,
    subrepoutil,
    util,
    vfs as vfsmod,
)
from mercurial.utils import (
    dateutil,
    stringutil,
    urlutil,
)

release = lockmod.release
seriesopts = [(b's', b'summary', None, _(b'print first line of patch header'))]

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'mq',
    b'git',
    default=b'auto',
)
configitem(
    b'mq',
    b'keepchanges',
    default=False,
)
configitem(
    b'mq',
    b'plain',
    default=False,
)
configitem(
    b'mq',
    b'secret',
    default=False,
)

# force load strip extension formerly included in mq and import some utility
try:
    extensions.find(b'strip')
except KeyError:
    # note: load is lazy so we could avoid the try-except,
    # but I (marmoute) prefer this explicit code.
    class dummyui(object):
        def debug(self, msg):
            pass

        def log(self, event, msgfmt, *msgargs, **opts):
            pass

    extensions.load(dummyui(), b'strip', b'')

strip = strip.strip


def checksubstate(repo, baserev=None):
    """return list of subrepos at a different revision than substate.
    Abort if any subrepos have uncommitted changes."""
    inclsubs = []
    wctx = repo[None]
    if baserev:
        bctx = repo[baserev]
    else:
        bctx = wctx.p1()
    for s in sorted(wctx.substate):
        wctx.sub(s).bailifchanged(True)
        if s not in bctx.substate or bctx.sub(s).dirty():
            inclsubs.append(s)
    return inclsubs


# Patch names looks like unix-file names.
# They must be joinable with queue directory and result in the patch path.
normname = util.normpath


class statusentry(object):
    def __init__(self, node, name):
        self.node, self.name = node, name

    def __bytes__(self):
        return hex(self.node) + b':' + self.name

    __str__ = encoding.strmethod(__bytes__)
    __repr__ = encoding.strmethod(__bytes__)


# The order of the headers in 'hg export' HG patches:
HGHEADERS = [
    #   '# HG changeset patch',
    b'# User ',
    b'# Date ',
    b'#      ',
    b'# Branch ',
    b'# Node ID ',
    b'# Parent  ',  # can occur twice for merges - but that is not relevant for mq
]
# The order of headers in plain 'mail style' patches:
PLAINHEADERS = {
    b'from': 0,
    b'date': 1,
    b'subject': 2,
}


def inserthgheader(lines, header, value):
    """Assuming lines contains a HG patch header, add a header line with value.
    >>> try: inserthgheader([], b'# Date ', b'z')
    ... except ValueError as inst: print("oops")
    oops
    >>> inserthgheader([b'# HG changeset patch'], b'# Date ', b'z')
    ['# HG changeset patch', '# Date z']
    >>> inserthgheader([b'# HG changeset patch', b''], b'# Date ', b'z')
    ['# HG changeset patch', '# Date z', '']
    >>> inserthgheader([b'# HG changeset patch', b'# User y'], b'# Date ', b'z')
    ['# HG changeset patch', '# User y', '# Date z']
    >>> inserthgheader([b'# HG changeset patch', b'# Date x', b'# User y'],
    ...                b'# User ', b'z')
    ['# HG changeset patch', '# Date x', '# User z']
    >>> inserthgheader([b'# HG changeset patch', b'# Date y'], b'# Date ', b'z')
    ['# HG changeset patch', '# Date z']
    >>> inserthgheader([b'# HG changeset patch', b'', b'# Date y'],
    ...                b'# Date ', b'z')
    ['# HG changeset patch', '# Date z', '', '# Date y']
    >>> inserthgheader([b'# HG changeset patch', b'# Parent  y'],
    ...                b'# Date ', b'z')
    ['# HG changeset patch', '# Date z', '# Parent  y']
    """
    start = lines.index(b'# HG changeset patch') + 1
    newindex = HGHEADERS.index(header)
    bestpos = len(lines)
    for i in range(start, len(lines)):
        line = lines[i]
        if not line.startswith(b'# '):
            bestpos = min(bestpos, i)
            break
        for lineindex, h in enumerate(HGHEADERS):
            if line.startswith(h):
                if lineindex == newindex:
                    lines[i] = header + value
                    return lines
                if lineindex > newindex:
                    bestpos = min(bestpos, i)
                break  # next line
    lines.insert(bestpos, header + value)
    return lines


def insertplainheader(lines, header, value):
    """For lines containing a plain patch header, add a header line with value.
    >>> insertplainheader([], b'Date', b'z')
    ['Date: z']
    >>> insertplainheader([b''], b'Date', b'z')
    ['Date: z', '']
    >>> insertplainheader([b'x'], b'Date', b'z')
    ['Date: z', '', 'x']
    >>> insertplainheader([b'From: y', b'x'], b'Date', b'z')
    ['From: y', 'Date: z', '', 'x']
    >>> insertplainheader([b' date : x', b' from : y', b''], b'From', b'z')
    [' date : x', 'From: z', '']
    >>> insertplainheader([b'', b'Date: y'], b'Date', b'z')
    ['Date: z', '', 'Date: y']
    >>> insertplainheader([b'foo: bar', b'DATE: z', b'x'], b'From', b'y')
    ['From: y', 'foo: bar', 'DATE: z', '', 'x']
    """
    newprio = PLAINHEADERS[header.lower()]
    bestpos = len(lines)
    for i, line in enumerate(lines):
        if b':' in line:
            lheader = line.split(b':', 1)[0].strip().lower()
            lprio = PLAINHEADERS.get(lheader, newprio + 1)
            if lprio == newprio:
                lines[i] = b'%s: %s' % (header, value)
                return lines
            if lprio > newprio and i < bestpos:
                bestpos = i
        else:
            if line:
                lines.insert(i, b'')
            if i < bestpos:
                bestpos = i
            break
    lines.insert(bestpos, b'%s: %s' % (header, value))
    return lines


class patchheader(object):
    def __init__(self, pf, plainmode=False):
        def eatdiff(lines):
            while lines:
                l = lines[-1]
                if (
                    l.startswith(b"diff -")
                    or l.startswith(b"Index:")
                    or l.startswith(b"===========")
                ):
                    del lines[-1]
                else:
                    break

        def eatempty(lines):
            while lines:
                if not lines[-1].strip():
                    del lines[-1]
                else:
                    break

        message = []
        comments = []
        user = None
        date = None
        parent = None
        format = None
        subject = None
        branch = None
        nodeid = None
        diffstart = 0

        for line in open(pf, b'rb'):
            line = line.rstrip()
            if line.startswith(b'diff --git') or (
                diffstart and line.startswith(b'+++ ')
            ):
                diffstart = 2
                break
            diffstart = 0  # reset
            if line.startswith(b"--- "):
                diffstart = 1
                continue
            elif format == b"hgpatch":
                # parse values when importing the result of an hg export
                if line.startswith(b"# User "):
                    user = line[7:]
                elif line.startswith(b"# Date "):
                    date = line[7:]
                elif line.startswith(b"# Parent "):
                    parent = line[9:].lstrip()  # handle double trailing space
                elif line.startswith(b"# Branch "):
                    branch = line[9:]
                elif line.startswith(b"# Node ID "):
                    nodeid = line[10:]
                elif not line.startswith(b"# ") and line:
                    message.append(line)
                    format = None
            elif line == b'# HG changeset patch':
                message = []
                format = b"hgpatch"
            elif format != b"tagdone" and (
                line.startswith(b"Subject: ") or line.startswith(b"subject: ")
            ):
                subject = line[9:]
                format = b"tag"
            elif format != b"tagdone" and (
                line.startswith(b"From: ") or line.startswith(b"from: ")
            ):
                user = line[6:]
                format = b"tag"
            elif format != b"tagdone" and (
                line.startswith(b"Date: ") or line.startswith(b"date: ")
            ):
                date = line[6:]
                format = b"tag"
            elif format == b"tag" and line == b"":
                # when looking for tags (subject: from: etc) they
                # end once you find a blank line in the source
                format = b"tagdone"
            elif message or line:
                message.append(line)
            comments.append(line)

        eatdiff(message)
        eatdiff(comments)
        # Remember the exact starting line of the patch diffs before consuming
        # empty lines, for external use by TortoiseHg and others
        self.diffstartline = len(comments)
        eatempty(message)
        eatempty(comments)

        # make sure message isn't empty
        if format and format.startswith(b"tag") and subject:
            message.insert(0, subject)

        self.message = message
        self.comments = comments
        self.user = user
        self.date = date
        self.parent = parent
        # nodeid and branch are for external use by TortoiseHg and others
        self.nodeid = nodeid
        self.branch = branch
        self.haspatch = diffstart > 1
        self.plainmode = (
            plainmode
            or b'# HG changeset patch' not in self.comments
            and any(
                c.startswith(b'Date: ') or c.startswith(b'From: ')
                for c in self.comments
            )
        )

    def setuser(self, user):
        try:
            inserthgheader(self.comments, b'# User ', user)
        except ValueError:
            if self.plainmode:
                insertplainheader(self.comments, b'From', user)
            else:
                tmp = [b'# HG changeset patch', b'# User ' + user]
                self.comments = tmp + self.comments
        self.user = user

    def setdate(self, date):
        try:
            inserthgheader(self.comments, b'# Date ', date)
        except ValueError:
            if self.plainmode:
                insertplainheader(self.comments, b'Date', date)
            else:
                tmp = [b'# HG changeset patch', b'# Date ' + date]
                self.comments = tmp + self.comments
        self.date = date

    def setparent(self, parent):
        try:
            inserthgheader(self.comments, b'# Parent  ', parent)
        except ValueError:
            if not self.plainmode:
                tmp = [b'# HG changeset patch', b'# Parent  ' + parent]
                self.comments = tmp + self.comments
        self.parent = parent

    def setmessage(self, message):
        if self.comments:
            self._delmsg()
        self.message = [message]
        if message:
            if self.plainmode and self.comments and self.comments[-1]:
                self.comments.append(b'')
            self.comments.append(message)

    def __bytes__(self):
        s = b'\n'.join(self.comments).rstrip()
        if not s:
            return b''
        return s + b'\n\n'

    __str__ = encoding.strmethod(__bytes__)

    def _delmsg(self):
        """Remove existing message, keeping the rest of the comments fields.
        If comments contains 'subject: ', message will prepend
        the field and a blank line."""
        if self.message:
            subj = b'subject: ' + self.message[0].lower()
            for i in pycompat.xrange(len(self.comments)):
                if subj == self.comments[i].lower():
                    del self.comments[i]
                    self.message = self.message[2:]
                    break
        ci = 0
        for mi in self.message:
            while mi != self.comments[ci]:
                ci += 1
            del self.comments[ci]


def newcommit(repo, phase, *args, **kwargs):
    """helper dedicated to ensure a commit respect mq.secret setting

    It should be used instead of repo.commit inside the mq source for operation
    creating new changeset.
    """
    repo = repo.unfiltered()
    if phase is None:
        if repo.ui.configbool(b'mq', b'secret'):
            phase = phases.secret
    overrides = {(b'ui', b'allowemptycommit'): True}
    if phase is not None:
        overrides[(b'phases', b'new-commit')] = phase
    with repo.ui.configoverride(overrides, b'mq'):
        repo.ui.setconfig(b'ui', b'allowemptycommit', True)
        return repo.commit(*args, **kwargs)


class AbortNoCleanup(error.Abort):
    pass


class queue(object):
    def __init__(self, ui, baseui, path, patchdir=None):
        self.basepath = path
        try:
            with open(os.path.join(path, b'patches.queue'), 'rb') as fh:
                cur = fh.read().rstrip()

            if not cur:
                curpath = os.path.join(path, b'patches')
            else:
                curpath = os.path.join(path, b'patches-' + cur)
        except IOError:
            curpath = os.path.join(path, b'patches')
        self.path = patchdir or curpath
        self.opener = vfsmod.vfs(self.path)
        self.ui = ui
        self.baseui = baseui
        self.applieddirty = False
        self.seriesdirty = False
        self.added = []
        self.seriespath = b"series"
        self.statuspath = b"status"
        self.guardspath = b"guards"
        self.activeguards = None
        self.guardsdirty = False
        # Handle mq.git as a bool with extended values
        gitmode = ui.config(b'mq', b'git').lower()
        boolmode = stringutil.parsebool(gitmode)
        if boolmode is not None:
            if boolmode:
                gitmode = b'yes'
            else:
                gitmode = b'no'
        self.gitmode = gitmode
        # deprecated config: mq.plain
        self.plainmode = ui.configbool(b'mq', b'plain')
        self.checkapplied = True

    @util.propertycache
    def applied(self):
        def parselines(lines):
            for l in lines:
                entry = l.split(b':', 1)
                if len(entry) > 1:
                    n, name = entry
                    yield statusentry(bin(n), name)
                elif l.strip():
                    self.ui.warn(
                        _(b'malformated mq status line: %s\n')
                        % stringutil.pprint(entry)
                    )
                # else we ignore empty lines

        try:
            lines = self.opener.read(self.statuspath).splitlines()
            return list(parselines(lines))
        except IOError as e:
            if e.errno == errno.ENOENT:
                return []
            raise

    @util.propertycache
    def fullseries(self):
        try:
            return self.opener.read(self.seriespath).splitlines()
        except IOError as e:
            if e.errno == errno.ENOENT:
                return []
            raise

    @util.propertycache
    def series(self):
        self.parseseries()
        return self.series

    @util.propertycache
    def seriesguards(self):
        self.parseseries()
        return self.seriesguards

    def invalidate(self):
        for a in 'applied fullseries series seriesguards'.split():
            if a in self.__dict__:
                delattr(self, a)
        self.applieddirty = False
        self.seriesdirty = False
        self.guardsdirty = False
        self.activeguards = None

    def diffopts(self, opts=None, patchfn=None, plain=False):
        """Return diff options tweaked for this mq use, possibly upgrading to
        git format, and possibly plain and without lossy options."""
        diffopts = patchmod.difffeatureopts(
            self.ui,
            opts,
            git=True,
            whitespace=not plain,
            formatchanging=not plain,
        )
        if self.gitmode == b'auto':
            diffopts.upgrade = True
        elif self.gitmode == b'keep':
            pass
        elif self.gitmode in (b'yes', b'no'):
            diffopts.git = self.gitmode == b'yes'
        else:
            raise error.Abort(
                _(b'mq.git option can be auto/keep/yes/no got %s')
                % self.gitmode
            )
        if patchfn:
            diffopts = self.patchopts(diffopts, patchfn)
        return diffopts

    def patchopts(self, diffopts, *patches):
        """Return a copy of input diff options with git set to true if
        referenced patch is a git patch and should be preserved as such.
        """
        diffopts = diffopts.copy()
        if not diffopts.git and self.gitmode == b'keep':
            for patchfn in patches:
                patchf = self.opener(patchfn, b'r')
                # if the patch was a git patch, refresh it as a git patch
                diffopts.git = any(
                    line.startswith(b'diff --git') for line in patchf
                )
                patchf.close()
        return diffopts

    def join(self, *p):
        return os.path.join(self.path, *p)

    def findseries(self, patch):
        def matchpatch(l):
            l = l.split(b'#', 1)[0]
            return l.strip() == patch

        for index, l in enumerate(self.fullseries):
            if matchpatch(l):
                return index
        return None

    guard_re = re.compile(br'\s?#([-+][^-+# \t\r\n\f][^# \t\r\n\f]*)')

    def parseseries(self):
        self.series = []
        self.seriesguards = []
        for l in self.fullseries:
            h = l.find(b'#')
            if h == -1:
                patch = l
                comment = b''
            elif h == 0:
                continue
            else:
                patch = l[:h]
                comment = l[h:]
            patch = patch.strip()
            if patch:
                if patch in self.series:
                    raise error.Abort(
                        _(b'%s appears more than once in %s')
                        % (patch, self.join(self.seriespath))
                    )
                self.series.append(patch)
                self.seriesguards.append(self.guard_re.findall(comment))

    def checkguard(self, guard):
        if not guard:
            return _(b'guard cannot be an empty string')
        bad_chars = b'# \t\r\n\f'
        first = guard[0]
        if first in b'-+':
            return _(b'guard %r starts with invalid character: %r') % (
                guard,
                first,
            )
        for c in bad_chars:
            if c in guard:
                return _(b'invalid character in guard %r: %r') % (guard, c)

    def setactive(self, guards):
        for guard in guards:
            bad = self.checkguard(guard)
            if bad:
                raise error.Abort(bad)
        guards = sorted(set(guards))
        self.ui.debug(b'active guards: %s\n' % b' '.join(guards))
        self.activeguards = guards
        self.guardsdirty = True

    def active(self):
        if self.activeguards is None:
            self.activeguards = []
            try:
                guards = self.opener.read(self.guardspath).split()
            except IOError as err:
                if err.errno != errno.ENOENT:
                    raise
                guards = []
            for i, guard in enumerate(guards):
                bad = self.checkguard(guard)
                if bad:
                    self.ui.warn(
                        b'%s:%d: %s\n'
                        % (self.join(self.guardspath), i + 1, bad)
                    )
                else:
                    self.activeguards.append(guard)
        return self.activeguards

    def setguards(self, idx, guards):
        for g in guards:
            if len(g) < 2:
                raise error.Abort(_(b'guard %r too short') % g)
            if g[0] not in b'-+':
                raise error.Abort(_(b'guard %r starts with invalid char') % g)
            bad = self.checkguard(g[1:])
            if bad:
                raise error.Abort(bad)
        drop = self.guard_re.sub(b'', self.fullseries[idx])
        self.fullseries[idx] = drop + b''.join([b' #' + g for g in guards])
        self.parseseries()
        self.seriesdirty = True

    def pushable(self, idx):
        if isinstance(idx, bytes):
            idx = self.series.index(idx)
        patchguards = self.seriesguards[idx]
        if not patchguards:
            return True, None
        guards = self.active()
        exactneg = [
            g for g in patchguards if g.startswith(b'-') and g[1:] in guards
        ]
        if exactneg:
            return False, stringutil.pprint(exactneg[0])
        pos = [g for g in patchguards if g.startswith(b'+')]
        exactpos = [g for g in pos if g[1:] in guards]
        if pos:
            if exactpos:
                return True, stringutil.pprint(exactpos[0])
            return False, b' '.join([stringutil.pprint(p) for p in pos])
        return True, b''

    def explainpushable(self, idx, all_patches=False):
        if all_patches:
            write = self.ui.write
        else:
            write = self.ui.warn

        if all_patches or self.ui.verbose:
            if isinstance(idx, bytes):
                idx = self.series.index(idx)
            pushable, why = self.pushable(idx)
            if all_patches and pushable:
                if why is None:
                    write(
                        _(b'allowing %s - no guards in effect\n')
                        % self.series[idx]
                    )
                else:
                    if not why:
                        write(
                            _(b'allowing %s - no matching negative guards\n')
                            % self.series[idx]
                        )
                    else:
                        write(
                            _(b'allowing %s - guarded by %s\n')
                            % (self.series[idx], why)
                        )
            if not pushable:
                if why:
                    write(
                        _(b'skipping %s - guarded by %s\n')
                        % (self.series[idx], why)
                    )
                else:
                    write(
                        _(b'skipping %s - no matching guards\n')
                        % self.series[idx]
                    )

    def savedirty(self):
        def writelist(items, path):
            fp = self.opener(path, b'wb')
            for i in items:
                fp.write(b"%s\n" % i)
            fp.close()

        if self.applieddirty:
            writelist(map(bytes, self.applied), self.statuspath)
            self.applieddirty = False
        if self.seriesdirty:
            writelist(self.fullseries, self.seriespath)
            self.seriesdirty = False
        if self.guardsdirty:
            writelist(self.activeguards, self.guardspath)
            self.guardsdirty = False
        if self.added:
            qrepo = self.qrepo()
            if qrepo:
                qrepo[None].add(f for f in self.added if f not in qrepo[None])
            self.added = []

    def removeundo(self, repo):
        undo = repo.sjoin(b'undo')
        if not os.path.exists(undo):
            return
        try:
            os.unlink(undo)
        except OSError as inst:
            self.ui.warn(
                _(b'error removing undo: %s\n') % stringutil.forcebytestr(inst)
            )

    def backup(self, repo, files, copy=False):
        # backup local changes in --force case
        for f in sorted(files):
            absf = repo.wjoin(f)
            if os.path.lexists(absf):
                absorig = scmutil.backuppath(self.ui, repo, f)
                self.ui.note(
                    _(b'saving current version of %s as %s\n')
                    % (f, os.path.relpath(absorig))
                )

                if copy:
                    util.copyfile(absf, absorig)
                else:
                    util.rename(absf, absorig)

    def printdiff(
        self,
        repo,
        diffopts,
        node1,
        node2=None,
        files=None,
        fp=None,
        changes=None,
        opts=None,
    ):
        if opts is None:
            opts = {}
        stat = opts.get(b'stat')
        m = scmutil.match(repo[node1], files, opts)
        logcmdutil.diffordiffstat(
            self.ui,
            repo,
            diffopts,
            repo[node1],
            repo[node2],
            m,
            changes,
            stat,
            fp,
        )

    def mergeone(self, repo, mergeq, head, patch, rev, diffopts):
        # first try just applying the patch
        (err, n) = self.apply(
            repo, [patch], update_status=False, strict=True, merge=rev
        )

        if err == 0:
            return (err, n)

        if n is None:
            raise error.Abort(_(b"apply failed for patch %s") % patch)

        self.ui.warn(_(b"patch didn't work out, merging %s\n") % patch)

        # apply failed, strip away that rev and merge.
        hg.clean(repo, head)
        strip(self.ui, repo, [n], update=False, backup=False)

        ctx = repo[rev]
        ret = hg.merge(ctx, remind=False)
        if ret:
            raise error.Abort(_(b"update returned %d") % ret)
        n = newcommit(repo, None, ctx.description(), ctx.user(), force=True)
        if n is None:
            raise error.Abort(_(b"repo commit failed"))
        try:
            ph = patchheader(mergeq.join(patch), self.plainmode)
        except Exception:
            raise error.Abort(_(b"unable to read %s") % patch)

        diffopts = self.patchopts(diffopts, patch)
        patchf = self.opener(patch, b"w")
        comments = bytes(ph)
        if comments:
            patchf.write(comments)
        self.printdiff(repo, diffopts, head, n, fp=patchf)
        patchf.close()
        self.removeundo(repo)
        return (0, n)

    def qparents(self, repo, rev=None):
        """return the mq handled parent or p1

        In some case where mq get himself in being the parent of a merge the
        appropriate parent may be p2.
        (eg: an in progress merge started with mq disabled)

        If no parent are managed by mq, p1 is returned.
        """
        if rev is None:
            (p1, p2) = repo.dirstate.parents()
            if p2 == repo.nullid:
                return p1
            if not self.applied:
                return None
            return self.applied[-1].node
        p1, p2 = repo.changelog.parents(rev)
        if p2 != repo.nullid and p2 in [x.node for x in self.applied]:
            return p2
        return p1

    def mergepatch(self, repo, mergeq, series, diffopts):
        if not self.applied:
            # each of the patches merged in will have two parents.  This
            # can confuse the qrefresh, qdiff, and strip code because it
            # needs to know which parent is actually in the patch queue.
            # so, we insert a merge marker with only one parent.  This way
            # the first patch in the queue is never a merge patch
            #
            pname = b".hg.patches.merge.marker"
            n = newcommit(repo, None, b'[mq]: merge marker', force=True)
            self.removeundo(repo)
            self.applied.append(statusentry(n, pname))
            self.applieddirty = True

        head = self.qparents(repo)

        for patch in series:
            patch = mergeq.lookup(patch, strict=True)
            if not patch:
                self.ui.warn(_(b"patch %s does not exist\n") % patch)
                return (1, None)
            pushable, reason = self.pushable(patch)
            if not pushable:
                self.explainpushable(patch, all_patches=True)
                continue
            info = mergeq.isapplied(patch)
            if not info:
                self.ui.warn(_(b"patch %s is not applied\n") % patch)
                return (1, None)
            rev = info[1]
            err, head = self.mergeone(repo, mergeq, head, patch, rev, diffopts)
            if head:
                self.applied.append(statusentry(head, patch))
                self.applieddirty = True
            if err:
                return (err, head)
        self.savedirty()
        return (0, head)

    def patch(self, repo, patchfile):
        """Apply patchfile  to the working directory.
        patchfile: name of patch file"""
        files = set()
        try:
            fuzz = patchmod.patch(
                self.ui, repo, patchfile, strip=1, files=files, eolmode=None
            )
            return (True, list(files), fuzz)
        except Exception as inst:
            self.ui.note(stringutil.forcebytestr(inst) + b'\n')
            if not self.ui.verbose:
                self.ui.warn(_(b"patch failed, unable to continue (try -v)\n"))
            self.ui.traceback()
            return (False, list(files), False)

    def apply(
        self,
        repo,
        series,
        list=False,
        update_status=True,
        strict=False,
        patchdir=None,
        merge=None,
        all_files=None,
        tobackup=None,
        keepchanges=False,
    ):
        wlock = lock = tr = None
        try:
            wlock = repo.wlock()
            lock = repo.lock()
            tr = repo.transaction(b"qpush")
            try:
                ret = self._apply(
                    repo,
                    series,
                    list,
                    update_status,
                    strict,
                    patchdir,
                    merge,
                    all_files=all_files,
                    tobackup=tobackup,
                    keepchanges=keepchanges,
                )
                tr.close()
                self.savedirty()
                return ret
            except AbortNoCleanup:
                tr.close()
                self.savedirty()
                raise
            except:  # re-raises
                try:
                    tr.abort()
                finally:
                    self.invalidate()
                raise
        finally:
            release(tr, lock, wlock)
            self.removeundo(repo)

    def _apply(
        self,
        repo,
        series,
        list=False,
        update_status=True,
        strict=False,
        patchdir=None,
        merge=None,
        all_files=None,
        tobackup=None,
        keepchanges=False,
    ):
        """returns (error, hash)

        error = 1 for unable to read, 2 for patch failed, 3 for patch
        fuzz. tobackup is None or a set of files to backup before they
        are modified by a patch.
        """
        # TODO unify with commands.py
        if not patchdir:
            patchdir = self.path
        err = 0
        n = None
        for patchname in series:
            pushable, reason = self.pushable(patchname)
            if not pushable:
                self.explainpushable(patchname, all_patches=True)
                continue
            self.ui.status(_(b"applying %s\n") % patchname)
            pf = os.path.join(patchdir, patchname)

            try:
                ph = patchheader(self.join(patchname), self.plainmode)
            except IOError:
                self.ui.warn(_(b"unable to read %s\n") % patchname)
                err = 1
                break

            message = ph.message
            if not message:
                # The commit message should not be translated
                message = b"imported patch %s\n" % patchname
            else:
                if list:
                    # The commit message should not be translated
                    message.append(b"\nimported patch %s" % patchname)
                message = b'\n'.join(message)

            if ph.haspatch:
                if tobackup:
                    touched = patchmod.changedfiles(self.ui, repo, pf)
                    touched = set(touched) & tobackup
                    if touched and keepchanges:
                        raise AbortNoCleanup(
                            _(b"conflicting local changes found"),
                            hint=_(b"did you forget to qrefresh?"),
                        )
                    self.backup(repo, touched, copy=True)
                    tobackup = tobackup - touched
                (patcherr, files, fuzz) = self.patch(repo, pf)
                if all_files is not None:
                    all_files.update(files)
                patcherr = not patcherr
            else:
                self.ui.warn(_(b"patch %s is empty\n") % patchname)
                patcherr, files, fuzz = 0, [], 0

            if merge and files:
                # Mark as removed/merged and update dirstate parent info
                with repo.dirstate.parentchange():
                    for f in files:
                        repo.dirstate.update_file_p1(f, p1_tracked=True)
                    p1 = repo.dirstate.p1()
                    repo.setparents(p1, merge)

            if all_files and b'.hgsubstate' in all_files:
                wctx = repo[None]
                pctx = repo[b'.']
                overwrite = False
                mergedsubstate = subrepoutil.submerge(
                    repo, pctx, wctx, wctx, overwrite
                )
                files += mergedsubstate.keys()

            match = scmutil.matchfiles(repo, files or [])
            oldtip = repo.changelog.tip()
            n = newcommit(
                repo, None, message, ph.user, ph.date, match=match, force=True
            )
            if repo.changelog.tip() == oldtip:
                raise error.Abort(
                    _(b"qpush exactly duplicates child changeset")
                )
            if n is None:
                raise error.Abort(_(b"repository commit failed"))

            if update_status:
                self.applied.append(statusentry(n, patchname))

            if patcherr:
                self.ui.warn(
                    _(b"patch failed, rejects left in working directory\n")
                )
                err = 2
                break

            if fuzz and strict:
                self.ui.warn(_(b"fuzz found when applying patch, stopping\n"))
                err = 3
                break
        return (err, n)

    def _cleanup(self, patches, numrevs, keep=False):
        if not keep:
            r = self.qrepo()
            if r:
                r[None].forget(patches)
            for p in patches:
                try:
                    os.unlink(self.join(p))
                except OSError as inst:
                    if inst.errno != errno.ENOENT:
                        raise

        qfinished = []
        if numrevs:
            qfinished = self.applied[:numrevs]
            del self.applied[:numrevs]
            self.applieddirty = True

        unknown = []

        sortedseries = []
        for p in patches:
            idx = self.findseries(p)
            if idx is None:
                sortedseries.append((-1, p))
            else:
                sortedseries.append((idx, p))

        sortedseries.sort(reverse=True)
        for (i, p) in sortedseries:
            if i != -1:
                del self.fullseries[i]
            else:
                unknown.append(p)

        if unknown:
            if numrevs:
                rev = {entry.name: entry.node for entry in qfinished}
                for p in unknown:
                    msg = _(b'revision %s refers to unknown patches: %s\n')
                    self.ui.warn(msg % (short(rev[p]), p))
            else:
                msg = _(b'unknown patches: %s\n')
                raise error.Abort(b''.join(msg % p for p in unknown))

        self.parseseries()
        self.seriesdirty = True
        return [entry.node for entry in qfinished]

    def _revpatches(self, repo, revs):
        firstrev = repo[self.applied[0].node].rev()
        patches = []
        for i, rev in enumerate(revs):

            if rev < firstrev:
                raise error.Abort(_(b'revision %d is not managed') % rev)

            ctx = repo[rev]
            base = self.applied[i].node
            if ctx.node() != base:
                msg = _(b'cannot delete revision %d above applied patches')
                raise error.Abort(msg % rev)

            patch = self.applied[i].name
            for fmt in (b'[mq]: %s', b'imported patch %s'):
                if ctx.description() == fmt % patch:
                    msg = _(b'patch %s finalized without changeset message\n')
                    repo.ui.status(msg % patch)
                    break

            patches.append(patch)
        return patches

    def finish(self, repo, revs):
        # Manually trigger phase computation to ensure phasedefaults is
        # executed before we remove the patches.
        repo._phasecache
        patches = self._revpatches(repo, sorted(revs))
        qfinished = self._cleanup(patches, len(patches))
        if qfinished and repo.ui.configbool(b'mq', b'secret'):
            # only use this logic when the secret option is added
            oldqbase = repo[qfinished[0]]
            tphase = phases.newcommitphase(repo.ui)
            if oldqbase.phase() > tphase and oldqbase.p1().phase() <= tphase:
                with repo.transaction(b'qfinish') as tr:
                    phases.advanceboundary(repo, tr, tphase, qfinished)

    def delete(self, repo, patches, opts):
        if not patches and not opts.get(b'rev'):
            raise error.Abort(
                _(b'qdelete requires at least one revision or patch name')
            )

        realpatches = []
        for patch in patches:
            patch = self.lookup(patch, strict=True)
            info = self.isapplied(patch)
            if info:
                raise error.Abort(_(b"cannot delete applied patch %s") % patch)
            if patch not in self.series:
                raise error.Abort(_(b"patch %s not in series file") % patch)
            if patch not in realpatches:
                realpatches.append(patch)

        numrevs = 0
        if opts.get(b'rev'):
            if not self.applied:
                raise error.Abort(_(b'no patches applied'))
            revs = scmutil.revrange(repo, opts.get(b'rev'))
            revs.sort()
            revpatches = self._revpatches(repo, revs)
            realpatches += revpatches
            numrevs = len(revpatches)

        self._cleanup(realpatches, numrevs, opts.get(b'keep'))

    def checktoppatch(self, repo):
        '''check that working directory is at qtip'''
        if self.applied:
            top = self.applied[-1].node
            patch = self.applied[-1].name
            if repo.dirstate.p1() != top:
                raise error.Abort(_(b"working directory revision is not qtip"))
            return top, patch
        return None, None

    def putsubstate2changes(self, substatestate, changes):
        if isinstance(changes, list):
            mar = changes[:3]
        else:
            mar = (changes.modified, changes.added, changes.removed)
        if any((b'.hgsubstate' in files for files in mar)):
            return  # already listed up
        # not yet listed up
        if substatestate in b'a?':
            mar[1].append(b'.hgsubstate')
        elif substatestate in b'r':
            mar[2].append(b'.hgsubstate')
        else:  # modified
            mar[0].append(b'.hgsubstate')

    def checklocalchanges(self, repo, force=False, refresh=True):
        excsuffix = b''
        if refresh:
            excsuffix = b', qrefresh first'
            # plain versions for i18n tool to detect them
            _(b"local changes found, qrefresh first")
            _(b"local changed subrepos found, qrefresh first")

        s = repo.status()
        if not force:
            cmdutil.checkunfinished(repo)
            if s.modified or s.added or s.removed or s.deleted:
                _(b"local changes found")  # i18n tool detection
                raise error.Abort(_(b"local changes found" + excsuffix))
            if checksubstate(repo):
                _(b"local changed subrepos found")  # i18n tool detection
                raise error.Abort(
                    _(b"local changed subrepos found" + excsuffix)
                )
        else:
            cmdutil.checkunfinished(repo, skipmerge=True)
        return s

    _reserved = (b'series', b'status', b'guards', b'.', b'..')

    def checkreservedname(self, name):
        if name in self._reserved:
            raise error.Abort(
                _(b'"%s" cannot be used as the name of a patch') % name
            )
        if name != name.strip():
            # whitespace is stripped by parseseries()
            raise error.Abort(
                _(b'patch name cannot begin or end with whitespace')
            )
        for prefix in (b'.hg', b'.mq'):
            if name.startswith(prefix):
                raise error.Abort(
                    _(b'patch name cannot begin with "%s"') % prefix
                )
        for c in (b'#', b':', b'\r', b'\n'):
            if c in name:
                raise error.Abort(
                    _(b'%r cannot be used in the name of a patch')
                    % pycompat.bytestr(c)
                )

    def checkpatchname(self, name, force=False):
        self.checkreservedname(name)
        if not force and os.path.exists(self.join(name)):
            if os.path.isdir(self.join(name)):
                raise error.Abort(
                    _(b'"%s" already exists as a directory') % name
                )
            else:
                raise error.Abort(_(b'patch "%s" already exists') % name)

    def makepatchname(self, title, fallbackname):
        """Return a suitable filename for title, adding a suffix to make
        it unique in the existing list"""
        namebase = re.sub(br'[\s\W_]+', b'_', title.lower()).strip(b'_')
        namebase = namebase[:75]  # avoid too long name (issue5117)
        if namebase:
            try:
                self.checkreservedname(namebase)
            except error.Abort:
                namebase = fallbackname
        else:
            namebase = fallbackname
        name = namebase
        i = 0
        while True:
            if name not in self.fullseries:
                try:
                    self.checkpatchname(name)
                    break
                except error.Abort:
                    pass
            i += 1
            name = b'%s__%d' % (namebase, i)
        return name

    def checkkeepchanges(self, keepchanges, force):
        if force and keepchanges:
            raise error.Abort(_(b'cannot use both --force and --keep-changes'))

    def new(self, repo, patchfn, *pats, **opts):
        """options:
        msg: a string or a no-argument function returning a string
        """
        opts = pycompat.byteskwargs(opts)
        msg = opts.get(b'msg')
        edit = opts.get(b'edit')
        editform = opts.get(b'editform', b'mq.qnew')
        user = opts.get(b'user')
        date = opts.get(b'date')
        if date:
            date = dateutil.parsedate(date)
        diffopts = self.diffopts({b'git': opts.get(b'git')}, plain=True)
        if opts.get(b'checkname', True):
            self.checkpatchname(patchfn)
        inclsubs = checksubstate(repo)
        if inclsubs:
            substatestate = repo.dirstate[b'.hgsubstate']
        if opts.get(b'include') or opts.get(b'exclude') or pats:
            # detect missing files in pats
            def badfn(f, msg):
                if f != b'.hgsubstate':  # .hgsubstate is auto-created
                    raise error.Abort(b'%s: %s' % (f, msg))

            match = scmutil.match(repo[None], pats, opts, badfn=badfn)
            changes = repo.status(match=match)
        else:
            changes = self.checklocalchanges(repo, force=True)
        commitfiles = list(inclsubs)
        commitfiles.extend(changes.modified)
        commitfiles.extend(changes.added)
        commitfiles.extend(changes.removed)
        match = scmutil.matchfiles(repo, commitfiles)
        if len(repo[None].parents()) > 1:
            raise error.Abort(_(b'cannot manage merge changesets'))
        self.checktoppatch(repo)
        insert = self.fullseriesend()
        with repo.wlock():
            try:
                # if patch file write fails, abort early
                p = self.opener(patchfn, b"w")
            except IOError as e:
                raise error.Abort(
                    _(b'cannot write patch "%s": %s')
                    % (patchfn, encoding.strtolocal(e.strerror))
                )
            try:
                defaultmsg = b"[mq]: %s" % patchfn
                editor = cmdutil.getcommiteditor(editform=editform)
                if edit:

                    def finishdesc(desc):
                        if desc.rstrip():
                            return desc
                        else:
                            return defaultmsg

                    # i18n: this message is shown in editor with "HG: " prefix
                    extramsg = _(b'Leave message empty to use default message.')
                    editor = cmdutil.getcommiteditor(
                        finishdesc=finishdesc,
                        extramsg=extramsg,
                        editform=editform,
                    )
                    commitmsg = msg
                else:
                    commitmsg = msg or defaultmsg

                n = newcommit(
                    repo,
                    None,
                    commitmsg,
                    user,
                    date,
                    match=match,
                    force=True,
                    editor=editor,
                )
                if n is None:
                    raise error.Abort(_(b"repo commit failed"))
                try:
                    self.fullseries[insert:insert] = [patchfn]
                    self.applied.append(statusentry(n, patchfn))
                    self.parseseries()
                    self.seriesdirty = True
                    self.applieddirty = True
                    nctx = repo[n]
                    ph = patchheader(self.join(patchfn), self.plainmode)
                    if user:
                        ph.setuser(user)
                    if date:
                        ph.setdate(b'%d %d' % date)
                    ph.setparent(hex(nctx.p1().node()))
                    msg = nctx.description().strip()
                    if msg == defaultmsg.strip():
                        msg = b''
                    ph.setmessage(msg)
                    p.write(bytes(ph))
                    if commitfiles:
                        parent = self.qparents(repo, n)
                        if inclsubs:
                            self.putsubstate2changes(substatestate, changes)
                        chunks = patchmod.diff(
                            repo,
                            node1=parent,
                            node2=n,
                            changes=changes,
                            opts=diffopts,
                        )
                        for chunk in chunks:
                            p.write(chunk)
                    p.close()
                    r = self.qrepo()
                    if r:
                        r[None].add([patchfn])
                except:  # re-raises
                    repo.rollback()
                    raise
            except Exception:
                patchpath = self.join(patchfn)
                try:
                    os.unlink(patchpath)
                except OSError:
                    self.ui.warn(_(b'error unlinking %s\n') % patchpath)
                raise
            self.removeundo(repo)

    def isapplied(self, patch):
        """returns (index, rev, patch)"""
        for i, a in enumerate(self.applied):
            if a.name == patch:
                return (i, a.node, a.name)
        return None

    # if the exact patch name does not exist, we try a few
    # variations.  If strict is passed, we try only #1
    #
    # 1) a number (as string) to indicate an offset in the series file
    # 2) a unique substring of the patch name was given
    # 3) patchname[-+]num to indicate an offset in the series file
    def lookup(self, patch, strict=False):
        def partialname(s):
            if s in self.series:
                return s
            matches = [x for x in self.series if s in x]
            if len(matches) > 1:
                self.ui.warn(_(b'patch name "%s" is ambiguous:\n') % s)
                for m in matches:
                    self.ui.warn(b'  %s\n' % m)
                return None
            if matches:
                return matches[0]
            if self.series and self.applied:
                if s == b'qtip':
                    return self.series[self.seriesend(True) - 1]
                if s == b'qbase':
                    return self.series[0]
            return None

        if patch in self.series:
            return patch

        if not os.path.isfile(self.join(patch)):
            try:
                sno = int(patch)
            except (ValueError, OverflowError):
                pass
            else:
                if -len(self.series) <= sno < len(self.series):
                    return self.series[sno]

            if not strict:
                res = partialname(patch)
                if res:
                    return res
                minus = patch.rfind(b'-')
                if minus >= 0:
                    res = partialname(patch[:minus])
                    if res:
                        i = self.series.index(res)
                        try:
                            off = int(patch[minus + 1 :] or 1)
                        except (ValueError, OverflowError):
                            pass
                        else:
                            if i - off >= 0:
                                return self.series[i - off]
                plus = patch.rfind(b'+')
                if plus >= 0:
                    res = partialname(patch[:plus])
                    if res:
                        i = self.series.index(res)
                        try:
                            off = int(patch[plus + 1 :] or 1)
                        except (ValueError, OverflowError):
                            pass
                        else:
                            if i + off < len(self.series):
                                return self.series[i + off]
        raise error.Abort(_(b"patch %s not in series") % patch)

    def push(
        self,
        repo,
        patch=None,
        force=False,
        list=False,
        mergeq=None,
        all=False,
        move=False,
        exact=False,
        nobackup=False,
        keepchanges=False,
    ):
        self.checkkeepchanges(keepchanges, force)
        diffopts = self.diffopts()
        with repo.wlock():
            heads = []
            for hs in repo.branchmap().iterheads():
                heads.extend(hs)
            if not heads:
                heads = [repo.nullid]
            if repo.dirstate.p1() not in heads and not exact:
                self.ui.status(_(b"(working directory not at a head)\n"))

            if not self.series:
                self.ui.warn(_(b'no patches in series\n'))
                return 0

            # Suppose our series file is: A B C and the current 'top'
            # patch is B. qpush C should be performed (moving forward)
            # qpush B is a NOP (no change) qpush A is an error (can't
            # go backwards with qpush)
            if patch:
                patch = self.lookup(patch)
                info = self.isapplied(patch)
                if info and info[0] >= len(self.applied) - 1:
                    self.ui.warn(
                        _(b'qpush: %s is already at the top\n') % patch
                    )
                    return 0

                pushable, reason = self.pushable(patch)
                if pushable:
                    if self.series.index(patch) < self.seriesend():
                        raise error.Abort(
                            _(b"cannot push to a previous patch: %s") % patch
                        )
                else:
                    if reason:
                        reason = _(b'guarded by %s') % reason
                    else:
                        reason = _(b'no matching guards')
                    self.ui.warn(
                        _(b"cannot push '%s' - %s\n") % (patch, reason)
                    )
                    return 1
            elif all:
                patch = self.series[-1]
                if self.isapplied(patch):
                    self.ui.warn(_(b'all patches are currently applied\n'))
                    return 0

            # Following the above example, starting at 'top' of B:
            # qpush should be performed (pushes C), but a subsequent
            # qpush without an argument is an error (nothing to
            # apply). This allows a loop of "...while hg qpush..." to
            # work as it detects an error when done
            start = self.seriesend()
            if start == len(self.series):
                self.ui.warn(_(b'patch series already fully applied\n'))
                return 1
            if not force and not keepchanges:
                self.checklocalchanges(repo, refresh=self.applied)

            if exact:
                if keepchanges:
                    raise error.Abort(
                        _(b"cannot use --exact and --keep-changes together")
                    )
                if move:
                    raise error.Abort(
                        _(b'cannot use --exact and --move together')
                    )
                if self.applied:
                    raise error.Abort(
                        _(b'cannot push --exact with applied patches')
                    )
                root = self.series[start]
                target = patchheader(self.join(root), self.plainmode).parent
                if not target:
                    raise error.Abort(
                        _(b"%s does not have a parent recorded") % root
                    )
                if not repo[target] == repo[b'.']:
                    hg.update(repo, target)

            if move:
                if not patch:
                    raise error.Abort(_(b"please specify the patch to move"))
                for fullstart, rpn in enumerate(self.fullseries):
                    # strip markers for patch guards
                    if self.guard_re.split(rpn, 1)[0] == self.series[start]:
                        break
                for i, rpn in enumerate(self.fullseries[fullstart:]):
                    # strip markers for patch guards
                    if self.guard_re.split(rpn, 1)[0] == patch:
                        break
                index = fullstart + i
                assert index < len(self.fullseries)
                fullpatch = self.fullseries[index]
                del self.fullseries[index]
                self.fullseries.insert(fullstart, fullpatch)
                self.parseseries()
                self.seriesdirty = True

            self.applieddirty = True
            if start > 0:
                self.checktoppatch(repo)
            if not patch:
                patch = self.series[start]
                end = start + 1
            else:
                end = self.series.index(patch, start) + 1

            tobackup = set()
            if (not nobackup and force) or keepchanges:
                status = self.checklocalchanges(repo, force=True)
                if keepchanges:
                    tobackup.update(
                        status.modified
                        + status.added
                        + status.removed
                        + status.deleted
                    )
                else:
                    tobackup.update(status.modified + status.added)

            s = self.series[start:end]
            all_files = set()
            try:
                if mergeq:
                    ret = self.mergepatch(repo, mergeq, s, diffopts)
                else:
                    ret = self.apply(
                        repo,
                        s,
                        list,
                        all_files=all_files,
                        tobackup=tobackup,
                        keepchanges=keepchanges,
                    )
            except AbortNoCleanup:
                raise
            except:  # re-raises
                self.ui.warn(_(b'cleaning up working directory...\n'))
                cmdutil.revert(
                    self.ui,
                    repo,
                    repo[b'.'],
                    no_backup=True,
                )
                # only remove unknown files that we know we touched or
                # created while patching
                for f in all_files:
                    if f not in repo.dirstate:
                        repo.wvfs.unlinkpath(f, ignoremissing=True)
                self.ui.warn(_(b'done\n'))
                raise

            if not self.applied:
                return ret[0]
            top = self.applied[-1].name
            if ret[0] and ret[0] > 1:
                msg = _(b"errors during apply, please fix and qrefresh %s\n")
                self.ui.write(msg % top)
            else:
                self.ui.write(_(b"now at: %s\n") % top)
            return ret[0]

    def pop(
        self,
        repo,
        patch=None,
        force=False,
        update=True,
        all=False,
        nobackup=False,
        keepchanges=False,
    ):
        self.checkkeepchanges(keepchanges, force)
        with repo.wlock():
            if patch:
                # index, rev, patch
                info = self.isapplied(patch)
                if not info:
                    patch = self.lookup(patch)
                info = self.isapplied(patch)
                if not info:
                    raise error.Abort(_(b"patch %s is not applied") % patch)

            if not self.applied:
                # Allow qpop -a to work repeatedly,
                # but not qpop without an argument
                self.ui.warn(_(b"no patches applied\n"))
                return not all

            if all:
                start = 0
            elif patch:
                start = info[0] + 1
            else:
                start = len(self.applied) - 1

            if start >= len(self.applied):
                self.ui.warn(_(b"qpop: %s is already at the top\n") % patch)
                return

            if not update:
                parents = repo.dirstate.parents()
                rr = [x.node for x in self.applied]
                for p in parents:
                    if p in rr:
                        self.ui.warn(_(b"qpop: forcing dirstate update\n"))
                        update = True
            else:
                parents = [p.node() for p in repo[None].parents()]
                update = any(
                    entry.node in parents for entry in self.applied[start:]
                )

            tobackup = set()
            if update:
                s = self.checklocalchanges(repo, force=force or keepchanges)
                if force:
                    if not nobackup:
                        tobackup.update(s.modified + s.added)
                elif keepchanges:
                    tobackup.update(
                        s.modified + s.added + s.removed + s.deleted
                    )

            self.applieddirty = True
            end = len(self.applied)
            rev = self.applied[start].node

            try:
                heads = repo.changelog.heads(rev)
            except error.LookupError:
                node = short(rev)
                raise error.Abort(_(b'trying to pop unknown node %s') % node)

            if heads != [self.applied[-1].node]:
                raise error.Abort(
                    _(
                        b"popping would remove a revision not "
                        b"managed by this patch queue"
                    )
                )
            if not repo[self.applied[-1].node].mutable():
                raise error.Abort(
                    _(b"popping would remove a public revision"),
                    hint=_(b"see 'hg help phases' for details"),
                )

            # we know there are no local changes, so we can make a simplified
            # form of hg.update.
            if update:
                qp = self.qparents(repo, rev)
                ctx = repo[qp]
                st = repo.status(qp, b'.')
                m, a, r, d = st.modified, st.added, st.removed, st.deleted
                if d:
                    raise error.Abort(_(b"deletions found between repo revs"))

                tobackup = set(a + m + r) & tobackup
                if keepchanges and tobackup:
                    raise error.Abort(_(b"local changes found, qrefresh first"))
                self.backup(repo, tobackup)
                with repo.dirstate.parentchange():
                    for f in a:
                        repo.wvfs.unlinkpath(f, ignoremissing=True)
                        repo.dirstate.update_file(
                            f, p1_tracked=False, wc_tracked=False
                        )
                    for f in m + r:
                        fctx = ctx[f]
                        repo.wwrite(f, fctx.data(), fctx.flags())
                        repo.dirstate.update_file(
                            f, p1_tracked=True, wc_tracked=True
                        )
                    repo.setparents(qp, repo.nullid)
            for patch in reversed(self.applied[start:end]):
                self.ui.status(_(b"popping %s\n") % patch.name)
            del self.applied[start:end]
            strip(self.ui, repo, [rev], update=False, backup=False)
            for s, state in repo[b'.'].substate.items():
                repo[b'.'].sub(s).get(state)
            if self.applied:
                self.ui.write(_(b"now at: %s\n") % self.applied[-1].name)
            else:
                self.ui.write(_(b"patch queue now empty\n"))

    def diff(self, repo, pats, opts):
        top, patch = self.checktoppatch(repo)
        if not top:
            self.ui.write(_(b"no patches applied\n"))
            return
        qp = self.qparents(repo, top)
        if opts.get(b'reverse'):
            node1, node2 = None, qp
        else:
            node1, node2 = qp, None
        diffopts = self.diffopts(opts, patch)
        self.printdiff(repo, diffopts, node1, node2, files=pats, opts=opts)

    def refresh(self, repo, pats=None, **opts):
        opts = pycompat.byteskwargs(opts)
        if not self.applied:
            self.ui.write(_(b"no patches applied\n"))
            return 1
        msg = opts.get(b'msg', b'').rstrip()
        edit = opts.get(b'edit')
        editform = opts.get(b'editform', b'mq.qrefresh')
        newuser = opts.get(b'user')
        newdate = opts.get(b'date')
        if newdate:
            newdate = b'%d %d' % dateutil.parsedate(newdate)
        wlock = repo.wlock()

        try:
            self.checktoppatch(repo)
            (top, patchfn) = (self.applied[-1].node, self.applied[-1].name)
            if repo.changelog.heads(top) != [top]:
                raise error.Abort(
                    _(b"cannot qrefresh a revision with children")
                )
            if not repo[top].mutable():
                raise error.Abort(
                    _(b"cannot qrefresh public revision"),
                    hint=_(b"see 'hg help phases' for details"),
                )

            cparents = repo.changelog.parents(top)
            patchparent = self.qparents(repo, top)

            inclsubs = checksubstate(repo, patchparent)
            if inclsubs:
                substatestate = repo.dirstate[b'.hgsubstate']

            ph = patchheader(self.join(patchfn), self.plainmode)
            diffopts = self.diffopts(
                {b'git': opts.get(b'git')}, patchfn, plain=True
            )
            if newuser:
                ph.setuser(newuser)
            if newdate:
                ph.setdate(newdate)
            ph.setparent(hex(patchparent))

            # only commit new patch when write is complete
            patchf = self.opener(patchfn, b'w', atomictemp=True)

            # update the dirstate in place, strip off the qtip commit
            # and then commit.
            #
            # this should really read:
            #   st = repo.status(top, patchparent)
            # but we do it backwards to take advantage of manifest/changelog
            # caching against the next repo.status call
            st = repo.status(patchparent, top)
            mm, aa, dd = st.modified, st.added, st.removed
            ctx = repo[top]
            aaa = aa[:]
            match1 = scmutil.match(repo[None], pats, opts)
            # in short mode, we only diff the files included in the
            # patch already plus specified files
            if opts.get(b'short'):
                # if amending a patch, we start with existing
                # files plus specified files - unfiltered
                match = scmutil.matchfiles(repo, mm + aa + dd + match1.files())
                # filter with include/exclude options
                match1 = scmutil.match(repo[None], opts=opts)
            else:
                match = scmutil.matchall(repo)
            stb = repo.status(match=match)
            m, a, r, d = stb.modified, stb.added, stb.removed, stb.deleted
            mm = set(mm)
            aa = set(aa)
            dd = set(dd)

            # we might end up with files that were added between
            # qtip and the dirstate parent, but then changed in the
            # local dirstate. in this case, we want them to only
            # show up in the added section
            for x in m:
                if x not in aa:
                    mm.add(x)
            # we might end up with files added by the local dirstate that
            # were deleted by the patch.  In this case, they should only
            # show up in the changed section.
            for x in a:
                if x in dd:
                    dd.remove(x)
                    mm.add(x)
                else:
                    aa.add(x)
            # make sure any files deleted in the local dirstate
            # are not in the add or change column of the patch
            forget = []
            for x in d + r:
                if x in aa:
                    aa.remove(x)
                    forget.append(x)
                    continue
                else:
                    mm.discard(x)
                dd.add(x)

            m = list(mm)
            r = list(dd)
            a = list(aa)

            # create 'match' that includes the files to be recommitted.
            # apply match1 via repo.status to ensure correct case handling.
            st = repo.status(patchparent, match=match1)
            cm, ca, cr, cd = st.modified, st.added, st.removed, st.deleted
            allmatches = set(cm + ca + cr + cd)
            refreshchanges = [x.intersection(allmatches) for x in (mm, aa, dd)]

            files = set(inclsubs)
            for x in refreshchanges:
                files.update(x)
            match = scmutil.matchfiles(repo, files)

            bmlist = repo[top].bookmarks()

            with repo.dirstate.parentchange():
                # XXX do we actually need the dirstateguard
                dsguard = None
                try:
                    dsguard = dirstateguard.dirstateguard(repo, b'mq.refresh')
                    if diffopts.git or diffopts.upgrade:
                        copies = {}
                        for dst in a:
                            src = repo.dirstate.copied(dst)
                            # during qfold, the source file for copies may
                            # be removed. Treat this as a simple add.
                            if src is not None and src in repo.dirstate:
                                copies.setdefault(src, []).append(dst)
                            repo.dirstate.update_file(
                                dst, p1_tracked=False, wc_tracked=True
                            )
                        # remember the copies between patchparent and qtip
                        for dst in aaa:
                            src = ctx[dst].copysource()
                            if src:
                                copies.setdefault(src, []).extend(
                                    copies.get(dst, [])
                                )
                                if dst in a:
                                    copies[src].append(dst)
                            # we can't copy a file created by the patch itself
                            if dst in copies:
                                del copies[dst]
                        for src, dsts in pycompat.iteritems(copies):
                            for dst in dsts:
                                repo.dirstate.copy(src, dst)
                    else:
                        for dst in a:
                            repo.dirstate.update_file(
                                dst, p1_tracked=False, wc_tracked=True
                            )
                        # Drop useless copy information
                        for f in list(repo.dirstate.copies()):
                            repo.dirstate.copy(None, f)
                    for f in r:
                        repo.dirstate.update_file_p1(f, p1_tracked=True)
                    # if the patch excludes a modified file, mark that
                    # file with mtime=0 so status can see it.
                    mm = []
                    for i in pycompat.xrange(len(m) - 1, -1, -1):
                        if not match1(m[i]):
                            mm.append(m[i])
                            del m[i]
                    for f in m:
                        repo.dirstate.update_file_p1(f, p1_tracked=True)
                    for f in mm:
                        repo.dirstate.update_file_p1(f, p1_tracked=True)
                    for f in forget:
                        repo.dirstate.update_file_p1(f, p1_tracked=False)

                    user = ph.user or ctx.user()

                    oldphase = repo[top].phase()

                    # assumes strip can roll itself back if interrupted
                    repo.setparents(*cparents)
                    self.applied.pop()
                    self.applieddirty = True
                    strip(self.ui, repo, [top], update=False, backup=False)
                    dsguard.close()
                finally:
                    release(dsguard)

            try:
                # might be nice to attempt to roll back strip after this

                defaultmsg = b"[mq]: %s" % patchfn
                editor = cmdutil.getcommiteditor(editform=editform)
                if edit:

                    def finishdesc(desc):
                        if desc.rstrip():
                            ph.setmessage(desc)
                            return desc
                        return defaultmsg

                    # i18n: this message is shown in editor with "HG: " prefix
                    extramsg = _(b'Leave message empty to use default message.')
                    editor = cmdutil.getcommiteditor(
                        finishdesc=finishdesc,
                        extramsg=extramsg,
                        editform=editform,
                    )
                    message = msg or b"\n".join(ph.message)
                elif not msg:
                    if not ph.message:
                        message = defaultmsg
                    else:
                        message = b"\n".join(ph.message)
                else:
                    message = msg
                    ph.setmessage(msg)

                # Ensure we create a new changeset in the same phase than
                # the old one.
                lock = tr = None
                try:
                    lock = repo.lock()
                    tr = repo.transaction(b'mq')
                    n = newcommit(
                        repo,
                        oldphase,
                        message,
                        user,
                        ph.date,
                        match=match,
                        force=True,
                        editor=editor,
                    )
                    # only write patch after a successful commit
                    c = [list(x) for x in refreshchanges]
                    if inclsubs:
                        self.putsubstate2changes(substatestate, c)
                    chunks = patchmod.diff(
                        repo, patchparent, changes=c, opts=diffopts
                    )
                    comments = bytes(ph)
                    if comments:
                        patchf.write(comments)
                    for chunk in chunks:
                        patchf.write(chunk)
                    patchf.close()

                    marks = repo._bookmarks
                    marks.applychanges(repo, tr, [(bm, n) for bm in bmlist])
                    tr.close()

                    self.applied.append(statusentry(n, patchfn))
                finally:
                    lockmod.release(tr, lock)
            except:  # re-raises
                ctx = repo[cparents[0]]
                repo.dirstate.rebuild(ctx.node(), ctx.manifest())
                self.savedirty()
                self.ui.warn(
                    _(
                        b'qrefresh interrupted while patch was popped! '
                        b'(revert --all, qpush to recover)\n'
                    )
                )
                raise
        finally:
            wlock.release()
            self.removeundo(repo)

    def init(self, repo, create=False):
        if not create and os.path.isdir(self.path):
            raise error.Abort(_(b"patch queue directory already exists"))
        try:
            os.mkdir(self.path)
        except OSError as inst:
            if inst.errno != errno.EEXIST or not create:
                raise
        if create:
            return self.qrepo(create=True)

    def unapplied(self, repo, patch=None):
        if patch and patch not in self.series:
            raise error.Abort(_(b"patch %s is not in series file") % patch)
        if not patch:
            start = self.seriesend()
        else:
            start = self.series.index(patch) + 1
        unapplied = []
        for i in pycompat.xrange(start, len(self.series)):
            pushable, reason = self.pushable(i)
            if pushable:
                unapplied.append((i, self.series[i]))
            self.explainpushable(i)
        return unapplied

    def qseries(
        self,
        repo,
        missing=None,
        start=0,
        length=None,
        status=None,
        summary=False,
    ):
        def displayname(pfx, patchname, state):
            if pfx:
                self.ui.write(pfx)
            if summary:
                ph = patchheader(self.join(patchname), self.plainmode)
                if ph.message:
                    msg = ph.message[0]
                else:
                    msg = b''

                if self.ui.formatted():
                    width = self.ui.termwidth() - len(pfx) - len(patchname) - 2
                    if width > 0:
                        msg = stringutil.ellipsis(msg, width)
                    else:
                        msg = b''
                self.ui.write(patchname, label=b'qseries.' + state)
                self.ui.write(b': ')
                self.ui.write(msg, label=b'qseries.message.' + state)
            else:
                self.ui.write(patchname, label=b'qseries.' + state)
            self.ui.write(b'\n')

        applied = {p.name for p in self.applied}
        if length is None:
            length = len(self.series) - start
        if not missing:
            if self.ui.verbose:
                idxwidth = len(b"%d" % (start + length - 1))
            for i in pycompat.xrange(start, start + length):
                patch = self.series[i]
                if patch in applied:
                    char, state = b'A', b'applied'
                elif self.pushable(i)[0]:
                    char, state = b'U', b'unapplied'
                else:
                    char, state = b'G', b'guarded'
                pfx = b''
                if self.ui.verbose:
                    pfx = b'%*d %s ' % (idxwidth, i, char)
                elif status and status != char:
                    continue
                displayname(pfx, patch, state)
        else:
            msng_list = []
            for root, dirs, files in os.walk(self.path):
                d = root[len(self.path) + 1 :]
                for f in files:
                    fl = os.path.join(d, f)
                    if (
                        fl not in self.series
                        and fl
                        not in (
                            self.statuspath,
                            self.seriespath,
                            self.guardspath,
                        )
                        and not fl.startswith(b'.')
                    ):
                        msng_list.append(fl)
            for x in sorted(msng_list):
                pfx = self.ui.verbose and b'D ' or b''
                displayname(pfx, x, b'missing')

    def issaveline(self, l):
        if l.name == b'.hg.patches.save.line':
            return True

    def qrepo(self, create=False):
        ui = self.baseui.copy()
        # copy back attributes set by ui.pager()
        if self.ui.pageractive and not ui.pageractive:
            ui.pageractive = self.ui.pageractive
            # internal config: ui.formatted
            ui.setconfig(
                b'ui',
                b'formatted',
                self.ui.config(b'ui', b'formatted'),
                b'mqpager',
            )
            ui.setconfig(
                b'ui',
                b'interactive',
                self.ui.config(b'ui', b'interactive'),
                b'mqpager',
            )
        if create or os.path.isdir(self.join(b".hg")):
            return hg.repository(ui, path=self.path, create=create)

    def restore(self, repo, rev, delete=None, qupdate=None):
        desc = repo[rev].description().strip()
        lines = desc.splitlines()
        datastart = None
        series = []
        applied = []
        qpp = None
        for i, line in enumerate(lines):
            if line == b'Patch Data:':
                datastart = i + 1
            elif line.startswith(b'Dirstate:'):
                l = line.rstrip()
                l = l[10:].split(b' ')
                qpp = [bin(x) for x in l]
            elif datastart is not None:
                l = line.rstrip()
                n, name = l.split(b':', 1)
                if n:
                    applied.append(statusentry(bin(n), name))
                else:
                    series.append(l)
        if datastart is None:
            self.ui.warn(_(b"no saved patch data found\n"))
            return 1
        self.ui.warn(_(b"restoring status: %s\n") % lines[0])
        self.fullseries = series
        self.applied = applied
        self.parseseries()
        self.seriesdirty = True
        self.applieddirty = True
        heads = repo.changelog.heads()
        if delete:
            if rev not in heads:
                self.ui.warn(_(b"save entry has children, leaving it alone\n"))
            else:
                self.ui.warn(_(b"removing save entry %s\n") % short(rev))
                pp = repo.dirstate.parents()
                if rev in pp:
                    update = True
                else:
                    update = False
                strip(self.ui, repo, [rev], update=update, backup=False)
        if qpp:
            self.ui.warn(
                _(b"saved queue repository parents: %s %s\n")
                % (short(qpp[0]), short(qpp[1]))
            )
            if qupdate:
                self.ui.status(_(b"updating queue directory\n"))
                r = self.qrepo()
                if not r:
                    self.ui.warn(_(b"unable to load queue repository\n"))
                    return 1
                hg.clean(r, qpp[0])

    def save(self, repo, msg=None):
        if not self.applied:
            self.ui.warn(_(b"save: no patches applied, exiting\n"))
            return 1
        if self.issaveline(self.applied[-1]):
            self.ui.warn(_(b"status is already saved\n"))
            return 1

        if not msg:
            msg = _(b"hg patches saved state")
        else:
            msg = b"hg patches: " + msg.rstrip(b'\r\n')
        r = self.qrepo()
        if r:
            pp = r.dirstate.parents()
            msg += b"\nDirstate: %s %s" % (hex(pp[0]), hex(pp[1]))
        msg += b"\n\nPatch Data:\n"
        msg += b''.join(b'%s\n' % x for x in self.applied)
        msg += b''.join(b':%s\n' % x for x in self.fullseries)
        n = repo.commit(msg, force=True)
        if not n:
            self.ui.warn(_(b"repo commit failed\n"))
            return 1
        self.applied.append(statusentry(n, b'.hg.patches.save.line'))
        self.applieddirty = True
        self.removeundo(repo)

    def fullseriesend(self):
        if self.applied:
            p = self.applied[-1].name
            end = self.findseries(p)
            if end is None:
                return len(self.fullseries)
            return end + 1
        return 0

    def seriesend(self, all_patches=False):
        """If all_patches is False, return the index of the next pushable patch
        in the series, or the series length. If all_patches is True, return the
        index of the first patch past the last applied one.
        """
        end = 0

        def nextpatch(start):
            if all_patches or start >= len(self.series):
                return start
            for i in pycompat.xrange(start, len(self.series)):
                p, reason = self.pushable(i)
                if p:
                    return i
                self.explainpushable(i)
            return len(self.series)

        if self.applied:
            p = self.applied[-1].name
            try:
                end = self.series.index(p)
            except ValueError:
                return 0
            return nextpatch(end + 1)
        return nextpatch(end)

    def appliedname(self, index):
        pname = self.applied[index].name
        if not self.ui.verbose:
            p = pname
        else:
            p = (b"%d" % self.series.index(pname)) + b" " + pname
        return p

    def qimport(
        self,
        repo,
        files,
        patchname=None,
        rev=None,
        existing=None,
        force=None,
        git=False,
    ):
        def checkseries(patchname):
            if patchname in self.series:
                raise error.Abort(
                    _(b'patch %s is already in the series file') % patchname
                )

        if rev:
            if files:
                raise error.Abort(
                    _(b'option "-r" not valid when importing files')
                )
            rev = scmutil.revrange(repo, rev)
            rev.sort(reverse=True)
        elif not files:
            raise error.Abort(_(b'no files or revisions specified'))
        if (len(files) > 1 or len(rev) > 1) and patchname:
            raise error.Abort(
                _(b'option "-n" not valid when importing multiple patches')
            )
        imported = []
        if rev:
            # If mq patches are applied, we can only import revisions
            # that form a linear path to qbase.
            # Otherwise, they should form a linear path to a head.
            heads = repo.changelog.heads(repo.changelog.node(rev.first()))
            if len(heads) > 1:
                raise error.Abort(
                    _(b'revision %d is the root of more than one branch')
                    % rev.last()
                )
            if self.applied:
                base = repo.changelog.node(rev.first())
                if base in [n.node for n in self.applied]:
                    raise error.Abort(
                        _(b'revision %d is already managed') % rev.first()
                    )
                if heads != [self.applied[-1].node]:
                    raise error.Abort(
                        _(b'revision %d is not the parent of the queue')
                        % rev.first()
                    )
                base = repo.changelog.rev(self.applied[0].node)
                lastparent = repo.changelog.parentrevs(base)[0]
            else:
                if heads != [repo.changelog.node(rev.first())]:
                    raise error.Abort(
                        _(b'revision %d has unmanaged children') % rev.first()
                    )
                lastparent = None

            diffopts = self.diffopts({b'git': git})
            with repo.transaction(b'qimport') as tr:
                for r in rev:
                    if not repo[r].mutable():
                        raise error.Abort(
                            _(b'revision %d is not mutable') % r,
                            hint=_(b"see 'hg help phases' " b'for details'),
                        )
                    p1, p2 = repo.changelog.parentrevs(r)
                    n = repo.changelog.node(r)
                    if p2 != nullrev:
                        raise error.Abort(
                            _(b'cannot import merge revision %d') % r
                        )
                    if lastparent and lastparent != r:
                        raise error.Abort(
                            _(b'revision %d is not the parent of %d')
                            % (r, lastparent)
                        )
                    lastparent = p1

                    if not patchname:
                        patchname = self.makepatchname(
                            repo[r].description().split(b'\n', 1)[0],
                            b'%d.diff' % r,
                        )
                    checkseries(patchname)
                    self.checkpatchname(patchname, force)
                    self.fullseries.insert(0, patchname)

                    with self.opener(patchname, b"w") as fp:
                        cmdutil.exportfile(repo, [n], fp, opts=diffopts)

                    se = statusentry(n, patchname)
                    self.applied.insert(0, se)

                    self.added.append(patchname)
                    imported.append(patchname)
                    patchname = None
                    if rev and repo.ui.configbool(b'mq', b'secret'):
                        # if we added anything with --rev, move the secret root
                        phases.retractboundary(repo, tr, phases.secret, [n])
                    self.parseseries()
                    self.applieddirty = True
                    self.seriesdirty = True

        for i, filename in enumerate(files):
            if existing:
                if filename == b'-':
                    raise error.Abort(
                        _(b'-e is incompatible with import from -')
                    )
                filename = normname(filename)
                self.checkreservedname(filename)
                if urlutil.url(filename).islocal():
                    originpath = self.join(filename)
                    if not os.path.isfile(originpath):
                        raise error.Abort(
                            _(b"patch %s does not exist") % filename
                        )

                if patchname:
                    self.checkpatchname(patchname, force)

                    self.ui.write(
                        _(b'renaming %s to %s\n') % (filename, patchname)
                    )
                    util.rename(originpath, self.join(patchname))
                else:
                    patchname = filename

            else:
                if filename == b'-' and not patchname:
                    raise error.Abort(
                        _(b'need --name to import a patch from -')
                    )
                elif not patchname:
                    patchname = normname(
                        os.path.basename(filename.rstrip(b'/'))
                    )
                self.checkpatchname(patchname, force)
                try:
                    if filename == b'-':
                        text = self.ui.fin.read()
                    else:
                        fp = hg.openpath(self.ui, filename)
                        text = fp.read()
                        fp.close()
                except (OSError, IOError):
                    raise error.Abort(_(b"unable to read file %s") % filename)
                patchf = self.opener(patchname, b"w")
                patchf.write(text)
                patchf.close()
            if not force:
                checkseries(patchname)
            if patchname not in self.series:
                index = self.fullseriesend() + i
                self.fullseries[index:index] = [patchname]
            self.parseseries()
            self.seriesdirty = True
            self.ui.warn(_(b"adding %s to series file\n") % patchname)
            self.added.append(patchname)
            imported.append(patchname)
            patchname = None

        self.removeundo(repo)
        return imported


def fixkeepchangesopts(ui, opts):
    if (
        not ui.configbool(b'mq', b'keepchanges')
        or opts.get(b'force')
        or opts.get(b'exact')
    ):
        return opts
    opts = dict(opts)
    opts[b'keep_changes'] = True
    return opts


@command(
    b"qdelete|qremove|qrm",
    [
        (b'k', b'keep', None, _(b'keep patch file')),
        (
            b'r',
            b'rev',
            [],
            _(b'stop managing a revision (DEPRECATED)'),
            _(b'REV'),
        ),
    ],
    _(b'hg qdelete [-k] [PATCH]...'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def delete(ui, repo, *patches, **opts):
    """remove patches from queue

    The patches must not be applied, and at least one patch is required. Exact
    patch identifiers must be given. With -k/--keep, the patch files are
    preserved in the patch directory.

    To stop managing a patch and move it into permanent history,
    use the :hg:`qfinish` command."""
    q = repo.mq
    q.delete(repo, patches, pycompat.byteskwargs(opts))
    q.savedirty()
    return 0


@command(
    b"qapplied",
    [(b'1', b'last', None, _(b'show only the preceding applied patch'))]
    + seriesopts,
    _(b'hg qapplied [-1] [-s] [PATCH]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def applied(ui, repo, patch=None, **opts):
    """print the patches already applied

    Returns 0 on success."""

    q = repo.mq
    opts = pycompat.byteskwargs(opts)

    if patch:
        if patch not in q.series:
            raise error.Abort(_(b"patch %s is not in series file") % patch)
        end = q.series.index(patch) + 1
    else:
        end = q.seriesend(True)

    if opts.get(b'last') and not end:
        ui.write(_(b"no patches applied\n"))
        return 1
    elif opts.get(b'last') and end == 1:
        ui.write(_(b"only one patch applied\n"))
        return 1
    elif opts.get(b'last'):
        start = end - 2
        end = 1
    else:
        start = 0

    q.qseries(
        repo, length=end, start=start, status=b'A', summary=opts.get(b'summary')
    )


@command(
    b"qunapplied",
    [(b'1', b'first', None, _(b'show only the first patch'))] + seriesopts,
    _(b'hg qunapplied [-1] [-s] [PATCH]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def unapplied(ui, repo, patch=None, **opts):
    """print the patches not yet applied

    Returns 0 on success."""

    q = repo.mq
    opts = pycompat.byteskwargs(opts)
    if patch:
        if patch not in q.series:
            raise error.Abort(_(b"patch %s is not in series file") % patch)
        start = q.series.index(patch) + 1
    else:
        start = q.seriesend(True)

    if start == len(q.series) and opts.get(b'first'):
        ui.write(_(b"all patches applied\n"))
        return 1

    if opts.get(b'first'):
        length = 1
    else:
        length = None
    q.qseries(
        repo,
        start=start,
        length=length,
        status=b'U',
        summary=opts.get(b'summary'),
    )


@command(
    b"qimport",
    [
        (b'e', b'existing', None, _(b'import file in patch directory')),
        (b'n', b'name', b'', _(b'name of patch file'), _(b'NAME')),
        (b'f', b'force', None, _(b'overwrite existing files')),
        (
            b'r',
            b'rev',
            [],
            _(b'place existing revisions under mq control'),
            _(b'REV'),
        ),
        (b'g', b'git', None, _(b'use git extended diff format')),
        (b'P', b'push', None, _(b'qpush after importing')),
    ],
    _(b'hg qimport [-e] [-n NAME] [-f] [-g] [-P] [-r REV]... [FILE]...'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def qimport(ui, repo, *filename, **opts):
    """import a patch or existing changeset

    The patch is inserted into the series after the last applied
    patch. If no patches have been applied, qimport prepends the patch
    to the series.

    The patch will have the same name as its source file unless you
    give it a new one with -n/--name.

    You can register an existing patch inside the patch directory with
    the -e/--existing flag.

    With -f/--force, an existing patch of the same name will be
    overwritten.

    An existing changeset may be placed under mq control with -r/--rev
    (e.g. qimport --rev . -n patch will place the current revision
    under mq control). With -g/--git, patches imported with --rev will
    use the git diff format. See the diffs help topic for information
    on why this is important for preserving rename/copy information
    and permission changes. Use :hg:`qfinish` to remove changesets
    from mq control.

    To import a patch from standard input, pass - as the patch file.
    When importing from standard input, a patch name must be specified
    using the --name flag.

    To import an existing patch while renaming it::

      hg qimport -e existing-patch -n new-name

    Returns 0 if import succeeded.
    """
    opts = pycompat.byteskwargs(opts)
    with repo.lock():  # cause this may move phase
        q = repo.mq
        try:
            imported = q.qimport(
                repo,
                filename,
                patchname=opts.get(b'name'),
                existing=opts.get(b'existing'),
                force=opts.get(b'force'),
                rev=opts.get(b'rev'),
                git=opts.get(b'git'),
            )
        finally:
            q.savedirty()

    if imported and opts.get(b'push') and not opts.get(b'rev'):
        return q.push(repo, imported[-1])
    return 0


def qinit(ui, repo, create):
    """initialize a new queue repository

    This command also creates a series file for ordering patches, and
    an mq-specific .hgignore file in the queue repository, to exclude
    the status and guards files (these contain mostly transient state).

    Returns 0 if initialization succeeded."""
    q = repo.mq
    r = q.init(repo, create)
    q.savedirty()
    if r:
        if not os.path.exists(r.wjoin(b'.hgignore')):
            fp = r.wvfs(b'.hgignore', b'w')
            fp.write(b'^\\.hg\n')
            fp.write(b'^\\.mq\n')
            fp.write(b'syntax: glob\n')
            fp.write(b'status\n')
            fp.write(b'guards\n')
            fp.close()
        if not os.path.exists(r.wjoin(b'series')):
            r.wvfs(b'series', b'w').close()
        r[None].add([b'.hgignore', b'series'])
        commands.add(ui, r)
    return 0


@command(
    b"qinit",
    [(b'c', b'create-repo', None, _(b'create queue repository'))],
    _(b'hg qinit [-c]'),
    helpcategory=command.CATEGORY_REPO_CREATION,
    helpbasic=True,
)
def init(ui, repo, **opts):
    """init a new queue repository (DEPRECATED)

    The queue repository is unversioned by default. If
    -c/--create-repo is specified, qinit will create a separate nested
    repository for patches (qinit -c may also be run later to convert
    an unversioned patch repository into a versioned one). You can use
    qcommit to commit changes to this queue repository.

    This command is deprecated. Without -c, it's implied by other relevant
    commands. With -c, use :hg:`init --mq` instead."""
    return qinit(ui, repo, create=opts.get('create_repo'))


@command(
    b"qclone",
    [
        (b'', b'pull', None, _(b'use pull protocol to copy metadata')),
        (
            b'U',
            b'noupdate',
            None,
            _(b'do not update the new working directories'),
        ),
        (
            b'',
            b'uncompressed',
            None,
            _(b'use uncompressed transfer (fast over LAN)'),
        ),
        (
            b'p',
            b'patches',
            b'',
            _(b'location of source patch repository'),
            _(b'REPO'),
        ),
    ]
    + cmdutil.remoteopts,
    _(b'hg qclone [OPTION]... SOURCE [DEST]'),
    helpcategory=command.CATEGORY_REPO_CREATION,
    norepo=True,
)
def clone(ui, source, dest=None, **opts):
    """clone main and patch repository at same time

    If source is local, destination will have no patches applied. If
    source is remote, this command can not check if patches are
    applied in source, so cannot guarantee that patches are not
    applied in destination. If you clone remote repository, be sure
    before that it has no patches applied.

    Source patch repository is looked for in <src>/.hg/patches by
    default. Use -p <url> to change.

    The patch directory must be a nested Mercurial repository, as
    would be created by :hg:`init --mq`.

    Return 0 on success.
    """
    opts = pycompat.byteskwargs(opts)

    def patchdir(repo):
        """compute a patch repo url from a repo object"""
        url = repo.url()
        if url.endswith(b'/'):
            url = url[:-1]
        return url + b'/.hg/patches'

    # main repo (destination and sources)
    if dest is None:
        dest = hg.defaultdest(source)
    __, source_path, __ = urlutil.get_clone_path(ui, source)
    sr = hg.peer(ui, opts, source_path)

    # patches repo (source only)
    if opts.get(b'patches'):
        __, patchespath, __ = urlutil.get_clone_path(ui, opts.get(b'patches'))
    else:
        patchespath = patchdir(sr)
    try:
        hg.peer(ui, opts, patchespath)
    except error.RepoError:
        raise error.Abort(
            _(b'versioned patch repository not found (see init --mq)')
        )
    qbase, destrev = None, None
    if sr.local():
        repo = sr.local()
        if repo.mq.applied and repo[qbase].phase() != phases.secret:
            qbase = repo.mq.applied[0].node
            if not hg.islocal(dest):
                heads = set(repo.heads())
                destrev = list(heads.difference(repo.heads(qbase)))
                destrev.append(repo.changelog.parents(qbase)[0])
    elif sr.capable(b'lookup'):
        try:
            qbase = sr.lookup(b'qbase')
        except error.RepoError:
            pass

    ui.note(_(b'cloning main repository\n'))
    sr, dr = hg.clone(
        ui,
        opts,
        sr.url(),
        dest,
        pull=opts.get(b'pull'),
        revs=destrev,
        update=False,
        stream=opts.get(b'uncompressed'),
    )

    ui.note(_(b'cloning patch repository\n'))
    hg.clone(
        ui,
        opts,
        opts.get(b'patches') or patchdir(sr),
        patchdir(dr),
        pull=opts.get(b'pull'),
        update=not opts.get(b'noupdate'),
        stream=opts.get(b'uncompressed'),
    )

    if dr.local():
        repo = dr.local()
        if qbase:
            ui.note(
                _(
                    b'stripping applied patches from destination '
                    b'repository\n'
                )
            )
            strip(ui, repo, [qbase], update=False, backup=None)
        if not opts.get(b'noupdate'):
            ui.note(_(b'updating destination repository\n'))
            hg.update(repo, repo.changelog.tip())


@command(
    b"qcommit|qci",
    commands.table[b"commit|ci"][1],
    _(b'hg qcommit [OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
    inferrepo=True,
)
def commit(ui, repo, *pats, **opts):
    """commit changes in the queue repository (DEPRECATED)

    This command is deprecated; use :hg:`commit --mq` instead."""
    q = repo.mq
    r = q.qrepo()
    if not r:
        raise error.Abort(b'no queue repository')
    commands.commit(r.ui, r, *pats, **opts)


@command(
    b"qseries",
    [
        (b'm', b'missing', None, _(b'print patches not in series')),
    ]
    + seriesopts,
    _(b'hg qseries [-ms]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def series(ui, repo, **opts):
    """print the entire series file

    Returns 0 on success."""
    repo.mq.qseries(
        repo, missing=opts.get('missing'), summary=opts.get('summary')
    )
    return 0


@command(
    b"qtop",
    seriesopts,
    _(b'hg qtop [-s]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def top(ui, repo, **opts):
    """print the name of the current patch

    Returns 0 on success."""
    q = repo.mq
    if q.applied:
        t = q.seriesend(True)
    else:
        t = 0

    if t:
        q.qseries(
            repo,
            start=t - 1,
            length=1,
            status=b'A',
            summary=opts.get('summary'),
        )
    else:
        ui.write(_(b"no patches applied\n"))
        return 1


@command(
    b"qnext",
    seriesopts,
    _(b'hg qnext [-s]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def next(ui, repo, **opts):
    """print the name of the next pushable patch

    Returns 0 on success."""
    q = repo.mq
    end = q.seriesend()
    if end == len(q.series):
        ui.write(_(b"all patches applied\n"))
        return 1
    q.qseries(repo, start=end, length=1, summary=opts.get('summary'))


@command(
    b"qprev",
    seriesopts,
    _(b'hg qprev [-s]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def prev(ui, repo, **opts):
    """print the name of the preceding applied patch

    Returns 0 on success."""
    q = repo.mq
    l = len(q.applied)
    if l == 1:
        ui.write(_(b"only one patch applied\n"))
        return 1
    if not l:
        ui.write(_(b"no patches applied\n"))
        return 1
    idx = q.series.index(q.applied[-2].name)
    q.qseries(
        repo, start=idx, length=1, status=b'A', summary=opts.get('summary')
    )


def setupheaderopts(ui, opts):
    if not opts.get(b'user') and opts.get(b'currentuser'):
        opts[b'user'] = ui.username()
    if not opts.get(b'date') and opts.get(b'currentdate'):
        opts[b'date'] = b"%d %d" % dateutil.makedate()


@command(
    b"qnew",
    [
        (b'e', b'edit', None, _(b'invoke editor on commit messages')),
        (b'f', b'force', None, _(b'import uncommitted changes (DEPRECATED)')),
        (b'g', b'git', None, _(b'use git extended diff format')),
        (b'U', b'currentuser', None, _(b'add "From: <current user>" to patch')),
        (b'u', b'user', b'', _(b'add "From: <USER>" to patch'), _(b'USER')),
        (b'D', b'currentdate', None, _(b'add "Date: <current date>" to patch')),
        (b'd', b'date', b'', _(b'add "Date: <DATE>" to patch'), _(b'DATE')),
    ]
    + cmdutil.walkopts
    + cmdutil.commitopts,
    _(b'hg qnew [-e] [-m TEXT] [-l FILE] PATCH [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
    helpbasic=True,
    inferrepo=True,
)
def new(ui, repo, patch, *args, **opts):
    """create a new patch

    qnew creates a new patch on top of the currently-applied patch (if
    any). The patch will be initialized with any outstanding changes
    in the working directory. You may also use -I/--include,
    -X/--exclude, and/or a list of files after the patch name to add
    only changes to matching files to the new patch, leaving the rest
    as uncommitted modifications.

    -u/--user and -d/--date can be used to set the (given) user and
    date, respectively. -U/--currentuser and -D/--currentdate set user
    to current user and date to current date.

    -e/--edit, -m/--message or -l/--logfile set the patch header as
    well as the commit message. If none is specified, the header is
    empty and the commit message is '[mq]: PATCH'.

    Use the -g/--git option to keep the patch in the git extended diff
    format. Read the diffs help topic for more information on why this
    is important for preserving permission changes and copy/rename
    information.

    Returns 0 on successful creation of a new patch.
    """
    opts = pycompat.byteskwargs(opts)
    msg = cmdutil.logmessage(ui, opts)
    q = repo.mq
    opts[b'msg'] = msg
    setupheaderopts(ui, opts)
    q.new(repo, patch, *args, **pycompat.strkwargs(opts))
    q.savedirty()
    return 0


@command(
    b"qrefresh",
    [
        (b'e', b'edit', None, _(b'invoke editor on commit messages')),
        (b'g', b'git', None, _(b'use git extended diff format')),
        (
            b's',
            b'short',
            None,
            _(b'refresh only files already in the patch and specified files'),
        ),
        (
            b'U',
            b'currentuser',
            None,
            _(b'add/update author field in patch with current user'),
        ),
        (
            b'u',
            b'user',
            b'',
            _(b'add/update author field in patch with given user'),
            _(b'USER'),
        ),
        (
            b'D',
            b'currentdate',
            None,
            _(b'add/update date field in patch with current date'),
        ),
        (
            b'd',
            b'date',
            b'',
            _(b'add/update date field in patch with given date'),
            _(b'DATE'),
        ),
    ]
    + cmdutil.walkopts
    + cmdutil.commitopts,
    _(b'hg qrefresh [-I] [-X] [-e] [-m TEXT] [-l FILE] [-s] [FILE]...'),
    helpcategory=command.CATEGORY_COMMITTING,
    helpbasic=True,
    inferrepo=True,
)
def refresh(ui, repo, *pats, **opts):
    """update the current patch

    If any file patterns are provided, the refreshed patch will
    contain only the modifications that match those patterns; the
    remaining modifications will remain in the working directory.

    If -s/--short is specified, files currently included in the patch
    will be refreshed just like matched files and remain in the patch.

    If -e/--edit is specified, Mercurial will start your configured editor for
    you to enter a message. In case qrefresh fails, you will find a backup of
    your message in ``.hg/last-message.txt``.

    hg add/remove/copy/rename work as usual, though you might want to
    use git-style patches (-g/--git or [diff] git=1) to track copies
    and renames. See the diffs help topic for more information on the
    git diff format.

    Returns 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    q = repo.mq
    message = cmdutil.logmessage(ui, opts)
    setupheaderopts(ui, opts)
    with repo.wlock():
        ret = q.refresh(repo, pats, msg=message, **pycompat.strkwargs(opts))
        q.savedirty()
        return ret


@command(
    b"qdiff",
    cmdutil.diffopts + cmdutil.diffopts2 + cmdutil.walkopts,
    _(b'hg qdiff [OPTION]... [FILE]...'),
    helpcategory=command.CATEGORY_FILE_CONTENTS,
    helpbasic=True,
    inferrepo=True,
)
def diff(ui, repo, *pats, **opts):
    """diff of the current patch and subsequent modifications

    Shows a diff which includes the current patch as well as any
    changes which have been made in the working directory since the
    last refresh (thus showing what the current patch would become
    after a qrefresh).

    Use :hg:`diff` if you only want to see the changes made since the
    last qrefresh, or :hg:`export qtip` if you want to see changes
    made by the current patch without including changes made since the
    qrefresh.

    Returns 0 on success.
    """
    ui.pager(b'qdiff')
    repo.mq.diff(repo, pats, pycompat.byteskwargs(opts))
    return 0


@command(
    b'qfold',
    [
        (b'e', b'edit', None, _(b'invoke editor on commit messages')),
        (b'k', b'keep', None, _(b'keep folded patch files')),
    ]
    + cmdutil.commitopts,
    _(b'hg qfold [-e] [-k] [-m TEXT] [-l FILE] PATCH...'),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
)
def fold(ui, repo, *files, **opts):
    """fold the named patches into the current patch

    Patches must not yet be applied. Each patch will be successively
    applied to the current patch in the order given. If all the
    patches apply successfully, the current patch will be refreshed
    with the new cumulative patch, and the folded patches will be
    deleted. With -k/--keep, the folded patch files will not be
    removed afterwards.

    The header for each folded patch will be concatenated with the
    current patch header, separated by a line of ``* * *``.

    Returns 0 on success."""
    opts = pycompat.byteskwargs(opts)
    q = repo.mq
    if not files:
        raise error.Abort(_(b'qfold requires at least one patch name'))
    if not q.checktoppatch(repo)[0]:
        raise error.Abort(_(b'no patches applied'))
    q.checklocalchanges(repo)

    message = cmdutil.logmessage(ui, opts)

    parent = q.lookup(b'qtip')
    patches = []
    messages = []
    for f in files:
        p = q.lookup(f)
        if p in patches or p == parent:
            ui.warn(_(b'skipping already folded patch %s\n') % p)
        if q.isapplied(p):
            raise error.Abort(
                _(b'qfold cannot fold already applied patch %s') % p
            )
        patches.append(p)

    for p in patches:
        if not message:
            ph = patchheader(q.join(p), q.plainmode)
            if ph.message:
                messages.append(ph.message)
        pf = q.join(p)
        (patchsuccess, files, fuzz) = q.patch(repo, pf)
        if not patchsuccess:
            raise error.Abort(_(b'error folding patch %s') % p)

    if not message:
        ph = patchheader(q.join(parent), q.plainmode)
        message = ph.message
        for msg in messages:
            if msg:
                if message:
                    message.append(b'* * *')
                message.extend(msg)
        message = b'\n'.join(message)

    diffopts = q.patchopts(q.diffopts(), *patches)
    with repo.wlock():
        q.refresh(
            repo,
            msg=message,
            git=diffopts.git,
            edit=opts.get(b'edit'),
            editform=b'mq.qfold',
        )
        q.delete(repo, patches, opts)
        q.savedirty()


@command(
    b"qgoto",
    [
        (
            b'',
            b'keep-changes',
            None,
            _(b'tolerate non-conflicting local changes'),
        ),
        (b'f', b'force', None, _(b'overwrite any local changes')),
        (b'', b'no-backup', None, _(b'do not save backup copies of files')),
    ],
    _(b'hg qgoto [OPTION]... PATCH'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def goto(ui, repo, patch, **opts):
    """push or pop patches until named patch is at top of stack

    Returns 0 on success."""
    opts = pycompat.byteskwargs(opts)
    opts = fixkeepchangesopts(ui, opts)
    q = repo.mq
    patch = q.lookup(patch)
    nobackup = opts.get(b'no_backup')
    keepchanges = opts.get(b'keep_changes')
    if q.isapplied(patch):
        ret = q.pop(
            repo,
            patch,
            force=opts.get(b'force'),
            nobackup=nobackup,
            keepchanges=keepchanges,
        )
    else:
        ret = q.push(
            repo,
            patch,
            force=opts.get(b'force'),
            nobackup=nobackup,
            keepchanges=keepchanges,
        )
    q.savedirty()
    return ret


@command(
    b"qguard",
    [
        (b'l', b'list', None, _(b'list all patches and guards')),
        (b'n', b'none', None, _(b'drop all guards')),
    ],
    _(b'hg qguard [-l] [-n] [PATCH] [-- [+GUARD]... [-GUARD]...]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def guard(ui, repo, *args, **opts):
    """set or print guards for a patch

    Guards control whether a patch can be pushed. A patch with no
    guards is always pushed. A patch with a positive guard ("+foo") is
    pushed only if the :hg:`qselect` command has activated it. A patch with
    a negative guard ("-foo") is never pushed if the :hg:`qselect` command
    has activated it.

    With no arguments, print the currently active guards.
    With arguments, set guards for the named patch.

    .. note::

       Specifying negative guards now requires '--'.

    To set guards on another patch::

      hg qguard other.patch -- +2.6.17 -stable

    Returns 0 on success.
    """

    def status(idx):
        guards = q.seriesguards[idx] or [b'unguarded']
        if q.series[idx] in applied:
            state = b'applied'
        elif q.pushable(idx)[0]:
            state = b'unapplied'
        else:
            state = b'guarded'
        label = b'qguard.patch qguard.%s qseries.%s' % (state, state)
        ui.write(b'%s: ' % ui.label(q.series[idx], label))

        for i, guard in enumerate(guards):
            if guard.startswith(b'+'):
                ui.write(guard, label=b'qguard.positive')
            elif guard.startswith(b'-'):
                ui.write(guard, label=b'qguard.negative')
            else:
                ui.write(guard, label=b'qguard.unguarded')
            if i != len(guards) - 1:
                ui.write(b' ')
        ui.write(b'\n')

    q = repo.mq
    applied = {p.name for p in q.applied}
    patch = None
    args = list(args)
    if opts.get('list'):
        if args or opts.get('none'):
            raise error.Abort(
                _(b'cannot mix -l/--list with options or arguments')
            )
        for i in pycompat.xrange(len(q.series)):
            status(i)
        return
    if not args or args[0][0:1] in b'-+':
        if not q.applied:
            raise error.Abort(_(b'no patches applied'))
        patch = q.applied[-1].name
    if patch is None and args[0][0:1] not in b'-+':
        patch = args.pop(0)
    if patch is None:
        raise error.Abort(_(b'no patch to work with'))
    if args or opts.get('none'):
        idx = q.findseries(patch)
        if idx is None:
            raise error.Abort(_(b'no patch named %s') % patch)
        q.setguards(idx, args)
        q.savedirty()
    else:
        status(q.series.index(q.lookup(patch)))


@command(
    b"qheader",
    [],
    _(b'hg qheader [PATCH]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def header(ui, repo, patch=None):
    """print the header of the topmost or specified patch

    Returns 0 on success."""
    q = repo.mq

    if patch:
        patch = q.lookup(patch)
    else:
        if not q.applied:
            ui.write(_(b'no patches applied\n'))
            return 1
        patch = q.lookup(b'qtip')
    ph = patchheader(q.join(patch), q.plainmode)

    ui.write(b'\n'.join(ph.message) + b'\n')


def lastsavename(path):
    (directory, base) = os.path.split(path)
    names = os.listdir(directory)
    namere = re.compile(b"%s.([0-9]+)" % base)
    maxindex = None
    maxname = None
    for f in names:
        m = namere.match(f)
        if m:
            index = int(m.group(1))
            if maxindex is None or index > maxindex:
                maxindex = index
                maxname = f
    if maxname:
        return (os.path.join(directory, maxname), maxindex)
    return (None, None)


def savename(path):
    (last, index) = lastsavename(path)
    if last is None:
        index = 0
    newpath = path + b".%d" % (index + 1)
    return newpath


@command(
    b"qpush",
    [
        (
            b'',
            b'keep-changes',
            None,
            _(b'tolerate non-conflicting local changes'),
        ),
        (b'f', b'force', None, _(b'apply on top of local changes')),
        (
            b'e',
            b'exact',
            None,
            _(b'apply the target patch to its recorded parent'),
        ),
        (b'l', b'list', None, _(b'list patch name in commit text')),
        (b'a', b'all', None, _(b'apply all patches')),
        (b'm', b'merge', None, _(b'merge from another queue (DEPRECATED)')),
        (b'n', b'name', b'', _(b'merge queue name (DEPRECATED)'), _(b'NAME')),
        (
            b'',
            b'move',
            None,
            _(b'reorder patch series and apply only the patch'),
        ),
        (b'', b'no-backup', None, _(b'do not save backup copies of files')),
    ],
    _(b'hg qpush [-f] [-l] [-a] [--move] [PATCH | INDEX]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
    helpbasic=True,
)
def push(ui, repo, patch=None, **opts):
    """push the next patch onto the stack

    By default, abort if the working directory contains uncommitted
    changes. With --keep-changes, abort only if the uncommitted files
    overlap with patched files. With -f/--force, backup and patch over
    uncommitted changes.

    Return 0 on success.
    """
    q = repo.mq
    mergeq = None

    opts = pycompat.byteskwargs(opts)
    opts = fixkeepchangesopts(ui, opts)
    if opts.get(b'merge'):
        if opts.get(b'name'):
            newpath = repo.vfs.join(opts.get(b'name'))
        else:
            newpath, i = lastsavename(q.path)
        if not newpath:
            ui.warn(_(b"no saved queues found, please use -n\n"))
            return 1
        mergeq = queue(ui, repo.baseui, repo.path, newpath)
        ui.warn(_(b"merging with queue at: %s\n") % mergeq.path)
    ret = q.push(
        repo,
        patch,
        force=opts.get(b'force'),
        list=opts.get(b'list'),
        mergeq=mergeq,
        all=opts.get(b'all'),
        move=opts.get(b'move'),
        exact=opts.get(b'exact'),
        nobackup=opts.get(b'no_backup'),
        keepchanges=opts.get(b'keep_changes'),
    )
    return ret


@command(
    b"qpop",
    [
        (b'a', b'all', None, _(b'pop all patches')),
        (b'n', b'name', b'', _(b'queue name to pop (DEPRECATED)'), _(b'NAME')),
        (
            b'',
            b'keep-changes',
            None,
            _(b'tolerate non-conflicting local changes'),
        ),
        (b'f', b'force', None, _(b'forget any local changes to patched files')),
        (b'', b'no-backup', None, _(b'do not save backup copies of files')),
    ],
    _(b'hg qpop [-a] [-f] [PATCH | INDEX]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
    helpbasic=True,
)
def pop(ui, repo, patch=None, **opts):
    """pop the current patch off the stack

    Without argument, pops off the top of the patch stack. If given a
    patch name, keeps popping off patches until the named patch is at
    the top of the stack.

    By default, abort if the working directory contains uncommitted
    changes. With --keep-changes, abort only if the uncommitted files
    overlap with patched files. With -f/--force, backup and discard
    changes made to such files.

    Return 0 on success.
    """
    opts = pycompat.byteskwargs(opts)
    opts = fixkeepchangesopts(ui, opts)
    localupdate = True
    if opts.get(b'name'):
        q = queue(ui, repo.baseui, repo.path, repo.vfs.join(opts.get(b'name')))
        ui.warn(_(b'using patch queue: %s\n') % q.path)
        localupdate = False
    else:
        q = repo.mq
    ret = q.pop(
        repo,
        patch,
        force=opts.get(b'force'),
        update=localupdate,
        all=opts.get(b'all'),
        nobackup=opts.get(b'no_backup'),
        keepchanges=opts.get(b'keep_changes'),
    )
    q.savedirty()
    return ret


@command(
    b"qrename|qmv",
    [],
    _(b'hg qrename PATCH1 [PATCH2]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def rename(ui, repo, patch, name=None, **opts):
    """rename a patch

    With one argument, renames the current patch to PATCH1.
    With two arguments, renames PATCH1 to PATCH2.

    Returns 0 on success."""
    q = repo.mq
    if not name:
        name = patch
        patch = None

    if patch:
        patch = q.lookup(patch)
    else:
        if not q.applied:
            ui.write(_(b'no patches applied\n'))
            return
        patch = q.lookup(b'qtip')
    absdest = q.join(name)
    if os.path.isdir(absdest):
        name = normname(os.path.join(name, os.path.basename(patch)))
        absdest = q.join(name)
    q.checkpatchname(name)

    ui.note(_(b'renaming %s to %s\n') % (patch, name))
    i = q.findseries(patch)
    guards = q.guard_re.findall(q.fullseries[i])
    q.fullseries[i] = name + b''.join([b' #' + g for g in guards])
    q.parseseries()
    q.seriesdirty = True

    info = q.isapplied(patch)
    if info:
        q.applied[info[0]] = statusentry(info[1], name)
    q.applieddirty = True

    destdir = os.path.dirname(absdest)
    if not os.path.isdir(destdir):
        os.makedirs(destdir)
    util.rename(q.join(patch), absdest)
    r = q.qrepo()
    if r and patch in r.dirstate:
        wctx = r[None]
        with r.wlock():
            if r.dirstate[patch] == b'a':
                r.dirstate.set_untracked(patch)
                r.dirstate.set_tracked(name)
            else:
                wctx.copy(patch, name)
                wctx.forget([patch])

    q.savedirty()


@command(
    b"qrestore",
    [
        (b'd', b'delete', None, _(b'delete save entry')),
        (b'u', b'update', None, _(b'update queue working directory')),
    ],
    _(b'hg qrestore [-d] [-u] REV'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def restore(ui, repo, rev, **opts):
    """restore the queue state saved by a revision (DEPRECATED)

    This command is deprecated, use :hg:`rebase` instead."""
    rev = repo.lookup(rev)
    q = repo.mq
    q.restore(repo, rev, delete=opts.get('delete'), qupdate=opts.get('update'))
    q.savedirty()
    return 0


@command(
    b"qsave",
    [
        (b'c', b'copy', None, _(b'copy patch directory')),
        (b'n', b'name', b'', _(b'copy directory name'), _(b'NAME')),
        (b'e', b'empty', None, _(b'clear queue status file')),
        (b'f', b'force', None, _(b'force copy')),
    ]
    + cmdutil.commitopts,
    _(b'hg qsave [-m TEXT] [-l FILE] [-c] [-n NAME] [-e] [-f]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def save(ui, repo, **opts):
    """save current queue state (DEPRECATED)

    This command is deprecated, use :hg:`rebase` instead."""
    q = repo.mq
    opts = pycompat.byteskwargs(opts)
    message = cmdutil.logmessage(ui, opts)
    ret = q.save(repo, msg=message)
    if ret:
        return ret
    q.savedirty()  # save to .hg/patches before copying
    if opts.get(b'copy'):
        path = q.path
        if opts.get(b'name'):
            newpath = os.path.join(q.basepath, opts.get(b'name'))
            if os.path.exists(newpath):
                if not os.path.isdir(newpath):
                    raise error.Abort(
                        _(b'destination %s exists and is not a directory')
                        % newpath
                    )
                if not opts.get(b'force'):
                    raise error.Abort(
                        _(b'destination %s exists, use -f to force') % newpath
                    )
        else:
            newpath = savename(path)
        ui.warn(_(b"copy %s to %s\n") % (path, newpath))
        util.copyfiles(path, newpath)
    if opts.get(b'empty'):
        del q.applied[:]
        q.applieddirty = True
        q.savedirty()
    return 0


@command(
    b"qselect",
    [
        (b'n', b'none', None, _(b'disable all guards')),
        (b's', b'series', None, _(b'list all guards in series file')),
        (b'', b'pop', None, _(b'pop to before first guarded applied patch')),
        (b'', b'reapply', None, _(b'pop, then reapply patches')),
    ],
    _(b'hg qselect [OPTION]... [GUARD]...'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def select(ui, repo, *args, **opts):
    """set or print guarded patches to push

    Use the :hg:`qguard` command to set or print guards on patch, then use
    qselect to tell mq which guards to use. A patch will be pushed if
    it has no guards or any positive guards match the currently
    selected guard, but will not be pushed if any negative guards
    match the current guard. For example::

        qguard foo.patch -- -stable    (negative guard)
        qguard bar.patch    +stable    (positive guard)
        qselect stable

    This activates the "stable" guard. mq will skip foo.patch (because
    it has a negative match) but push bar.patch (because it has a
    positive match).

    With no arguments, prints the currently active guards.
    With one argument, sets the active guard.

    Use -n/--none to deactivate guards (no other arguments needed).
    When no guards are active, patches with positive guards are
    skipped and patches with negative guards are pushed.

    qselect can change the guards on applied patches. It does not pop
    guarded patches by default. Use --pop to pop back to the last
    applied patch that is not guarded. Use --reapply (which implies
    --pop) to push back to the current patch afterwards, but skip
    guarded patches.

    Use -s/--series to print a list of all guards in the series file
    (no other arguments needed). Use -v for more information.

    Returns 0 on success."""

    q = repo.mq
    opts = pycompat.byteskwargs(opts)
    guards = q.active()
    pushable = lambda i: q.pushable(q.applied[i].name)[0]
    if args or opts.get(b'none'):
        old_unapplied = q.unapplied(repo)
        old_guarded = [
            i for i in pycompat.xrange(len(q.applied)) if not pushable(i)
        ]
        q.setactive(args)
        q.savedirty()
        if not args:
            ui.status(_(b'guards deactivated\n'))
        if not opts.get(b'pop') and not opts.get(b'reapply'):
            unapplied = q.unapplied(repo)
            guarded = [
                i for i in pycompat.xrange(len(q.applied)) if not pushable(i)
            ]
            if len(unapplied) != len(old_unapplied):
                ui.status(
                    _(
                        b'number of unguarded, unapplied patches has '
                        b'changed from %d to %d\n'
                    )
                    % (len(old_unapplied), len(unapplied))
                )
            if len(guarded) != len(old_guarded):
                ui.status(
                    _(
                        b'number of guarded, applied patches has changed '
                        b'from %d to %d\n'
                    )
                    % (len(old_guarded), len(guarded))
                )
    elif opts.get(b'series'):
        guards = {}
        noguards = 0
        for gs in q.seriesguards:
            if not gs:
                noguards += 1
            for g in gs:
                guards.setdefault(g, 0)
                guards[g] += 1
        if ui.verbose:
            guards[b'NONE'] = noguards
        guards = list(guards.items())
        guards.sort(key=lambda x: x[0][1:])
        if guards:
            ui.note(_(b'guards in series file:\n'))
            for guard, count in guards:
                ui.note(b'%2d  ' % count)
                ui.write(guard, b'\n')
        else:
            ui.note(_(b'no guards in series file\n'))
    else:
        if guards:
            ui.note(_(b'active guards:\n'))
            for g in guards:
                ui.write(g, b'\n')
        else:
            ui.write(_(b'no active guards\n'))
    reapply = opts.get(b'reapply') and q.applied and q.applied[-1].name
    popped = False
    if opts.get(b'pop') or opts.get(b'reapply'):
        for i in pycompat.xrange(len(q.applied)):
            if not pushable(i):
                ui.status(_(b'popping guarded patches\n'))
                popped = True
                if i == 0:
                    q.pop(repo, all=True)
                else:
                    q.pop(repo, q.applied[i - 1].name)
                break
    if popped:
        try:
            if reapply:
                ui.status(_(b'reapplying unguarded patches\n'))
                q.push(repo, reapply)
        finally:
            q.savedirty()


@command(
    b"qfinish",
    [(b'a', b'applied', None, _(b'finish all applied changesets'))],
    _(b'hg qfinish [-a] [REV]...'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def finish(ui, repo, *revrange, **opts):
    """move applied patches into repository history

    Finishes the specified revisions (corresponding to applied
    patches) by moving them out of mq control into regular repository
    history.

    Accepts a revision range or the -a/--applied option. If --applied
    is specified, all applied mq revisions are removed from mq
    control. Otherwise, the given revisions must be at the base of the
    stack of applied patches.

    This can be especially useful if your changes have been applied to
    an upstream repository, or if you are about to push your changes
    to upstream.

    Returns 0 on success.
    """
    if not opts.get('applied') and not revrange:
        raise error.Abort(_(b'no revisions specified'))
    elif opts.get('applied'):
        revrange = (b'qbase::qtip',) + revrange

    q = repo.mq
    if not q.applied:
        ui.status(_(b'no patches applied\n'))
        return 0

    revs = scmutil.revrange(repo, revrange)
    if repo[b'.'].rev() in revs and repo[None].files():
        ui.warn(_(b'warning: uncommitted changes in the working directory\n'))
    # queue.finish may changes phases but leave the responsibility to lock the
    # repo to the caller to avoid deadlock with wlock. This command code is
    # responsibility for this locking.
    with repo.lock():
        q.finish(repo, revs)
        q.savedirty()
    return 0


@command(
    b"qqueue",
    [
        (b'l', b'list', False, _(b'list all available queues')),
        (b'', b'active', False, _(b'print name of active queue')),
        (b'c', b'create', False, _(b'create new queue')),
        (b'', b'rename', False, _(b'rename active queue')),
        (b'', b'delete', False, _(b'delete reference to queue')),
        (b'', b'purge', False, _(b'delete queue, and remove patch dir')),
    ],
    _(b'[OPTION] [QUEUE]'),
    helpcategory=command.CATEGORY_CHANGE_ORGANIZATION,
)
def qqueue(ui, repo, name=None, **opts):
    """manage multiple patch queues

    Supports switching between different patch queues, as well as creating
    new patch queues and deleting existing ones.

    Omitting a queue name or specifying -l/--list will show you the registered
    queues - by default the "normal" patches queue is registered. The currently
    active queue will be marked with "(active)". Specifying --active will print
    only the name of the active queue.

    To create a new queue, use -c/--create. The queue is automatically made
    active, except in the case where there are applied patches from the
    currently active queue in the repository. Then the queue will only be
    created and switching will fail.

    To delete an existing queue, use --delete. You cannot delete the currently
    active queue.

    Returns 0 on success.
    """
    q = repo.mq
    _defaultqueue = b'patches'
    _allqueues = b'patches.queues'
    _activequeue = b'patches.queue'

    def _getcurrent():
        cur = os.path.basename(q.path)
        if cur.startswith(b'patches-'):
            cur = cur[8:]
        return cur

    def _noqueues():
        try:
            fh = repo.vfs(_allqueues, b'r')
            fh.close()
        except IOError:
            return True

        return False

    def _getqueues():
        current = _getcurrent()

        try:
            fh = repo.vfs(_allqueues, b'r')
            queues = [queue.strip() for queue in fh if queue.strip()]
            fh.close()
            if current not in queues:
                queues.append(current)
        except IOError:
            queues = [_defaultqueue]

        return sorted(queues)

    def _setactive(name):
        if q.applied:
            raise error.Abort(
                _(
                    b'new queue created, but cannot make active '
                    b'as patches are applied'
                )
            )
        _setactivenocheck(name)

    def _setactivenocheck(name):
        fh = repo.vfs(_activequeue, b'w')
        if name != b'patches':
            fh.write(name)
        fh.close()

    def _addqueue(name):
        fh = repo.vfs(_allqueues, b'a')
        fh.write(b'%s\n' % (name,))
        fh.close()

    def _queuedir(name):
        if name == b'patches':
            return repo.vfs.join(b'patches')
        else:
            return repo.vfs.join(b'patches-' + name)

    def _validname(name):
        for n in name:
            if n in b':\\/.':
                return False
        return True

    def _delete(name):
        if name not in existing:
            raise error.Abort(_(b'cannot delete queue that does not exist'))

        current = _getcurrent()

        if name == current:
            raise error.Abort(_(b'cannot delete currently active queue'))

        fh = repo.vfs(b'patches.queues.new', b'w')
        for queue in existing:
            if queue == name:
                continue
            fh.write(b'%s\n' % (queue,))
        fh.close()
        repo.vfs.rename(b'patches.queues.new', _allqueues)

    opts = pycompat.byteskwargs(opts)
    if not name or opts.get(b'list') or opts.get(b'active'):
        current = _getcurrent()
        if opts.get(b'active'):
            ui.write(b'%s\n' % (current,))
            return
        for queue in _getqueues():
            ui.write(b'%s' % (queue,))
            if queue == current and not ui.quiet:
                ui.write(_(b' (active)\n'))
            else:
                ui.write(b'\n')
        return

    if not _validname(name):
        raise error.Abort(
            _(b'invalid queue name, may not contain the characters ":\\/."')
        )

    with repo.wlock():
        existing = _getqueues()

        if opts.get(b'create'):
            if name in existing:
                raise error.Abort(_(b'queue "%s" already exists') % name)
            if _noqueues():
                _addqueue(_defaultqueue)
            _addqueue(name)
            _setactive(name)
        elif opts.get(b'rename'):
            current = _getcurrent()
            if name == current:
                raise error.Abort(
                    _(b'can\'t rename "%s" to its current name') % name
                )
            if name in existing:
                raise error.Abort(_(b'queue "%s" already exists') % name)

            olddir = _queuedir(current)
            newdir = _queuedir(name)

            if os.path.exists(newdir):
                raise error.Abort(
                    _(b'non-queue directory "%s" already exists') % newdir
                )

            fh = repo.vfs(b'patches.queues.new', b'w')
            for queue in existing:
                if queue == current:
                    fh.write(b'%s\n' % (name,))
                    if os.path.exists(olddir):
                        util.rename(olddir, newdir)
                else:
                    fh.write(b'%s\n' % (queue,))
            fh.close()
            repo.vfs.rename(b'patches.queues.new', _allqueues)
            _setactivenocheck(name)
        elif opts.get(b'delete'):
            _delete(name)
        elif opts.get(b'purge'):
            if name in existing:
                _delete(name)
            qdir = _queuedir(name)
            if os.path.exists(qdir):
                shutil.rmtree(qdir)
        else:
            if name not in existing:
                raise error.Abort(_(b'use --create to create a new queue'))
            _setactive(name)


def mqphasedefaults(repo, roots):
    """callback used to set mq changeset as secret when no phase data exists"""
    if repo.mq.applied:
        if repo.ui.configbool(b'mq', b'secret'):
            mqphase = phases.secret
        else:
            mqphase = phases.draft
        qbase = repo[repo.mq.applied[0].node]
        roots[mqphase].add(qbase.node())
    return roots


def reposetup(ui, repo):
    class mqrepo(repo.__class__):
        @localrepo.unfilteredpropertycache
        def mq(self):
            return queue(self.ui, self.baseui, self.path)

        def invalidateall(self):
            super(mqrepo, self).invalidateall()
            if localrepo.hasunfilteredcache(self, 'mq'):
                # recreate mq in case queue path was changed
                delattr(self.unfiltered(), 'mq')

        def abortifwdirpatched(self, errmsg, force=False):
            if self.mq.applied and self.mq.checkapplied and not force:
                parents = self.dirstate.parents()
                patches = [s.node for s in self.mq.applied]
                if any(p in patches for p in parents):
                    raise error.Abort(errmsg)

        def commit(
            self,
            text=b"",
            user=None,
            date=None,
            match=None,
            force=False,
            editor=False,
            extra=None,
        ):
            if extra is None:
                extra = {}
            self.abortifwdirpatched(
                _(b'cannot commit over an applied mq patch'), force
            )

            return super(mqrepo, self).commit(
                text, user, date, match, force, editor, extra
            )

        def checkpush(self, pushop):
            if self.mq.applied and self.mq.checkapplied and not pushop.force:
                outapplied = [e.node for e in self.mq.applied]
                if pushop.revs:
                    # Assume applied patches have no non-patch descendants and
                    # are not on remote already. Filtering any changeset not
                    # pushed.
                    heads = set(pushop.revs)
                    for node in reversed(outapplied):
                        if node in heads:
                            break
                        else:
                            outapplied.pop()
                # looking for pushed and shared changeset
                for node in outapplied:
                    if self[node].phase() < phases.secret:
                        raise error.Abort(_(b'source has mq patches applied'))
                # no non-secret patches pushed
            super(mqrepo, self).checkpush(pushop)

        def _findtags(self):
            '''augment tags from base class with patch tags'''
            result = super(mqrepo, self)._findtags()

            q = self.mq
            if not q.applied:
                return result

            mqtags = [(patch.node, patch.name) for patch in q.applied]

            try:
                # for now ignore filtering business
                self.unfiltered().changelog.rev(mqtags[-1][0])
            except error.LookupError:
                self.ui.warn(
                    _(b'mq status file refers to unknown node %s\n')
                    % short(mqtags[-1][0])
                )
                return result

            # do not add fake tags for filtered revisions
            included = self.changelog.hasnode
            mqtags = [mqt for mqt in mqtags if included(mqt[0])]
            if not mqtags:
                return result

            mqtags.append((mqtags[-1][0], b'qtip'))
            mqtags.append((mqtags[0][0], b'qbase'))
            mqtags.append((self.changelog.parents(mqtags[0][0])[0], b'qparent'))
            tags = result[0]
            for patch in mqtags:
                if patch[1] in tags:
                    self.ui.warn(
                        _(b'tag %s overrides mq patch of the same name\n')
                        % patch[1]
                    )
                else:
                    tags[patch[1]] = patch[0]

            return result

    if repo.local():
        repo.__class__ = mqrepo

        repo._phasedefaults.append(mqphasedefaults)


def mqimport(orig, ui, repo, *args, **kwargs):
    if util.safehasattr(repo, b'abortifwdirpatched') and not kwargs.get(
        'no_commit', False
    ):
        repo.abortifwdirpatched(
            _(b'cannot import over an applied patch'), kwargs.get('force')
        )
    return orig(ui, repo, *args, **kwargs)


def mqinit(orig, ui, *args, **kwargs):
    mq = kwargs.pop('mq', None)

    if not mq:
        return orig(ui, *args, **kwargs)

    if args:
        repopath = args[0]
        if not hg.islocal(repopath):
            raise error.Abort(
                _(b'only a local queue repository may be initialized')
            )
    else:
        repopath = cmdutil.findrepo(encoding.getcwd())
        if not repopath:
            raise error.Abort(
                _(b'there is no Mercurial repository here (.hg not found)')
            )
    repo = hg.repository(ui, repopath)
    return qinit(ui, repo, True)


def mqcommand(orig, ui, repo, *args, **kwargs):
    """Add --mq option to operate on patch repository instead of main"""

    # some commands do not like getting unknown options
    mq = kwargs.pop('mq', None)

    if not mq:
        return orig(ui, repo, *args, **kwargs)

    q = repo.mq
    r = q.qrepo()
    if not r:
        raise error.Abort(_(b'no queue repository'))
    return orig(r.ui, r, *args, **kwargs)


def summaryhook(ui, repo):
    q = repo.mq
    m = []
    a, u = len(q.applied), len(q.unapplied(repo))
    if a:
        m.append(ui.label(_(b"%d applied"), b'qseries.applied') % a)
    if u:
        m.append(ui.label(_(b"%d unapplied"), b'qseries.unapplied') % u)
    if m:
        # i18n: column positioning for "hg summary"
        ui.write(_(b"mq:     %s\n") % b', '.join(m))
    else:
        # i18n: column positioning for "hg summary"
        ui.note(_(b"mq:     (empty queue)\n"))


revsetpredicate = registrar.revsetpredicate()


@revsetpredicate(b'mq()')
def revsetmq(repo, subset, x):
    """Changesets managed by MQ."""
    revsetlang.getargs(x, 0, 0, _(b"mq takes no arguments"))
    applied = {repo[r.node].rev() for r in repo.mq.applied}
    return smartset.baseset([r for r in subset if r in applied])


# tell hggettext to extract docstrings from these functions:
i18nfunctions = [revsetmq]


def extsetup(ui):
    # Ensure mq wrappers are called first, regardless of extension load order by
    # NOT wrapping in uisetup() and instead deferring to init stage two here.
    mqopt = [(b'', b'mq', None, _(b"operate on patch repository"))]

    extensions.wrapcommand(commands.table, b'import', mqimport)
    cmdutil.summaryhooks.add(b'mq', summaryhook)

    entry = extensions.wrapcommand(commands.table, b'init', mqinit)
    entry[1].extend(mqopt)

    def dotable(cmdtable):
        for cmd, entry in pycompat.iteritems(cmdtable):
            cmd = cmdutil.parsealiases(cmd)[0]
            func = entry[0]
            if func.norepo:
                continue
            entry = extensions.wrapcommand(cmdtable, cmd, mqcommand)
            entry[1].extend(mqopt)

    dotable(commands.table)

    thismodule = sys.modules["hgext.mq"]
    for extname, extmodule in extensions.extensions():
        if extmodule != thismodule:
            dotable(getattr(extmodule, 'cmdtable', {}))


colortable = {
    b'qguard.negative': b'red',
    b'qguard.positive': b'yellow',
    b'qguard.unguarded': b'green',
    b'qseries.applied': b'blue bold underline',
    b'qseries.guarded': b'black bold',
    b'qseries.missing': b'red bold',
    b'qseries.unapplied': b'black bold',
}
