/*
 pathencode.c - efficient path name encoding

 Copyright 2012 Facebook

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

/*
 * An implementation of the name encoding scheme used by the fncache
 * store.  The common case is of a path < 120 bytes long, which is
 * handled either in a single pass with no allocations or two passes
 * with a single allocation.  For longer paths, multiple passes are
 * required.
 */

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <assert.h>
#include <ctype.h>
#include <stdlib.h>
#include <string.h>
#include "pythoncapi_compat.h"

#include "util.h"

/* state machine for the fast path */
enum path_state {
	START, /* first byte of a path component */
	A,     /* "AUX" */
	AU,
	THIRD, /* third of a 3-byte sequence, e.g. "AUX", "NUL" */
	C,     /* "CON" or "COMn" */
	CO,
	COMLPT, /* "COM" or "LPT" */
	COMLPTn,
	L,
	LP,
	N,
	NU,
	P, /* "PRN" */
	PR,
	LDOT, /* leading '.' */
	DOT,  /* '.' in a non-leading position */
	H,    /* ".h" */
	HGDI, /* ".hg", ".d", or ".i" */
	SPACE,
	DEFAULT, /* byte of a path component after the first */
};

/* state machine for dir-encoding */
enum dir_state {
	DDOT,
	DH,
	DHGDI,
	DDEFAULT,
};

static inline int inset(const uint32_t bitset[], char c)
{
	return bitset[((uint8_t)c) >> 5] & (1 << (((uint8_t)c) & 31));
}

static inline void charcopy(char *dest, Py_ssize_t *destlen, size_t destsize,
                            char c)
{
	if (dest) {
		assert(*destlen < destsize);
		dest[*destlen] = c;
	}
	(*destlen)++;
}

static inline void memcopy(char *dest, Py_ssize_t *destlen, size_t destsize,
                           const void *src, Py_ssize_t len)
{
	if (dest) {
		assert(*destlen + len < destsize);
		memcpy((void *)&dest[*destlen], src, len);
	}
	*destlen += len;
}

static inline void hexencode(char *dest, Py_ssize_t *destlen, size_t destsize,
                             uint8_t c)
{
	static const char hexdigit[] = "0123456789abcdef";

	charcopy(dest, destlen, destsize, hexdigit[c >> 4]);
	charcopy(dest, destlen, destsize, hexdigit[c & 15]);
}

/* 3-byte escape: tilde followed by two hex digits */
static inline void escape3(char *dest, Py_ssize_t *destlen, size_t destsize,
                           char c)
{
	charcopy(dest, destlen, destsize, '~');
	hexencode(dest, destlen, destsize, c);
}

static Py_ssize_t _encodedir(char *dest, size_t destsize, const char *src,
                             Py_ssize_t len)
{
	enum dir_state state = DDEFAULT;
	Py_ssize_t i = 0, destlen = 0;

	while (i < len) {
		switch (state) {
		case DDOT:
			switch (src[i]) {
			case 'd':
			case 'i':
				state = DHGDI;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'h':
				state = DH;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			default:
				state = DDEFAULT;
				break;
			}
			break;
		case DH:
			if (src[i] == 'g') {
				state = DHGDI;
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DDEFAULT;
			}
			break;
		case DHGDI:
			if (src[i] == '/') {
				memcopy(dest, &destlen, destsize, ".hg", 3);
				charcopy(dest, &destlen, destsize, src[i++]);
			}
			state = DDEFAULT;
			break;
		case DDEFAULT:
			if (src[i] == '.') {
				state = DDOT;
			}
			charcopy(dest, &destlen, destsize, src[i++]);
			break;
		}
	}

	return destlen;
}

PyObject *encodedir(PyObject *self, PyObject *args)
{
	Py_ssize_t len, newlen;
	PyObject *pathobj, *newobj;
	char *path;

	if (!PyArg_ParseTuple(args, "O:encodedir", &pathobj)) {
		return NULL;
	}

	if (PyBytes_AsStringAndSize(pathobj, &path, &len) == -1) {
		PyErr_SetString(PyExc_TypeError, "expected a string");
		return NULL;
	}

	newlen = len ? _encodedir(NULL, 0, path, len + 1) : 1;

	if (newlen == len + 1) {
		Py_INCREF(pathobj);
		return pathobj;
	}

	newobj = PyBytes_FromStringAndSize(NULL, newlen);

	if (newobj) {
		assert(PyBytes_Check(newobj));
		Py_SET_SIZE(newobj, Py_SIZE(newobj) - 1);
		_encodedir(PyBytes_AS_STRING(newobj), newlen, path, len + 1);
	}

	return newobj;
}

static Py_ssize_t _encode(const uint32_t twobytes[8], const uint32_t onebyte[8],
                          char *dest, Py_ssize_t destlen, size_t destsize,
                          const char *src, Py_ssize_t len, int encodedir)
{
	enum path_state state = START;
	Py_ssize_t i = 0;

	/*
	 * Python strings end with a zero byte, which we use as a
	 * terminal token as they are not valid inside path names.
	 */

	while (i < len) {
		switch (state) {
		case START:
			switch (src[i]) {
			case '/':
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case '.':
				state = LDOT;
				escape3(dest, &destlen, destsize, src[i++]);
				break;
			case ' ':
				state = DEFAULT;
				escape3(dest, &destlen, destsize, src[i++]);
				break;
			case 'a':
				state = A;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'c':
				state = C;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'l':
				state = L;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'n':
				state = N;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'p':
				state = P;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			default:
				state = DEFAULT;
				break;
			}
			break;
		case A:
			if (src[i] == 'u') {
				state = AU;
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DEFAULT;
			}
			break;
		case AU:
			if (src[i] == 'x') {
				state = THIRD;
				i++;
			} else {
				state = DEFAULT;
			}
			break;
		case THIRD:
			state = DEFAULT;
			switch (src[i]) {
			case '.':
			case '/':
			case '\0':
				escape3(dest, &destlen, destsize, src[i - 1]);
				break;
			default:
				i--;
				break;
			}
			break;
		case C:
			if (src[i] == 'o') {
				state = CO;
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DEFAULT;
			}
			break;
		case CO:
			if (src[i] == 'm') {
				state = COMLPT;
				i++;
			} else if (src[i] == 'n') {
				state = THIRD;
				i++;
			} else {
				state = DEFAULT;
			}
			break;
		case COMLPT:
			switch (src[i]) {
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				state = COMLPTn;
				i++;
				break;
			default:
				state = DEFAULT;
				charcopy(dest, &destlen, destsize, src[i - 1]);
				break;
			}
			break;
		case COMLPTn:
			state = DEFAULT;
			switch (src[i]) {
			case '.':
			case '/':
			case '\0':
				escape3(dest, &destlen, destsize, src[i - 2]);
				charcopy(dest, &destlen, destsize, src[i - 1]);
				break;
			default:
				memcopy(dest, &destlen, destsize, &src[i - 2],
				        2);
				break;
			}
			break;
		case L:
			if (src[i] == 'p') {
				state = LP;
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DEFAULT;
			}
			break;
		case LP:
			if (src[i] == 't') {
				state = COMLPT;
				i++;
			} else {
				state = DEFAULT;
			}
			break;
		case N:
			if (src[i] == 'u') {
				state = NU;
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DEFAULT;
			}
			break;
		case NU:
			if (src[i] == 'l') {
				state = THIRD;
				i++;
			} else {
				state = DEFAULT;
			}
			break;
		case P:
			if (src[i] == 'r') {
				state = PR;
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DEFAULT;
			}
			break;
		case PR:
			if (src[i] == 'n') {
				state = THIRD;
				i++;
			} else {
				state = DEFAULT;
			}
			break;
		case LDOT:
			switch (src[i]) {
			case 'd':
			case 'i':
				state = HGDI;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'h':
				state = H;
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			default:
				state = DEFAULT;
				break;
			}
			break;
		case DOT:
			switch (src[i]) {
			case '/':
			case '\0':
				state = START;
				memcopy(dest, &destlen, destsize, "~2e", 3);
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'd':
			case 'i':
				state = HGDI;
				charcopy(dest, &destlen, destsize, '.');
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			case 'h':
				state = H;
				memcopy(dest, &destlen, destsize, ".h", 2);
				i++;
				break;
			default:
				state = DEFAULT;
				charcopy(dest, &destlen, destsize, '.');
				break;
			}
			break;
		case H:
			if (src[i] == 'g') {
				state = HGDI;
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DEFAULT;
			}
			break;
		case HGDI:
			if (src[i] == '/') {
				state = START;
				if (encodedir) {
					memcopy(dest, &destlen, destsize, ".hg",
					        3);
				}
				charcopy(dest, &destlen, destsize, src[i++]);
			} else {
				state = DEFAULT;
			}
			break;
		case SPACE:
			switch (src[i]) {
			case '/':
			case '\0':
				state = START;
				memcopy(dest, &destlen, destsize, "~20", 3);
				charcopy(dest, &destlen, destsize, src[i++]);
				break;
			default:
				state = DEFAULT;
				charcopy(dest, &destlen, destsize, ' ');
				break;
			}
			break;
		case DEFAULT:
			while (inset(onebyte, src[i])) {
				charcopy(dest, &destlen, destsize, src[i++]);
				if (i == len) {
					goto done;
				}
			}
			switch (src[i]) {
			case '.':
				state = DOT;
				i++;
				break;
			case ' ':
				state = SPACE;
				i++;
				break;
			case '/':
				state = START;
				charcopy(dest, &destlen, destsize, '/');
				i++;
				break;
			default:
				if (inset(onebyte, src[i])) {
					do {
						charcopy(dest, &destlen,
						         destsize, src[i++]);
					} while (i < len &&
					         inset(onebyte, src[i]));
				} else if (inset(twobytes, src[i])) {
					char c = src[i++];
					charcopy(dest, &destlen, destsize, '_');
					charcopy(dest, &destlen, destsize,
					         c == '_' ? '_' : c + 32);
				} else {
					escape3(dest, &destlen, destsize,
					        src[i++]);
				}
				break;
			}
			break;
		}
	}
done:
	return destlen;
}

static Py_ssize_t basicencode(char *dest, size_t destsize, const char *src,
                              Py_ssize_t len)
{
	static const uint32_t twobytes[8] = {0, 0, 0x87fffffe};

	static const uint32_t onebyte[8] = {
	    1,
	    0x2bff3bfa,
	    0x68000001,
	    0x2fffffff,
	};

	Py_ssize_t destlen = 0;

	return _encode(twobytes, onebyte, dest, destlen, destsize, src, len, 1);
}

static const Py_ssize_t maxstorepathlen = 120;

static Py_ssize_t _lowerencode(char *dest, size_t destsize, const char *src,
                               Py_ssize_t len)
{
	static const uint32_t onebyte[8] = {1, 0x2bfffbfb, 0xe8000001,
	                                    0x2fffffff};

	static const uint32_t lower[8] = {0, 0, 0x7fffffe};

	Py_ssize_t i, destlen = 0;

	for (i = 0; i < len; i++) {
		if (inset(onebyte, src[i])) {
			charcopy(dest, &destlen, destsize, src[i]);
		} else if (inset(lower, src[i])) {
			charcopy(dest, &destlen, destsize, src[i] + 32);
		} else {
			escape3(dest, &destlen, destsize, src[i]);
		}
	}

	return destlen;
}

PyObject *lowerencode(PyObject *self, PyObject *args)
{
	char *path;
	Py_ssize_t len, newlen;
	PyObject *ret;

	if (!PyArg_ParseTuple(args, "y#:lowerencode", &path, &len)) {
		return NULL;
	}

	newlen = _lowerencode(NULL, 0, path, len);
	ret = PyBytes_FromStringAndSize(NULL, newlen);
	if (ret) {
		_lowerencode(PyBytes_AS_STRING(ret), newlen, path, len);
	}

	return ret;
}

/* See store.py:_auxencode for a description. */
static Py_ssize_t auxencode(char *dest, size_t destsize, const char *src,
                            Py_ssize_t len)
{
	static const uint32_t twobytes[8];

	static const uint32_t onebyte[8] = {
	    ~0U, 0xffff3ffe, ~0U, ~0U, ~0U, ~0U, ~0U, ~0U,
	};

	return _encode(twobytes, onebyte, dest, 0, destsize, src, len, 0);
}

static PyObject *hashmangle(const char *src, Py_ssize_t len, const char sha[20])
{
	static const Py_ssize_t dirprefixlen = 8;
	static const Py_ssize_t maxshortdirslen = 68;
	char *dest;
	PyObject *ret;

	Py_ssize_t i, d, p, lastslash = len - 1, lastdot = -1;
	Py_ssize_t destsize, destlen = 0, slop, used;

	while (lastslash >= 0 && src[lastslash] != '/') {
		if (src[lastslash] == '.' && lastdot == -1) {
			lastdot = lastslash;
		}
		lastslash--;
	}

#if 0
	/* All paths should end in a suffix of ".i" or ".d".
           Unfortunately, the file names in test-hybridencode.py
           violate this rule.  */
	if (lastdot != len - 3) {
		PyErr_SetString(PyExc_ValueError,
				"suffix missing or wrong length");
		return NULL;
	}
#endif

	/* If src contains a suffix, we will append it to the end of
	   the new string, so make room. */
	destsize = 120;
	if (lastdot >= 0) {
		destsize += len - lastdot - 1;
	}

	ret = PyBytes_FromStringAndSize(NULL, destsize);
	if (ret == NULL) {
		return NULL;
	}

	dest = PyBytes_AS_STRING(ret);
	memcopy(dest, &destlen, destsize, "dh/", 3);

	/* Copy up to dirprefixlen bytes of each path component, up to
	   a limit of maxshortdirslen bytes. */
	for (i = d = p = 0; i < lastslash; i++, p++) {
		if (src[i] == '/') {
			char d = dest[destlen - 1];
			/* After truncation, a directory name may end
			   in a space or dot, which are unportable. */
			if (d == '.' || d == ' ') {
				dest[destlen - 1] = '_';
				/* The + 3 is to account for "dh/" in the
				 * beginning */
			}
			if (destlen > maxshortdirslen + 3) {
				break;
			}
			charcopy(dest, &destlen, destsize, src[i]);
			p = -1;
		} else if (p < dirprefixlen) {
			charcopy(dest, &destlen, destsize, src[i]);
		}
	}

	/* Rewind to just before the last slash copied. */
	if (destlen > maxshortdirslen + 3) {
		do {
			destlen--;
		} while (destlen > 0 && dest[destlen] != '/');
	}

	if (destlen > 3) {
		if (lastslash > 0) {
			char d = dest[destlen - 1];
			/* The last directory component may be
			   truncated, so make it safe. */
			if (d == '.' || d == ' ') {
				dest[destlen - 1] = '_';
			}
		}

		charcopy(dest, &destlen, destsize, '/');
	}

	/* Add a prefix of the original file's name. Its length
	   depends on the number of bytes left after accounting for
	   hash and suffix. */
	used = destlen + 40;
	if (lastdot >= 0) {
		used += len - lastdot - 1;
	}
	slop = maxstorepathlen - used;
	if (slop > 0) {
		Py_ssize_t basenamelen =
		    lastslash >= 0 ? len - lastslash - 2 : len - 1;

		if (basenamelen > slop) {
			basenamelen = slop;
		}
		if (basenamelen > 0) {
			memcopy(dest, &destlen, destsize, &src[lastslash + 1],
			        basenamelen);
		}
	}

	/* Add hash and suffix. */
	for (i = 0; i < 20; i++) {
		hexencode(dest, &destlen, destsize, sha[i]);
	}

	if (lastdot >= 0) {
		memcopy(dest, &destlen, destsize, &src[lastdot],
		        len - lastdot - 1);
	}

	assert(PyBytes_Check(ret));
	Py_SET_SIZE(ret, destlen);

	return ret;
}

/*
 * Avoiding a trip through Python would improve performance by 50%,
 * but we don't encounter enough long names to be worth the code.
 */
static int sha1hash(char hash[20], const char *str, Py_ssize_t len)
{
	static PyObject *shafunc;
	PyObject *shaobj, *hashobj;

	if (shafunc == NULL) {
		PyObject *hashlib = PyImport_ImportModule("hashlib");
		if (hashlib == NULL) {
			PyErr_SetString(PyExc_ImportError,
			                "pathencode failed to find hashlib");
			return -1;
		}
		shafunc = PyObject_GetAttrString(hashlib, "sha1");
		Py_DECREF(hashlib);

		if (shafunc == NULL) {
			PyErr_SetString(PyExc_AttributeError,
			                "module 'hashlib' has no "
			                "attribute 'sha1' in pathencode");
			return -1;
		}
	}

	shaobj = PyObject_CallFunction(shafunc, "y#", str, len);

	if (shaobj == NULL) {
		return -1;
	}

	hashobj = PyObject_CallMethod(shaobj, "digest", "");
	Py_DECREF(shaobj);
	if (hashobj == NULL) {
		return -1;
	}

	if (!PyBytes_Check(hashobj) || PyBytes_GET_SIZE(hashobj) != 20) {
		PyErr_SetString(PyExc_TypeError,
		                "result of digest is not a 20-byte hash");
		Py_DECREF(hashobj);
		return -1;
	}

	memcpy(hash, PyBytes_AS_STRING(hashobj), 20);
	Py_DECREF(hashobj);
	return 0;
}

#define MAXENCODE 4096 * 4

static PyObject *hashencode(const char *src, Py_ssize_t len)
{
	char dired[MAXENCODE];
	char lowered[MAXENCODE];
	char auxed[MAXENCODE];
	Py_ssize_t dirlen, lowerlen, auxlen, baselen;
	char sha[20];

	baselen = (len - 5) * 3;
	if (baselen >= MAXENCODE) {
		PyErr_SetString(PyExc_ValueError, "string too long");
		return NULL;
	}

	dirlen = _encodedir(dired, baselen, src, len);
	if (sha1hash(sha, dired, dirlen - 1) == -1) {
		return NULL;
	}
	lowerlen = _lowerencode(lowered, baselen, dired + 5, dirlen - 5);
	auxlen = auxencode(auxed, baselen, lowered, lowerlen);
	return hashmangle(auxed, auxlen, sha);
}

PyObject *pathencode(PyObject *self, PyObject *args)
{
	Py_ssize_t len, newlen;
	PyObject *pathobj, *newobj;
	char *path;

	if (!PyArg_ParseTuple(args, "O:pathencode", &pathobj)) {
		return NULL;
	}

	if (PyBytes_AsStringAndSize(pathobj, &path, &len) == -1) {
		PyErr_SetString(PyExc_TypeError, "expected a string");
		return NULL;
	}

	if (len > maxstorepathlen) {
		newlen = maxstorepathlen + 2;
	} else {
		newlen = len ? basicencode(NULL, 0, path, len + 1) : 1;
	}

	if (newlen <= maxstorepathlen + 1) {
		if (newlen == len + 1) {
			Py_INCREF(pathobj);
			return pathobj;
		}

		newobj = PyBytes_FromStringAndSize(NULL, newlen);

		if (newobj) {
			assert(PyBytes_Check(newobj));
			Py_SET_SIZE(newobj, Py_SIZE(newobj) - 1);
			basicencode(PyBytes_AS_STRING(newobj), newlen, path,
			            len + 1);
		}
	} else {
		newobj = hashencode(path, len + 1);
	}

	return newobj;
}
