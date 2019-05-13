# linuxserver.py - inotify status server for linux
#
# Copyright 2006, 2007, 2008 Bryan O'Sullivan <bos@serpentine.com>
# Copyright 2007, 2008 Brendan Cully <brendan@kublai.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from mercurial.i18n import _
from mercurial import osutil, util, error
import server
import errno, os, select, stat, sys, time

try:
    import linux as inotify
    from linux import watcher
except ImportError:
    raise

def walkrepodirs(dirstate, absroot):
    '''Iterate over all subdirectories of this repo.
    Exclude the .hg directory, any nested repos, and ignored dirs.'''
    def walkit(dirname, top):
        fullpath = server.join(absroot, dirname)
        try:
            for name, kind in osutil.listdir(fullpath):
                if kind == stat.S_IFDIR:
                    if name == '.hg':
                        if not top:
                            return
                    else:
                        d = server.join(dirname, name)
                        if dirstate._ignore(d):
                            continue
                        for subdir in walkit(d, False):
                            yield subdir
        except OSError, err:
            if err.errno not in server.walk_ignored_errors:
                raise
        yield fullpath

    return walkit('', True)

def _explain_watch_limit(ui, dirstate, rootabs):
    path = '/proc/sys/fs/inotify/max_user_watches'
    try:
        limit = int(util.readfile(path))
    except IOError, err:
        if err.errno != errno.ENOENT:
            raise
        raise util.Abort(_('this system does not seem to '
                           'support inotify'))
    ui.warn(_('*** the current per-user limit on the number '
              'of inotify watches is %s\n') % limit)
    ui.warn(_('*** this limit is too low to watch every '
              'directory in this repository\n'))
    ui.warn(_('*** counting directories: '))
    ndirs = len(list(walkrepodirs(dirstate, rootabs)))
    ui.warn(_('found %d\n') % ndirs)
    newlimit = min(limit, 1024)
    while newlimit < ((limit + ndirs) * 1.1):
        newlimit *= 2
    ui.warn(_('*** to raise the limit from %d to %d (run as root):\n') %
            (limit, newlimit))
    ui.warn(_('***  echo %d > %s\n') % (newlimit, path))
    raise util.Abort(_('cannot watch %s until inotify watch limit is raised')
                     % rootabs)

class pollable(object):
    """
    Interface to support polling.
    The file descriptor returned by fileno() is registered to a polling
    object.
    Usage:
        Every tick, check if an event has happened since the last tick:
        * If yes, call handle_events
        * If no, call handle_timeout
    """
    poll_events = select.POLLIN
    instances = {}
    poll = select.poll()

    def fileno(self):
        raise NotImplementedError

    def handle_events(self, events):
        raise NotImplementedError

    def handle_timeout(self):
        raise NotImplementedError

    def shutdown(self):
        raise NotImplementedError

    def register(self, timeout):
        fd = self.fileno()

        pollable.poll.register(fd, pollable.poll_events)
        pollable.instances[fd] = self

        self.registered = True
        self.timeout = timeout

    def unregister(self):
        pollable.poll.unregister(self)
        self.registered = False

    @classmethod
    def run(cls):
        while True:
            timeout = None
            timeobj = None
            for obj in cls.instances.itervalues():
                if obj.timeout is not None and (timeout is None
                                                or obj.timeout < timeout):
                    timeout, timeobj = obj.timeout, obj
            try:
                events = cls.poll.poll(timeout)
            except select.error, err:
                if err.args[0] == errno.EINTR:
                    continue
                raise
            if events:
                by_fd = {}
                for fd, event in events:
                    by_fd.setdefault(fd, []).append(event)

                for fd, events in by_fd.iteritems():
                    cls.instances[fd].handle_pollevents(events)

            elif timeobj:
                timeobj.handle_timeout()

def eventaction(code):
    """
    Decorator to help handle events in repowatcher
    """
    def decorator(f):
        def wrapper(self, wpath):
            if code == 'm' and wpath in self.lastevent and \
                self.lastevent[wpath] in 'cm':
                return
            self.lastevent[wpath] = code
            self.timeout = 250

            f(self, wpath)

        wrapper.func_name = f.func_name
        return wrapper
    return decorator

class repowatcher(server.repowatcher, pollable):
    """
    Watches inotify events
    """
    mask = (
        inotify.IN_ATTRIB |
        inotify.IN_CREATE |
        inotify.IN_DELETE |
        inotify.IN_DELETE_SELF |
        inotify.IN_MODIFY |
        inotify.IN_MOVED_FROM |
        inotify.IN_MOVED_TO |
        inotify.IN_MOVE_SELF |
        inotify.IN_ONLYDIR |
        inotify.IN_UNMOUNT |
        0)

    def __init__(self, ui, dirstate, root):
        server.repowatcher.__init__(self, ui, dirstate, root)

        self.lastevent = {}
        self.dirty = False
        try:
            self.watcher = watcher.watcher()
        except OSError, err:
            raise util.Abort(_('inotify service not available: %s') %
                             err.strerror)
        self.threshold = watcher.threshold(self.watcher)
        self.fileno = self.watcher.fileno
        self.register(timeout=None)

        self.handle_timeout()
        self.scan()

    def event_time(self):
        last = self.last_event
        now = time.time()
        self.last_event = now

        if last is None:
            return 'start'
        delta = now - last
        if delta < 5:
            return '+%.3f' % delta
        if delta < 50:
            return '+%.2f' % delta
        return '+%.1f' % delta

    def add_watch(self, path, mask):
        if not path:
            return
        if self.watcher.path(path) is None:
            if self.ui.debugflag:
                self.ui.note(_('watching %r\n') % path[self.prefixlen:])
            try:
                self.watcher.add(path, mask)
            except OSError, err:
                if err.errno in (errno.ENOENT, errno.ENOTDIR):
                    return
                if err.errno != errno.ENOSPC:
                    raise
                _explain_watch_limit(self.ui, self.dirstate, self.wprefix)

    def setup(self):
        self.ui.note(_('watching directories under %r\n') % self.wprefix)
        self.add_watch(self.wprefix + '.hg', inotify.IN_DELETE)

    def scan(self, topdir=''):
        ds = self.dirstate._map.copy()
        self.add_watch(server.join(self.wprefix, topdir), self.mask)
        for root, dirs, files in server.walk(self.dirstate, self.wprefix,
                                             topdir):
            for d in dirs:
                self.add_watch(server.join(root, d), self.mask)
            wroot = root[self.prefixlen:]
            for fn in files:
                wfn = server.join(wroot, fn)
                self.updatefile(wfn, self.getstat(wfn))
                ds.pop(wfn, None)
        wtopdir = topdir
        if wtopdir and wtopdir[-1] != '/':
            wtopdir += '/'
        for wfn, state in ds.iteritems():
            if not wfn.startswith(wtopdir):
                continue
            try:
                st = self.stat(wfn)
            except OSError:
                status = state[0]
                self.deletefile(wfn, status)
            else:
                self.updatefile(wfn, st)
        self.check_deleted('!')
        self.check_deleted('r')

    @eventaction('c')
    def created(self, wpath):
        if wpath == '.hgignore':
            self.update_hgignore()
        try:
            st = self.stat(wpath)
            if stat.S_ISREG(st[0]) or stat.S_ISLNK(st[0]):
                self.updatefile(wpath, st)
        except OSError:
            pass

    @eventaction('m')
    def modified(self, wpath):
        if wpath == '.hgignore':
            self.update_hgignore()
        try:
            st = self.stat(wpath)
            if stat.S_ISREG(st[0]):
                if self.dirstate[wpath] in 'lmn':
                    self.updatefile(wpath, st)
        except OSError:
            pass

    @eventaction('d')
    def deleted(self, wpath):
        if wpath == '.hgignore':
            self.update_hgignore()
        elif wpath.startswith('.hg/'):
            return

        self.deletefile(wpath, self.dirstate[wpath])

    def process_create(self, wpath, evt):
        if self.ui.debugflag:
            self.ui.note(_('%s event: created %s\n') %
                         (self.event_time(), wpath))

        if evt.mask & inotify.IN_ISDIR:
            self.scan(wpath)
        else:
            self.created(wpath)

    def process_delete(self, wpath, evt):
        if self.ui.debugflag:
            self.ui.note(_('%s event: deleted %s\n') %
                         (self.event_time(), wpath))

        if evt.mask & inotify.IN_ISDIR:
            tree = self.tree.dir(wpath)
            todelete = [wfn for wfn, ignore in tree.walk('?')]
            for fn in todelete:
                self.deletefile(fn, '?')
            self.scan(wpath)
        else:
            self.deleted(wpath)

    def process_modify(self, wpath, evt):
        if self.ui.debugflag:
            self.ui.note(_('%s event: modified %s\n') %
                         (self.event_time(), wpath))

        if not (evt.mask & inotify.IN_ISDIR):
            self.modified(wpath)

    def process_unmount(self, evt):
        self.ui.warn(_('filesystem containing %s was unmounted\n') %
                     evt.fullpath)
        sys.exit(0)

    def handle_pollevents(self, events):
        if self.ui.debugflag:
            self.ui.note(_('%s readable: %d bytes\n') %
                         (self.event_time(), self.threshold.readable()))
        if not self.threshold():
            if self.registered:
                if self.ui.debugflag:
                    self.ui.note(_('%s below threshold - unhooking\n') %
                                 (self.event_time()))
                self.unregister()
                self.timeout = 250
        else:
            self.read_events()

    def read_events(self, bufsize=None):
        events = self.watcher.read(bufsize)
        if self.ui.debugflag:
            self.ui.note(_('%s reading %d events\n') %
                         (self.event_time(), len(events)))
        for evt in events:
            if evt.fullpath == self.wprefix[:-1]:
                # events on the root of the repository
                # itself, e.g. permission changes or repository move
                continue
            assert evt.fullpath.startswith(self.wprefix)
            wpath = evt.fullpath[self.prefixlen:]

            # paths have been normalized, wpath never ends with a '/'

            if wpath.startswith('.hg/') and evt.mask & inotify.IN_ISDIR:
                # ignore subdirectories of .hg/ (merge, patches...)
                continue
            if wpath == ".hg/wlock":
                if evt.mask & inotify.IN_DELETE:
                    self.dirstate.invalidate()
                    self.dirty = False
                    self.scan()
                elif evt.mask & inotify.IN_CREATE:
                    self.dirty = True
            else:
                if self.dirty:
                    continue

                if evt.mask & inotify.IN_UNMOUNT:
                    self.process_unmount(wpath, evt)
                elif evt.mask & (inotify.IN_MODIFY | inotify.IN_ATTRIB):
                    self.process_modify(wpath, evt)
                elif evt.mask & (inotify.IN_DELETE | inotify.IN_DELETE_SELF |
                                 inotify.IN_MOVED_FROM):
                    self.process_delete(wpath, evt)
                elif evt.mask & (inotify.IN_CREATE | inotify.IN_MOVED_TO):
                    self.process_create(wpath, evt)

        self.lastevent.clear()

    def handle_timeout(self):
        if not self.registered:
            if self.ui.debugflag:
                self.ui.note(_('%s hooking back up with %d bytes readable\n') %
                             (self.event_time(), self.threshold.readable()))
            self.read_events(0)
            self.register(timeout=None)

        self.timeout = None

    def shutdown(self):
        self.watcher.close()

    def debug(self):
        """
        Returns a sorted list of relatives paths currently watched,
        for debugging purposes.
        """
        return sorted(tuple[0][self.prefixlen:] for tuple in self.watcher)

class socketlistener(server.socketlistener, pollable):
    """
    Listens for client queries on unix socket inotify.sock
    """
    def __init__(self, ui, root, repowatcher, timeout):
        server.socketlistener.__init__(self, ui, root, repowatcher, timeout)
        self.register(timeout=timeout)

    def handle_timeout(self):
        raise server.TimeoutException

    def handle_pollevents(self, events):
        for e in events:
            self.accept_connection()

    def shutdown(self):
        self.sock.close()
        self.sock.cleanup()

    def answer_stat_query(self, cs):
        if self.repowatcher.timeout:
            # We got a query while a rescan is pending.  Make sure we
            # rescan before responding, or we could give back a wrong
            # answer.
            self.repowatcher.handle_timeout()
        return server.socketlistener.answer_stat_query(self, cs)

class master(object):
    def __init__(self, ui, dirstate, root, timeout=None):
        self.ui = ui
        self.repowatcher = repowatcher(ui, dirstate, root)
        self.socketlistener = socketlistener(ui, root, self.repowatcher,
                                             timeout)

    def shutdown(self):
        for obj in pollable.instances.itervalues():
            try:
                obj.shutdown()
            except error.SignalInterrupt:
                pass

    def run(self):
        self.repowatcher.setup()
        self.ui.note(_('finished setup\n'))
        if os.getenv('TIME_STARTUP'):
            sys.exit(0)
        pollable.run()
