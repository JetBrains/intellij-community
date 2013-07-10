/*
 mpatch.c - efficient binary patching for Mercurial

 This implements a patch algorithm that's O(m + nlog n) where m is the
 size of the output and n is the number of patches.

 Given a list of binary patches, it unpacks each into a hunk list,
 then combines the hunk lists with a treewise recursion to form a
 single hunk list. This hunk list is then applied to the original
 text.

 The text (or binary) fragments are copied directly from their source
 Python objects into a preallocated output string to avoid the
 allocation of intermediate Python objects. Working memory is about 2x
 the total number of hunks.

 Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>

 This software may be used and distributed according to the terms
 of the GNU General Public License, incorporated herein by reference.
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <stdlib.h>
#include <string.h>

#include "util.h"

static char mpatch_doc[] = "Efficient binary patching.";
static PyObject *mpatch_Error;

struct frag {
	int start, end, len;
	const char *data;
};

struct flist {
	struct frag *base, *head, *tail;
};

static struct flist *lalloc(Py_ssize_t size)
{
	struct flist *a = NULL;

	if (size < 1)
		size = 1;

	a = (struct flist *)malloc(sizeof(struct flist));
	if (a) {
		a->base = (struct frag *)malloc(sizeof(struct frag) * size);
		if (a->base) {
			a->head = a->tail = a->base;
			return a;
		}
		free(a);
		a = NULL;
	}
	if (!PyErr_Occurred())
		PyErr_NoMemory();
	return NULL;
}

static void lfree(struct flist *a)
{
	if (a) {
		free(a->base);
		free(a);
	}
}

static Py_ssize_t lsize(struct flist *a)
{
	return a->tail - a->head;
}

/* move hunks in source that are less cut to dest, compensating
   for changes in offset. the last hunk may be split if necessary.
*/
static int gather(struct flist *dest, struct flist *src, int cut, int offset)
{
	struct frag *d = dest->tail, *s = src->head;
	int postend, c, l;

	while (s != src->tail) {
		if (s->start + offset >= cut)
			break; /* we've gone far enough */

		postend = offset + s->start + s->len;
		if (postend <= cut) {
			/* save this hunk */
			offset += s->start + s->len - s->end;
			*d++ = *s++;
		}
		else {
			/* break up this hunk */
			c = cut - offset;
			if (s->end < c)
				c = s->end;
			l = cut - offset - s->start;
			if (s->len < l)
				l = s->len;

			offset += s->start + l - c;

			d->start = s->start;
			d->end = c;
			d->len = l;
			d->data = s->data;
			d++;
			s->start = c;
			s->len = s->len - l;
			s->data = s->data + l;

			break;
		}
	}

	dest->tail = d;
	src->head = s;
	return offset;
}

/* like gather, but with no output list */
static int discard(struct flist *src, int cut, int offset)
{
	struct frag *s = src->head;
	int postend, c, l;

	while (s != src->tail) {
		if (s->start + offset >= cut)
			break;

		postend = offset + s->start + s->len;
		if (postend <= cut) {
			offset += s->start + s->len - s->end;
			s++;
		}
		else {
			c = cut - offset;
			if (s->end < c)
				c = s->end;
			l = cut - offset - s->start;
			if (s->len < l)
				l = s->len;

			offset += s->start + l - c;
			s->start = c;
			s->len = s->len - l;
			s->data = s->data + l;

			break;
		}
	}

	src->head = s;
	return offset;
}

/* combine hunk lists a and b, while adjusting b for offset changes in a/
   this deletes a and b and returns the resultant list. */
static struct flist *combine(struct flist *a, struct flist *b)
{
	struct flist *c = NULL;
	struct frag *bh, *ct;
	int offset = 0, post;

	if (a && b)
		c = lalloc((lsize(a) + lsize(b)) * 2);

	if (c) {

		for (bh = b->head; bh != b->tail; bh++) {
			/* save old hunks */
			offset = gather(c, a, bh->start, offset);

			/* discard replaced hunks */
			post = discard(a, bh->end, offset);

			/* insert new hunk */
			ct = c->tail;
			ct->start = bh->start - offset;
			ct->end = bh->end - post;
			ct->len = bh->len;
			ct->data = bh->data;
			c->tail++;
			offset = post;
		}

		/* hold on to tail from a */
		memcpy(c->tail, a->head, sizeof(struct frag) * lsize(a));
		c->tail += lsize(a);
	}

	lfree(a);
	lfree(b);
	return c;
}

/* decode a binary patch into a hunk list */
static struct flist *decode(const char *bin, Py_ssize_t len)
{
	struct flist *l;
	struct frag *lt;
	const char *data = bin + 12, *end = bin + len;

	/* assume worst case size, we won't have many of these lists */
	l = lalloc(len / 12);
	if (!l)
		return NULL;

	lt = l->tail;

	while (data <= end) {
		lt->start = getbe32(bin);
		lt->end = getbe32(bin + 4);
		lt->len = getbe32(bin + 8);
		if (lt->start > lt->end)
			break; /* sanity check */
		bin = data + lt->len;
		if (bin < data)
			break; /* big data + big (bogus) len can wrap around */
		lt->data = data;
		data = bin + 12;
		lt++;
	}

	if (bin != end) {
		if (!PyErr_Occurred())
			PyErr_SetString(mpatch_Error, "patch cannot be decoded");
		lfree(l);
		return NULL;
	}

	l->tail = lt;
	return l;
}

/* calculate the size of resultant text */
static Py_ssize_t calcsize(Py_ssize_t len, struct flist *l)
{
	Py_ssize_t outlen = 0, last = 0;
	struct frag *f = l->head;

	while (f != l->tail) {
		if (f->start < last || f->end > len) {
			if (!PyErr_Occurred())
				PyErr_SetString(mpatch_Error,
				                "invalid patch");
			return -1;
		}
		outlen += f->start - last;
		last = f->end;
		outlen += f->len;
		f++;
	}

	outlen += len - last;
	return outlen;
}

static int apply(char *buf, const char *orig, Py_ssize_t len, struct flist *l)
{
	struct frag *f = l->head;
	int last = 0;
	char *p = buf;

	while (f != l->tail) {
		if (f->start < last || f->end > len) {
			if (!PyErr_Occurred())
				PyErr_SetString(mpatch_Error,
				                "invalid patch");
			return 0;
		}
		memcpy(p, orig + last, f->start - last);
		p += f->start - last;
		memcpy(p, f->data, f->len);
		last = f->end;
		p += f->len;
		f++;
	}
	memcpy(p, orig + last, len - last);
	return 1;
}

/* recursively generate a patch of all bins between start and end */
static struct flist *fold(PyObject *bins, Py_ssize_t start, Py_ssize_t end)
{
	Py_ssize_t len, blen;
	const char *buffer;

	if (start + 1 == end) {
		/* trivial case, output a decoded list */
		PyObject *tmp = PyList_GetItem(bins, start);
		if (!tmp)
			return NULL;
		if (PyObject_AsCharBuffer(tmp, &buffer, &blen))
			return NULL;
		return decode(buffer, blen);
	}

	/* divide and conquer, memory management is elsewhere */
	len = (end - start) / 2;
	return combine(fold(bins, start, start + len),
		       fold(bins, start + len, end));
}

static PyObject *
patches(PyObject *self, PyObject *args)
{
	PyObject *text, *bins, *result;
	struct flist *patch;
	const char *in;
	char *out;
	Py_ssize_t len, outlen, inlen;

	if (!PyArg_ParseTuple(args, "OO:mpatch", &text, &bins))
		return NULL;

	len = PyList_Size(bins);
	if (!len) {
		/* nothing to do */
		Py_INCREF(text);
		return text;
	}

	if (PyObject_AsCharBuffer(text, &in, &inlen))
		return NULL;

	patch = fold(bins, 0, len);
	if (!patch)
		return NULL;

	outlen = calcsize(inlen, patch);
	if (outlen < 0) {
		result = NULL;
		goto cleanup;
	}
	result = PyBytes_FromStringAndSize(NULL, outlen);
	if (!result) {
		result = NULL;
		goto cleanup;
	}
	out = PyBytes_AsString(result);
	if (!apply(out, in, inlen, patch)) {
		Py_DECREF(result);
		result = NULL;
	}
cleanup:
	lfree(patch);
	return result;
}

/* calculate size of a patched file directly */
static PyObject *
patchedsize(PyObject *self, PyObject *args)
{
	long orig, start, end, len, outlen = 0, last = 0;
	Py_ssize_t patchlen;
	char *bin, *binend, *data;

	if (!PyArg_ParseTuple(args, "ls#", &orig, &bin, &patchlen))
		return NULL;

	binend = bin + patchlen;
	data = bin + 12;

	while (data <= binend) {
		start = getbe32(bin);
		end = getbe32(bin + 4);
		len = getbe32(bin + 8);
		if (start > end)
			break; /* sanity check */
		bin = data + len;
		if (bin < data)
			break; /* big data + big (bogus) len can wrap around */
		data = bin + 12;
		outlen += start - last;
		last = end;
		outlen += len;
	}

	if (bin != binend) {
		if (!PyErr_Occurred())
			PyErr_SetString(mpatch_Error, "patch cannot be decoded");
		return NULL;
	}

	outlen += orig - last;
	return Py_BuildValue("l", outlen);
}

static PyMethodDef methods[] = {
	{"patches", patches, METH_VARARGS, "apply a series of patches\n"},
	{"patchedsize", patchedsize, METH_VARARGS, "calculed patched size\n"},
	{NULL, NULL}
};

#ifdef IS_PY3K
static struct PyModuleDef mpatch_module = {
	PyModuleDef_HEAD_INIT,
	"mpatch",
	mpatch_doc,
	-1,
	methods
};

PyMODINIT_FUNC PyInit_mpatch(void)
{
	PyObject *m;

	m = PyModule_Create(&mpatch_module);
	if (m == NULL)
		return NULL;

	mpatch_Error = PyErr_NewException("mpatch.mpatchError", NULL, NULL);
	Py_INCREF(mpatch_Error);
	PyModule_AddObject(m, "mpatchError", mpatch_Error);

	return m;
}
#else
PyMODINIT_FUNC
initmpatch(void)
{
	Py_InitModule3("mpatch", methods, mpatch_doc);
	mpatch_Error = PyErr_NewException("mpatch.mpatchError", NULL, NULL);
}
#endif
