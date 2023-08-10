from __future__ import absolute_import

import cffi

ffi = cffi.FFI()
ffi.set_source(
    "mercurial.cffi._osutil",
    """
#include <sys/attr.h>
#include <sys/vnode.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>

typedef struct val_attrs {
    uint32_t          length;
    attribute_set_t   returned;
    attrreference_t   name_info;
    fsobj_type_t      obj_type;
    struct timespec   mtime;
    uint32_t          accessmask;
    off_t             datalength;
} __attribute__((aligned(4), packed)) val_attrs_t;
""",
    include_dirs=['mercurial'],
)
ffi.cdef(
    '''

typedef uint32_t attrgroup_t;

typedef struct attrlist {
    uint16_t     bitmapcount; /* number of attr. bit sets in list */
    uint16_t   reserved;    /* (to maintain 4-byte alignment) */
    attrgroup_t commonattr;  /* common attribute group */
    attrgroup_t volattr;     /* volume attribute group */
    attrgroup_t dirattr;     /* directory attribute group */
    attrgroup_t fileattr;    /* file attribute group */
    attrgroup_t forkattr;    /* fork attribute group */
    ...;
};

typedef struct attribute_set {
    ...;
} attribute_set_t;

typedef struct attrreference {
    int attr_dataoffset;
    int attr_length;
    ...;
} attrreference_t;

typedef int ... off_t;

typedef struct val_attrs {
    uint32_t          length;
    attribute_set_t   returned;
    attrreference_t   name_info;
    uint32_t          obj_type;
    struct timespec   mtime;
    uint32_t          accessmask;
    off_t             datalength;
    ...;
} val_attrs_t;

/* the exact layout of the above struct will be figured out during build time */

typedef int ... time_t;

typedef struct timespec {
    time_t tv_sec;
    ...;
};

int getattrlist(const char* path, struct attrlist * attrList, void * attrBuf,
                size_t attrBufSize, unsigned int options);

int getattrlistbulk(int dirfd, struct attrlist * attrList, void * attrBuf,
                    size_t attrBufSize, uint64_t options);

#define ATTR_BIT_MAP_COUNT ...
#define ATTR_CMN_NAME ...
#define ATTR_CMN_OBJTYPE ...
#define ATTR_CMN_MODTIME ...
#define ATTR_CMN_ACCESSMASK ...
#define ATTR_CMN_ERROR ...
#define ATTR_CMN_RETURNED_ATTRS ...
#define ATTR_FILE_DATALENGTH ...

#define VREG ...
#define VDIR ...
#define VLNK ...
#define VBLK ...
#define VCHR ...
#define VFIFO ...
#define VSOCK ...

#define S_IFMT ...

int open(const char *path, int oflag, int perm);
int close(int);

#define O_RDONLY ...
'''
)

if __name__ == '__main__':
    ffi.compile()
