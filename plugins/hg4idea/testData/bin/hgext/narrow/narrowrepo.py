# narrowrepo.py - repository which supports narrow revlogs, lazy loading
#
# Copyright 2017 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from mercurial import wireprototypes

from . import narrowdirstate


def wraprepo(repo):
    """Enables narrow clone functionality on a single local repository."""

    class narrowrepository(repo.__class__):
        def _makedirstate(self):
            dirstate = super(narrowrepository, self)._makedirstate()
            return narrowdirstate.wrapdirstate(self, dirstate)

        def peer(self):
            peer = super(narrowrepository, self).peer()
            peer._caps.add(wireprototypes.NARROWCAP)
            peer._caps.add(wireprototypes.ELLIPSESCAP)
            return peer

    repo.__class__ = narrowrepository
