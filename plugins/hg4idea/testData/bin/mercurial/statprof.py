## statprof.py
## Copyright (C) 2012 Bryan O'Sullivan <bos@serpentine.com>
## Copyright (C) 2011 Alex Fraser <alex at phatcore dot com>
## Copyright (C) 2004,2005 Andy Wingo <wingo at pobox dot com>
## Copyright (C) 2001 Rob Browning <rlb at defaultvalue dot org>

## This library is free software; you can redistribute it and/or
## modify it under the terms of the GNU Lesser General Public
## License as published by the Free Software Foundation; either
## version 2.1 of the License, or (at your option) any later version.
##
## This library is distributed in the hope that it will be useful,
## but WITHOUT ANY WARRANTY; without even the implied warranty of
## MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
## Lesser General Public License for more details.
##
## You should have received a copy of the GNU Lesser General Public
## License along with this program; if not, contact:
##
## Free Software Foundation           Voice:  +1-617-542-5942
## 59 Temple Place - Suite 330        Fax:    +1-617-542-2652
## Boston, MA  02111-1307,  USA       gnu@gnu.org

"""
statprof is intended to be a fairly simple statistical profiler for
python. It was ported directly from a statistical profiler for guile,
also named statprof, available from guile-lib [0].

[0] http://wingolog.org/software/guile-lib/statprof/

To start profiling, call statprof.start():
>>> start()

Then run whatever it is that you want to profile, for example:
>>> import test.pystone; test.pystone.pystones()

Then stop the profiling and print out the results:
>>> stop()
>>> display()
  %   cumulative      self
 time    seconds   seconds  name
 26.72      1.40      0.37  pystone.py:79:Proc0
 13.79      0.56      0.19  pystone.py:133:Proc1
 13.79      0.19      0.19  pystone.py:208:Proc8
 10.34      0.16      0.14  pystone.py:229:Func2
  6.90      0.10      0.10  pystone.py:45:__init__
  4.31      0.16      0.06  pystone.py:53:copy
    ...

All of the numerical data is statistically approximate. In the
following column descriptions, and in all of statprof, "time" refers
to execution time (both user and system), not wall clock time.

% time
    The percent of the time spent inside the procedure itself (not
    counting children).

cumulative seconds
    The total number of seconds spent in the procedure, including
    children.

self seconds
    The total number of seconds spent in the procedure itself (not
    counting children).

name
    The name of the procedure.

By default statprof keeps the data collected from previous runs. If you
want to clear the collected data, call reset():
>>> reset()

reset() can also be used to change the sampling frequency from the
default of 1000 Hz. For example, to tell statprof to sample 50 times a
second:
>>> reset(50)

This means that statprof will sample the call stack after every 1/50 of
a second of user + system time spent running on behalf of the python
process. When your process is idle (for example, blocking in a read(),
as is the case at the listener), the clock does not advance. For this
reason statprof is not currently not suitable for profiling io-bound
operations.

The profiler uses the hash of the code object itself to identify the
procedures, so it won't confuse different procedures with the same name.
They will show up as two different rows in the output.

Right now the profiler is quite simplistic.  I cannot provide
call-graphs or other higher level information.  What you see in the
table is pretty much all there is. Patches are welcome :-)


Threading
---------

Because signals only get delivered to the main thread in Python,
statprof only profiles the main thread. However because the time
reporting function uses per-process timers, the results can be
significantly off if other threads' work patterns are not similar to the
main thread's work patterns.
"""
# no-check-code
from __future__ import absolute_import, division, print_function

import collections
import contextlib
import getopt
import inspect
import json
import os
import signal
import sys
import threading
import time

from .pycompat import open
from . import (
    encoding,
    pycompat,
)

defaultdict = collections.defaultdict
contextmanager = contextlib.contextmanager

__all__ = [b'start', b'stop', b'reset', b'display', b'profile']

skips = {
    "util.py:check",
    "extensions.py:closure",
    "color.py:colorcmd",
    "dispatch.py:checkargs",
    "dispatch.py:<lambda>",
    "dispatch.py:_runcatch",
    "dispatch.py:_dispatch",
    "dispatch.py:_runcommand",
    "pager.py:pagecmd",
    "dispatch.py:run",
    "dispatch.py:dispatch",
    "dispatch.py:runcommand",
    "hg.py:<module>",
    "evolve.py:warnobserrors",
}

###########################################################################
## Utils


def clock():
    times = os.times()
    return (times[0] + times[1], times[4])


###########################################################################
## Collection data structures


class ProfileState(object):
    def __init__(self, frequency=None):
        self.reset(frequency)
        self.track = b'cpu'

    def reset(self, frequency=None):
        # total so far
        self.accumulated_time = (0.0, 0.0)
        # start_time when timer is active
        self.last_start_time = None
        # a float
        if frequency:
            self.sample_interval = 1.0 / frequency
        elif not pycompat.hasattr(self, 'sample_interval'):
            # default to 1000 Hz
            self.sample_interval = 1.0 / 1000.0
        else:
            # leave the frequency as it was
            pass
        self.remaining_prof_time = None
        # for user start/stop nesting
        self.profile_level = 0

        self.samples = []

    def accumulate_time(self, stop_time):
        increment = (
            stop_time[0] - self.last_start_time[0],
            stop_time[1] - self.last_start_time[1],
        )
        self.accumulated_time = (
            self.accumulated_time[0] + increment[0],
            self.accumulated_time[1] + increment[1],
        )

    def seconds_per_sample(self):
        return self.accumulated_time[self.timeidx] / len(self.samples)

    @property
    def timeidx(self):
        if self.track == b'real':
            return 1
        return 0


state = ProfileState()


class CodeSite(object):
    cache = {}

    __slots__ = ('path', 'lineno', 'function', 'source')

    def __init__(self, path, lineno, function):
        assert isinstance(path, bytes)
        self.path = path
        self.lineno = lineno
        assert isinstance(function, bytes)
        self.function = function
        self.source = None

    def __eq__(self, other):
        try:
            return self.lineno == other.lineno and self.path == other.path
        except:
            return False

    def __hash__(self):
        return hash((self.lineno, self.path))

    @classmethod
    def get(cls, path, lineno, function):
        k = (path, lineno)
        try:
            return cls.cache[k]
        except KeyError:
            v = cls(path, lineno, function)
            cls.cache[k] = v
            return v

    def getsource(self, length):
        if self.source is None:
            lineno = self.lineno - 1
            try:
                with open(self.path, b'rb') as fp:
                    for i, line in enumerate(fp):
                        if i == lineno:
                            self.source = line.strip()
                            break
            except:
                pass
            if self.source is None:
                self.source = b''

        source = self.source
        if len(source) > length:
            source = source[: (length - 3)] + b"..."
        return source

    def filename(self):
        return os.path.basename(self.path)

    def skipname(self):
        return '%s:%s' % (self.filename(), self.function)


class Sample(object):
    __slots__ = ('stack', 'time')

    def __init__(self, stack, time):
        self.stack = stack
        self.time = time

    @classmethod
    def from_frame(cls, frame, time):
        stack = []

        while frame:
            stack.append(
                CodeSite.get(
                    pycompat.sysbytes(frame.f_code.co_filename),
                    frame.f_lineno,
                    pycompat.sysbytes(frame.f_code.co_name),
                )
            )
            frame = frame.f_back

        return Sample(stack, time)


###########################################################################
## SIGPROF handler


def profile_signal_handler(signum, frame):
    if state.profile_level > 0:
        now = clock()
        state.accumulate_time(now)

        timestamp = state.accumulated_time[state.timeidx]
        state.samples.append(Sample.from_frame(frame, timestamp))

        signal.setitimer(signal.ITIMER_PROF, state.sample_interval, 0.0)
        state.last_start_time = now


stopthread = threading.Event()


def samplerthread(tid):
    while not stopthread.is_set():
        now = clock()
        state.accumulate_time(now)

        frame = sys._current_frames()[tid]

        timestamp = state.accumulated_time[state.timeidx]
        state.samples.append(Sample.from_frame(frame, timestamp))

        state.last_start_time = now
        time.sleep(state.sample_interval)

    stopthread.clear()


###########################################################################
## Profiling API


def is_active():
    return state.profile_level > 0


lastmechanism = None


def start(mechanism=b'thread', track=b'cpu'):
    '''Install the profiling signal handler, and start profiling.'''
    state.track = track  # note: nesting different mode won't work
    state.profile_level += 1
    if state.profile_level == 1:
        state.last_start_time = clock()
        rpt = state.remaining_prof_time
        state.remaining_prof_time = None

        global lastmechanism
        lastmechanism = mechanism

        if mechanism == b'signal':
            signal.signal(signal.SIGPROF, profile_signal_handler)
            signal.setitimer(
                signal.ITIMER_PROF, rpt or state.sample_interval, 0.0
            )
        elif mechanism == b'thread':
            frame = inspect.currentframe()
            tid = [k for k, f in sys._current_frames().items() if f == frame][0]
            state.thread = threading.Thread(
                target=samplerthread, args=(tid,), name="samplerthread"
            )
            state.thread.start()


def stop():
    '''Stop profiling, and uninstall the profiling signal handler.'''
    state.profile_level -= 1
    if state.profile_level == 0:
        if lastmechanism == b'signal':
            rpt = signal.setitimer(signal.ITIMER_PROF, 0.0, 0.0)
            signal.signal(signal.SIGPROF, signal.SIG_IGN)
            state.remaining_prof_time = rpt[0]
        elif lastmechanism == b'thread':
            stopthread.set()
            state.thread.join()

        state.accumulate_time(clock())
        state.last_start_time = None
        statprofpath = encoding.environ.get(b'STATPROF_DEST')
        if statprofpath:
            save_data(statprofpath)

    return state


def save_data(path):
    with open(path, b'w+') as file:
        file.write(b"%f %f\n" % state.accumulated_time)
        for sample in state.samples:
            time = sample.time
            stack = sample.stack
            sites = [
                b'\1'.join([s.path, b'%d' % s.lineno, s.function])
                for s in stack
            ]
            file.write(b"%d\0%s\n" % (time, b'\0'.join(sites)))


def load_data(path):
    lines = open(path, b'rb').read().splitlines()

    state.accumulated_time = [float(value) for value in lines[0].split()]
    state.samples = []
    for line in lines[1:]:
        parts = line.split(b'\0')
        time = float(parts[0])
        rawsites = parts[1:]
        sites = []
        for rawsite in rawsites:
            siteparts = rawsite.split(b'\1')
            sites.append(
                CodeSite.get(siteparts[0], int(siteparts[1]), siteparts[2])
            )

        state.samples.append(Sample(sites, time))


def reset(frequency=None):
    """Clear out the state of the profiler.  Do not call while the
    profiler is running.

    The optional frequency argument specifies the number of samples to
    collect per second."""
    assert state.profile_level == 0, b"Can't reset() while statprof is running"
    CodeSite.cache.clear()
    state.reset(frequency)


@contextmanager
def profile():
    start()
    try:
        yield
    finally:
        stop()
        display()


###########################################################################
## Reporting API


class SiteStats(object):
    def __init__(self, site):
        self.site = site
        self.selfcount = 0
        self.totalcount = 0

    def addself(self):
        self.selfcount += 1

    def addtotal(self):
        self.totalcount += 1

    def selfpercent(self):
        return self.selfcount / len(state.samples) * 100

    def totalpercent(self):
        return self.totalcount / len(state.samples) * 100

    def selfseconds(self):
        return self.selfcount * state.seconds_per_sample()

    def totalseconds(self):
        return self.totalcount * state.seconds_per_sample()

    @classmethod
    def buildstats(cls, samples):
        stats = {}

        for sample in samples:
            for i, site in enumerate(sample.stack):
                sitestat = stats.get(site)
                if not sitestat:
                    sitestat = SiteStats(site)
                    stats[site] = sitestat

                sitestat.addtotal()

                if i == 0:
                    sitestat.addself()

        return [s for s in pycompat.itervalues(stats)]


class DisplayFormats:
    ByLine = 0
    ByMethod = 1
    AboutMethod = 2
    Hotpath = 3
    FlameGraph = 4
    Json = 5
    Chrome = 6


def display(fp=None, format=3, data=None, **kwargs):
    '''Print statistics, either to stdout or the given file object.'''
    if data is None:
        data = state

    if fp is None:
        import sys

        fp = sys.stdout
    if len(data.samples) == 0:
        fp.write(b'No samples recorded.\n')
        return

    if format == DisplayFormats.ByLine:
        display_by_line(data, fp)
    elif format == DisplayFormats.ByMethod:
        display_by_method(data, fp)
    elif format == DisplayFormats.AboutMethod:
        display_about_method(data, fp, **kwargs)
    elif format == DisplayFormats.Hotpath:
        display_hotpath(data, fp, **kwargs)
    elif format == DisplayFormats.FlameGraph:
        write_to_flame(data, fp, **kwargs)
    elif format == DisplayFormats.Json:
        write_to_json(data, fp)
    elif format == DisplayFormats.Chrome:
        write_to_chrome(data, fp, **kwargs)
    else:
        raise Exception(b"Invalid display format")

    if format not in (DisplayFormats.Json, DisplayFormats.Chrome):
        fp.write(b'---\n')
        fp.write(b'Sample count: %d\n' % len(data.samples))
        fp.write(b'Total time: %f seconds (%f wall)\n' % data.accumulated_time)


def display_by_line(data, fp):
    """Print the profiler data with each sample line represented
    as one row in a table.  Sorted by self-time per line."""
    stats = SiteStats.buildstats(data.samples)
    stats.sort(reverse=True, key=lambda x: x.selfseconds())

    fp.write(
        b'%5.5s %10.10s   %7.7s  %-8.8s\n'
        % (b'%  ', b'cumulative', b'self', b'')
    )
    fp.write(
        b'%5.5s  %9.9s  %8.8s  %-8.8s\n'
        % (b"time", b"seconds", b"seconds", b"name")
    )

    for stat in stats:
        site = stat.site
        sitelabel = b'%s:%d:%s' % (site.filename(), site.lineno, site.function)
        fp.write(
            b'%6.2f %9.2f %9.2f  %s\n'
            % (
                stat.selfpercent(),
                stat.totalseconds(),
                stat.selfseconds(),
                sitelabel,
            )
        )


def display_by_method(data, fp):
    """Print the profiler data with each sample function represented
    as one row in a table.  Important lines within that function are
    output as nested rows.  Sorted by self-time per line."""
    fp.write(
        b'%5.5s %10.10s   %7.7s  %-8.8s\n'
        % (b'%  ', b'cumulative', b'self', b'')
    )
    fp.write(
        b'%5.5s  %9.9s  %8.8s  %-8.8s\n'
        % (b"time", b"seconds", b"seconds", b"name")
    )

    stats = SiteStats.buildstats(data.samples)

    grouped = defaultdict(list)
    for stat in stats:
        grouped[stat.site.filename() + b":" + stat.site.function].append(stat)

    # compute sums for each function
    functiondata = []
    for fname, sitestats in pycompat.iteritems(grouped):
        total_cum_sec = 0
        total_self_sec = 0
        total_percent = 0
        for stat in sitestats:
            total_cum_sec += stat.totalseconds()
            total_self_sec += stat.selfseconds()
            total_percent += stat.selfpercent()

        functiondata.append(
            (fname, total_cum_sec, total_self_sec, total_percent, sitestats)
        )

    # sort by total self sec
    functiondata.sort(reverse=True, key=lambda x: x[2])

    for function in functiondata:
        if function[3] < 0.05:
            continue
        fp.write(
            b'%6.2f %9.2f %9.2f  %s\n'
            % (
                function[3],  # total percent
                function[1],  # total cum sec
                function[2],  # total self sec
                function[0],
            )
        )  # file:function

        function[4].sort(reverse=True, key=lambda i: i.selfseconds())
        for stat in function[4]:
            # only show line numbers for significant locations (>1% time spent)
            if stat.selfpercent() > 1:
                source = stat.site.getsource(25)
                if sys.version_info.major >= 3 and not isinstance(
                    source, bytes
                ):
                    source = pycompat.bytestr(source)

                stattuple = (
                    stat.selfpercent(),
                    stat.selfseconds(),
                    stat.site.lineno,
                    source,
                )

                fp.write(b'%33.0f%% %6.2f   line %d: %s\n' % stattuple)


def display_about_method(data, fp, function=None, **kwargs):
    if function is None:
        raise Exception(b"Invalid function")

    filename = None
    if b':' in function:
        filename, function = function.split(b':')

    relevant_samples = 0
    parents = {}
    children = {}

    for sample in data.samples:
        for i, site in enumerate(sample.stack):
            if site.function == function and (
                not filename or site.filename() == filename
            ):
                relevant_samples += 1
                if i != len(sample.stack) - 1:
                    parent = sample.stack[i + 1]
                    if parent in parents:
                        parents[parent] = parents[parent] + 1
                    else:
                        parents[parent] = 1

                if site in children:
                    children[site] = children[site] + 1
                else:
                    children[site] = 1

    parents = [(parent, count) for parent, count in pycompat.iteritems(parents)]
    parents.sort(reverse=True, key=lambda x: x[1])
    for parent, count in parents:
        fp.write(
            b'%6.2f%%   %s:%s   line %s: %s\n'
            % (
                count / relevant_samples * 100,
                pycompat.fsencode(parent.filename()),
                pycompat.sysbytes(parent.function),
                parent.lineno,
                pycompat.sysbytes(parent.getsource(50)),
            )
        )

    stats = SiteStats.buildstats(data.samples)
    stats = [
        s
        for s in stats
        if s.site.function == function
        and (not filename or s.site.filename() == filename)
    ]

    total_cum_sec = 0
    total_self_sec = 0
    total_self_percent = 0
    total_cum_percent = 0
    for stat in stats:
        total_cum_sec += stat.totalseconds()
        total_self_sec += stat.selfseconds()
        total_self_percent += stat.selfpercent()
        total_cum_percent += stat.totalpercent()

    fp.write(
        b'\n    %s:%s    Total: %0.2fs (%0.2f%%)    Self: %0.2fs (%0.2f%%)\n\n'
        % (
            pycompat.sysbytes(filename or b'___'),
            pycompat.sysbytes(function),
            total_cum_sec,
            total_cum_percent,
            total_self_sec,
            total_self_percent,
        )
    )

    children = [(child, count) for child, count in pycompat.iteritems(children)]
    children.sort(reverse=True, key=lambda x: x[1])
    for child, count in children:
        fp.write(
            b'        %6.2f%%   line %s: %s\n'
            % (
                count / relevant_samples * 100,
                child.lineno,
                pycompat.sysbytes(child.getsource(50)),
            )
        )


def display_hotpath(data, fp, limit=0.05, **kwargs):
    class HotNode(object):
        def __init__(self, site):
            self.site = site
            self.count = 0
            self.children = {}

        def add(self, stack, time):
            self.count += time
            site = stack[0]
            child = self.children.get(site)
            if not child:
                child = HotNode(site)
                self.children[site] = child

            if len(stack) > 1:
                i = 1
                # Skip boiler plate parts of the stack
                while i < len(stack) and stack[i].skipname() in skips:
                    i += 1
                if i < len(stack):
                    child.add(stack[i:], time)
            else:
                # Normally this is done by the .add() calls
                child.count += time

    root = HotNode(None)
    lasttime = data.samples[0].time
    for sample in data.samples:
        root.add(sample.stack[::-1], sample.time - lasttime)
        lasttime = sample.time
    showtime = kwargs.get('showtime', True)

    def _write(node, depth, multiple_siblings):
        site = node.site
        visiblechildren = [
            c
            for c in pycompat.itervalues(node.children)
            if c.count >= (limit * root.count)
        ]
        if site:
            indent = depth * 2 - 1
            filename = (site.filename() + b':').ljust(15)
            function = site.function

            # lots of string formatting
            listpattern = (
                b''.ljust(indent)
                + (b'\\' if multiple_siblings else b'|')
                + b' %4.1f%%'
                + (b' %5.2fs' % node.count if showtime else b'')
                + b'  %s %s'
            )
            liststring = listpattern % (
                node.count / root.count * 100,
                filename,
                function,
            )
            # 4 to account for the word 'line'
            spacing_len = max(4, 55 - len(liststring))
            prefix = b''
            if spacing_len == 4:
                prefix = b', '

            codepattern = b'%s%s %d: %s%s'
            codestring = codepattern % (
                prefix,
                b'line'.rjust(spacing_len),
                site.lineno,
                b''.ljust(max(0, 4 - len(str(site.lineno)))),
                site.getsource(30),
            )

            finalstring = liststring + codestring
            childrensamples = sum(
                [c.count for c in pycompat.itervalues(node.children)]
            )
            # Make frames that performed more than 10% of the operation red
            if node.count - childrensamples > (0.1 * root.count):
                finalstring = b'\033[91m' + finalstring + b'\033[0m'
            # Make frames that didn't actually perform work dark grey
            elif node.count - childrensamples == 0:
                finalstring = b'\033[90m' + finalstring + b'\033[0m'
            fp.write(finalstring + b'\n')

        newdepth = depth
        if len(visiblechildren) > 1 or multiple_siblings:
            newdepth += 1

        visiblechildren.sort(reverse=True, key=lambda x: x.count)
        for child in visiblechildren:
            _write(child, newdepth, len(visiblechildren) > 1)

    if root.count > 0:
        _write(root, 0, False)


def write_to_flame(data, fp, scriptpath=None, outputfile=None, **kwargs):
    if scriptpath is None:
        scriptpath = encoding.environ[b'HOME'] + b'/flamegraph.pl'
    if not os.path.exists(scriptpath):
        fp.write(b'error: missing %s\n' % scriptpath)
        fp.write(b'get it here: https://github.com/brendangregg/FlameGraph\n')
        return

    lines = {}
    for sample in data.samples:
        sites = [s.function for s in sample.stack]
        sites.reverse()
        line = b';'.join(sites)
        if line in lines:
            lines[line] = lines[line] + 1
        else:
            lines[line] = 1

    fd, path = pycompat.mkstemp()

    with open(path, b"w+") as file:
        for line, count in pycompat.iteritems(lines):
            file.write(b"%s %d\n" % (line, count))

    if outputfile is None:
        outputfile = b'~/flamegraph.svg'

    os.system(b"perl ~/flamegraph.pl %s > %s" % (path, outputfile))
    fp.write(b'Written to %s\n' % outputfile)


_pathcache = {}


def simplifypath(path):
    """Attempt to make the path to a Python module easier to read by
    removing whatever part of the Python search path it was found
    on."""

    if path in _pathcache:
        return _pathcache[path]
    hgpath = encoding.__file__.rsplit(os.sep, 2)[0]
    for p in [hgpath] + sys.path:
        prefix = p + os.sep
        if path.startswith(prefix):
            path = path[len(prefix) :]
            break
    _pathcache[path] = path
    return path


def write_to_json(data, fp):
    samples = []

    for sample in data.samples:
        stack = []

        for frame in sample.stack:
            stack.append(
                (
                    pycompat.sysstr(frame.path),
                    frame.lineno,
                    pycompat.sysstr(frame.function),
                )
            )

        samples.append((sample.time, stack))

    data = json.dumps(samples)
    if not isinstance(data, bytes):
        data = data.encode('utf-8')

    fp.write(data)


def write_to_chrome(data, fp, minthreshold=0.005, maxthreshold=0.999):
    samples = []
    laststack = collections.deque()
    lastseen = collections.deque()

    # The Chrome tracing format allows us to use a compact stack
    # representation to save space. It's fiddly but worth it.
    # We maintain a bijection between stack and ID.
    stack2id = {}
    id2stack = []  # will eventually be rendered

    def stackid(stack):
        if not stack:
            return
        if stack in stack2id:
            return stack2id[stack]
        parent = stackid(stack[1:])
        myid = len(stack2id)
        stack2id[stack] = myid
        id2stack.append(dict(category=stack[0][0], name='%s %s' % stack[0]))
        if parent is not None:
            id2stack[-1].update(parent=parent)
        return myid

    # The sampling profiler can sample multiple times without
    # advancing the clock, potentially causing the Chrome trace viewer
    # to render single-pixel columns that we cannot zoom in on.  We
    # work around this by pretending that zero-duration samples are a
    # millisecond in length.

    clamp = 0.001

    # We provide knobs that by default attempt to filter out stack
    # frames that are too noisy:
    #
    # * A few take almost all execution time. These are usually boring
    #   setup functions, giving a stack that is deep but uninformative.
    #
    # * Numerous samples take almost no time, but introduce lots of
    #   noisy, oft-deep "spines" into a rendered profile.

    blacklist = set()
    totaltime = data.samples[-1].time - data.samples[0].time
    minthreshold = totaltime * minthreshold
    maxthreshold = max(totaltime * maxthreshold, clamp)

    def poplast():
        oldsid = stackid(tuple(laststack))
        oldcat, oldfunc = laststack.popleft()
        oldtime, oldidx = lastseen.popleft()
        duration = sample.time - oldtime
        if minthreshold <= duration <= maxthreshold:
            # ensure no zero-duration events
            sampletime = max(oldtime + clamp, sample.time)
            samples.append(
                dict(
                    ph='E',
                    name=oldfunc,
                    cat=oldcat,
                    sf=oldsid,
                    ts=sampletime * 1e6,
                    pid=0,
                )
            )
        else:
            blacklist.add(oldidx)

    # Much fiddling to synthesize correctly(ish) nested begin/end
    # events given only stack snapshots.

    for sample in data.samples:
        stack = tuple(
            (
                (
                    '%s:%d'
                    % (simplifypath(pycompat.sysstr(frame.path)), frame.lineno),
                    pycompat.sysstr(frame.function),
                )
                for frame in sample.stack
            )
        )
        qstack = collections.deque(stack)
        if laststack == qstack:
            continue
        while laststack and qstack and laststack[-1] == qstack[-1]:
            laststack.pop()
            qstack.pop()
        while laststack:
            poplast()
        for f in reversed(qstack):
            lastseen.appendleft((sample.time, len(samples)))
            laststack.appendleft(f)
            path, name = f
            sid = stackid(tuple(laststack))
            samples.append(
                dict(
                    ph='B',
                    name=name,
                    cat=path,
                    ts=sample.time * 1e6,
                    sf=sid,
                    pid=0,
                )
            )
        laststack = collections.deque(stack)
    while laststack:
        poplast()
    events = [
        sample for idx, sample in enumerate(samples) if idx not in blacklist
    ]
    frames = collections.OrderedDict(
        (str(k), v) for (k, v) in enumerate(id2stack)
    )
    data = json.dumps(dict(traceEvents=events, stackFrames=frames), indent=1)
    if not isinstance(data, bytes):
        data = data.encode('utf-8')
    fp.write(data)
    fp.write(b'\n')


def printusage():
    print(
        r"""
The statprof command line allows you to inspect the last profile's results in
the following forms:

usage:
    hotpath [-l --limit percent]
        Shows a graph of calls with the percent of time each takes.
        Red calls take over 10%% of the total time themselves.
    lines
        Shows the actual sampled lines.
    functions
        Shows the samples grouped by function.
    function [filename:]functionname
        Shows the callers and callees of a particular function.
    flame [-s --script-path] [-o --output-file path]
        Writes out a flamegraph to output-file (defaults to ~/flamegraph.svg)
        Requires that ~/flamegraph.pl exist.
        (Specify alternate script path with --script-path.)"""
    )


def main(argv=None):
    if argv is None:
        argv = sys.argv

    if len(argv) == 1:
        printusage()
        return 0

    displayargs = {}

    optstart = 2
    displayargs[b'function'] = None
    if argv[1] == 'hotpath':
        displayargs[b'format'] = DisplayFormats.Hotpath
    elif argv[1] == 'lines':
        displayargs[b'format'] = DisplayFormats.ByLine
    elif argv[1] == 'functions':
        displayargs[b'format'] = DisplayFormats.ByMethod
    elif argv[1] == 'function':
        displayargs[b'format'] = DisplayFormats.AboutMethod
        displayargs[b'function'] = argv[2]
        optstart = 3
    elif argv[1] == 'flame':
        displayargs[b'format'] = DisplayFormats.FlameGraph
    else:
        printusage()
        return 0

    # process options
    try:
        opts, args = pycompat.getoptb(
            sys.argv[optstart:],
            b"hl:f:o:p:",
            [b"help", b"limit=", b"file=", b"output-file=", b"script-path="],
        )
    except getopt.error as msg:
        print(msg)
        printusage()
        return 2

    displayargs[b'limit'] = 0.05
    path = None
    for o, value in opts:
        if o in ("-l", "--limit"):
            displayargs[b'limit'] = float(value)
        elif o in ("-f", "--file"):
            path = value
        elif o in ("-o", "--output-file"):
            displayargs[b'outputfile'] = value
        elif o in ("-p", "--script-path"):
            displayargs[b'scriptpath'] = value
        elif o in ("-h", "help"):
            printusage()
            return 0
        else:
            assert False, b"unhandled option %s" % o

    if not path:
        print('must specify --file to load')
        return 1

    load_data(path=path)

    display(**pycompat.strkwargs(displayargs))

    return 0


if __name__ == "__main__":
    sys.exit(main())
