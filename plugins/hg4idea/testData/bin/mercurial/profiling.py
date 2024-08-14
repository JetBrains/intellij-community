# profiling.py - profiling functions
#
# Copyright 2016 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import contextlib

from .i18n import _
from .pycompat import (
    open,
)
from . import (
    encoding,
    error,
    extensions,
    pycompat,
    util,
)


def _loadprofiler(ui, profiler):
    """load profiler extension. return profile method, or None on failure"""
    extname = profiler
    extensions.loadall(ui, whitelist=[extname])
    try:
        mod = extensions.find(extname)
    except KeyError:
        return None
    else:
        return getattr(mod, 'profile', None)


@contextlib.contextmanager
def lsprofile(ui, fp):
    format = ui.config(b'profiling', b'format')
    field = ui.config(b'profiling', b'sort')
    limit = ui.configint(b'profiling', b'limit')
    climit = ui.configint(b'profiling', b'nested')

    if format not in [b'text', b'kcachegrind']:
        ui.warn(_(b"unrecognized profiling format '%s' - Ignored\n") % format)
        format = b'text'

    try:
        from . import lsprof
    except ImportError:
        raise error.Abort(
            _(
                b'lsprof not available - install from '
                b'http://codespeak.net/svn/user/arigo/hack/misc/lsprof/'
            )
        )
    p = lsprof.Profiler()
    p.enable(subcalls=True)
    try:
        yield
    finally:
        p.disable()

        if format == b'kcachegrind':
            from . import lsprofcalltree

            calltree = lsprofcalltree.KCacheGrind(p)
            calltree.output(fp)
        else:
            # format == 'text'
            stats = lsprof.Stats(p.getstats())
            stats.sort(pycompat.sysstr(field))
            stats.pprint(limit=limit, file=fp, climit=climit)
        fp.flush()


@contextlib.contextmanager
def flameprofile(ui, fp):
    try:
        from flamegraph import flamegraph  # pytype: disable=import-error
    except ImportError:
        raise error.Abort(
            _(
                b'flamegraph not available - install from '
                b'https://github.com/evanhempel/python-flamegraph'
            )
        )
    # developer config: profiling.freq
    freq = ui.configint(b'profiling', b'freq')
    filter_ = None
    collapse_recursion = True
    thread = flamegraph.ProfileThread(
        fp, 1.0 / freq, filter_, collapse_recursion
    )
    start_time = util.timer()
    try:
        thread.start()
        yield
    finally:
        thread.stop()
        thread.join()
        m = b'Collected %d stack frames (%d unique) in %2.2f seconds.'
        m %= (
            (
                util.timer() - start_time,
                thread.num_frames(),
                thread.num_frames(unique=True),
            ),
        )
        print(m, flush=True)


@contextlib.contextmanager
def statprofile(ui, fp):
    from . import statprof

    freq = ui.configint(b'profiling', b'freq')
    if freq > 0:
        # Cannot reset when profiler is already active. So silently no-op.
        if statprof.state.profile_level == 0:
            statprof.reset(freq)
    else:
        ui.warn(_(b"invalid sampling frequency '%s' - ignoring\n") % freq)

    track = ui.config(
        b'profiling', b'time-track', pycompat.iswindows and b'cpu' or b'real'
    )
    statprof.start(mechanism=b'thread', track=track)

    try:
        yield
    finally:
        data = statprof.stop()

        profformat = ui.config(b'profiling', b'statformat')

        formats = {
            b'byline': statprof.DisplayFormats.ByLine,
            b'bymethod': statprof.DisplayFormats.ByMethod,
            b'hotpath': statprof.DisplayFormats.Hotpath,
            b'json': statprof.DisplayFormats.Json,
            b'chrome': statprof.DisplayFormats.Chrome,
        }

        if profformat in formats:
            displayformat = formats[profformat]
        else:
            ui.warn(_(b'unknown profiler output format: %s\n') % profformat)
            displayformat = statprof.DisplayFormats.Hotpath

        kwargs = {}

        def fraction(s):
            if isinstance(s, (float, int)):
                return float(s)
            if s.endswith(b'%'):
                v = float(s[:-1]) / 100
            else:
                v = float(s)
            if 0 <= v <= 1:
                return v
            raise ValueError(s)

        if profformat == b'chrome':
            showmin = ui.configwith(fraction, b'profiling', b'showmin', 0.005)
            showmax = ui.configwith(fraction, b'profiling', b'showmax')
            kwargs.update(minthreshold=showmin, maxthreshold=showmax)
        elif profformat == b'hotpath':
            # inconsistent config: profiling.showmin
            limit = ui.configwith(fraction, b'profiling', b'showmin', 0.05)
            kwargs['limit'] = limit
            showtime = ui.configbool(b'profiling', b'showtime')
            kwargs['showtime'] = showtime

        statprof.display(fp, data=data, format=displayformat, **kwargs)
        fp.flush()


class profile:
    """Start profiling.

    Profiling is active when the context manager is active. When the context
    manager exits, profiling results will be written to the configured output.
    """

    def __init__(self, ui, enabled=True):
        self._ui = ui
        self._output = None
        self._fp = None
        self._fpdoclose = True
        self._flushfp = None
        self._profiler = None
        self._enabled = enabled
        self._entered = False
        self._started = False

    def __enter__(self):
        self._entered = True
        if self._enabled:
            self.start()
        return self

    def start(self):
        """Start profiling.

        The profiling will stop at the context exit.

        If the profiler was already started, this has no effect."""
        if not self._entered:
            raise error.ProgrammingError(b'use a context manager to start')
        if self._started:
            return
        self._started = True
        profiler = encoding.environ.get(b'HGPROF')
        proffn = None
        if profiler is None:
            profiler = self._ui.config(b'profiling', b'type')
        if profiler not in (b'ls', b'stat', b'flame'):
            # try load profiler from extension with the same name
            proffn = _loadprofiler(self._ui, profiler)
            if proffn is None:
                self._ui.warn(
                    _(b"unrecognized profiler '%s' - ignored\n") % profiler
                )
                profiler = b'stat'

        self._output = self._ui.config(b'profiling', b'output')

        try:
            if self._output == b'blackbox':
                self._fp = util.stringio()
            elif self._output:
                path = util.expandpath(self._output)
                self._fp = open(path, b'wb')
            elif pycompat.iswindows:
                # parse escape sequence by win32print()
                class uifp:
                    def __init__(self, ui):
                        self._ui = ui

                    def write(self, data):
                        self._ui.write_err(data)

                    def flush(self):
                        self._ui.flush()

                self._fpdoclose = False
                self._fp = uifp(self._ui)
            else:
                self._fpdoclose = False
                self._fp = self._ui.ferr
                # Ensure we've flushed fout before writing to ferr.
                self._flushfp = self._ui.fout

            if proffn is not None:
                pass
            elif profiler == b'ls':
                proffn = lsprofile
            elif profiler == b'flame':
                proffn = flameprofile
            else:
                proffn = statprofile

            self._profiler = proffn(self._ui, self._fp)
            self._profiler.__enter__()
        except:  # re-raises
            self._closefp()
            raise

    def __exit__(self, exception_type, exception_value, traceback):
        propagate = None
        if self._profiler is not None:
            self._uiflush()
            propagate = self._profiler.__exit__(
                exception_type, exception_value, traceback
            )
            if self._output == b'blackbox':
                val = b'Profile:\n%s' % self._fp.getvalue()
                # ui.log treats the input as a format string,
                # so we need to escape any % signs.
                val = val.replace(b'%', b'%%')
                self._ui.log(b'profile', val)
        self._closefp()
        return propagate

    def _closefp(self):
        if self._fpdoclose and self._fp is not None:
            self._fp.close()

    def _uiflush(self):
        if self._flushfp:
            self._flushfp.flush()
