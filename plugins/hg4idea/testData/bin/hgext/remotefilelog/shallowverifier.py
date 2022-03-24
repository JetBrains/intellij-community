# shallowverifier.py - shallow repository verifier
#
# Copyright 2015 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import verify


class shallowverifier(verify.verifier):
    def _verifyfiles(self, filenodes, filelinkrevs):
        """Skips files verification since repo's not guaranteed to have them"""
        self.repo.ui.status(
            _(b"skipping filelog check since remotefilelog is used\n")
        )
        return 0, 0
