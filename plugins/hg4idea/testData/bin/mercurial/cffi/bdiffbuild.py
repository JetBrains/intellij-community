from __future__ import absolute_import

import cffi
import os

ffi = cffi.FFI()
with open(
    os.path.join(os.path.join(os.path.dirname(__file__), '..'), 'bdiff.c')
) as f:
    ffi.set_source(
        "mercurial.cffi._bdiff", f.read(), include_dirs=['mercurial']
    )
ffi.cdef(
    """
struct bdiff_line {
    int hash, n, e;
    ssize_t len;
    const char *l;
};

struct bdiff_hunk;
struct bdiff_hunk {
    int a1, a2, b1, b2;
    struct bdiff_hunk *next;
};

int bdiff_splitlines(const char *a, ssize_t len, struct bdiff_line **lr);
int bdiff_diff(struct bdiff_line *a, int an, struct bdiff_line *b, int bn,
    struct bdiff_hunk *base);
void bdiff_freehunks(struct bdiff_hunk *l);
void free(void*);
"""
)

if __name__ == '__main__':
    ffi.compile()
