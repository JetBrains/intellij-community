from __future__ import absolute_import, print_function

import _lsprof
import sys

from .pycompat import getattr
from . import pycompat

Profiler = _lsprof.Profiler

# PyPy doesn't expose profiler_entry from the module.
profiler_entry = getattr(_lsprof, 'profiler_entry', None)

__all__ = [b'profile', b'Stats']


def profile(f, *args, **kwds):
    """XXX docstring"""
    p = Profiler()
    p.enable(subcalls=True, builtins=True)
    try:
        f(*args, **kwds)
    finally:
        p.disable()
    return Stats(p.getstats())


class Stats(object):
    """XXX docstring"""

    def __init__(self, data):
        self.data = data

    def sort(self, crit="inlinetime"):
        """XXX docstring"""
        # profiler_entries isn't defined when running under PyPy.
        if profiler_entry:
            if crit not in profiler_entry.__dict__:
                raise ValueError(b"Can't sort by %s" % crit)
        elif self.data and not getattr(self.data[0], crit, None):
            raise ValueError(b"Can't sort by %s" % crit)

        self.data.sort(key=lambda x: getattr(x, crit), reverse=True)
        for e in self.data:
            if e.calls:
                e.calls.sort(key=lambda x: getattr(x, crit), reverse=True)

    def pprint(self, top=None, file=None, limit=None, climit=None):
        """XXX docstring"""
        if file is None:
            file = sys.stdout
        d = self.data
        if top is not None:
            d = d[:top]
        cols = b"% 12d %12d %11.4f %11.4f   %s\n"
        hcols = b"% 12s %12s %12s %12s %s\n"
        file.write(
            hcols
            % (
                b"CallCount",
                b"Recursive",
                b"Total(s)",
                b"Inline(s)",
                b"module:lineno(function)",
            )
        )
        count = 0
        for e in d:
            file.write(
                cols
                % (
                    e.callcount,
                    e.reccallcount,
                    e.totaltime,
                    e.inlinetime,
                    label(e.code),
                )
            )
            count += 1
            if limit is not None and count == limit:
                return
            ccount = 0
            if climit and e.calls:
                for se in e.calls:
                    file.write(
                        cols
                        % (
                            se.callcount,
                            se.reccallcount,
                            se.totaltime,
                            se.inlinetime,
                            b"    %s" % label(se.code),
                        )
                    )
                    count += 1
                    ccount += 1
                    if limit is not None and count == limit:
                        return
                    if climit is not None and ccount == climit:
                        break

    def freeze(self):
        """Replace all references to code objects with string
        descriptions; this makes it possible to pickle the instance."""

        # this code is probably rather ickier than it needs to be!
        for i in range(len(self.data)):
            e = self.data[i]
            if not isinstance(e.code, str):
                self.data[i] = type(e)((label(e.code),) + e[1:])
            if e.calls:
                for j in range(len(e.calls)):
                    se = e.calls[j]
                    if not isinstance(se.code, str):
                        e.calls[j] = type(se)((label(se.code),) + se[1:])


_fn2mod = {}


def label(code):
    if isinstance(code, str):
        if sys.version_info.major >= 3:
            code = code.encode('latin-1')
        return code
    try:
        mname = _fn2mod[code.co_filename]
    except KeyError:
        for k, v in list(pycompat.iteritems(sys.modules)):
            if v is None:
                continue
            if not isinstance(getattr(v, '__file__', None), str):
                continue
            if v.__file__.startswith(code.co_filename):
                mname = _fn2mod[code.co_filename] = k
                break
        else:
            mname = _fn2mod[code.co_filename] = '<%s>' % code.co_filename

    res = '%s:%d(%s)' % (mname, code.co_firstlineno, code.co_name)

    if sys.version_info.major >= 3:
        res = res.encode('latin-1')

    return res
