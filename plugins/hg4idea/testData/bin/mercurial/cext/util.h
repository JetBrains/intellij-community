/*
 util.h - utility functions for interfacing with the various python APIs.

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#ifndef _HG_UTIL_H_
#define _HG_UTIL_H_

#include "compat.h"

#if PY_MAJOR_VERSION >= 3
#define IS_PY3K
#endif

/* helper to switch things like string literal depending on Python version */
#ifdef IS_PY3K
#define PY23(py2, py3) py3
#else
#define PY23(py2, py3) py2
#endif

/* clang-format off */
typedef struct {
	PyObject_HEAD
	char state;
	int mode;
	int size;
	int mtime;
} dirstateItemObject;
/* clang-format on */

extern PyTypeObject dirstateItemType;
#define dirstate_tuple_check(op) (Py_TYPE(op) == &dirstateItemType)

#ifndef MIN
#define MIN(a, b) (((a) < (b)) ? (a) : (b))
#endif
/* VC9 doesn't include bool and lacks stdbool.h based on my searching */
#if defined(_MSC_VER) || __STDC_VERSION__ < 199901L
#define true 1
#define false 0
typedef unsigned char bool;
#else
#include <stdbool.h>
#endif

static inline PyObject *_dict_new_presized(Py_ssize_t expected_size)
{
	/* _PyDict_NewPresized expects a minused parameter, but it actually
	   creates a dictionary that's the nearest power of two bigger than the
	   parameter. For example, with the initial minused = 1000, the
	   dictionary created has size 1024. Of course in a lot of cases that
	   can be greater than the maximum load factor Python's dict object
	   expects (= 2/3), so as soon as we cross the threshold we'll resize
	   anyway. So create a dictionary that's at least 3/2 the size. */
	return _PyDict_NewPresized(((1 + expected_size) / 2) * 3);
}

/* Convert a PyInt or PyLong to a long. Returns false if there is an
   error, in which case an exception will already have been set. */
static inline bool pylong_to_long(PyObject *pylong, long *out)
{
	*out = PyLong_AsLong(pylong);
	/* Fast path to avoid hitting PyErr_Occurred if the value was obviously
	 * not an error. */
	if (*out != -1) {
		return true;
	}
	return PyErr_Occurred() == NULL;
}

#endif /* _HG_UTIL_H_ */
