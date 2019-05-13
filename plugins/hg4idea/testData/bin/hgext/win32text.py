# win32text.py - LF <-> CRLF/CR translation utilities for Windows/Mac users
#
#  Copyright 2005, 2007-2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''perform automatic newline conversion

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

from mercurial.i18n import _
from mercurial.node import short
from mercurial import util
import re

testedwith = 'internal'

# regexp for single LF without CR preceding.
re_single_lf = re.compile('(^|[^\r])\n', re.MULTILINE)

newlinestr = {'\r\n': 'CRLF', '\r': 'CR'}
filterstr = {'\r\n': 'clever', '\r': 'mac'}

def checknewline(s, newline, ui=None, repo=None, filename=None):
    # warn if already has 'newline' in repository.
    # it might cause unexpected eol conversion.
    # see issue 302:
    #   http://mercurial.selenic.com/bts/issue302
    if newline in s and ui and filename and repo:
        ui.warn(_('WARNING: %s already has %s line endings\n'
                  'and does not need EOL conversion by the win32text plugin.\n'
                  'Before your next commit, please reconsider your '
                  'encode/decode settings in \nMercurial.ini or %s.\n') %
                (filename, newlinestr[newline], repo.join('hgrc')))

def dumbdecode(s, cmd, **kwargs):
    checknewline(s, '\r\n', **kwargs)
    # replace single LF to CRLF
    return re_single_lf.sub('\\1\r\n', s)

def dumbencode(s, cmd):
    return s.replace('\r\n', '\n')

def macdumbdecode(s, cmd, **kwargs):
    checknewline(s, '\r', **kwargs)
    return s.replace('\n', '\r')

def macdumbencode(s, cmd):
    return s.replace('\r', '\n')

def cleverdecode(s, cmd, **kwargs):
    if not util.binary(s):
        return dumbdecode(s, cmd, **kwargs)
    return s

def cleverencode(s, cmd):
    if not util.binary(s):
        return dumbencode(s, cmd)
    return s

def macdecode(s, cmd, **kwargs):
    if not util.binary(s):
        return macdumbdecode(s, cmd, **kwargs)
    return s

def macencode(s, cmd):
    if not util.binary(s):
        return macdumbencode(s, cmd)
    return s

_filters = {
    'dumbdecode:': dumbdecode,
    'dumbencode:': dumbencode,
    'cleverdecode:': cleverdecode,
    'cleverencode:': cleverencode,
    'macdumbdecode:': macdumbdecode,
    'macdumbencode:': macdumbencode,
    'macdecode:': macdecode,
    'macencode:': macencode,
    }

def forbidnewline(ui, repo, hooktype, node, newline, **kwargs):
    halt = False
    seen = set()
    # we try to walk changesets in reverse order from newest to
    # oldest, so that if we see a file multiple times, we take the
    # newest version as canonical. this prevents us from blocking a
    # changegroup that contains an unacceptable commit followed later
    # by a commit that fixes the problem.
    tip = repo['tip']
    for rev in xrange(len(repo) - 1, repo[node].rev() - 1, -1):
        c = repo[rev]
        for f in c.files():
            if f in seen or f not in tip or f not in c:
                continue
            seen.add(f)
            data = c[f].data()
            if not util.binary(data) and newline in data:
                if not halt:
                    ui.warn(_('attempt to commit or push text file(s) '
                              'using %s line endings\n') %
                              newlinestr[newline])
                ui.warn(_('in %s: %s\n') % (short(c.node()), f))
                halt = True
    if halt and hooktype == 'pretxnchangegroup':
        crlf = newlinestr[newline].lower()
        filter = filterstr[newline]
        ui.warn(_('\nTo prevent this mistake in your local repository,\n'
                  'add to Mercurial.ini or .hg/hgrc:\n'
                  '\n'
                  '[hooks]\n'
                  'pretxncommit.%s = python:hgext.win32text.forbid%s\n'
                  '\n'
                  'and also consider adding:\n'
                  '\n'
                  '[extensions]\n'
                  'win32text =\n'
                  '[encode]\n'
                  '** = %sencode:\n'
                  '[decode]\n'
                  '** = %sdecode:\n') % (crlf, crlf, filter, filter))
    return halt

def forbidcrlf(ui, repo, hooktype, node, **kwargs):
    return forbidnewline(ui, repo, hooktype, node, '\r\n', **kwargs)

def forbidcr(ui, repo, hooktype, node, **kwargs):
    return forbidnewline(ui, repo, hooktype, node, '\r', **kwargs)

def reposetup(ui, repo):
    if not repo.local():
        return
    for name, fn in _filters.iteritems():
        repo.adddatafilter(name, fn)

def extsetup(ui):
    if ui.configbool('win32text', 'warn', True):
        ui.warn(_("win32text is deprecated: "
                  "http://mercurial.selenic.com/wiki/Win32TextExtension\n"))
