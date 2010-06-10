# streamclone.py - streaming clone server support for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import util, error
from i18n import _

from mercurial import store

class StreamException(Exception):
    def __init__(self, code):
        Exception.__init__(self)
        self.code = code
    def __str__(self):
        return '%i\n' % self.code

# if server supports streaming clone, it advertises "stream"
# capability with value that is version+flags of repo it is serving.
# client only streams if it can read that repo format.

# stream file format is simple.
#
# server writes out line that says how many files, how many total
# bytes.  separator is ascii space, byte counts are strings.
#
# then for each file:
#
#   server writes out line that says filename, how many bytes in
#   file.  separator is ascii nul, byte count is string.
#
#   server writes out raw file data.

def allowed(ui):
    return ui.configbool('server', 'uncompressed', True, untrusted=True)

def stream_out(repo):
    '''stream out all metadata files in repository.
    writes to file-like object, must support write() and optional flush().'''

    if not allowed(repo.ui):
        raise StreamException(1)

    entries = []
    total_bytes = 0
    try:
        # get consistent snapshot of repo, lock during scan
        lock = repo.lock()
        try:
            repo.ui.debug('scanning\n')
            for name, ename, size in repo.store.walk():
                entries.append((name, size))
                total_bytes += size
        finally:
            lock.release()
    except error.LockError:
        raise StreamException(2)

    yield '0\n'
    repo.ui.debug('%d files, %d bytes to transfer\n' %
                  (len(entries), total_bytes))
    yield '%d %d\n' % (len(entries), total_bytes)
    for name, size in entries:
        repo.ui.debug('sending %s (%d bytes)\n' % (name, size))
        # partially encode name over the wire for backwards compat
        yield '%s\0%d\n' % (store.encodedir(name), size)
        for chunk in util.filechunkiter(repo.sopener(name), limit=size):
            yield chunk
