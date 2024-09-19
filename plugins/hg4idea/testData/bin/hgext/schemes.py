# Copyright 2009, Alexander Solovyov <piranha@piranha.org.ua>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""extend schemes with shortcuts to repository swarms

This extension allows you to specify shortcuts for parent URLs with a
lot of repositories to act like a scheme, for example::

  [schemes]
  py = http://code.python.org/hg/

After that you can use it like::

  hg clone py://trunk/

Additionally there is support for some more complex schemas, for
example used by Google Code::

  [schemes]
  gcode = http://{1}.googlecode.com/hg/

The syntax is taken from Mercurial templates, and you have unlimited
number of variables, starting with ``{1}`` and continuing with
``{2}``, ``{3}`` and so on. This variables will receive parts of URL
supplied, split by ``/``. Anything not specified as ``{part}`` will be
just appended to an URL.

For convenience, the extension adds these schemes by default::

  [schemes]
  py = http://hg.python.org/
  bb = https://bitbucket.org/
  bb+ssh = ssh://hg@bitbucket.org/
  gcode = https://{1}.googlecode.com/hg/
  kiln = https://{1}.kilnhg.com/Repo/

You can override a predefined scheme by defining a new scheme with the
same name.
"""
from __future__ import absolute_import

import os
import re

from mercurial.i18n import _
from mercurial import (
    error,
    extensions,
    hg,
    pycompat,
    registrar,
    templater,
)
from mercurial.utils import (
    urlutil,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

_partre = re.compile(br'{(\d+)\}')


class ShortRepository(object):
    def __init__(self, url, scheme, templater):
        self.scheme = scheme
        self.templater = templater
        self.url = url
        try:
            self.parts = max(map(int, _partre.findall(self.url)))
        except ValueError:
            self.parts = 0

    def __repr__(self):
        return b'<ShortRepository: %s>' % self.scheme

    def instance(self, ui, url, create, intents=None, createopts=None):
        url = self.resolve(url)
        return hg._peerlookup(url).instance(
            ui, url, create, intents=intents, createopts=createopts
        )

    def resolve(self, url):
        # Should this use the urlutil.url class, or is manual parsing better?
        try:
            url = url.split(b'://', 1)[1]
        except IndexError:
            raise error.Abort(_(b"no '://' in scheme url '%s'") % url)
        parts = url.split(b'/', self.parts)
        if len(parts) > self.parts:
            tail = parts[-1]
            parts = parts[:-1]
        else:
            tail = b''
        context = {b'%d' % (i + 1): v for i, v in enumerate(parts)}
        return b''.join(self.templater.process(self.url, context)) + tail


def hasdriveletter(orig, path):
    if path:
        for scheme in schemes:
            if path.startswith(scheme + b':'):
                return False
    return orig(path)


schemes = {
    b'py': b'http://hg.python.org/',
    b'bb': b'https://bitbucket.org/',
    b'bb+ssh': b'ssh://hg@bitbucket.org/',
    b'gcode': b'https://{1}.googlecode.com/hg/',
    b'kiln': b'https://{1}.kilnhg.com/Repo/',
}


def extsetup(ui):
    schemes.update(dict(ui.configitems(b'schemes')))
    t = templater.engine(templater.parse)
    for scheme, url in schemes.items():
        if (
            pycompat.iswindows
            and len(scheme) == 1
            and scheme.isalpha()
            and os.path.exists(b'%s:\\' % scheme)
        ):
            raise error.Abort(
                _(
                    b'custom scheme %s:// conflicts with drive '
                    b'letter %s:\\\n'
                )
                % (scheme, scheme.upper())
            )
        hg.schemes[scheme] = ShortRepository(url, scheme, t)

    extensions.wrapfunction(urlutil, b'hasdriveletter', hasdriveletter)


@command(b'debugexpandscheme', norepo=True)
def expandscheme(ui, url, **opts):
    """given a repo path, provide the scheme-expanded path"""
    repo = hg._peerlookup(url)
    if isinstance(repo, ShortRepository):
        url = repo.resolve(url)
    ui.write(url + b'\n')
