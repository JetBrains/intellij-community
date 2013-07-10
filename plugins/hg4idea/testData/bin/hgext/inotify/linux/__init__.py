# __init__.py - low-level interfaces to the Linux inotify subsystem

# Copyright 2006 Bryan O'Sullivan <bos@serpentine.com>

# This library is free software; you can redistribute it and/or modify
# it under the terms of version 2.1 of the GNU Lesser General Public
# License, or any later version.

'''Low-level interface to the Linux inotify subsystem.

The inotify subsystem provides an efficient mechanism for file status
monitoring and change notification.

This package provides the low-level inotify system call interface and
associated constants and helper functions.

For a higher-level interface that remains highly efficient, use the
inotify.watcher package.'''

__author__ = "Bryan O'Sullivan <bos@serpentine.com>"

from _inotify import *

procfs_path = '/proc/sys/fs/inotify'

def _read_procfs_value(name):
    def read_value():
        try:
            fp = open(procfs_path + '/' + name)
            r = int(fp.read())
            fp.close()
            return r
        except OSError:
            return None

    read_value.__doc__ = '''Return the value of the %s setting from /proc.

    If inotify is not enabled on this system, return None.''' % name

    return read_value

max_queued_events = _read_procfs_value('max_queued_events')
max_user_instances = _read_procfs_value('max_user_instances')
max_user_watches = _read_procfs_value('max_user_watches')
