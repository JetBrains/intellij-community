# mpatch.py - CFFI implementation of mpatch.c
#
# Copyright 2016 Maciej Fijalkowski <fijall@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from typing import List

from ..pure.mpatch import *
from ..pure.mpatch import mpatchError  # silence pyflakes
from . import _mpatch  # pytype: disable=import-error

ffi = _mpatch.ffi
lib = _mpatch.lib


@ffi.def_extern()
def cffi_get_next_item(arg, pos):
    all, bins = ffi.from_handle(arg)
    container = ffi.new(b"struct mpatch_flist*[1]")
    to_pass = ffi.new(b"char[]", str(bins[pos]))
    all.append(to_pass)
    r = lib.mpatch_decode(to_pass, len(to_pass) - 1, container)
    if r < 0:
        return ffi.NULL
    return container[0]


def patches(text: bytes, bins: List[bytes]) -> bytes:
    lgt = len(bins)
    all = []
    if not lgt:
        return text
    arg = (all, bins)
    patch = lib.mpatch_fold(ffi.new_handle(arg), lib.cffi_get_next_item, 0, lgt)
    if not patch:
        raise mpatchError(b"cannot decode chunk")
    outlen = lib.mpatch_calcsize(len(text), patch)
    if outlen < 0:
        lib.mpatch_lfree(patch)
        raise mpatchError(b"inconsistency detected")
    buf = ffi.new(b"char[]", outlen)
    if lib.mpatch_apply(buf, text, len(text), patch) < 0:
        lib.mpatch_lfree(patch)
        raise mpatchError(b"error applying patches")
    res = ffi.buffer(buf, outlen)[:]
    lib.mpatch_lfree(patch)
    return res
