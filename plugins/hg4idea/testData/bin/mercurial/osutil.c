/*
 osutil.c - native operating system services

 Copyright 2007 Matt Mackall and others

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#define _ATFILE_SOURCE
#include <Python.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

#ifdef _WIN32
#include <windows.h>
#include <io.h>
#else
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
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
		return PyInt_FromLong(((struct listdir_stat *)self)->st.name); \
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
	o->ob_type->tp_free(o);
}

static PyTypeObject listdir_stat_type = {
	PyVarObject_HEAD_INIT(NULL, 0)
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
	0,                         /*tp_as_mapping*/
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
		return Py_BuildValue("si", fd->cFileName, kind);

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
	return Py_BuildValue("siN", fd->cFileName,
		kind, py_st);
}

static PyObject *_listdir(char *path, int plen, int wantstat, char *skip)
{
	PyObject *rval = NULL; /* initialize - return value */
	PyObject *list;
	HANDLE fh;
	WIN32_FIND_DATAA fd;
	char *pattern;

	/* build the path + \* pattern string */
	pattern = malloc(plen + 3); /* path + \* + \0 */
	if (!pattern) {
		PyErr_NoMemory();
		goto error_nomem;
	}
	strcpy(pattern, path);

	if (plen > 0) {
		char c = path[plen-1];
		if (c != ':' && c != '/' && c != '\\')
			pattern[plen++] = '\\';
	}
	strcpy(pattern + plen, "*");

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
	free(pattern);
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

static PyObject *_listdir(char *path, int pathlen, int keepstat, char *skip)
{
	PyObject *list, *elem, *stat, *ret = NULL;
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
			fullpath[PATH_MAX] = 0;
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
			stat = makestat(&st);
			if (!stat)
				goto error;
			elem = Py_BuildValue("siN", ent->d_name, kind, stat);
		} else
			elem = Py_BuildValue("si", ent->d_name, kind);
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
error_dir:
#ifdef AT_SYMLINK_NOFOLLOW
	close(dfd);
#endif
error_value:
	return ret;
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
		PyObject *stat;
		struct stat st;
		int ret, kind;
		char *path;

		path = PyString_AsString(PySequence_GetItem(names, i));
		if (path == NULL) {
			PyErr_SetString(PyExc_TypeError, "not a string");
			goto bail;
		}
		ret = lstat(path, &st);
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

#endif /* ndef _WIN32 */

static PyObject *listdir(PyObject *self, PyObject *args, PyObject *kwargs)
{
	PyObject *statobj = NULL; /* initialize - optional arg */
	PyObject *skipobj = NULL; /* initialize - optional arg */
	char *path, *skip = NULL;
	int wantstat, plen;

	static char *kwlist[] = {"path", "stat", "skip", NULL};

	if (!PyArg_ParseTupleAndKeywords(args, kwargs, "s#|OO:listdir",
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
	FILE *fp;

	if (!PyArg_ParseTupleAndKeywords(args, kwds, "et|si:posixfile", kwlist,
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
#ifndef IS_PY3K
	fp = _fdopen(fd, fpmode);
	if (fp == NULL) {
		_close(fd);
		PyErr_SetFromErrnoWithFilename(PyExc_IOError, name);
		goto bail;
	}

	file_obj = PyFile_FromFile(fp, name, mode, fclose);
	if (file_obj == NULL) {
		fclose(fp);
		goto bail;
	}

	PyFile_SetBufSize(file_obj, bufsize);
#else
	file_obj = PyFile_FromFd(fd, name, mode, bufsize, NULL, NULL, NULL, 1);
	if (file_obj == NULL)
		goto bail;
#endif
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
#endif
#ifdef __APPLE__
	{
		"isgui", (PyCFunction)isgui, METH_NOARGS,
		"Is a CoreGraphics session available?"
	},
#endif
	{NULL, NULL}
};

#ifdef IS_PY3K
static struct PyModuleDef osutil_module = {
	PyModuleDef_HEAD_INIT,
	"osutil",
	osutil_doc,
	-1,
	methods
};

PyMODINIT_FUNC PyInit_osutil(void)
{
	if (PyType_Ready(&listdir_stat_type) < 0)
		return NULL;

	return PyModule_Create(&osutil_module);
}
#else
PyMODINIT_FUNC initosutil(void)
{
	if (PyType_Ready(&listdir_stat_type) == -1)
		return;

	Py_InitModule3("osutil", methods, osutil_doc);
}
#endif
