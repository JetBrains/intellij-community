/*
 dirs.c - dynamic directory diddling for dirstates

 Copyright 2013 Facebook

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include "util.h"

/*
 * This is a multiset of directory names, built from the files that
 * appear in a dirstate or manifest.
 *
 * A few implementation notes:
 *
 * We modify Python integers for refcounting, but those integers are
 * never visible to Python code.
 *
 * We mutate strings in-place, but leave them immutable once they can
 * be seen by Python code.
 */
typedef struct {
	PyObject_HEAD
	PyObject *dict;
} dirsObject;

static inline Py_ssize_t _finddir(PyObject *path, Py_ssize_t pos)
{
	const char *s = PyString_AS_STRING(path);

	while (pos != -1) {
		if (s[pos] == '/')
			break;
		pos -= 1;
	}

	return pos;
}

static int _addpath(PyObject *dirs, PyObject *path)
{
	const char *cpath = PyString_AS_STRING(path);
	Py_ssize_t pos = PyString_GET_SIZE(path);
	PyObject *key = NULL;
	int ret = -1;

	while ((pos = _finddir(path, pos - 1)) != -1) {
		PyObject *val;

		/* It's likely that every prefix already has an entry
		   in our dict. Try to avoid allocating and
		   deallocating a string for each prefix we check. */
		if (key != NULL)
			((PyStringObject *)key)->ob_shash = -1;
		else {
			/* Force Python to not reuse a small shared string. */
			key = PyString_FromStringAndSize(cpath,
							 pos < 2 ? 2 : pos);
			if (key == NULL)
				goto bail;
		}
		PyString_GET_SIZE(key) = pos;
		PyString_AS_STRING(key)[pos] = '\0';

		val = PyDict_GetItem(dirs, key);
		if (val != NULL) {
			PyInt_AS_LONG(val) += 1;
			continue;
		}

		/* Force Python to not reuse a small shared int. */
		val = PyInt_FromLong(0x1eadbeef);

		if (val == NULL)
			goto bail;

		PyInt_AS_LONG(val) = 1;
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
	Py_ssize_t pos = PyString_GET_SIZE(path);
	PyObject *key = NULL;
	int ret = -1;

	while ((pos = _finddir(path, pos - 1)) != -1) {
		PyObject *val;

		key = PyString_FromStringAndSize(PyString_AS_STRING(path), pos);

		if (key == NULL)
			goto bail;

		val = PyDict_GetItem(dirs, key);
		if (val == NULL) {
			PyErr_SetString(PyExc_ValueError,
					"expected a value, found none");
			goto bail;
		}

		if (--PyInt_AS_LONG(val) <= 0 &&
		    PyDict_DelItem(dirs, key) == -1)
			goto bail;
		Py_CLEAR(key);
	}
	ret = 0;

bail:
	Py_XDECREF(key);

	return ret;
}

static int dirs_fromdict(PyObject *dirs, PyObject *source, char skipchar)
{
	PyObject *key, *value;
	Py_ssize_t pos = 0;

	while (PyDict_Next(source, &pos, &key, &value)) {
		if (!PyString_Check(key)) {
			PyErr_SetString(PyExc_TypeError, "expected string key");
			return -1;
		}
		if (skipchar) {
			PyObject *st;

			if (!PyTuple_Check(value) ||
			    PyTuple_GET_SIZE(value) == 0) {
				PyErr_SetString(PyExc_TypeError,
						"expected non-empty tuple");
				return -1;
			}

			st = PyTuple_GET_ITEM(value, 0);

			if (!PyString_Check(st) || PyString_GET_SIZE(st) == 0) {
				PyErr_SetString(PyExc_TypeError,
						"expected non-empty string "
						"at tuple index 0");
				return -1;
			}

			if (PyString_AS_STRING(st)[0] == skipchar)
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
		if (!PyString_Check(item)) {
			PyErr_SetString(PyExc_TypeError, "expected string");
			break;
		}

		if (_addpath(dirs, item) == -1)
			break;
		Py_CLEAR(item);
	}

	ret = PyErr_Occurred() ? -1 : 0;
	Py_XDECREF(item);
	return ret;
}

/*
 * Calculate a refcounted set of directory names for the files in a
 * dirstate.
 */
static int dirs_init(dirsObject *self, PyObject *args)
{
	PyObject *dirs = NULL, *source = NULL;
	char skipchar = 0;
	int ret = -1;

	self->dict = NULL;

	if (!PyArg_ParseTuple(args, "|Oc:__init__", &source, &skipchar))
		return -1;

	dirs = PyDict_New();

	if (dirs == NULL)
		return -1;

	if (source == NULL)
		ret = 0;
	else if (PyDict_Check(source))
		ret = dirs_fromdict(dirs, source, skipchar);
	else if (skipchar)
		PyErr_SetString(PyExc_ValueError,
				"skip character is only supported "
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

	if (!PyArg_ParseTuple(args, "O!:addpath", &PyString_Type, &path))
		return NULL;

	if (_addpath(self->dict, path) == -1)
		return NULL;

	Py_RETURN_NONE;
}

static PyObject *dirs_delpath(dirsObject *self, PyObject *args)
{
	PyObject *path;

	if (!PyArg_ParseTuple(args, "O!:delpath", &PyString_Type, &path))
		return NULL;

	if (_delpath(self->dict, path) == -1)
		return NULL;

	Py_RETURN_NONE;
}

static int dirs_contains(dirsObject *self, PyObject *value)
{
	return PyString_Check(value) ? PyDict_Contains(self->dict, value) : 0;
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

static PyTypeObject dirsType = { PyObject_HEAD_INIT(NULL) };

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
