# -*- coding: utf-8 -*-

# Copyright (C) 2007 Daniel Holth <dholth@fastmail.fm>
# This is a stripped-down version of the original bzr-svn transport.py,
# Copyright (C) 2006 Jelmer Vernooij <jelmer@samba.org>

# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.

# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.

# You should have received a copy of the GNU General Public License
# along with this program; if not, see <http://www.gnu.org/licenses/>.

from mercurial import util
from svn.core import SubversionException, Pool
import svn.ra
import svn.client
import svn.core

# Some older versions of the Python bindings need to be
# explicitly initialized. But what we want to do probably
# won't work worth a darn against those libraries anyway!
svn.ra.initialize()

svn_config = svn.core.svn_config_get_config(None)


def _create_auth_baton(pool):
    """Create a Subversion authentication baton. """
    import svn.client
    # Give the client context baton a suite of authentication
    # providers.h
    providers = [
        svn.client.get_simple_provider(pool),
        svn.client.get_username_provider(pool),
        svn.client.get_ssl_client_cert_file_provider(pool),
        svn.client.get_ssl_client_cert_pw_file_provider(pool),
        svn.client.get_ssl_server_trust_file_provider(pool),
        ]
    # Platform-dependent authentication methods
    getprovider = getattr(svn.core, 'svn_auth_get_platform_specific_provider',
                          None)
    if getprovider:
        # Available in svn >= 1.6
        for name in ('gnome_keyring', 'keychain', 'kwallet', 'windows'):
            for type in ('simple', 'ssl_client_cert_pw', 'ssl_server_trust'):
                p = getprovider(name, type, pool)
                if p:
                    providers.append(p)
    else:
        if util.safehasattr(svn.client, 'get_windows_simple_provider'):
            providers.append(svn.client.get_windows_simple_provider(pool))

    return svn.core.svn_auth_open(providers, pool)

class NotBranchError(SubversionException):
    pass

class SvnRaTransport(object):
    """
    Open an ra connection to a Subversion repository.
    """
    def __init__(self, url="", ra=None):
        self.pool = Pool()
        self.svn_url = url
        self.username = ''
        self.password = ''

        # Only Subversion 1.4 has reparent()
        if ra is None or not util.safehasattr(svn.ra, 'reparent'):
            self.client = svn.client.create_context(self.pool)
            ab = _create_auth_baton(self.pool)
            if False:
                svn.core.svn_auth_set_parameter(
                    ab, svn.core.SVN_AUTH_PARAM_DEFAULT_USERNAME, self.username)
                svn.core.svn_auth_set_parameter(
                    ab, svn.core.SVN_AUTH_PARAM_DEFAULT_PASSWORD, self.password)
            self.client.auth_baton = ab
            self.client.config = svn_config
            try:
                self.ra = svn.client.open_ra_session(
                    self.svn_url,
                    self.client, self.pool)
            except SubversionException, (inst, num):
                if num in (svn.core.SVN_ERR_RA_ILLEGAL_URL,
                           svn.core.SVN_ERR_RA_LOCAL_REPOS_OPEN_FAILED,
                           svn.core.SVN_ERR_BAD_URL):
                    raise NotBranchError(url)
                raise
        else:
            self.ra = ra
            svn.ra.reparent(self.ra, self.svn_url.encode('utf8'))

    class Reporter(object):
        def __init__(self, reporter_data):
            self._reporter, self._baton = reporter_data

        def set_path(self, path, revnum, start_empty, lock_token, pool=None):
            svn.ra.reporter2_invoke_set_path(self._reporter, self._baton,
                        path, revnum, start_empty, lock_token, pool)

        def delete_path(self, path, pool=None):
            svn.ra.reporter2_invoke_delete_path(self._reporter, self._baton,
                    path, pool)

        def link_path(self, path, url, revision, start_empty, lock_token,
                      pool=None):
            svn.ra.reporter2_invoke_link_path(self._reporter, self._baton,
                    path, url, revision, start_empty, lock_token,
                    pool)

        def finish_report(self, pool=None):
            svn.ra.reporter2_invoke_finish_report(self._reporter,
                    self._baton, pool)

        def abort_report(self, pool=None):
            svn.ra.reporter2_invoke_abort_report(self._reporter,
                    self._baton, pool)

    def do_update(self, revnum, path, *args, **kwargs):
        return self.Reporter(svn.ra.do_update(self.ra, revnum, path,
                                              *args, **kwargs))
