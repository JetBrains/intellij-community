# mergeutil.py - help for merge processing in mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from .i18n import _

from . import error


def checkunresolved(ms):
    if ms.unresolvedcount():
        raise error.StateError(
            _(b"unresolved merge conflicts (see 'hg help resolve')")
        )
