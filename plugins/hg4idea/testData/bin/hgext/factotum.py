# factotum.py - Plan 9 factotum integration for Mercurial
#
# Copyright (C) 2012 Steven Stallion <sstallion@gmail.com>
#
# This program is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the
# Free Software Foundation; either version 2 of the License, or (at your
# option) any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General
# Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

'''http authentication with factotum

This extension allows the factotum(4) facility on Plan 9 from Bell Labs
platforms to provide authentication information for HTTP access. Configuration
entries specified in the auth section as well as authentication information
provided in the repository URL are fully supported. If no prefix is specified,
a value of "*" will be assumed.

By default, keys are specified as::

  proto=pass service=hg prefix=<prefix> user=<username> !password=<password>

If the factotum extension is unable to read the required key, one will be
requested interactively.

A configuration section is available to customize runtime behavior. By
default, these entries are::

  [factotum]
  executable = /bin/auth/factotum
  mountpoint = /mnt/factotum
  service = hg

The executable entry defines the full path to the factotum binary. The
mountpoint entry defines the path to the factotum file service. Lastly, the
service entry controls the service name used when reading keys.

'''

from __future__ import absolute_import

import os
from mercurial.i18n import _
from mercurial.pycompat import setattr
from mercurial.utils import procutil
from mercurial import (
    error,
    httpconnection,
    registrar,
    url,
    util,
)

urlreq = util.urlreq
passwordmgr = url.passwordmgr

ERRMAX = 128

_executable = _mountpoint = _service = None

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'factotum',
    b'executable',
    default=b'/bin/auth/factotum',
)
configitem(
    b'factotum',
    b'mountpoint',
    default=b'/mnt/factotum',
)
configitem(
    b'factotum',
    b'service',
    default=b'hg',
)


def auth_getkey(self, params):
    if not self.ui.interactive():
        raise error.Abort(_(b'factotum not interactive'))
    if b'user=' not in params:
        params = b'%s user?' % params
    params = b'%s !password?' % params
    os.system(procutil.tonativestr(b"%s -g '%s'" % (_executable, params)))


def auth_getuserpasswd(self, getkey, params):
    params = b'proto=pass %s' % params
    while True:
        fd = os.open(b'%s/rpc' % _mountpoint, os.O_RDWR)
        try:
            os.write(fd, b'start %s' % params)
            l = os.read(fd, ERRMAX).split()
            if l[0] == b'ok':
                os.write(fd, b'read')
                status, user, passwd = os.read(fd, ERRMAX).split(None, 2)
                if status == b'ok':
                    if passwd.startswith(b"'"):
                        if passwd.endswith(b"'"):
                            passwd = passwd[1:-1].replace(b"''", b"'")
                        else:
                            raise error.Abort(_(b'malformed password string'))
                    return (user, passwd)
        except (OSError, IOError):
            raise error.Abort(_(b'factotum not responding'))
        finally:
            os.close(fd)
        getkey(self, params)


def monkeypatch_method(cls):
    def decorator(func):
        setattr(cls, func.__name__, func)
        return func

    return decorator


@monkeypatch_method(passwordmgr)
def find_user_password(self, realm, authuri):
    user, passwd = self.passwddb.find_user_password(realm, authuri)
    if user and passwd:
        self._writedebug(user, passwd)
        return (user, passwd)

    prefix = b''
    res = httpconnection.readauthforuri(self.ui, authuri, user)
    if res:
        _, auth = res
        prefix = auth.get(b'prefix')
        user, passwd = auth.get(b'username'), auth.get(b'password')
    if not user or not passwd:
        if not prefix:
            prefix = realm.split(b' ')[0].lower()
        params = b'service=%s prefix=%s' % (_service, prefix)
        if user:
            params = b'%s user=%s' % (params, user)
        user, passwd = auth_getuserpasswd(self, auth_getkey, params)

    self.add_password(realm, authuri, user, passwd)
    self._writedebug(user, passwd)
    return (user, passwd)


def uisetup(ui):
    global _executable
    _executable = ui.config(b'factotum', b'executable')
    global _mountpoint
    _mountpoint = ui.config(b'factotum', b'mountpoint')
    global _service
    _service = ui.config(b'factotum', b'service')
