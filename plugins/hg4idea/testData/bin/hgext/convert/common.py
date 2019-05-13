# common.py - common code for the convert extension
#
#  Copyright 2005-2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import base64, errno, subprocess, os, datetime
import cPickle as pickle
from mercurial import util
from mercurial.i18n import _

propertycache = util.propertycache

def encodeargs(args):
    def encodearg(s):
        lines = base64.encodestring(s)
        lines = [l.splitlines()[0] for l in lines]
        return ''.join(lines)

    s = pickle.dumps(args)
    return encodearg(s)

def decodeargs(s):
    s = base64.decodestring(s)
    return pickle.loads(s)

class MissingTool(Exception):
    pass

def checktool(exe, name=None, abort=True):
    name = name or exe
    if not util.findexe(exe):
        exc = abort and util.Abort or MissingTool
        raise exc(_('cannot find required "%s" tool') % name)

class NoRepo(Exception):
    pass

SKIPREV = 'SKIP'

class commit(object):
    def __init__(self, author, date, desc, parents, branch=None, rev=None,
                 extra={}, sortkey=None):
        self.author = author or 'unknown'
        self.date = date or '0 0'
        self.desc = desc
        self.parents = parents
        self.branch = branch
        self.rev = rev
        self.extra = extra
        self.sortkey = sortkey

class converter_source(object):
    """Conversion source interface"""

    def __init__(self, ui, path=None, rev=None):
        """Initialize conversion source (or raise NoRepo("message")
        exception if path is not a valid repository)"""
        self.ui = ui
        self.path = path
        self.rev = rev

        self.encoding = 'utf-8'

    def before(self):
        pass

    def after(self):
        pass

    def setrevmap(self, revmap):
        """set the map of already-converted revisions"""
        pass

    def getheads(self):
        """Return a list of this repository's heads"""
        raise NotImplementedError

    def getfile(self, name, rev):
        """Return a pair (data, mode) where data is the file content
        as a string and mode one of '', 'x' or 'l'. rev is the
        identifier returned by a previous call to getchanges(). Raise
        IOError to indicate that name was deleted in rev.
        """
        raise NotImplementedError

    def getchanges(self, version):
        """Returns a tuple of (files, copies).

        files is a sorted list of (filename, id) tuples for all files
        changed between version and its first parent returned by
        getcommit(). id is the source revision id of the file.

        copies is a dictionary of dest: source
        """
        raise NotImplementedError

    def getcommit(self, version):
        """Return the commit object for version"""
        raise NotImplementedError

    def gettags(self):
        """Return the tags as a dictionary of name: revision

        Tag names must be UTF-8 strings.
        """
        raise NotImplementedError

    def recode(self, s, encoding=None):
        if not encoding:
            encoding = self.encoding or 'utf-8'

        if isinstance(s, unicode):
            return s.encode("utf-8")
        try:
            return s.decode(encoding).encode("utf-8")
        except UnicodeError:
            try:
                return s.decode("latin-1").encode("utf-8")
            except UnicodeError:
                return s.decode(encoding, "replace").encode("utf-8")

    def getchangedfiles(self, rev, i):
        """Return the files changed by rev compared to parent[i].

        i is an index selecting one of the parents of rev.  The return
        value should be the list of files that are different in rev and
        this parent.

        If rev has no parents, i is None.

        This function is only needed to support --filemap
        """
        raise NotImplementedError

    def converted(self, rev, sinkrev):
        '''Notify the source that a revision has been converted.'''
        pass

    def hasnativeorder(self):
        """Return true if this source has a meaningful, native revision
        order. For instance, Mercurial revisions are store sequentially
        while there is no such global ordering with Darcs.
        """
        return False

    def hasnativeclose(self):
        """Return true if this source has ability to close branch.
        """
        return False

    def lookuprev(self, rev):
        """If rev is a meaningful revision reference in source, return
        the referenced identifier in the same format used by getcommit().
        return None otherwise.
        """
        return None

    def getbookmarks(self):
        """Return the bookmarks as a dictionary of name: revision

        Bookmark names are to be UTF-8 strings.
        """
        return {}

class converter_sink(object):
    """Conversion sink (target) interface"""

    def __init__(self, ui, path):
        """Initialize conversion sink (or raise NoRepo("message")
        exception if path is not a valid repository)

        created is a list of paths to remove if a fatal error occurs
        later"""
        self.ui = ui
        self.path = path
        self.created = []

    def getheads(self):
        """Return a list of this repository's heads"""
        raise NotImplementedError

    def revmapfile(self):
        """Path to a file that will contain lines
        source_rev_id sink_rev_id
        mapping equivalent revision identifiers for each system."""
        raise NotImplementedError

    def authorfile(self):
        """Path to a file that will contain lines
        srcauthor=dstauthor
        mapping equivalent authors identifiers for each system."""
        return None

    def putcommit(self, files, copies, parents, commit, source, revmap):
        """Create a revision with all changed files listed in 'files'
        and having listed parents. 'commit' is a commit object
        containing at a minimum the author, date, and message for this
        changeset.  'files' is a list of (path, version) tuples,
        'copies' is a dictionary mapping destinations to sources,
        'source' is the source repository, and 'revmap' is a mapfile
        of source revisions to converted revisions. Only getfile() and
        lookuprev() should be called on 'source'.

        Note that the sink repository is not told to update itself to
        a particular revision (or even what that revision would be)
        before it receives the file data.
        """
        raise NotImplementedError

    def puttags(self, tags):
        """Put tags into sink.

        tags: {tagname: sink_rev_id, ...} where tagname is an UTF-8 string.
        Return a pair (tag_revision, tag_parent_revision), or (None, None)
        if nothing was changed.
        """
        raise NotImplementedError

    def setbranch(self, branch, pbranches):
        """Set the current branch name. Called before the first putcommit
        on the branch.
        branch: branch name for subsequent commits
        pbranches: (converted parent revision, parent branch) tuples"""
        pass

    def setfilemapmode(self, active):
        """Tell the destination that we're using a filemap

        Some converter_sources (svn in particular) can claim that a file
        was changed in a revision, even if there was no change.  This method
        tells the destination that we're using a filemap and that it should
        filter empty revisions.
        """
        pass

    def before(self):
        pass

    def after(self):
        pass

    def putbookmarks(self, bookmarks):
        """Put bookmarks into sink.

        bookmarks: {bookmarkname: sink_rev_id, ...}
        where bookmarkname is an UTF-8 string.
        """
        pass

    def hascommit(self, rev):
        """Return True if the sink contains rev"""
        raise NotImplementedError

class commandline(object):
    def __init__(self, ui, command):
        self.ui = ui
        self.command = command

    def prerun(self):
        pass

    def postrun(self):
        pass

    def _cmdline(self, cmd, *args, **kwargs):
        cmdline = [self.command, cmd] + list(args)
        for k, v in kwargs.iteritems():
            if len(k) == 1:
                cmdline.append('-' + k)
            else:
                cmdline.append('--' + k.replace('_', '-'))
            try:
                if len(k) == 1:
                    cmdline.append('' + v)
                else:
                    cmdline[-1] += '=' + v
            except TypeError:
                pass
        cmdline = [util.shellquote(arg) for arg in cmdline]
        if not self.ui.debugflag:
            cmdline += ['2>', os.devnull]
        cmdline = ' '.join(cmdline)
        return cmdline

    def _run(self, cmd, *args, **kwargs):
        def popen(cmdline):
            p = subprocess.Popen(cmdline, shell=True, bufsize=-1,
                    close_fds=util.closefds,
                    stdout=subprocess.PIPE)
            return p
        return self._dorun(popen, cmd, *args, **kwargs)

    def _run2(self, cmd, *args, **kwargs):
        return self._dorun(util.popen2, cmd, *args, **kwargs)

    def _dorun(self, openfunc, cmd,  *args, **kwargs):
        cmdline = self._cmdline(cmd, *args, **kwargs)
        self.ui.debug('running: %s\n' % (cmdline,))
        self.prerun()
        try:
            return openfunc(cmdline)
        finally:
            self.postrun()

    def run(self, cmd, *args, **kwargs):
        p = self._run(cmd, *args, **kwargs)
        output = p.communicate()[0]
        self.ui.debug(output)
        return output, p.returncode

    def runlines(self, cmd, *args, **kwargs):
        p = self._run(cmd, *args, **kwargs)
        output = p.stdout.readlines()
        p.wait()
        self.ui.debug(''.join(output))
        return output, p.returncode

    def checkexit(self, status, output=''):
        if status:
            if output:
                self.ui.warn(_('%s error:\n') % self.command)
                self.ui.warn(output)
            msg = util.explainexit(status)[0]
            raise util.Abort('%s %s' % (self.command, msg))

    def run0(self, cmd, *args, **kwargs):
        output, status = self.run(cmd, *args, **kwargs)
        self.checkexit(status, output)
        return output

    def runlines0(self, cmd, *args, **kwargs):
        output, status = self.runlines(cmd, *args, **kwargs)
        self.checkexit(status, ''.join(output))
        return output

    @propertycache
    def argmax(self):
        # POSIX requires at least 4096 bytes for ARG_MAX
        argmax = 4096
        try:
            argmax = os.sysconf("SC_ARG_MAX")
        except (AttributeError, ValueError):
            pass

        # Windows shells impose their own limits on command line length,
        # down to 2047 bytes for cmd.exe under Windows NT/2k and 2500 bytes
        # for older 4nt.exe. See http://support.microsoft.com/kb/830473 for
        # details about cmd.exe limitations.

        # Since ARG_MAX is for command line _and_ environment, lower our limit
        # (and make happy Windows shells while doing this).
        return argmax // 2 - 1

    def _limit_arglist(self, arglist, cmd, *args, **kwargs):
        cmdlen = len(self._cmdline(cmd, *args, **kwargs))
        limit = self.argmax - cmdlen
        bytes = 0
        fl = []
        for fn in arglist:
            b = len(fn) + 3
            if bytes + b < limit or len(fl) == 0:
                fl.append(fn)
                bytes += b
            else:
                yield fl
                fl = [fn]
                bytes = b
        if fl:
            yield fl

    def xargs(self, arglist, cmd, *args, **kwargs):
        for l in self._limit_arglist(arglist, cmd, *args, **kwargs):
            self.run0(cmd, *(list(args) + l), **kwargs)

class mapfile(dict):
    def __init__(self, ui, path):
        super(mapfile, self).__init__()
        self.ui = ui
        self.path = path
        self.fp = None
        self.order = []
        self._read()

    def _read(self):
        if not self.path:
            return
        try:
            fp = open(self.path, 'r')
        except IOError, err:
            if err.errno != errno.ENOENT:
                raise
            return
        for i, line in enumerate(fp):
            line = line.splitlines()[0].rstrip()
            if not line:
                # Ignore blank lines
                continue
            try:
                key, value = line.rsplit(' ', 1)
            except ValueError:
                raise util.Abort(
                    _('syntax error in %s(%d): key/value pair expected')
                    % (self.path, i + 1))
            if key not in self:
                self.order.append(key)
            super(mapfile, self).__setitem__(key, value)
        fp.close()

    def __setitem__(self, key, value):
        if self.fp is None:
            try:
                self.fp = open(self.path, 'a')
            except IOError, err:
                raise util.Abort(_('could not open map file %r: %s') %
                                 (self.path, err.strerror))
        self.fp.write('%s %s\n' % (key, value))
        self.fp.flush()
        super(mapfile, self).__setitem__(key, value)

    def close(self):
        if self.fp:
            self.fp.close()
            self.fp = None

def parsesplicemap(path):
    """Parse a splicemap, return a child/parents dictionary."""
    if not path:
        return {}
    m = {}
    try:
        fp = open(path, 'r')
        for i, line in enumerate(fp):
            line = line.splitlines()[0].rstrip()
            if not line:
                # Ignore blank lines
                continue
            try:
                child, parents = line.split(' ', 1)
                parents = parents.replace(',', ' ').split()
            except ValueError:
                raise util.Abort(_('syntax error in %s(%d): child parent1'
                                   '[,parent2] expected') % (path, i + 1))
            pp = []
            for p in parents:
                if p not in pp:
                    pp.append(p)
            m[child] = pp
    except IOError, e:
        if e.errno != errno.ENOENT:
            raise
    return m

def makedatetimestamp(t):
    """Like util.makedate() but for time t instead of current time"""
    delta = (datetime.datetime.utcfromtimestamp(t) -
             datetime.datetime.fromtimestamp(t))
    tz = delta.days * 86400 + delta.seconds
    return t, tz
