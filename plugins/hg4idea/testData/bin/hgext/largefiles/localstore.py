# Copyright 2009-2010 Gregory P. Ward
# Copyright 2009-2010 Intelerad Medical Systems Incorporated
# Copyright 2010-2011 Fog Creek Software
# Copyright 2010-2011 Unity Technologies
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''store class for local filesystem'''

from mercurial.i18n import _

import lfutil
import basestore

class localstore(basestore.basestore):
    '''localstore first attempts to grab files out of the store in the remote
    Mercurial repository.  Failing that, it attempts to grab the files from
    the user cache.'''

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
            raise basestore.StoreError(filename, hash, self.url,
                _("can't get file locally"))
        fd = open(path, 'rb')
        try:
            return lfutil.copyandhash(fd, tmpfile)
        finally:
            fd.close()

    def _verifyfile(self, cctx, cset, contents, standin, verified):
        filename = lfutil.splitstandin(standin)
        if not filename:
            return False
        fctx = cctx[standin]
        key = (filename, fctx.filenode())
        if key in verified:
            return False

        expecthash = fctx.data()[0:40]
        storepath = lfutil.storepath(self.remote, expecthash)
        verified.add(key)
        if not lfutil.instore(self.remote, expecthash):
            self.ui.warn(
                _('changeset %s: %s references missing %s\n')
                % (cset, filename, storepath))
            return True                 # failed

        if contents:
            actualhash = lfutil.hashfile(storepath)
            if actualhash != expecthash:
                self.ui.warn(
                    _('changeset %s: %s references corrupted %s\n')
                    % (cset, filename, storepath))
                return True             # failed
        return False
