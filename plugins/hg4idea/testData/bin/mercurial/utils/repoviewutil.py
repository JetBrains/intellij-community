# repoviewutil.py - constaints data relevant to repoview.py and other module
#
# Copyright 2012 Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#                Logilab SA        <contact@logilab.fr>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from .. import error

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


def get_ordered_subset():
    """return a list of subset name from dependencies to dependents"""
    _unfinalized = set(subsettable.values())
    ordered = []

    # the subset table is expected to be small so we do the stupid N² version
    # of the algorithm
    while _unfinalized:
        this_level = []
        for candidate in _unfinalized:
            dependency = subsettable.get(candidate)
            if dependency not in _unfinalized:
                this_level.append(candidate)

        if not this_level:
            msg = "cyclic dependencies in repoview subset %r"
            msg %= subsettable
            raise error.ProgrammingError(msg)

        this_level.sort(key=lambda x: x if x is not None else '')

        ordered.extend(this_level)
        _unfinalized.difference_update(this_level)

    return ordered
