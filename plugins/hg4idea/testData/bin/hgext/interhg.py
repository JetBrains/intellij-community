# interhg.py - interhg
#
# Copyright 2007 OHASHI Hideya <ohachige@gmail.com>
#
# Contributor(s):
#   Edward Lee <edward.lee@engineering.uiuc.edu>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''expand expressions into changelog and summaries

This extension allows the use of a special syntax in summaries, which
will be automatically expanded into links or any other arbitrary
expression, much like InterWiki does.

A few example patterns (link to bug tracking, etc.) that may be used
in your hgrc::

  [interhg]
  issues = s!issue(\\d+)!<a href="http://bts/issue\\1">issue\\1</a>!
  bugzilla = s!((?:bug|b=|(?=#?\\d{4,}))(?:\\s*#?)(\\d+))!<a..=\\2">\\1</a>!i
  boldify = s!(^|\\s)#(\\d+)\\b! <b>#\\2</b>!
'''

import re
from mercurial.hgweb import hgweb_mod
from mercurial import templatefilters, extensions
from mercurial.i18n import _

testedwith = 'internal'

interhg_table = []

def uisetup(ui):
    orig_escape = templatefilters.filters["escape"]

    def interhg_escape(x):
        escstr = orig_escape(x)
        for regexp, format in interhg_table:
            escstr = regexp.sub(format, escstr)
        return escstr

    templatefilters.filters["escape"] = interhg_escape

def interhg_refresh(orig, self, *args, **kwargs):
    interhg_table[:] = []
    for key, pattern in self.repo.ui.configitems('interhg'):
        # grab the delimiter from the character after the "s"
        unesc = pattern[1]
        delim = re.escape(unesc)

        # identify portions of the pattern, taking care to avoid escaped
        # delimiters. the replace format and flags are optional, but delimiters
        # are required.
        match = re.match(r'^s%s(.+)(?:(?<=\\\\)|(?<!\\))%s(.*)%s([ilmsux])*$'
                         % (delim, delim, delim), pattern)
        if not match:
            self.repo.ui.warn(_("interhg: invalid pattern for %s: %s\n")
                              % (key, pattern))
            continue

        # we need to unescape the delimiter for regexp and format
        delim_re = re.compile(r'(?<!\\)\\%s' % delim)
        regexp = delim_re.sub(unesc, match.group(1))
        format = delim_re.sub(unesc, match.group(2))

        # the pattern allows for 6 regexp flags, so set them if necessary
        flagin = match.group(3)
        flags = 0
        if flagin:
            for flag in flagin.upper():
                flags |= re.__dict__[flag]

        try:
            regexp = re.compile(regexp, flags)
            interhg_table.append((regexp, format))
        except re.error:
            self.repo.ui.warn(_("interhg: invalid regexp for %s: %s\n")
                              % (key, regexp))
    return orig(self, *args, **kwargs)

extensions.wrapfunction(hgweb_mod.hgweb, 'refresh', interhg_refresh)
