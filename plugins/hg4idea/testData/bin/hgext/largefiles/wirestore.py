# Copyright 2010-2011 Fog Creek Software
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''largefile store working over Mercurial's wire protocol'''
from __future__ import absolute_import

from . import (
    lfutil,
    remotestore,
)


class wirestore(remotestore.remotestore):
    def __init__(self, ui, repo, remote):
        cap = remote.capable(b'largefiles')
        if not cap:
            raise lfutil.storeprotonotcapable([])
        storetypes = cap.split(b',')
        if b'serve' not in storetypes:
            raise lfutil.storeprotonotcapable(storetypes)
        self.remote = remote
        super(wirestore, self).__init__(ui, repo, remote.url())

    def _put(self, hash, fd):
        return self.remote.putlfile(hash, fd)

    def _get(self, hash):
        return self.remote.getlfile(hash)

    def _stat(self, hashes):
        """For each hash, return 0 if it is available, other values if not.
        It is usually 2 if the largefile is missing, but might be 1 the server
        has a corrupted copy."""

        with self.remote.commandexecutor() as e:
            fs = []
            for hash in hashes:
                fs.append(
                    (
                        hash,
                        e.callcommand(
                            b'statlfile',
                            {
                                b'sha': hash,
                            },
                        ),
                    )
                )

            return {hash: f.result() for hash, f in fs}
