# Support code for event tracing in Mercurial. Lives in demandimport
# so it can also be used in demandimport.
#
# Copyright 2018 Google LLC.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from __future__ import absolute_import

import contextlib
import os

_pipe = None
_checked = False
_session = 'none'


def _isactive():
    global _pipe, _session, _checked
    if _pipe is None:
        if _checked:
            return False
        _checked = True
        if 'HGCATAPULTSERVERPIPE' not in os.environ:
            return False
        _pipe = open(os.environ['HGCATAPULTSERVERPIPE'], 'w', 1)
        _session = os.environ.get('HGCATAPULTSESSION', 'none')
    return True


@contextlib.contextmanager
def log(whencefmt, *whenceargs):
    if not _isactive():
        yield
        return
    whence = whencefmt % whenceargs
    try:
        # Both writes to the pipe are wrapped in try/except to ignore
        # errors, as we can see mysterious errors in here if the pager
        # is active. Presumably other conditions could trigger
        # problems too.
        try:
            _pipe.write('START %s %s\n' % (_session, whence))
        except IOError:
            pass
        yield
    finally:
        try:
            _pipe.write('END %s %s\n' % (_session, whence))
        except IOError:
            pass


def counter(label, amount, *labelargs):
    if not _isactive():
        return
    l = label % labelargs
    # See above in log() for why this is in a try/except.
    try:
        _pipe.write('COUNTER %s %d %s\n' % (_session, amount, l))
    except IOError:
        pass
