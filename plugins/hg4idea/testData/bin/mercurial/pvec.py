# pvec.py - probabilistic vector clocks for Mercurial
#
# Copyright 2012 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''
A "pvec" is a changeset property based on the theory of vector clocks
that can be compared to discover relatedness without consulting a
graph. This can be useful for tasks like determining how a
disconnected patch relates to a repository.

Currently a pvec consist of 448 bits, of which 24 are 'depth' and the
remainder are a bit vector. It is represented as a 70-character base85
string.

Construction:

- a root changeset has a depth of 0 and a bit vector based on its hash
- a normal commit has a changeset where depth is increased by one and
  one bit vector bit is flipped based on its hash
- a merge changeset pvec is constructed by copying changes from one pvec into
  the other to balance its depth

Properties:

- for linear changes, difference in depth is always <= hamming distance
- otherwise, changes are probably divergent
- when hamming distance is < 200, we can reliably detect when pvecs are near

Issues:

- hamming distance ceases to work over distances of ~ 200
- detecting divergence is less accurate when the common ancestor is very close
  to either revision or total distance is high
- this could probably be improved by modeling the relation between
  delta and hdist

Uses:

- a patch pvec can be used to locate the nearest available common ancestor for
  resolving conflicts
- ordering of patches can be established without a DAG
- two head pvecs can be compared to determine whether push/pull/merge is needed
  and approximately how many changesets are involved
- can be used to find a heuristic divergence measure between changesets on
  different branches
'''

import base85, util
from node import nullrev

_size = 448 # 70 chars b85-encoded
_bytes = _size / 8
_depthbits = 24
_depthbytes = _depthbits / 8
_vecbytes = _bytes - _depthbytes
_vecbits = _vecbytes * 8
_radius = (_vecbits - 30) / 2 # high probability vectors are related

def _bin(bs):
    '''convert a bytestring to a long'''
    v = 0
    for b in bs:
        v = v * 256 + ord(b)
    return v

def _str(v, l):
    bs = ""
    for p in xrange(l):
        bs = chr(v & 255) + bs
        v >>= 8
    return bs

def _split(b):
    '''depth and bitvec'''
    return _bin(b[:_depthbytes]), _bin(b[_depthbytes:])

def _join(depth, bitvec):
    return _str(depth, _depthbytes) + _str(bitvec, _vecbytes)

def _hweight(x):
    c = 0
    while x:
        if x & 1:
            c += 1
        x >>= 1
    return c
_htab = [_hweight(x) for x in xrange(256)]

def _hamming(a, b):
    '''find the hamming distance between two longs'''
    d = a ^ b
    c = 0
    while d:
        c += _htab[d & 0xff]
        d >>= 8
    return c

def _mergevec(x, y, c):
    # Ideally, this function would be x ^ y ^ ancestor, but finding
    # ancestors is a nuisance. So instead we find the minimal number
    # of changes to balance the depth and hamming distance

    d1, v1 = x
    d2, v2 = y
    if d1 < d2:
        d1, d2, v1, v2 = d2, d1, v2, v1

    hdist = _hamming(v1, v2)
    ddist = d1 - d2
    v = v1
    m = v1 ^ v2 # mask of different bits
    i = 1

    if hdist > ddist:
        # if delta = 10 and hdist = 100, then we need to go up 55 steps
        # to the ancestor and down 45
        changes = (hdist - ddist + 1) / 2
    else:
        # must make at least one change
        changes = 1
    depth = d1 + changes

    # copy changes from v2
    if m:
        while changes:
            if m & i:
                v ^= i
                changes -= 1
            i <<= 1
    else:
        v = _flipbit(v, c)

    return depth, v

def _flipbit(v, node):
    # converting bit strings to longs is slow
    bit = (hash(node) & 0xffffffff) % _vecbits
    return v ^ (1<<bit)

def ctxpvec(ctx):
    '''construct a pvec for ctx while filling in the cache'''
    r = ctx._repo
    if not util.safehasattr(r, "_pveccache"):
        r._pveccache = {}
    pvc = r._pveccache
    if ctx.rev() not in pvc:
        cl = r.changelog
        for n in xrange(ctx.rev() + 1):
            if n not in pvc:
                node = cl.node(n)
                p1, p2 = cl.parentrevs(n)
                if p1 == nullrev:
                    # start with a 'random' vector at root
                    pvc[n] = (0, _bin((node * 3)[:_vecbytes]))
                elif p2 == nullrev:
                    d, v = pvc[p1]
                    pvc[n] = (d + 1, _flipbit(v, node))
                else:
                    pvc[n] = _mergevec(pvc[p1], pvc[p2], node)
    bs = _join(*pvc[ctx.rev()])
    return pvec(base85.b85encode(bs))

class pvec(object):
    def __init__(self, hashorctx):
        if isinstance(hashorctx, str):
            self._bs = hashorctx
            self._depth, self._vec = _split(base85.b85decode(hashorctx))
        else:
            self._vec = ctxpvec(hashorctx)

    def __str__(self):
        return self._bs

    def __eq__(self, b):
        return self._vec == b._vec and self._depth == b._depth

    def __lt__(self, b):
        delta = b._depth - self._depth
        if delta < 0:
            return False # always correct
        if _hamming(self._vec, b._vec) > delta:
            return False
        return True

    def __gt__(self, b):
        return b < self

    def __or__(self, b):
        delta = abs(b._depth - self._depth)
        if _hamming(self._vec, b._vec) <= delta:
            return False
        return True

    def __sub__(self, b):
        if self | b:
            raise ValueError("concurrent pvecs")
        return self._depth - b._depth

    def distance(self, b):
        d = abs(b._depth - self._depth)
        h = _hamming(self._vec, b._vec)
        return max(d, h)

    def near(self, b):
        dist = abs(b.depth - self._depth)
        if dist > _radius or _hamming(self._vec, b._vec) > _radius:
            return False
