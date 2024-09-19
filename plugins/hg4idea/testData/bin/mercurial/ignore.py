# ignore.py - ignored file handling for mercurial
#
# Copyright 2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import util, match
import re

_commentre = None

def ignorepats(lines):
    '''parse lines (iterable) of .hgignore text, returning a tuple of
    (patterns, parse errors). These patterns should be given to compile()
    to be validated and converted into a match function.'''
    syntaxes = {'re': 'relre:', 'regexp': 'relre:', 'glob': 'relglob:'}
    syntax = 'relre:'
    patterns = []
    warnings = []

    for line in lines:
        if "#" in line:
            global _commentre
            if not _commentre:
                _commentre = re.compile(r'((^|[^\\])(\\\\)*)#.*')
            # remove comments prefixed by an even number of escapes
            line = _commentre.sub(r'\1', line)
            # fixup properly escaped comments that survived the above
            line = line.replace("\\#", "#")
        line = line.rstrip()
        if not line:
            continue

        if line.startswith('syntax:'):
            s = line[7:].strip()
            try:
                syntax = syntaxes[s]
            except KeyError:
                warnings.append(_("ignoring invalid syntax '%s'") % s)
            continue
        pat = syntax + line
        for s, rels in syntaxes.iteritems():
            if line.startswith(rels):
                pat = line
                break
            elif line.startswith(s+':'):
                pat = rels + line[len(s) + 1:]
                break
        patterns.append(pat)

    return patterns, warnings

def readpats(root, files, warn):
    '''return a dict mapping ignore-file-name to list-of-patterns'''

    pats = {}
    for f in files:
        if f in pats:
            continue
        try:
            pats[f] = []
            fp = open(f)
            pats[f], warnings = ignorepats(fp)
            fp.close()
            for warning in warnings:
                warn("%s: %s\n" % (f, warning))
        except IOError, inst:
            if f != files[0]:
                warn(_("skipping unreadable ignore file '%s': %s\n") %
                     (f, inst.strerror))
    return [(f, pats[f]) for f in files if f in pats]

def ignore(root, files, warn):
    '''return matcher covering patterns in 'files'.

    the files parsed for patterns include:
    .hgignore in the repository root
    any additional files specified in the [ui] section of ~/.hgrc

    trailing white space is dropped.
    the escape character is backslash.
    comments start with #.
    empty lines are skipped.

    lines can be of the following formats:

    syntax: regexp # defaults following lines to non-rooted regexps
    syntax: glob   # defaults following lines to non-rooted globs
    re:pattern     # non-rooted regular expression
    glob:pattern   # non-rooted glob
    pattern        # pattern of the current default type'''

    pats = readpats(root, files, warn)

    allpats = []
    for f, patlist in pats:
        allpats.extend(patlist)
    if not allpats:
        return util.never

    try:
        ignorefunc = match.match(root, '', [], allpats)
    except util.Abort:
        # Re-raise an exception where the src is the right file
        for f, patlist in pats:
            try:
                match.match(root, '', [], patlist)
            except util.Abort, inst:
                raise util.Abort('%s: %s' % (f, inst[0]))

    return ignorefunc
