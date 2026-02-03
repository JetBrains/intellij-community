# osutil.py - CFFI version of osutil.c
#
# Copyright 2016 Maciej Fijalkowski <fijall@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import os
import stat as statmod

from ..pure.osutil import *

from .. import pycompat

if pycompat.isdarwin:
    from . import _osutil  # pytype: disable=import-error

    ffi = _osutil.ffi
    lib = _osutil.lib

    listdir_batch_size = 4096
    # tweakable number, only affects performance, which chunks
    # of bytes do we get back from getattrlistbulk

    attrkinds = [None] * 20  # we need the max no for enum VXXX, 20 is plenty

    attrkinds[lib.VREG] = statmod.S_IFREG
    attrkinds[lib.VDIR] = statmod.S_IFDIR
    attrkinds[lib.VLNK] = statmod.S_IFLNK
    attrkinds[lib.VBLK] = statmod.S_IFBLK
    attrkinds[lib.VCHR] = statmod.S_IFCHR
    attrkinds[lib.VFIFO] = statmod.S_IFIFO
    attrkinds[lib.VSOCK] = statmod.S_IFSOCK

    class stat_res:
        def __init__(self, st_mode, st_mtime, st_size):
            self.st_mode = st_mode
            self.st_mtime = st_mtime
            self.st_size = st_size

    tv_sec_ofs = ffi.offsetof(b"struct timespec", b"tv_sec")
    buf = ffi.new(b"char[]", listdir_batch_size)

    def listdirinternal(dfd, req, stat, skip):
        ret = []
        while True:
            r = lib.getattrlistbulk(dfd, req, buf, listdir_batch_size, 0)
            if r == 0:
                break
            if r == -1:
                raise OSError(ffi.errno, os.strerror(ffi.errno))
            cur = ffi.cast(b"val_attrs_t*", buf)
            for i in range(r):
                lgt = cur.length
                assert lgt == ffi.cast(b'uint32_t*', cur)[0]
                ofs = cur.name_info.attr_dataoffset
                str_lgt = cur.name_info.attr_length
                base_ofs = ffi.offsetof(b'val_attrs_t', b'name_info')
                name = bytes(
                    ffi.buffer(
                        ffi.cast(b"char*", cur) + base_ofs + ofs, str_lgt - 1
                    )
                )
                tp = attrkinds[cur.obj_type]
                if name == b"." or name == b"..":
                    continue
                if skip == name and tp == statmod.S_ISDIR:
                    return []
                if stat:
                    mtime = cur.mtime.tv_sec
                    mode = (cur.accessmask & ~lib.S_IFMT) | tp
                    ret.append(
                        (
                            name,
                            tp,
                            stat_res(
                                st_mode=mode,
                                st_mtime=mtime,
                                st_size=cur.datalength,
                            ),
                        )
                    )
                else:
                    ret.append((name, tp))
                cur = ffi.cast(
                    b"val_attrs_t*", int(ffi.cast(b"intptr_t", cur)) + lgt
                )
        return ret

    def listdir(path, stat=False, skip=None):
        req = ffi.new(b"struct attrlist*")
        req.bitmapcount = lib.ATTR_BIT_MAP_COUNT
        req.commonattr = (
            lib.ATTR_CMN_RETURNED_ATTRS
            | lib.ATTR_CMN_NAME
            | lib.ATTR_CMN_OBJTYPE
            | lib.ATTR_CMN_ACCESSMASK
            | lib.ATTR_CMN_MODTIME
        )
        req.fileattr = lib.ATTR_FILE_DATALENGTH
        dfd = lib.open(path, lib.O_RDONLY, 0)
        if dfd == -1:
            raise OSError(ffi.errno, os.strerror(ffi.errno))

        try:
            ret = listdirinternal(dfd, req, stat, skip)
        finally:
            try:
                lib.close(dfd)
            except BaseException:
                pass  # we ignore all the errors from closing, not
                # much we can do about that
        return ret
