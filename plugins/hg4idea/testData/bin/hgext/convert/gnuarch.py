# gnuarch.py - GNU Arch support for the convert extension
#
#  Copyright 2008, 2009 Aleix Conchillo Flaque <aleix@member.fsf.org>
#  and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from common import NoRepo, commandline, commit, converter_source
from mercurial.i18n import _
from mercurial import encoding, util
import os, shutil, tempfile, stat
from email.Parser import Parser

class gnuarch_source(converter_source, commandline):

    class gnuarch_rev(object):
        def __init__(self, rev):
            self.rev = rev
            self.summary = ''
            self.date = None
            self.author = ''
            self.continuationof = None
            self.add_files = []
            self.mod_files = []
            self.del_files = []
            self.ren_files = {}
            self.ren_dirs = {}

    def __init__(self, ui, path, rev=None):
        super(gnuarch_source, self).__init__(ui, path, rev=rev)

        if not os.path.exists(os.path.join(path, '{arch}')):
            raise NoRepo(_("%s does not look like a GNU Arch repository")
                         % path)

        # Could use checktool, but we want to check for baz or tla.
        self.execmd = None
        if util.findexe('baz'):
            self.execmd = 'baz'
        else:
            if util.findexe('tla'):
                self.execmd = 'tla'
            else:
                raise util.Abort(_('cannot find a GNU Arch tool'))

        commandline.__init__(self, ui, self.execmd)

        self.path = os.path.realpath(path)
        self.tmppath = None

        self.treeversion = None
        self.lastrev = None
        self.changes = {}
        self.parents = {}
        self.tags = {}
        self.catlogparser = Parser()
        self.encoding = encoding.encoding
        self.archives = []

    def before(self):
        # Get registered archives
        self.archives = [i.rstrip('\n')
                         for i in self.runlines0('archives', '-n')]

        if self.execmd == 'tla':
            output = self.run0('tree-version', self.path)
        else:
            output = self.run0('tree-version', '-d', self.path)
        self.treeversion = output.strip()

        # Get name of temporary directory
        version = self.treeversion.split('/')
        self.tmppath = os.path.join(tempfile.gettempdir(),
                                    'hg-%s' % version[1])

        # Generate parents dictionary
        self.parents[None] = []
        treeversion = self.treeversion
        child = None
        while treeversion:
            self.ui.status(_('analyzing tree version %s...\n') % treeversion)

            archive = treeversion.split('/')[0]
            if archive not in self.archives:
                self.ui.status(_('tree analysis stopped because it points to '
                                 'an unregistered archive %s...\n') % archive)
                break

            # Get the complete list of revisions for that tree version
            output, status = self.runlines('revisions', '-r', '-f', treeversion)
            self.checkexit(status, 'failed retrieving revisions for %s'
                           % treeversion)

            # No new iteration unless a revision has a continuation-of header
            treeversion = None

            for l in output:
                rev = l.strip()
                self.changes[rev] = self.gnuarch_rev(rev)
                self.parents[rev] = []

                # Read author, date and summary
                catlog, status = self.run('cat-log', '-d', self.path, rev)
                if status:
                    catlog  = self.run0('cat-archive-log', rev)
                self._parsecatlog(catlog, rev)

                # Populate the parents map
                self.parents[child].append(rev)

                # Keep track of the current revision as the child of the next
                # revision scanned
                child = rev

                # Check if we have to follow the usual incremental history
                # or if we have to 'jump' to a different treeversion given
                # by the continuation-of header.
                if self.changes[rev].continuationof:
                    treeversion = '--'.join(
                        self.changes[rev].continuationof.split('--')[:-1])
                    break

                # If we reached a base-0 revision w/o any continuation-of
                # header, it means the tree history ends here.
                if rev[-6:] == 'base-0':
                    break

    def after(self):
        self.ui.debug('cleaning up %s\n' % self.tmppath)
        shutil.rmtree(self.tmppath, ignore_errors=True)

    def getheads(self):
        return self.parents[None]

    def getfile(self, name, rev):
        if rev != self.lastrev:
            raise util.Abort(_('internal calling inconsistency'))

        # Raise IOError if necessary (i.e. deleted files).
        if not os.path.lexists(os.path.join(self.tmppath, name)):
            raise IOError

        return self._getfile(name, rev)

    def getchanges(self, rev):
        self._update(rev)
        changes = []
        copies = {}

        for f in self.changes[rev].add_files:
            changes.append((f, rev))

        for f in self.changes[rev].mod_files:
            changes.append((f, rev))

        for f in self.changes[rev].del_files:
            changes.append((f, rev))

        for src in self.changes[rev].ren_files:
            to = self.changes[rev].ren_files[src]
            changes.append((src, rev))
            changes.append((to, rev))
            copies[to] = src

        for src in self.changes[rev].ren_dirs:
            to = self.changes[rev].ren_dirs[src]
            chgs, cps = self._rendirchanges(src, to)
            changes += [(f, rev) for f in chgs]
            copies.update(cps)

        self.lastrev = rev
        return sorted(set(changes)), copies

    def getcommit(self, rev):
        changes = self.changes[rev]
        return commit(author=changes.author, date=changes.date,
                      desc=changes.summary, parents=self.parents[rev], rev=rev)

    def gettags(self):
        return self.tags

    def _execute(self, cmd, *args, **kwargs):
        cmdline = [self.execmd, cmd]
        cmdline += args
        cmdline = [util.shellquote(arg) for arg in cmdline]
        cmdline += ['>', os.devnull, '2>', os.devnull]
        cmdline = util.quotecommand(' '.join(cmdline))
        self.ui.debug(cmdline, '\n')
        return os.system(cmdline)

    def _update(self, rev):
        self.ui.debug('applying revision %s...\n' % rev)
        changeset, status = self.runlines('replay', '-d', self.tmppath,
                                              rev)
        if status:
            # Something went wrong while merging (baz or tla
            # issue?), get latest revision and try from there
            shutil.rmtree(self.tmppath, ignore_errors=True)
            self._obtainrevision(rev)
        else:
            old_rev = self.parents[rev][0]
            self.ui.debug('computing changeset between %s and %s...\n'
                          % (old_rev, rev))
            self._parsechangeset(changeset, rev)

    def _getfile(self, name, rev):
        mode = os.lstat(os.path.join(self.tmppath, name)).st_mode
        if stat.S_ISLNK(mode):
            data = os.readlink(os.path.join(self.tmppath, name))
            mode = mode and 'l' or ''
        else:
            data = open(os.path.join(self.tmppath, name), 'rb').read()
            mode = (mode & 0111) and 'x' or ''
        return data, mode

    def _exclude(self, name):
        exclude = ['{arch}', '.arch-ids', '.arch-inventory']
        for exc in exclude:
            if name.find(exc) != -1:
                return True
        return False

    def _readcontents(self, path):
        files = []
        contents = os.listdir(path)
        while len(contents) > 0:
            c = contents.pop()
            p = os.path.join(path, c)
            # os.walk could be used, but here we avoid internal GNU
            # Arch files and directories, thus saving a lot time.
            if not self._exclude(p):
                if os.path.isdir(p):
                    contents += [os.path.join(c, f) for f in os.listdir(p)]
                else:
                    files.append(c)
        return files

    def _rendirchanges(self, src, dest):
        changes = []
        copies = {}
        files = self._readcontents(os.path.join(self.tmppath, dest))
        for f in files:
            s = os.path.join(src, f)
            d = os.path.join(dest, f)
            changes.append(s)
            changes.append(d)
            copies[d] = s
        return changes, copies

    def _obtainrevision(self, rev):
        self.ui.debug('obtaining revision %s...\n' % rev)
        output = self._execute('get', rev, self.tmppath)
        self.checkexit(output)
        self.ui.debug('analyzing revision %s...\n' % rev)
        files = self._readcontents(self.tmppath)
        self.changes[rev].add_files += files

    def _stripbasepath(self, path):
        if path.startswith('./'):
            return path[2:]
        return path

    def _parsecatlog(self, data, rev):
        try:
            catlog = self.catlogparser.parsestr(data)

            # Commit date
            self.changes[rev].date = util.datestr(
                util.strdate(catlog['Standard-date'],
                             '%Y-%m-%d %H:%M:%S'))

            # Commit author
            self.changes[rev].author = self.recode(catlog['Creator'])

            # Commit description
            self.changes[rev].summary = '\n\n'.join((catlog['Summary'],
                                                    catlog.get_payload()))
            self.changes[rev].summary = self.recode(self.changes[rev].summary)

            # Commit revision origin when dealing with a branch or tag
            if 'Continuation-of' in catlog:
                self.changes[rev].continuationof = self.recode(
                    catlog['Continuation-of'])
        except Exception:
            raise util.Abort(_('could not parse cat-log of %s') % rev)

    def _parsechangeset(self, data, rev):
        for l in data:
            l = l.strip()
            # Added file (ignore added directory)
            if l.startswith('A') and not l.startswith('A/'):
                file = self._stripbasepath(l[1:].strip())
                if not self._exclude(file):
                    self.changes[rev].add_files.append(file)
            # Deleted file (ignore deleted directory)
            elif l.startswith('D') and not l.startswith('D/'):
                file = self._stripbasepath(l[1:].strip())
                if not self._exclude(file):
                    self.changes[rev].del_files.append(file)
            # Modified binary file
            elif l.startswith('Mb'):
                file = self._stripbasepath(l[2:].strip())
                if not self._exclude(file):
                    self.changes[rev].mod_files.append(file)
            # Modified link
            elif l.startswith('M->'):
                file = self._stripbasepath(l[3:].strip())
                if not self._exclude(file):
                    self.changes[rev].mod_files.append(file)
            # Modified file
            elif l.startswith('M'):
                file = self._stripbasepath(l[1:].strip())
                if not self._exclude(file):
                    self.changes[rev].mod_files.append(file)
            # Renamed file (or link)
            elif l.startswith('=>'):
                files = l[2:].strip().split(' ')
                if len(files) == 1:
                    files = l[2:].strip().split('\t')
                src = self._stripbasepath(files[0])
                dst = self._stripbasepath(files[1])
                if not self._exclude(src) and not self._exclude(dst):
                    self.changes[rev].ren_files[src] = dst
            # Conversion from file to link or from link to file (modified)
            elif l.startswith('ch'):
                file = self._stripbasepath(l[2:].strip())
                if not self._exclude(file):
                    self.changes[rev].mod_files.append(file)
            # Renamed directory
            elif l.startswith('/>'):
                dirs = l[2:].strip().split(' ')
                if len(dirs) == 1:
                    dirs = l[2:].strip().split('\t')
                src = self._stripbasepath(dirs[0])
                dst = self._stripbasepath(dirs[1])
                if not self._exclude(src) and not self._exclude(dst):
                    self.changes[rev].ren_dirs[src] = dst
