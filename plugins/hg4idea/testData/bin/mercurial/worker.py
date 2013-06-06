# worker.py - master-slave parallelism support
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import errno, os, signal, sys, threading, util

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
        n = int(os.environ['NUMBER_OF_PROCESSORS'])
        if n > 0:
            return n
    except (KeyError, ValueError):
        pass

    return 1

def _numworkers(ui):
    s = ui.config('worker', 'numcpus')
    if s:
        try:
            n = int(s)
            if n >= 1:
                return n
        except ValueError:
            raise util.Abort(_('number of cpus must be an integer'))
    return min(max(countcpus(), 4), 32)

if os.name == 'posix':
    _startupcost = 0.01
else:
    _startupcost = 1e30

def worthwhile(ui, costperop, nops):
    '''try to determine whether the benefit of multiple processes can
    outweigh the cost of starting them'''
    linear = costperop * nops
    workers = _numworkers(ui)
    benefit = linear - (_startupcost * workers + linear / workers)
    return benefit >= 0.15

def worker(ui, costperarg, func, staticargs, args):
    '''run a function, possibly in parallel in multiple worker
    processes.

    returns a progress iterator

    costperarg - cost of a single task

    func - function to run

    staticargs - arguments to pass to every invocation of the function

    args - arguments to split into chunks, to pass to individual
    workers
    '''
    if worthwhile(ui, costperarg, len(args)):
        return _platformworker(ui, func, staticargs, args)
    return func(*staticargs + (args,))

def _posixworker(ui, func, staticargs, args):
    rfd, wfd = os.pipe()
    workers = _numworkers(ui)
    oldhandler = signal.getsignal(signal.SIGINT)
    signal.signal(signal.SIGINT, signal.SIG_IGN)
    pids, problem = [], [0]
    for pargs in partition(args, workers):
        pid = os.fork()
        if pid == 0:
            signal.signal(signal.SIGINT, oldhandler)
            try:
                os.close(rfd)
                for i, item in func(*(staticargs + (pargs,))):
                    os.write(wfd, '%d %s\n' % (i, item))
                os._exit(0)
            except KeyboardInterrupt:
                os._exit(255)
            except: # re-raises (close enough for debugging anyway)
                try:
                    ui.traceback()
                finally:
                    os._exit(255)
        pids.append(pid)
    pids.reverse()
    os.close(wfd)
    fp = os.fdopen(rfd, 'rb', 0)
    def killworkers():
        # if one worker bails, there's no good reason to wait for the rest
        for p in pids:
            try:
                os.kill(p, signal.SIGTERM)
            except OSError, err:
                if err.errno != errno.ESRCH:
                    raise
    def waitforworkers():
        for _ in pids:
            st = _exitstatus(os.wait()[1])
            if st and not problem:
                problem[0] = st
                killworkers()
    t = threading.Thread(target=waitforworkers)
    t.start()
    def cleanup():
        signal.signal(signal.SIGINT, oldhandler)
        t.join()
        status = problem[0]
        if status:
            if status < 0:
                os.kill(os.getpid(), -status)
            sys.exit(status)
    try:
        for line in fp:
            l = line.split(' ', 1)
            yield int(l[0]), l[1][:-1]
    except: # re-raises
        killworkers()
        cleanup()
        raise
    cleanup()

def _posixexitstatus(code):
    '''convert a posix exit status into the same form returned by
    os.spawnv

    returns None if the process was stopped instead of exiting'''
    if os.WIFEXITED(code):
        return os.WEXITSTATUS(code)
    elif os.WIFSIGNALED(code):
        return -os.WTERMSIG(code)

if os.name != 'nt':
    _platformworker = _posixworker
    _exitstatus = _posixexitstatus

def partition(lst, nslices):
    '''partition a list into N slices of equal size'''
    n = len(lst)
    chunk, slop = n / nslices, n % nslices
    end = 0
    for i in xrange(nslices):
        start = end
        end = start + chunk
        if slop:
            end += 1
            slop -= 1
        yield lst[start:end]
