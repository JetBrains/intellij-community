# repoviewutil.py - constaints data relevant to repoview.py and other module
#
# Copyright 2012 Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#                Logilab SA        <contact@logilab.fr>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

### Nearest subset relation
# Nearest subset of filter X is a filter Y so that:
# * Y is included in X,
# * X - Y is as small as possible.
# This create and ordering used for branchmap purpose.
# the ordering may be partial
subsettable = {
    None: b'visible',
    b'visible-hidden': b'visible',
    b'visible': b'served',
    b'served.hidden': b'served',
    b'served': b'immutable',
    b'immutable': b'base',
}
