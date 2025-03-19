# narrowdirstate.py - extensions to mercurial dirstate to support narrow clones
#
# Copyright 2017 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from mercurial.i18n import _
from mercurial import error


def wrapdirstate(repo, dirstate):
    """Add narrow spec dirstate ignore, block changes outside narrow spec."""

    def _editfunc(fn):
        def _wrapper(self, *args, **kwargs):
            narrowmatch = repo.narrowmatch()
            for f in args:
                if f is not None and not narrowmatch(f) and f not in self:
                    raise error.Abort(
                        _(
                            b"cannot track '%s' - it is outside "
                            + b"the narrow clone"
                        )
                        % f
                    )
            return fn(self, *args, **kwargs)

        return _wrapper

    class narrowdirstate(dirstate.__class__):
        # Prevent adding/editing/copying/deleting files that are outside the
        # sparse checkout
        @_editfunc
        def normal(self, *args, **kwargs):
            return super(narrowdirstate, self).normal(*args, **kwargs)

        @_editfunc
        def set_tracked(self, *args, **kwargs):
            return super(narrowdirstate, self).set_tracked(*args, **kwargs)

        @_editfunc
        def set_untracked(self, *args):
            return super(narrowdirstate, self).set_untracked(*args)

        @_editfunc
        def add(self, *args):
            return super(narrowdirstate, self).add(*args)

        @_editfunc
        def normallookup(self, *args):
            return super(narrowdirstate, self).normallookup(*args)

        @_editfunc
        def copy(self, *args):
            return super(narrowdirstate, self).copy(*args)

        @_editfunc
        def remove(self, *args):
            return super(narrowdirstate, self).remove(*args)

        @_editfunc
        def merge(self, *args):
            return super(narrowdirstate, self).merge(*args)

        def rebuild(self, parent, allfiles, changedfiles=None):
            if changedfiles is None:
                # Rebuilding entire dirstate, let's filter allfiles to match the
                # narrowspec.
                allfiles = [f for f in allfiles if repo.narrowmatch()(f)]
            super(narrowdirstate, self).rebuild(parent, allfiles, changedfiles)

    dirstate.__class__ = narrowdirstate
    return dirstate
