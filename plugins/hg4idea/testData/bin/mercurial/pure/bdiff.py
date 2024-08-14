# bdiff.py - Python implementation of bdiff.c
#
# Copyright 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import difflib
import re
import struct

from typing import (
    List,
    Tuple,
)


def splitnewlines(text: bytes) -> List[bytes]:
    '''like str.splitlines, but only split on newlines.'''
    lines = [l + b'\n' for l in text.split(b'\n')]
    if lines:
        if lines[-1] == b'\n':
            lines.pop()
        else:
            lines[-1] = lines[-1][:-1]
    return lines


def _normalizeblocks(
    a: List[bytes], b: List[bytes], blocks
) -> List[Tuple[int, int, int]]:
    prev = None
    r = []
    for curr in blocks:
        if prev is None:
            prev = curr
            continue
        shift = 0

        a1, b1, l1 = prev
        a1end = a1 + l1
        b1end = b1 + l1

        a2, b2, l2 = curr
        a2end = a2 + l2
        b2end = b2 + l2
        if a1end == a2:
            while (
                a1end + shift < a2end and a[a1end + shift] == b[b1end + shift]
            ):
                shift += 1
        elif b1end == b2:
            while (
                b1end + shift < b2end and a[a1end + shift] == b[b1end + shift]
            ):
                shift += 1
        r.append((a1, b1, l1 + shift))
        prev = a2 + shift, b2 + shift, l2 - shift

    if prev is not None:
        r.append(prev)

    return r


def bdiff(a: bytes, b: bytes) -> bytes:
    a = bytes(a).splitlines(True)
    b = bytes(b).splitlines(True)

    if not a:
        s = b"".join(b)
        return s and (struct.pack(b">lll", 0, 0, len(s)) + s)

    bin = []
    p = [0]
    for i in a:
        p.append(p[-1] + len(i))

    d = difflib.SequenceMatcher(None, a, b).get_matching_blocks()
    d = _normalizeblocks(a, b, d)
    la = 0
    lb = 0
    for am, bm, size in d:
        s = b"".join(b[lb:bm])
        if am > la or s:
            bin.append(struct.pack(b">lll", p[la], p[am], len(s)) + s)
        la = am + size
        lb = bm + size

    return b"".join(bin)


def blocks(a: bytes, b: bytes) -> List[Tuple[int, int, int, int]]:
    an = splitnewlines(a)
    bn = splitnewlines(b)
    d = difflib.SequenceMatcher(None, an, bn).get_matching_blocks()
    d = _normalizeblocks(an, bn, d)
    return [(i, i + n, j, j + n) for (i, j, n) in d]


def fixws(text: bytes, allws: bool) -> bytes:
    if allws:
        text = re.sub(b'[ \t\r]+', b'', text)
    else:
        text = re.sub(b'[ \t\r]+', b' ', text)
        text = text.replace(b' \n', b'\n')
    return text
