/*
 osutil.c - native operating system services

 Copyright 2007 Olivia Mackall and others

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#define _ATFILE_SOURCE
#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <io.h>
#include <windows.h>
#else
#include <dirent.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#ifdef HAVE_LINUX_STATFS
#include <linux/magic.h>
#include <sys/vfs.h>
#endif
#ifdef HAVE_BSD_STATFS
#include <sys/mount.h>
#include <sys/param.h>
#endif
#endif

#ifdef __APPLE__
#include <sys/attr.h>
#include <sys/vnode.h>
#endif

#include "util.h"

/* some platforms lack the PATH_MAX definition (eg. GNU/Hurd) */
#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

#ifdef _WIN32
/*
stat struct compatible with hg expectations
Mercurial only uses st_mode, st_size and st_mtime
the rest is kept to minimize changes between implementations
*/
struct hg_stat {
	int st_dev;
	int st_mode;
	int st_nlink;
	__int64 st_size;
	int st_mtime;
	int st_ctime;
};
struct listdir_stat {
	PyObject_HEAD
	struct hg_stat st;
};
#else
struct listdir_stat {
	PyObject_HEAD
	struct stat st;
};
#endif

#define listdir_slot(name) \
	static PyObject *listdir_stat_##name(PyObject *self, void *x) \
	{ \
		return PyLong_FromLong(((struct listdir_stat *)self)->st.name); \
	}

listdir_slot(st_dev)
listdir_slot(st_mode)
listdir_slot(st_nlink)
#ifdef _WIN32
static PyObject *listdir_stat_st_size(PyObject *self, void *x)
{
	return PyLong_FromLongLong(
		(PY_LONG_LONG)((struct listdir_stat *)self)->st.st_size);
}
#else
listdir_slot(st_size)
#endif
listdir_slot(st_mtime)
listdir_slot(st_ctime)

static struct PyGetSetDef listdir_stat_getsets[] = {
	{"st_dev", listdir_stat_st_dev, 0, 0, 0},
	{"st_mode", listdir_stat_st_mode, 0, 0, 0},
	{"st_nlink", listdir_stat_st_nlink, 0, 0, 0},
	{"st_size", listdir_stat_st_size, 0, 0, 0},
	{"st_mtime", listdir_stat_st_mtime, 0, 0, 0},
	{"st_ctime", listdir_stat_st_ctime, 0, 0, 0},
	{0, 0, 0, 0, 0}
};

static PyObject *listdir_stat_new(PyTypeObject *t, PyObject *a, PyObject *k)
{
	return t->tp_alloc(t, 0);
}

static void listdir_stat_dealloc(PyObject *o)
{
	Py_TYPE(o)->tp_free(o);
}

static PyObject *listdir_stat_getitem(PyObject *self, PyObject *key)
{
	long index = PyLong_AsLong(key);
	if (index == -1 && PyErr_Occurred()) {
		return NULL;
	}
	if (index != 8) {
		PyErr_Format(PyExc_IndexError, "osutil.stat objects only "
		                               "support stat.ST_MTIME in "
		                               "__getitem__");
		return NULL;
	}
	return listdir_stat_st_mtime(self, NULL);
}

static PyMappingMethods listdir_stat_type_mapping_methods = {
	(lenfunc)NULL,             /* mp_length */
	(binaryfunc)listdir_stat_getitem,       /* mp_subscript */
	(objobjargproc)NULL,    /* mp_ass_subscript */
};

static PyTypeObject listdir_stat_type = {
	PyVarObject_HEAD_INIT(NULL, 0) /* header */
	"osutil.stat",             /*tp_name*/
	sizeof(struct listdir_stat), /*tp_basicsize*/
	0,                         /*tp_itemsize*/
	(destructor)listdir_stat_dealloc, /*tp_dealloc*/
	0,                         /*tp_print*/
	0,                         /*tp_getattr*/
	0,                         /*tp_setattr*/
	0,                         /*tp_compare*/
	0,                         /*tp_repr*/
	0,                         /*tp_as_number*/
	0,                         /*tp_as_sequence*/
	&listdir_stat_type_mapping_methods, /*tp_as_mapping*/
	0,                         /*tp_hash */
	0,                         /*tp_call*/
	0,                         /*tp_str*/
	0,                         /*tp_getattro*/
	0,                         /*tp_setattro*/
	0,                         /*tp_as_buffer*/
	Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, /*tp_flags*/
	"stat objects",            /* tp_doc */
	0,                         /* tp_traverse */
	0,                         /* tp_clear */
	0,                         /* tp_richcompare */
	0,                         /* tp_weaklistoffset */
	0,                         /* tp_iter */
	0,                         /* tp_iternext */
	0,                         /* tp_methods */
	0,                         /* tp_members */
	listdir_stat_getsets,      /* tp_getset */
	0,                         /* tp_base */
	0,                         /* tp_dict */
	0,                         /* tp_descr_get */
	0,                         /* tp_descr_set */
	0,                         /* tp_dictoffset */
	0,                         /* tp_init */
	0,                         /* tp_alloc */
	listdir_stat_new,          /* tp_new */
};

#ifdef _WIN32

static int to_python_time(const FILETIME *tm)
{
	/* number of seconds between epoch and January 1 1601 */
	const __int64 a0 = (__int64)134774L * (__int64)24L * (__int64)3600L;
	/* conversion factor from 100ns to 1s */
	const __int64 a1 = 10000000;
	/* explicit (int) cast to suspend compiler warnings */
	return (int)((((__int64)tm->dwHighDateTime << 32)
			+ tm->dwLowDateTime) / a1 - a0);
}

static PyObject *make_item(const WIN32_FIND_DATAA *fd, int wantstat)
{
	PyObject *py_st;
	struct hg_stat *stp;

	int kind = (fd->dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)
		? _S_IFDIR : _S_IFREG;

	if (!wantstat)
		return Py_BuildValue("yi", fd->cFileName, kind);

	py_st = PyObject_CallObject((PyObject *)&listdir_stat_type, NULL);
	if (!py_st)
		return NULL;

	stp = &((struct listdir_stat *)py_st)->st;
	/*
	use kind as st_mode
	rwx bits on Win32 are meaningless
	and Hg does not use them anyway
	*/
	stp->st_mode  = kind;
	stp->st_mtime = to_python_time(&fd->ftLastWriteTime);
	stp->st_ctime = to_python_time(&fd->ftCreationTime);
	if (kind == _S_IFREG)
		stp->st_size = ((__int64)fd->nFileSizeHigh << 32)
				+ fd->nFileSizeLow;
	return Py_BuildValue("yiN", fd->cFileName,
		kind, py_st);
}

static PyObject *_listdir(char *path, Py_ssize_t plen, int wantstat, char *skip)
{
	PyObject *rval = NULL; /* initialize - return value */
	PyObject *list;
	HANDLE fh;
	WIN32_FIND_DATAA fd;
	char *pattern;

	/* build the path + \* pattern string */
	pattern = PyMem_Malloc(plen + 3); /* path + \* + \0 */
	if (!pattern) {
		PyErr_NoMemory();
		goto error_nomem;
	}
	memcpy(pattern, path, plen);

	if (plen > 0) {
		char c = path[plen-1];
		if (c != ':' && c != '/' && c != '\\')
			pattern[plen++] = '\\';
	}
	pattern[plen++] = '*';
	pattern[plen] = '\0';

	fh = FindFirstFileA(pattern, &fd);
	if (fh == INVALID_HANDLE_VALUE) {
		PyErr_SetFromWindowsErrWithFilename(GetLastError(), path);
		goto error_file;
	}

	list = PyList_New(0);
	if (!list)
		goto error_list;

	do {
		PyObject *item;

		if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
			if (!strcmp(fd.cFileName, ".")
			|| !strcmp(fd.cFileName, ".."))
				continue;

			if (skip && !strcmp(fd.cFileName, skip)) {
				rval = PyList_New(0);
				goto error;
			}
		}

		item = make_item(&fd, wantstat);
		if (!item)
			goto error;

		if (PyList_Append(list, item)) {
			Py_XDECREF(item);
			goto error;
		}

		Py_XDECREF(item);
	} while (FindNextFileA(fh, &fd));

	if (GetLastError() != ERROR_NO_MORE_FILES) {
		PyErr_SetFromWindowsErrWithFilename(GetLastError(), path);
		goto error;
	}

	rval = list;
	Py_XINCREF(rval);
error:
	Py_XDECREF(list);
error_list:
	FindClose(fh);
error_file:
	PyMem_Free(pattern);
error_nomem:
	return rval;
}

#else

int entkind(struct dirent *ent)
{
#ifdef DT_REG
	switch (ent->d_type) {
	case DT_REG: return S_IFREG;
	case DT_DIR: return S_IFDIR;
	case DT_LNK: return S_IFLNK;
	case DT_BLK: return S_IFBLK;
	case DT_CHR: return S_IFCHR;
	case DT_FIFO: return S_IFIFO;
	case DT_SOCK: return S_IFSOCK;
	}
#endif
	return -1;
}

static PyObject *makestat(const struct stat *st)
{
	PyObject *stat;

	stat = PyObject_CallObject((PyObject *)&listdir_stat_type, NULL);
	if (stat)
		memcpy(&((struct listdir_stat *)stat)->st, st, sizeof(*st));
	return stat;
}

static PyObject *_listdir_stat(char *path, int pathlen, int keepstat,
			       char *skip)
{
	PyObject *list, *elem, *ret = NULL;
	char fullpath[PATH_MAX + 10];
	int kind, err;
	struct stat st;
	struct dirent *ent;
	DIR *dir;
#ifdef AT_SYMLINK_NOFOLLOW
	int dfd = -1;
#endif

	if (pathlen >= PATH_MAX) {
		errno = ENAMETOOLONG;
		PyErr_SetFromErrnoWithFilename(PyExc_OSError, path);
		goto error_value;
	}
	strncpy(fullpath, path, PATH_MAX);
	fullpath[pathlen] = '/';

#ifdef AT_SYMLINK_NOFOLLOW
	dfd = open(path, O_RDONLY);
	if (dfd == -1) {
		PyErr_SetFromErrnoWithFilename(PyExc_OSError, path);
		goto error_value;
	}
	dir = fdopendir(dfd);
#else
	dir = opendir(path);
#endif
	if (!dir) {
		PyErr_SetFromErrnoWithFilename(PyExc_OSError, path);
		goto error_dir;
	}

	list = PyList_New(0);
	if (!list)
		goto error_list;

	while ((ent = readdir(dir))) {
		if (!strcmp(ent->d_name, ".") || !strcmp(ent->d_name, ".."))
			continue;

		kind = entkind(ent);
		if (kind == -1 || keepstat) {
#ifdef AT_SYMLINK_NOFOLLOW
			err = fstatat(dfd, ent->d_name, &st,
				      AT_SYMLINK_NOFOLLOW);
#else
			strncpy(fullpath + pathlen + 1, ent->d_name,
				PATH_MAX - pathlen);
			fullpath[PATH_MAX] = '\0';
			err = lstat(fullpath, &st);
#endif
			if (err == -1) {
				/* race with file deletion? */
				if (errno == ENOENT)
					continue;
				strncpy(fullpath + pathlen + 1, ent->d_name,
					PATH_MAX - pathlen);
				fullpath[PATH_MAX] = 0;
				PyErr_SetFromErrnoWithFilename(PyExc_OSError,
							       fullpath);
				goto error;
			}
			kind = st.st_mode & S_IFMT;
		}

		/* quit early? */
		if (skip && kind == S_IFDIR && !strcmp(ent->d_name, skip)) {
			ret = PyList_New(0);
			goto error;
		}

		if (keepstat) {
			PyObject *stat = makestat(&st);
			if (!stat)
				goto error;
			elem = Py_BuildValue("yiN", ent->d_name,
					     kind, stat);
		} else
			elem = Py_BuildValue("yi", ent->d_name,
					     kind);
		if (!elem)
			goto error;

		PyList_Append(list, elem);
		Py_DECREF(elem);
	}

	ret = list;
	Py_INCREF(ret);

error:
	Py_DECREF(list);
error_list:
	closedir(dir);
	/* closedir also closes its dirfd */
	goto error_value;
error_dir:
#ifdef AT_SYMLINK_NOFOLLOW
	close(dfd);
#endif
error_value:
	return ret;
}

#ifdef __APPLE__

typedef struct {
	u_int32_t length;
	attrreference_t name;
	fsobj_type_t obj_type;
	struct timespec mtime;
#if __LITTLE_ENDIAN__
	mode_t access_mask;
	uint16_t padding;
#else
	uint16_t padding;
	mode_t access_mask;
#endif
	off_t size;
} __attribute__((packed)) attrbuf_entry;

int attrkind(attrbuf_entry *entry)
{
	switch (entry->obj_type) {
	case VREG: return S_IFREG;
	case VDIR: return S_IFDIR;
	case VLNK: return S_IFLNK;
	case VBLK: return S_IFBLK;
	case VCHR: return S_IFCHR;
	case VFIFO: return S_IFIFO;
	case VSOCK: return S_IFSOCK;
	}
	return -1;
}

/* get these many entries at a time */
#define LISTDIR_BATCH_SIZE 50

static PyObject *_listdir_batch(char *path, int pathlen, int keepstat,
				char *skip, bool *fallback)
{
	PyObject *list, *elem, *ret = NULL;
	int kind, err;
	unsigned long index;
	unsigned int count, old_state, new_state;
	bool state_seen = false;
	attrbuf_entry *entry;
	/* from the getattrlist(2) man page: a path can be no longer than
	   (NAME_MAX * 3 + 1) bytes. Also, "The getattrlist() function will
	   silently truncate attribute data if attrBufSize is too small." So
	   pass in a buffer big enough for the worst case. */
	char attrbuf[LISTDIR_BATCH_SIZE * (sizeof(attrbuf_entry) + NAME_MAX * 3 + 1)];
	unsigned int basep_unused;

	struct stat st;
	int dfd = -1;

	/* these must match the attrbuf_entry struct, otherwise you'll end up
	   with garbage */
	struct attrlist requested_attr = {0};
	requested_attr.bitmapcount = ATTR_BIT_MAP_COUNT;
	requested_attr.commonattr = (ATTR_CMN_NAME | ATTR_CMN_OBJTYPE |
				     ATTR_CMN_MODTIME | ATTR_CMN_ACCESSMASK);
	requested_attr.fileattr = ATTR_FILE_DATALENGTH;

	*fallback = false;

	if (pathlen >= PATH_MAX) {
		errno = ENAMETOOLONG;
		PyErr_SetFromErrnoWithFilename(PyExc_OSError, path);
		goto error_value;
	}

	dfd = open(path, O_RDONLY);
	if (dfd == -1) {
		PyErr_SetFromErrnoWithFilename(PyExc_OSError, path);
		goto error_value;
	}

	list = PyList_New(0);
	if (!list)
		goto error_dir;

	do {
		count = LISTDIR_BATCH_SIZE;
		err = getdirentriesattr(dfd, &requested_attr, &attrbuf,
					sizeof(attrbuf), &count, &basep_unused,
					&new_state, 0);
		if (err < 0) {
			if (errno == ENOTSUP) {
				/* We're on a filesystem that doesn't support
				   getdirentriesattr. Fall back to the
				   stat-based implementation. */
				*fallback = true;
			} else
				PyErr_SetFromErrnoWithFilename(PyExc_OSError, path);
			goto error;
		}

		if (!state_seen) {
			old_state = new_state;
			state_seen = true;
		} else if (old_state != new_state) {
			/* There's an edge case with getdirentriesattr. Consider
			   the following initial list of files:

			   a
			   b
			   <--
			   c
			   d

			   If the iteration is paused at the arrow, and b is
			   deleted before it is resumed, getdirentriesattr will
			   not return d at all!  Ordinarily we're expected to
			   restart the iteration from the beginning. To avoid
			   getting stuck in a retry loop here, fall back to
			   stat. */
			*fallback = true;
			goto error;
		}

		entry = (attrbuf_entry *)attrbuf;

		for (index = 0; index < count; index++) {
			char *filename = ((char *)&entry->name) +
				entry->name.attr_dataoffset;

			if (!strcmp(filename, ".") || !strcmp(filename, ".."))
				continue;

			kind = attrkind(entry);
			if (kind == -1) {
				PyErr_Format(PyExc_OSError,
					     "unknown object type %u for file "
					     "%s%s!",
					     entry->obj_type, path, filename);
				goto error;
			}

			/* quit early? */
			if (skip && kind == S_IFDIR && !strcmp(filename, skip)) {
				ret = PyList_New(0);
				goto error;
			}

			if (keepstat) {
				PyObject *stat = NULL;
				/* from the getattrlist(2) man page: "Only the
				   permission bits ... are valid". */
				st.st_mode = (entry->access_mask & ~S_IFMT) | kind;
				st.st_mtime = entry->mtime.tv_sec;
				st.st_size = entry->size;
				stat = makestat(&st);
				if (!stat)
					goto error;
				elem = Py_BuildValue("yiN",
						     filename, kind, stat);
			} else
				elem = Py_BuildValue("yi",
						     filename, kind);
			if (!elem)
				goto error;

			PyList_Append(list, elem);
			Py_DECREF(elem);

			entry = (attrbuf_entry *)((char *)entry + entry->length);
		}
	} while (err == 0);

	ret = list;
	Py_INCREF(ret);

error:
	Py_DECREF(list);
error_dir:
	close(dfd);
error_value:
	return ret;
}

#endif /* __APPLE__ */

static PyObject *_listdir(char *path, int pathlen, int keepstat, char *skip)
{
#ifdef __APPLE__
	PyObject *ret;
	bool fallback = false;

	ret = _listdir_batch(path, pathlen, keepstat, skip, &fallback);
	if (ret != NULL || !fallback)
		return ret;
#endif
	return _listdir_stat(path, pathlen, keepstat, skip);
}

static PyObject *statfiles(PyObject *self, PyObject *args)
{
	PyObject *names, *stats;
	Py_ssize_t i, count;

	if (!PyArg_ParseTuple(args, "O:statfiles", &names))
		return NULL;

	count = PySequence_Length(names);
	if (count == -1) {
		PyErr_SetString(PyExc_TypeError, "not a sequence");
		return NULL;
	}

	stats = PyList_New(count);
	if (stats == NULL)
		return NULL;

	for (i = 0; i < count; i++) {
		PyObject *stat, *pypath;
		struct stat st;
		int ret, kind;
		char *path;

		/* With a large file count or on a slow filesystem,
		   don't block signals for long (issue4878). */
		if ((i % 1000) == 999 && PyErr_CheckSignals() == -1)
			goto bail;

		pypath = PySequence_GetItem(names, i);
		if (!pypath)
			goto bail;
		path = PyBytes_AsString(pypath);
		if (path == NULL) {
			Py_DECREF(pypath);
			PyErr_SetString(PyExc_TypeError, "not a string");
			goto bail;
		}
		ret = lstat(path, &st);
		Py_DECREF(pypath);
		kind = st.st_mode & S_IFMT;
		if (ret != -1 && (kind == S_IFREG || kind == S_IFLNK)) {
			stat = makestat(&st);
			if (stat == NULL)
				goto bail;
			PyList_SET_ITEM(stats, i, stat);
		} else {
			Py_INCREF(Py_None);
			PyList_SET_ITEM(stats, i, Py_None);
		}
	}

	return stats;

bail:
	Py_DECREF(stats);
	return NULL;
}

/* allow disabling setprocname via compiler flags */
#ifndef SETPROCNAME_USE_NONE
#if defined(HAVE_SETPROCTITLE)
/* setproctitle is the first choice - available in FreeBSD */
#define SETPROCNAME_USE_SETPROCTITLE
#else
#define SETPROCNAME_USE_NONE
#endif
#endif /* ndef SETPROCNAME_USE_NONE */

#ifndef SETPROCNAME_USE_NONE
static PyObject *setprocname(PyObject *self, PyObject *args)
{
	const char *name = NULL;
	if (!PyArg_ParseTuple(args, "y", &name))
		return NULL;

#if defined(SETPROCNAME_USE_SETPROCTITLE)
	setproctitle("%s", name);
#endif

	Py_RETURN_NONE;
}
#endif /* ndef SETPROCNAME_USE_NONE */

#if defined(HAVE_BSD_STATFS)
static const char *describefstype(const struct statfs *pbuf)
{
	/* BSD or OSX provides a f_fstypename field */
	return pbuf->f_fstypename;
}
#elif defined(HAVE_LINUX_STATFS)
static const char *describefstype(const struct statfs *pbuf)
{
	/* Begin of Linux filesystems */
#ifdef ADFS_SUPER_MAGIC
	if (pbuf->f_type == ADFS_SUPER_MAGIC)
		return "adfs";
#endif
#ifdef AFFS_SUPER_MAGIC
	if (pbuf->f_type == AFFS_SUPER_MAGIC)
		return "affs";
#endif
#ifdef AUTOFS_SUPER_MAGIC
	if (pbuf->f_type == AUTOFS_SUPER_MAGIC)
		return "autofs";
#endif
#ifdef BDEVFS_MAGIC
	if (pbuf->f_type == BDEVFS_MAGIC)
		return "bdevfs";
#endif
#ifdef BEFS_SUPER_MAGIC
	if (pbuf->f_type == BEFS_SUPER_MAGIC)
		return "befs";
#endif
#ifdef BFS_MAGIC
	if (pbuf->f_type == BFS_MAGIC)
		return "bfs";
#endif
#ifdef BINFMTFS_MAGIC
	if (pbuf->f_type == BINFMTFS_MAGIC)
		return "binfmtfs";
#endif
#ifdef BTRFS_SUPER_MAGIC
	if (pbuf->f_type == BTRFS_SUPER_MAGIC)
		return "btrfs";
#endif
#ifdef CGROUP_SUPER_MAGIC
	if (pbuf->f_type == CGROUP_SUPER_MAGIC)
		return "cgroup";
#endif
#ifdef CIFS_MAGIC_NUMBER
	if (pbuf->f_type == CIFS_MAGIC_NUMBER)
		return "cifs";
#endif
#ifdef CODA_SUPER_MAGIC
	if (pbuf->f_type == CODA_SUPER_MAGIC)
		return "coda";
#endif
#ifdef COH_SUPER_MAGIC
	if (pbuf->f_type == COH_SUPER_MAGIC)
		return "coh";
#endif
#ifdef CRAMFS_MAGIC
	if (pbuf->f_type == CRAMFS_MAGIC)
		return "cramfs";
#endif
#ifdef DEBUGFS_MAGIC
	if (pbuf->f_type == DEBUGFS_MAGIC)
		return "debugfs";
#endif
#ifdef DEVFS_SUPER_MAGIC
	if (pbuf->f_type == DEVFS_SUPER_MAGIC)
		return "devfs";
#endif
#ifdef DEVPTS_SUPER_MAGIC
	if (pbuf->f_type == DEVPTS_SUPER_MAGIC)
		return "devpts";
#endif
#ifdef EFIVARFS_MAGIC
	if (pbuf->f_type == EFIVARFS_MAGIC)
		return "efivarfs";
#endif
#ifdef EFS_SUPER_MAGIC
	if (pbuf->f_type == EFS_SUPER_MAGIC)
		return "efs";
#endif
#ifdef EXT_SUPER_MAGIC
	if (pbuf->f_type == EXT_SUPER_MAGIC)
		return "ext";
#endif
#ifdef EXT2_OLD_SUPER_MAGIC
	if (pbuf->f_type == EXT2_OLD_SUPER_MAGIC)
		return "ext2";
#endif
#ifdef EXT2_SUPER_MAGIC
	if (pbuf->f_type == EXT2_SUPER_MAGIC)
		return "ext2";
#endif
#ifdef EXT3_SUPER_MAGIC
	if (pbuf->f_type == EXT3_SUPER_MAGIC)
		return "ext3";
#endif
#ifdef EXT4_SUPER_MAGIC
	if (pbuf->f_type == EXT4_SUPER_MAGIC)
		return "ext4";
#endif
#ifdef F2FS_SUPER_MAGIC
	if (pbuf->f_type == F2FS_SUPER_MAGIC)
		return "f2fs";
#endif
#ifdef FUSE_SUPER_MAGIC
	if (pbuf->f_type == FUSE_SUPER_MAGIC)
		return "fuse";
#endif
#ifdef FUTEXFS_SUPER_MAGIC
	if (pbuf->f_type == FUTEXFS_SUPER_MAGIC)
		return "futexfs";
#endif
#ifdef HFS_SUPER_MAGIC
	if (pbuf->f_type == HFS_SUPER_MAGIC)
		return "hfs";
#endif
#ifdef HOSTFS_SUPER_MAGIC
	if (pbuf->f_type == HOSTFS_SUPER_MAGIC)
		return "hostfs";
#endif
#ifdef HPFS_SUPER_MAGIC
	if (pbuf->f_type == HPFS_SUPER_MAGIC)
		return "hpfs";
#endif
#ifdef HUGETLBFS_MAGIC
	if (pbuf->f_type == HUGETLBFS_MAGIC)
		return "hugetlbfs";
#endif
#ifdef ISOFS_SUPER_MAGIC
	if (pbuf->f_type == ISOFS_SUPER_MAGIC)
		return "isofs";
#endif
#ifdef JFFS2_SUPER_MAGIC
	if (pbuf->f_type == JFFS2_SUPER_MAGIC)
		return "jffs2";
#endif
#ifdef JFS_SUPER_MAGIC
	if (pbuf->f_type == JFS_SUPER_MAGIC)
		return "jfs";
#endif
#ifdef MINIX_SUPER_MAGIC
	if (pbuf->f_type == MINIX_SUPER_MAGIC)
		return "minix";
#endif
#ifdef MINIX2_SUPER_MAGIC
	if (pbuf->f_type == MINIX2_SUPER_MAGIC)
		return "minix2";
#endif
#ifdef MINIX3_SUPER_MAGIC
	if (pbuf->f_type == MINIX3_SUPER_MAGIC)
		return "minix3";
#endif
#ifdef MQUEUE_MAGIC
	if (pbuf->f_type == MQUEUE_MAGIC)
		return "mqueue";
#endif
#ifdef MSDOS_SUPER_MAGIC
	if (pbuf->f_type == MSDOS_SUPER_MAGIC)
		return "msdos";
#endif
#ifdef NCP_SUPER_MAGIC
	if (pbuf->f_type == NCP_SUPER_MAGIC)
		return "ncp";
#endif
#ifdef NFS_SUPER_MAGIC
	if (pbuf->f_type == NFS_SUPER_MAGIC)
		return "nfs";
#endif
#ifdef NILFS_SUPER_MAGIC
	if (pbuf->f_type == NILFS_SUPER_MAGIC)
		return "nilfs";
#endif
#ifdef NTFS_SB_MAGIC
	if (pbuf->f_type == NTFS_SB_MAGIC)
		return "ntfs-sb";
#endif
#ifdef OCFS2_SUPER_MAGIC
	if (pbuf->f_type == OCFS2_SUPER_MAGIC)
		return "ocfs2";
#endif
#ifdef OPENPROM_SUPER_MAGIC
	if (pbuf->f_type == OPENPROM_SUPER_MAGIC)
		return "openprom";
#endif
#ifdef OVERLAYFS_SUPER_MAGIC
	if (pbuf->f_type == OVERLAYFS_SUPER_MAGIC)
		return "overlay";
#endif
#ifdef PIPEFS_MAGIC
	if (pbuf->f_type == PIPEFS_MAGIC)
		return "pipefs";
#endif
#ifdef PROC_SUPER_MAGIC
	if (pbuf->f_type == PROC_SUPER_MAGIC)
		return "proc";
#endif
#ifdef PSTOREFS_MAGIC
	if (pbuf->f_type == PSTOREFS_MAGIC)
		return "pstorefs";
#endif
#ifdef QNX4_SUPER_MAGIC
	if (pbuf->f_type == QNX4_SUPER_MAGIC)
		return "qnx4";
#endif
#ifdef QNX6_SUPER_MAGIC
	if (pbuf->f_type == QNX6_SUPER_MAGIC)
		return "qnx6";
#endif
#ifdef RAMFS_MAGIC
	if (pbuf->f_type == RAMFS_MAGIC)
		return "ramfs";
#endif
#ifdef REISERFS_SUPER_MAGIC
	if (pbuf->f_type == REISERFS_SUPER_MAGIC)
		return "reiserfs";
#endif
#ifdef ROMFS_MAGIC
	if (pbuf->f_type == ROMFS_MAGIC)
		return "romfs";
#endif
#ifdef SECURITYFS_MAGIC
	if (pbuf->f_type == SECURITYFS_MAGIC)
		return "securityfs";
#endif
#ifdef SELINUX_MAGIC
	if (pbuf->f_type == SELINUX_MAGIC)
		return "selinux";
#endif
#ifdef SMACK_MAGIC
	if (pbuf->f_type == SMACK_MAGIC)
		return "smack";
#endif
#ifdef SMB_SUPER_MAGIC
	if (pbuf->f_type == SMB_SUPER_MAGIC)
		return "smb";
#endif
#ifdef SOCKFS_MAGIC
	if (pbuf->f_type == SOCKFS_MAGIC)
		return "sockfs";
#endif
#ifdef SQUASHFS_MAGIC
	if (pbuf->f_type == SQUASHFS_MAGIC)
		return "squashfs";
#endif
#ifdef SYSFS_MAGIC
	if (pbuf->f_type == SYSFS_MAGIC)
		return "sysfs";
#endif
#ifdef SYSV2_SUPER_MAGIC
	if (pbuf->f_type == SYSV2_SUPER_MAGIC)
		return "sysv2";
#endif
#ifdef SYSV4_SUPER_MAGIC
	if (pbuf->f_type == SYSV4_SUPER_MAGIC)
		return "sysv4";
#endif
#ifdef TMPFS_MAGIC
	if (pbuf->f_type == TMPFS_MAGIC)
		return "tmpfs";
#endif
#ifdef UDF_SUPER_MAGIC
	if (pbuf->f_type == UDF_SUPER_MAGIC)
		return "udf";
#endif
#ifdef UFS_MAGIC
	if (pbuf->f_type == UFS_MAGIC)
		return "ufs";
#endif
#ifdef USBDEVICE_SUPER_MAGIC
	if (pbuf->f_type == USBDEVICE_SUPER_MAGIC)
		return "usbdevice";
#endif
#ifdef V9FS_MAGIC
	if (pbuf->f_type == V9FS_MAGIC)
		return "v9fs";
#endif
#ifdef VXFS_SUPER_MAGIC
	if (pbuf->f_type == VXFS_SUPER_MAGIC)
		return "vxfs";
#endif
#ifdef XENFS_SUPER_MAGIC
	if (pbuf->f_type == XENFS_SUPER_MAGIC)
		return "xenfs";
#endif
#ifdef XENIX_SUPER_MAGIC
	if (pbuf->f_type == XENIX_SUPER_MAGIC)
		return "xenix";
#endif
#ifdef XFS_SUPER_MAGIC
	if (pbuf->f_type == XFS_SUPER_MAGIC)
		return "xfs";
#endif
	/* End of Linux filesystems */
	return NULL;
}
#endif /* def HAVE_LINUX_STATFS */

#if defined(HAVE_BSD_STATFS) || defined(HAVE_LINUX_STATFS)
/* given a directory path, return filesystem type name (best-effort) */
static PyObject *getfstype(PyObject *self, PyObject *args)
{
	const char *path = NULL;
	struct statfs buf;
	int r;
	if (!PyArg_ParseTuple(args, "y", &path))
		return NULL;

	memset(&buf, 0, sizeof(buf));
	r = statfs(path, &buf);
	if (r != 0)
		return PyErr_SetFromErrno(PyExc_OSError);
	return Py_BuildValue("y", describefstype(&buf));
}
#endif /* defined(HAVE_LINUX_STATFS) || defined(HAVE_BSD_STATFS) */

#if defined(HAVE_BSD_STATFS)
/* given a directory path, return filesystem mount point (best-effort) */
static PyObject *getfsmountpoint(PyObject *self, PyObject *args)
{
	const char *path = NULL;
	struct statfs buf;
	int r;
	if (!PyArg_ParseTuple(args, "y", &path))
		return NULL;

	memset(&buf, 0, sizeof(buf));
	r = statfs(path, &buf);
	if (r != 0)
		return PyErr_SetFromErrno(PyExc_OSError);
	return Py_BuildValue("y", buf.f_mntonname);
}
#endif /* defined(HAVE_BSD_STATFS) */

static PyObject *unblocksignal(PyObject *self, PyObject *args)
{
	int sig = 0;
	sigset_t set;
	int r;
	if (!PyArg_ParseTuple(args, "i", &sig))
		return NULL;
	r = sigemptyset(&set);
	if (r != 0)
		return PyErr_SetFromErrno(PyExc_OSError);
	r = sigaddset(&set, sig);
	if (r != 0)
		return PyErr_SetFromErrno(PyExc_OSError);
	r = sigprocmask(SIG_UNBLOCK, &set, NULL);
	if (r != 0)
		return PyErr_SetFromErrno(PyExc_OSError);
	Py_RETURN_NONE;
}

#endif /* ndef _WIN32 */

static PyObject *listdir(PyObject *self, PyObject *args, PyObject *kwargs)
{
	PyObject *statobj = NULL; /* initialize - optional arg */
	PyObject *skipobj = NULL; /* initialize - optional arg */
	char *path, *skip = NULL;
	Py_ssize_t plen;
	int wantstat;

	static char *kwlist[] = {"path", "stat", "skip", NULL};

	if (!PyArg_ParseTupleAndKeywords(args, kwargs, "y#|OO:listdir",
			kwlist, &path, &plen, &statobj, &skipobj))
		return NULL;

	wantstat = statobj && PyObject_IsTrue(statobj);

	if (skipobj && skipobj != Py_None) {
		skip = PyBytes_AsString(skipobj);
		if (!skip)
			return NULL;
	}

	return _listdir(path, plen, wantstat, skip);
}

#ifdef _WIN32
static PyObject *posixfile(PyObject *self, PyObject *args, PyObject *kwds)
{
	static char *kwlist[] = {"name", "mode", "buffering", NULL};
	PyObject *file_obj = NULL;
	char *name = NULL;
	char *mode = "rb";
	DWORD access = 0;
	DWORD creation;
	HANDLE handle;
	int fd, flags = 0;
	int bufsize = -1;
	char m0, m1, m2;
	char fpmode[4];
	int fppos = 0;
	int plus;

	if (!PyArg_ParseTupleAndKeywords(args, kwds, "et|yi:posixfile",
					 kwlist,
					 Py_FileSystemDefaultEncoding,
					 &name, &mode, &bufsize))
		return NULL;

	m0 = mode[0];
	m1 = m0 ? mode[1] : '\0';
	m2 = m1 ? mode[2] : '\0';
	plus = m1 == '+' || m2 == '+';

	fpmode[fppos++] = m0;
	if (m1 == 'b' || m2 == 'b') {
		flags = _O_BINARY;
		fpmode[fppos++] = 'b';
	}
	else
		flags = _O_TEXT;
	if (m0 == 'r' && !plus) {
		flags |= _O_RDONLY;
		access = GENERIC_READ;
	} else {
		/*
		work around http://support.microsoft.com/kb/899149 and
		set _O_RDWR for 'w' and 'a', even if mode has no '+'
		*/
		flags |= _O_RDWR;
		access = GENERIC_READ | GENERIC_WRITE;
		fpmode[fppos++] = '+';
	}
	fpmode[fppos++] = '\0';

	switch (m0) {
	case 'r':
		creation = OPEN_EXISTING;
		break;
	case 'w':
		creation = CREATE_ALWAYS;
		break;
	case 'a':
		creation = OPEN_ALWAYS;
		flags |= _O_APPEND;
		break;
	default:
		PyErr_Format(PyExc_ValueError,
			     "mode string must begin with one of 'r', 'w', "
			     "or 'a', not '%c'", m0);
		goto bail;
	}

	handle = CreateFile(name, access,
			    FILE_SHARE_READ | FILE_SHARE_WRITE |
			    FILE_SHARE_DELETE,
			    NULL,
			    creation,
			    FILE_ATTRIBUTE_NORMAL,
			    0);

	if (handle == INVALID_HANDLE_VALUE) {
		PyErr_SetFromWindowsErrWithFilename(GetLastError(), name);
		goto bail;
	}

	fd = _open_osfhandle((intptr_t)handle, flags);

	if (fd == -1) {
		CloseHandle(handle);
		PyErr_SetFromErrnoWithFilename(PyExc_IOError, name);
		goto bail;
	}
	file_obj = PyFile_FromFd(fd, name, mode, bufsize, NULL, NULL, NULL, 1);
	if (file_obj == NULL)
		goto bail;
bail:
	PyMem_Free(name);
	return file_obj;
}
#endif

#ifdef __APPLE__
#include <ApplicationServices/ApplicationServices.h>

static PyObject *isgui(PyObject *self)
{
	CFDictionaryRef dict = CGSessionCopyCurrentDictionary();

	if (dict != NULL) {
		CFRelease(dict);
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
}
#endif

static char osutil_doc[] = "Native operating system services.";

static PyMethodDef methods[] = {
	{"listdir", (PyCFunction)listdir, METH_VARARGS | METH_KEYWORDS,
	 "list a directory\n"},
#ifdef _WIN32
	{"posixfile", (PyCFunction)posixfile, METH_VARARGS | METH_KEYWORDS,
	 "Open a file with POSIX-like semantics.\n"
"On error, this function may raise either a WindowsError or an IOError."},
#else
	{"statfiles", (PyCFunction)statfiles, METH_VARARGS | METH_KEYWORDS,
	 "stat a series of files or symlinks\n"
"Returns None for non-existent entries and entries of other types.\n"},
#ifndef SETPROCNAME_USE_NONE
	{"setprocname", (PyCFunction)setprocname, METH_VARARGS,
	 "set process title (best-effort)\n"},
#endif
#if defined(HAVE_BSD_STATFS) || defined(HAVE_LINUX_STATFS)
	{"getfstype", (PyCFunction)getfstype, METH_VARARGS,
	 "get filesystem type (best-effort)\n"},
#endif
#if defined(HAVE_BSD_STATFS)
	{"getfsmountpoint", (PyCFunction)getfsmountpoint, METH_VARARGS,
	 "get filesystem mount point (best-effort)\n"},
#endif
	{"unblocksignal", (PyCFunction)unblocksignal, METH_VARARGS,
	 "change signal mask to unblock a given signal\n"},
#endif /* ndef _WIN32 */
#ifdef __APPLE__
	{
		"isgui", (PyCFunction)isgui, METH_NOARGS,
		"Is a CoreGraphics session available?"
	},
#endif
	{NULL, NULL}
};

static const int version = 4;

static struct PyModuleDef osutil_module = {
	PyModuleDef_HEAD_INIT,
	"osutil",
	osutil_doc,
	-1,
	methods
};

PyMODINIT_FUNC PyInit_osutil(void)
{
	PyObject *m;
	if (PyType_Ready(&listdir_stat_type) < 0)
		return NULL;

	m = PyModule_Create(&osutil_module);
	PyModule_AddIntConstant(m, "version", version);
	return m;
}
