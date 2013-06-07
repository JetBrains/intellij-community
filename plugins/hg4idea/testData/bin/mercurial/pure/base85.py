# base85.py: pure python base85 codec
#
# Copyright (C) 2009 Brendan Cully <brendan@kublai.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import struct

_b85chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ" \
            "abcdefghijklmnopqrstuvwxyz!#$%&()*+-;<=>?@^_`{|}~"
_b85chars2 = [(a + b) for a in _b85chars for b in _b85chars]
_b85dec = {}

def _mkb85dec():
    for i, c in enumerate(_b85chars):
        _b85dec[c] = i

def b85encode(text, pad=False):
    """encode text in base85 format"""
    l = len(text)
    r = l % 4
    if r:
        text += '\0' * (4 - r)
    longs = len(text) >> 2
    words = struct.unpack('>%dL' % (longs), text)

    out = ''.join(_b85chars[(word // 52200625) % 85] +
                  _b85chars2[(word // 7225) % 7225] +
                  _b85chars2[word % 7225]
                  for word in words)

    if pad:
        return out

    # Trim padding
    olen = l % 4
    if olen:
        olen += 1
    olen += l // 4 * 5
    return out[:olen]

def b85decode(text):
    """decode base85-encoded text"""
    if not _b85dec:
        _mkb85dec()

    l = len(text)
    out = []
    for i in range(0, len(text), 5):
        chunk = text[i:i + 5]
        acc = 0
        for j, c in enumerate(chunk):
            try:
                acc = acc * 85 + _b85dec[c]
            except KeyError:
                raise ValueError('bad base85 character at position %d'
                                 % (i + j))
        if acc > 4294967295:
            raise ValueError('Base85 overflow in hunk starting at byte %d' % i)
        out.append(acc)

    # Pad final chunk if necessary
    cl = l % 5
    if cl:
        acc *= 85 ** (5 - cl)
        if cl > 1:
            acc += 0xffffff >> (cl - 2) * 8
        out[-1] = acc

    out = struct.pack('>%dL' % (len(out)), *out)
    if cl:
        out = out[:-(5 - cl)]

    return out
