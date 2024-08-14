# worker.py - master-slave parallelism support
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import os
import pickle
import selectors
import signal
import sys
import threading
import time

from .i18n import _
from . import (
    encoding,
    error,
    pycompat,
    scmutil,
)


def countcpus():
    '''try to count the number of CPUs on the system'''

    # posix
    try:
        n = int(os.sysconf('SC_NPROCESSORS_ONLN'))
        if n > 0:
            return n
    except (AttributeError, ValueError):
        pass

    # windows
    try:
        n = int(encoding.environ[b'NUMBER_OF_PROCESSORS'])
        if n > 0:
            return n
    except (KeyError, ValueError):
        pass

    return 1


def _numworkers(ui):
    s = ui.config(b'worker', b'numcpus')
    if s:
        try:
            n = int(s)
            if n >= 1:
                return n
        except ValueError:
            raise error.Abort(_(b'number of cpus must be an integer'))
    return min(max(countcpus(), 4), 32)


def ismainthread():
    return threading.current_thread() == threading.main_thread()


if (
    pycompat.isposix and pycompat.sysplatform != b'OpenVMS'
) or pycompat.iswindows:
    _STARTUP_COST = 0.01
    # The Windows worker is thread based. If tasks are CPU bound, threads
    # in the presence of the GIL result in excessive context switching and
    # this overhead can slow down execution.
    _DISALLOW_THREAD_UNSAFE = pycompat.iswindows
else:
    _STARTUP_COST = 1e30
    _DISALLOW_THREAD_UNSAFE = False


def worthwhile(ui, costperop, nops, threadsafe=True):
    """try to determine whether the benefit of multiple processes can
    outweigh the cost of starting them"""

    if not threadsafe and _DISALLOW_THREAD_UNSAFE:
        return False

    linear = costperop * nops
    workers = _numworkers(ui)
    benefit = linear - (_STARTUP_COST * workers + linear / workers)
    return benefit >= 0.15


def worker(
    ui,
    costperarg,
    func,
    staticargs,
    args,
    hasretval=False,
    threadsafe=True,
    prefork=None,
):
    """run a function, possibly in parallel in multiple worker
    processes.

    returns a progress iterator

    costperarg - cost of a single task

    func - function to run. It is expected to return a progress iterator.

    staticargs - arguments to pass to every invocation of the function

    args - arguments to split into chunks, to pass to individual
    workers

    hasretval - when True, func and the current function return an progress
    iterator then a dict (encoded as an iterator that yield many (False, ..)
    then a (True, dict)). The dicts are joined in some arbitrary order, so
    overlapping keys are a bad idea.

    threadsafe - whether work items are thread safe and can be executed using
    a thread-based worker. Should be disabled for CPU heavy tasks that don't
    release the GIL.

    prefork - a parameterless Callable that is invoked prior to forking the
    process.  fork() is only used on non-Windows platforms, but is also not
    called on POSIX platforms if the work amount doesn't warrant a worker.
    """
    enabled = ui.configbool(b'worker', b'enabled')
    if enabled and _platformworker is _posixworker and not ismainthread():
        # The POSIX worker has to install a handler for SIGCHLD.
        # Python up to 3.9 only allows this in the main thread.
        enabled = False

    if enabled and worthwhile(ui, costperarg, len(args), threadsafe=threadsafe):
        return _platformworker(
            ui, func, staticargs, args, hasretval, prefork=prefork
        )
    return func(*staticargs + (args,))


def _posixworker(ui, func, staticargs, args, hasretval, prefork=None):
    workers = _numworkers(ui)
    oldhandler = signal.getsignal(signal.SIGINT)
    signal.signal(signal.SIGINT, signal.SIG_IGN)
    pids, problem = set(), [0]

    def killworkers():
        # unregister SIGCHLD handler as all children will be killed. This
        # function shouldn't be interrupted by another SIGCHLD; otherwise pids
        # could be updated while iterating, which would cause inconsistency.
        signal.signal(signal.SIGCHLD, oldchldhandler)
        # if one worker bails, there's no good reason to wait for the rest
        for p in pids:
            try:
                os.kill(p, signal.SIGTERM)
            except ProcessLookupError:
                pass

    def waitforworkers(blocking=True):
        for pid in pids.copy():
            p = st = 0
            try:
                p, st = os.waitpid(pid, (0 if blocking else os.WNOHANG))
            except ChildProcessError:
                # child would already be reaped, but pids yet been
                # updated (maybe interrupted just after waitpid)
                pids.discard(pid)
            if not p:
                # skip subsequent steps, because child process should
                # be still running in this case
                continue
            pids.discard(p)
            st = _exitstatus(st)
            if st and not problem[0]:
                problem[0] = st

    def sigchldhandler(signum, frame):
        waitforworkers(blocking=False)
        if problem[0]:
            killworkers()

    oldchldhandler = signal.signal(signal.SIGCHLD, sigchldhandler)
    ui.flush()
    parentpid = os.getpid()
    pipes = []
    retval = {}

    if prefork:
        prefork()

    for pargs in partition(args, min(workers, len(args))):
        # Every worker gets its own pipe to send results on, so we don't have to
        # implement atomic writes larger than PIPE_BUF. Each forked process has
        # its own pipe's descriptors in the local variables, and the parent
        # process has the full list of pipe descriptors (and it doesn't really
        # care what order they're in).
        rfd, wfd = os.pipe()
        pipes.append((rfd, wfd))
        # make sure we use os._exit in all worker code paths. otherwise the
        # worker may do some clean-ups which could cause surprises like
        # deadlock. see sshpeer.cleanup for example.
        # override error handling *before* fork. this is necessary because
        # exception (signal) may arrive after fork, before "pid =" assignment
        # completes, and other exception handler (dispatch.py) can lead to
        # unexpected code path without os._exit.
        ret = -1
        try:
            pid = os.fork()
            if pid == 0:
                signal.signal(signal.SIGINT, oldhandler)
                signal.signal(signal.SIGCHLD, oldchldhandler)

                def workerfunc():
                    for r, w in pipes[:-1]:
                        os.close(r)
                        os.close(w)
                    os.close(rfd)
                    with os.fdopen(wfd, 'wb') as wf:
                        for result in func(*(staticargs + (pargs,))):
                            pickle.dump(result, wf)
                            wf.flush()
                    return 0

                ret = scmutil.callcatch(ui, workerfunc)
        except:  # parent re-raises, child never returns
            if os.getpid() == parentpid:
                raise
            exctype = sys.exc_info()[0]
            force = not issubclass(exctype, KeyboardInterrupt)
            ui.traceback(force=force)
        finally:
            if os.getpid() != parentpid:
                try:
                    ui.flush()
                except:  # never returns, no re-raises
                    pass
                finally:
                    os._exit(ret & 255)
        pids.add(pid)
    selector = selectors.DefaultSelector()
    for rfd, wfd in pipes:
        os.close(wfd)
        # Buffering is needed for performance, but it also presents a problem:
        # selector doesn't take the buffered data into account,
        # so we have to arrange it so that the buffers are empty when select is called
        # (see [peek_nonblock])
        selector.register(os.fdopen(rfd, 'rb', 4096), selectors.EVENT_READ)

    def peek_nonblock(f):
        os.set_blocking(f.fileno(), False)
        res = f.peek()
        os.set_blocking(f.fileno(), True)
        return res

    def load_all(f):
        # The pytype error likely goes away on a modern version of
        # pytype having a modern typeshed snapshot.
        # pytype: disable=wrong-arg-types
        yield pickle.load(f)
        while len(peek_nonblock(f)) > 0:
            yield pickle.load(f)
        # pytype: enable=wrong-arg-types

    def cleanup():
        signal.signal(signal.SIGINT, oldhandler)
        waitforworkers()
        signal.signal(signal.SIGCHLD, oldchldhandler)
        selector.close()
        return problem[0]

    try:
        openpipes = len(pipes)
        while openpipes > 0:
            for key, events in selector.select():
                try:
                    for res in load_all(key.fileobj):
                        if hasretval and res[0]:
                            retval.update(res[1])
                        else:
                            yield res
                except EOFError:
                    selector.unregister(key.fileobj)
                    # pytype: disable=attribute-error
                    key.fileobj.close()
                    # pytype: enable=attribute-error
                    openpipes -= 1
    except:  # re-raises
        killworkers()
        cleanup()
        raise
    status = cleanup()
    if status:
        if status < 0:
            os.kill(os.getpid(), -status)
        raise error.WorkerError(status)
    if hasretval:
        yield True, retval


def _posixexitstatus(code):
    """convert a posix exit status into the same form returned by
    os.spawnv

    returns None if the process was stopped instead of exiting"""
    if os.WIFEXITED(code):
        return os.WEXITSTATUS(code)
    elif os.WIFSIGNALED(code):
        return -(os.WTERMSIG(code))


def _windowsworker(ui, func, staticargs, args, hasretval, prefork=None):
    class Worker(threading.Thread):
        def __init__(
            self, taskqueue, resultqueue, func, staticargs, *args, **kwargs
        ):
            threading.Thread.__init__(self, *args, **kwargs)
            self._taskqueue = taskqueue
            self._resultqueue = resultqueue
            self._func = func
            self._staticargs = staticargs
            self._interrupted = False
            self.daemon = True
            self.exception = None

        def interrupt(self):
            self._interrupted = True

        def run(self):
            try:
                while not self._taskqueue.empty():
                    try:
                        args = self._taskqueue.get_nowait()
                        for res in self._func(*self._staticargs + (args,)):
                            self._resultqueue.put(res)
                            # threading doesn't provide a native way to
                            # interrupt execution. handle it manually at every
                            # iteration.
                            if self._interrupted:
                                return
                    except pycompat.queue.Empty:
                        break
            except Exception as e:
                # store the exception such that the main thread can resurface
                # it as if the func was running without workers.
                self.exception = e
                raise

    threads = []

    def trykillworkers():
        # Allow up to 1 second to clean worker threads nicely
        cleanupend = time.time() + 1
        for t in threads:
            t.interrupt()
        for t in threads:
            remainingtime = cleanupend - time.time()
            t.join(remainingtime)
            if t.is_alive():
                # pass over the workers joining failure. it is more
                # important to surface the inital exception than the
                # fact that one of workers may be processing a large
                # task and does not get to handle the interruption.
                ui.warn(
                    _(
                        b"failed to kill worker threads while "
                        b"handling an exception\n"
                    )
                )
                return

    workers = _numworkers(ui)
    resultqueue = pycompat.queue.Queue()
    taskqueue = pycompat.queue.Queue()
    retval = {}
    # partition work to more pieces than workers to minimize the chance
    # of uneven distribution of large tasks between the workers
    for pargs in partition(args, workers * 20):
        taskqueue.put(pargs)
    for _i in range(workers):
        t = Worker(taskqueue, resultqueue, func, staticargs)
        threads.append(t)
        t.start()
    try:
        while len(threads) > 0:
            while not resultqueue.empty():
                res = resultqueue.get()
                if hasretval and res[0]:
                    retval.update(res[1])
                else:
                    yield res
            threads[0].join(0.05)
            finishedthreads = [_t for _t in threads if not _t.is_alive()]
            for t in finishedthreads:
                if t.exception is not None:
                    raise t.exception
                threads.remove(t)
    except (Exception, KeyboardInterrupt):  # re-raises
        trykillworkers()
        raise
    while not resultqueue.empty():
        res = resultqueue.get()
        if hasretval and res[0]:
            retval.update(res[1])
        else:
            yield res
    if hasretval:
        yield True, retval


if pycompat.iswindows:
    _platformworker = _windowsworker
else:
    _platformworker = _posixworker
    _exitstatus = _posixexitstatus


def partition(lst, nslices):
    """partition a list into N slices of roughly equal size

    The current strategy takes every Nth element from the input. If
    we ever write workers that need to preserve grouping in input
    we should consider allowing callers to specify a partition strategy.

    olivia is not a fan of this partitioning strategy when files are involved.
    In his words:

        Single-threaded Mercurial makes a point of creating and visiting
        files in a fixed order (alphabetical). When creating files in order,
        a typical filesystem is likely to allocate them on nearby regions on
        disk. Thus, when revisiting in the same order, locality is maximized
        and various forms of OS and disk-level caching and read-ahead get a
        chance to work.

        This effect can be quite significant on spinning disks. I discovered it
        circa Mercurial v0.4 when revlogs were named by hashes of filenames.
        Tarring a repo and copying it to another disk effectively randomized
        the revlog ordering on disk by sorting the revlogs by hash and suddenly
        performance of my kernel checkout benchmark dropped by ~10x because the
        "working set" of sectors visited no longer fit in the drive's cache and
        the workload switched from streaming to random I/O.

        What we should really be doing is have workers read filenames from a
        ordered queue. This preserves locality and also keeps any worker from
        getting more than one file out of balance.
    """
    for i in range(nslices):
        yield lst[i::nslices]
