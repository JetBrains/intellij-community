# server.py - common entry point for inotify status server
#
# Copyright 2009 Nicolas Dumazet <nicdumz@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from mercurial.i18n import _
from mercurial import cmdutil, posix, osutil, util
import common

import errno
import os
import socket
import stat
import struct
import sys

class AlreadyStartedException(Exception):
    pass
class TimeoutException(Exception):
    pass

def join(a, b):
    if a:
        if a[-1] == '/':
            return a + b
        return a + '/' + b
    return b

def split(path):
    c = path.rfind('/')
    if c == -1:
        return '', path
    return path[:c], path[c + 1:]

walk_ignored_errors = (errno.ENOENT, errno.ENAMETOOLONG)

def walk(dirstate, absroot, root):
    '''Like os.walk, but only yields regular files.'''

    # This function is critical to performance during startup.

    def walkit(root, reporoot):
        files, dirs = [], []

        try:
            fullpath = join(absroot, root)
            for name, kind in osutil.listdir(fullpath):
                if kind == stat.S_IFDIR:
                    if name == '.hg':
                        if not reporoot:
                            return
                    else:
                        dirs.append(name)
                        path = join(root, name)
                        if dirstate._ignore(path):
                            continue
                        for result in walkit(path, False):
                            yield result
                elif kind in (stat.S_IFREG, stat.S_IFLNK):
                    files.append(name)
            yield fullpath, dirs, files

        except OSError, err:
            if err.errno == errno.ENOTDIR:
                # fullpath was a directory, but has since been replaced
                # by a file.
                yield fullpath, dirs, files
            elif err.errno not in walk_ignored_errors:
                raise

    return walkit(root, root == '')

class directory(object):
    """
    Representing a directory

    * path is the relative path from repo root to this directory
    * files is a dict listing the files in this directory
        - keys are file names
        - values are file status
    * dirs is a dict listing the subdirectories
        - key are subdirectories names
        - values are directory objects
    """
    def __init__(self, relpath=''):
        self.path = relpath
        self.files = {}
        self.dirs = {}

    def dir(self, relpath):
        """
        Returns the directory contained at the relative path relpath.
        Creates the intermediate directories if necessary.
        """
        if not relpath:
            return self
        l = relpath.split('/')
        ret = self
        while l:
            next = l.pop(0)
            try:
                ret = ret.dirs[next]
            except KeyError:
                d = directory(join(ret.path, next))
                ret.dirs[next] = d
                ret = d
        return ret

    def walk(self, states, visited=None):
        """
        yield (filename, status) pairs for items in the trees
        that have status in states.
        filenames are relative to the repo root
        """
        for file, st in self.files.iteritems():
            if st in states:
                yield join(self.path, file), st
        for dir in self.dirs.itervalues():
            if visited is not None:
                visited.add(dir.path)
            for e in dir.walk(states):
                yield e

    def lookup(self, states, path, visited):
        """
        yield root-relative filenames that match path, and whose
        status are in states:
        * if path is a file, yield path
        * if path is a directory, yield directory files
        * if path is not tracked, yield nothing
        """
        if path[-1] == '/':
            path = path[:-1]

        paths = path.split('/')

        # we need to check separately for last node
        last = paths.pop()

        tree = self
        try:
            for dir in paths:
                tree = tree.dirs[dir]
        except KeyError:
            # path is not tracked
            visited.add(tree.path)
            return

        try:
            # if path is a directory, walk it
            target = tree.dirs[last]
            visited.add(target.path)
            for file, st in target.walk(states, visited):
                yield file
        except KeyError:
            try:
                if tree.files[last] in states:
                    # path is a file
                    visited.add(tree.path)
                    yield path
            except KeyError:
                # path is not tracked
                pass

class repowatcher(object):
    """
    Watches inotify events
    """
    statuskeys = 'almr!?'

    def __init__(self, ui, dirstate, root):
        self.ui = ui
        self.dirstate = dirstate

        self.wprefix = join(root, '')
        self.prefixlen = len(self.wprefix)

        self.tree = directory()
        self.statcache = {}
        self.statustrees = dict([(s, directory()) for s in self.statuskeys])

        self.ds_info = self.dirstate_info()

        self.last_event = None


    def handle_timeout(self):
        pass

    def dirstate_info(self):
        try:
            st = os.lstat(self.wprefix + '.hg/dirstate')
            return st.st_mtime, st.st_ino
        except OSError, err:
            if err.errno != errno.ENOENT:
                raise
            return 0, 0

    def filestatus(self, fn, st):
        try:
            type_, mode, size, time = self.dirstate._map[fn][:4]
        except KeyError:
            type_ = '?'
        if type_ == 'n':
            st_mode, st_size, st_mtime = st
            if size == -1:
                return 'l'
            if size and (size != st_size or (mode ^ st_mode) & 0100):
                return 'm'
            if time != int(st_mtime):
                return 'l'
            return 'n'
        if type_ == '?' and self.dirstate._dirignore(fn):
            # we must check not only if the file is ignored, but if any part
            # of its path match an ignore pattern
            return 'i'
        return type_

    def updatefile(self, wfn, osstat):
        '''
        update the file entry of an existing file.

        osstat: (mode, size, time) tuple, as returned by os.lstat(wfn)
        '''

        self._updatestatus(wfn, self.filestatus(wfn, osstat))

    def deletefile(self, wfn, oldstatus):
        '''
        update the entry of a file which has been deleted.

        oldstatus: char in statuskeys, status of the file before deletion
        '''
        if oldstatus == 'r':
            newstatus = 'r'
        elif oldstatus in 'almn':
            newstatus = '!'
        else:
            newstatus = None

        self.statcache.pop(wfn, None)
        self._updatestatus(wfn, newstatus)

    def _updatestatus(self, wfn, newstatus):
        '''
        Update the stored status of a file.

        newstatus: - char in (statuskeys + 'ni'), new status to apply.
                   - or None, to stop tracking wfn
        '''
        root, fn = split(wfn)
        d = self.tree.dir(root)

        oldstatus = d.files.get(fn)
        # oldstatus can be either:
        # - None : fn is new
        # - a char in statuskeys: fn is a (tracked) file

        if self.ui.debugflag and oldstatus != newstatus:
            self.ui.note(_('status: %r %s -> %s\n') %
                             (wfn, oldstatus, newstatus))

        if oldstatus and oldstatus in self.statuskeys \
            and oldstatus != newstatus:
            del self.statustrees[oldstatus].dir(root).files[fn]

        if newstatus in (None, 'i'):
            d.files.pop(fn, None)
        elif oldstatus != newstatus:
            d.files[fn] = newstatus
            if newstatus != 'n':
                self.statustrees[newstatus].dir(root).files[fn] = newstatus

    def check_deleted(self, key):
        # Files that had been deleted but were present in the dirstate
        # may have vanished from the dirstate; we must clean them up.
        nuke = []
        for wfn, ignore in self.statustrees[key].walk(key):
            if wfn not in self.dirstate:
                nuke.append(wfn)
        for wfn in nuke:
            root, fn = split(wfn)
            del self.statustrees[key].dir(root).files[fn]
            del self.tree.dir(root).files[fn]

    def update_hgignore(self):
        # An update of the ignore file can potentially change the
        # states of all unknown and ignored files.

        # XXX If the user has other ignore files outside the repo, or
        # changes their list of ignore files at run time, we'll
        # potentially never see changes to them.  We could get the
        # client to report to us what ignore data they're using.
        # But it's easier to do nothing than to open that can of
        # worms.

        if '_ignore' in self.dirstate.__dict__:
            delattr(self.dirstate, '_ignore')
            self.ui.note(_('rescanning due to .hgignore change\n'))
            self.handle_timeout()
            self.scan()

    def getstat(self, wpath):
        try:
            return self.statcache[wpath]
        except KeyError:
            try:
                return self.stat(wpath)
            except OSError, err:
                if err.errno != errno.ENOENT:
                    raise

    def stat(self, wpath):
        try:
            st = os.lstat(join(self.wprefix, wpath))
            ret = st.st_mode, st.st_size, st.st_mtime
            self.statcache[wpath] = ret
            return ret
        except OSError:
            self.statcache.pop(wpath, None)
            raise

class socketlistener(object):
    """
    Listens for client queries on unix socket inotify.sock
    """
    def __init__(self, ui, root, repowatcher, timeout):
        self.ui = ui
        self.repowatcher = repowatcher
        try:
            self.sock = posix.unixdomainserver(
                lambda p: os.path.join(root, '.hg', p),
                'inotify')
        except (OSError, socket.error), err:
            if err.args[0] == errno.EADDRINUSE:
                raise AlreadyStartedException(_('cannot start: '
                                                'socket is already bound'))
            raise
        self.fileno = self.sock.fileno

    def answer_stat_query(self, cs):
        names = cs.read().split('\0')

        states = names.pop()

        self.ui.note(_('answering query for %r\n') % states)

        visited = set()
        if not names:
            def genresult(states, tree):
                for fn, state in tree.walk(states):
                    yield fn
        else:
            def genresult(states, tree):
                for fn in names:
                    for f in tree.lookup(states, fn, visited):
                        yield f

        return ['\0'.join(r) for r in [
            genresult('l', self.repowatcher.statustrees['l']),
            genresult('m', self.repowatcher.statustrees['m']),
            genresult('a', self.repowatcher.statustrees['a']),
            genresult('r', self.repowatcher.statustrees['r']),
            genresult('!', self.repowatcher.statustrees['!']),
            '?' in states
                and genresult('?', self.repowatcher.statustrees['?'])
                or [],
            [],
            'c' in states and genresult('n', self.repowatcher.tree) or [],
            visited
            ]]

    def answer_dbug_query(self):
        return ['\0'.join(self.repowatcher.debug())]

    def accept_connection(self):
        sock, addr = self.sock.accept()

        cs = common.recvcs(sock)
        version = ord(cs.read(1))

        if version != common.version:
            self.ui.warn(_('received query from incompatible client '
                           'version %d\n') % version)
            try:
                # try to send back our version to the client
                # this way, the client too is informed of the mismatch
                sock.sendall(chr(common.version))
            except socket.error:
                pass
            return

        type = cs.read(4)

        if type == 'STAT':
            results = self.answer_stat_query(cs)
        elif type == 'DBUG':
            results = self.answer_dbug_query()
        else:
            self.ui.warn(_('unrecognized query type: %s\n') % type)
            return

        try:
            try:
                v = chr(common.version)

                sock.sendall(v + type + struct.pack(common.resphdrfmts[type],
                                            *map(len, results)))
                sock.sendall(''.join(results))
            finally:
                sock.shutdown(socket.SHUT_WR)
        except socket.error, err:
            if err.args[0] != errno.EPIPE:
                raise

if sys.platform.startswith('linux'):
    import linuxserver as _server
else:
    raise ImportError

master = _server.master

def start(ui, dirstate, root, opts):
    timeout = opts.get('idle_timeout')
    if timeout:
        timeout = float(timeout) * 60000
    else:
        timeout = None

    class service(object):
        def init(self):
            try:
                self.master = master(ui, dirstate, root, timeout)
            except AlreadyStartedException, inst:
                raise util.Abort("inotify-server: %s" % inst)

        def run(self):
            try:
                try:
                    self.master.run()
                except TimeoutException:
                    pass
            finally:
                self.master.shutdown()

    if 'inserve' not in sys.argv:
        runargs = util.hgcmd() + ['inserve', '-R', root]
    else:
        runargs = util.hgcmd() + sys.argv[1:]

    pidfile = ui.config('inotify', 'pidfile')
    if opts['daemon'] and pidfile is not None and 'pid-file' not in runargs:
        runargs.append("--pid-file=%s" % pidfile)

    service = service()
    logfile = ui.config('inotify', 'log')

    appendpid = ui.configbool('inotify', 'appendpid', False)

    ui.debug('starting inotify server: %s\n' % ' '.join(runargs))
    cmdutil.service(opts, initfn=service.init, runfn=service.run,
                    logfile=logfile, runargs=runargs, appendpid=appendpid)
