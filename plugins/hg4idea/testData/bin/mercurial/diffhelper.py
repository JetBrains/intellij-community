# diffhelper.py - helper routines for patch
#
# Copyright 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from .i18n import _

from . import (
    error,
)

MISSING_NEWLINE_MARKER = b'\\ No newline at end of file\n'


def addlines(fp, hunk, lena, lenb, a, b):
    """Read lines from fp into the hunk

    The hunk is parsed into two arrays, a and b. a gets the old state of
    the text, b gets the new state. The control char from the hunk is saved
    when inserting into a, but not b (for performance while deleting files.)
    """
    while True:
        todoa = lena - len(a)
        todob = lenb - len(b)
        num = max(todoa, todob)
        if num == 0:
            break
        for i in range(num):
            s = fp.readline()
            if not s:
                raise error.ParseError(_(b'incomplete hunk'))
            if s == MISSING_NEWLINE_MARKER:
                fixnewline(hunk, a, b)
                continue
            if s == b'\n' or s == b'\r\n':
                # Some patches may be missing the control char
                # on empty lines. Supply a leading space.
                s = b' ' + s
            hunk.append(s)
            if s.startswith(b'+'):
                b.append(s[1:])
            elif s.startswith(b'-'):
                a.append(s)
            else:
                b.append(s[1:])
                a.append(s)


def fixnewline(hunk, a, b):
    """Fix up the last lines of a and b when the patch has no newline at EOF"""
    l = hunk[-1]
    # tolerate CRLF in last line
    if l.endswith(b'\r\n'):
        hline = l[:-2]
    else:
        hline = l[:-1]

    if hline.startswith((b' ', b'+')):
        b[-1] = hline[1:]
    if hline.startswith((b' ', b'-')):
        a[-1] = hline
    hunk[-1] = hline


def testhunk(a, b, bstart):
    """Compare the lines in a with the lines in b

    a is assumed to have a control char at the start of each line, this char
    is ignored in the compare.
    """
    alen = len(a)
    blen = len(b)
    if alen > blen - bstart or bstart < 0:
        return False
    for i in range(alen):
        if a[i][1:] != b[i + bstart]:
            return False
    return True
