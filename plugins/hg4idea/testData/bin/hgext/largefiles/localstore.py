# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''store class for local filesystem'''
from __future__ import absolute_import

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial import util

from . import (
    basestore,
    lfutil,
)


class localstore(basestore.basestore):
    """localstore first attempts to grab files out of the store in the remote
    Mercurial repository.  Failing that, it attempts to grab the files from
    the user cache."""

    def __init__(self, ui, repo, remote):
        self.remote = remote.local()
        super(localstore, self).__init__(ui, repo, self.remote.url())

    def put(self, source, hash):
        if lfutil.instore(self.remote, hash):
            return
        lfutil.link(source, lfutil.storepath(self.remote, hash))

    def exists(self, hashes):
        retval = {}
        for hash in hashes:
            retval[hash] = lfutil.instore(self.remote, hash)
        return retval

    def _getfile(self, tmpfile, filename, hash):
        path = lfutil.findfile(self.remote, hash)
        if not path:
            raise basestore.StoreError(
                filename, hash, self.url, _(b"can't get file locally")
            )
        with open(path, b'rb') as fd:
            return lfutil.copyandhash(util.filechunkiter(fd), tmpfile)

    def _verifyfiles(self, contents, filestocheck):
        failed = False
        for cset, filename, expectedhash in filestocheck:
            storepath, exists = lfutil.findstorepath(self.repo, expectedhash)
            if not exists:
                storepath, exists = lfutil.findstorepath(
                    self.remote, expectedhash
                )
            if not exists:
                self.ui.warn(
                    _(b'changeset %s: %s references missing %s\n')
                    % (cset, filename, storepath)
                )
                failed = True
            elif contents:
                actualhash = lfutil.hashfile(storepath)
                if actualhash != expectedhash:
                    self.ui.warn(
                        _(b'changeset %s: %s references corrupted %s\n')
                        % (cset, filename, storepath)
                    )
                    failed = True
        return failed
