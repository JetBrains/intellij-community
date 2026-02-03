/*
 parsers.c - efficient content parsing

 Copyright 2008 Olivia Mackall <olivia@selenic.com> and others

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <ctype.h>
#include <stddef.h>
#include <string.h>

#include "bitmanipulation.h"
#include "charencode.h"
#include "util.h"

static const char *const versionerrortext = "Python minor version mismatch";

static const int dirstate_v1_from_p2 = -2;
static const int dirstate_v1_nonnormal = -1;
static const int ambiguous_time = -1;

static PyObject *dict_new_presized(PyObject *self, PyObject *args)
{
	Py_ssize_t expected_size;

	if (!PyArg_ParseTuple(args, "n:make_presized_dict", &expected_size)) {
		return NULL;
	}

	return _dict_new_presized(expected_size);
}

static PyObject *dirstate_item_new(PyTypeObject *subtype, PyObject *args,
                                   PyObject *kwds)
{
	/* We do all the initialization here and not a tp_init function because
	 * dirstate_item is immutable. */
	dirstateItemObject *t;
	int wc_tracked;
	int p1_tracked;
	int p2_info;
	int has_meaningful_data;
	int has_meaningful_mtime;
	int mtime_second_ambiguous;
	int mode;
	int size;
	int mtime_s;
	int mtime_ns;
	PyObject *parentfiledata;
	PyObject *mtime;
	PyObject *fallback_exec;
	PyObject *fallback_symlink;
	static char *keywords_name[] = {
	    "wc_tracked",          "p1_tracked",           "p2_info",
	    "has_meaningful_data", "has_meaningful_mtime", "parentfiledata",
	    "fallback_exec",       "fallback_symlink",     NULL,
	};
	wc_tracked = 0;
	p1_tracked = 0;
	p2_info = 0;
	has_meaningful_mtime = 1;
	has_meaningful_data = 1;
	mtime_second_ambiguous = 0;
	parentfiledata = Py_None;
	fallback_exec = Py_None;
	fallback_symlink = Py_None;
	if (!PyArg_ParseTupleAndKeywords(args, kwds, "|iiiiiOOO", keywords_name,
	                                 &wc_tracked, &p1_tracked, &p2_info,
	                                 &has_meaningful_data,
	                                 &has_meaningful_mtime, &parentfiledata,
	                                 &fallback_exec, &fallback_symlink)) {
		return NULL;
	}
	t = (dirstateItemObject *)subtype->tp_alloc(subtype, 1);
	if (!t) {
		return NULL;
	}

	t->flags = 0;
	if (wc_tracked) {
		t->flags |= dirstate_flag_wc_tracked;
	}
	if (p1_tracked) {
		t->flags |= dirstate_flag_p1_tracked;
	}
	if (p2_info) {
		t->flags |= dirstate_flag_p2_info;
	}

	if (fallback_exec != Py_None) {
		t->flags |= dirstate_flag_has_fallback_exec;
		if (PyObject_IsTrue(fallback_exec)) {
			t->flags |= dirstate_flag_fallback_exec;
		}
	}
	if (fallback_symlink != Py_None) {
		t->flags |= dirstate_flag_has_fallback_symlink;
		if (PyObject_IsTrue(fallback_symlink)) {
			t->flags |= dirstate_flag_fallback_symlink;
		}
	}

	if (parentfiledata != Py_None) {
		if (!PyArg_ParseTuple(parentfiledata, "iiO", &mode, &size,
		                      &mtime)) {
			return NULL;
		}
		if (mtime != Py_None) {
			if (!PyArg_ParseTuple(mtime, "iii", &mtime_s, &mtime_ns,
			                      &mtime_second_ambiguous)) {
				return NULL;
			}
		} else {
			has_meaningful_mtime = 0;
		}
	} else {
		has_meaningful_data = 0;
		has_meaningful_mtime = 0;
	}
	if (has_meaningful_data) {
		t->flags |= dirstate_flag_has_meaningful_data;
		t->mode = mode;
		t->size = size;
		if (mtime_second_ambiguous) {
			t->flags |= dirstate_flag_mtime_second_ambiguous;
		}
	} else {
		t->mode = 0;
		t->size = 0;
	}
	if (has_meaningful_mtime) {
		t->flags |= dirstate_flag_has_mtime;
		t->mtime_s = mtime_s;
		t->mtime_ns = mtime_ns;
	} else {
		t->mtime_s = 0;
		t->mtime_ns = 0;
	}
	return (PyObject *)t;
}

static void dirstate_item_dealloc(PyObject *o)
{
	PyObject_Del(o);
}

static inline bool dirstate_item_c_tracked(dirstateItemObject *self)
{
	return (self->flags & dirstate_flag_wc_tracked);
}

static inline bool dirstate_item_c_any_tracked(dirstateItemObject *self)
{
	const int mask = dirstate_flag_wc_tracked | dirstate_flag_p1_tracked |
	                 dirstate_flag_p2_info;
	return (self->flags & mask);
}

static inline bool dirstate_item_c_added(dirstateItemObject *self)
{
	const int mask = (dirstate_flag_wc_tracked | dirstate_flag_p1_tracked |
	                  dirstate_flag_p2_info);
	const int target = dirstate_flag_wc_tracked;
	return (self->flags & mask) == target;
}

static inline bool dirstate_item_c_removed(dirstateItemObject *self)
{
	if (self->flags & dirstate_flag_wc_tracked) {
		return false;
	}
	return (self->flags &
	        (dirstate_flag_p1_tracked | dirstate_flag_p2_info));
}

static inline bool dirstate_item_c_modified(dirstateItemObject *self)
{
	return ((self->flags & dirstate_flag_wc_tracked) &&
	        (self->flags & dirstate_flag_p1_tracked) &&
	        (self->flags & dirstate_flag_p2_info));
}

static inline bool dirstate_item_c_from_p2(dirstateItemObject *self)
{
	return ((self->flags & dirstate_flag_wc_tracked) &&
	        !(self->flags & dirstate_flag_p1_tracked) &&
	        (self->flags & dirstate_flag_p2_info));
}

static inline char dirstate_item_c_v1_state(dirstateItemObject *self)
{
	if (dirstate_item_c_removed(self)) {
		return 'r';
	} else if (dirstate_item_c_modified(self)) {
		return 'm';
	} else if (dirstate_item_c_added(self)) {
		return 'a';
	} else {
		return 'n';
	}
}

static inline bool dirstate_item_c_has_fallback_exec(dirstateItemObject *self)
{
	return (bool)self->flags & dirstate_flag_has_fallback_exec;
}

static inline bool
dirstate_item_c_has_fallback_symlink(dirstateItemObject *self)
{
	return (bool)self->flags & dirstate_flag_has_fallback_symlink;
}

static inline int dirstate_item_c_v1_mode(dirstateItemObject *self)
{
	if (self->flags & dirstate_flag_has_meaningful_data) {
		return self->mode;
	} else {
		return 0;
	}
}

static inline int dirstate_item_c_v1_size(dirstateItemObject *self)
{
	if (!(self->flags & dirstate_flag_wc_tracked) &&
	    (self->flags & dirstate_flag_p2_info)) {
		if (self->flags & dirstate_flag_p1_tracked) {
			return dirstate_v1_nonnormal;
		} else {
			return dirstate_v1_from_p2;
		}
	} else if (dirstate_item_c_removed(self)) {
		return 0;
	} else if (self->flags & dirstate_flag_p2_info) {
		return dirstate_v1_from_p2;
	} else if (dirstate_item_c_added(self)) {
		return dirstate_v1_nonnormal;
	} else if (self->flags & dirstate_flag_has_meaningful_data) {
		return self->size;
	} else {
		return dirstate_v1_nonnormal;
	}
}

static inline int dirstate_item_c_v1_mtime(dirstateItemObject *self)
{
	if (dirstate_item_c_removed(self)) {
		return 0;
	} else if (!(self->flags & dirstate_flag_has_mtime) ||
	           !(self->flags & dirstate_flag_p1_tracked) ||
	           !(self->flags & dirstate_flag_wc_tracked) ||
	           (self->flags & dirstate_flag_p2_info) ||
	           (self->flags & dirstate_flag_mtime_second_ambiguous)) {
		return ambiguous_time;
	} else {
		return self->mtime_s;
	}
}

static PyObject *dirstate_item_v2_data(dirstateItemObject *self)
{
	int flags = self->flags;
	int mode = dirstate_item_c_v1_mode(self);
#ifdef S_IXUSR
	/* This is for platforms with an exec bit */
	if ((mode & S_IXUSR) != 0) {
		flags |= dirstate_flag_mode_exec_perm;
	} else {
		flags &= ~dirstate_flag_mode_exec_perm;
	}
#else
	flags &= ~dirstate_flag_mode_exec_perm;
#endif
#ifdef S_ISLNK
	/* This is for platforms with support for symlinks */
	if (S_ISLNK(mode)) {
		flags |= dirstate_flag_mode_is_symlink;
	} else {
		flags &= ~dirstate_flag_mode_is_symlink;
	}
#else
	flags &= ~dirstate_flag_mode_is_symlink;
#endif
	return Py_BuildValue("iiii", flags, self->size, self->mtime_s,
	                     self->mtime_ns);
};

static PyObject *dirstate_item_mtime_likely_equal_to(dirstateItemObject *self,
                                                     PyObject *other)
{
	int other_s;
	int other_ns;
	int other_second_ambiguous;
	if (!PyArg_ParseTuple(other, "iii", &other_s, &other_ns,
	                      &other_second_ambiguous)) {
		return NULL;
	}
	if (!(self->flags & dirstate_flag_has_mtime)) {
		Py_RETURN_FALSE;
	}
	if (self->mtime_s != other_s) {
		Py_RETURN_FALSE;
	}
	if (self->mtime_ns == 0 || other_ns == 0) {
		if (self->flags & dirstate_flag_mtime_second_ambiguous) {
			Py_RETURN_FALSE;
		} else {
			Py_RETURN_TRUE;
		}
	}
	if (self->mtime_ns == other_ns) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

/* This will never change since it's bound to V1
 */
static inline dirstateItemObject *
dirstate_item_from_v1_data(char state, int mode, int size, int mtime)
{
	dirstateItemObject *t =
	    PyObject_New(dirstateItemObject, &dirstateItemType);
	if (!t) {
		return NULL;
	}
	t->flags = 0;
	t->mode = 0;
	t->size = 0;
	t->mtime_s = 0;
	t->mtime_ns = 0;

	if (state == 'm') {
		t->flags = (dirstate_flag_wc_tracked |
		            dirstate_flag_p1_tracked | dirstate_flag_p2_info);
	} else if (state == 'a') {
		t->flags = dirstate_flag_wc_tracked;
	} else if (state == 'r') {
		if (size == dirstate_v1_nonnormal) {
			t->flags =
			    dirstate_flag_p1_tracked | dirstate_flag_p2_info;
		} else if (size == dirstate_v1_from_p2) {
			t->flags = dirstate_flag_p2_info;
		} else {
			t->flags = dirstate_flag_p1_tracked;
		}
	} else if (state == 'n') {
		if (size == dirstate_v1_from_p2) {
			t->flags =
			    dirstate_flag_wc_tracked | dirstate_flag_p2_info;
		} else if (size == dirstate_v1_nonnormal) {
			t->flags =
			    dirstate_flag_wc_tracked | dirstate_flag_p1_tracked;
		} else if (mtime == ambiguous_time) {
			t->flags = (dirstate_flag_wc_tracked |
			            dirstate_flag_p1_tracked |
			            dirstate_flag_has_meaningful_data);
			t->mode = mode;
			t->size = size;
		} else {
			t->flags = (dirstate_flag_wc_tracked |
			            dirstate_flag_p1_tracked |
			            dirstate_flag_has_meaningful_data |
			            dirstate_flag_has_mtime);
			t->mode = mode;
			t->size = size;
			t->mtime_s = mtime;
		}
	} else {
		PyErr_Format(PyExc_RuntimeError,
		             "unknown state: `%c` (%d, %d, %d)", state, mode,
		             size, mtime);
		Py_DECREF(t);
		return NULL;
	}

	return t;
}

static PyObject *dirstate_item_from_v2_meth(PyTypeObject *subtype,
                                            PyObject *args)
{
	dirstateItemObject *t =
	    PyObject_New(dirstateItemObject, &dirstateItemType);
	if (!t) {
		return NULL;
	}
	if (!PyArg_ParseTuple(args, "iiii", &t->flags, &t->size, &t->mtime_s,
	                      &t->mtime_ns)) {
		return NULL;
	}
	if (t->flags & dirstate_flag_expected_state_is_modified) {
		t->flags &= ~(dirstate_flag_expected_state_is_modified |
		              dirstate_flag_has_meaningful_data |
		              dirstate_flag_has_mtime);
	}
	t->mode = 0;
	if (t->flags & dirstate_flag_has_meaningful_data) {
		if (t->flags & dirstate_flag_mode_exec_perm) {
			t->mode = 0755;
		} else {
			t->mode = 0644;
		}
		if (t->flags & dirstate_flag_mode_is_symlink) {
			t->mode |= S_IFLNK;
		} else {
			t->mode |= S_IFREG;
		}
	}
	return (PyObject *)t;
};

/* This means the next status call will have to actually check its content
   to make sure it is correct. */
static PyObject *dirstate_item_set_possibly_dirty(dirstateItemObject *self)
{
	self->flags &= ~dirstate_flag_has_mtime;
	Py_RETURN_NONE;
}

/* See docstring of the python implementation for details */
static PyObject *dirstate_item_set_clean(dirstateItemObject *self,
                                         PyObject *args)
{
	int size, mode, mtime_s, mtime_ns, mtime_second_ambiguous;
	PyObject *mtime;
	mtime_s = 0;
	mtime_ns = 0;
	mtime_second_ambiguous = 0;
	if (!PyArg_ParseTuple(args, "iiO", &mode, &size, &mtime)) {
		return NULL;
	}
	if (mtime != Py_None) {
		if (!PyArg_ParseTuple(mtime, "iii", &mtime_s, &mtime_ns,
		                      &mtime_second_ambiguous)) {
			return NULL;
		}
	} else {
		self->flags &= ~dirstate_flag_has_mtime;
	}
	self->flags = dirstate_flag_wc_tracked | dirstate_flag_p1_tracked |
	              dirstate_flag_has_meaningful_data |
	              dirstate_flag_has_mtime;
	if (mtime_second_ambiguous) {
		self->flags |= dirstate_flag_mtime_second_ambiguous;
	}
	self->mode = mode;
	self->size = size;
	self->mtime_s = mtime_s;
	self->mtime_ns = mtime_ns;
	Py_RETURN_NONE;
}

static PyObject *dirstate_item_set_tracked(dirstateItemObject *self)
{
	self->flags |= dirstate_flag_wc_tracked;
	self->flags &= ~dirstate_flag_has_mtime;
	Py_RETURN_NONE;
}

static PyObject *dirstate_item_set_untracked(dirstateItemObject *self)
{
	self->flags &= ~dirstate_flag_wc_tracked;
	self->flags &= ~dirstate_flag_has_meaningful_data;
	self->flags &= ~dirstate_flag_has_mtime;
	self->mode = 0;
	self->size = 0;
	self->mtime_s = 0;
	self->mtime_ns = 0;
	Py_RETURN_NONE;
}

static PyObject *dirstate_item_drop_merge_data(dirstateItemObject *self)
{
	if (self->flags & dirstate_flag_p2_info) {
		self->flags &= ~(dirstate_flag_p2_info |
		                 dirstate_flag_has_meaningful_data |
		                 dirstate_flag_has_mtime);
		self->mode = 0;
		self->size = 0;
		self->mtime_s = 0;
		self->mtime_ns = 0;
	}
	Py_RETURN_NONE;
}
static PyMethodDef dirstate_item_methods[] = {
    {"v2_data", (PyCFunction)dirstate_item_v2_data, METH_NOARGS,
     "return data suitable for v2 serialization"},
    {"mtime_likely_equal_to", (PyCFunction)dirstate_item_mtime_likely_equal_to,
     METH_O, "True if the stored mtime is likely equal to the given mtime"},
    {"from_v2_data", (PyCFunction)dirstate_item_from_v2_meth,
     METH_VARARGS | METH_CLASS, "build a new DirstateItem object from V2 data"},
    {"set_possibly_dirty", (PyCFunction)dirstate_item_set_possibly_dirty,
     METH_NOARGS, "mark a file as \"possibly dirty\""},
    {"set_clean", (PyCFunction)dirstate_item_set_clean, METH_VARARGS,
     "mark a file as \"clean\""},
    {"set_tracked", (PyCFunction)dirstate_item_set_tracked, METH_NOARGS,
     "mark a file as \"tracked\""},
    {"set_untracked", (PyCFunction)dirstate_item_set_untracked, METH_NOARGS,
     "mark a file as \"untracked\""},
    {"drop_merge_data", (PyCFunction)dirstate_item_drop_merge_data, METH_NOARGS,
     "remove all \"merge-only\" from a DirstateItem"},
    {NULL} /* Sentinel */
};

static PyObject *dirstate_item_get_mode(dirstateItemObject *self)
{
	return PyLong_FromLong(dirstate_item_c_v1_mode(self));
};

static PyObject *dirstate_item_get_size(dirstateItemObject *self)
{
	return PyLong_FromLong(dirstate_item_c_v1_size(self));
};

static PyObject *dirstate_item_get_mtime(dirstateItemObject *self)
{
	return PyLong_FromLong(dirstate_item_c_v1_mtime(self));
};

static PyObject *dirstate_item_get_state(dirstateItemObject *self)
{
	char state = dirstate_item_c_v1_state(self);
	return PyBytes_FromStringAndSize(&state, 1);
};

static PyObject *dirstate_item_get_has_fallback_exec(dirstateItemObject *self)
{
	if (dirstate_item_c_has_fallback_exec(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_fallback_exec(dirstateItemObject *self)
{
	if (dirstate_item_c_has_fallback_exec(self)) {
		if (self->flags & dirstate_flag_fallback_exec) {
			Py_RETURN_TRUE;
		} else {
			Py_RETURN_FALSE;
		}
	} else {
		Py_RETURN_NONE;
	}
};

static int dirstate_item_set_fallback_exec(dirstateItemObject *self,
                                           PyObject *value)
{
	if ((value == Py_None) || (value == NULL)) {
		self->flags &= ~dirstate_flag_has_fallback_exec;
	} else {
		self->flags |= dirstate_flag_has_fallback_exec;
		if (PyObject_IsTrue(value)) {
			self->flags |= dirstate_flag_fallback_exec;
		} else {
			self->flags &= ~dirstate_flag_fallback_exec;
		}
	}
	return 0;
};

static PyObject *
dirstate_item_get_has_fallback_symlink(dirstateItemObject *self)
{
	if (dirstate_item_c_has_fallback_symlink(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_fallback_symlink(dirstateItemObject *self)
{
	if (dirstate_item_c_has_fallback_symlink(self)) {
		if (self->flags & dirstate_flag_fallback_symlink) {
			Py_RETURN_TRUE;
		} else {
			Py_RETURN_FALSE;
		}
	} else {
		Py_RETURN_NONE;
	}
};

static int dirstate_item_set_fallback_symlink(dirstateItemObject *self,
                                              PyObject *value)
{
	if ((value == Py_None) || (value == NULL)) {
		self->flags &= ~dirstate_flag_has_fallback_symlink;
	} else {
		self->flags |= dirstate_flag_has_fallback_symlink;
		if (PyObject_IsTrue(value)) {
			self->flags |= dirstate_flag_fallback_symlink;
		} else {
			self->flags &= ~dirstate_flag_fallback_symlink;
		}
	}
	return 0;
};

static PyObject *dirstate_item_get_tracked(dirstateItemObject *self)
{
	if (dirstate_item_c_tracked(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};
static PyObject *dirstate_item_get_p1_tracked(dirstateItemObject *self)
{
	if (self->flags & dirstate_flag_p1_tracked) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_added(dirstateItemObject *self)
{
	if (dirstate_item_c_added(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_p2_info(dirstateItemObject *self)
{
	if (self->flags & dirstate_flag_wc_tracked &&
	    self->flags & dirstate_flag_p2_info) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_modified(dirstateItemObject *self)
{
	if (dirstate_item_c_modified(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_from_p2(dirstateItemObject *self)
{
	if (dirstate_item_c_from_p2(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_maybe_clean(dirstateItemObject *self)
{
	if (!(self->flags & dirstate_flag_wc_tracked)) {
		Py_RETURN_FALSE;
	} else if (!(self->flags & dirstate_flag_p1_tracked)) {
		Py_RETURN_FALSE;
	} else if (self->flags & dirstate_flag_p2_info) {
		Py_RETURN_FALSE;
	} else {
		Py_RETURN_TRUE;
	}
};

static PyObject *dirstate_item_get_any_tracked(dirstateItemObject *self)
{
	if (dirstate_item_c_any_tracked(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyObject *dirstate_item_get_removed(dirstateItemObject *self)
{
	if (dirstate_item_c_removed(self)) {
		Py_RETURN_TRUE;
	} else {
		Py_RETURN_FALSE;
	}
};

static PyGetSetDef dirstate_item_getset[] = {
    {"mode", (getter)dirstate_item_get_mode, NULL, "mode", NULL},
    {"size", (getter)dirstate_item_get_size, NULL, "size", NULL},
    {"mtime", (getter)dirstate_item_get_mtime, NULL, "mtime", NULL},
    {"state", (getter)dirstate_item_get_state, NULL, "state", NULL},
    {"has_fallback_exec", (getter)dirstate_item_get_has_fallback_exec, NULL,
     "has_fallback_exec", NULL},
    {"fallback_exec", (getter)dirstate_item_get_fallback_exec,
     (setter)dirstate_item_set_fallback_exec, "fallback_exec", NULL},
    {"has_fallback_symlink", (getter)dirstate_item_get_has_fallback_symlink,
     NULL, "has_fallback_symlink", NULL},
    {"fallback_symlink", (getter)dirstate_item_get_fallback_symlink,
     (setter)dirstate_item_set_fallback_symlink, "fallback_symlink", NULL},
    {"tracked", (getter)dirstate_item_get_tracked, NULL, "tracked", NULL},
    {"p1_tracked", (getter)dirstate_item_get_p1_tracked, NULL, "p1_tracked",
     NULL},
    {"added", (getter)dirstate_item_get_added, NULL, "added", NULL},
    {"p2_info", (getter)dirstate_item_get_p2_info, NULL, "p2_info", NULL},
    {"modified", (getter)dirstate_item_get_modified, NULL, "modified", NULL},
    {"from_p2", (getter)dirstate_item_get_from_p2, NULL, "from_p2", NULL},
    {"maybe_clean", (getter)dirstate_item_get_maybe_clean, NULL, "maybe_clean",
     NULL},
    {"any_tracked", (getter)dirstate_item_get_any_tracked, NULL, "any_tracked",
     NULL},
    {"removed", (getter)dirstate_item_get_removed, NULL, "removed", NULL},
    {NULL} /* Sentinel */
};

PyTypeObject dirstateItemType = {
    PyVarObject_HEAD_INIT(NULL, 0)     /* header */
    "dirstate_tuple",                  /* tp_name */
    sizeof(dirstateItemObject),        /* tp_basicsize */
    0,                                 /* tp_itemsize */
    (destructor)dirstate_item_dealloc, /* tp_dealloc */
    0,                                 /* tp_print */
    0,                                 /* tp_getattr */
    0,                                 /* tp_setattr */
    0,                                 /* tp_compare */
    0,                                 /* tp_repr */
    0,                                 /* tp_as_number */
    0,                                 /* tp_as_sequence */
    0,                                 /* tp_as_mapping */
    0,                                 /* tp_hash  */
    0,                                 /* tp_call */
    0,                                 /* tp_str */
    0,                                 /* tp_getattro */
    0,                                 /* tp_setattro */
    0,                                 /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT,                /* tp_flags */
    "dirstate tuple",                  /* tp_doc */
    0,                                 /* tp_traverse */
    0,                                 /* tp_clear */
    0,                                 /* tp_richcompare */
    0,                                 /* tp_weaklistoffset */
    0,                                 /* tp_iter */
    0,                                 /* tp_iternext */
    dirstate_item_methods,             /* tp_methods */
    0,                                 /* tp_members */
    dirstate_item_getset,              /* tp_getset */
    0,                                 /* tp_base */
    0,                                 /* tp_dict */
    0,                                 /* tp_descr_get */
    0,                                 /* tp_descr_set */
    0,                                 /* tp_dictoffset */
    0,                                 /* tp_init */
    0,                                 /* tp_alloc */
    dirstate_item_new,                 /* tp_new */
};

static PyObject *parse_dirstate(PyObject *self, PyObject *args)
{
	PyObject *dmap, *cmap, *parents = NULL, *ret = NULL;
	PyObject *fname = NULL, *cname = NULL, *entry = NULL;
	char state, *cur, *str, *cpos;
	int mode, size, mtime;
	unsigned int flen, pos = 40;
	Py_ssize_t len = 40;
	Py_ssize_t readlen;

	if (!PyArg_ParseTuple(args, "O!O!y#:parse_dirstate", &PyDict_Type,
	                      &dmap, &PyDict_Type, &cmap, &str, &readlen)) {
		goto quit;
	}

	len = readlen;

	/* read parents */
	if (len < 40) {
		PyErr_SetString(PyExc_ValueError,
		                "too little data for parents");
		goto quit;
	}

	parents = Py_BuildValue("y#y#", str, (Py_ssize_t)20, str + 20,
	                        (Py_ssize_t)20);
	if (!parents) {
		goto quit;
	}

	/* read filenames */
	while (pos >= 40 && pos < len) {
		if (pos + 17 > len) {
			PyErr_SetString(PyExc_ValueError,
			                "overflow in dirstate");
			goto quit;
		}
		cur = str + pos;
		/* unpack header */
		state = *cur;
		mode = getbe32(cur + 1);
		size = getbe32(cur + 5);
		mtime = getbe32(cur + 9);
		flen = getbe32(cur + 13);
		pos += 17;
		cur += 17;
		if (flen > len - pos) {
			PyErr_SetString(PyExc_ValueError,
			                "overflow in dirstate");
			goto quit;
		}

		entry = (PyObject *)dirstate_item_from_v1_data(state, mode,
		                                               size, mtime);
		if (!entry)
			goto quit;
		cpos = memchr(cur, 0, flen);
		if (cpos) {
			fname = PyBytes_FromStringAndSize(cur, cpos - cur);
			cname = PyBytes_FromStringAndSize(
			    cpos + 1, flen - (cpos - cur) - 1);
			if (!fname || !cname ||
			    PyDict_SetItem(cmap, fname, cname) == -1 ||
			    PyDict_SetItem(dmap, fname, entry) == -1) {
				goto quit;
			}
			Py_DECREF(cname);
		} else {
			fname = PyBytes_FromStringAndSize(cur, flen);
			if (!fname ||
			    PyDict_SetItem(dmap, fname, entry) == -1) {
				goto quit;
			}
		}
		Py_DECREF(fname);
		Py_DECREF(entry);
		fname = cname = entry = NULL;
		pos += flen;
	}

	ret = parents;
	Py_INCREF(ret);
quit:
	Py_XDECREF(fname);
	Py_XDECREF(cname);
	Py_XDECREF(entry);
	Py_XDECREF(parents);
	return ret;
}

/*
 * Efficiently pack a dirstate object into its on-disk format.
 */
static PyObject *pack_dirstate(PyObject *self, PyObject *args)
{
	PyObject *packobj = NULL;
	PyObject *map, *copymap, *pl, *mtime_unset = NULL;
	Py_ssize_t nbytes, pos, l;
	PyObject *k, *v = NULL, *pn;
	char *p, *s;

	if (!PyArg_ParseTuple(args, "O!O!O!:pack_dirstate", &PyDict_Type, &map,
	                      &PyDict_Type, &copymap, &PyTuple_Type, &pl)) {
		return NULL;
	}

	if (PyTuple_Size(pl) != 2) {
		PyErr_SetString(PyExc_TypeError, "expected 2-element tuple");
		return NULL;
	}

	/* Figure out how much we need to allocate. */
	for (nbytes = 40, pos = 0; PyDict_Next(map, &pos, &k, &v);) {
		PyObject *c;
		if (!PyBytes_Check(k)) {
			PyErr_SetString(PyExc_TypeError, "expected string key");
			goto bail;
		}
		nbytes += PyBytes_GET_SIZE(k) + 17;
		c = PyDict_GetItem(copymap, k);
		if (c) {
			if (!PyBytes_Check(c)) {
				PyErr_SetString(PyExc_TypeError,
				                "expected string key");
				goto bail;
			}
			nbytes += PyBytes_GET_SIZE(c) + 1;
		}
	}

	packobj = PyBytes_FromStringAndSize(NULL, nbytes);
	if (packobj == NULL) {
		goto bail;
	}

	p = PyBytes_AS_STRING(packobj);

	pn = PyTuple_GET_ITEM(pl, 0);
	if (PyBytes_AsStringAndSize(pn, &s, &l) == -1 || l != 20) {
		PyErr_SetString(PyExc_TypeError, "expected a 20-byte hash");
		goto bail;
	}
	memcpy(p, s, l);
	p += 20;
	pn = PyTuple_GET_ITEM(pl, 1);
	if (PyBytes_AsStringAndSize(pn, &s, &l) == -1 || l != 20) {
		PyErr_SetString(PyExc_TypeError, "expected a 20-byte hash");
		goto bail;
	}
	memcpy(p, s, l);
	p += 20;

	for (pos = 0; PyDict_Next(map, &pos, &k, &v);) {
		dirstateItemObject *tuple;
		char state;
		int mode, size, mtime;
		Py_ssize_t len, l;
		PyObject *o;
		char *t;

		if (!dirstate_tuple_check(v)) {
			PyErr_SetString(PyExc_TypeError,
			                "expected a dirstate tuple");
			goto bail;
		}
		tuple = (dirstateItemObject *)v;

		state = dirstate_item_c_v1_state(tuple);
		mode = dirstate_item_c_v1_mode(tuple);
		size = dirstate_item_c_v1_size(tuple);
		mtime = dirstate_item_c_v1_mtime(tuple);
		*p++ = state;
		putbe32((uint32_t)mode, p);
		putbe32((uint32_t)size, p + 4);
		putbe32((uint32_t)mtime, p + 8);
		t = p + 12;
		p += 16;
		len = PyBytes_GET_SIZE(k);
		memcpy(p, PyBytes_AS_STRING(k), len);
		p += len;
		o = PyDict_GetItem(copymap, k);
		if (o) {
			*p++ = '\0';
			l = PyBytes_GET_SIZE(o);
			memcpy(p, PyBytes_AS_STRING(o), l);
			p += l;
			len += l + 1;
		}
		putbe32((uint32_t)len, t);
	}

	pos = p - PyBytes_AS_STRING(packobj);
	if (pos != nbytes) {
		PyErr_Format(PyExc_SystemError, "bad dirstate size: %ld != %ld",
		             (long)pos, (long)nbytes);
		goto bail;
	}

	return packobj;
bail:
	Py_XDECREF(mtime_unset);
	Py_XDECREF(packobj);
	Py_XDECREF(v);
	return NULL;
}

#define BUMPED_FIX 1
#define USING_SHA_256 2
#define FM1_HEADER_SIZE (4 + 8 + 2 + 2 + 1 + 1 + 1)

static PyObject *readshas(const char *source, unsigned char num,
                          Py_ssize_t hashwidth)
{
	int i;
	PyObject *list = PyTuple_New(num);
	if (list == NULL) {
		return NULL;
	}
	for (i = 0; i < num; i++) {
		PyObject *hash = PyBytes_FromStringAndSize(source, hashwidth);
		if (hash == NULL) {
			Py_DECREF(list);
			return NULL;
		}
		PyTuple_SET_ITEM(list, i, hash);
		source += hashwidth;
	}
	return list;
}

static PyObject *fm1readmarker(const char *databegin, const char *dataend,
                               uint32_t *msize)
{
	const char *data = databegin;
	const char *meta;

	double mtime;
	int16_t tz;
	uint16_t flags;
	unsigned char nsuccs, nparents, nmetadata;
	Py_ssize_t hashwidth = 20;

	PyObject *prec = NULL, *parents = NULL, *succs = NULL;
	PyObject *metadata = NULL, *ret = NULL;
	int i;

	if (data + FM1_HEADER_SIZE > dataend) {
		goto overflow;
	}

	*msize = getbe32(data);
	data += 4;
	mtime = getbefloat64(data);
	data += 8;
	tz = getbeint16(data);
	data += 2;
	flags = getbeuint16(data);
	data += 2;

	if (flags & USING_SHA_256) {
		hashwidth = 32;
	}

	nsuccs = (unsigned char)(*data++);
	nparents = (unsigned char)(*data++);
	nmetadata = (unsigned char)(*data++);

	if (databegin + *msize > dataend) {
		goto overflow;
	}
	dataend = databegin + *msize; /* narrow down to marker size */

	if (data + hashwidth > dataend) {
		goto overflow;
	}
	prec = PyBytes_FromStringAndSize(data, hashwidth);
	data += hashwidth;
	if (prec == NULL) {
		goto bail;
	}

	if (data + nsuccs * hashwidth > dataend) {
		goto overflow;
	}
	succs = readshas(data, nsuccs, hashwidth);
	if (succs == NULL) {
		goto bail;
	}
	data += nsuccs * hashwidth;

	if (nparents == 1 || nparents == 2) {
		if (data + nparents * hashwidth > dataend) {
			goto overflow;
		}
		parents = readshas(data, nparents, hashwidth);
		if (parents == NULL) {
			goto bail;
		}
		data += nparents * hashwidth;
	} else {
		parents = Py_None;
		Py_INCREF(parents);
	}

	if (data + 2 * nmetadata > dataend) {
		goto overflow;
	}
	meta = data + (2 * nmetadata);
	metadata = PyTuple_New(nmetadata);
	if (metadata == NULL) {
		goto bail;
	}
	for (i = 0; i < nmetadata; i++) {
		PyObject *tmp, *left = NULL, *right = NULL;
		Py_ssize_t leftsize = (unsigned char)(*data++);
		Py_ssize_t rightsize = (unsigned char)(*data++);
		if (meta + leftsize + rightsize > dataend) {
			goto overflow;
		}
		left = PyBytes_FromStringAndSize(meta, leftsize);
		meta += leftsize;
		right = PyBytes_FromStringAndSize(meta, rightsize);
		meta += rightsize;
		tmp = PyTuple_New(2);
		if (!left || !right || !tmp) {
			Py_XDECREF(left);
			Py_XDECREF(right);
			Py_XDECREF(tmp);
			goto bail;
		}
		PyTuple_SET_ITEM(tmp, 0, left);
		PyTuple_SET_ITEM(tmp, 1, right);
		PyTuple_SET_ITEM(metadata, i, tmp);
	}
	ret = Py_BuildValue("(OOHO(di)O)", prec, succs, flags, metadata, mtime,
	                    (int)tz * 60, parents);
	goto bail; /* return successfully */

overflow:
	PyErr_SetString(PyExc_ValueError, "overflow in obsstore");
bail:
	Py_XDECREF(prec);
	Py_XDECREF(succs);
	Py_XDECREF(metadata);
	Py_XDECREF(parents);
	return ret;
}

static PyObject *fm1readmarkers(PyObject *self, PyObject *args)
{
	const char *data, *dataend;
	Py_ssize_t datalen, offset, stop;
	PyObject *markers = NULL;

	if (!PyArg_ParseTuple(args, "y#nn", &data, &datalen, &offset, &stop)) {
		return NULL;
	}
	if (offset < 0) {
		PyErr_SetString(PyExc_ValueError,
		                "invalid negative offset in fm1readmarkers");
		return NULL;
	}
	if (stop > datalen) {
		PyErr_SetString(
		    PyExc_ValueError,
		    "stop longer than data length in fm1readmarkers");
		return NULL;
	}
	dataend = data + datalen;
	data += offset;
	markers = PyList_New(0);
	if (!markers) {
		return NULL;
	}
	while (offset < stop) {
		uint32_t msize;
		int error;
		PyObject *record = fm1readmarker(data, dataend, &msize);
		if (!record) {
			goto bail;
		}
		error = PyList_Append(markers, record);
		Py_DECREF(record);
		if (error) {
			goto bail;
		}
		data += msize;
		offset += msize;
	}
	return markers;
bail:
	Py_DECREF(markers);
	return NULL;
}

static char parsers_doc[] = "Efficient content parsing.";

PyObject *encodedir(PyObject *self, PyObject *args);
PyObject *pathencode(PyObject *self, PyObject *args);
PyObject *lowerencode(PyObject *self, PyObject *args);
PyObject *parse_index2(PyObject *self, PyObject *args, PyObject *kwargs);

static PyMethodDef methods[] = {
    {"pack_dirstate", pack_dirstate, METH_VARARGS, "pack a dirstate\n"},
    {"parse_dirstate", parse_dirstate, METH_VARARGS, "parse a dirstate\n"},
    {"parse_index2", (PyCFunction)parse_index2, METH_VARARGS | METH_KEYWORDS,
     "parse a revlog index\n"},
    {"isasciistr", isasciistr, METH_VARARGS, "check if an ASCII string\n"},
    {"asciilower", asciilower, METH_VARARGS, "lowercase an ASCII string\n"},
    {"asciiupper", asciiupper, METH_VARARGS, "uppercase an ASCII string\n"},
    {"dict_new_presized", dict_new_presized, METH_VARARGS,
     "construct a dict with an expected size\n"},
    {"make_file_foldmap", make_file_foldmap, METH_VARARGS,
     "make file foldmap\n"},
    {"jsonescapeu8fast", jsonescapeu8fast, METH_VARARGS,
     "escape a UTF-8 byte string to JSON (fast path)\n"},
    {"encodedir", encodedir, METH_VARARGS, "encodedir a path\n"},
    {"pathencode", pathencode, METH_VARARGS, "fncache-encode a path\n"},
    {"lowerencode", lowerencode, METH_VARARGS, "lower-encode a path\n"},
    {"fm1readmarkers", fm1readmarkers, METH_VARARGS,
     "parse v1 obsolete markers\n"},
    {NULL, NULL}};

void dirs_module_init(PyObject *mod);
void manifest_module_init(PyObject *mod);
void revlog_module_init(PyObject *mod);

static const int version = 21;

static void module_init(PyObject *mod)
{
	PyModule_AddIntConstant(mod, "version", version);

	/* This module constant has two purposes.  First, it lets us unit test
	 * the ImportError raised without hard-coding any error text.  This
	 * means we can change the text in the future without breaking tests,
	 * even across changesets without a recompile.  Second, its presence
	 * can be used to determine whether the version-checking logic is
	 * present, which also helps in testing across changesets without a
	 * recompile.  Note that this means the pure-Python version of parsers
	 * should not have this module constant. */
	PyModule_AddStringConstant(mod, "versionerrortext", versionerrortext);

	dirs_module_init(mod);
	manifest_module_init(mod);
	revlog_module_init(mod);

	if (PyType_Ready(&dirstateItemType) < 0) {
		return;
	}
	Py_INCREF(&dirstateItemType);
	PyModule_AddObject(mod, "DirstateItem", (PyObject *)&dirstateItemType);
}

static int check_python_version(void)
{
	PyObject *sys = PyImport_ImportModule("sys"), *ver;
	long hexversion;
	if (!sys) {
		return -1;
	}
	ver = PyObject_GetAttrString(sys, "hexversion");
	Py_DECREF(sys);
	if (!ver) {
		return -1;
	}
	hexversion = PyLong_AsLong(ver);
	Py_DECREF(ver);
	/* sys.hexversion is a 32-bit number by default, so the -1 case
	 * should only occur in unusual circumstances (e.g. if sys.hexversion
	 * is manually set to an invalid value). */
	if ((hexversion == -1) || (hexversion >> 16 != PY_VERSION_HEX >> 16)) {
		PyErr_Format(PyExc_ImportError,
		             "%s: The Mercurial extension "
		             "modules were compiled with Python " PY_VERSION
		             ", but "
		             "Mercurial is currently using Python with "
		             "sys.hexversion=%ld: "
		             "Python %s\n at: %s",
		             versionerrortext, hexversion, Py_GetVersion(),
		             Py_GetProgramFullPath());
		return -1;
	}
	return 0;
}

static struct PyModuleDef parsers_module = {PyModuleDef_HEAD_INIT, "parsers",
                                            parsers_doc, -1, methods};

PyMODINIT_FUNC PyInit_parsers(void)
{
	PyObject *mod;

	if (check_python_version() == -1)
		return NULL;
	mod = PyModule_Create(&parsers_module);
	module_init(mod);
	return mod;
}
