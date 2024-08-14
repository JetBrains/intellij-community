/*
 * manifest.c - manifest type that does on-demand parsing.
 *
 * Copyright 2015, Google Inc.
 *
 * This software may be used and distributed according to the terms of
 * the GNU General Public License, incorporated herein by reference.
 */
#include <Python.h>

#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "charencode.h"
#include "util.h"

#define DEFAULT_LINES 100000

typedef struct {
	char *start;
	Py_ssize_t len; /* length of line including terminal newline */
	char hash_suffix;
	bool from_malloc;
	bool deleted;
} line;

typedef struct {
	PyObject_HEAD
	PyObject *pydata;
	Py_ssize_t nodelen;
	line *lines;
	int numlines; /* number of line entries */
	int livelines; /* number of non-deleted lines */
	int maxlines; /* allocated number of lines */
	bool dirty;
} lazymanifest;

#define MANIFEST_OOM -1
#define MANIFEST_NOT_SORTED -2
#define MANIFEST_MALFORMED -3
#define MANIFEST_BOGUS_FILENAME -4
#define MANIFEST_TOO_SHORT_LINE -5

/* get the length of the path for a line */
static Py_ssize_t pathlen(line *l)
{
	const char *end = memchr(l->start, '\0', l->len);
	return (end) ? (Py_ssize_t)(end - l->start) : l->len;
}

/* get the node value of a single line */
static PyObject *nodeof(Py_ssize_t nodelen, line *l, char *flag)
{
	char *s = l->start;
	Py_ssize_t llen = pathlen(l);
	Py_ssize_t hlen = l->len - llen - 2;
	PyObject *hash;
	if (llen + 1 + 40 + 1 > l->len) { /* path '\0' hash '\n' */
		PyErr_SetString(PyExc_ValueError, "manifest line too short");
		return NULL;
	}
	/* Detect flags after the hash first. */
	switch (s[llen + hlen]) {
	case 'l':
	case 't':
	case 'x':
		*flag = s[llen + hlen];
		--hlen;
		break;
	default:
		*flag = '\0';
		break;
	}

	if (hlen != 2 * nodelen) {
		PyErr_SetString(PyExc_ValueError, "invalid node length in manifest");
		return NULL;
	}
	hash = unhexlify(s + llen + 1, nodelen * 2);
	if (!hash) {
		return NULL;
	}
	if (l->hash_suffix != '\0') {
		char newhash[33];
		memcpy(newhash, PyBytes_AsString(hash), nodelen);
		Py_DECREF(hash);
		newhash[nodelen] = l->hash_suffix;
		hash = PyBytes_FromStringAndSize(newhash, nodelen + 1);
	}
	return hash;
}

/* get the node hash and flags of a line as a tuple */
static PyObject *hashflags(Py_ssize_t nodelen, line *l)
{
	char flag;
	PyObject *hash = nodeof(nodelen, l, &flag);
	PyObject *flags;
	PyObject *tup;

	if (!hash)
		return NULL;
	flags = PyBytes_FromStringAndSize(&flag, flag ? 1 : 0);
	if (!flags) {
		Py_DECREF(hash);
		return NULL;
	}
	tup = PyTuple_Pack(2, hash, flags);
	Py_DECREF(flags);
	Py_DECREF(hash);
	return tup;
}

/* if we're about to run out of space in the line index, add more */
static bool realloc_if_full(lazymanifest *self)
{
	if (self->numlines == self->maxlines) {
		self->maxlines *= 2;
		self->lines = realloc(self->lines, self->maxlines * sizeof(line));
	}
	return !!self->lines;
}

/*
 * Find the line boundaries in the manifest that 'data' points to and store
 * information about each line in 'self'.
 */
static int find_lines(lazymanifest *self, char *data, Py_ssize_t len)
{
	char *prev = NULL;
	while (len > 0) {
		line *l;
		char *next;
		if (*data == '\0') {
			/* It's implausible there's no filename, don't
			 * even bother looking for the newline. */
			return MANIFEST_BOGUS_FILENAME;
		}
		next = memchr(data, '\n', len);
		if (!next) {
			return MANIFEST_MALFORMED;
		}
		if ((next - data) < 42) {
			/* We should have at least 42 bytes in a line:
			   1 byte filename
			   1 NUL
			   40 bytes of hash
			   so we can give up here.
			*/
			return MANIFEST_TOO_SHORT_LINE;
		}
		next++; /* advance past newline */
		if (prev && strcmp(prev, data) > -1) {
			/* This data isn't sorted, so we have to abort. */
			return MANIFEST_NOT_SORTED;
		}
		if (!realloc_if_full(self)) {
			return MANIFEST_OOM; /* no memory */
		}
		l = self->lines + ((self->numlines)++);
		l->start = data;
		l->len = next - data;
		l->hash_suffix = '\0';
		l->from_malloc = false;
		l->deleted = false;
		len = len - l->len;
		prev = data;
		data = next;
	}
	self->livelines = self->numlines;
	return 0;
}

static void lazymanifest_init_early(lazymanifest *self)
{
	self->pydata = NULL;
	self->lines = NULL;
	self->numlines = 0;
	self->maxlines = 0;
}

static int lazymanifest_init(lazymanifest *self, PyObject *args)
{
	char *data;
	Py_ssize_t nodelen, len;
	int err, ret;
	PyObject *pydata;

	lazymanifest_init_early(self);
	if (!PyArg_ParseTuple(args, "nS", &nodelen, &pydata)) {
		return -1;
	}
	if (nodelen != 20 && nodelen != 32) {
		/* See fixed buffer in nodeof */
		PyErr_Format(PyExc_ValueError, "Unsupported node length");
		return -1;
	}
	self->nodelen = nodelen;
	self->dirty = false;

	err = PyBytes_AsStringAndSize(pydata, &data, &len);
	if (err == -1)
		return -1;
	self->pydata = pydata;
	Py_INCREF(self->pydata);
	Py_BEGIN_ALLOW_THREADS
	self->lines = malloc(DEFAULT_LINES * sizeof(line));
	self->maxlines = DEFAULT_LINES;
	self->numlines = 0;
	if (!self->lines)
		ret = MANIFEST_OOM;
	else
		ret = find_lines(self, data, len);
	Py_END_ALLOW_THREADS
	switch (ret) {
	case 0:
		break;
	case MANIFEST_OOM:
		PyErr_NoMemory();
		break;
	case MANIFEST_NOT_SORTED:
		PyErr_Format(PyExc_ValueError,
			     "Manifest lines not in sorted order.");
		break;
	case MANIFEST_MALFORMED:
		PyErr_Format(PyExc_ValueError,
			     "Manifest did not end in a newline.");
		break;
	case MANIFEST_BOGUS_FILENAME:
		PyErr_Format(
			PyExc_ValueError,
			"Manifest had an entry with a zero-length filename.");
		break;
	case MANIFEST_TOO_SHORT_LINE:
		PyErr_Format(
			PyExc_ValueError,
			"Manifest had implausibly-short line.");
		break;
	default:
		PyErr_Format(PyExc_ValueError,
			     "Unknown problem parsing manifest.");
	}
	return ret == 0 ? 0 : -1;
}

static void lazymanifest_dealloc(lazymanifest *self)
{
	/* free any extra lines we had to allocate */
	int i;
	for (i = 0; self->lines && (i < self->numlines); i++) {
		if (self->lines[i].from_malloc) {
			free(self->lines[i].start);
		}
	}
	free(self->lines);
	self->lines = NULL;
	if (self->pydata) {
		Py_DECREF(self->pydata);
		self->pydata = NULL;
	}
	PyObject_Del(self);
}

/* iteration support */

typedef struct {
	PyObject_HEAD lazymanifest *m;
	Py_ssize_t pos;
} lmIter;

static void lmiter_dealloc(PyObject *o)
{
	lmIter *self = (lmIter *)o;
	Py_DECREF(self->m);
	PyObject_Del(self);
}

static line *lmiter_nextline(lmIter *self)
{
	do {
		self->pos++;
		if (self->pos >= self->m->numlines) {
			return NULL;
		}
		/* skip over deleted manifest entries */
	} while (self->m->lines[self->pos].deleted);
	return self->m->lines + self->pos;
}

static PyObject *lmiter_iterentriesnext(PyObject *o)
{
	lmIter *self = (lmIter *)o;
	Py_ssize_t pl;
	line *l;
	char flag;
	PyObject *ret = NULL, *path = NULL, *hash = NULL, *flags = NULL;
	l = lmiter_nextline(self);
	if (!l) {
		goto done;
	}
	pl = pathlen(l);
	path = PyBytes_FromStringAndSize(l->start, pl);
	hash = nodeof(self->m->nodelen, l, &flag);
	if (!path || !hash) {
		goto done;
	}
	flags = PyBytes_FromStringAndSize(&flag, flag ? 1 : 0);
	if (!flags) {
		goto done;
	}
	ret = PyTuple_Pack(3, path, hash, flags);
done:
	Py_XDECREF(path);
	Py_XDECREF(hash);
	Py_XDECREF(flags);
	return ret;
}

#define LAZYMANIFESTENTRIESITERATOR_TPFLAGS Py_TPFLAGS_DEFAULT

static PyTypeObject lazymanifestEntriesIterator = {
	PyVarObject_HEAD_INIT(NULL, 0) /* header */
	"parsers.lazymanifest.entriesiterator", /*tp_name */
	sizeof(lmIter),                  /*tp_basicsize */
	0,                               /*tp_itemsize */
	lmiter_dealloc,                  /*tp_dealloc */
	0,                               /*tp_print */
	0,                               /*tp_getattr */
	0,                               /*tp_setattr */
	0,                               /*tp_compare */
	0,                               /*tp_repr */
	0,                               /*tp_as_number */
	0,                               /*tp_as_sequence */
	0,                               /*tp_as_mapping */
	0,                               /*tp_hash */
	0,                               /*tp_call */
	0,                               /*tp_str */
	0,                               /*tp_getattro */
	0,                               /*tp_setattro */
	0,                               /*tp_as_buffer */
	LAZYMANIFESTENTRIESITERATOR_TPFLAGS, /* tp_flags */
	"Iterator for 3-tuples in a lazymanifest.",  /* tp_doc */
	0,                               /* tp_traverse */
	0,                               /* tp_clear */
	0,                               /* tp_richcompare */
	0,                               /* tp_weaklistoffset */
	PyObject_SelfIter,               /* tp_iter: __iter__() method */
	lmiter_iterentriesnext,          /* tp_iternext: next() method */
};

static PyObject *lmiter_iterkeysnext(PyObject *o)
{
	Py_ssize_t pl;
	line *l = lmiter_nextline((lmIter *)o);
	if (!l) {
		return NULL;
	}
	pl = pathlen(l);
	return PyBytes_FromStringAndSize(l->start, pl);
}

#define LAZYMANIFESTKEYSITERATOR_TPFLAGS Py_TPFLAGS_DEFAULT

static PyTypeObject lazymanifestKeysIterator = {
	PyVarObject_HEAD_INIT(NULL, 0) /* header */
	"parsers.lazymanifest.keysiterator", /*tp_name */
	sizeof(lmIter),                  /*tp_basicsize */
	0,                               /*tp_itemsize */
	lmiter_dealloc,                  /*tp_dealloc */
	0,                               /*tp_print */
	0,                               /*tp_getattr */
	0,                               /*tp_setattr */
	0,                               /*tp_compare */
	0,                               /*tp_repr */
	0,                               /*tp_as_number */
	0,                               /*tp_as_sequence */
	0,                               /*tp_as_mapping */
	0,                               /*tp_hash */
	0,                               /*tp_call */
	0,                               /*tp_str */
	0,                               /*tp_getattro */
	0,                               /*tp_setattro */
	0,                               /*tp_as_buffer */
	LAZYMANIFESTKEYSITERATOR_TPFLAGS, /* tp_flags */
	"Keys iterator for a lazymanifest.",  /* tp_doc */
	0,                               /* tp_traverse */
	0,                               /* tp_clear */
	0,                               /* tp_richcompare */
	0,                               /* tp_weaklistoffset */
	PyObject_SelfIter,               /* tp_iter: __iter__() method */
	lmiter_iterkeysnext,             /* tp_iternext: next() method */
};

static lazymanifest *lazymanifest_copy(lazymanifest *self);

static PyObject *lazymanifest_getentriesiter(lazymanifest *self)
{
	lmIter *i = NULL;
	lazymanifest *t = lazymanifest_copy(self);
	if (!t) {
		PyErr_NoMemory();
		return NULL;
	}
	i = PyObject_New(lmIter, &lazymanifestEntriesIterator);
	if (i) {
		i->m = t;
		i->pos = -1;
	} else {
		Py_DECREF(t);
		PyErr_NoMemory();
	}
	return (PyObject *)i;
}

static PyObject *lazymanifest_getkeysiter(lazymanifest *self)
{
	lmIter *i = NULL;
	lazymanifest *t = lazymanifest_copy(self);
	if (!t) {
		PyErr_NoMemory();
		return NULL;
	}
	i = PyObject_New(lmIter, &lazymanifestKeysIterator);
	if (i) {
		i->m = t;
		i->pos = -1;
	} else {
		Py_DECREF(t);
		PyErr_NoMemory();
	}
	return (PyObject *)i;
}

/* __getitem__ and __setitem__ support */

static Py_ssize_t lazymanifest_size(lazymanifest *self)
{
	return self->livelines;
}

static int linecmp(const void *left, const void *right)
{
	return strcmp(((const line *)left)->start,
		      ((const line *)right)->start);
}

static PyObject *lazymanifest_getitem(lazymanifest *self, PyObject *key)
{
	line needle;
	line *hit;
	if (!PyBytes_Check(key)) {
		PyErr_Format(PyExc_TypeError,
			     "getitem: manifest keys must be a string.");
		return NULL;
	}
	needle.start = PyBytes_AsString(key);
	hit = bsearch(&needle, self->lines, self->numlines, sizeof(line),
		      &linecmp);
	if (!hit || hit->deleted) {
		PyErr_Format(PyExc_KeyError, "No such manifest entry.");
		return NULL;
	}
	return hashflags(self->nodelen, hit);
}

static int lazymanifest_delitem(lazymanifest *self, PyObject *key)
{
	line needle;
	line *hit;
	if (!PyBytes_Check(key)) {
		PyErr_Format(PyExc_TypeError,
			     "delitem: manifest keys must be a string.");
		return -1;
	}
	needle.start = PyBytes_AsString(key);
	hit = bsearch(&needle, self->lines, self->numlines, sizeof(line),
		      &linecmp);
	if (!hit || hit->deleted) {
		PyErr_Format(PyExc_KeyError,
			     "Tried to delete nonexistent manifest entry.");
		return -1;
	}
	self->dirty = true;
	hit->deleted = true;
	self->livelines--;
	return 0;
}

/* Do a binary search for the insertion point for new, creating the
 * new entry if needed. */
static int internalsetitem(lazymanifest *self, line *new)
{
	int start = 0, end = self->numlines;
	while (start < end) {
		int pos = start + (end - start) / 2;
		int c = linecmp(new, self->lines + pos);
		if (c < 0)
			end = pos;
		else if (c > 0)
			start = pos + 1;
		else {
			if (self->lines[pos].deleted)
				self->livelines++;
			if (self->lines[pos].from_malloc)
				free(self->lines[pos].start);
			start = pos;
			goto finish;
		}
	}
	/* being here means we need to do an insert */
	if (!realloc_if_full(self)) {
		PyErr_NoMemory();
		return -1;
	}
	memmove(self->lines + start + 1, self->lines + start,
		(self->numlines - start) * sizeof(line));
	self->numlines++;
	self->livelines++;
finish:
	self->lines[start] = *new;
	self->dirty = true;
	return 0;
}

static int lazymanifest_setitem(
	lazymanifest *self, PyObject *key, PyObject *value)
{
	char *path;
	Py_ssize_t plen;
	PyObject *pyhash;
	Py_ssize_t hlen;
	char *hash;
	PyObject *pyflags;
	char *flags;
	Py_ssize_t flen;
	Py_ssize_t dlen;
	char *dest;
	int i;
	line new;
	if (!PyBytes_Check(key)) {
		PyErr_Format(PyExc_TypeError,
			     "setitem: manifest keys must be a string.");
		return -1;
	}
	if (!value) {
		return lazymanifest_delitem(self, key);
	}
	if (!PyTuple_Check(value) || PyTuple_Size(value) != 2) {
		PyErr_Format(PyExc_TypeError,
			     "Manifest values must be a tuple of (node, flags).");
		return -1;
	}
	if (PyBytes_AsStringAndSize(key, &path, &plen) == -1) {
		return -1;
	}

	pyhash = PyTuple_GetItem(value, 0);
	if (!PyBytes_Check(pyhash)) {
		PyErr_Format(PyExc_TypeError,
			     "node must be a %zi bytes string", self->nodelen);
		return -1;
	}
	hlen = PyBytes_Size(pyhash);
	if (hlen != self->nodelen) {
		PyErr_Format(PyExc_TypeError,
			     "node must be a %zi bytes string", self->nodelen);
		return -1;
	}
	hash = PyBytes_AsString(pyhash);

	pyflags = PyTuple_GetItem(value, 1);
	if (!PyBytes_Check(pyflags) || PyBytes_Size(pyflags) > 1) {
		PyErr_Format(PyExc_TypeError,
			     "flags must a 0 or 1 bytes string");
		return -1;
	}
	if (PyBytes_AsStringAndSize(pyflags, &flags, &flen) == -1) {
		return -1;
	}
	if (flen == 1) {
		switch (*flags) {
		case 'l':
		case 't':
		case 'x':
			break;
		default:
			PyErr_Format(PyExc_TypeError, "invalid manifest flag");
			return -1;
		}
	}
	/* one null byte and one newline */
	dlen = plen + hlen * 2 + 1 + flen + 1;
	dest = malloc(dlen);
	if (!dest) {
		PyErr_NoMemory();
		return -1;
	}
	memcpy(dest, path, plen + 1);
	for (i = 0; i < hlen; i++) {
		/* Cast to unsigned, so it will not get sign-extended when promoted
		 * to int (as is done when passing to a variadic function)
		 */
		sprintf(dest + plen + 1 + (i * 2), "%02x", (unsigned char)hash[i]);
	}
	memcpy(dest + plen + 2 * hlen + 1, flags, flen);
	dest[plen + 2 * hlen + 1 + flen] = '\n';
	new.start = dest;
	new.len = dlen;
	new.hash_suffix = '\0';
	if (hlen > 20) {
		new.hash_suffix = hash[20];
	}
	new.from_malloc = true;     /* is `start` a pointer we allocated? */
	new.deleted = false;        /* is this entry deleted? */
	if (internalsetitem(self, &new)) {
		return -1;
	}
	return 0;
}

static PyMappingMethods lazymanifest_mapping_methods = {
	(lenfunc)lazymanifest_size,             /* mp_length */
	(binaryfunc)lazymanifest_getitem,       /* mp_subscript */
	(objobjargproc)lazymanifest_setitem,    /* mp_ass_subscript */
};

/* sequence methods (important or __contains__ builds an iterator) */

static int lazymanifest_contains(lazymanifest *self, PyObject *key)
{
	line needle;
	line *hit;
	if (!PyBytes_Check(key)) {
		/* Our keys are always strings, so if the contains
		 * check is for a non-string, just return false. */
		return 0;
	}
	needle.start = PyBytes_AsString(key);
	hit = bsearch(&needle, self->lines, self->numlines, sizeof(line),
		      &linecmp);
	if (!hit || hit->deleted) {
		return 0;
	}
	return 1;
}

static PySequenceMethods lazymanifest_seq_meths = {
	(lenfunc)lazymanifest_size, /* sq_length */
	0, /* sq_concat */
	0, /* sq_repeat */
	0, /* sq_item */
	0, /* sq_slice */
	0, /* sq_ass_item */
	0, /* sq_ass_slice */
	(objobjproc)lazymanifest_contains, /* sq_contains */
	0, /* sq_inplace_concat */
	0, /* sq_inplace_repeat */
};


/* Other methods (copy, diff, etc) */
static PyTypeObject lazymanifestType;

/* If the manifest has changes, build the new manifest text and reindex it. */
static int compact(lazymanifest *self)
{
	int i;
	ssize_t need = 0;
	char *data;
	line *src, *dst;
	PyObject *pydata;
	if (!self->dirty)
		return 0;
	for (i = 0; i < self->numlines; i++) {
		if (!self->lines[i].deleted) {
			need += self->lines[i].len;
		}
	}
	pydata = PyBytes_FromStringAndSize(NULL, need);
	if (!pydata)
		return -1;
	data = PyBytes_AsString(pydata);
	if (!data) {
		return -1;
	}
	src = self->lines;
	dst = self->lines;
	for (i = 0; i < self->numlines; i++, src++) {
		char *tofree = NULL;
		if (src->from_malloc) {
			tofree = src->start;
		}
		if (!src->deleted) {
			memcpy(data, src->start, src->len);
			*dst = *src;
			dst->start = data;
			dst->from_malloc = false;
			data += dst->len;
			dst++;
		}
		free(tofree);
	}
	Py_DECREF(self->pydata);
	self->pydata = pydata;
	self->numlines = self->livelines;
	self->dirty = false;
	return 0;
}

static PyObject *lazymanifest_text(lazymanifest *self)
{
	if (compact(self) != 0) {
		PyErr_NoMemory();
		return NULL;
	}
	Py_INCREF(self->pydata);
	return self->pydata;
}

static lazymanifest *lazymanifest_copy(lazymanifest *self)
{
	lazymanifest *copy = NULL;
	if (compact(self) != 0) {
		goto nomem;
	}
	copy = PyObject_New(lazymanifest, &lazymanifestType);
	if (!copy) {
		goto nomem;
	}
	lazymanifest_init_early(copy);
	copy->nodelen = self->nodelen;
	copy->numlines = self->numlines;
	copy->livelines = self->livelines;
	copy->dirty = false;
	copy->lines = malloc(self->maxlines *sizeof(line));
	if (!copy->lines) {
		goto nomem;
	}
	memcpy(copy->lines, self->lines, self->numlines * sizeof(line));
	copy->maxlines = self->maxlines;
	copy->pydata = self->pydata;
	Py_INCREF(copy->pydata);
	return copy;
nomem:
	PyErr_NoMemory();
	Py_XDECREF(copy);
	return NULL;
}

static lazymanifest *lazymanifest_filtercopy(
	lazymanifest *self, PyObject *matchfn)
{
	lazymanifest *copy = NULL;
	int i;
	if (!PyCallable_Check(matchfn)) {
		PyErr_SetString(PyExc_TypeError, "matchfn must be callable");
		return NULL;
	}
	/* compact ourselves first to avoid double-frees later when we
	 * compact tmp so that it doesn't have random pointers to our
	 * underlying from_malloc-data (self->pydata is safe) */
	if (compact(self) != 0) {
		goto nomem;
	}
	copy = PyObject_New(lazymanifest, &lazymanifestType);
	if (!copy) {
		goto nomem;
	}
	lazymanifest_init_early(copy);
	copy->nodelen = self->nodelen;
	copy->dirty = true;
	copy->lines = malloc(self->maxlines * sizeof(line));
	if (!copy->lines) {
		goto nomem;
	}
	copy->maxlines = self->maxlines;
	copy->numlines = 0;
	copy->pydata = self->pydata;
	Py_INCREF(copy->pydata);
	for (i = 0; i < self->numlines; i++) {
		PyObject *arglist = NULL, *result = NULL;
		arglist = Py_BuildValue("(y)",
					self->lines[i].start);
		if (!arglist) {
			goto bail;
		}
		result = PyObject_CallObject(matchfn, arglist);
		Py_DECREF(arglist);
		/* if the callback raised an exception, just let it
		 * through and give up */
		if (!result) {
			goto bail;
		}
		if (PyObject_IsTrue(result)) {
			assert(!(self->lines[i].from_malloc));
			copy->lines[copy->numlines++] = self->lines[i];
		}
		Py_DECREF(result);
	}
	copy->livelines = copy->numlines;
	return copy;
nomem:
	PyErr_NoMemory();
bail:
	Py_XDECREF(copy);
	return NULL;
}

static PyObject *lazymanifest_diff(lazymanifest *self, PyObject *args)
{
	lazymanifest *other;
	PyObject *pyclean = NULL;
	bool listclean;
	PyObject *emptyTup = NULL, *ret = NULL;
	PyObject *es;
	int sneedle = 0, oneedle = 0;
	if (!PyArg_ParseTuple(args, "O!|O", &lazymanifestType, &other, &pyclean)) {
		return NULL;
	}
	listclean = (!pyclean) ? false : PyObject_IsTrue(pyclean);
	es = PyBytes_FromString("");
	if (!es) {
		goto nomem;
	}
	emptyTup = PyTuple_Pack(2, Py_None, es);
	Py_DECREF(es);
	if (!emptyTup) {
		goto nomem;
	}
	ret = PyDict_New();
	if (!ret) {
		goto nomem;
	}
	while (sneedle != self->numlines || oneedle != other->numlines) {
		line *left = self->lines + sneedle;
		line *right = other->lines + oneedle;
		int result;
		PyObject *key;
		PyObject *outer;
		/* If we're looking at a deleted entry and it's not
		 * the end of the manifest, just skip it. */
		if (sneedle < self->numlines && left->deleted) {
			sneedle++;
			continue;
		}
		if (oneedle < other->numlines && right->deleted) {
			oneedle++;
			continue;
		}
		/* if we're at the end of either manifest, then we
		 * know the remaining items are adds so we can skip
		 * the strcmp. */
		if (sneedle == self->numlines) {
			result = 1;
		} else if (oneedle == other->numlines) {
			result = -1;
		} else {
			result = linecmp(left, right);
		}
		key = result <= 0 ?
			PyBytes_FromString(left->start) :
			PyBytes_FromString(right->start);
		if (!key)
			goto nomem;
		if (result < 0) {
			PyObject *l = hashflags(self->nodelen, left);
			if (!l) {
				goto nomem;
			}
			outer = PyTuple_Pack(2, l, emptyTup);
			Py_DECREF(l);
			if (!outer) {
				goto nomem;
			}
			PyDict_SetItem(ret, key, outer);
			Py_DECREF(outer);
			sneedle++;
		} else if (result > 0) {
			PyObject *r = hashflags(self->nodelen, right);
			if (!r) {
				goto nomem;
			}
			outer = PyTuple_Pack(2, emptyTup, r);
			Py_DECREF(r);
			if (!outer) {
				goto nomem;
			}
			PyDict_SetItem(ret, key, outer);
			Py_DECREF(outer);
			oneedle++;
		} else {
			/* file exists in both manifests */
			if (left->len != right->len
			    || memcmp(left->start, right->start, left->len)
			    || left->hash_suffix != right->hash_suffix) {
				PyObject *l = hashflags(self->nodelen, left);
				PyObject *r;
				if (!l) {
					goto nomem;
				}
				r = hashflags(self->nodelen, right);
				if (!r) {
					Py_DECREF(l);
					goto nomem;
				}
				outer = PyTuple_Pack(2, l, r);
				Py_DECREF(l);
				Py_DECREF(r);
				if (!outer) {
					goto nomem;
				}
				PyDict_SetItem(ret, key, outer);
				Py_DECREF(outer);
			} else if (listclean) {
				PyDict_SetItem(ret, key, Py_None);
			}
			sneedle++;
			oneedle++;
		}
		Py_DECREF(key);
	}
	Py_DECREF(emptyTup);
	return ret;
nomem:
	PyErr_NoMemory();
	Py_XDECREF(ret);
	Py_XDECREF(emptyTup);
	return NULL;
}

static PyMethodDef lazymanifest_methods[] = {
	{"iterkeys", (PyCFunction)lazymanifest_getkeysiter, METH_NOARGS,
	 "Iterate over file names in this lazymanifest."},
	{"iterentries", (PyCFunction)lazymanifest_getentriesiter, METH_NOARGS,
	 "Iterate over (path, nodeid, flags) tuples in this lazymanifest."},
	{"copy", (PyCFunction)lazymanifest_copy, METH_NOARGS,
	 "Make a copy of this lazymanifest."},
	{"filtercopy", (PyCFunction)lazymanifest_filtercopy, METH_O,
	 "Make a copy of this manifest filtered by matchfn."},
	{"diff", (PyCFunction)lazymanifest_diff, METH_VARARGS,
	 "Compare this lazymanifest to another one."},
	{"text", (PyCFunction)lazymanifest_text, METH_NOARGS,
	 "Encode this manifest to text."},
	{NULL},
};

#define LAZYMANIFEST_TPFLAGS Py_TPFLAGS_DEFAULT

static PyTypeObject lazymanifestType = {
	PyVarObject_HEAD_INIT(NULL, 0) /* header */
	"parsers.lazymanifest",                           /* tp_name */
	sizeof(lazymanifest),                             /* tp_basicsize */
	0,                                                /* tp_itemsize */
	(destructor)lazymanifest_dealloc,                 /* tp_dealloc */
	0,                                                /* tp_print */
	0,                                                /* tp_getattr */
	0,                                                /* tp_setattr */
	0,                                                /* tp_compare */
	0,                                                /* tp_repr */
	0,                                                /* tp_as_number */
	&lazymanifest_seq_meths,                          /* tp_as_sequence */
	&lazymanifest_mapping_methods,                    /* tp_as_mapping */
	0,                                                /* tp_hash */
	0,                                                /* tp_call */
	0,                                                /* tp_str */
	0,                                                /* tp_getattro */
	0,                                                /* tp_setattro */
	0,                                                /* tp_as_buffer */
	LAZYMANIFEST_TPFLAGS,                             /* tp_flags */
	"TODO(augie)",                                    /* tp_doc */
	0,                                                /* tp_traverse */
	0,                                                /* tp_clear */
	0,                                                /* tp_richcompare */
	0,                                             /* tp_weaklistoffset */
	(getiterfunc)lazymanifest_getkeysiter,                /* tp_iter */
	0,                                                /* tp_iternext */
	lazymanifest_methods,                             /* tp_methods */
	0,                                                /* tp_members */
	0,                                                /* tp_getset */
	0,                                                /* tp_base */
	0,                                                /* tp_dict */
	0,                                                /* tp_descr_get */
	0,                                                /* tp_descr_set */
	0,                                                /* tp_dictoffset */
	(initproc)lazymanifest_init,                      /* tp_init */
	0,                                                /* tp_alloc */
};

void manifest_module_init(PyObject * mod)
{
	lazymanifestType.tp_new = PyType_GenericNew;
	if (PyType_Ready(&lazymanifestType) < 0)
		return;
	Py_INCREF(&lazymanifestType);

	PyModule_AddObject(mod, "lazymanifest",
			   (PyObject *)&lazymanifestType);
}
