# shallowstore.py - shallow store for interacting with shallow repos
#
# Copyright 2013 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


def wrapstore(store):
    class shallowstore(store.__class__):
        def __contains__(self, path):
            # Assume it exists
            return True

    store.__class__ = shallowstore

    return store
