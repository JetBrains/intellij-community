"""
lsprofcalltree.py - lsprof output which is readable by kcachegrind

Authors:
    * David Allouche <david <at> allouche.net>
    * Jp Calderone & Itamar Shtull-Trauring
    * Johan Dahlin

This software may be used and distributed according to the terms
of the GNU General Public License, incorporated herein by reference.
"""

from __future__ import absolute_import

from . import pycompat


def label(code):
    if isinstance(code, str):
        # built-in functions ('~' sorts at the end)
        return b'~' + pycompat.sysbytes(code)
    else:
        return b'%s %s:%d' % (
            pycompat.sysbytes(code.co_name),
            pycompat.sysbytes(code.co_filename),
            code.co_firstlineno,
        )


class KCacheGrind(object):
    def __init__(self, profiler):
        self.data = profiler.getstats()
        self.out_file = None

    def output(self, out_file):
        self.out_file = out_file
        out_file.write(b'events: Ticks\n')
        self._print_summary()
        for entry in self.data:
            self._entry(entry)

    def _print_summary(self):
        max_cost = 0
        for entry in self.data:
            totaltime = int(entry.totaltime * 1000)
            max_cost = max(max_cost, totaltime)
        self.out_file.write(b'summary: %d\n' % max_cost)

    def _entry(self, entry):
        out_file = self.out_file

        code = entry.code
        if isinstance(code, str):
            out_file.write(b'fi=~\n')
        else:
            out_file.write(b'fi=%s\n' % pycompat.sysbytes(code.co_filename))

        out_file.write(b'fn=%s\n' % label(code))

        inlinetime = int(entry.inlinetime * 1000)
        if isinstance(code, str):
            out_file.write(b'0 %d\n' % inlinetime)
        else:
            out_file.write(b'%d %d\n' % (code.co_firstlineno, inlinetime))

        # recursive calls are counted in entry.calls
        if entry.calls:
            calls = entry.calls
        else:
            calls = []

        if isinstance(code, str):
            lineno = 0
        else:
            lineno = code.co_firstlineno

        for subentry in calls:
            self._subentry(lineno, subentry)

        out_file.write(b'\n')

    def _subentry(self, lineno, subentry):
        out_file = self.out_file
        code = subentry.code
        out_file.write(b'cfn=%s\n' % label(code))
        if isinstance(code, str):
            out_file.write(b'cfi=~\n')
            out_file.write(b'calls=%d 0\n' % subentry.callcount)
        else:
            out_file.write(b'cfi=%s\n' % pycompat.sysbytes(code.co_filename))
            out_file.write(
                b'calls=%d %d\n' % (subentry.callcount, code.co_firstlineno)
            )

        totaltime = int(subentry.totaltime * 1000)
        out_file.write(b'%d %d\n' % (lineno, totaltime))
