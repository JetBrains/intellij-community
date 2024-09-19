# diffhelpers.py - pure Python implementation of diffhelpers.c
#
# Copyright 2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

def addlines(fp, hunk, lena, lenb, a, b):
    while True:
        todoa = lena - len(a)
        todob = lenb - len(b)
        num = max(todoa, todob)
        if num == 0:
            break
        for i in xrange(num):
            s = fp.readline()
            c = s[0]
            if s == "\\ No newline at end of file\n":
                fix_newline(hunk, a, b)
                continue
            if c == "\n":
                # Some patches may be missing the control char
                # on empty lines. Supply a leading space.
                s = " \n"
            hunk.append(s)
            if c == "+":
                b.append(s[1:])
            elif c == "-":
                a.append(s)
            else:
                b.append(s[1:])
                a.append(s)
    return 0

def fix_newline(hunk, a, b):
    l = hunk[-1]
    # tolerate CRLF in last line
    if l.endswith('\r\n'):
        hline = l[:-2]
    else:
        hline = l[:-1]
    c = hline[0]

    if c in " +":
        b[-1] = hline[1:]
    if c in " -":
        a[-1] = hline
    hunk[-1] = hline
    return 0


def testhunk(a, b, bstart):
    alen = len(a)
    blen = len(b)
    if alen > blen - bstart:
        return -1
    for i in xrange(alen):
        if a[i][1:] != b[i + bstart]:
            return -1
    return 0
