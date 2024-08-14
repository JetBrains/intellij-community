/*
 dirs.c - dynamic directory diddling for dirstates

 Copyright 2013 Facebook

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <string.h>

#include "util.h"

#if PY_VERSION_HEX >= 0x030C00A5
#define PYLONG_VALUE(o) ((PyLongObject *)o)->long_value.ob_digit[0]
#else
#define PYLONG_VALUE(o) ((PyLongObject *)o)->ob_digit[0]
#endif

/*
 * This is a multiset of directory names, built from the files that
 * appear in a dirstate or manifest.
 *
 * A few implementation notes:
 *
 * We modify Python integers for refcounting, but those integers are
 * never visible to Python code.
 */
/* clang-format off */
typedef struct {
	PyObject_HEAD
	PyObject *dict;
} dirsObject;
/* clang-format on */

static inline Py_ssize_t _finddir(const char *path, Py_ssize_t pos)
{
	while (pos != -1) {
		if (path[pos] == '/')
			break;
		pos -= 1;
	}
	if (pos == -1) {
		return 0;
	}

	return pos;
}

/* Mercurial will fail to run on directory hierarchies deeper than
 * this constant, so we should try and keep this constant as big as
 * possible.
 */
#define MAX_DIRS_DEPTH 2048

static int _addpath(PyObject *dirs, PyObject *path)
{
	const char *cpath = PyBytes_AS_STRING(path);
	Py_ssize_t pos = PyBytes_GET_SIZE(path);
	PyObject *key = NULL;
	int ret = -1;
	size_t num_slashes = 0;

	/* This loop is super critical for performance. That's why we inline
	 * access to Python structs instead of going through a supported API.
	 * The implementation, therefore, is heavily dependent on CPython
	 * implementation details. We also commit violations of the Python
	 * "protocol" such as mutating immutable objects. But since we only
	 * mutate objects created in this function or in other well-defined
	 * locations, the references are known so these violations should go
	 * unnoticed. */
	while ((pos = _finddir(cpath, pos - 1)) != -1) {
		PyObject *val;
		++num_slashes;
		if (num_slashes > MAX_DIRS_DEPTH) {
			PyErr_SetString(PyExc_ValueError,
			                "Directory hierarchy too deep.");
			goto bail;
		}

		/* Sniff for trailing slashes, a marker of an invalid input. */
		if (pos > 0 && cpath[pos - 1] == '/') {
			PyErr_SetString(
			    PyExc_ValueError,
			    "found invalid consecutive slashes in path");
			goto bail;
		}

		key = PyBytes_FromStringAndSize(cpath, pos);
		if (key == NULL)
			goto bail;

		val = PyDict_GetItem(dirs, key);
		if (val != NULL) {
			PYLONG_VALUE(val) += 1;
			Py_CLEAR(key);
			break;
		}

		/* Force Python to not reuse a small shared int. */
		val = PyLong_FromLong(0x1eadbeef);

		if (val == NULL)
			goto bail;

		PYLONG_VALUE(val) = 1;
		ret = PyDict_SetItem(dirs, key, val);
		Py_DECREF(val);
		if (ret == -1)
			goto bail;
		Py_CLEAR(key);
	}
	ret = 0;

bail:
	Py_XDECREF(key);

	return ret;
}

static int _delpath(PyObject *dirs, PyObject *path)
{
	char *cpath = PyBytes_AS_STRING(path);
	Py_ssize_t pos = PyBytes_GET_SIZE(path);
	PyObject *key = NULL;
	int ret = -1;

	while ((pos = _finddir(cpath, pos - 1)) != -1) {
		PyObject *val;

		key = PyBytes_FromStringAndSize(cpath, pos);

		if (key == NULL)
			goto bail;

		val = PyDict_GetItem(dirs, key);
		if (val == NULL) {
			PyErr_SetString(PyExc_ValueError,
			                "expected a value, found none");
			goto bail;
		}

		if (--PYLONG_VALUE(val) <= 0) {
			if (PyDict_DelItem(dirs, key) == -1)
				goto bail;
		} else
			break;
		Py_CLEAR(key);
	}
	ret = 0;

bail:
	Py_XDECREF(key);

	return ret;
}

static int dirs_fromdict(PyObject *dirs, PyObject *source, bool only_tracked)
{
	PyObject *key, *value;
	Py_ssize_t pos = 0;

	while (PyDict_Next(source, &pos, &key, &value)) {
		if (!PyBytes_Check(key)) {
			PyErr_SetString(PyExc_TypeError, "expected string key");
			return -1;
		}
		if (only_tracked) {
			if (!dirstate_tuple_check(value)) {
				PyErr_SetString(PyExc_TypeError,
				                "expected a dirstate tuple");
				return -1;
			}
			if (!(((dirstateItemObject *)value)->flags &
			      dirstate_flag_wc_tracked))
				continue;
		}

		if (_addpath(dirs, key) == -1)
			return -1;
	}

	return 0;
}

static int dirs_fromiter(PyObject *dirs, PyObject *source)
{
	PyObject *iter, *item = NULL;
	int ret;

	iter = PyObject_GetIter(source);
	if (iter == NULL)
		return -1;

	while ((item = PyIter_Next(iter)) != NULL) {
		if (!PyBytes_Check(item)) {
			PyErr_SetString(PyExc_TypeError, "expected string");
			break;
		}

		if (_addpath(dirs, item) == -1)
			break;
		Py_CLEAR(item);
	}

	ret = PyErr_Occurred() ? -1 : 0;
	Py_DECREF(iter);
	Py_XDECREF(item);
	return ret;
}

/*
 * Calculate a refcounted set of directory names for the files in a
 * dirstate.
 */
static int dirs_init(dirsObject *self, PyObject *args, PyObject *kwargs)
{
	PyObject *dirs = NULL, *source = NULL;
	int only_tracked = 0;
	int ret = -1;
	static char *keywords_name[] = {"map", "only_tracked", NULL};

	self->dict = NULL;

	if (!PyArg_ParseTupleAndKeywords(args, kwargs, "|Oi:__init__",
	                                 keywords_name, &source, &only_tracked))
		return -1;

	dirs = PyDict_New();

	if (dirs == NULL)
		return -1;

	if (source == NULL)
		ret = 0;
	else if (PyDict_Check(source))
		ret = dirs_fromdict(dirs, source, (bool)only_tracked);
	else if (only_tracked)
		PyErr_SetString(PyExc_ValueError,
		                "`only_tracked` is only supported "
		                "with a dict source");
	else
		ret = dirs_fromiter(dirs, source);

	if (ret == -1)
		Py_XDECREF(dirs);
	else
		self->dict = dirs;

	return ret;
}

PyObject *dirs_addpath(dirsObject *self, PyObject *args)
{
	PyObject *path;

	if (!PyArg_ParseTuple(args, "O!:addpath", &PyBytes_Type, &path))
		return NULL;

	if (_addpath(self->dict, path) == -1)
		return NULL;

	Py_RETURN_NONE;
}

static PyObject *dirs_delpath(dirsObject *self, PyObject *args)
{
	PyObject *path;

	if (!PyArg_ParseTuple(args, "O!:delpath", &PyBytes_Type, &path))
		return NULL;

	if (_delpath(self->dict, path) == -1)
		return NULL;

	Py_RETURN_NONE;
}

static int dirs_contains(dirsObject *self, PyObject *value)
{
	return PyBytes_Check(value) ? PyDict_Contains(self->dict, value) : 0;
}

static void dirs_dealloc(dirsObject *self)
{
	Py_XDECREF(self->dict);
	PyObject_Del(self);
}

static PyObject *dirs_iter(dirsObject *self)
{
	return PyObject_GetIter(self->dict);
}

static PySequenceMethods dirs_sequence_methods;

static PyMethodDef dirs_methods[] = {
    {"addpath", (PyCFunction)dirs_addpath, METH_VARARGS, "add a path"},
    {"delpath", (PyCFunction)dirs_delpath, METH_VARARGS, "remove a path"},
    {NULL} /* Sentinel */
};

static PyTypeObject dirsType = {PyVarObject_HEAD_INIT(NULL, 0)};

void dirs_module_init(PyObject *mod)
{
	dirs_sequence_methods.sq_contains = (objobjproc)dirs_contains;
	dirsType.tp_name = "parsers.dirs";
	dirsType.tp_new = PyType_GenericNew;
	dirsType.tp_basicsize = sizeof(dirsObject);
	dirsType.tp_dealloc = (destructor)dirs_dealloc;
	dirsType.tp_as_sequence = &dirs_sequence_methods;
	dirsType.tp_flags = Py_TPFLAGS_DEFAULT;
	dirsType.tp_doc = "dirs";
	dirsType.tp_iter = (getiterfunc)dirs_iter;
	dirsType.tp_methods = dirs_methods;
	dirsType.tp_init = (initproc)dirs_init;

	if (PyType_Ready(&dirsType) < 0)
		return;
	Py_INCREF(&dirsType);

	PyModule_AddObject(mod, "dirs", (PyObject *)&dirsType);
}
