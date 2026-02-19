# hgweb/__init__.py - web interface to a mercurial repository
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import os

from ..i18n import _

from .. import (
    error,
    pycompat,
)

from ..utils import procutil

# pytype: disable=pyi-error
from . import (
    hgweb_mod,
    hgwebdir_mod,
    server,
)

# pytype: enable=pyi-error


def hgweb(config, name=None, baseui=None):
    """create an hgweb wsgi object

    config can be one of:
    - repo object (single repo view)
    - path to repo (single repo view)
    - path to config file (multi-repo view)
    - dict of virtual:real pairs (multi-repo view)
    - list of virtual:real tuples (multi-repo view)
    """

    if isinstance(config, str):
        raise error.ProgrammingError(
            b'Mercurial only supports encoded strings: %r' % config
        )
    if (
        (isinstance(config, bytes) and not os.path.isdir(config))
        or isinstance(config, dict)
        or isinstance(config, list)
    ):
        # create a multi-dir interface
        return hgwebdir_mod.hgwebdir(config, baseui=baseui)
    return hgweb_mod.hgweb(config, name=name, baseui=baseui)


def hgwebdir(config, baseui=None):
    return hgwebdir_mod.hgwebdir(config, baseui=baseui)


class httpservice:
    def __init__(self, ui, app, opts):
        self.ui = ui
        self.app = app
        self.opts = opts

    def init(self):
        procutil.setsignalhandler()
        self.httpd = server.create_server(self.ui, self.app)

        if (
            self.opts[b'port']
            and not self.ui.verbose
            and not self.opts[b'print_url']
        ):
            return

        if self.httpd.prefix:
            prefix = self.httpd.prefix.strip(b'/') + b'/'
        else:
            prefix = b''

        port = ':%d' % self.httpd.port
        if port == ':80':
            port = ''

        bindaddr = self.httpd.addr
        if bindaddr == '0.0.0.0':
            bindaddr = '*'
        elif ':' in bindaddr:  # IPv6
            bindaddr = '[%s]' % bindaddr

        fqaddr = self.httpd.fqaddr
        if ':' in fqaddr:
            fqaddr = '[%s]' % fqaddr

        url = b'http://%s%s/%s' % (
            pycompat.sysbytes(fqaddr),
            pycompat.sysbytes(port),
            prefix,
        )
        if self.opts[b'print_url']:
            self.ui.write(b'%s\n' % url)
        else:
            if self.opts[b'port']:
                write = self.ui.status
            else:
                write = self.ui.write
            write(
                _(b'listening at %s (bound to %s:%d)\n')
                % (url, pycompat.sysbytes(bindaddr), self.httpd.port)
            )
        self.ui.flush()  # avoid buffering of status message

    def run(self):
        self.httpd.serve_forever()


def createapp(baseui, repo, webconf):
    if webconf:
        return hgwebdir_mod.hgwebdir(webconf, baseui=baseui)
    else:
        if not repo:
            raise error.RepoError(
                _(b"there is no Mercurial repository here (.hg not found)")
            )
        return hgweb_mod.hgweb(repo, baseui=baseui)
