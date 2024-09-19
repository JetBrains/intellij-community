# win32text.py - LF <-> CRLF/CR translation utilities for Windows/Mac users
#
#  Copyright 2005, 2007-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''perform automatic newline conversion (DEPRECATED)

  Deprecation: The win32text extension requires each user to configure
  the extension again and again for each clone since the configuration
  is not copied when cloning.

  We have therefore made the ``eol`` as an alternative. The ``eol``
  uses a version controlled file for its configuration and each clone
  will therefore use the right settings from the start.

To perform automatic newline conversion, use::

  [extensions]
  win32text =
  [encode]
  ** = cleverencode:
  # or ** = macencode:

  [decode]
  ** = cleverdecode:
  # or ** = macdecode:

If not doing conversion, to make sure you do not commit CRLF/CR by accident::

  [hooks]
  pretxncommit.crlf = python:hgext.win32text.forbidcrlf
  # or pretxncommit.cr = python:hgext.win32text.forbidcr

To do the same check on a server to prevent CRLF/CR from being
pushed or pulled::

  [hooks]
  pretxnchangegroup.crlf = python:hgext.win32text.forbidcrlf
  # or pretxnchangegroup.cr = python:hgext.win32text.forbidcr
'''

from __future__ import absolute_import

import re
from mercurial.i18n import _
from mercurial.node import short
from mercurial import (
    pycompat,
    registrar,
)
from mercurial.utils import stringutil

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'win32text',
    b'warn',
    default=True,
)

# regexp for single LF without CR preceding.
re_single_lf = re.compile(b'(^|[^\r])\n', re.MULTILINE)

newlinestr = {b'\r\n': b'CRLF', b'\r': b'CR'}
filterstr = {b'\r\n': b'clever', b'\r': b'mac'}


def checknewline(s, newline, ui=None, repo=None, filename=None):
    # warn if already has 'newline' in repository.
    # it might cause unexpected eol conversion.
    # see issue 302:
    #   https://bz.mercurial-scm.org/302
    if newline in s and ui and filename and repo:
        ui.warn(
            _(
                b'WARNING: %s already has %s line endings\n'
                b'and does not need EOL conversion by the win32text plugin.\n'
                b'Before your next commit, please reconsider your '
                b'encode/decode settings in \nMercurial.ini or %s.\n'
            )
            % (filename, newlinestr[newline], repo.vfs.join(b'hgrc'))
        )


def dumbdecode(s, cmd, **kwargs):
    checknewline(s, b'\r\n', **kwargs)
    # replace single LF to CRLF
    return re_single_lf.sub(b'\\1\r\n', s)


def dumbencode(s, cmd):
    return s.replace(b'\r\n', b'\n')


def macdumbdecode(s, cmd, **kwargs):
    checknewline(s, b'\r', **kwargs)
    return s.replace(b'\n', b'\r')


def macdumbencode(s, cmd):
    return s.replace(b'\r', b'\n')


def cleverdecode(s, cmd, **kwargs):
    if not stringutil.binary(s):
        return dumbdecode(s, cmd, **kwargs)
    return s


def cleverencode(s, cmd):
    if not stringutil.binary(s):
        return dumbencode(s, cmd)
    return s


def macdecode(s, cmd, **kwargs):
    if not stringutil.binary(s):
        return macdumbdecode(s, cmd, **kwargs)
    return s


def macencode(s, cmd):
    if not stringutil.binary(s):
        return macdumbencode(s, cmd)
    return s


_filters = {
    b'dumbdecode:': dumbdecode,
    b'dumbencode:': dumbencode,
    b'cleverdecode:': cleverdecode,
    b'cleverencode:': cleverencode,
    b'macdumbdecode:': macdumbdecode,
    b'macdumbencode:': macdumbencode,
    b'macdecode:': macdecode,
    b'macencode:': macencode,
}


def forbidnewline(ui, repo, hooktype, node, newline, **kwargs):
    halt = False
    seen = set()
    # we try to walk changesets in reverse order from newest to
    # oldest, so that if we see a file multiple times, we take the
    # newest version as canonical. this prevents us from blocking a
    # changegroup that contains an unacceptable commit followed later
    # by a commit that fixes the problem.
    tip = repo[b'tip']
    for rev in pycompat.xrange(
        repo.changelog.tiprev(), repo[node].rev() - 1, -1
    ):
        c = repo[rev]
        for f in c.files():
            if f in seen or f not in tip or f not in c:
                continue
            seen.add(f)
            data = c[f].data()
            if not stringutil.binary(data) and newline in data:
                if not halt:
                    ui.warn(
                        _(
                            b'attempt to commit or push text file(s) '
                            b'using %s line endings\n'
                        )
                        % newlinestr[newline]
                    )
                ui.warn(_(b'in %s: %s\n') % (short(c.node()), f))
                halt = True
    if halt and hooktype == b'pretxnchangegroup':
        crlf = newlinestr[newline].lower()
        filter = filterstr[newline]
        ui.warn(
            _(
                b'\nTo prevent this mistake in your local repository,\n'
                b'add to Mercurial.ini or .hg/hgrc:\n'
                b'\n'
                b'[hooks]\n'
                b'pretxncommit.%s = python:hgext.win32text.forbid%s\n'
                b'\n'
                b'and also consider adding:\n'
                b'\n'
                b'[extensions]\n'
                b'win32text =\n'
                b'[encode]\n'
                b'** = %sencode:\n'
                b'[decode]\n'
                b'** = %sdecode:\n'
            )
            % (crlf, crlf, filter, filter)
        )
    return halt


def forbidcrlf(ui, repo, hooktype, node, **kwargs):
    return forbidnewline(ui, repo, hooktype, node, b'\r\n', **kwargs)


def forbidcr(ui, repo, hooktype, node, **kwargs):
    return forbidnewline(ui, repo, hooktype, node, b'\r', **kwargs)


def reposetup(ui, repo):
    if not repo.local():
        return
    for name, fn in pycompat.iteritems(_filters):
        repo.adddatafilter(name, fn)


def extsetup(ui):
    # deprecated config: win32text.warn
    if ui.configbool(b'win32text', b'warn'):
        ui.warn(
            _(
                b"win32text is deprecated: "
                b"https://mercurial-scm.org/wiki/Win32TextExtension\n"
            )
        )
