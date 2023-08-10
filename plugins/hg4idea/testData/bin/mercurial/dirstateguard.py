# dirstateguard.py - class to allow restoring dirstate after failure
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import os
from .i18n import _

from . import (
    error,
    narrowspec,
    requirements,
    util,
)


class dirstateguard(util.transactional):
    """Restore dirstate at unexpected failure.

    At the construction, this class does:

    - write current ``repo.dirstate`` out, and
    - save ``.hg/dirstate`` into the backup file

    This restores ``.hg/dirstate`` from backup file, if ``release()``
    is invoked before ``close()``.

    This just removes the backup file at ``close()`` before ``release()``.
    """

    def __init__(self, repo, name):
        self._repo = repo
        self._active = False
        self._closed = False

        def getname(prefix):
            fd, fname = repo.vfs.mkstemp(prefix=prefix)
            os.close(fd)
            return fname

        self._backupname = getname(b'dirstate.backup.%s.' % name)
        repo.dirstate.savebackup(repo.currenttransaction(), self._backupname)
        # Don't make this the empty string, things may join it with stuff and
        # blindly try to unlink it, which could be bad.
        self._narrowspecbackupname = None
        if requirements.NARROW_REQUIREMENT in repo.requirements:
            self._narrowspecbackupname = getname(
                b'narrowspec.backup.%s.' % name
            )
            narrowspec.savewcbackup(repo, self._narrowspecbackupname)
        self._active = True

    def __del__(self):
        if self._active:  # still active
            # this may occur, even if this class is used correctly:
            # for example, releasing other resources like transaction
            # may raise exception before ``dirstateguard.release`` in
            # ``release(tr, ....)``.
            self._abort()

    def close(self):
        if not self._active:  # already inactivated
            msg = (
                _(b"can't close already inactivated backup: %s")
                % self._backupname
            )
            raise error.Abort(msg)

        self._repo.dirstate.clearbackup(
            self._repo.currenttransaction(), self._backupname
        )
        if self._narrowspecbackupname:
            narrowspec.clearwcbackup(self._repo, self._narrowspecbackupname)
        self._active = False
        self._closed = True

    def _abort(self):
        if self._narrowspecbackupname:
            narrowspec.restorewcbackup(self._repo, self._narrowspecbackupname)
        self._repo.dirstate.restorebackup(
            self._repo.currenttransaction(), self._backupname
        )
        self._active = False

    def release(self):
        if not self._closed:
            if not self._active:  # already inactivated
                msg = (
                    _(b"can't release already inactivated backup: %s")
                    % self._backupname
                )
                raise error.Abort(msg)
            self._abort()
