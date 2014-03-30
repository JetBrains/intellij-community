/*
 bdiff.c - efficient binary diff extension for Mercurial

 Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.

 Based roughly on Python difflib
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>

#include "util.h"

struct line {
	int hash, n, e;
	Py_ssize_t len;
	const char *l;
};

struct pos {
	int pos, len;
};

struct hunk;
struct hunk {
	int a1, a2, b1, b2;
	struct hunk *next;
};

static int splitlines(const char *a, Py_ssize_t len, struct line **lr)
{
	unsigned hash;
	int i;
	const char *p, *b = a;
	const char * const plast = a + len - 1;
	struct line *l;

	/* count the lines */
	i = 1; /* extra line for sentinel */
	for (p = a; p < a + len; p++)
		if (*p == '\n' || p == plast)
			i++;

	*lr = l = (struct line *)malloc(sizeof(struct line) * i);
	if (!l)
		return -1;

	/* build the line array and calculate hashes */
	hash = 0;
	for (p = a; p < a + len; p++) {
		/* Leonid Yuriev's hash */
		hash = (hash * 1664525) + (unsigned char)*p + 1013904223;

		if (*p == '\n' || p == plast) {
			l->hash = hash;
			hash = 0;
			l->len = p - b + 1;
			l->l = b;
			l->n = INT_MAX;
			l++;
			b = p + 1;
		}
	}

	/* set up a sentinel */
	l->hash = 0;
	l->len = 0;
	l->l = a + len;
	return i - 1;
}

static inline int cmp(struct line *a, struct line *b)
{
	return a->hash != b->hash || a->len != b->len || memcmp(a->l, b->l, a->len);
}

static int equatelines(struct line *a, int an, struct line *b, int bn)
{
	int i, j, buckets = 1, t, scale;
	struct pos *h = NULL;

	/* build a hash table of the next highest power of 2 */
	while (buckets < bn + 1)
		buckets *= 2;

	/* try to allocate a large hash table to avoid collisions */
	for (scale = 4; scale; scale /= 2) {
		h = (struct pos *)malloc(scale * buckets * sizeof(struct pos));
		if (h)
			break;
	}

	if (!h)
		return 0;

	buckets = buckets * scale - 1;

	/* clear the hash table */
	for (i = 0; i <= buckets; i++) {
		h[i].pos = INT_MAX;
		h[i].len = 0;
	}

	/* add lines to the hash table chains */
	for (i = bn - 1; i >= 0; i--) {
		/* find the equivalence class */
		for (j = b[i].hash & buckets; h[j].pos != INT_MAX;
		     j = (j + 1) & buckets)
			if (!cmp(b + i, b + h[j].pos))
				break;

		/* add to the head of the equivalence class */
		b[i].n = h[j].pos;
		b[i].e = j;
		h[j].pos = i;
		h[j].len++; /* keep track of popularity */
	}

	/* compute popularity threshold */
	t = (bn >= 31000) ? bn / 1000 : 1000000 / (bn + 1);

	/* match items in a to their equivalence class in b */
	for (i = 0; i < an; i++) {
		/* find the equivalence class */
		for (j = a[i].hash & buckets; h[j].pos != INT_MAX;
		     j = (j + 1) & buckets)
			if (!cmp(a + i, b + h[j].pos))
				break;

		a[i].e = j; /* use equivalence class for quick compare */
		if (h[j].len <= t)
			a[i].n = h[j].pos; /* point to head of match list */
		else
			a[i].n = INT_MAX; /* too popular */
	}

	/* discard hash tables */
	free(h);
	return 1;
}

static int longest_match(struct line *a, struct line *b, struct pos *pos,
			 int a1, int a2, int b1, int b2, int *omi, int *omj)
{
	int mi = a1, mj = b1, mk = 0, mb = 0, i, j, k;

	for (i = a1; i < a2; i++) {
		/* skip things before the current block */
		for (j = a[i].n; j < b1; j = b[j].n)
			;

		/* loop through all lines match a[i] in b */
		for (; j < b2; j = b[j].n) {
			/* does this extend an earlier match? */
			if (i > a1 && j > b1 && pos[j - 1].pos == i - 1)
				k = pos[j - 1].len + 1;
			else
				k = 1;
			pos[j].pos = i;
			pos[j].len = k;

			/* best match so far? */
			if (k > mk) {
				mi = i;
				mj = j;
				mk = k;
			}
		}
	}

	if (mk) {
		mi = mi - mk + 1;
		mj = mj - mk + 1;
	}

	/* expand match to include neighboring popular lines */
	while (mi - mb > a1 && mj - mb > b1 &&
	       a[mi - mb - 1].e == b[mj - mb - 1].e)
		mb++;
	while (mi + mk < a2 && mj + mk < b2 &&
	       a[mi + mk].e == b[mj + mk].e)
		mk++;

	*omi = mi - mb;
	*omj = mj - mb;

	return mk + mb;
}

static struct hunk *recurse(struct line *a, struct line *b, struct pos *pos,
			    int a1, int a2, int b1, int b2, struct hunk *l)
{
	int i, j, k;

	while (1) {
		/* find the longest match in this chunk */
		k = longest_match(a, b, pos, a1, a2, b1, b2, &i, &j);
		if (!k)
			return l;

		/* and recurse on the remaining chunks on either side */
		l = recurse(a, b, pos, a1, i, b1, j, l);
		if (!l)
			return NULL;

		l->next = (struct hunk *)malloc(sizeof(struct hunk));
		if (!l->next)
			return NULL;

		l = l->next;
		l->a1 = i;
		l->a2 = i + k;
		l->b1 = j;
		l->b2 = j + k;
		l->next = NULL;

		/* tail-recursion didn't happen, so do equivalent iteration */
		a1 = i + k;
		b1 = j + k;
	}
}

static int diff(struct line *a, int an, struct line *b, int bn,
		 struct hunk *base)
{
	struct hunk *curr;
	struct pos *pos;
	int t, count = 0;

	/* allocate and fill arrays */
	t = equatelines(a, an, b, bn);
	pos = (struct pos *)calloc(bn ? bn : 1, sizeof(struct pos));

	if (pos && t) {
		/* generate the matching block list */

		curr = recurse(a, b, pos, 0, an, 0, bn, base);
		if (!curr)
			return -1;

		/* sentinel end hunk */
		curr->next = (struct hunk *)malloc(sizeof(struct hunk));
		if (!curr->next)
			return -1;
		curr = curr->next;
		curr->a1 = curr->a2 = an;
		curr->b1 = curr->b2 = bn;
		curr->next = NULL;
	}

	free(pos);

	/* normalize the hunk list, try to push each hunk towards the end */
	for (curr = base->next; curr; curr = curr->next) {
		struct hunk *next = curr->next;
		int shift = 0;

		if (!next)
			break;

		if (curr->a2 == next->a1)
			while (curr->a2 + shift < an && curr->b2 + shift < bn
			       && !cmp(a + curr->a2 + shift,
				       b + curr->b2 + shift))
				shift++;
		else if (curr->b2 == next->b1)
			while (curr->b2 + shift < bn && curr->a2 + shift < an
			       && !cmp(b + curr->b2 + shift,
				       a + curr->a2 + shift))
				shift++;
		if (!shift)
			continue;
		curr->b2 += shift;
		next->b1 += shift;
		curr->a2 += shift;
		next->a1 += shift;
	}

	for (curr = base->next; curr; curr = curr->next)
		count++;
	return count;
}

static void freehunks(struct hunk *l)
{
	struct hunk *n;
	for (; l; l = n) {
		n = l->next;
		free(l);
	}
}

static PyObject *blocks(PyObject *self, PyObject *args)
{
	PyObject *sa, *sb, *rl = NULL, *m;
	struct line *a, *b;
	struct hunk l, *h;
	int an, bn, count, pos = 0;

	if (!PyArg_ParseTuple(args, "SS:bdiff", &sa, &sb))
		return NULL;

	an = splitlines(PyBytes_AsString(sa), PyBytes_Size(sa), &a);
	bn = splitlines(PyBytes_AsString(sb), PyBytes_Size(sb), &b);

	if (!a || !b)
		goto nomem;

	l.next = NULL;
	count = diff(a, an, b, bn, &l);
	if (count < 0)
		goto nomem;

	rl = PyList_New(count);
	if (!rl)
		goto nomem;

	for (h = l.next; h; h = h->next) {
		m = Py_BuildValue("iiii", h->a1, h->a2, h->b1, h->b2);
		PyList_SetItem(rl, pos, m);
		pos++;
	}

nomem:
	free(a);
	free(b);
	freehunks(l.next);
	return rl ? rl : PyErr_NoMemory();
}

static PyObject *bdiff(PyObject *self, PyObject *args)
{
	char *sa, *sb, *rb;
	PyObject *result = NULL;
	struct line *al, *bl;
	struct hunk l, *h;
	int an, bn, count;
	Py_ssize_t len = 0, la, lb;
	PyThreadState *_save;

	if (!PyArg_ParseTuple(args, "s#s#:bdiff", &sa, &la, &sb, &lb))
		return NULL;

	if (la > UINT_MAX || lb > UINT_MAX) {
		PyErr_SetString(PyExc_ValueError, "bdiff inputs too large");
		return NULL;
	}

	_save = PyEval_SaveThread();
	an = splitlines(sa, la, &al);
	bn = splitlines(sb, lb, &bl);
	if (!al || !bl)
		goto nomem;

	l.next = NULL;
	count = diff(al, an, bl, bn, &l);
	if (count < 0)
		goto nomem;

	/* calculate length of output */
	la = lb = 0;
	for (h = l.next; h; h = h->next) {
		if (h->a1 != la || h->b1 != lb)
			len += 12 + bl[h->b1].l - bl[lb].l;
		la = h->a2;
		lb = h->b2;
	}
	PyEval_RestoreThread(_save);
	_save = NULL;

	result = PyBytes_FromStringAndSize(NULL, len);

	if (!result)
		goto nomem;

	/* build binary patch */
	rb = PyBytes_AsString(result);
	la = lb = 0;

	for (h = l.next; h; h = h->next) {
		if (h->a1 != la || h->b1 != lb) {
			len = bl[h->b1].l - bl[lb].l;
			putbe32((uint32_t)(al[la].l - al->l), rb);
			putbe32((uint32_t)(al[h->a1].l - al->l), rb + 4);
			putbe32((uint32_t)len, rb + 8);
			memcpy(rb + 12, bl[lb].l, len);
			rb += 12 + len;
		}
		la = h->a2;
		lb = h->b2;
	}

nomem:
	if (_save)
		PyEval_RestoreThread(_save);
	free(al);
	free(bl);
	freehunks(l.next);
	return result ? result : PyErr_NoMemory();
}

/*
 * If allws != 0, remove all whitespace (' ', \t and \r). Otherwise,
 * reduce whitespace sequences to a single space and trim remaining whitespace
 * from end of lines.
 */
static PyObject *fixws(PyObject *self, PyObject *args)
{
	PyObject *s, *result = NULL;
	char allws, c;
	const char *r;
	Py_ssize_t i, rlen, wlen = 0;
	char *w;

	if (!PyArg_ParseTuple(args, "Sb:fixws", &s, &allws))
		return NULL;
	r = PyBytes_AsString(s);
	rlen = PyBytes_Size(s);

	w = (char *)malloc(rlen ? rlen : 1);
	if (!w)
		goto nomem;

	for (i = 0; i != rlen; i++) {
		c = r[i];
		if (c == ' ' || c == '\t' || c == '\r') {
			if (!allws && (wlen == 0 || w[wlen - 1] != ' '))
				w[wlen++] = ' ';
		} else if (c == '\n' && !allws
			  && wlen > 0 && w[wlen - 1] == ' ') {
			w[wlen - 1] = '\n';
		} else {
			w[wlen++] = c;
		}
	}

	result = PyBytes_FromStringAndSize(w, wlen);

nomem:
	free(w);
	return result ? result : PyErr_NoMemory();
}


static char mdiff_doc[] = "Efficient binary diff.";

static PyMethodDef methods[] = {
	{"bdiff", bdiff, METH_VARARGS, "calculate a binary diff\n"},
	{"blocks", blocks, METH_VARARGS, "find a list of matching lines\n"},
	{"fixws", fixws, METH_VARARGS, "normalize diff whitespaces\n"},
	{NULL, NULL}
};

#ifdef IS_PY3K
static struct PyModuleDef bdiff_module = {
	PyModuleDef_HEAD_INIT,
	"bdiff",
	mdiff_doc,
	-1,
	methods
};

PyMODINIT_FUNC PyInit_bdiff(void)
{
	return PyModule_Create(&bdiff_module);
}
#else
PyMODINIT_FUNC initbdiff(void)
{
	Py_InitModule3("bdiff", methods, mdiff_doc);
}
#endif

