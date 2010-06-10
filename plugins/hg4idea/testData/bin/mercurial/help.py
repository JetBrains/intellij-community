# help.py - help data for mercurial
#
# Copyright 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import gettext, _
import sys, os
import extensions


def moduledoc(file):
    '''return the top-level python documentation for the given file

    Loosely inspired by pydoc.source_synopsis(), but rewritten to
    handle triple quotes and to return the whole text instead of just
    the synopsis'''
    result = []

    line = file.readline()
    while line[:1] == '#' or not line.strip():
        line = file.readline()
        if not line:
            break

    start = line[:3]
    if start == '\"\"\"' or start == "\'\'\'":
        line = line[3:]
        while line:
            if line.rstrip().endswith(start):
                line = line.split(start)[0]
                if line:
                    result.append(line)
                break
            elif not line:
                return None # unmatched delimiter
            result.append(line)
            line = file.readline()
    else:
        return None

    return ''.join(result)

def listexts(header, exts, maxlength, indent=1):
    '''return a text listing of the given extensions'''
    if not exts:
        return ''
    result = '\n%s\n\n' % header
    for name, desc in sorted(exts.iteritems()):
        result += '%s%-*s %s\n' % (' ' * indent, maxlength + 2,
                                   ':%s:' % name, desc)
    return result

def extshelp():
    doc = loaddoc('extensions')()

    exts, maxlength = extensions.enabled()
    doc += listexts(_('enabled extensions:'), exts, maxlength)

    exts, maxlength = extensions.disabled()
    doc += listexts(_('disabled extensions:'), exts, maxlength)

    return doc

def loaddoc(topic):
    """Return a delayed loader for help/topic.txt."""

    def loader():
        if hasattr(sys, 'frozen'):
            module = sys.executable
        else:
            module = __file__
        base = os.path.dirname(module)

        for dir in ('.', '..'):
            docdir = os.path.join(base, dir, 'help')
            if os.path.isdir(docdir):
                break

        path = os.path.join(docdir, topic + ".txt")
        return gettext(open(path).read())
    return loader

helptable = (
    (["config"], _("Configuration Files"), loaddoc('config')),
    (["dates"], _("Date Formats"), loaddoc('dates')),
    (["patterns"], _("File Name Patterns"), loaddoc('patterns')),
    (['environment', 'env'], _('Environment Variables'),
     loaddoc('environment')),
    (['revs', 'revisions'], _('Specifying Single Revisions'),
     loaddoc('revisions')),
    (['mrevs', 'multirevs'], _('Specifying Multiple Revisions'),
     loaddoc('multirevs')),
    (['diffs'], _('Diff Formats'), loaddoc('diffs')),
    (['templating', 'templates'], _('Template Usage'),
     loaddoc('templates')),
    (['urls'], _('URL Paths'), loaddoc('urls')),
    (["extensions"], _("Using additional features"), extshelp),
)
