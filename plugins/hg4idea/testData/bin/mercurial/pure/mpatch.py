# mpatch.py - Python implementation of mpatch.c
#
# Copyright 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import struct

from .. import pycompat

stringio = pycompat.bytesio


class mpatchError(Exception):
    """error raised when a delta cannot be decoded"""


# This attempts to apply a series of patches in time proportional to
# the total size of the patches, rather than patches * len(text). This
# means rather than shuffling strings around, we shuffle around
# pointers to fragments with fragment lists.
#
# When the fragment lists get too long, we collapse them. To do this
# efficiently, we do all our operations inside a buffer created by
# mmap and simply use memmove. This avoids creating a bunch of large
# temporary string buffers.


def _pull(dst, src, l):  # pull l bytes from src
    while l:
        f = src.pop()
        if f[0] > l:  # do we need to split?
            src.append((f[0] - l, f[1] + l))
            dst.append((l, f[1]))
            return
        dst.append(f)
        l -= f[0]


def _move(m, dest, src, count):
    """move count bytes from src to dest

    The file pointer is left at the end of dest.
    """
    m.seek(src)
    buf = m.read(count)
    m.seek(dest)
    m.write(buf)


def _collect(m, buf, list):
    start = buf
    for l, p in reversed(list):
        _move(m, buf, p, l)
        buf += l
    return (buf - start, start)


def patches(a, bins):
    if not bins:
        return a

    plens = [len(x) for x in bins]
    pl = sum(plens)
    bl = len(a) + pl
    tl = bl + bl + pl  # enough for the patches and two working texts
    b1, b2 = 0, bl

    if not tl:
        return a

    m = stringio()

    # load our original text
    m.write(a)
    frags = [(len(a), b1)]

    # copy all the patches into our segment so we can memmove from them
    pos = b2 + bl
    m.seek(pos)
    for p in bins:
        m.write(p)

    for plen in plens:
        # if our list gets too long, execute it
        if len(frags) > 128:
            b2, b1 = b1, b2
            frags = [_collect(m, b1, frags)]

        new = []
        end = pos + plen
        last = 0
        while pos < end:
            m.seek(pos)
            try:
                p1, p2, l = struct.unpack(b">lll", m.read(12))
            except struct.error:
                raise mpatchError(b"patch cannot be decoded")
            _pull(new, frags, p1 - last)  # what didn't change
            _pull([], frags, p2 - p1)  # what got deleted
            new.append((l, pos + 12))  # what got added
            pos += l + 12
            last = p2
        frags.extend(reversed(new))  # what was left at the end

    t = _collect(m, b2, frags)

    m.seek(t[1])
    return m.read(t[0])


def patchedsize(orig, delta):
    outlen, last, bin = 0, 0, 0
    binend = len(delta)
    data = 12

    while data <= binend:
        decode = delta[bin : bin + 12]
        start, end, length = struct.unpack(b">lll", decode)
        if start > end:
            break
        bin = data + length
        data = bin + 12
        outlen += start - last
        last = end
        outlen += length

    if bin != binend:
        raise mpatchError(b"patch cannot be decoded")

    outlen += orig - last
    return outlen
