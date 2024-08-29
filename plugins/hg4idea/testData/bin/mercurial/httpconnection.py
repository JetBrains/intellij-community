# httpconnection.py - urllib2 handler for new http support
#
# Copyright 2005, 2006, 2007, 2008 Olivia Mackall <olivia@selenic.com>
# Copyright 2006, 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
# Copyright 2011 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import os

from .i18n import _
from .pycompat import open
from . import (
    pycompat,
    util,
)
from .utils import (
    urlutil,
)


urlerr = util.urlerr
urlreq = util.urlreq

# moved here from url.py to avoid a cycle
class httpsendfile:
    """This is a wrapper around the objects returned by python's "open".

    Its purpose is to send file-like objects via HTTP.
    It do however not define a __len__ attribute because the length
    might be more than Py_ssize_t can handle.
    """

    def __init__(self, ui, *args, **kwargs):
        self.ui = ui
        self._data = open(*args, **kwargs)
        self.seek = self._data.seek
        self.close = self._data.close
        self.write = self._data.write
        self.length = os.fstat(self._data.fileno()).st_size
        self._pos = 0
        self._progress = self._makeprogress()

    def _makeprogress(self):
        # We pass double the max for total because we currently have
        # to send the bundle twice in the case of a server that
        # requires authentication. Since we can't know until we try
        # once whether authentication will be required, just lie to
        # the user and maybe the push succeeds suddenly at 50%.
        return self.ui.makeprogress(
            _(b'sending'), unit=_(b'kb'), total=(self.length // 1024 * 2)
        )

    def read(self, *args, **kwargs):
        ret = self._data.read(*args, **kwargs)
        if not ret:
            self._progress.complete()
            return ret
        self._pos += len(ret)
        self._progress.update(self._pos // 1024)
        return ret

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


# moved here from url.py to avoid a cycle
def readauthforuri(ui, uri, user):
    uri = pycompat.bytesurl(uri)
    # Read configuration
    groups = {}
    for key, val in ui.configitems(b'auth'):
        if key in (b'cookiefile',):
            continue

        if b'.' not in key:
            ui.warn(_(b"ignoring invalid [auth] key '%s'\n") % key)
            continue
        group, setting = key.rsplit(b'.', 1)
        gdict = groups.setdefault(group, {})
        if setting in (b'username', b'cert', b'key'):
            val = util.expandpath(val)
        gdict[setting] = val

    # Find the best match
    scheme, hostpath = uri.split(b'://', 1)
    bestuser = None
    bestlen = 0
    bestauth = None
    for group, auth in groups.items():
        if user and user != auth.get(b'username', user):
            # If a username was set in the URI, the entry username
            # must either match it or be unset
            continue
        prefix = auth.get(b'prefix')
        if not prefix:
            continue

        prefixurl = urlutil.url(prefix)
        if prefixurl.user and prefixurl.user != user:
            # If a username was set in the prefix, it must match the username in
            # the URI.
            continue

        # The URI passed in has been stripped of credentials, so erase the user
        # here to allow simpler matching.
        prefixurl.user = None
        prefix = bytes(prefixurl)

        p = prefix.split(b'://', 1)
        if len(p) > 1:
            schemes, prefix = [p[0]], p[1]
        else:
            schemes = (auth.get(b'schemes') or b'https').split()
        if (
            (prefix == b'*' or hostpath.startswith(prefix))
            and (
                len(prefix) > bestlen
                or (
                    len(prefix) == bestlen
                    and not bestuser
                    and b'username' in auth
                )
            )
            and scheme in schemes
        ):
            bestlen = len(prefix)
            bestauth = group, auth
            bestuser = auth.get(b'username')
            if user and not bestuser:
                auth[b'username'] = user
    return bestauth
