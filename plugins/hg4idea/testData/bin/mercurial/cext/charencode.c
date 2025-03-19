/*
 charencode.c - miscellaneous character encoding

 Copyright 2008 Olivia Mackall <olivia@selenic.com> and others

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <assert.h>

#include "charencode.h"
#include "compat.h"
#include "util.h"

/* clang-format off */
static const char lowertable[128] = {
	'\x00', '\x01', '\x02', '\x03', '\x04', '\x05', '\x06', '\x07',
	'\x08', '\x09', '\x0a', '\x0b', '\x0c', '\x0d', '\x0e', '\x0f',
	'\x10', '\x11', '\x12', '\x13', '\x14', '\x15', '\x16', '\x17',
	'\x18', '\x19', '\x1a', '\x1b', '\x1c', '\x1d', '\x1e', '\x1f',
	'\x20', '\x21', '\x22', '\x23', '\x24', '\x25', '\x26', '\x27',
	'\x28', '\x29', '\x2a', '\x2b', '\x2c', '\x2d', '\x2e', '\x2f',
	'\x30', '\x31', '\x32', '\x33', '\x34', '\x35', '\x36', '\x37',
	'\x38', '\x39', '\x3a', '\x3b', '\x3c', '\x3d', '\x3e', '\x3f',
	'\x40',
	        '\x61', '\x62', '\x63', '\x64', '\x65', '\x66', '\x67', /* A-G */
	'\x68', '\x69', '\x6a', '\x6b', '\x6c', '\x6d', '\x6e', '\x6f', /* H-O */
	'\x70', '\x71', '\x72', '\x73', '\x74', '\x75', '\x76', '\x77', /* P-W */
	'\x78', '\x79', '\x7a',                                         /* X-Z */
	                        '\x5b', '\x5c', '\x5d', '\x5e', '\x5f',
	'\x60', '\x61', '\x62', '\x63', '\x64', '\x65', '\x66', '\x67',
	'\x68', '\x69', '\x6a', '\x6b', '\x6c', '\x6d', '\x6e', '\x6f',
	'\x70', '\x71', '\x72', '\x73', '\x74', '\x75', '\x76', '\x77',
	'\x78', '\x79', '\x7a', '\x7b', '\x7c', '\x7d', '\x7e', '\x7f'
};

static const char uppertable[128] = {
	'\x00', '\x01', '\x02', '\x03', '\x04', '\x05', '\x06', '\x07',
	'\x08', '\x09', '\x0a', '\x0b', '\x0c', '\x0d', '\x0e', '\x0f',
	'\x10', '\x11', '\x12', '\x13', '\x14', '\x15', '\x16', '\x17',
	'\x18', '\x19', '\x1a', '\x1b', '\x1c', '\x1d', '\x1e', '\x1f',
	'\x20', '\x21', '\x22', '\x23', '\x24', '\x25', '\x26', '\x27',
	'\x28', '\x29', '\x2a', '\x2b', '\x2c', '\x2d', '\x2e', '\x2f',
	'\x30', '\x31', '\x32', '\x33', '\x34', '\x35', '\x36', '\x37',
	'\x38', '\x39', '\x3a', '\x3b', '\x3c', '\x3d', '\x3e', '\x3f',
	'\x40', '\x41', '\x42', '\x43', '\x44', '\x45', '\x46', '\x47',
	'\x48', '\x49', '\x4a', '\x4b', '\x4c', '\x4d', '\x4e', '\x4f',
	'\x50', '\x51', '\x52', '\x53', '\x54', '\x55', '\x56', '\x57',
	'\x58', '\x59', '\x5a', '\x5b', '\x5c', '\x5d', '\x5e', '\x5f',
	'\x60',
		'\x41', '\x42', '\x43', '\x44', '\x45', '\x46', '\x47', /* a-g */
	'\x48', '\x49', '\x4a', '\x4b', '\x4c', '\x4d', '\x4e', '\x4f', /* h-o */
	'\x50', '\x51', '\x52', '\x53', '\x54', '\x55', '\x56', '\x57', /* p-w */
	'\x58', '\x59', '\x5a', 					/* x-z */
				'\x7b', '\x7c', '\x7d', '\x7e', '\x7f'
};

/* 1: no escape, 2: \<c>, 6: \u<x> */
static const uint8_t jsonlentable[256] = {
	6, 6, 6, 6, 6, 6, 6, 6, 2, 2, 2, 6, 2, 2, 6, 6, /* b, t, n, f, r */
	6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
	1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, /* " */
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, /* \\ */
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 6, /* DEL */
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
};

static const uint8_t jsonparanoidlentable[128] = {
	6, 6, 6, 6, 6, 6, 6, 6, 2, 2, 2, 6, 2, 2, 6, 6, /* b, t, n, f, r */
	6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
	1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, /* " */
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 6, 1, 6, 1, /* <, > */
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, /* \\ */
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
	1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 6, /* DEL */
};

static const char hexchartable[16] = {
	'0', '1', '2', '3', '4', '5', '6', '7',
	'8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
};
/* clang-format on */

/*
 * Turn a hex-encoded string into binary.
 */
PyObject *unhexlify(const char *str, Py_ssize_t len)
{
	PyObject *ret;
	char *d;
	Py_ssize_t i;

	ret = PyBytes_FromStringAndSize(NULL, len / 2);

	if (!ret) {
		return NULL;
	}

	d = PyBytes_AsString(ret);

	for (i = 0; i < len;) {
		int hi = hexdigit(str, i++);
		int lo = hexdigit(str, i++);
		*d++ = (hi << 4) | lo;
	}

	return ret;
}

PyObject *isasciistr(PyObject *self, PyObject *args)
{
	const char *buf;
	Py_ssize_t i, len;
	if (!PyArg_ParseTuple(args, "y#:isasciistr", &buf, &len)) {
		return NULL;
	}
	i = 0;
	/* char array in PyStringObject should be at least 4-byte aligned */
	if (((uintptr_t)buf & 3) == 0) {
		const uint32_t *p = (const uint32_t *)buf;
		for (; i < len / 4; i++) {
			if (p[i] & 0x80808080U) {
				Py_RETURN_FALSE;
			}
		}
		i *= 4;
	}
	for (; i < len; i++) {
		if (buf[i] & 0x80) {
			Py_RETURN_FALSE;
		}
	}
	Py_RETURN_TRUE;
}

static inline PyObject *
_asciitransform(PyObject *str_obj, const char table[128], PyObject *fallback_fn)
{
	char *str, *newstr;
	Py_ssize_t i, len;
	PyObject *newobj = NULL;
	PyObject *ret = NULL;

	str = PyBytes_AS_STRING(str_obj);
	len = PyBytes_GET_SIZE(str_obj);

	newobj = PyBytes_FromStringAndSize(NULL, len);
	if (!newobj) {
		goto quit;
	}

	newstr = PyBytes_AS_STRING(newobj);

	for (i = 0; i < len; i++) {
		char c = str[i];
		if (c & 0x80) {
			if (fallback_fn != NULL) {
				ret = PyObject_CallFunctionObjArgs(
				    fallback_fn, str_obj, NULL);
			} else {
				PyObject *err = PyUnicodeDecodeError_Create(
				    "ascii", str, len, i, (i + 1),
				    "unexpected code byte");
				PyErr_SetObject(PyExc_UnicodeDecodeError, err);
				Py_XDECREF(err);
			}
			goto quit;
		}
		newstr[i] = table[(unsigned char)c];
	}

	ret = newobj;
	Py_INCREF(ret);
quit:
	Py_XDECREF(newobj);
	return ret;
}

PyObject *asciilower(PyObject *self, PyObject *args)
{
	PyObject *str_obj;
	if (!PyArg_ParseTuple(args, "O!:asciilower", &PyBytes_Type, &str_obj)) {
		return NULL;
	}
	return _asciitransform(str_obj, lowertable, NULL);
}

PyObject *asciiupper(PyObject *self, PyObject *args)
{
	PyObject *str_obj;
	if (!PyArg_ParseTuple(args, "O!:asciiupper", &PyBytes_Type, &str_obj)) {
		return NULL;
	}
	return _asciitransform(str_obj, uppertable, NULL);
}

PyObject *make_file_foldmap(PyObject *self, PyObject *args)
{
	PyObject *dmap, *spec_obj, *normcase_fallback;
	PyObject *file_foldmap = NULL;
	enum normcase_spec spec;
	PyObject *k, *v;
	dirstateItemObject *tuple;
	Py_ssize_t pos = 0;
	const char *table;

	if (!PyArg_ParseTuple(args, "O!O!O!:make_file_foldmap", &PyDict_Type,
	                      &dmap, &PyLong_Type, &spec_obj, &PyFunction_Type,
	                      &normcase_fallback)) {
		goto quit;
	}

	spec = (int)PyLong_AS_LONG(spec_obj);
	switch (spec) {
	case NORMCASE_LOWER:
		table = lowertable;
		break;
	case NORMCASE_UPPER:
		table = uppertable;
		break;
	case NORMCASE_OTHER:
		table = NULL;
		break;
	default:
		PyErr_SetString(PyExc_TypeError, "invalid normcasespec");
		goto quit;
	}

	/* Add some more entries to deal with additions outside this
	   function. */
	file_foldmap = _dict_new_presized((PyDict_Size(dmap) / 10) * 11);
	if (file_foldmap == NULL) {
		goto quit;
	}

	while (PyDict_Next(dmap, &pos, &k, &v)) {
		if (!dirstate_tuple_check(v)) {
			PyErr_SetString(PyExc_TypeError,
			                "expected a dirstate tuple");
			goto quit;
		}

		tuple = (dirstateItemObject *)v;
		if (tuple->flags | dirstate_flag_wc_tracked) {
			PyObject *normed;
			if (table != NULL) {
				normed = _asciitransform(k, table,
				                         normcase_fallback);
			} else {
				normed = PyObject_CallFunctionObjArgs(
				    normcase_fallback, k, NULL);
			}

			if (normed == NULL) {
				goto quit;
			}
			if (PyDict_SetItem(file_foldmap, normed, k) == -1) {
				Py_DECREF(normed);
				goto quit;
			}
			Py_DECREF(normed);
		}
	}
	return file_foldmap;
quit:
	Py_XDECREF(file_foldmap);
	return NULL;
}

/* calculate length of JSON-escaped string; returns -1 if unsupported */
static Py_ssize_t jsonescapelen(const char *buf, Py_ssize_t len, bool paranoid)
{
	Py_ssize_t i, esclen = 0;

	if (paranoid) {
		/* don't want to process multi-byte escapes in C */
		for (i = 0; i < len; i++) {
			char c = buf[i];
			if (c & 0x80) {
				PyErr_SetString(PyExc_ValueError,
				                "cannot process non-ascii str");
				return -1;
			}
			esclen += jsonparanoidlentable[(unsigned char)c];
			if (esclen < 0) {
				PyErr_SetString(PyExc_MemoryError,
				                "overflow in jsonescapelen");
				return -1;
			}
		}
	} else {
		for (i = 0; i < len; i++) {
			char c = buf[i];
			esclen += jsonlentable[(unsigned char)c];
			if (esclen < 0) {
				PyErr_SetString(PyExc_MemoryError,
				                "overflow in jsonescapelen");
				return -1;
			}
		}
	}

	return esclen;
}

/* map '\<c>' escape character */
static char jsonescapechar2(char c)
{
	switch (c) {
	case '\b':
		return 'b';
	case '\t':
		return 't';
	case '\n':
		return 'n';
	case '\f':
		return 'f';
	case '\r':
		return 'r';
	case '"':
		return '"';
	case '\\':
		return '\\';
	}
	return '\0'; /* should not happen */
}

/* convert 'origbuf' to JSON-escaped form 'escbuf'; 'origbuf' should only
   include characters mappable by json(paranoid)lentable */
static void encodejsonescape(char *escbuf, Py_ssize_t esclen,
                             const char *origbuf, Py_ssize_t origlen,
                             bool paranoid)
{
	const uint8_t *lentable =
	    (paranoid) ? jsonparanoidlentable : jsonlentable;
	Py_ssize_t i, j;

	for (i = 0, j = 0; i < origlen; i++) {
		char c = origbuf[i];
		uint8_t l = lentable[(unsigned char)c];
		assert(j + l <= esclen);
		switch (l) {
		case 1:
			escbuf[j] = c;
			break;
		case 2:
			escbuf[j] = '\\';
			escbuf[j + 1] = jsonescapechar2(c);
			break;
		case 6:
			memcpy(escbuf + j, "\\u00", 4);
			escbuf[j + 4] = hexchartable[(unsigned char)c >> 4];
			escbuf[j + 5] = hexchartable[(unsigned char)c & 0xf];
			break;
		}
		j += l;
	}
}

PyObject *jsonescapeu8fast(PyObject *self, PyObject *args)
{
	PyObject *origstr, *escstr;
	const char *origbuf;
	Py_ssize_t origlen, esclen;
	int paranoid;
	if (!PyArg_ParseTuple(args, "O!i:jsonescapeu8fast", &PyBytes_Type,
	                      &origstr, &paranoid)) {
		return NULL;
	}

	origbuf = PyBytes_AS_STRING(origstr);
	origlen = PyBytes_GET_SIZE(origstr);
	esclen = jsonescapelen(origbuf, origlen, paranoid);
	if (esclen < 0) {
		return NULL; /* unsupported char found or overflow */
	}
	if (origlen == esclen) {
		Py_INCREF(origstr);
		return origstr;
	}

	escstr = PyBytes_FromStringAndSize(NULL, esclen);
	if (!escstr) {
		return NULL;
	}
	encodejsonescape(PyBytes_AS_STRING(escstr), esclen, origbuf, origlen,
	                 paranoid);

	return escstr;
}
