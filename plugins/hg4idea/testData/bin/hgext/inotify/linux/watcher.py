# watcher.py - high-level interfaces to the Linux inotify subsystem

# Copyright 2006 Bryan O'Sullivan <bos@serpentine.com>

# This library is free software; you can redistribute it and/or modify
# it under the terms of version 2.1 of the GNU Lesser General Public
# License, or any later version.

'''High-level interfaces to the Linux inotify subsystem.

The inotify subsystem provides an efficient mechanism for file status
monitoring and change notification.

The watcher class hides the low-level details of the inotify
interface, and provides a Pythonic wrapper around it.  It generates
events that provide somewhat more information than raw inotify makes
available.

The autowatcher class is more useful, as it automatically watches
newly-created directories on your behalf.'''

__author__ = "Bryan O'Sullivan <bos@serpentine.com>"

import _inotify as inotify
import array
import errno
import fcntl
import os
import termios


class event(object):
    '''Derived inotify event class.

    The following fields are available:

        mask: event mask, indicating what kind of event this is

        cookie: rename cookie, if a rename-related event

        path: path of the directory in which the event occurred

        name: name of the directory entry to which the event occurred
        (may be None if the event happened to a watched directory)

        fullpath: complete path at which the event occurred

        wd: watch descriptor that triggered this event'''

    __slots__ = (
        'cookie',
        'fullpath',
        'mask',
        'name',
        'path',
        'raw',
        'wd',
        )

    def __init__(self, raw, path):
        self.path = path
        self.raw = raw
        if raw.name:
            self.fullpath = path + '/' + raw.name
        else:
            self.fullpath = path

        self.wd = raw.wd
        self.mask = raw.mask
        self.cookie = raw.cookie
        self.name = raw.name

    def __repr__(self):
        r = repr(self.raw)
        return 'event(path=' + repr(self.path) + ', ' + r[r.find('(') + 1:]


_event_props = {
    'access': 'File was accessed',
    'modify': 'File was modified',
    'attrib': 'Attribute of a directory entry was changed',
    'close_write': 'File was closed after being written to',
    'close_nowrite': 'File was closed without being written to',
    'open': 'File was opened',
    'moved_from': 'Directory entry was renamed from this name',
    'moved_to': 'Directory entry was renamed to this name',
    'create': 'Directory entry was created',
    'delete': 'Directory entry was deleted',
    'delete_self': 'The watched directory entry was deleted',
    'move_self': 'The watched directory entry was renamed',
    'unmount': 'Directory was unmounted, and can no longer be watched',
    'q_overflow': 'Kernel dropped events due to queue overflow',
    'ignored': 'Directory entry is no longer being watched',
    'isdir': 'Event occurred on a directory',
    }

for k, v in _event_props.iteritems():
    mask = getattr(inotify, 'IN_' + k.upper())
    def getter(self):
        return self.mask & mask
    getter.__name__ = k
    getter.__doc__ = v
    setattr(event, k, property(getter, doc=v))

del _event_props


class watcher(object):
    '''Provide a Pythonic interface to the low-level inotify API.

    Also adds derived information to each event that is not available
    through the normal inotify API, such as directory name.'''

    __slots__ = (
        'fd',
        '_paths',
        '_wds',
        )

    def __init__(self):
        '''Create a new inotify instance.'''

        self.fd = inotify.init()
        self._paths = {}
        self._wds = {}

    def fileno(self):
        '''Return the file descriptor this watcher uses.

        Useful for passing to select and poll.'''

        return self.fd

    def add(self, path, mask):
        '''Add or modify a watch.

        Return the watch descriptor added or modified.'''

        path = os.path.normpath(path)
        wd = inotify.add_watch(self.fd, path, mask)
        self._paths[path] = wd, mask
        self._wds[wd] = path, mask
        return wd

    def remove(self, wd):
        '''Remove the given watch.'''

        inotify.remove_watch(self.fd, wd)
        self._remove(wd)

    def _remove(self, wd):
        path_mask = self._wds.pop(wd, None)
        if path_mask is not None:
            self._paths.pop(path_mask[0])

    def path(self, path):
        '''Return a (watch descriptor, event mask) pair for the given path.

        If the path is not being watched, return None.'''

        return self._paths.get(path)

    def wd(self, wd):
        '''Return a (path, event mask) pair for the given watch descriptor.

        If the watch descriptor is not valid or not associated with
        this watcher, return None.'''

        return self._wds.get(wd)

    def read(self, bufsize=None):
        '''Read a list of queued inotify events.

        If bufsize is zero, only return those events that can be read
        immediately without blocking.  Otherwise, block until events are
        available.'''

        events = []
        for evt in inotify.read(self.fd, bufsize):
            events.append(event(evt, self._wds[evt.wd][0]))
            if evt.mask & inotify.IN_IGNORED:
                self._remove(evt.wd)
            elif evt.mask & inotify.IN_UNMOUNT:
                self.close()
        return events

    def close(self):
        '''Shut down this watcher.

        All subsequent method calls are likely to raise exceptions.'''

        os.close(self.fd)
        self.fd = None
        self._paths = None
        self._wds = None

    def __len__(self):
        '''Return the number of active watches.'''

        return len(self._paths)

    def __iter__(self):
        '''Yield a (path, watch descriptor, event mask) tuple for each
        entry being watched.'''

        for path, (wd, mask) in self._paths.iteritems():
            yield path, wd, mask

    def __del__(self):
        if self.fd is not None:
            os.close(self.fd)

    ignored_errors = [errno.ENOENT, errno.EPERM, errno.ENOTDIR]

    def add_iter(self, path, mask, onerror=None):
        '''Add or modify watches over path and its subdirectories.

        Yield each added or modified watch descriptor.

        To ensure that this method runs to completion, you must
        iterate over all of its results, even if you do not care what
        they are.  For example:

            for wd in w.add_iter(path, mask):
                pass

        By default, errors are ignored.  If optional arg "onerror" is
        specified, it should be a function; it will be called with one
        argument, an OSError instance.  It can report the error to
        continue with the walk, or raise the exception to abort the
        walk.'''

        # Add the IN_ONLYDIR flag to the event mask, to avoid a possible
        # race when adding a subdirectory.  In the time between the
        # event being queued by the kernel and us processing it, the
        # directory may have been deleted, or replaced with a different
        # kind of entry with the same name.

        submask = mask | inotify.IN_ONLYDIR

        try:
            yield self.add(path, mask)
        except OSError, err:
            if onerror and err.errno not in self.ignored_errors:
                onerror(err)
        for root, dirs, names in os.walk(path, topdown=False, onerror=onerror):
            for d in dirs:
                try:
                    yield self.add(root + '/' + d, submask)
                except OSError, err:
                    if onerror and err.errno not in self.ignored_errors:
                        onerror(err)

    def add_all(self, path, mask, onerror=None):
        '''Add or modify watches over path and its subdirectories.

        Return a list of added or modified watch descriptors.

        By default, errors are ignored.  If optional arg "onerror" is
        specified, it should be a function; it will be called with one
        argument, an OSError instance.  It can report the error to
        continue with the walk, or raise the exception to abort the
        walk.'''

        return [w for w in self.add_iter(path, mask, onerror)]


class autowatcher(watcher):
    '''watcher class that automatically watches newly created directories.'''

    __slots__ = (
        'addfilter',
        )

    def __init__(self, addfilter=None):
        '''Create a new inotify instance.

        This instance will automatically watch newly created
        directories.

        If the optional addfilter parameter is not None, it must be a
        callable that takes one parameter.  It will be called each time
        a directory is about to be automatically watched.  If it returns
        True, the directory will be watched if it still exists,
        otherwise, it will be skipped.'''

        super(autowatcher, self).__init__()
        self.addfilter = addfilter

    _dir_create_mask = inotify.IN_ISDIR | inotify.IN_CREATE

    def read(self, bufsize=None):
        events = super(autowatcher, self).read(bufsize)
        for evt in events:
            if evt.mask & self._dir_create_mask == self._dir_create_mask:
                if self.addfilter is None or self.addfilter(evt):
                    parentmask = self._wds[evt.wd][1]
                    # See note about race avoidance via IN_ONLYDIR above.
                    mask = parentmask | inotify.IN_ONLYDIR
                    try:
                        self.add_all(evt.fullpath, mask)
                    except OSError, err:
                        if err.errno not in self.ignored_errors:
                            raise
        return events


class threshold(object):
    '''Class that indicates whether a file descriptor has reached a
    threshold of readable bytes available.

    This class is not thread-safe.'''

    __slots__ = (
        'fd',
        'threshold',
        '_iocbuf',
        )

    def __init__(self, fd, threshold=1024):
        self.fd = fd
        self.threshold = threshold
        self._iocbuf = array.array('i', [0])

    def readable(self):
        '''Return the number of bytes readable on this file descriptor.'''

        fcntl.ioctl(self.fd, termios.FIONREAD, self._iocbuf, True)
        return self._iocbuf[0]

    def __call__(self):
        '''Indicate whether the number of readable bytes has met or
        exceeded the threshold.'''

        return self.readable() >= self.threshold
