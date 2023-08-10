# repocache.py - in-memory repository cache for long-running services
#
# Copyright 2018 Yuya Nishihara <yuya@tcha.org>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import collections
import gc
import threading

from . import (
    error,
    hg,
    obsolete,
    scmutil,
    util,
)


class repoloader(object):
    """Load repositories in background thread

    This is designed for a forking server. A cached repo cannot be obtained
    until the server fork()s a worker and the loader thread stops.
    """

    def __init__(self, ui, maxlen):
        self._ui = ui.copy()
        self._cache = util.lrucachedict(max=maxlen)
        # use deque and Event instead of Queue since deque can discard
        # old items to keep at most maxlen items.
        self._inqueue = collections.deque(maxlen=maxlen)
        self._accepting = False
        self._newentry = threading.Event()
        self._thread = None

    def start(self):
        assert not self._thread
        if self._inqueue.maxlen == 0:
            # no need to spawn loader thread as the cache is disabled
            return
        self._accepting = True
        self._thread = threading.Thread(target=self._mainloop)
        self._thread.start()

    def stop(self):
        if not self._thread:
            return
        self._accepting = False
        self._newentry.set()
        self._thread.join()
        self._thread = None
        self._cache.clear()
        self._inqueue.clear()

    def load(self, path):
        """Request to load the specified repository in background"""
        self._inqueue.append(path)
        self._newentry.set()

    def get(self, path):
        """Return a cached repo if available

        This function must be called after fork(), where the loader thread
        is stopped. Otherwise, the returned repo might be updated by the
        loader thread.
        """
        if self._thread and self._thread.is_alive():
            raise error.ProgrammingError(
                b'cannot obtain cached repo while loader is active'
            )
        return self._cache.peek(path, None)

    def _mainloop(self):
        while self._accepting:
            # Avoid heavy GC after fork(), which would cancel the benefit of
            # COW. We assume that GIL is acquired while GC is underway in the
            # loader thread. If that isn't true, we might have to move
            # gc.collect() to the main thread so that fork() would never stop
            # the thread where GC is in progress.
            gc.collect()

            self._newentry.wait()
            while self._accepting:
                self._newentry.clear()
                try:
                    path = self._inqueue.popleft()
                except IndexError:
                    break
                scmutil.callcatch(self._ui, lambda: self._load(path))

    def _load(self, path):
        start = util.timer()
        # TODO: repo should be recreated if storage configuration changed
        try:
            # pop before loading so inconsistent state wouldn't be exposed
            repo = self._cache.pop(path)
        except KeyError:
            repo = hg.repository(self._ui, path).unfiltered()
        _warmupcache(repo)
        repo.ui.log(
            b'repocache',
            b'loaded repo into cache: %s (in %.3fs)\n',
            path,
            util.timer() - start,
        )
        self._cache.insert(path, repo)


# TODO: think about proper API of preloading cache
def _warmupcache(repo):
    repo.invalidateall()
    repo.changelog
    repo.obsstore._all
    repo.obsstore.successors
    repo.obsstore.predecessors
    repo.obsstore.children
    for name in obsolete.cachefuncs:
        obsolete.getrevs(repo, name)
    repo._phasecache.loadphaserevs(repo)


# TODO: think about proper API of attaching preloaded attributes
def copycache(srcrepo, destrepo):
    """Copy cached attributes from srcrepo to destrepo"""
    destfilecache = destrepo._filecache
    srcfilecache = srcrepo._filecache
    if b'changelog' in srcfilecache:
        destfilecache[b'changelog'] = ce = srcfilecache[b'changelog']
        ce.obj.opener = ce.obj._realopener = destrepo.svfs
    if b'obsstore' in srcfilecache:
        destfilecache[b'obsstore'] = ce = srcfilecache[b'obsstore']
        ce.obj.svfs = destrepo.svfs
    if b'_phasecache' in srcfilecache:
        destfilecache[b'_phasecache'] = ce = srcfilecache[b'_phasecache']
        ce.obj.opener = destrepo.svfs
