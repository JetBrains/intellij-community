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

Common tasks (use :hg:`help command` for more details)::

  create new patch                          qnew
  import existing patch                     qimport

  print patch series                        qseries
  print applied patches                     qapplied

  add known patch to applied stack          qpush
  remove patch from applied stack           qpop
  refresh contents of top applied patch     qrefresh

By default, mq will automatically use git patches when required to
avoid losing file mode changes, copy records, binary files or empty
files creations or deletions. This behaviour can be configured with::

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
'''

from mercurial.i18n import _
from mercurial.node import bin, hex, short, nullid, nullrev
from mercurial.lock import release
from mercurial import commands, cmdutil, hg, scmutil, util, revset
from mercurial import repair, extensions, error, phases
from mercurial import patch as patchmod
import os, re, errno, shutil

commands.norepo += " qclone"

seriesopts = [('s', 'summary', None, _('print first line of patch header'))]

cmdtable = {}
command = cmdutil.command(cmdtable)
testedwith = 'internal'

# Patch names looks like unix-file names.
# They must be joinable with queue directory and result in the patch path.
normname = util.normpath

class statusentry(object):
    def __init__(self, node, name):
        self.node, self.name = node, name
    def __repr__(self):
        return hex(self.node) + ':' + self.name

class patchheader(object):
    def __init__(self, pf, plainmode=False):
        def eatdiff(lines):
            while lines:
                l = lines[-1]
                if (l.startswith("diff -") or
                    l.startswith("Index:") or
                    l.startswith("===========")):
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

        for line in file(pf):
            line = line.rstrip()
            if (line.startswith('diff --git')
                or (diffstart and line.startswith('+++ '))):
                diffstart = 2
                break
            diffstart = 0 # reset
            if line.startswith("--- "):
                diffstart = 1
                continue
            elif format == "hgpatch":
                # parse values when importing the result of an hg export
                if line.startswith("# User "):
                    user = line[7:]
                elif line.startswith("# Date "):
                    date = line[7:]
                elif line.startswith("# Parent "):
                    parent = line[9:].lstrip()
                elif line.startswith("# Branch "):
                    branch = line[9:]
                elif line.startswith("# Node ID "):
                    nodeid = line[10:]
                elif not line.startswith("# ") and line:
                    message.append(line)
                    format = None
            elif line == '# HG changeset patch':
                message = []
                format = "hgpatch"
            elif (format != "tagdone" and (line.startswith("Subject: ") or
                                           line.startswith("subject: "))):
                subject = line[9:]
                format = "tag"
            elif (format != "tagdone" and (line.startswith("From: ") or
                                           line.startswith("from: "))):
                user = line[6:]
                format = "tag"
            elif (format != "tagdone" and (line.startswith("Date: ") or
                                           line.startswith("date: "))):
                date = line[6:]
                format = "tag"
            elif format == "tag" and line == "":
                # when looking for tags (subject: from: etc) they
                # end once you find a blank line in the source
                format = "tagdone"
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
        if format and format.startswith("tag") and subject:
            message.insert(0, "")
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
        self.plainmode = plainmode

    def setuser(self, user):
        if not self.updateheader(['From: ', '# User '], user):
            try:
                patchheaderat = self.comments.index('# HG changeset patch')
                self.comments.insert(patchheaderat + 1, '# User ' + user)
            except ValueError:
                if self.plainmode or self._hasheader(['Date: ']):
                    self.comments = ['From: ' + user] + self.comments
                else:
                    tmp = ['# HG changeset patch', '# User ' + user, '']
                    self.comments = tmp + self.comments
        self.user = user

    def setdate(self, date):
        if not self.updateheader(['Date: ', '# Date '], date):
            try:
                patchheaderat = self.comments.index('# HG changeset patch')
                self.comments.insert(patchheaderat + 1, '# Date ' + date)
            except ValueError:
                if self.plainmode or self._hasheader(['From: ']):
                    self.comments = ['Date: ' + date] + self.comments
                else:
                    tmp = ['# HG changeset patch', '# Date ' + date, '']
                    self.comments = tmp + self.comments
        self.date = date

    def setparent(self, parent):
        if not self.updateheader(['# Parent '], parent):
            try:
                patchheaderat = self.comments.index('# HG changeset patch')
                self.comments.insert(patchheaderat + 1, '# Parent ' + parent)
            except ValueError:
                pass
        self.parent = parent

    def setmessage(self, message):
        if self.comments:
            self._delmsg()
        self.message = [message]
        self.comments += self.message

    def updateheader(self, prefixes, new):
        '''Update all references to a field in the patch header.
        Return whether the field is present.'''
        res = False
        for prefix in prefixes:
            for i in xrange(len(self.comments)):
                if self.comments[i].startswith(prefix):
                    self.comments[i] = prefix + new
                    res = True
                    break
        return res

    def _hasheader(self, prefixes):
        '''Check if a header starts with any of the given prefixes.'''
        for prefix in prefixes:
            for comment in self.comments:
                if comment.startswith(prefix):
                    return True
        return False

    def __str__(self):
        if not self.comments:
            return ''
        return '\n'.join(self.comments) + '\n\n'

    def _delmsg(self):
        '''Remove existing message, keeping the rest of the comments fields.
        If comments contains 'subject: ', message will prepend
        the field and a blank line.'''
        if self.message:
            subj = 'subject: ' + self.message[0].lower()
            for i in xrange(len(self.comments)):
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
        if repo.ui.configbool('mq', 'secret', False):
            phase = phases.secret
    if phase is not None:
        backup = repo.ui.backupconfig('phases', 'new-commit')
    # Marking the repository as committing an mq patch can be used
    # to optimize operations like branchtags().
    repo._committingpatch = True
    try:
        if phase is not None:
            repo.ui.setconfig('phases', 'new-commit', phase)
        return repo.commit(*args, **kwargs)
    finally:
        repo._committingpatch = False
        if phase is not None:
            repo.ui.restoreconfig(backup)

class AbortNoCleanup(error.Abort):
    pass

class queue(object):
    def __init__(self, ui, baseui, path, patchdir=None):
        self.basepath = path
        try:
            fh = open(os.path.join(path, 'patches.queue'))
            cur = fh.read().rstrip()
            fh.close()
            if not cur:
                curpath = os.path.join(path, 'patches')
            else:
                curpath = os.path.join(path, 'patches-' + cur)
        except IOError:
            curpath = os.path.join(path, 'patches')
        self.path = patchdir or curpath
        self.opener = scmutil.opener(self.path)
        self.ui = ui
        self.baseui = baseui
        self.applieddirty = False
        self.seriesdirty = False
        self.added = []
        self.seriespath = "series"
        self.statuspath = "status"
        self.guardspath = "guards"
        self.activeguards = None
        self.guardsdirty = False
        # Handle mq.git as a bool with extended values
        try:
            gitmode = ui.configbool('mq', 'git', None)
            if gitmode is None:
                raise error.ConfigError
            self.gitmode = gitmode and 'yes' or 'no'
        except error.ConfigError:
            self.gitmode = ui.config('mq', 'git', 'auto').lower()
        self.plainmode = ui.configbool('mq', 'plain', False)

    @util.propertycache
    def applied(self):
        def parselines(lines):
            for l in lines:
                entry = l.split(':', 1)
                if len(entry) > 1:
                    n, name = entry
                    yield statusentry(bin(n), name)
                elif l.strip():
                    self.ui.warn(_('malformated mq status line: %s\n') % entry)
                # else we ignore empty lines
        try:
            lines = self.opener.read(self.statuspath).splitlines()
            return list(parselines(lines))
        except IOError, e:
            if e.errno == errno.ENOENT:
                return []
            raise

    @util.propertycache
    def fullseries(self):
        try:
            return self.opener.read(self.seriespath).splitlines()
        except IOError, e:
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

    def diffopts(self, opts={}, patchfn=None):
        diffopts = patchmod.diffopts(self.ui, opts)
        if self.gitmode == 'auto':
            diffopts.upgrade = True
        elif self.gitmode == 'keep':
            pass
        elif self.gitmode in ('yes', 'no'):
            diffopts.git = self.gitmode == 'yes'
        else:
            raise util.Abort(_('mq.git option can be auto/keep/yes/no'
                               ' got %s') % self.gitmode)
        if patchfn:
            diffopts = self.patchopts(diffopts, patchfn)
        return diffopts

    def patchopts(self, diffopts, *patches):
        """Return a copy of input diff options with git set to true if
        referenced patch is a git patch and should be preserved as such.
        """
        diffopts = diffopts.copy()
        if not diffopts.git and self.gitmode == 'keep':
            for patchfn in patches:
                patchf = self.opener(patchfn, 'r')
                # if the patch was a git patch, refresh it as a git patch
                for line in patchf:
                    if line.startswith('diff --git'):
                        diffopts.git = True
                        break
                patchf.close()
        return diffopts

    def join(self, *p):
        return os.path.join(self.path, *p)

    def findseries(self, patch):
        def matchpatch(l):
            l = l.split('#', 1)[0]
            return l.strip() == patch
        for index, l in enumerate(self.fullseries):
            if matchpatch(l):
                return index
        return None

    guard_re = re.compile(r'\s?#([-+][^-+# \t\r\n\f][^# \t\r\n\f]*)')

    def parseseries(self):
        self.series = []
        self.seriesguards = []
        for l in self.fullseries:
            h = l.find('#')
            if h == -1:
                patch = l
                comment = ''
            elif h == 0:
                continue
            else:
                patch = l[:h]
                comment = l[h:]
            patch = patch.strip()
            if patch:
                if patch in self.series:
                    raise util.Abort(_('%s appears more than once in %s') %
                                     (patch, self.join(self.seriespath)))
                self.series.append(patch)
                self.seriesguards.append(self.guard_re.findall(comment))

    def checkguard(self, guard):
        if not guard:
            return _('guard cannot be an empty string')
        bad_chars = '# \t\r\n\f'
        first = guard[0]
        if first in '-+':
            return (_('guard %r starts with invalid character: %r') %
                      (guard, first))
        for c in bad_chars:
            if c in guard:
                return _('invalid character in guard %r: %r') % (guard, c)

    def setactive(self, guards):
        for guard in guards:
            bad = self.checkguard(guard)
            if bad:
                raise util.Abort(bad)
        guards = sorted(set(guards))
        self.ui.debug('active guards: %s\n' % ' '.join(guards))
        self.activeguards = guards
        self.guardsdirty = True

    def active(self):
        if self.activeguards is None:
            self.activeguards = []
            try:
                guards = self.opener.read(self.guardspath).split()
            except IOError, err:
                if err.errno != errno.ENOENT:
                    raise
                guards = []
            for i, guard in enumerate(guards):
                bad = self.checkguard(guard)
                if bad:
                    self.ui.warn('%s:%d: %s\n' %
                                 (self.join(self.guardspath), i + 1, bad))
                else:
                    self.activeguards.append(guard)
        return self.activeguards

    def setguards(self, idx, guards):
        for g in guards:
            if len(g) < 2:
                raise util.Abort(_('guard %r too short') % g)
            if g[0] not in '-+':
                raise util.Abort(_('guard %r starts with invalid char') % g)
            bad = self.checkguard(g[1:])
            if bad:
                raise util.Abort(bad)
        drop = self.guard_re.sub('', self.fullseries[idx])
        self.fullseries[idx] = drop + ''.join([' #' + g for g in guards])
        self.parseseries()
        self.seriesdirty = True

    def pushable(self, idx):
        if isinstance(idx, str):
            idx = self.series.index(idx)
        patchguards = self.seriesguards[idx]
        if not patchguards:
            return True, None
        guards = self.active()
        exactneg = [g for g in patchguards if g[0] == '-' and g[1:] in guards]
        if exactneg:
            return False, repr(exactneg[0])
        pos = [g for g in patchguards if g[0] == '+']
        exactpos = [g for g in pos if g[1:] in guards]
        if pos:
            if exactpos:
                return True, repr(exactpos[0])
            return False, ' '.join(map(repr, pos))
        return True, ''

    def explainpushable(self, idx, all_patches=False):
        write = all_patches and self.ui.write or self.ui.warn
        if all_patches or self.ui.verbose:
            if isinstance(idx, str):
                idx = self.series.index(idx)
            pushable, why = self.pushable(idx)
            if all_patches and pushable:
                if why is None:
                    write(_('allowing %s - no guards in effect\n') %
                          self.series[idx])
                else:
                    if not why:
                        write(_('allowing %s - no matching negative guards\n') %
                              self.series[idx])
                    else:
                        write(_('allowing %s - guarded by %s\n') %
                              (self.series[idx], why))
            if not pushable:
                if why:
                    write(_('skipping %s - guarded by %s\n') %
                          (self.series[idx], why))
                else:
                    write(_('skipping %s - no matching guards\n') %
                          self.series[idx])

    def savedirty(self):
        def writelist(items, path):
            fp = self.opener(path, 'w')
            for i in items:
                fp.write("%s\n" % i)
            fp.close()
        if self.applieddirty:
            writelist(map(str, self.applied), self.statuspath)
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
        undo = repo.sjoin('undo')
        if not os.path.exists(undo):
            return
        try:
            os.unlink(undo)
        except OSError, inst:
            self.ui.warn(_('error removing undo: %s\n') % str(inst))

    def backup(self, repo, files, copy=False):
        # backup local changes in --force case
        for f in sorted(files):
            absf = repo.wjoin(f)
            if os.path.lexists(absf):
                self.ui.note(_('saving current version of %s as %s\n') %
                             (f, f + '.orig'))
                if copy:
                    util.copyfile(absf, absf + '.orig')
                else:
                    util.rename(absf, absf + '.orig')

    def printdiff(self, repo, diffopts, node1, node2=None, files=None,
                  fp=None, changes=None, opts={}):
        stat = opts.get('stat')
        m = scmutil.match(repo[node1], files, opts)
        cmdutil.diffordiffstat(self.ui, repo, diffopts, node1, node2,  m,
                               changes, stat, fp)

    def mergeone(self, repo, mergeq, head, patch, rev, diffopts):
        # first try just applying the patch
        (err, n) = self.apply(repo, [patch], update_status=False,
                              strict=True, merge=rev)

        if err == 0:
            return (err, n)

        if n is None:
            raise util.Abort(_("apply failed for patch %s") % patch)

        self.ui.warn(_("patch didn't work out, merging %s\n") % patch)

        # apply failed, strip away that rev and merge.
        hg.clean(repo, head)
        self.strip(repo, [n], update=False, backup='strip')

        ctx = repo[rev]
        ret = hg.merge(repo, rev)
        if ret:
            raise util.Abort(_("update returned %d") % ret)
        n = newcommit(repo, None, ctx.description(), ctx.user(), force=True)
        if n is None:
            raise util.Abort(_("repo commit failed"))
        try:
            ph = patchheader(mergeq.join(patch), self.plainmode)
        except Exception:
            raise util.Abort(_("unable to read %s") % patch)

        diffopts = self.patchopts(diffopts, patch)
        patchf = self.opener(patch, "w")
        comments = str(ph)
        if comments:
            patchf.write(comments)
        self.printdiff(repo, diffopts, head, n, fp=patchf)
        patchf.close()
        self.removeundo(repo)
        return (0, n)

    def qparents(self, repo, rev=None):
        if rev is None:
            (p1, p2) = repo.dirstate.parents()
            if p2 == nullid:
                return p1
            if not self.applied:
                return None
            return self.applied[-1].node
        p1, p2 = repo.changelog.parents(rev)
        if p2 != nullid and p2 in [x.node for x in self.applied]:
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
            pname = ".hg.patches.merge.marker"
            n = newcommit(repo, None, '[mq]: merge marker', force=True)
            self.removeundo(repo)
            self.applied.append(statusentry(n, pname))
            self.applieddirty = True

        head = self.qparents(repo)

        for patch in series:
            patch = mergeq.lookup(patch, strict=True)
            if not patch:
                self.ui.warn(_("patch %s does not exist\n") % patch)
                return (1, None)
            pushable, reason = self.pushable(patch)
            if not pushable:
                self.explainpushable(patch, all_patches=True)
                continue
            info = mergeq.isapplied(patch)
            if not info:
                self.ui.warn(_("patch %s is not applied\n") % patch)
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
        '''Apply patchfile  to the working directory.
        patchfile: name of patch file'''
        files = set()
        try:
            fuzz = patchmod.patch(self.ui, repo, patchfile, strip=1,
                                  files=files, eolmode=None)
            return (True, list(files), fuzz)
        except Exception, inst:
            self.ui.note(str(inst) + '\n')
            if not self.ui.verbose:
                self.ui.warn(_("patch failed, unable to continue (try -v)\n"))
            self.ui.traceback()
            return (False, list(files), False)

    def apply(self, repo, series, list=False, update_status=True,
              strict=False, patchdir=None, merge=None, all_files=None,
              tobackup=None, keepchanges=False):
        wlock = lock = tr = None
        try:
            wlock = repo.wlock()
            lock = repo.lock()
            tr = repo.transaction("qpush")
            try:
                ret = self._apply(repo, series, list, update_status,
                                  strict, patchdir, merge, all_files=all_files,
                                  tobackup=tobackup, keepchanges=keepchanges)
                tr.close()
                self.savedirty()
                return ret
            except AbortNoCleanup:
                tr.close()
                self.savedirty()
                return 2, repo.dirstate.p1()
            except: # re-raises
                try:
                    tr.abort()
                finally:
                    repo.invalidate()
                    repo.dirstate.invalidate()
                    self.invalidate()
                raise
        finally:
            release(tr, lock, wlock)
            self.removeundo(repo)

    def _apply(self, repo, series, list=False, update_status=True,
               strict=False, patchdir=None, merge=None, all_files=None,
               tobackup=None, keepchanges=False):
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
            self.ui.status(_("applying %s\n") % patchname)
            pf = os.path.join(patchdir, patchname)

            try:
                ph = patchheader(self.join(patchname), self.plainmode)
            except IOError:
                self.ui.warn(_("unable to read %s\n") % patchname)
                err = 1
                break

            message = ph.message
            if not message:
                # The commit message should not be translated
                message = "imported patch %s\n" % patchname
            else:
                if list:
                    # The commit message should not be translated
                    message.append("\nimported patch %s" % patchname)
                message = '\n'.join(message)

            if ph.haspatch:
                if tobackup:
                    touched = patchmod.changedfiles(self.ui, repo, pf)
                    touched = set(touched) & tobackup
                    if touched and keepchanges:
                        raise AbortNoCleanup(
                            _("local changes found, refresh first"))
                    self.backup(repo, touched, copy=True)
                    tobackup = tobackup - touched
                (patcherr, files, fuzz) = self.patch(repo, pf)
                if all_files is not None:
                    all_files.update(files)
                patcherr = not patcherr
            else:
                self.ui.warn(_("patch %s is empty\n") % patchname)
                patcherr, files, fuzz = 0, [], 0

            if merge and files:
                # Mark as removed/merged and update dirstate parent info
                removed = []
                merged = []
                for f in files:
                    if os.path.lexists(repo.wjoin(f)):
                        merged.append(f)
                    else:
                        removed.append(f)
                for f in removed:
                    repo.dirstate.remove(f)
                for f in merged:
                    repo.dirstate.merge(f)
                p1, p2 = repo.dirstate.parents()
                repo.setparents(p1, merge)

            match = scmutil.matchfiles(repo, files or [])
            oldtip = repo['tip']
            n = newcommit(repo, None, message, ph.user, ph.date, match=match,
                          force=True)
            if repo['tip'] == oldtip:
                raise util.Abort(_("qpush exactly duplicates child changeset"))
            if n is None:
                raise util.Abort(_("repository commit failed"))

            if update_status:
                self.applied.append(statusentry(n, patchname))

            if patcherr:
                self.ui.warn(_("patch failed, rejects left in working dir\n"))
                err = 2
                break

            if fuzz and strict:
                self.ui.warn(_("fuzz found when applying patch, stopping\n"))
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
                except OSError, inst:
                    if inst.errno != errno.ENOENT:
                        raise

        qfinished = []
        if numrevs:
            qfinished = self.applied[:numrevs]
            del self.applied[:numrevs]
            self.applieddirty = True

        unknown = []

        for (i, p) in sorted([(self.findseries(p), p) for p in patches],
                             reverse=True):
            if i is not None:
                del self.fullseries[i]
            else:
                unknown.append(p)

        if unknown:
            if numrevs:
                rev  = dict((entry.name, entry.node) for entry in qfinished)
                for p in unknown:
                    msg = _('revision %s refers to unknown patches: %s\n')
                    self.ui.warn(msg % (short(rev[p]), p))
            else:
                msg = _('unknown patches: %s\n')
                raise util.Abort(''.join(msg % p for p in unknown))

        self.parseseries()
        self.seriesdirty = True
        return [entry.node for entry in qfinished]

    def _revpatches(self, repo, revs):
        firstrev = repo[self.applied[0].node].rev()
        patches = []
        for i, rev in enumerate(revs):

            if rev < firstrev:
                raise util.Abort(_('revision %d is not managed') % rev)

            ctx = repo[rev]
            base = self.applied[i].node
            if ctx.node() != base:
                msg = _('cannot delete revision %d above applied patches')
                raise util.Abort(msg % rev)

            patch = self.applied[i].name
            for fmt in ('[mq]: %s', 'imported patch %s'):
                if ctx.description() == fmt % patch:
                    msg = _('patch %s finalized without changeset message\n')
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
        if qfinished and repo.ui.configbool('mq', 'secret', False):
            # only use this logic when the secret option is added
            oldqbase = repo[qfinished[0]]
            tphase = repo.ui.config('phases', 'new-commit', phases.draft)
            if oldqbase.phase() > tphase and oldqbase.p1().phase() <= tphase:
                phases.advanceboundary(repo, tphase, qfinished)

    def delete(self, repo, patches, opts):
        if not patches and not opts.get('rev'):
            raise util.Abort(_('qdelete requires at least one revision or '
                               'patch name'))

        realpatches = []
        for patch in patches:
            patch = self.lookup(patch, strict=True)
            info = self.isapplied(patch)
            if info:
                raise util.Abort(_("cannot delete applied patch %s") % patch)
            if patch not in self.series:
                raise util.Abort(_("patch %s not in series file") % patch)
            if patch not in realpatches:
                realpatches.append(patch)

        numrevs = 0
        if opts.get('rev'):
            if not self.applied:
                raise util.Abort(_('no patches applied'))
            revs = scmutil.revrange(repo, opts.get('rev'))
            if len(revs) > 1 and revs[0] > revs[1]:
                revs.reverse()
            revpatches = self._revpatches(repo, revs)
            realpatches += revpatches
            numrevs = len(revpatches)

        self._cleanup(realpatches, numrevs, opts.get('keep'))

    def checktoppatch(self, repo):
        '''check that working directory is at qtip'''
        if self.applied:
            top = self.applied[-1].node
            patch = self.applied[-1].name
            if repo.dirstate.p1() != top:
                raise util.Abort(_("working directory revision is not qtip"))
            return top, patch
        return None, None

    def checksubstate(self, repo, baserev=None):
        '''return list of subrepos at a different revision than substate.
        Abort if any subrepos have uncommitted changes.'''
        inclsubs = []
        wctx = repo[None]
        if baserev:
            bctx = repo[baserev]
        else:
            bctx = wctx.parents()[0]
        for s in sorted(wctx.substate):
            if wctx.sub(s).dirty(True):
                raise util.Abort(
                    _("uncommitted changes in subrepository %s") % s)
            elif s not in bctx.substate or bctx.sub(s).dirty():
                inclsubs.append(s)
        return inclsubs

    def putsubstate2changes(self, substatestate, changes):
        for files in changes[:3]:
            if '.hgsubstate' in files:
                return # already listed up
        # not yet listed up
        if substatestate in 'a?':
            changes[1].append('.hgsubstate')
        elif substatestate in 'r':
            changes[2].append('.hgsubstate')
        else: # modified
            changes[0].append('.hgsubstate')

    def localchangesfound(self, refresh=True):
        if refresh:
            raise util.Abort(_("local changes found, refresh first"))
        else:
            raise util.Abort(_("local changes found"))

    def checklocalchanges(self, repo, force=False, refresh=True):
        m, a, r, d = repo.status()[:4]
        if (m or a or r or d) and not force:
            self.localchangesfound(refresh)
        return m, a, r, d

    _reserved = ('series', 'status', 'guards', '.', '..')
    def checkreservedname(self, name):
        if name in self._reserved:
            raise util.Abort(_('"%s" cannot be used as the name of a patch')
                             % name)
        for prefix in ('.hg', '.mq'):
            if name.startswith(prefix):
                raise util.Abort(_('patch name cannot begin with "%s"')
                                 % prefix)
        for c in ('#', ':'):
            if c in name:
                raise util.Abort(_('"%s" cannot be used in the name of a patch')
                                 % c)

    def checkpatchname(self, name, force=False):
        self.checkreservedname(name)
        if not force and os.path.exists(self.join(name)):
            if os.path.isdir(self.join(name)):
                raise util.Abort(_('"%s" already exists as a directory')
                                 % name)
            else:
                raise util.Abort(_('patch "%s" already exists') % name)

    def checkkeepchanges(self, keepchanges, force):
        if force and keepchanges:
            raise util.Abort(_('cannot use both --force and --keep-changes'))

    def new(self, repo, patchfn, *pats, **opts):
        """options:
           msg: a string or a no-argument function returning a string
        """
        msg = opts.get('msg')
        user = opts.get('user')
        date = opts.get('date')
        if date:
            date = util.parsedate(date)
        diffopts = self.diffopts({'git': opts.get('git')})
        if opts.get('checkname', True):
            self.checkpatchname(patchfn)
        inclsubs = self.checksubstate(repo)
        if inclsubs:
            inclsubs.append('.hgsubstate')
            substatestate = repo.dirstate['.hgsubstate']
        if opts.get('include') or opts.get('exclude') or pats:
            if inclsubs:
                pats = list(pats or []) + inclsubs
            match = scmutil.match(repo[None], pats, opts)
            # detect missing files in pats
            def badfn(f, msg):
                if f != '.hgsubstate': # .hgsubstate is auto-created
                    raise util.Abort('%s: %s' % (f, msg))
            match.bad = badfn
            changes = repo.status(match=match)
            m, a, r, d = changes[:4]
        else:
            changes = self.checklocalchanges(repo, force=True)
            m, a, r, d = changes
        match = scmutil.matchfiles(repo, m + a + r + inclsubs)
        if len(repo[None].parents()) > 1:
            raise util.Abort(_('cannot manage merge changesets'))
        commitfiles = m + a + r
        self.checktoppatch(repo)
        insert = self.fullseriesend()
        wlock = repo.wlock()
        try:
            try:
                # if patch file write fails, abort early
                p = self.opener(patchfn, "w")
            except IOError, e:
                raise util.Abort(_('cannot write patch "%s": %s')
                                 % (patchfn, e.strerror))
            try:
                if self.plainmode:
                    if user:
                        p.write("From: " + user + "\n")
                        if not date:
                            p.write("\n")
                    if date:
                        p.write("Date: %d %d\n\n" % date)
                else:
                    p.write("# HG changeset patch\n")
                    p.write("# Parent "
                            + hex(repo[None].p1().node()) + "\n")
                    if user:
                        p.write("# User " + user + "\n")
                    if date:
                        p.write("# Date %s %s\n\n" % date)
                if util.safehasattr(msg, '__call__'):
                    msg = msg()
                commitmsg = msg and msg or ("[mq]: %s" % patchfn)
                n = newcommit(repo, None, commitmsg, user, date, match=match,
                              force=True)
                if n is None:
                    raise util.Abort(_("repo commit failed"))
                try:
                    self.fullseries[insert:insert] = [patchfn]
                    self.applied.append(statusentry(n, patchfn))
                    self.parseseries()
                    self.seriesdirty = True
                    self.applieddirty = True
                    if msg:
                        msg = msg + "\n\n"
                        p.write(msg)
                    if commitfiles:
                        parent = self.qparents(repo, n)
                        if inclsubs:
                            self.putsubstate2changes(substatestate, changes)
                        chunks = patchmod.diff(repo, node1=parent, node2=n,
                                               changes=changes, opts=diffopts)
                        for chunk in chunks:
                            p.write(chunk)
                    p.close()
                    r = self.qrepo()
                    if r:
                        r[None].add([patchfn])
                except: # re-raises
                    repo.rollback()
                    raise
            except Exception:
                patchpath = self.join(patchfn)
                try:
                    os.unlink(patchpath)
                except OSError:
                    self.ui.warn(_('error unlinking %s\n') % patchpath)
                raise
            self.removeundo(repo)
        finally:
            release(wlock)

    def strip(self, repo, revs, update=True, backup="all", force=None):
        wlock = lock = None
        try:
            wlock = repo.wlock()
            lock = repo.lock()

            if update:
                self.checklocalchanges(repo, force=force, refresh=False)
                urev = self.qparents(repo, revs[0])
                hg.clean(repo, urev)
                repo.dirstate.write()

            repair.strip(self.ui, repo, revs, backup)
        finally:
            release(lock, wlock)

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
                self.ui.warn(_('patch name "%s" is ambiguous:\n') % s)
                for m in matches:
                    self.ui.warn('  %s\n' % m)
                return None
            if matches:
                return matches[0]
            if self.series and self.applied:
                if s == 'qtip':
                    return self.series[self.seriesend(True) - 1]
                if s == 'qbase':
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
                minus = patch.rfind('-')
                if minus >= 0:
                    res = partialname(patch[:minus])
                    if res:
                        i = self.series.index(res)
                        try:
                            off = int(patch[minus + 1:] or 1)
                        except (ValueError, OverflowError):
                            pass
                        else:
                            if i - off >= 0:
                                return self.series[i - off]
                plus = patch.rfind('+')
                if plus >= 0:
                    res = partialname(patch[:plus])
                    if res:
                        i = self.series.index(res)
                        try:
                            off = int(patch[plus + 1:] or 1)
                        except (ValueError, OverflowError):
                            pass
                        else:
                            if i + off < len(self.series):
                                return self.series[i + off]
        raise util.Abort(_("patch %s not in series") % patch)

    def push(self, repo, patch=None, force=False, list=False, mergeq=None,
             all=False, move=False, exact=False, nobackup=False,
             keepchanges=False):
        self.checkkeepchanges(keepchanges, force)
        diffopts = self.diffopts()
        wlock = repo.wlock()
        try:
            heads = []
            for b, ls in repo.branchmap().iteritems():
                heads += ls
            if not heads:
                heads = [nullid]
            if repo.dirstate.p1() not in heads and not exact:
                self.ui.status(_("(working directory not at a head)\n"))

            if not self.series:
                self.ui.warn(_('no patches in series\n'))
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
                        _('qpush: %s is already at the top\n') % patch)
                    return 0

                pushable, reason = self.pushable(patch)
                if pushable:
                    if self.series.index(patch) < self.seriesend():
                        raise util.Abort(
                            _("cannot push to a previous patch: %s") % patch)
                else:
                    if reason:
                        reason = _('guarded by %s') % reason
                    else:
                        reason = _('no matching guards')
                    self.ui.warn(_("cannot push '%s' - %s\n") % (patch, reason))
                    return 1
            elif all:
                patch = self.series[-1]
                if self.isapplied(patch):
                    self.ui.warn(_('all patches are currently applied\n'))
                    return 0

            # Following the above example, starting at 'top' of B:
            # qpush should be performed (pushes C), but a subsequent
            # qpush without an argument is an error (nothing to
            # apply). This allows a loop of "...while hg qpush..." to
            # work as it detects an error when done
            start = self.seriesend()
            if start == len(self.series):
                self.ui.warn(_('patch series already fully applied\n'))
                return 1
            if not force and not keepchanges:
                self.checklocalchanges(repo, refresh=self.applied)

            if exact:
                if keepchanges:
                    raise util.Abort(
                        _("cannot use --exact and --keep-changes together"))
                if move:
                    raise util.Abort(_('cannot use --exact and --move '
                                       'together'))
                if self.applied:
                    raise util.Abort(_('cannot push --exact with applied '
                                       'patches'))
                root = self.series[start]
                target = patchheader(self.join(root), self.plainmode).parent
                if not target:
                    raise util.Abort(
                        _("%s does not have a parent recorded") % root)
                if not repo[target] == repo['.']:
                    hg.update(repo, target)

            if move:
                if not patch:
                    raise util.Abort(_("please specify the patch to move"))
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
                m, a, r, d = self.checklocalchanges(repo, force=True)
                if keepchanges:
                    tobackup.update(m + a + r + d)
                else:
                    tobackup.update(m + a)

            s = self.series[start:end]
            all_files = set()
            try:
                if mergeq:
                    ret = self.mergepatch(repo, mergeq, s, diffopts)
                else:
                    ret = self.apply(repo, s, list, all_files=all_files,
                                     tobackup=tobackup, keepchanges=keepchanges)
            except: # re-raises
                self.ui.warn(_('cleaning up working directory...'))
                node = repo.dirstate.p1()
                hg.revert(repo, node, None)
                # only remove unknown files that we know we touched or
                # created while patching
                for f in all_files:
                    if f not in repo.dirstate:
                        util.unlinkpath(repo.wjoin(f), ignoremissing=True)
                self.ui.warn(_('done\n'))
                raise

            if not self.applied:
                return ret[0]
            top = self.applied[-1].name
            if ret[0] and ret[0] > 1:
                msg = _("errors during apply, please fix and refresh %s\n")
                self.ui.write(msg % top)
            else:
                self.ui.write(_("now at: %s\n") % top)
            return ret[0]

        finally:
            wlock.release()

    def pop(self, repo, patch=None, force=False, update=True, all=False,
            nobackup=False, keepchanges=False):
        self.checkkeepchanges(keepchanges, force)
        wlock = repo.wlock()
        try:
            if patch:
                # index, rev, patch
                info = self.isapplied(patch)
                if not info:
                    patch = self.lookup(patch)
                info = self.isapplied(patch)
                if not info:
                    raise util.Abort(_("patch %s is not applied") % patch)

            if not self.applied:
                # Allow qpop -a to work repeatedly,
                # but not qpop without an argument
                self.ui.warn(_("no patches applied\n"))
                return not all

            if all:
                start = 0
            elif patch:
                start = info[0] + 1
            else:
                start = len(self.applied) - 1

            if start >= len(self.applied):
                self.ui.warn(_("qpop: %s is already at the top\n") % patch)
                return

            if not update:
                parents = repo.dirstate.parents()
                rr = [x.node for x in self.applied]
                for p in parents:
                    if p in rr:
                        self.ui.warn(_("qpop: forcing dirstate update\n"))
                        update = True
            else:
                parents = [p.node() for p in repo[None].parents()]
                needupdate = False
                for entry in self.applied[start:]:
                    if entry.node in parents:
                        needupdate = True
                        break
                update = needupdate

            tobackup = set()
            if update:
                m, a, r, d = self.checklocalchanges(
                    repo, force=force or keepchanges)
                if force:
                    if not nobackup:
                        tobackup.update(m + a)
                elif keepchanges:
                    tobackup.update(m + a + r + d)

            self.applieddirty = True
            end = len(self.applied)
            rev = self.applied[start].node

            try:
                heads = repo.changelog.heads(rev)
            except error.LookupError:
                node = short(rev)
                raise util.Abort(_('trying to pop unknown node %s') % node)

            if heads != [self.applied[-1].node]:
                raise util.Abort(_("popping would remove a revision not "
                                   "managed by this patch queue"))
            if not repo[self.applied[-1].node].mutable():
                raise util.Abort(
                    _("popping would remove an immutable revision"),
                    hint=_('see "hg help phases" for details'))

            # we know there are no local changes, so we can make a simplified
            # form of hg.update.
            if update:
                qp = self.qparents(repo, rev)
                ctx = repo[qp]
                m, a, r, d = repo.status(qp, '.')[:4]
                if d:
                    raise util.Abort(_("deletions found between repo revs"))

                tobackup = set(a + m + r) & tobackup
                if keepchanges and tobackup:
                    self.localchangesfound()
                self.backup(repo, tobackup)

                for f in a:
                    util.unlinkpath(repo.wjoin(f), ignoremissing=True)
                    repo.dirstate.drop(f)
                for f in m + r:
                    fctx = ctx[f]
                    repo.wwrite(f, fctx.data(), fctx.flags())
                    repo.dirstate.normal(f)
                repo.setparents(qp, nullid)
            for patch in reversed(self.applied[start:end]):
                self.ui.status(_("popping %s\n") % patch.name)
            del self.applied[start:end]
            self.strip(repo, [rev], update=False, backup='strip')
            if self.applied:
                self.ui.write(_("now at: %s\n") % self.applied[-1].name)
            else:
                self.ui.write(_("patch queue now empty\n"))
        finally:
            wlock.release()

    def diff(self, repo, pats, opts):
        top, patch = self.checktoppatch(repo)
        if not top:
            self.ui.write(_("no patches applied\n"))
            return
        qp = self.qparents(repo, top)
        if opts.get('reverse'):
            node1, node2 = None, qp
        else:
            node1, node2 = qp, None
        diffopts = self.diffopts(opts, patch)
        self.printdiff(repo, diffopts, node1, node2, files=pats, opts=opts)

    def refresh(self, repo, pats=None, **opts):
        if not self.applied:
            self.ui.write(_("no patches applied\n"))
            return 1
        msg = opts.get('msg', '').rstrip()
        newuser = opts.get('user')
        newdate = opts.get('date')
        if newdate:
            newdate = '%d %d' % util.parsedate(newdate)
        wlock = repo.wlock()

        try:
            self.checktoppatch(repo)
            (top, patchfn) = (self.applied[-1].node, self.applied[-1].name)
            if repo.changelog.heads(top) != [top]:
                raise util.Abort(_("cannot refresh a revision with children"))
            if not repo[top].mutable():
                raise util.Abort(_("cannot refresh immutable revision"),
                                 hint=_('see "hg help phases" for details'))

            cparents = repo.changelog.parents(top)
            patchparent = self.qparents(repo, top)

            inclsubs = self.checksubstate(repo, hex(patchparent))
            if inclsubs:
                inclsubs.append('.hgsubstate')
                substatestate = repo.dirstate['.hgsubstate']

            ph = patchheader(self.join(patchfn), self.plainmode)
            diffopts = self.diffopts({'git': opts.get('git')}, patchfn)
            if msg:
                ph.setmessage(msg)
            if newuser:
                ph.setuser(newuser)
            if newdate:
                ph.setdate(newdate)
            ph.setparent(hex(patchparent))

            # only commit new patch when write is complete
            patchf = self.opener(patchfn, 'w', atomictemp=True)

            comments = str(ph)
            if comments:
                patchf.write(comments)

            # update the dirstate in place, strip off the qtip commit
            # and then commit.
            #
            # this should really read:
            #   mm, dd, aa = repo.status(top, patchparent)[:3]
            # but we do it backwards to take advantage of manifest/changelog
            # caching against the next repo.status call
            mm, aa, dd = repo.status(patchparent, top)[:3]
            changes = repo.changelog.read(top)
            man = repo.manifest.read(changes[0])
            aaa = aa[:]
            matchfn = scmutil.match(repo[None], pats, opts)
            # in short mode, we only diff the files included in the
            # patch already plus specified files
            if opts.get('short'):
                # if amending a patch, we start with existing
                # files plus specified files - unfiltered
                match = scmutil.matchfiles(repo, mm + aa + dd + matchfn.files())
                # filter with include/exclude options
                matchfn = scmutil.match(repo[None], opts=opts)
            else:
                match = scmutil.matchall(repo)
            m, a, r, d = repo.status(match=match)[:4]
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
            # apply matchfn via repo.status to ensure correct case handling.
            cm, ca, cr, cd = repo.status(patchparent, match=matchfn)[:4]
            allmatches = set(cm + ca + cr + cd)
            refreshchanges = [x.intersection(allmatches) for x in (mm, aa, dd)]

            files = set(inclsubs)
            for x in refreshchanges:
                files.update(x)
            match = scmutil.matchfiles(repo, files)

            bmlist = repo[top].bookmarks()

            try:
                if diffopts.git or diffopts.upgrade:
                    copies = {}
                    for dst in a:
                        src = repo.dirstate.copied(dst)
                        # during qfold, the source file for copies may
                        # be removed. Treat this as a simple add.
                        if src is not None and src in repo.dirstate:
                            copies.setdefault(src, []).append(dst)
                        repo.dirstate.add(dst)
                    # remember the copies between patchparent and qtip
                    for dst in aaa:
                        f = repo.file(dst)
                        src = f.renamed(man[dst])
                        if src:
                            copies.setdefault(src[0], []).extend(
                                copies.get(dst, []))
                            if dst in a:
                                copies[src[0]].append(dst)
                        # we can't copy a file created by the patch itself
                        if dst in copies:
                            del copies[dst]
                    for src, dsts in copies.iteritems():
                        for dst in dsts:
                            repo.dirstate.copy(src, dst)
                else:
                    for dst in a:
                        repo.dirstate.add(dst)
                    # Drop useless copy information
                    for f in list(repo.dirstate.copies()):
                        repo.dirstate.copy(None, f)
                for f in r:
                    repo.dirstate.remove(f)
                # if the patch excludes a modified file, mark that
                # file with mtime=0 so status can see it.
                mm = []
                for i in xrange(len(m) - 1, -1, -1):
                    if not matchfn(m[i]):
                        mm.append(m[i])
                        del m[i]
                for f in m:
                    repo.dirstate.normal(f)
                for f in mm:
                    repo.dirstate.normallookup(f)
                for f in forget:
                    repo.dirstate.drop(f)

                if not msg:
                    if not ph.message:
                        message = "[mq]: %s\n" % patchfn
                    else:
                        message = "\n".join(ph.message)
                else:
                    message = msg

                user = ph.user or changes[1]

                oldphase = repo[top].phase()

                # assumes strip can roll itself back if interrupted
                repo.setparents(*cparents)
                self.applied.pop()
                self.applieddirty = True
                self.strip(repo, [top], update=False,
                           backup='strip')
            except: # re-raises
                repo.dirstate.invalidate()
                raise

            try:
                # might be nice to attempt to roll back strip after this

                # Ensure we create a new changeset in the same phase than
                # the old one.
                n = newcommit(repo, oldphase, message, user, ph.date,
                              match=match, force=True)
                # only write patch after a successful commit
                c = [list(x) for x in refreshchanges]
                if inclsubs:
                    self.putsubstate2changes(substatestate, c)
                chunks = patchmod.diff(repo, patchparent,
                                       changes=c, opts=diffopts)
                for chunk in chunks:
                    patchf.write(chunk)
                patchf.close()

                marks = repo._bookmarks
                for bm in bmlist:
                    marks[bm] = n
                marks.write()

                self.applied.append(statusentry(n, patchfn))
            except: # re-raises
                ctx = repo[cparents[0]]
                repo.dirstate.rebuild(ctx.node(), ctx.manifest())
                self.savedirty()
                self.ui.warn(_('refresh interrupted while patch was popped! '
                               '(revert --all, qpush to recover)\n'))
                raise
        finally:
            wlock.release()
            self.removeundo(repo)

    def init(self, repo, create=False):
        if not create and os.path.isdir(self.path):
            raise util.Abort(_("patch queue directory already exists"))
        try:
            os.mkdir(self.path)
        except OSError, inst:
            if inst.errno != errno.EEXIST or not create:
                raise
        if create:
            return self.qrepo(create=True)

    def unapplied(self, repo, patch=None):
        if patch and patch not in self.series:
            raise util.Abort(_("patch %s is not in series file") % patch)
        if not patch:
            start = self.seriesend()
        else:
            start = self.series.index(patch) + 1
        unapplied = []
        for i in xrange(start, len(self.series)):
            pushable, reason = self.pushable(i)
            if pushable:
                unapplied.append((i, self.series[i]))
            self.explainpushable(i)
        return unapplied

    def qseries(self, repo, missing=None, start=0, length=None, status=None,
                summary=False):
        def displayname(pfx, patchname, state):
            if pfx:
                self.ui.write(pfx)
            if summary:
                ph = patchheader(self.join(patchname), self.plainmode)
                msg = ph.message and ph.message[0] or ''
                if self.ui.formatted():
                    width = self.ui.termwidth() - len(pfx) - len(patchname) - 2
                    if width > 0:
                        msg = util.ellipsis(msg, width)
                    else:
                        msg = ''
                self.ui.write(patchname, label='qseries.' + state)
                self.ui.write(': ')
                self.ui.write(msg, label='qseries.message.' + state)
            else:
                self.ui.write(patchname, label='qseries.' + state)
            self.ui.write('\n')

        applied = set([p.name for p in self.applied])
        if length is None:
            length = len(self.series) - start
        if not missing:
            if self.ui.verbose:
                idxwidth = len(str(start + length - 1))
            for i in xrange(start, start + length):
                patch = self.series[i]
                if patch in applied:
                    char, state = 'A', 'applied'
                elif self.pushable(i)[0]:
                    char, state = 'U', 'unapplied'
                else:
                    char, state = 'G', 'guarded'
                pfx = ''
                if self.ui.verbose:
                    pfx = '%*d %s ' % (idxwidth, i, char)
                elif status and status != char:
                    continue
                displayname(pfx, patch, state)
        else:
            msng_list = []
            for root, dirs, files in os.walk(self.path):
                d = root[len(self.path) + 1:]
                for f in files:
                    fl = os.path.join(d, f)
                    if (fl not in self.series and
                        fl not in (self.statuspath, self.seriespath,
                                   self.guardspath)
                        and not fl.startswith('.')):
                        msng_list.append(fl)
            for x in sorted(msng_list):
                pfx = self.ui.verbose and ('D ') or ''
                displayname(pfx, x, 'missing')

    def issaveline(self, l):
        if l.name == '.hg.patches.save.line':
            return True

    def qrepo(self, create=False):
        ui = self.baseui.copy()
        if create or os.path.isdir(self.join(".hg")):
            return hg.repository(ui, path=self.path, create=create)

    def restore(self, repo, rev, delete=None, qupdate=None):
        desc = repo[rev].description().strip()
        lines = desc.splitlines()
        i = 0
        datastart = None
        series = []
        applied = []
        qpp = None
        for i, line in enumerate(lines):
            if line == 'Patch Data:':
                datastart = i + 1
            elif line.startswith('Dirstate:'):
                l = line.rstrip()
                l = l[10:].split(' ')
                qpp = [bin(x) for x in l]
            elif datastart is not None:
                l = line.rstrip()
                n, name = l.split(':', 1)
                if n:
                    applied.append(statusentry(bin(n), name))
                else:
                    series.append(l)
        if datastart is None:
            self.ui.warn(_("no saved patch data found\n"))
            return 1
        self.ui.warn(_("restoring status: %s\n") % lines[0])
        self.fullseries = series
        self.applied = applied
        self.parseseries()
        self.seriesdirty = True
        self.applieddirty = True
        heads = repo.changelog.heads()
        if delete:
            if rev not in heads:
                self.ui.warn(_("save entry has children, leaving it alone\n"))
            else:
                self.ui.warn(_("removing save entry %s\n") % short(rev))
                pp = repo.dirstate.parents()
                if rev in pp:
                    update = True
                else:
                    update = False
                self.strip(repo, [rev], update=update, backup='strip')
        if qpp:
            self.ui.warn(_("saved queue repository parents: %s %s\n") %
                         (short(qpp[0]), short(qpp[1])))
            if qupdate:
                self.ui.status(_("updating queue directory\n"))
                r = self.qrepo()
                if not r:
                    self.ui.warn(_("unable to load queue repository\n"))
                    return 1
                hg.clean(r, qpp[0])

    def save(self, repo, msg=None):
        if not self.applied:
            self.ui.warn(_("save: no patches applied, exiting\n"))
            return 1
        if self.issaveline(self.applied[-1]):
            self.ui.warn(_("status is already saved\n"))
            return 1

        if not msg:
            msg = _("hg patches saved state")
        else:
            msg = "hg patches: " + msg.rstrip('\r\n')
        r = self.qrepo()
        if r:
            pp = r.dirstate.parents()
            msg += "\nDirstate: %s %s" % (hex(pp[0]), hex(pp[1]))
        msg += "\n\nPatch Data:\n"
        msg += ''.join('%s\n' % x for x in self.applied)
        msg += ''.join(':%s\n' % x for x in self.fullseries)
        n = repo.commit(msg, force=True)
        if not n:
            self.ui.warn(_("repo commit failed\n"))
            return 1
        self.applied.append(statusentry(n, '.hg.patches.save.line'))
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
        def next(start):
            if all_patches or start >= len(self.series):
                return start
            for i in xrange(start, len(self.series)):
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
            return next(end + 1)
        return next(end)

    def appliedname(self, index):
        pname = self.applied[index].name
        if not self.ui.verbose:
            p = pname
        else:
            p = str(self.series.index(pname)) + " " + pname
        return p

    def qimport(self, repo, files, patchname=None, rev=None, existing=None,
                force=None, git=False):
        def checkseries(patchname):
            if patchname in self.series:
                raise util.Abort(_('patch %s is already in the series file')
                                 % patchname)

        if rev:
            if files:
                raise util.Abort(_('option "-r" not valid when importing '
                                   'files'))
            rev = scmutil.revrange(repo, rev)
            rev.sort(reverse=True)
        elif not files:
            raise util.Abort(_('no files or revisions specified'))
        if (len(files) > 1 or len(rev) > 1) and patchname:
            raise util.Abort(_('option "-n" not valid when importing multiple '
                               'patches'))
        imported = []
        if rev:
            # If mq patches are applied, we can only import revisions
            # that form a linear path to qbase.
            # Otherwise, they should form a linear path to a head.
            heads = repo.changelog.heads(repo.changelog.node(rev[-1]))
            if len(heads) > 1:
                raise util.Abort(_('revision %d is the root of more than one '
                                   'branch') % rev[-1])
            if self.applied:
                base = repo.changelog.node(rev[0])
                if base in [n.node for n in self.applied]:
                    raise util.Abort(_('revision %d is already managed')
                                     % rev[0])
                if heads != [self.applied[-1].node]:
                    raise util.Abort(_('revision %d is not the parent of '
                                       'the queue') % rev[0])
                base = repo.changelog.rev(self.applied[0].node)
                lastparent = repo.changelog.parentrevs(base)[0]
            else:
                if heads != [repo.changelog.node(rev[0])]:
                    raise util.Abort(_('revision %d has unmanaged children')
                                     % rev[0])
                lastparent = None

            diffopts = self.diffopts({'git': git})
            for r in rev:
                if not repo[r].mutable():
                    raise util.Abort(_('revision %d is not mutable') % r,
                                     hint=_('see "hg help phases" for details'))
                p1, p2 = repo.changelog.parentrevs(r)
                n = repo.changelog.node(r)
                if p2 != nullrev:
                    raise util.Abort(_('cannot import merge revision %d') % r)
                if lastparent and lastparent != r:
                    raise util.Abort(_('revision %d is not the parent of %d')
                                     % (r, lastparent))
                lastparent = p1

                if not patchname:
                    patchname = normname('%d.diff' % r)
                checkseries(patchname)
                self.checkpatchname(patchname, force)
                self.fullseries.insert(0, patchname)

                patchf = self.opener(patchname, "w")
                cmdutil.export(repo, [n], fp=patchf, opts=diffopts)
                patchf.close()

                se = statusentry(n, patchname)
                self.applied.insert(0, se)

                self.added.append(patchname)
                imported.append(patchname)
                patchname = None
            if rev and repo.ui.configbool('mq', 'secret', False):
                # if we added anything with --rev, we must move the secret root
                phases.retractboundary(repo, phases.secret, [n])
            self.parseseries()
            self.applieddirty = True
            self.seriesdirty = True

        for i, filename in enumerate(files):
            if existing:
                if filename == '-':
                    raise util.Abort(_('-e is incompatible with import from -'))
                filename = normname(filename)
                self.checkreservedname(filename)
                originpath = self.join(filename)
                if not os.path.isfile(originpath):
                    raise util.Abort(_("patch %s does not exist") % filename)

                if patchname:
                    self.checkpatchname(patchname, force)

                    self.ui.write(_('renaming %s to %s\n')
                                        % (filename, patchname))
                    util.rename(originpath, self.join(patchname))
                else:
                    patchname = filename

            else:
                if filename == '-' and not patchname:
                    raise util.Abort(_('need --name to import a patch from -'))
                elif not patchname:
                    patchname = normname(os.path.basename(filename.rstrip('/')))
                self.checkpatchname(patchname, force)
                try:
                    if filename == '-':
                        text = self.ui.fin.read()
                    else:
                        fp = hg.openpath(self.ui, filename)
                        text = fp.read()
                        fp.close()
                except (OSError, IOError):
                    raise util.Abort(_("unable to read file %s") % filename)
                patchf = self.opener(patchname, "w")
                patchf.write(text)
                patchf.close()
            if not force:
                checkseries(patchname)
            if patchname not in self.series:
                index = self.fullseriesend() + i
                self.fullseries[index:index] = [patchname]
            self.parseseries()
            self.seriesdirty = True
            self.ui.warn(_("adding %s to series file\n") % patchname)
            self.added.append(patchname)
            imported.append(patchname)
            patchname = None

        self.removeundo(repo)
        return imported

def fixkeepchangesopts(ui, opts):
    if (not ui.configbool('mq', 'keepchanges') or opts.get('force')
        or opts.get('exact')):
        return opts
    opts = dict(opts)
    opts['keep_changes'] = True
    return opts

@command("qdelete|qremove|qrm",
         [('k', 'keep', None, _('keep patch file')),
          ('r', 'rev', [],
           _('stop managing a revision (DEPRECATED)'), _('REV'))],
         _('hg qdelete [-k] [PATCH]...'))
def delete(ui, repo, *patches, **opts):
    """remove patches from queue

    The patches must not be applied, and at least one patch is required. Exact
    patch identifiers must be given. With -k/--keep, the patch files are
    preserved in the patch directory.

    To stop managing a patch and move it into permanent history,
    use the :hg:`qfinish` command."""
    q = repo.mq
    q.delete(repo, patches, opts)
    q.savedirty()
    return 0

@command("qapplied",
         [('1', 'last', None, _('show only the preceding applied patch'))
          ] + seriesopts,
         _('hg qapplied [-1] [-s] [PATCH]'))
def applied(ui, repo, patch=None, **opts):
    """print the patches already applied

    Returns 0 on success."""

    q = repo.mq

    if patch:
        if patch not in q.series:
            raise util.Abort(_("patch %s is not in series file") % patch)
        end = q.series.index(patch) + 1
    else:
        end = q.seriesend(True)

    if opts.get('last') and not end:
        ui.write(_("no patches applied\n"))
        return 1
    elif opts.get('last') and end == 1:
        ui.write(_("only one patch applied\n"))
        return 1
    elif opts.get('last'):
        start = end - 2
        end = 1
    else:
        start = 0

    q.qseries(repo, length=end, start=start, status='A',
              summary=opts.get('summary'))


@command("qunapplied",
         [('1', 'first', None, _('show only the first patch'))] + seriesopts,
         _('hg qunapplied [-1] [-s] [PATCH]'))
def unapplied(ui, repo, patch=None, **opts):
    """print the patches not yet applied

    Returns 0 on success."""

    q = repo.mq
    if patch:
        if patch not in q.series:
            raise util.Abort(_("patch %s is not in series file") % patch)
        start = q.series.index(patch) + 1
    else:
        start = q.seriesend(True)

    if start == len(q.series) and opts.get('first'):
        ui.write(_("all patches applied\n"))
        return 1

    length = opts.get('first') and 1 or None
    q.qseries(repo, start=start, length=length, status='U',
              summary=opts.get('summary'))

@command("qimport",
         [('e', 'existing', None, _('import file in patch directory')),
          ('n', 'name', '',
           _('name of patch file'), _('NAME')),
          ('f', 'force', None, _('overwrite existing files')),
          ('r', 'rev', [],
           _('place existing revisions under mq control'), _('REV')),
          ('g', 'git', None, _('use git extended diff format')),
          ('P', 'push', None, _('qpush after importing'))],
         _('hg qimport [-e] [-n NAME] [-f] [-g] [-P] [-r REV]... [FILE]...'))
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
    (e.g. qimport --rev tip -n patch will place tip under mq control).
    With -g/--git, patches imported with --rev will use the git diff
    format. See the diffs help topic for information on why this is
    important for preserving rename/copy information and permission
    changes. Use :hg:`qfinish` to remove changesets from mq control.

    To import a patch from standard input, pass - as the patch file.
    When importing from standard input, a patch name must be specified
    using the --name flag.

    To import an existing patch while renaming it::

      hg qimport -e existing-patch -n new-name

    Returns 0 if import succeeded.
    """
    lock = repo.lock() # cause this may move phase
    try:
        q = repo.mq
        try:
            imported = q.qimport(
                repo, filename, patchname=opts.get('name'),
                existing=opts.get('existing'), force=opts.get('force'),
                rev=opts.get('rev'), git=opts.get('git'))
        finally:
            q.savedirty()
    finally:
        lock.release()

    if imported and opts.get('push') and not opts.get('rev'):
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
        if not os.path.exists(r.wjoin('.hgignore')):
            fp = r.wopener('.hgignore', 'w')
            fp.write('^\\.hg\n')
            fp.write('^\\.mq\n')
            fp.write('syntax: glob\n')
            fp.write('status\n')
            fp.write('guards\n')
            fp.close()
        if not os.path.exists(r.wjoin('series')):
            r.wopener('series', 'w').close()
        r[None].add(['.hgignore', 'series'])
        commands.add(ui, r)
    return 0

@command("^qinit",
         [('c', 'create-repo', None, _('create queue repository'))],
         _('hg qinit [-c]'))
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

@command("qclone",
         [('', 'pull', None, _('use pull protocol to copy metadata')),
          ('U', 'noupdate', None,
           _('do not update the new working directories')),
          ('', 'uncompressed', None,
           _('use uncompressed transfer (fast over LAN)')),
          ('p', 'patches', '',
           _('location of source patch repository'), _('REPO')),
         ] + commands.remoteopts,
         _('hg qclone [OPTION]... SOURCE [DEST]'))
def clone(ui, source, dest=None, **opts):
    '''clone main and patch repository at same time

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
    '''
    def patchdir(repo):
        """compute a patch repo url from a repo object"""
        url = repo.url()
        if url.endswith('/'):
            url = url[:-1]
        return url + '/.hg/patches'

    # main repo (destination and sources)
    if dest is None:
        dest = hg.defaultdest(source)
    sr = hg.peer(ui, opts, ui.expandpath(source))

    # patches repo (source only)
    if opts.get('patches'):
        patchespath = ui.expandpath(opts.get('patches'))
    else:
        patchespath = patchdir(sr)
    try:
        hg.peer(ui, opts, patchespath)
    except error.RepoError:
        raise util.Abort(_('versioned patch repository not found'
                           ' (see init --mq)'))
    qbase, destrev = None, None
    if sr.local():
        repo = sr.local()
        if repo.mq.applied and repo[qbase].phase() != phases.secret:
            qbase = repo.mq.applied[0].node
            if not hg.islocal(dest):
                heads = set(repo.heads())
                destrev = list(heads.difference(repo.heads(qbase)))
                destrev.append(repo.changelog.parents(qbase)[0])
    elif sr.capable('lookup'):
        try:
            qbase = sr.lookup('qbase')
        except error.RepoError:
            pass

    ui.note(_('cloning main repository\n'))
    sr, dr = hg.clone(ui, opts, sr.url(), dest,
                      pull=opts.get('pull'),
                      rev=destrev,
                      update=False,
                      stream=opts.get('uncompressed'))

    ui.note(_('cloning patch repository\n'))
    hg.clone(ui, opts, opts.get('patches') or patchdir(sr), patchdir(dr),
             pull=opts.get('pull'), update=not opts.get('noupdate'),
             stream=opts.get('uncompressed'))

    if dr.local():
        repo = dr.local()
        if qbase:
            ui.note(_('stripping applied patches from destination '
                      'repository\n'))
            repo.mq.strip(repo, [qbase], update=False, backup=None)
        if not opts.get('noupdate'):
            ui.note(_('updating destination repository\n'))
            hg.update(repo, repo.changelog.tip())

@command("qcommit|qci",
         commands.table["^commit|ci"][1],
         _('hg qcommit [OPTION]... [FILE]...'))
def commit(ui, repo, *pats, **opts):
    """commit changes in the queue repository (DEPRECATED)

    This command is deprecated; use :hg:`commit --mq` instead."""
    q = repo.mq
    r = q.qrepo()
    if not r:
        raise util.Abort('no queue repository')
    commands.commit(r.ui, r, *pats, **opts)

@command("qseries",
         [('m', 'missing', None, _('print patches not in series')),
         ] + seriesopts,
          _('hg qseries [-ms]'))
def series(ui, repo, **opts):
    """print the entire series file

    Returns 0 on success."""
    repo.mq.qseries(repo, missing=opts.get('missing'),
                    summary=opts.get('summary'))
    return 0

@command("qtop", seriesopts, _('hg qtop [-s]'))
def top(ui, repo, **opts):
    """print the name of the current patch

    Returns 0 on success."""
    q = repo.mq
    t = q.applied and q.seriesend(True) or 0
    if t:
        q.qseries(repo, start=t - 1, length=1, status='A',
                  summary=opts.get('summary'))
    else:
        ui.write(_("no patches applied\n"))
        return 1

@command("qnext", seriesopts, _('hg qnext [-s]'))
def next(ui, repo, **opts):
    """print the name of the next pushable patch

    Returns 0 on success."""
    q = repo.mq
    end = q.seriesend()
    if end == len(q.series):
        ui.write(_("all patches applied\n"))
        return 1
    q.qseries(repo, start=end, length=1, summary=opts.get('summary'))

@command("qprev", seriesopts, _('hg qprev [-s]'))
def prev(ui, repo, **opts):
    """print the name of the preceding applied patch

    Returns 0 on success."""
    q = repo.mq
    l = len(q.applied)
    if l == 1:
        ui.write(_("only one patch applied\n"))
        return 1
    if not l:
        ui.write(_("no patches applied\n"))
        return 1
    idx = q.series.index(q.applied[-2].name)
    q.qseries(repo, start=idx, length=1, status='A',
              summary=opts.get('summary'))

def setupheaderopts(ui, opts):
    if not opts.get('user') and opts.get('currentuser'):
        opts['user'] = ui.username()
    if not opts.get('date') and opts.get('currentdate'):
        opts['date'] = "%d %d" % util.makedate()

@command("^qnew",
         [('e', 'edit', None, _('edit commit message')),
          ('f', 'force', None, _('import uncommitted changes (DEPRECATED)')),
          ('g', 'git', None, _('use git extended diff format')),
          ('U', 'currentuser', None, _('add "From: <current user>" to patch')),
          ('u', 'user', '',
           _('add "From: <USER>" to patch'), _('USER')),
          ('D', 'currentdate', None, _('add "Date: <current date>" to patch')),
          ('d', 'date', '',
           _('add "Date: <DATE>" to patch'), _('DATE'))
          ] + commands.walkopts + commands.commitopts,
         _('hg qnew [-e] [-m TEXT] [-l FILE] PATCH [FILE]...'))
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
    msg = cmdutil.logmessage(ui, opts)
    def getmsg():
        return ui.edit(msg, opts.get('user') or ui.username())
    q = repo.mq
    opts['msg'] = msg
    if opts.get('edit'):
        opts['msg'] = getmsg
    else:
        opts['msg'] = msg
    setupheaderopts(ui, opts)
    q.new(repo, patch, *args, **opts)
    q.savedirty()
    return 0

@command("^qrefresh",
         [('e', 'edit', None, _('edit commit message')),
          ('g', 'git', None, _('use git extended diff format')),
          ('s', 'short', None,
           _('refresh only files already in the patch and specified files')),
          ('U', 'currentuser', None,
           _('add/update author field in patch with current user')),
          ('u', 'user', '',
           _('add/update author field in patch with given user'), _('USER')),
          ('D', 'currentdate', None,
           _('add/update date field in patch with current date')),
          ('d', 'date', '',
           _('add/update date field in patch with given date'), _('DATE'))
          ] + commands.walkopts + commands.commitopts,
         _('hg qrefresh [-I] [-X] [-e] [-m TEXT] [-l FILE] [-s] [FILE]...'))
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
    q = repo.mq
    message = cmdutil.logmessage(ui, opts)
    if opts.get('edit'):
        if not q.applied:
            ui.write(_("no patches applied\n"))
            return 1
        if message:
            raise util.Abort(_('option "-e" incompatible with "-m" or "-l"'))
        patch = q.applied[-1].name
        ph = patchheader(q.join(patch), q.plainmode)
        message = ui.edit('\n'.join(ph.message), ph.user or ui.username())
        # We don't want to lose the patch message if qrefresh fails (issue2062)
        repo.savecommitmessage(message)
    setupheaderopts(ui, opts)
    wlock = repo.wlock()
    try:
        ret = q.refresh(repo, pats, msg=message, **opts)
        q.savedirty()
        return ret
    finally:
        wlock.release()

@command("^qdiff",
         commands.diffopts + commands.diffopts2 + commands.walkopts,
         _('hg qdiff [OPTION]... [FILE]...'))
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
    repo.mq.diff(repo, pats, opts)
    return 0

@command('qfold',
         [('e', 'edit', None, _('edit patch header')),
          ('k', 'keep', None, _('keep folded patch files')),
         ] + commands.commitopts,
         _('hg qfold [-e] [-k] [-m TEXT] [-l FILE] PATCH...'))
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
    q = repo.mq
    if not files:
        raise util.Abort(_('qfold requires at least one patch name'))
    if not q.checktoppatch(repo)[0]:
        raise util.Abort(_('no patches applied'))
    q.checklocalchanges(repo)

    message = cmdutil.logmessage(ui, opts)
    if opts.get('edit'):
        if message:
            raise util.Abort(_('option "-e" incompatible with "-m" or "-l"'))

    parent = q.lookup('qtip')
    patches = []
    messages = []
    for f in files:
        p = q.lookup(f)
        if p in patches or p == parent:
            ui.warn(_('skipping already folded patch %s\n') % p)
        if q.isapplied(p):
            raise util.Abort(_('qfold cannot fold already applied patch %s')
                             % p)
        patches.append(p)

    for p in patches:
        if not message:
            ph = patchheader(q.join(p), q.plainmode)
            if ph.message:
                messages.append(ph.message)
        pf = q.join(p)
        (patchsuccess, files, fuzz) = q.patch(repo, pf)
        if not patchsuccess:
            raise util.Abort(_('error folding patch %s') % p)

    if not message:
        ph = patchheader(q.join(parent), q.plainmode)
        message, user = ph.message, ph.user
        for msg in messages:
            message.append('* * *')
            message.extend(msg)
        message = '\n'.join(message)

    if opts.get('edit'):
        message = ui.edit(message, user or ui.username())

    diffopts = q.patchopts(q.diffopts(), *patches)
    wlock = repo.wlock()
    try:
        q.refresh(repo, msg=message, git=diffopts.git)
        q.delete(repo, patches, opts)
        q.savedirty()
    finally:
        wlock.release()

@command("qgoto",
         [('', 'keep-changes', None,
           _('tolerate non-conflicting local changes')),
          ('f', 'force', None, _('overwrite any local changes')),
          ('', 'no-backup', None, _('do not save backup copies of files'))],
         _('hg qgoto [OPTION]... PATCH'))
def goto(ui, repo, patch, **opts):
    '''push or pop patches until named patch is at top of stack

    Returns 0 on success.'''
    opts = fixkeepchangesopts(ui, opts)
    q = repo.mq
    patch = q.lookup(patch)
    nobackup = opts.get('no_backup')
    keepchanges = opts.get('keep_changes')
    if q.isapplied(patch):
        ret = q.pop(repo, patch, force=opts.get('force'), nobackup=nobackup,
                    keepchanges=keepchanges)
    else:
        ret = q.push(repo, patch, force=opts.get('force'), nobackup=nobackup,
                     keepchanges=keepchanges)
    q.savedirty()
    return ret

@command("qguard",
         [('l', 'list', None, _('list all patches and guards')),
          ('n', 'none', None, _('drop all guards'))],
         _('hg qguard [-l] [-n] [PATCH] [-- [+GUARD]... [-GUARD]...]'))
def guard(ui, repo, *args, **opts):
    '''set or print guards for a patch

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
    '''
    def status(idx):
        guards = q.seriesguards[idx] or ['unguarded']
        if q.series[idx] in applied:
            state = 'applied'
        elif q.pushable(idx)[0]:
            state = 'unapplied'
        else:
            state = 'guarded'
        label = 'qguard.patch qguard.%s qseries.%s' % (state, state)
        ui.write('%s: ' % ui.label(q.series[idx], label))

        for i, guard in enumerate(guards):
            if guard.startswith('+'):
                ui.write(guard, label='qguard.positive')
            elif guard.startswith('-'):
                ui.write(guard, label='qguard.negative')
            else:
                ui.write(guard, label='qguard.unguarded')
            if i != len(guards) - 1:
                ui.write(' ')
        ui.write('\n')
    q = repo.mq
    applied = set(p.name for p in q.applied)
    patch = None
    args = list(args)
    if opts.get('list'):
        if args or opts.get('none'):
            raise util.Abort(_('cannot mix -l/--list with options or '
                               'arguments'))
        for i in xrange(len(q.series)):
            status(i)
        return
    if not args or args[0][0:1] in '-+':
        if not q.applied:
            raise util.Abort(_('no patches applied'))
        patch = q.applied[-1].name
    if patch is None and args[0][0:1] not in '-+':
        patch = args.pop(0)
    if patch is None:
        raise util.Abort(_('no patch to work with'))
    if args or opts.get('none'):
        idx = q.findseries(patch)
        if idx is None:
            raise util.Abort(_('no patch named %s') % patch)
        q.setguards(idx, args)
        q.savedirty()
    else:
        status(q.series.index(q.lookup(patch)))

@command("qheader", [], _('hg qheader [PATCH]'))
def header(ui, repo, patch=None):
    """print the header of the topmost or specified patch

    Returns 0 on success."""
    q = repo.mq

    if patch:
        patch = q.lookup(patch)
    else:
        if not q.applied:
            ui.write(_('no patches applied\n'))
            return 1
        patch = q.lookup('qtip')
    ph = patchheader(q.join(patch), q.plainmode)

    ui.write('\n'.join(ph.message) + '\n')

def lastsavename(path):
    (directory, base) = os.path.split(path)
    names = os.listdir(directory)
    namere = re.compile("%s.([0-9]+)" % base)
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
    newpath = path + ".%d" % (index + 1)
    return newpath

@command("^qpush",
         [('', 'keep-changes', None,
           _('tolerate non-conflicting local changes')),
          ('f', 'force', None, _('apply on top of local changes')),
          ('e', 'exact', None,
           _('apply the target patch to its recorded parent')),
          ('l', 'list', None, _('list patch name in commit text')),
          ('a', 'all', None, _('apply all patches')),
          ('m', 'merge', None, _('merge from another queue (DEPRECATED)')),
          ('n', 'name', '',
           _('merge queue name (DEPRECATED)'), _('NAME')),
          ('', 'move', None,
           _('reorder patch series and apply only the patch')),
          ('', 'no-backup', None, _('do not save backup copies of files'))],
         _('hg qpush [-f] [-l] [-a] [--move] [PATCH | INDEX]'))
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

    opts = fixkeepchangesopts(ui, opts)
    if opts.get('merge'):
        if opts.get('name'):
            newpath = repo.join(opts.get('name'))
        else:
            newpath, i = lastsavename(q.path)
        if not newpath:
            ui.warn(_("no saved queues found, please use -n\n"))
            return 1
        mergeq = queue(ui, repo.baseui, repo.path, newpath)
        ui.warn(_("merging with queue at: %s\n") % mergeq.path)
    ret = q.push(repo, patch, force=opts.get('force'), list=opts.get('list'),
                 mergeq=mergeq, all=opts.get('all'), move=opts.get('move'),
                 exact=opts.get('exact'), nobackup=opts.get('no_backup'),
                 keepchanges=opts.get('keep_changes'))
    return ret

@command("^qpop",
         [('a', 'all', None, _('pop all patches')),
          ('n', 'name', '',
           _('queue name to pop (DEPRECATED)'), _('NAME')),
          ('', 'keep-changes', None,
           _('tolerate non-conflicting local changes')),
          ('f', 'force', None, _('forget any local changes to patched files')),
          ('', 'no-backup', None, _('do not save backup copies of files'))],
         _('hg qpop [-a] [-f] [PATCH | INDEX]'))
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
    opts = fixkeepchangesopts(ui, opts)
    localupdate = True
    if opts.get('name'):
        q = queue(ui, repo.baseui, repo.path, repo.join(opts.get('name')))
        ui.warn(_('using patch queue: %s\n') % q.path)
        localupdate = False
    else:
        q = repo.mq
    ret = q.pop(repo, patch, force=opts.get('force'), update=localupdate,
                all=opts.get('all'), nobackup=opts.get('no_backup'),
                keepchanges=opts.get('keep_changes'))
    q.savedirty()
    return ret

@command("qrename|qmv", [], _('hg qrename PATCH1 [PATCH2]'))
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
            ui.write(_('no patches applied\n'))
            return
        patch = q.lookup('qtip')
    absdest = q.join(name)
    if os.path.isdir(absdest):
        name = normname(os.path.join(name, os.path.basename(patch)))
        absdest = q.join(name)
    q.checkpatchname(name)

    ui.note(_('renaming %s to %s\n') % (patch, name))
    i = q.findseries(patch)
    guards = q.guard_re.findall(q.fullseries[i])
    q.fullseries[i] = name + ''.join([' #' + g for g in guards])
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
        wlock = r.wlock()
        try:
            if r.dirstate[patch] == 'a':
                r.dirstate.drop(patch)
                r.dirstate.add(name)
            else:
                wctx.copy(patch, name)
                wctx.forget([patch])
        finally:
            wlock.release()

    q.savedirty()

@command("qrestore",
         [('d', 'delete', None, _('delete save entry')),
          ('u', 'update', None, _('update queue working directory'))],
         _('hg qrestore [-d] [-u] REV'))
def restore(ui, repo, rev, **opts):
    """restore the queue state saved by a revision (DEPRECATED)

    This command is deprecated, use :hg:`rebase` instead."""
    rev = repo.lookup(rev)
    q = repo.mq
    q.restore(repo, rev, delete=opts.get('delete'),
              qupdate=opts.get('update'))
    q.savedirty()
    return 0

@command("qsave",
         [('c', 'copy', None, _('copy patch directory')),
          ('n', 'name', '',
           _('copy directory name'), _('NAME')),
          ('e', 'empty', None, _('clear queue status file')),
          ('f', 'force', None, _('force copy'))] + commands.commitopts,
         _('hg qsave [-m TEXT] [-l FILE] [-c] [-n NAME] [-e] [-f]'))
def save(ui, repo, **opts):
    """save current queue state (DEPRECATED)

    This command is deprecated, use :hg:`rebase` instead."""
    q = repo.mq
    message = cmdutil.logmessage(ui, opts)
    ret = q.save(repo, msg=message)
    if ret:
        return ret
    q.savedirty() # save to .hg/patches before copying
    if opts.get('copy'):
        path = q.path
        if opts.get('name'):
            newpath = os.path.join(q.basepath, opts.get('name'))
            if os.path.exists(newpath):
                if not os.path.isdir(newpath):
                    raise util.Abort(_('destination %s exists and is not '
                                       'a directory') % newpath)
                if not opts.get('force'):
                    raise util.Abort(_('destination %s exists, '
                                       'use -f to force') % newpath)
        else:
            newpath = savename(path)
        ui.warn(_("copy %s to %s\n") % (path, newpath))
        util.copyfiles(path, newpath)
    if opts.get('empty'):
        del q.applied[:]
        q.applieddirty = True
        q.savedirty()
    return 0

@command("strip",
         [
          ('r', 'rev', [], _('strip specified revision (optional, '
                               'can specify revisions without this '
                               'option)'), _('REV')),
          ('f', 'force', None, _('force removal of changesets, discard '
                                 'uncommitted changes (no backup)')),
          ('b', 'backup', None, _('bundle only changesets with local revision'
                                  ' number greater than REV which are not'
                                  ' descendants of REV (DEPRECATED)')),
          ('', 'no-backup', None, _('no backups')),
          ('', 'nobackup', None, _('no backups (DEPRECATED)')),
          ('n', '', None, _('ignored  (DEPRECATED)')),
          ('k', 'keep', None, _("do not modify working copy during strip")),
          ('B', 'bookmark', '', _("remove revs only reachable from given"
                                  " bookmark"))],
          _('hg strip [-k] [-f] [-n] [-B bookmark] [-r] REV...'))
def strip(ui, repo, *revs, **opts):
    """strip changesets and all their descendants from the repository

    The strip command removes the specified changesets and all their
    descendants. If the working directory has uncommitted changes, the
    operation is aborted unless the --force flag is supplied, in which
    case changes will be discarded.

    If a parent of the working directory is stripped, then the working
    directory will automatically be updated to the most recent
    available ancestor of the stripped parent after the operation
    completes.

    Any stripped changesets are stored in ``.hg/strip-backup`` as a
    bundle (see :hg:`help bundle` and :hg:`help unbundle`). They can
    be restored by running :hg:`unbundle .hg/strip-backup/BUNDLE`,
    where BUNDLE is the bundle file created by the strip. Note that
    the local revision numbers will in general be different after the
    restore.

    Use the --no-backup option to discard the backup bundle once the
    operation completes.

    Strip is not a history-rewriting operation and can be used on
    changesets in the public phase. But if the stripped changesets have
    been pushed to a remote repository you will likely pull them again.

    Return 0 on success.
    """
    backup = 'all'
    if opts.get('backup'):
        backup = 'strip'
    elif opts.get('no_backup') or opts.get('nobackup'):
        backup = 'none'

    cl = repo.changelog
    revs = list(revs) + opts.get('rev')
    revs = set(scmutil.revrange(repo, revs))

    if opts.get('bookmark'):
        mark = opts.get('bookmark')
        marks = repo._bookmarks
        if mark not in marks:
            raise util.Abort(_("bookmark '%s' not found") % mark)

        # If the requested bookmark is not the only one pointing to a
        # a revision we have to only delete the bookmark and not strip
        # anything. revsets cannot detect that case.
        uniquebm = True
        for m, n in marks.iteritems():
            if m != mark and n == repo[mark].node():
                uniquebm = False
                break
        if uniquebm:
            rsrevs = repo.revs("ancestors(bookmark(%s)) - "
                               "ancestors(head() and not bookmark(%s)) - "
                               "ancestors(bookmark() and not bookmark(%s))",
                               mark, mark, mark)
            revs.update(set(rsrevs))
        if not revs:
            del marks[mark]
            marks.write()
            ui.write(_("bookmark '%s' deleted\n") % mark)

    if not revs:
        raise util.Abort(_('empty revision set'))

    descendants = set(cl.descendants(revs))
    strippedrevs = revs.union(descendants)
    roots = revs.difference(descendants)

    update = False
    # if one of the wdir parent is stripped we'll need
    # to update away to an earlier revision
    for p in repo.dirstate.parents():
        if p != nullid and cl.rev(p) in strippedrevs:
            update = True
            break

    rootnodes = set(cl.node(r) for r in roots)

    q = repo.mq
    if q.applied:
        # refresh queue state if we're about to strip
        # applied patches
        if cl.rev(repo.lookup('qtip')) in strippedrevs:
            q.applieddirty = True
            start = 0
            end = len(q.applied)
            for i, statusentry in enumerate(q.applied):
                if statusentry.node in rootnodes:
                    # if one of the stripped roots is an applied
                    # patch, only part of the queue is stripped
                    start = i
                    break
            del q.applied[start:end]
            q.savedirty()

    revs = sorted(rootnodes)
    if update and opts.get('keep'):
        wlock = repo.wlock()
        try:
            urev = repo.mq.qparents(repo, revs[0])
            uctx = repo[urev]

            # only reset the dirstate for files that would actually change
            # between the working context and uctx
            descendantrevs = repo.revs("%s::." % uctx.rev())
            changedfiles = []
            for rev in descendantrevs:
                # blindly reset the files, regardless of what actually changed
                changedfiles.extend(repo[rev].files())

            # reset files that only changed in the dirstate too
            dirstate = repo.dirstate
            dirchanges = [f for f in dirstate if dirstate[f] != 'n']
            changedfiles.extend(dirchanges)

            repo.dirstate.rebuild(urev, uctx.manifest(), changedfiles)
            repo.dirstate.write()
            update = False
        finally:
            wlock.release()

    if opts.get('bookmark'):
        del marks[mark]
        marks.write()
        ui.write(_("bookmark '%s' deleted\n") % mark)

    repo.mq.strip(repo, revs, backup=backup, update=update,
                  force=opts.get('force'))

    return 0

@command("qselect",
         [('n', 'none', None, _('disable all guards')),
          ('s', 'series', None, _('list all guards in series file')),
          ('', 'pop', None, _('pop to before first guarded applied patch')),
          ('', 'reapply', None, _('pop, then reapply patches'))],
         _('hg qselect [OPTION]... [GUARD]...'))
def select(ui, repo, *args, **opts):
    '''set or print guarded patches to push

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

    Returns 0 on success.'''

    q = repo.mq
    guards = q.active()
    if args or opts.get('none'):
        old_unapplied = q.unapplied(repo)
        old_guarded = [i for i in xrange(len(q.applied)) if
                       not q.pushable(i)[0]]
        q.setactive(args)
        q.savedirty()
        if not args:
            ui.status(_('guards deactivated\n'))
        if not opts.get('pop') and not opts.get('reapply'):
            unapplied = q.unapplied(repo)
            guarded = [i for i in xrange(len(q.applied))
                       if not q.pushable(i)[0]]
            if len(unapplied) != len(old_unapplied):
                ui.status(_('number of unguarded, unapplied patches has '
                            'changed from %d to %d\n') %
                          (len(old_unapplied), len(unapplied)))
            if len(guarded) != len(old_guarded):
                ui.status(_('number of guarded, applied patches has changed '
                            'from %d to %d\n') %
                          (len(old_guarded), len(guarded)))
    elif opts.get('series'):
        guards = {}
        noguards = 0
        for gs in q.seriesguards:
            if not gs:
                noguards += 1
            for g in gs:
                guards.setdefault(g, 0)
                guards[g] += 1
        if ui.verbose:
            guards['NONE'] = noguards
        guards = guards.items()
        guards.sort(key=lambda x: x[0][1:])
        if guards:
            ui.note(_('guards in series file:\n'))
            for guard, count in guards:
                ui.note('%2d  ' % count)
                ui.write(guard, '\n')
        else:
            ui.note(_('no guards in series file\n'))
    else:
        if guards:
            ui.note(_('active guards:\n'))
            for g in guards:
                ui.write(g, '\n')
        else:
            ui.write(_('no active guards\n'))
    reapply = opts.get('reapply') and q.applied and q.appliedname(-1)
    popped = False
    if opts.get('pop') or opts.get('reapply'):
        for i in xrange(len(q.applied)):
            pushable, reason = q.pushable(i)
            if not pushable:
                ui.status(_('popping guarded patches\n'))
                popped = True
                if i == 0:
                    q.pop(repo, all=True)
                else:
                    q.pop(repo, str(i - 1))
                break
    if popped:
        try:
            if reapply:
                ui.status(_('reapplying unguarded patches\n'))
                q.push(repo, reapply)
        finally:
            q.savedirty()

@command("qfinish",
         [('a', 'applied', None, _('finish all applied changesets'))],
         _('hg qfinish [-a] [REV]...'))
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
        raise util.Abort(_('no revisions specified'))
    elif opts.get('applied'):
        revrange = ('qbase::qtip',) + revrange

    q = repo.mq
    if not q.applied:
        ui.status(_('no patches applied\n'))
        return 0

    revs = scmutil.revrange(repo, revrange)
    if repo['.'].rev() in revs and repo[None].files():
        ui.warn(_('warning: uncommitted changes in the working directory\n'))
    # queue.finish may changes phases but leave the responsibility to lock the
    # repo to the caller to avoid deadlock with wlock. This command code is
    # responsibility for this locking.
    lock = repo.lock()
    try:
        q.finish(repo, revs)
        q.savedirty()
    finally:
        lock.release()
    return 0

@command("qqueue",
         [('l', 'list', False, _('list all available queues')),
          ('', 'active', False, _('print name of active queue')),
          ('c', 'create', False, _('create new queue')),
          ('', 'rename', False, _('rename active queue')),
          ('', 'delete', False, _('delete reference to queue')),
          ('', 'purge', False, _('delete queue, and remove patch dir')),
         ],
         _('[OPTION] [QUEUE]'))
def qqueue(ui, repo, name=None, **opts):
    '''manage multiple patch queues

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
    '''
    q = repo.mq
    _defaultqueue = 'patches'
    _allqueues = 'patches.queues'
    _activequeue = 'patches.queue'

    def _getcurrent():
        cur = os.path.basename(q.path)
        if cur.startswith('patches-'):
            cur = cur[8:]
        return cur

    def _noqueues():
        try:
            fh = repo.opener(_allqueues, 'r')
            fh.close()
        except IOError:
            return True

        return False

    def _getqueues():
        current = _getcurrent()

        try:
            fh = repo.opener(_allqueues, 'r')
            queues = [queue.strip() for queue in fh if queue.strip()]
            fh.close()
            if current not in queues:
                queues.append(current)
        except IOError:
            queues = [_defaultqueue]

        return sorted(queues)

    def _setactive(name):
        if q.applied:
            raise util.Abort(_('new queue created, but cannot make active '
                               'as patches are applied'))
        _setactivenocheck(name)

    def _setactivenocheck(name):
        fh = repo.opener(_activequeue, 'w')
        if name != 'patches':
            fh.write(name)
        fh.close()

    def _addqueue(name):
        fh = repo.opener(_allqueues, 'a')
        fh.write('%s\n' % (name,))
        fh.close()

    def _queuedir(name):
        if name == 'patches':
            return repo.join('patches')
        else:
            return repo.join('patches-' + name)

    def _validname(name):
        for n in name:
            if n in ':\\/.':
                return False
        return True

    def _delete(name):
        if name not in existing:
            raise util.Abort(_('cannot delete queue that does not exist'))

        current = _getcurrent()

        if name == current:
            raise util.Abort(_('cannot delete currently active queue'))

        fh = repo.opener('patches.queues.new', 'w')
        for queue in existing:
            if queue == name:
                continue
            fh.write('%s\n' % (queue,))
        fh.close()
        util.rename(repo.join('patches.queues.new'), repo.join(_allqueues))

    if not name or opts.get('list') or opts.get('active'):
        current = _getcurrent()
        if opts.get('active'):
            ui.write('%s\n' % (current,))
            return
        for queue in _getqueues():
            ui.write('%s' % (queue,))
            if queue == current and not ui.quiet:
                ui.write(_(' (active)\n'))
            else:
                ui.write('\n')
        return

    if not _validname(name):
        raise util.Abort(
                _('invalid queue name, may not contain the characters ":\\/."'))

    existing = _getqueues()

    if opts.get('create'):
        if name in existing:
            raise util.Abort(_('queue "%s" already exists') % name)
        if _noqueues():
            _addqueue(_defaultqueue)
        _addqueue(name)
        _setactive(name)
    elif opts.get('rename'):
        current = _getcurrent()
        if name == current:
            raise util.Abort(_('can\'t rename "%s" to its current name') % name)
        if name in existing:
            raise util.Abort(_('queue "%s" already exists') % name)

        olddir = _queuedir(current)
        newdir = _queuedir(name)

        if os.path.exists(newdir):
            raise util.Abort(_('non-queue directory "%s" already exists') %
                    newdir)

        fh = repo.opener('patches.queues.new', 'w')
        for queue in existing:
            if queue == current:
                fh.write('%s\n' % (name,))
                if os.path.exists(olddir):
                    util.rename(olddir, newdir)
            else:
                fh.write('%s\n' % (queue,))
        fh.close()
        util.rename(repo.join('patches.queues.new'), repo.join(_allqueues))
        _setactivenocheck(name)
    elif opts.get('delete'):
        _delete(name)
    elif opts.get('purge'):
        if name in existing:
            _delete(name)
        qdir = _queuedir(name)
        if os.path.exists(qdir):
            shutil.rmtree(qdir)
    else:
        if name not in existing:
            raise util.Abort(_('use --create to create a new queue'))
        _setactive(name)

def mqphasedefaults(repo, roots):
    """callback used to set mq changeset as secret when no phase data exists"""
    if repo.mq.applied:
        if repo.ui.configbool('mq', 'secret', False):
            mqphase = phases.secret
        else:
            mqphase = phases.draft
        qbase = repo[repo.mq.applied[0].node]
        roots[mqphase].add(qbase.node())
    return roots

def reposetup(ui, repo):
    class mqrepo(repo.__class__):
        @util.propertycache
        def mq(self):
            return queue(self.ui, self.baseui, self.path)

        def abortifwdirpatched(self, errmsg, force=False):
            if self.mq.applied and not force:
                parents = self.dirstate.parents()
                patches = [s.node for s in self.mq.applied]
                if parents[0] in patches or parents[1] in patches:
                    raise util.Abort(errmsg)

        def commit(self, text="", user=None, date=None, match=None,
                   force=False, editor=False, extra={}):
            self.abortifwdirpatched(
                _('cannot commit over an applied mq patch'),
                force)

            return super(mqrepo, self).commit(text, user, date, match, force,
                                              editor, extra)

        def checkpush(self, force, revs):
            if self.mq.applied and not force:
                outapplied = [e.node for e in self.mq.applied]
                if revs:
                    # Assume applied patches have no non-patch descendants and
                    # are not on remote already. Filtering any changeset not
                    # pushed.
                    heads = set(revs)
                    for node in reversed(outapplied):
                        if node in heads:
                            break
                        else:
                            outapplied.pop()
                # looking for pushed and shared changeset
                for node in outapplied:
                    if self[node].phase() < phases.secret:
                        raise util.Abort(_('source has mq patches applied'))
                # no non-secret patches pushed
            super(mqrepo, self).checkpush(force, revs)

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
                self.ui.warn(_('mq status file refers to unknown node %s\n')
                             % short(mqtags[-1][0]))
                return result

            # do not add fake tags for filtered revisions
            included = self.changelog.hasnode
            mqtags = [mqt for mqt in mqtags if included(mqt[0])]
            if not mqtags:
                return result

            mqtags.append((mqtags[-1][0], 'qtip'))
            mqtags.append((mqtags[0][0], 'qbase'))
            mqtags.append((self.changelog.parents(mqtags[0][0])[0], 'qparent'))
            tags = result[0]
            for patch in mqtags:
                if patch[1] in tags:
                    self.ui.warn(_('tag %s overrides mq patch of the same '
                                   'name\n') % patch[1])
                else:
                    tags[patch[1]] = patch[0]

            return result

    if repo.local():
        repo.__class__ = mqrepo

        repo._phasedefaults.append(mqphasedefaults)

def mqimport(orig, ui, repo, *args, **kwargs):
    if (util.safehasattr(repo, 'abortifwdirpatched')
        and not kwargs.get('no_commit', False)):
        repo.abortifwdirpatched(_('cannot import over an applied patch'),
                                   kwargs.get('force'))
    return orig(ui, repo, *args, **kwargs)

def mqinit(orig, ui, *args, **kwargs):
    mq = kwargs.pop('mq', None)

    if not mq:
        return orig(ui, *args, **kwargs)

    if args:
        repopath = args[0]
        if not hg.islocal(repopath):
            raise util.Abort(_('only a local queue repository '
                               'may be initialized'))
    else:
        repopath = cmdutil.findrepo(os.getcwd())
        if not repopath:
            raise util.Abort(_('there is no Mercurial repository here '
                               '(.hg not found)'))
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
        raise util.Abort(_('no queue repository'))
    return orig(r.ui, r, *args, **kwargs)

def summary(orig, ui, repo, *args, **kwargs):
    r = orig(ui, repo, *args, **kwargs)
    q = repo.mq
    m = []
    a, u = len(q.applied), len(q.unapplied(repo))
    if a:
        m.append(ui.label(_("%d applied"), 'qseries.applied') % a)
    if u:
        m.append(ui.label(_("%d unapplied"), 'qseries.unapplied') % u)
    if m:
        # i18n: column positioning for "hg summary"
        ui.write(_("mq:     %s\n") % ', '.join(m))
    else:
        # i18n: column positioning for "hg summary"
        ui.note(_("mq:     (empty queue)\n"))
    return r

def revsetmq(repo, subset, x):
    """``mq()``
    Changesets managed by MQ.
    """
    revset.getargs(x, 0, 0, _("mq takes no arguments"))
    applied = set([repo[r.node].rev() for r in repo.mq.applied])
    return [r for r in subset if r in applied]

# tell hggettext to extract docstrings from these functions:
i18nfunctions = [revsetmq]

def extsetup(ui):
    # Ensure mq wrappers are called first, regardless of extension load order by
    # NOT wrapping in uisetup() and instead deferring to init stage two here.
    mqopt = [('', 'mq', None, _("operate on patch repository"))]

    extensions.wrapcommand(commands.table, 'import', mqimport)
    extensions.wrapcommand(commands.table, 'summary', summary)

    entry = extensions.wrapcommand(commands.table, 'init', mqinit)
    entry[1].extend(mqopt)

    nowrap = set(commands.norepo.split(" "))

    def dotable(cmdtable):
        for cmd in cmdtable.keys():
            cmd = cmdutil.parsealiases(cmd)[0]
            if cmd in nowrap:
                continue
            entry = extensions.wrapcommand(cmdtable, cmd, mqcommand)
            entry[1].extend(mqopt)

    dotable(commands.table)

    for extname, extmodule in extensions.extensions():
        if extmodule.__file__ != __file__:
            dotable(getattr(extmodule, 'cmdtable', {}))

    revset.symbols['mq'] = revsetmq

colortable = {'qguard.negative': 'red',
              'qguard.positive': 'yellow',
              'qguard.unguarded': 'green',
              'qseries.applied': 'blue bold underline',
              'qseries.guarded': 'black bold',
              'qseries.missing': 'red bold',
              'qseries.unapplied': 'black bold'}

commands.inferrepo += " qnew qrefresh qdiff qcommit"
