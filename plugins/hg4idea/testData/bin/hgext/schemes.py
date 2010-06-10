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

import re
from mercurial import hg, templater


class ShortRepository(object):
    def __init__(self, url, scheme, templater):
        self.scheme = scheme
        self.templater = templater
        self.url = url
        try:
            self.parts = max(map(int, re.findall(r'\{(\d+)\}', self.url)))
        except ValueError:
            self.parts = 0

    def __repr__(self):
        return '<ShortRepository: %s>' % self.scheme

    def instance(self, ui, url, create):
        url = url.split('://', 1)[1]
        parts = url.split('/', self.parts)
        if len(parts) > self.parts:
            tail = parts[-1]
            parts = parts[:-1]
        else:
            tail = ''
        context = dict((str(i + 1), v) for i, v in enumerate(parts))
        url = ''.join(self.templater.process(self.url, context)) + tail
        return hg._lookup(url).instance(ui, url, create)

schemes = {
    'py': 'http://hg.python.org/',
    'bb': 'https://bitbucket.org/',
    'bb+ssh': 'ssh://hg@bitbucket.org/',
    'gcode': 'https://{1}.googlecode.com/hg/',
    'kiln': 'https://{1}.kilnhg.com/Repo/'
    }

def extsetup(ui):
    schemes.update(dict(ui.configitems('schemes')))
    t = templater.engine(lambda x: x)
    for scheme, url in schemes.items():
        hg.schemes[scheme] = ShortRepository(url, scheme, t)
