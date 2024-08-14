/*
 parsers.c - efficient content parsing

 Copyright 2008 Olivia Mackall <olivia@selenic.com> and others

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <assert.h>
#include <ctype.h>
#include <limits.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <structmember.h>

#include "bitmanipulation.h"
#include "charencode.h"
#include "compat.h"
#include "revlog.h"
#include "util.h"

typedef struct indexObjectStruct indexObject;

typedef struct {
	int children[16];
} nodetreenode;

typedef struct {
	int abi_version;
	Py_ssize_t (*index_length)(const indexObject *);
	const char *(*index_node)(indexObject *, Py_ssize_t);
	int (*fast_rank)(indexObject *, Py_ssize_t);
	int (*index_parents)(PyObject *, int, int *);
} Revlog_CAPI;

/*
 * A base-16 trie for fast node->rev mapping.
 *
 * Positive value is index of the next node in the trie
 * Negative value is a leaf: -(rev + 2)
 * Zero is empty
 */
typedef struct {
	indexObject *index;
	nodetreenode *nodes;
	Py_ssize_t nodelen;
	size_t length;   /* # nodes in use */
	size_t capacity; /* # nodes allocated */
	int depth;       /* maximum depth of tree */
	int splits;      /* # splits performed */
} nodetree;

typedef struct {
	PyObject_HEAD /* ; */
	    nodetree nt;
} nodetreeObject;

/*
 * This class has two behaviors.
 *
 * When used in a list-like way (with integer keys), we decode an
 * entry in a RevlogNG index file on demand. We have limited support for
 * integer-keyed insert and delete, only at elements right before the
 * end.
 *
 * With string keys, we lazily perform a reverse mapping from node to
 * rev, using a base-16 trie.
 */
struct indexObjectStruct {
	PyObject_HEAD
	    /* Type-specific fields go here. */
	    PyObject *data;     /* raw bytes of index */
	Py_ssize_t nodelen;     /* digest size of the hash, 20 for SHA-1 */
	PyObject *nullentry;    /* fast path for references to null */
	Py_buffer buf;          /* buffer of data */
	const char **offsets;   /* populated on demand */
	Py_ssize_t length;      /* current on-disk number of elements */
	unsigned new_length;    /* number of added elements */
	unsigned added_length;  /* space reserved for added elements */
	char *added;            /* populated on demand */
	PyObject *headrevs;     /* cache, invalidated on changes */
	PyObject *filteredrevs; /* filtered revs set */
	nodetree nt;            /* base-16 trie */
	int ntinitialized;      /* 0 or 1 */
	int ntrev;              /* last rev scanned */
	int ntlookups;          /* # lookups */
	int ntmisses;           /* # lookups that miss the cache */
	int inlined;
	long entry_size; /* size of index headers. Differs in v1 v.s. v2 format
	                  */
	long rust_ext_compat; /* compatibility with being used in rust
	                         extensions */
	long format_version;  /* format version selector (format_*) */
};

static Py_ssize_t index_length(const indexObject *self)
{
	return self->length + self->new_length;
}

static const char nullid[32] = {0};
static const Py_ssize_t nullrev = -1;

static Py_ssize_t inline_scan(indexObject *self, const char **offsets);

static int index_find_node(indexObject *self, const char *node);

#if LONG_MAX == 0x7fffffffL
static const char *const tuple_format = "Kiiiiiiy#KiBBi";
#else
static const char *const tuple_format = "kiiiiiiy#kiBBi";
#endif

/* A RevlogNG v1 index entry is 64 bytes long. */
static const long v1_entry_size = 64;

/* A Revlogv2 index entry is 96 bytes long. */
static const long v2_entry_size = 96;

/* A Changelogv2 index entry is 96 bytes long. */
static const long cl2_entry_size = 96;

/* Internal format version.
 * Must match their counterparts in revlogutils/constants.py */
static const long format_v1 = 1;       /* constants.py: REVLOGV1 */
static const long format_v2 = 0xDEAD;  /* constants.py: REVLOGV2 */
static const long format_cl2 = 0xD34D; /* constants.py: CHANGELOGV2 */

static const long entry_v1_offset_high = 0;
static const long entry_v1_offset_offset_flags = 4;
static const long entry_v1_offset_comp_len = 8;
static const long entry_v1_offset_uncomp_len = 12;
static const long entry_v1_offset_base_rev = 16;
static const long entry_v1_offset_link_rev = 20;
static const long entry_v1_offset_parent_1 = 24;
static const long entry_v1_offset_parent_2 = 28;
static const long entry_v1_offset_node_id = 32;

static const long entry_v2_offset_high = 0;
static const long entry_v2_offset_offset_flags = 4;
static const long entry_v2_offset_comp_len = 8;
static const long entry_v2_offset_uncomp_len = 12;
static const long entry_v2_offset_base_rev = 16;
static const long entry_v2_offset_link_rev = 20;
static const long entry_v2_offset_parent_1 = 24;
static const long entry_v2_offset_parent_2 = 28;
static const long entry_v2_offset_node_id = 32;
static const long entry_v2_offset_sidedata_offset = 64;
static const long entry_v2_offset_sidedata_comp_len = 72;
static const long entry_v2_offset_all_comp_mode = 76;
/* next free offset: 77 */

static const long entry_cl2_offset_high = 0;
static const long entry_cl2_offset_offset_flags = 4;
static const long entry_cl2_offset_comp_len = 8;
static const long entry_cl2_offset_uncomp_len = 12;
static const long entry_cl2_offset_parent_1 = 16;
static const long entry_cl2_offset_parent_2 = 20;
static const long entry_cl2_offset_node_id = 24;
static const long entry_cl2_offset_sidedata_offset = 56;
static const long entry_cl2_offset_sidedata_comp_len = 64;
static const long entry_cl2_offset_all_comp_mode = 68;
static const long entry_cl2_offset_rank = 69;
/* next free offset: 73 */

static const char comp_mode_inline = 2;
static const int rank_unknown = -1;

static void raise_revlog_error(void)
{
	PyObject *mod = NULL, *dict = NULL, *errclass = NULL;

	mod = PyImport_ImportModule("mercurial.error");
	if (mod == NULL) {
		goto cleanup;
	}

	dict = PyModule_GetDict(mod);
	if (dict == NULL) {
		goto cleanup;
	}
	Py_INCREF(dict);

	errclass = PyDict_GetItemString(dict, "RevlogError");
	if (errclass == NULL) {
		PyErr_SetString(PyExc_SystemError,
		                "could not find RevlogError");
		goto cleanup;
	}

	/* value of exception is ignored by callers */
	PyErr_SetString(errclass, "RevlogError");

cleanup:
	Py_XDECREF(dict);
	Py_XDECREF(mod);
}

/*
 * Return a pointer to the beginning of a RevlogNG record.
 */
static const char *index_deref(indexObject *self, Py_ssize_t pos)
{
	if (pos >= self->length)
		return self->added + (pos - self->length) * self->entry_size;

	if (self->inlined && pos > 0) {
		if (self->offsets == NULL) {
			Py_ssize_t ret;
			self->offsets =
			    PyMem_Malloc(self->length * sizeof(*self->offsets));
			if (self->offsets == NULL)
				return (const char *)PyErr_NoMemory();
			ret = inline_scan(self, self->offsets);
			if (ret == -1) {
				return NULL;
			};
		}
		return self->offsets[pos];
	}

	return (const char *)(self->buf.buf) + pos * self->entry_size;
}

/*
 * Get parents of the given rev.
 *
 * The specified rev must be valid and must not be nullrev. A returned
 * parent revision may be nullrev, but is guaranteed to be in valid range.
 */
static inline int index_get_parents(indexObject *self, Py_ssize_t rev, int *ps,
                                    int maxrev)
{
	const char *data = index_deref(self, rev);

	if (self->format_version == format_v1) {
		ps[0] = getbe32(data + entry_v1_offset_parent_1);
		ps[1] = getbe32(data + entry_v1_offset_parent_2);
	} else if (self->format_version == format_v2) {
		ps[0] = getbe32(data + entry_v2_offset_parent_1);
		ps[1] = getbe32(data + entry_v2_offset_parent_2);
	} else if (self->format_version == format_cl2) {
		ps[0] = getbe32(data + entry_cl2_offset_parent_1);
		ps[1] = getbe32(data + entry_cl2_offset_parent_2);
	} else {
		raise_revlog_error();
		return -1;
	}

	/* If index file is corrupted, ps[] may point to invalid revisions. So
	 * there is a risk of buffer overflow to trust them unconditionally. */
	if (ps[0] < -1 || ps[0] > maxrev || ps[1] < -1 || ps[1] > maxrev) {
		PyErr_SetString(PyExc_ValueError, "parent out of range");
		return -1;
	}
	return 0;
}

/*
 * Get parents of the given rev.
 *
 * If the specified rev is out of range, IndexError will be raised. If the
 * revlog entry is corrupted, ValueError may be raised.
 *
 * Returns 0 on success or -1 on failure.
 */
static int HgRevlogIndex_GetParents(PyObject *op, int rev, int *ps)
{
	int tiprev;
	if (!op || !HgRevlogIndex_Check(op) || !ps) {
		PyErr_BadInternalCall();
		return -1;
	}
	tiprev = (int)index_length((indexObject *)op) - 1;
	if (rev < -1 || rev > tiprev) {
		PyErr_Format(PyExc_IndexError, "rev out of range: %d", rev);
		return -1;
	} else if (rev == -1) {
		ps[0] = ps[1] = -1;
		return 0;
	} else {
		return index_get_parents((indexObject *)op, rev, ps, tiprev);
	}
}

static inline int64_t index_get_start(indexObject *self, Py_ssize_t rev)
{
	const char *data;
	uint64_t offset;

	if (rev == nullrev)
		return 0;

	data = index_deref(self, rev);

	if (self->format_version == format_v1) {
		offset = getbe32(data + entry_v1_offset_offset_flags);
		if (rev == 0) {
			/* mask out version number for the first entry */
			offset &= 0xFFFF;
		} else {
			uint32_t offset_high =
			    getbe32(data + entry_v1_offset_high);
			offset |= ((uint64_t)offset_high) << 32;
		}
	} else if (self->format_version == format_v2) {
		offset = getbe32(data + entry_v2_offset_offset_flags);
		if (rev == 0) {
			/* mask out version number for the first entry */
			offset &= 0xFFFF;
		} else {
			uint32_t offset_high =
			    getbe32(data + entry_v2_offset_high);
			offset |= ((uint64_t)offset_high) << 32;
		}
	} else if (self->format_version == format_cl2) {
		uint32_t offset_high = getbe32(data + entry_cl2_offset_high);
		offset = getbe32(data + entry_cl2_offset_offset_flags);
		offset |= ((uint64_t)offset_high) << 32;
	} else {
		raise_revlog_error();
		return -1;
	}

	return (int64_t)(offset >> 16);
}

static inline int index_get_length(indexObject *self, Py_ssize_t rev)
{
	const char *data;
	int tmp;

	if (rev == nullrev)
		return 0;

	data = index_deref(self, rev);

	if (self->format_version == format_v1) {
		tmp = (int)getbe32(data + entry_v1_offset_comp_len);
	} else if (self->format_version == format_v2) {
		tmp = (int)getbe32(data + entry_v2_offset_comp_len);
	} else if (self->format_version == format_cl2) {
		tmp = (int)getbe32(data + entry_cl2_offset_comp_len);
	} else {
		raise_revlog_error();
		return -1;
	}
	if (tmp < 0) {
		PyErr_Format(PyExc_OverflowError,
		             "revlog entry size out of bound (%d)", tmp);
		return -1;
	}
	return tmp;
}

/*
 * RevlogNG format (all in big endian, data may be inlined):
 *    6 bytes: offset
 *    2 bytes: flags
 *    4 bytes: compressed length
 *    4 bytes: uncompressed length
 *    4 bytes: base revision
 *    4 bytes: link revision
 *    4 bytes: parent 1 revision
 *    4 bytes: parent 2 revision
 *   32 bytes: nodeid (only 20 bytes used with SHA-1)
 */
static PyObject *index_get(indexObject *self, Py_ssize_t pos)
{
	uint64_t offset_flags, sidedata_offset;
	int comp_len, uncomp_len, base_rev, link_rev, parent_1, parent_2,
	    sidedata_comp_len, rank = rank_unknown;
	char data_comp_mode, sidedata_comp_mode;
	const char *c_node_id;
	const char *data;
	Py_ssize_t length = index_length(self);

	if (pos == nullrev) {
		Py_INCREF(self->nullentry);
		return self->nullentry;
	}

	if (pos < 0 || pos >= length) {
		PyErr_SetString(PyExc_IndexError, "revlog index out of range");
		return NULL;
	}

	data = index_deref(self, pos);
	if (data == NULL)
		return NULL;

	if (self->format_version == format_v1) {
		offset_flags = getbe32(data + entry_v1_offset_offset_flags);
		/*
		 * The first entry on-disk needs the version number masked out,
		 * but this doesn't apply if entries are added to an empty
		 * index.
		 */
		if (self->length && pos == 0)
			offset_flags &= 0xFFFF;
		else {
			uint32_t offset_high =
			    getbe32(data + entry_v1_offset_high);
			offset_flags |= ((uint64_t)offset_high) << 32;
		}

		comp_len = getbe32(data + entry_v1_offset_comp_len);
		uncomp_len = getbe32(data + entry_v1_offset_uncomp_len);
		base_rev = getbe32(data + entry_v1_offset_base_rev);
		link_rev = getbe32(data + entry_v1_offset_link_rev);
		parent_1 = getbe32(data + entry_v1_offset_parent_1);
		parent_2 = getbe32(data + entry_v1_offset_parent_2);
		c_node_id = data + entry_v1_offset_node_id;

		sidedata_offset = 0;
		sidedata_comp_len = 0;
		data_comp_mode = comp_mode_inline;
		sidedata_comp_mode = comp_mode_inline;
	} else if (self->format_version == format_v2) {
		offset_flags = getbe32(data + entry_v2_offset_offset_flags);
		/*
		 * The first entry on-disk needs the version number masked out,
		 * but this doesn't apply if entries are added to an empty
		 * index.
		 */
		if (self->length && pos == 0)
			offset_flags &= 0xFFFF;
		else {
			uint32_t offset_high =
			    getbe32(data + entry_v2_offset_high);
			offset_flags |= ((uint64_t)offset_high) << 32;
		}

		comp_len = getbe32(data + entry_v2_offset_comp_len);
		uncomp_len = getbe32(data + entry_v2_offset_uncomp_len);
		base_rev = getbe32(data + entry_v2_offset_base_rev);
		link_rev = getbe32(data + entry_v2_offset_link_rev);
		parent_1 = getbe32(data + entry_v2_offset_parent_1);
		parent_2 = getbe32(data + entry_v2_offset_parent_2);
		c_node_id = data + entry_v2_offset_node_id;

		sidedata_offset =
		    getbe64(data + entry_v2_offset_sidedata_offset);
		sidedata_comp_len =
		    getbe32(data + entry_v2_offset_sidedata_comp_len);
		data_comp_mode = data[entry_v2_offset_all_comp_mode] & 3;
		sidedata_comp_mode =
		    ((data[entry_v2_offset_all_comp_mode] >> 2) & 3);
	} else if (self->format_version == format_cl2) {
		uint32_t offset_high = getbe32(data + entry_cl2_offset_high);
		offset_flags = getbe32(data + entry_cl2_offset_offset_flags);
		offset_flags |= ((uint64_t)offset_high) << 32;
		comp_len = getbe32(data + entry_cl2_offset_comp_len);
		uncomp_len = getbe32(data + entry_cl2_offset_uncomp_len);
		/* base_rev and link_rev are not stored in changelogv2, but are
		 still used by some functions shared with the other revlogs.
		 They are supposed to contain links to other revisions,
		 but they always point to themselves in the case of a changelog.
		*/
		base_rev = pos;
		link_rev = pos;
		parent_1 = getbe32(data + entry_cl2_offset_parent_1);
		parent_2 = getbe32(data + entry_cl2_offset_parent_2);
		c_node_id = data + entry_cl2_offset_node_id;
		sidedata_offset =
		    getbe64(data + entry_cl2_offset_sidedata_offset);
		sidedata_comp_len =
		    getbe32(data + entry_cl2_offset_sidedata_comp_len);
		data_comp_mode = data[entry_cl2_offset_all_comp_mode] & 3;
		sidedata_comp_mode =
		    ((data[entry_cl2_offset_all_comp_mode] >> 2) & 3);
		rank = getbe32(data + entry_cl2_offset_rank);
	} else {
		raise_revlog_error();
		return NULL;
	}

	return Py_BuildValue(tuple_format, offset_flags, comp_len, uncomp_len,
	                     base_rev, link_rev, parent_1, parent_2, c_node_id,
	                     self->nodelen, sidedata_offset, sidedata_comp_len,
	                     data_comp_mode, sidedata_comp_mode, rank);
}
/*
 * Pack header information in binary
 */
static PyObject *index_pack_header(indexObject *self, PyObject *args)
{
	int header;
	char out[4];
	if (!PyArg_ParseTuple(args, "i", &header)) {
		return NULL;
	}
	if (self->format_version != format_v1) {
		PyErr_Format(PyExc_RuntimeError,
		             "version header should go in the docket, not the "
		             "index: %d",
		             header);
		return NULL;
	}
	putbe32(header, out);
	return PyBytes_FromStringAndSize(out, 4);
}
/*
 * Return the raw binary string representing a revision
 */
static PyObject *index_entry_binary(indexObject *self, PyObject *value)
{
	long rev;
	const char *data;
	Py_ssize_t length = index_length(self);

	if (!pylong_to_long(value, &rev)) {
		return NULL;
	}
	if (rev < 0 || rev >= length) {
		PyErr_Format(PyExc_ValueError, "revlog index out of range: %ld",
		             rev);
		return NULL;
	};

	data = index_deref(self, rev);
	if (data == NULL)
		return NULL;
	if (rev == 0 && self->format_version == format_v1) {
		/* the header is eating the start of the first entry */
		return PyBytes_FromStringAndSize(data + 4,
		                                 self->entry_size - 4);
	}
	return PyBytes_FromStringAndSize(data, self->entry_size);
}

/*
 * Return the hash of node corresponding to the given rev.
 */
static const char *index_node(indexObject *self, Py_ssize_t pos)
{
	Py_ssize_t length = index_length(self);
	const char *data;
	const char *node_id;

	if (pos == nullrev)
		return nullid;

	if (pos >= length)
		return NULL;

	data = index_deref(self, pos);

	if (self->format_version == format_v1) {
		node_id = data + entry_v1_offset_node_id;
	} else if (self->format_version == format_v2) {
		node_id = data + entry_v2_offset_node_id;
	} else if (self->format_version == format_cl2) {
		node_id = data + entry_cl2_offset_node_id;
	} else {
		raise_revlog_error();
		return NULL;
	}

	return data ? node_id : NULL;
}

/*
 * Return the stored rank of a given revision if known, or rank_unknown
 * otherwise.
 *
 * The rank of a revision is the size of the sub-graph it defines as a head.
 * Equivalently, the rank of a revision `r` is the size of the set
 * `ancestors(r)`, `r` included.
 *
 * This method returns the rank retrieved from the revlog in constant time. It
 * makes no attempt at computing unknown values for versions of the revlog
 * which do not persist the rank.
 */
static int index_fast_rank(indexObject *self, Py_ssize_t pos)
{
	Py_ssize_t length = index_length(self);

	if (self->format_version != format_cl2 || pos >= length) {
		return rank_unknown;
	}

	if (pos == nullrev) {
		return 0; /* convention */
	}

	return getbe32(index_deref(self, pos) + entry_cl2_offset_rank);
}

/*
 * Return the hash of the node corresponding to the given rev. The
 * rev is assumed to be existing. If not, an exception is set.
 */
static const char *index_node_existing(indexObject *self, Py_ssize_t pos)
{
	const char *node = index_node(self, pos);
	if (node == NULL) {
		PyErr_Format(PyExc_IndexError, "could not access rev %d",
		             (int)pos);
	}
	return node;
}

static int nt_insert(nodetree *self, const char *node, int rev);

static int node_check(Py_ssize_t nodelen, PyObject *obj, char **node)
{
	Py_ssize_t thisnodelen;
	if (PyBytes_AsStringAndSize(obj, node, &thisnodelen) == -1)
		return -1;
	if (nodelen == thisnodelen)
		return 0;
	PyErr_Format(PyExc_ValueError, "node len %zd != expected node len %zd",
	             thisnodelen, nodelen);
	return -1;
}

static PyObject *index_append(indexObject *self, PyObject *obj)
{
	uint64_t offset_flags, sidedata_offset;
	int rev, comp_len, uncomp_len, base_rev, link_rev, parent_1, parent_2,
	    sidedata_comp_len, rank;
	char data_comp_mode, sidedata_comp_mode;
	Py_ssize_t c_node_id_len;
	const char *c_node_id;
	char comp_field;
	char *data;

	if (!PyArg_ParseTuple(obj, tuple_format, &offset_flags, &comp_len,
	                      &uncomp_len, &base_rev, &link_rev, &parent_1,
	                      &parent_2, &c_node_id, &c_node_id_len,
	                      &sidedata_offset, &sidedata_comp_len,
	                      &data_comp_mode, &sidedata_comp_mode, &rank)) {
		PyErr_SetString(PyExc_TypeError, "12-tuple required");
		return NULL;
	}

	if (c_node_id_len != self->nodelen) {
		PyErr_SetString(PyExc_TypeError, "invalid node");
		return NULL;
	}
	if (self->format_version == format_v1) {

		if (data_comp_mode != comp_mode_inline) {
			PyErr_Format(PyExc_ValueError,
			             "invalid data compression mode: %i",
			             data_comp_mode);
			return NULL;
		}
		if (sidedata_comp_mode != comp_mode_inline) {
			PyErr_Format(PyExc_ValueError,
			             "invalid sidedata compression mode: %i",
			             sidedata_comp_mode);
			return NULL;
		}
	}

	if (self->new_length == self->added_length) {
		size_t new_added_length =
		    self->added_length ? self->added_length * 2 : 4096;
		void *new_added = PyMem_Realloc(
		    self->added, new_added_length * self->entry_size);
		if (!new_added)
			return PyErr_NoMemory();
		self->added = new_added;
		self->added_length = new_added_length;
	}
	rev = self->length + self->new_length;
	data = self->added + self->entry_size * self->new_length++;

	memset(data, 0, self->entry_size);

	if (self->format_version == format_v1) {
		putbe32(offset_flags >> 32, data + entry_v1_offset_high);
		putbe32(offset_flags & 0xffffffffU,
		        data + entry_v1_offset_offset_flags);
		putbe32(comp_len, data + entry_v1_offset_comp_len);
		putbe32(uncomp_len, data + entry_v1_offset_uncomp_len);
		putbe32(base_rev, data + entry_v1_offset_base_rev);
		putbe32(link_rev, data + entry_v1_offset_link_rev);
		putbe32(parent_1, data + entry_v1_offset_parent_1);
		putbe32(parent_2, data + entry_v1_offset_parent_2);
		memcpy(data + entry_v1_offset_node_id, c_node_id,
		       c_node_id_len);
	} else if (self->format_version == format_v2) {
		putbe32(offset_flags >> 32, data + entry_v2_offset_high);
		putbe32(offset_flags & 0xffffffffU,
		        data + entry_v2_offset_offset_flags);
		putbe32(comp_len, data + entry_v2_offset_comp_len);
		putbe32(uncomp_len, data + entry_v2_offset_uncomp_len);
		putbe32(base_rev, data + entry_v2_offset_base_rev);
		putbe32(link_rev, data + entry_v2_offset_link_rev);
		putbe32(parent_1, data + entry_v2_offset_parent_1);
		putbe32(parent_2, data + entry_v2_offset_parent_2);
		memcpy(data + entry_v2_offset_node_id, c_node_id,
		       c_node_id_len);
		putbe64(sidedata_offset,
		        data + entry_v2_offset_sidedata_offset);
		putbe32(sidedata_comp_len,
		        data + entry_v2_offset_sidedata_comp_len);
		comp_field = data_comp_mode & 3;
		comp_field = comp_field | (sidedata_comp_mode & 3) << 2;
		data[entry_v2_offset_all_comp_mode] = comp_field;
	} else if (self->format_version == format_cl2) {
		putbe32(offset_flags >> 32, data + entry_cl2_offset_high);
		putbe32(offset_flags & 0xffffffffU,
		        data + entry_cl2_offset_offset_flags);
		putbe32(comp_len, data + entry_cl2_offset_comp_len);
		putbe32(uncomp_len, data + entry_cl2_offset_uncomp_len);
		putbe32(parent_1, data + entry_cl2_offset_parent_1);
		putbe32(parent_2, data + entry_cl2_offset_parent_2);
		memcpy(data + entry_cl2_offset_node_id, c_node_id,
		       c_node_id_len);
		putbe64(sidedata_offset,
		        data + entry_cl2_offset_sidedata_offset);
		putbe32(sidedata_comp_len,
		        data + entry_cl2_offset_sidedata_comp_len);
		comp_field = data_comp_mode & 3;
		comp_field = comp_field | (sidedata_comp_mode & 3) << 2;
		data[entry_cl2_offset_all_comp_mode] = comp_field;
		putbe32(rank, data + entry_cl2_offset_rank);
	} else {
		raise_revlog_error();
		return NULL;
	}

	if (self->ntinitialized)
		nt_insert(&self->nt, c_node_id, rev);

	Py_CLEAR(self->headrevs);
	Py_RETURN_NONE;
}

/* Replace an existing index entry's sidedata offset and length with new ones.
   This cannot be used outside of the context of sidedata rewriting,
   inside the transaction that creates the given revision. */
static PyObject *index_replace_sidedata_info(indexObject *self, PyObject *args)
{
	uint64_t offset_flags, sidedata_offset;
	Py_ssize_t rev;
	int sidedata_comp_len;
	char comp_mode;
	char *data;
#if LONG_MAX == 0x7fffffffL
	const char *const sidedata_format = "nKiKB";
#else
	const char *const sidedata_format = "nkikB";
#endif

	if (self->entry_size == v1_entry_size || self->inlined) {
		/*
		 There is a bug in the transaction handling when going from an
	   inline revlog to a separate index and data file. Turn it off until
	   it's fixed, since v2 revlogs sometimes get rewritten on exchange.
	   See issue6485.
	  */
		raise_revlog_error();
		return NULL;
	}

	if (!PyArg_ParseTuple(args, sidedata_format, &rev, &sidedata_offset,
	                      &sidedata_comp_len, &offset_flags, &comp_mode))
		return NULL;

	if (rev < 0 || rev >= index_length(self)) {
		PyErr_SetString(PyExc_IndexError, "revision outside index");
		return NULL;
	}
	if (rev < self->length) {
		PyErr_SetString(
		    PyExc_IndexError,
		    "cannot rewrite entries outside of this transaction");
		return NULL;
	}

	/* Find the newly added node, offset from the "already on-disk" length
	 */
	data = self->added + self->entry_size * (rev - self->length);
	if (self->format_version == format_v2) {
		putbe64(offset_flags, data + entry_v2_offset_high);
		putbe64(sidedata_offset,
		        data + entry_v2_offset_sidedata_offset);
		putbe32(sidedata_comp_len,
		        data + entry_v2_offset_sidedata_comp_len);
		data[entry_v2_offset_all_comp_mode] =
		    (data[entry_v2_offset_all_comp_mode] & ~(3 << 2)) |
		    ((comp_mode & 3) << 2);
	} else if (self->format_version == format_cl2) {
		putbe64(offset_flags, data + entry_cl2_offset_high);
		putbe64(sidedata_offset,
		        data + entry_cl2_offset_sidedata_offset);
		putbe32(sidedata_comp_len,
		        data + entry_cl2_offset_sidedata_comp_len);
		data[entry_cl2_offset_all_comp_mode] =
		    (data[entry_cl2_offset_all_comp_mode] & ~(3 << 2)) |
		    ((comp_mode & 3) << 2);
	} else {
		raise_revlog_error();
		return NULL;
	}

	Py_RETURN_NONE;
}

static PyObject *index_stats(indexObject *self)
{
	PyObject *obj = PyDict_New();
	PyObject *s = NULL;
	PyObject *t = NULL;

	if (obj == NULL)
		return NULL;

#define istat(__n, __d)                                                        \
	do {                                                                   \
		s = PyBytes_FromString(__d);                                   \
		t = PyLong_FromSsize_t(self->__n);                             \
		if (!s || !t)                                                  \
			goto bail;                                             \
		if (PyDict_SetItem(obj, s, t) == -1)                           \
			goto bail;                                             \
		Py_CLEAR(s);                                                   \
		Py_CLEAR(t);                                                   \
	} while (0)

	if (self->added_length)
		istat(new_length, "index entries added");
	istat(length, "revs in memory");
	istat(ntlookups, "node trie lookups");
	istat(ntmisses, "node trie misses");
	istat(ntrev, "node trie last rev scanned");
	if (self->ntinitialized) {
		istat(nt.capacity, "node trie capacity");
		istat(nt.depth, "node trie depth");
		istat(nt.length, "node trie count");
		istat(nt.splits, "node trie splits");
	}

#undef istat

	return obj;

bail:
	Py_XDECREF(obj);
	Py_XDECREF(s);
	Py_XDECREF(t);
	return NULL;
}

/*
 * When we cache a list, we want to be sure the caller can't mutate
 * the cached copy.
 */
static PyObject *list_copy(PyObject *list)
{
	Py_ssize_t len = PyList_GET_SIZE(list);
	PyObject *newlist = PyList_New(len);
	Py_ssize_t i;

	if (newlist == NULL)
		return NULL;

	for (i = 0; i < len; i++) {
		PyObject *obj = PyList_GET_ITEM(list, i);
		Py_INCREF(obj);
		PyList_SET_ITEM(newlist, i, obj);
	}

	return newlist;
}

static int check_filter(PyObject *filter, Py_ssize_t arg)
{
	if (filter) {
		PyObject *arglist, *result;
		int isfiltered;

		arglist = Py_BuildValue("(n)", arg);
		if (!arglist) {
			return -1;
		}

		result = PyObject_Call(filter, arglist, NULL);
		Py_DECREF(arglist);
		if (!result) {
			return -1;
		}

		/* PyObject_IsTrue returns 1 if true, 0 if false, -1 if error,
		 * same as this function, so we can just return it directly.*/
		isfiltered = PyObject_IsTrue(result);
		Py_DECREF(result);
		return isfiltered;
	} else {
		return 0;
	}
}

static inline void set_phase_from_parents(char *phases, int parent_1,
                                          int parent_2, Py_ssize_t i)
{
	if (parent_1 >= 0 && phases[parent_1] > phases[i])
		phases[i] = phases[parent_1];
	if (parent_2 >= 0 && phases[parent_2] > phases[i])
		phases[i] = phases[parent_2];
}

/* Take ownership of a given Python value and add it to a Python list.
   Return -1 on failure (including if [elem] is NULL). */
static int pylist_append_owned(PyObject *list, PyObject *elem)
{
	int res;

	if (elem == NULL)
		return -1;
	res = PyList_Append(list, elem);
	Py_DECREF(elem);
	return res;
}

static PyObject *reachableroots2(indexObject *self, PyObject *args)
{

	/* Input */
	long minroot;
	PyObject *includepatharg = NULL;
	int includepath = 0;
	/* heads and roots are lists */
	PyObject *heads = NULL;
	PyObject *roots = NULL;
	PyObject *reachable = NULL;

	Py_ssize_t len = index_length(self);
	long revnum;
	Py_ssize_t k;
	Py_ssize_t i;
	Py_ssize_t l;
	int r;
	int parents[2];

	/* Internal data structure:
	 * tovisit: array of length len+1 (all revs + nullrev), filled upto
	 * lentovisit
	 *
	 * revstates: array of length len+1 (all revs + nullrev) */
	int *tovisit = NULL;
	long lentovisit = 0;
	enum { RS_SEEN = 1, RS_ROOT = 2, RS_REACHABLE = 4 };
	char *revstates = NULL;

	/* Get arguments */
	if (!PyArg_ParseTuple(args, "lO!O!O!", &minroot, &PyList_Type, &heads,
	                      &PyList_Type, &roots, &PyBool_Type,
	                      &includepatharg))
		goto bail;

	if (includepatharg == Py_True)
		includepath = 1;

	/* Initialize return set */
	reachable = PyList_New(0);
	if (reachable == NULL)
		goto bail;

	/* Initialize internal datastructures */
	tovisit = (int *)malloc((len + 1) * sizeof(int));
	if (tovisit == NULL) {
		PyErr_NoMemory();
		goto bail;
	}

	revstates = (char *)calloc(len + 1, 1);
	if (revstates == NULL) {
		PyErr_NoMemory();
		goto bail;
	}

	l = PyList_GET_SIZE(roots);
	for (i = 0; i < l; i++) {
		revnum = PyLong_AsLong(PyList_GET_ITEM(roots, i));
		if (revnum == -1 && PyErr_Occurred())
			goto bail;
		/* If root is out of range, e.g. wdir(), it must be unreachable
		 * from heads. So we can just ignore it. */
		if (revnum + 1 < 0 || revnum + 1 >= len + 1)
			continue;
		revstates[revnum + 1] |= RS_ROOT;
	}

	/* Populate tovisit with all the heads */
	l = PyList_GET_SIZE(heads);
	for (i = 0; i < l; i++) {
		revnum = PyLong_AsLong(PyList_GET_ITEM(heads, i));
		if (revnum == -1 && PyErr_Occurred())
			goto bail;
		if (revnum + 1 < 0 || revnum + 1 >= len + 1) {
			PyErr_SetString(PyExc_IndexError, "head out of range");
			goto bail;
		}
		if (!(revstates[revnum + 1] & RS_SEEN)) {
			tovisit[lentovisit++] = (int)revnum;
			revstates[revnum + 1] |= RS_SEEN;
		}
	}

	/* Visit the tovisit list and find the reachable roots */
	k = 0;
	while (k < lentovisit) {
		/* Add the node to reachable if it is a root*/
		revnum = tovisit[k++];
		if (revstates[revnum + 1] & RS_ROOT) {
			revstates[revnum + 1] |= RS_REACHABLE;
			r = pylist_append_owned(reachable,
			                        PyLong_FromLong(revnum));
			if (r < 0)
				goto bail;
			if (includepath == 0)
				continue;
		}

		/* Add its parents to the list of nodes to visit */
		if (revnum == nullrev)
			continue;
		r = index_get_parents(self, revnum, parents, (int)len - 1);
		if (r < 0)
			goto bail;
		for (i = 0; i < 2; i++) {
			if (!(revstates[parents[i] + 1] & RS_SEEN) &&
			    parents[i] >= minroot) {
				tovisit[lentovisit++] = parents[i];
				revstates[parents[i] + 1] |= RS_SEEN;
			}
		}
	}

	/* Find all the nodes in between the roots we found and the heads
	 * and add them to the reachable set */
	if (includepath == 1) {
		long minidx = minroot;
		if (minidx < 0)
			minidx = 0;
		for (i = minidx; i < len; i++) {
			if (!(revstates[i + 1] & RS_SEEN))
				continue;
			r = index_get_parents(self, i, parents, (int)len - 1);
			/* Corrupted index file, error is set from
			 * index_get_parents */
			if (r < 0)
				goto bail;
			if (((revstates[parents[0] + 1] |
			      revstates[parents[1] + 1]) &
			     RS_REACHABLE) &&
			    !(revstates[i + 1] & RS_REACHABLE)) {
				revstates[i + 1] |= RS_REACHABLE;
				r = pylist_append_owned(reachable,
				                        PyLong_FromSsize_t(i));
				if (r < 0)
					goto bail;
			}
		}
	}

	free(revstates);
	free(tovisit);
	return reachable;
bail:
	Py_XDECREF(reachable);
	free(revstates);
	free(tovisit);
	return NULL;
}

static int add_roots_get_min(indexObject *self, PyObject *roots, char *phases,
                             char phase)
{
	Py_ssize_t len = index_length(self);
	PyObject *item;
	PyObject *iterator;
	int rev, minrev = -1;

	if (!PySet_Check(roots)) {
		PyErr_SetString(PyExc_TypeError,
		                "roots must be a set of nodes");
		return -2;
	}
	iterator = PyObject_GetIter(roots);
	if (iterator == NULL)
		return -2;
	while ((item = PyIter_Next(iterator))) {
		rev = (int)PyLong_AsLong(item);
		if (rev == -1 && PyErr_Occurred()) {
			goto failed;
		}
		/* null is implicitly public, so negative is invalid */
		if (rev < 0 || rev >= len)
			goto failed;
		phases[rev] = phase;
		if (minrev == -1 || minrev > rev)
			minrev = rev;
		Py_DECREF(item);
	}
	Py_DECREF(iterator);
	return minrev;
failed:
	Py_DECREF(iterator);
	Py_DECREF(item);
	return -2;
}

static PyObject *compute_phases_map_sets(indexObject *self, PyObject *args)
{
	/* 0: public (untracked), 1: draft, 2: secret, 32: archive,
	   96: internal */
	static const char trackedphases[] = {1, 2, 32, 96};
	PyObject *roots = Py_None;
	PyObject *phasesetsdict = NULL;
	PyObject *phasesets[4] = {NULL, NULL, NULL, NULL};
	Py_ssize_t len = index_length(self);
	char *phases = NULL;
	int minphaserev = -1, rev, i;
	const int numphases = (int)(sizeof(phasesets) / sizeof(phasesets[0]));

	if (!PyArg_ParseTuple(args, "O", &roots))
		return NULL;
	if (roots == NULL || !PyDict_Check(roots)) {
		PyErr_SetString(PyExc_TypeError, "roots must be a dictionary");
		return NULL;
	}

	phases = calloc(len, 1);
	if (phases == NULL) {
		PyErr_NoMemory();
		return NULL;
	}

	for (i = 0; i < numphases; ++i) {
		PyObject *pyphase = PyLong_FromLong(trackedphases[i]);
		PyObject *phaseroots = NULL;
		if (pyphase == NULL)
			goto release;
		phaseroots = PyDict_GetItem(roots, pyphase);
		Py_DECREF(pyphase);
		if (phaseroots == NULL)
			continue;
		rev = add_roots_get_min(self, phaseroots, phases,
		                        trackedphases[i]);
		if (rev == -2)
			goto release;
		if (rev != -1 && (minphaserev == -1 || rev < minphaserev))
			minphaserev = rev;
	}

	for (i = 0; i < numphases; ++i) {
		phasesets[i] = PySet_New(NULL);
		if (phasesets[i] == NULL)
			goto release;
	}

	if (minphaserev == -1)
		minphaserev = len;
	for (rev = minphaserev; rev < len; ++rev) {
		PyObject *pyphase = NULL;
		PyObject *pyrev = NULL;
		int parents[2];
		/*
		 * The parent lookup could be skipped for phaseroots, but
		 * phase --force would historically not recompute them
		 * correctly, leaving descendents with a lower phase around.
		 * As such, unconditionally recompute the phase.
		 */
		if (index_get_parents(self, rev, parents, (int)len - 1) < 0)
			goto release;
		set_phase_from_parents(phases, parents[0], parents[1], rev);
		switch (phases[rev]) {
		case 0:
			continue;
		case 1:
			pyphase = phasesets[0];
			break;
		case 2:
			pyphase = phasesets[1];
			break;
		case 32:
			pyphase = phasesets[2];
			break;
		case 96:
			pyphase = phasesets[3];
			break;
		default:
			/* this should never happen since the phase number is
			 * specified by this function. */
			PyErr_SetString(PyExc_SystemError,
			                "bad phase number in internal list");
			goto release;
		}
		pyrev = PyLong_FromLong(rev);
		if (pyrev == NULL)
			goto release;
		if (PySet_Add(pyphase, pyrev) == -1) {
			Py_DECREF(pyrev);
			goto release;
		}
		Py_DECREF(pyrev);
	}

	phasesetsdict = _dict_new_presized(numphases);
	if (phasesetsdict == NULL)
		goto release;
	for (i = 0; i < numphases; ++i) {
		PyObject *pyphase = PyLong_FromLong(trackedphases[i]);
		if (pyphase == NULL)
			goto release;
		if (PyDict_SetItem(phasesetsdict, pyphase, phasesets[i]) ==
		    -1) {
			Py_DECREF(pyphase);
			goto release;
		}
		Py_DECREF(phasesets[i]);
		phasesets[i] = NULL;
	}

	free(phases);
	return Py_BuildValue("nN", len, phasesetsdict);

release:
	for (i = 0; i < numphases; ++i)
		Py_XDECREF(phasesets[i]);
	Py_XDECREF(phasesetsdict);

	free(phases);
	return NULL;
}

static PyObject *index_headrevs(indexObject *self, PyObject *args)
{
	Py_ssize_t i, j, len;
	char *nothead = NULL;
	PyObject *heads = NULL;
	PyObject *filter = NULL;
	PyObject *filteredrevs = Py_None;

	if (!PyArg_ParseTuple(args, "|O", &filteredrevs)) {
		return NULL;
	}

	if (self->headrevs && filteredrevs == self->filteredrevs)
		return list_copy(self->headrevs);

	Py_DECREF(self->filteredrevs);
	self->filteredrevs = filteredrevs;
	Py_INCREF(filteredrevs);

	if (filteredrevs != Py_None) {
		filter = PyObject_GetAttrString(filteredrevs, "__contains__");
		if (!filter) {
			PyErr_SetString(
			    PyExc_TypeError,
			    "filteredrevs has no attribute __contains__");
			goto bail;
		}
	}

	len = index_length(self);
	heads = PyList_New(0);
	if (heads == NULL)
		goto bail;
	if (len == 0) {
		if (pylist_append_owned(heads, PyLong_FromLong(-1)) == -1) {
			Py_XDECREF(nullid);
			goto bail;
		}
		goto done;
	}

	nothead = calloc(len, 1);
	if (nothead == NULL) {
		PyErr_NoMemory();
		goto bail;
	}

	for (i = len - 1; i >= 0; i--) {
		int isfiltered;
		int parents[2];

		/* If nothead[i] == 1, it means we've seen an unfiltered child
		 * of this node already, and therefore this node is not
		 * filtered. So we can skip the expensive check_filter step.
		 */
		if (nothead[i] != 1) {
			isfiltered = check_filter(filter, i);
			if (isfiltered == -1) {
				PyErr_SetString(PyExc_TypeError,
				                "unable to check filter");
				goto bail;
			}

			if (isfiltered) {
				nothead[i] = 1;
				continue;
			}
		}

		if (index_get_parents(self, i, parents, (int)len - 1) < 0)
			goto bail;
		for (j = 0; j < 2; j++) {
			if (parents[j] >= 0)
				nothead[parents[j]] = 1;
		}
	}

	for (i = 0; i < len; i++) {
		if (nothead[i])
			continue;
		if (pylist_append_owned(heads, PyLong_FromSsize_t(i)) == -1) {
			goto bail;
		}
	}

done:
	self->headrevs = heads;
	Py_XDECREF(filter);
	free(nothead);
	return list_copy(self->headrevs);
bail:
	Py_XDECREF(filter);
	Py_XDECREF(heads);
	free(nothead);
	return NULL;
}

/* "rgs" stands for "reverse growable set".
   It is a representation of a set of integers that can't exceed, but
   tend to be close to `max`.

   `body` is a growable bit array covering the range `max-len+1..max`,
   in reverse order.
   `sum` keeps track of the cardinality of the set.
*/
typedef struct rgs {
	int max;
	int len;
	char *body;
	int sum;
} rgs;

static int rgs_offset(rgs *rgs, int i)
{
	return rgs->max - i;
}

/* returns 1 on success, 0 on failure */
static int rgs_alloc(rgs *rgs, int max)
{
	int new_len = 64;
	char *new_body = calloc(new_len, 1);
	if (new_body == NULL)
		return 0;
	rgs->len = new_len;
	rgs->body = new_body;
	rgs->max = max;
	rgs->sum = 0;
	return 1;
}

static bool rgs_get(rgs *rgs, int i)
{
	int offset = rgs_offset(rgs, i);
	if (offset >= rgs->len) {
		return 0;
	}
	if (offset < 0) {
		abort();
	}
	return rgs->body[offset];
}

/* Realloc `body` to length `new_len`.
   Returns -1 when out of memory. */
static int rgs_realloc(rgs *rgs, int new_len)
{
	int old_len = rgs->len;
	char *old_body = rgs->body;
	char *new_body = calloc(new_len, 1);
	assert(new_len >= old_len);
	if (new_body == NULL)
		return -1;
	memcpy(new_body, old_body, old_len);
	free(old_body);
	rgs->body = new_body;
	rgs->len = new_len;
	return 0;
}

/* Realloc the rgs `body` to include the `offset` */
static int rgs_realloc_amortized(rgs *rgs, int offset)
{
	int old_len = rgs->len;
	int new_len = old_len * 4;
	if (offset >= new_len)
		new_len = offset + 1;
	return rgs_realloc(rgs, new_len);
}

/* returns 0 on success, -1 on error */
static int rgs_set(rgs *rgs, int i, bool v)
{
	int offset = rgs_offset(rgs, i);
	if (offset >= rgs->len) {
		if (v == 0) {
			/* no-op change: no need to resize */
			return 0;
		}
		if (rgs_realloc_amortized(rgs, offset) == -1)
			return -1;
	}
	if (offset < 0) {
		abort();
	}
	rgs->sum -= rgs->body[offset];
	rgs->sum += v;
	rgs->body[offset] = v;
	return 0;
}

/* Add a size_t value to a Python list. Return -1 on failure. */
static inline int pylist_append_size_t(PyObject *list, size_t v)
{
	return pylist_append_owned(list, PyLong_FromSsize_t(v));
}

static PyObject *index_headrevsdiff(indexObject *self, PyObject *args)
{
	int begin, end;
	Py_ssize_t i, j;
	PyObject *heads_added = NULL;
	PyObject *heads_removed = NULL;
	PyObject *res = NULL;
	rgs rgs;
	rgs.body = NULL;

	if (!PyArg_ParseTuple(args, "ii", &begin, &end))
		goto bail;

	if (!rgs_alloc(&rgs, end))
		goto bail;

	heads_added = PyList_New(0);
	if (heads_added == NULL)
		goto bail;
	heads_removed = PyList_New(0);
	if (heads_removed == NULL)
		goto bail;

	for (i = end - 1; i >= begin; i--) {
		int parents[2];

		if (rgs_get(&rgs, i)) {
			if (rgs_set(&rgs, i, false) == -1) {
				goto bail;
			};
		} else {
			if (pylist_append_size_t(heads_added, i) == -1) {
				goto bail;
			}
		}

		if (index_get_parents(self, i, parents, i) < 0)
			goto bail;
		for (j = 0; j < 2; j++) {
			if (parents[j] >= 0)
				if (rgs_set(&rgs, parents[j], true) == -1) {
					goto bail;
				}
		}
	}

	while (rgs.sum) {
		int parents[2];

		if (rgs_get(&rgs, i)) {
			if (rgs_set(&rgs, i, false) == -1) {
				goto bail;
			}
			if (pylist_append_size_t(heads_removed, i) == -1) {
				goto bail;
			}
		}

		if (index_get_parents(self, i, parents, i) < 0)
			goto bail;
		for (j = 0; j < 2; j++) {
			if (parents[j] >= 0)
				if (rgs_set(&rgs, parents[j], false) == -1) {
					/* can't actually fail */
					goto bail;
				}
		}
		i--;
	}

	if (begin == 0 && end > 0) {
		if (pylist_append_size_t(heads_removed, -1) == -1) {
			goto bail;
		}
	}

	if (!(res = PyTuple_Pack(2, heads_removed, heads_added))) {
		goto bail;
	}

	Py_XDECREF(heads_removed);
	Py_XDECREF(heads_added);
	free(rgs.body);
	return res;
bail:
	Py_XDECREF(heads_added);
	Py_XDECREF(heads_removed);
	free(rgs.body);
	return NULL;
}

/**
 * Obtain the base revision index entry.
 *
 * Callers must ensure that rev >= 0 or illegal memory access may occur.
 */
static inline int index_baserev(indexObject *self, int rev)
{
	const char *data;
	int result;

	data = index_deref(self, rev);
	if (data == NULL)
		return -2;

	if (self->format_version == format_v1) {
		result = getbe32(data + entry_v1_offset_base_rev);
	} else if (self->format_version == format_v2) {
		result = getbe32(data + entry_v2_offset_base_rev);
	} else if (self->format_version == format_cl2) {
		return rev;
	} else {
		raise_revlog_error();
		return -1;
	}

	if (result > rev) {
		PyErr_Format(
		    PyExc_ValueError,
		    "corrupted revlog, revision base above revision: %d, %d",
		    rev, result);
		return -2;
	}
	if (result < -1) {
		PyErr_Format(
		    PyExc_ValueError,
		    "corrupted revlog, revision base out of range: %d, %d", rev,
		    result);
		return -2;
	}
	return result;
}

/**
 * Find if a revision is a snapshot or not
 *
 * Only relevant for sparse-revlog case.
 * Callers must ensure that rev is in a valid range.
 */
static int index_issnapshotrev(indexObject *self, Py_ssize_t rev)
{
	int ps[2];
	int b;
	Py_ssize_t base;
	while (rev >= 0) {
		base = (Py_ssize_t)index_baserev(self, rev);
		if (base == rev) {
			base = -1;
		}
		if (base == -2) {
			assert(PyErr_Occurred());
			return -1;
		}
		if (base == -1) {
			return 1;
		}
		if (index_get_parents(self, rev, ps, (int)rev) < 0) {
			assert(PyErr_Occurred());
			return -1;
		};
		while ((index_get_length(self, ps[0]) == 0) && ps[0] >= 0) {
			b = index_baserev(self, ps[0]);
			if (b == ps[0]) {
				break;
			}
			ps[0] = b;
		}
		while ((index_get_length(self, ps[1]) == 0) && ps[1] >= 0) {
			b = index_baserev(self, ps[1]);
			if (b == ps[1]) {
				break;
			}
			ps[1] = b;
		}
		if (base == ps[0] || base == ps[1]) {
			return 0;
		}
		rev = base;
	}
	return rev == -1;
}

static PyObject *index_issnapshot(indexObject *self, PyObject *value)
{
	long rev;
	int issnap;
	Py_ssize_t length = index_length(self);

	if (!pylong_to_long(value, &rev)) {
		return NULL;
	}
	if (rev < -1 || rev >= length) {
		PyErr_Format(PyExc_ValueError, "revlog index out of range: %ld",
		             rev);
		return NULL;
	};
	issnap = index_issnapshotrev(self, (Py_ssize_t)rev);
	if (issnap < 0) {
		return NULL;
	};
	return PyBool_FromLong((long)issnap);
}

static PyObject *index_findsnapshots(indexObject *self, PyObject *args)
{
	Py_ssize_t start_rev;
	Py_ssize_t end_rev;
	PyObject *cache;
	Py_ssize_t base;
	Py_ssize_t rev;
	PyObject *key = NULL;
	PyObject *value = NULL;
	const Py_ssize_t length = index_length(self);
	if (!PyArg_ParseTuple(args, "O!nn", &PyDict_Type, &cache, &start_rev,
	                      &end_rev)) {
		return NULL;
	}
	end_rev += 1;
	if (end_rev > length) {
		end_rev = length;
	}
	if (start_rev < 0) {
		start_rev = 0;
	}
	for (rev = start_rev; rev < end_rev; rev++) {
		int issnap;
		PyObject *allvalues = NULL;
		issnap = index_issnapshotrev(self, rev);
		if (issnap < 0) {
			goto bail;
		}
		if (issnap == 0) {
			continue;
		}
		base = (Py_ssize_t)index_baserev(self, rev);
		if (base == rev) {
			base = -1;
		}
		if (base == -2) {
			assert(PyErr_Occurred());
			goto bail;
		}
		key = PyLong_FromSsize_t(base);
		allvalues = PyDict_GetItem(cache, key);
		if (allvalues == NULL && PyErr_Occurred()) {
			goto bail;
		}
		if (allvalues == NULL) {
			int r;
			allvalues = PySet_New(0);
			if (!allvalues) {
				goto bail;
			}
			r = PyDict_SetItem(cache, key, allvalues);
			Py_DECREF(allvalues);
			if (r < 0) {
				goto bail;
			}
		}
		value = PyLong_FromSsize_t(rev);
		if (PySet_Add(allvalues, value)) {
			goto bail;
		}
		Py_CLEAR(key);
		Py_CLEAR(value);
	}
	Py_RETURN_NONE;
bail:
	Py_XDECREF(key);
	Py_XDECREF(value);
	return NULL;
}

static PyObject *index_deltachain(indexObject *self, PyObject *args)
{
	int rev, generaldelta;
	PyObject *stoparg;
	int stoprev, iterrev, baserev = -1;
	int stopped;
	PyObject *chain = NULL, *result = NULL;
	const Py_ssize_t length = index_length(self);

	if (!PyArg_ParseTuple(args, "iOi", &rev, &stoparg, &generaldelta)) {
		return NULL;
	}

	if (PyLong_Check(stoparg)) {
		stoprev = (int)PyLong_AsLong(stoparg);
		if (stoprev == -1 && PyErr_Occurred()) {
			return NULL;
		}
	} else if (stoparg == Py_None) {
		stoprev = -2;
	} else {
		PyErr_SetString(PyExc_ValueError,
		                "stoprev must be integer or None");
		return NULL;
	}

	if (rev < 0 || rev >= length) {
		PyErr_SetString(PyExc_ValueError, "revlog index out of range");
		return NULL;
	}

	chain = PyList_New(0);
	if (chain == NULL) {
		return NULL;
	}

	baserev = index_baserev(self, rev);

	/* This should never happen. */
	if (baserev <= -2) {
		/* Error should be set by index_deref() */
		assert(PyErr_Occurred());
		goto bail;
	}

	iterrev = rev;

	while (iterrev != baserev && iterrev != stoprev) {
		if (pylist_append_owned(chain, PyLong_FromLong(iterrev))) {
			goto bail;
		}

		if (generaldelta) {
			iterrev = baserev;
		} else {
			iterrev--;
		}

		if (iterrev < 0) {
			break;
		}

		if (iterrev >= length) {
			PyErr_SetString(PyExc_IndexError,
			                "revision outside index");
			return NULL;
		}

		baserev = index_baserev(self, iterrev);

		/* This should never happen. */
		if (baserev <= -2) {
			/* Error should be set by index_deref() */
			assert(PyErr_Occurred());
			goto bail;
		}
	}

	if (iterrev == stoprev) {
		stopped = 1;
	} else {
		if (pylist_append_owned(chain, PyLong_FromLong(iterrev))) {
			goto bail;
		}

		stopped = 0;
	}

	if (PyList_Reverse(chain)) {
		goto bail;
	}

	result = Py_BuildValue("OO", chain, stopped ? Py_True : Py_False);
	Py_DECREF(chain);
	return result;

bail:
	Py_DECREF(chain);
	return NULL;
}

static inline int64_t
index_segment_span(indexObject *self, Py_ssize_t start_rev, Py_ssize_t end_rev)
{
	int64_t start_offset;
	int64_t end_offset;
	int end_size;
	start_offset = index_get_start(self, start_rev);
	if (start_offset < 0) {
		return -1;
	}
	end_offset = index_get_start(self, end_rev);
	if (end_offset < 0) {
		return -1;
	}
	end_size = index_get_length(self, end_rev);
	if (end_size < 0) {
		return -1;
	}
	if (end_offset < start_offset) {
		PyErr_Format(PyExc_ValueError,
		             "corrupted revlog index: inconsistent offset "
		             "between revisions (%zd) and (%zd)",
		             start_rev, end_rev);
		return -1;
	}
	return (end_offset - start_offset) + (int64_t)end_size;
}

/* returns endidx so that revs[startidx:endidx] has no empty trailing revs */
static Py_ssize_t trim_endidx(indexObject *self, const Py_ssize_t *revs,
                              Py_ssize_t startidx, Py_ssize_t endidx)
{
	int length;
	while (endidx > 1 && endidx > startidx) {
		length = index_get_length(self, revs[endidx - 1]);
		if (length < 0) {
			return -1;
		}
		if (length != 0) {
			break;
		}
		endidx -= 1;
	}
	return endidx;
}

struct Gap {
	int64_t size;
	Py_ssize_t idx;
};

static int gap_compare(const void *left, const void *right)
{
	const struct Gap *l_left = ((const struct Gap *)left);
	const struct Gap *l_right = ((const struct Gap *)right);
	if (l_left->size < l_right->size) {
		return -1;
	} else if (l_left->size > l_right->size) {
		return 1;
	}
	return 0;
}
static int Py_ssize_t_compare(const void *left, const void *right)
{
	const Py_ssize_t l_left = *(const Py_ssize_t *)left;
	const Py_ssize_t l_right = *(const Py_ssize_t *)right;
	if (l_left < l_right) {
		return -1;
	} else if (l_left > l_right) {
		return 1;
	}
	return 0;
}

static PyObject *index_slicechunktodensity(indexObject *self, PyObject *args)
{
	/* method arguments */
	PyObject *list_revs = NULL; /* revisions in the chain */
	double targetdensity = 0;   /* min density to achieve */
	Py_ssize_t mingapsize = 0;  /* threshold to ignore gaps */

	/* other core variables */
	Py_ssize_t idxlen = index_length(self);
	Py_ssize_t i;            /* used for various iteration */
	PyObject *result = NULL; /* the final return of the function */

	/* generic information about the delta chain being slice */
	Py_ssize_t num_revs = 0;    /* size of the full delta chain */
	Py_ssize_t *revs = NULL;    /* native array of revision in the chain */
	int64_t chainpayload = 0;   /* sum of all delta in the chain */
	int64_t deltachainspan = 0; /* distance from first byte to last byte */

	/* variable used for slicing the delta chain */
	int64_t readdata = 0; /* amount of data currently planned to be read */
	double density = 0;   /* ration of payload data compared to read ones */
	int64_t previous_end;
	struct Gap *gaps = NULL; /* array of notable gap in the chain */
	Py_ssize_t num_gaps =
	    0; /* total number of notable gap recorded so far */
	Py_ssize_t *selected_indices = NULL; /* indices of gap skipped over */
	Py_ssize_t num_selected = 0;         /* number of gaps skipped */
	PyObject *allchunks = NULL;          /* all slices */
	Py_ssize_t previdx;

	/* parsing argument */
	if (!PyArg_ParseTuple(args, "O!dn", &PyList_Type, &list_revs,
	                      &targetdensity, &mingapsize)) {
		goto bail;
	}

	/* If the delta chain contains a single element, we do not need slicing
	 */
	num_revs = PyList_GET_SIZE(list_revs);
	if (num_revs <= 1) {
		result = PyTuple_Pack(1, list_revs);
		goto done;
	}

	/* Turn the python list into a native integer array (for efficiency) */
	revs = (Py_ssize_t *)calloc(num_revs, sizeof(Py_ssize_t));
	if (revs == NULL) {
		PyErr_NoMemory();
		goto bail;
	}
	for (i = 0; i < num_revs; i++) {
		Py_ssize_t revnum =
		    PyLong_AsLong(PyList_GET_ITEM(list_revs, i));
		if (revnum == -1 && PyErr_Occurred()) {
			goto bail;
		}
		if (revnum < nullrev || revnum >= idxlen) {
			PyErr_Format(PyExc_IndexError,
			             "index out of range: %zd", revnum);
			goto bail;
		}
		revs[i] = revnum;
	}

	/* Compute and check various property of the unsliced delta chain */
	deltachainspan = index_segment_span(self, revs[0], revs[num_revs - 1]);
	if (deltachainspan < 0) {
		goto bail;
	}

	if (deltachainspan <= mingapsize) {
		result = PyTuple_Pack(1, list_revs);
		goto done;
	}
	chainpayload = 0;
	for (i = 0; i < num_revs; i++) {
		int tmp = index_get_length(self, revs[i]);
		if (tmp < 0) {
			goto bail;
		}
		chainpayload += tmp;
	}

	readdata = deltachainspan;
	density = 1.0;

	if (0 < deltachainspan) {
		density = (double)chainpayload / (double)deltachainspan;
	}

	if (density >= targetdensity) {
		result = PyTuple_Pack(1, list_revs);
		goto done;
	}

	/* if chain is too sparse, look for relevant gaps */
	gaps = (struct Gap *)calloc(num_revs, sizeof(struct Gap));
	if (gaps == NULL) {
		PyErr_NoMemory();
		goto bail;
	}

	previous_end = -1;
	for (i = 0; i < num_revs; i++) {
		int64_t revstart;
		int revsize;
		revstart = index_get_start(self, revs[i]);
		if (revstart < 0) {
			goto bail;
		};
		revsize = index_get_length(self, revs[i]);
		if (revsize < 0) {
			goto bail;
		};
		if (revsize == 0) {
			continue;
		}
		if (previous_end >= 0) {
			int64_t gapsize = revstart - previous_end;
			if (gapsize > mingapsize) {
				gaps[num_gaps].size = gapsize;
				gaps[num_gaps].idx = i;
				num_gaps += 1;
			}
		}
		previous_end = revstart + revsize;
	}
	if (num_gaps == 0) {
		result = PyTuple_Pack(1, list_revs);
		goto done;
	}
	qsort(gaps, num_gaps, sizeof(struct Gap), &gap_compare);

	/* Slice the largest gap first, they improve the density the most */
	selected_indices =
	    (Py_ssize_t *)malloc((num_gaps + 1) * sizeof(Py_ssize_t));
	if (selected_indices == NULL) {
		PyErr_NoMemory();
		goto bail;
	}

	for (i = num_gaps - 1; i >= 0; i--) {
		selected_indices[num_selected] = gaps[i].idx;
		readdata -= gaps[i].size;
		num_selected += 1;
		if (readdata <= 0) {
			density = 1.0;
		} else {
			density = (double)chainpayload / (double)readdata;
		}
		if (density >= targetdensity) {
			break;
		}
	}
	qsort(selected_indices, num_selected, sizeof(Py_ssize_t),
	      &Py_ssize_t_compare);

	/* create the resulting slice */
	allchunks = PyList_New(0);
	if (allchunks == NULL) {
		goto bail;
	}
	previdx = 0;
	selected_indices[num_selected] = num_revs;
	for (i = 0; i <= num_selected; i++) {
		Py_ssize_t idx = selected_indices[i];
		Py_ssize_t endidx = trim_endidx(self, revs, previdx, idx);
		if (endidx < 0) {
			goto bail;
		}
		if (previdx < endidx) {
			PyObject *chunk =
			    PyList_GetSlice(list_revs, previdx, endidx);
			if (pylist_append_owned(allchunks, chunk) == -1) {
				goto bail;
			}
		}
		previdx = idx;
	}
	result = allchunks;
	goto done;

bail:
	Py_XDECREF(allchunks);
done:
	free(revs);
	free(gaps);
	free(selected_indices);
	return result;
}

static inline int nt_level(const char *node, Py_ssize_t level)
{
	int v = node[level >> 1];
	if (!(level & 1))
		v >>= 4;
	return v & 0xf;
}

/*
 * Return values:
 *
 *   -4: match is ambiguous (multiple candidates)
 *   -2: not found
 * rest: valid rev
 */
static int nt_find(nodetree *self, const char *node, Py_ssize_t nodelen,
                   int hex)
{
	int (*getnybble)(const char *, Py_ssize_t) = hex ? hexdigit : nt_level;
	int level, maxlevel, off;

	/* If the input is binary, do a fast check for the nullid first. */
	if (!hex && nodelen == self->nodelen && node[0] == '\0' &&
	    node[1] == '\0' && memcmp(node, nullid, self->nodelen) == 0)
		return -1;

	if (hex)
		maxlevel = nodelen;
	else
		maxlevel = 2 * nodelen;
	if (maxlevel > 2 * self->nodelen)
		maxlevel = 2 * self->nodelen;

	for (level = off = 0; level < maxlevel; level++) {
		int k = getnybble(node, level);
		nodetreenode *n = &self->nodes[off];
		int v = n->children[k];

		if (v < 0) {
			const char *n;
			Py_ssize_t i;

			v = -(v + 2);
			n = index_node(self->index, v);
			if (n == NULL)
				return -2;
			for (i = level; i < maxlevel; i++)
				if (getnybble(node, i) != nt_level(n, i))
					return -2;
			return v;
		}
		if (v == 0)
			return -2;
		off = v;
	}
	/* multiple matches against an ambiguous prefix */
	return -4;
}

static int nt_new(nodetree *self)
{
	if (self->length == self->capacity) {
		size_t newcapacity;
		nodetreenode *newnodes;
		newcapacity = self->capacity * 2;
		if (newcapacity >= SIZE_MAX / sizeof(nodetreenode)) {
			PyErr_SetString(PyExc_MemoryError,
			                "overflow in nt_new");
			return -1;
		}
		newnodes =
		    realloc(self->nodes, newcapacity * sizeof(nodetreenode));
		if (newnodes == NULL) {
			PyErr_SetString(PyExc_MemoryError, "out of memory");
			return -1;
		}
		self->capacity = newcapacity;
		self->nodes = newnodes;
		memset(&self->nodes[self->length], 0,
		       sizeof(nodetreenode) * (self->capacity - self->length));
	}
	return self->length++;
}

static int nt_insert(nodetree *self, const char *node, int rev)
{
	int level = 0;
	int off = 0;

	while (level < 2 * self->nodelen) {
		int k = nt_level(node, level);
		nodetreenode *n;
		int v;

		n = &self->nodes[off];
		v = n->children[k];

		if (v == 0) {
			n->children[k] = -rev - 2;
			return 0;
		}
		if (v < 0) {
			const char *oldnode =
			    index_node_existing(self->index, -(v + 2));
			int noff;

			if (oldnode == NULL)
				return -1;
			if (!memcmp(oldnode, node, self->nodelen)) {
				n->children[k] = -rev - 2;
				return 0;
			}
			noff = nt_new(self);
			if (noff == -1)
				return -1;
			/* self->nodes may have been changed by realloc */
			self->nodes[off].children[k] = noff;
			off = noff;
			n = &self->nodes[off];
			n->children[nt_level(oldnode, ++level)] = v;
			if (level > self->depth)
				self->depth = level;
			self->splits += 1;
		} else {
			level += 1;
			off = v;
		}
	}

	return -1;
}

static PyObject *ntobj_insert(nodetreeObject *self, PyObject *args)
{
	Py_ssize_t rev;
	const char *node;
	Py_ssize_t length;
	if (!PyArg_ParseTuple(args, "n", &rev))
		return NULL;
	length = index_length(self->nt.index);
	if (rev < 0 || rev >= length) {
		PyErr_SetString(PyExc_ValueError, "revlog index out of range");
		return NULL;
	}
	node = index_node_existing(self->nt.index, rev);
	if (nt_insert(&self->nt, node, (int)rev) == -1)
		return NULL;
	Py_RETURN_NONE;
}

static int nt_delete_node(nodetree *self, const char *node)
{
	/* rev==-2 happens to get encoded as 0, which is interpreted as not set
	 */
	return nt_insert(self, node, -2);
}

static int nt_init(nodetree *self, indexObject *index, unsigned capacity)
{
	/* Initialize before overflow-checking to avoid nt_dealloc() crash. */
	self->nodes = NULL;

	self->index = index;
	/* The input capacity is in terms of revisions, while the field is in
	 * terms of nodetree nodes. */
	self->capacity = (capacity < 4 ? 4 : capacity / 2);
	self->nodelen = index->nodelen;
	self->depth = 0;
	self->splits = 0;
	if (self->capacity > SIZE_MAX / sizeof(nodetreenode)) {
		PyErr_SetString(PyExc_ValueError, "overflow in init_nt");
		return -1;
	}
	self->nodes = calloc(self->capacity, sizeof(nodetreenode));
	if (self->nodes == NULL) {
		PyErr_NoMemory();
		return -1;
	}
	self->length = 1;
	return 0;
}

static int ntobj_init(nodetreeObject *self, PyObject *args)
{
	PyObject *index;
	unsigned capacity;
	if (!PyArg_ParseTuple(args, "O!I", &HgRevlogIndex_Type, &index,
	                      &capacity))
		return -1;
	Py_INCREF(index);
	return nt_init(&self->nt, (indexObject *)index, capacity);
}

static int nt_partialmatch(nodetree *self, const char *node, Py_ssize_t nodelen)
{
	return nt_find(self, node, nodelen, 1);
}

/*
 * Find the length of the shortest unique prefix of node.
 *
 * Return values:
 *
 *   -3: error (exception set)
 *   -2: not found (no exception set)
 * rest: length of shortest prefix
 */
static int nt_shortest(nodetree *self, const char *node)
{
	int level, off;

	for (level = off = 0; level < 2 * self->nodelen; level++) {
		int k, v;
		nodetreenode *n = &self->nodes[off];
		k = nt_level(node, level);
		v = n->children[k];
		if (v < 0) {
			const char *n;
			v = -(v + 2);
			n = index_node_existing(self->index, v);
			if (n == NULL)
				return -3;
			if (memcmp(node, n, self->nodelen) != 0)
				/*
				 * Found a unique prefix, but it wasn't for the
				 * requested node (i.e the requested node does
				 * not exist).
				 */
				return -2;
			return level + 1;
		}
		if (v == 0)
			return -2;
		off = v;
	}
	/*
	 * The node was still not unique after 40 hex digits, so this won't
	 * happen. Also, if we get here, then there's a programming error in
	 * this file that made us insert a node longer than 40 hex digits.
	 */
	PyErr_SetString(PyExc_Exception, "broken node tree");
	return -3;
}

static PyObject *ntobj_shortest(nodetreeObject *self, PyObject *args)
{
	PyObject *val;
	char *node;
	int length;

	if (!PyArg_ParseTuple(args, "O", &val))
		return NULL;
	if (node_check(self->nt.nodelen, val, &node) == -1)
		return NULL;

	length = nt_shortest(&self->nt, node);
	if (length == -3)
		return NULL;
	if (length == -2) {
		raise_revlog_error();
		return NULL;
	}
	return PyLong_FromLong(length);
}

static void nt_dealloc(nodetree *self)
{
	free(self->nodes);
	self->nodes = NULL;
}

static void ntobj_dealloc(nodetreeObject *self)
{
	Py_XDECREF(self->nt.index);
	nt_dealloc(&self->nt);
	PyObject_Del(self);
}

static PyMethodDef ntobj_methods[] = {
    {"insert", (PyCFunction)ntobj_insert, METH_VARARGS,
     "insert an index entry"},
    {"shortest", (PyCFunction)ntobj_shortest, METH_VARARGS,
     "find length of shortest hex nodeid of a binary ID"},
    {NULL} /* Sentinel */
};

static PyTypeObject nodetreeType = {
    PyVarObject_HEAD_INIT(NULL, 0) /* header */
    "parsers.nodetree",            /* tp_name */
    sizeof(nodetreeObject),        /* tp_basicsize */
    0,                             /* tp_itemsize */
    (destructor)ntobj_dealloc,     /* tp_dealloc */
    0,                             /* tp_print */
    0,                             /* tp_getattr */
    0,                             /* tp_setattr */
    0,                             /* tp_compare */
    0,                             /* tp_repr */
    0,                             /* tp_as_number */
    0,                             /* tp_as_sequence */
    0,                             /* tp_as_mapping */
    0,                             /* tp_hash */
    0,                             /* tp_call */
    0,                             /* tp_str */
    0,                             /* tp_getattro */
    0,                             /* tp_setattro */
    0,                             /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT,            /* tp_flags */
    "nodetree",                    /* tp_doc */
    0,                             /* tp_traverse */
    0,                             /* tp_clear */
    0,                             /* tp_richcompare */
    0,                             /* tp_weaklistoffset */
    0,                             /* tp_iter */
    0,                             /* tp_iternext */
    ntobj_methods,                 /* tp_methods */
    0,                             /* tp_members */
    0,                             /* tp_getset */
    0,                             /* tp_base */
    0,                             /* tp_dict */
    0,                             /* tp_descr_get */
    0,                             /* tp_descr_set */
    0,                             /* tp_dictoffset */
    (initproc)ntobj_init,          /* tp_init */
    0,                             /* tp_alloc */
};

static int index_init_nt(indexObject *self)
{
	if (!self->ntinitialized) {
		if (nt_init(&self->nt, self, (int)self->length) == -1) {
			nt_dealloc(&self->nt);
			return -1;
		}
		if (nt_insert(&self->nt, nullid, -1) == -1) {
			nt_dealloc(&self->nt);
			return -1;
		}
		self->ntinitialized = 1;
		self->ntrev = (int)index_length(self);
		self->ntlookups = 1;
		self->ntmisses = 0;
	}
	return 0;
}

/*
 * Return values:
 *
 *   -3: error (exception set)
 *   -2: not found (no exception set)
 * rest: valid rev
 */
static int index_find_node(indexObject *self, const char *node)
{
	int rev;

	if (index_init_nt(self) == -1)
		return -3;

	self->ntlookups++;
	rev = nt_find(&self->nt, node, self->nodelen, 0);
	if (rev >= -1)
		return rev;

	/*
	 * For the first handful of lookups, we scan the entire index,
	 * and cache only the matching nodes. This optimizes for cases
	 * like "hg tip", where only a few nodes are accessed.
	 *
	 * After that, we cache every node we visit, using a single
	 * scan amortized over multiple lookups.  This gives the best
	 * bulk performance, e.g. for "hg log".
	 */
	if (self->ntmisses++ < 4) {
		for (rev = self->ntrev - 1; rev >= 0; rev--) {
			const char *n = index_node_existing(self, rev);
			if (n == NULL)
				return -3;
			if (memcmp(node, n, self->nodelen) == 0) {
				if (nt_insert(&self->nt, n, rev) == -1)
					return -3;
				break;
			}
		}
	} else {
		for (rev = self->ntrev - 1; rev >= 0; rev--) {
			const char *n = index_node_existing(self, rev);
			if (n == NULL)
				return -3;
			if (nt_insert(&self->nt, n, rev) == -1) {
				self->ntrev = rev + 1;
				return -3;
			}
			if (memcmp(node, n, self->nodelen) == 0) {
				break;
			}
		}
		self->ntrev = rev;
	}

	if (rev >= 0)
		return rev;
	return -2;
}

static PyObject *index_getitem(indexObject *self, PyObject *value)
{
	char *node;
	int rev;

	if (PyLong_Check(value)) {
		long idx;
		if (!pylong_to_long(value, &idx)) {
			return NULL;
		}
		return index_get(self, idx);
	}

	if (node_check(self->nodelen, value, &node) == -1)
		return NULL;
	rev = index_find_node(self, node);
	if (rev >= -1)
		return PyLong_FromLong(rev);
	if (rev == -2)
		raise_revlog_error();
	return NULL;
}

/*
 * Fully populate the radix tree.
 */
static int index_populate_nt(indexObject *self)
{
	int rev;
	if (self->ntrev > 0) {
		for (rev = self->ntrev - 1; rev >= 0; rev--) {
			const char *n = index_node_existing(self, rev);
			if (n == NULL)
				return -1;
			if (nt_insert(&self->nt, n, rev) == -1)
				return -1;
		}
		self->ntrev = -1;
	}
	return 0;
}

static PyObject *index_partialmatch(indexObject *self, PyObject *args)
{
	const char *fullnode;
	Py_ssize_t nodelen;
	char *node;
	int rev, i;

	if (!PyArg_ParseTuple(args, "y#", &node, &nodelen))
		return NULL;

	if (nodelen < 1) {
		PyErr_SetString(PyExc_ValueError, "key too short");
		return NULL;
	}

	if (nodelen > 2 * self->nodelen) {
		PyErr_SetString(PyExc_ValueError, "key too long");
		return NULL;
	}

	for (i = 0; i < nodelen; i++)
		hexdigit(node, i);
	if (PyErr_Occurred()) {
		/* input contains non-hex characters */
		PyErr_Clear();
		Py_RETURN_NONE;
	}

	if (index_init_nt(self) == -1)
		return NULL;
	if (index_populate_nt(self) == -1)
		return NULL;
	rev = nt_partialmatch(&self->nt, node, nodelen);

	switch (rev) {
	case -4:
		raise_revlog_error();
		return NULL;
	case -2:
		Py_RETURN_NONE;
	case -1:
		return PyBytes_FromStringAndSize(nullid, self->nodelen);
	}

	fullnode = index_node_existing(self, rev);
	if (fullnode == NULL) {
		return NULL;
	}
	return PyBytes_FromStringAndSize(fullnode, self->nodelen);
}

static PyObject *index_shortest(indexObject *self, PyObject *args)
{
	PyObject *val;
	char *node;
	int length;

	if (!PyArg_ParseTuple(args, "O", &val))
		return NULL;
	if (node_check(self->nodelen, val, &node) == -1)
		return NULL;

	self->ntlookups++;
	if (index_init_nt(self) == -1)
		return NULL;
	if (index_populate_nt(self) == -1)
		return NULL;
	length = nt_shortest(&self->nt, node);
	if (length == -3)
		return NULL;
	if (length == -2) {
		raise_revlog_error();
		return NULL;
	}
	return PyLong_FromLong(length);
}

static PyObject *index_m_get(indexObject *self, PyObject *args)
{
	PyObject *val;
	char *node;
	int rev;

	if (!PyArg_ParseTuple(args, "O", &val))
		return NULL;
	if (node_check(self->nodelen, val, &node) == -1)
		return NULL;
	rev = index_find_node(self, node);
	if (rev == -3)
		return NULL;
	if (rev == -2)
		Py_RETURN_NONE;
	return PyLong_FromLong(rev);
}

static int index_contains(indexObject *self, PyObject *value)
{
	char *node;

	if (PyLong_Check(value)) {
		long rev;
		if (!pylong_to_long(value, &rev)) {
			return -1;
		}
		return rev >= -1 && rev < index_length(self);
	}

	if (node_check(self->nodelen, value, &node) == -1)
		return -1;

	switch (index_find_node(self, node)) {
	case -3:
		return -1;
	case -2:
		return 0;
	default:
		return 1;
	}
}

static PyObject *index_m_has_node(indexObject *self, PyObject *args)
{
	int ret = index_contains(self, args);
	if (ret < 0)
		return NULL;
	return PyBool_FromLong((long)ret);
}

static PyObject *index_m_rev(indexObject *self, PyObject *val)
{
	char *node;
	int rev;

	if (node_check(self->nodelen, val, &node) == -1)
		return NULL;
	rev = index_find_node(self, node);
	if (rev >= -1)
		return PyLong_FromLong(rev);
	if (rev == -2)
		raise_revlog_error();
	return NULL;
}

typedef uint64_t bitmask;

/*
 * Given a disjoint set of revs, return all candidates for the
 * greatest common ancestor. In revset notation, this is the set
 * "heads(::a and ::b and ...)"
 */
static PyObject *find_gca_candidates(indexObject *self, const int *revs,
                                     int revcount)
{
	const bitmask allseen = (1ull << revcount) - 1;
	const bitmask poison = 1ull << revcount;
	PyObject *gca = PyList_New(0);
	int i, v, interesting;
	int maxrev = -1;
	bitmask sp;
	bitmask *seen;

	if (gca == NULL)
		return PyErr_NoMemory();

	for (i = 0; i < revcount; i++) {
		if (revs[i] > maxrev)
			maxrev = revs[i];
	}

	seen = calloc(sizeof(*seen), maxrev + 1);
	if (seen == NULL) {
		Py_DECREF(gca);
		return PyErr_NoMemory();
	}

	for (i = 0; i < revcount; i++)
		seen[revs[i]] = 1ull << i;

	interesting = revcount;

	for (v = maxrev; v >= 0 && interesting; v--) {
		bitmask sv = seen[v];
		int parents[2];

		if (!sv)
			continue;

		if (sv < poison) {
			interesting -= 1;
			if (sv == allseen) {
				if (pylist_append_owned(
				        gca, PyLong_FromLong(v)) == -1) {
					goto bail;
				}
				sv |= poison;
				for (i = 0; i < revcount; i++) {
					if (revs[i] == v)
						goto done;
				}
			}
		}
		if (index_get_parents(self, v, parents, maxrev) < 0)
			goto bail;

		for (i = 0; i < 2; i++) {
			int p = parents[i];
			if (p == -1)
				continue;
			sp = seen[p];
			if (sv < poison) {
				if (sp == 0) {
					seen[p] = sv;
					interesting++;
				} else if (sp != sv)
					seen[p] |= sv;
			} else {
				if (sp && sp < poison)
					interesting--;
				seen[p] = sv;
			}
		}
	}

done:
	free(seen);
	return gca;
bail:
	free(seen);
	Py_XDECREF(gca);
	return NULL;
}

/*
 * Given a disjoint set of revs, return the subset with the longest
 * path to the root.
 */
static PyObject *find_deepest(indexObject *self, PyObject *revs)
{
	const Py_ssize_t revcount = PyList_GET_SIZE(revs);
	static const Py_ssize_t capacity = 24;
	int *depth, *interesting = NULL;
	int i, j, v, ninteresting;
	PyObject *dict = NULL, *keys = NULL;
	long *seen = NULL;
	int maxrev = -1;
	long final;

	if (revcount > capacity) {
		PyErr_Format(PyExc_OverflowError,
		             "bitset size (%ld) > capacity (%ld)",
		             (long)revcount, (long)capacity);
		return NULL;
	}

	for (i = 0; i < revcount; i++) {
		int n = (int)PyLong_AsLong(PyList_GET_ITEM(revs, i));
		if (n > maxrev)
			maxrev = n;
	}

	depth = calloc(sizeof(*depth), maxrev + 1);
	if (depth == NULL)
		return PyErr_NoMemory();

	seen = calloc(sizeof(*seen), maxrev + 1);
	if (seen == NULL) {
		PyErr_NoMemory();
		goto bail;
	}

	interesting = calloc(sizeof(*interesting), ((size_t)1) << revcount);
	if (interesting == NULL) {
		PyErr_NoMemory();
		goto bail;
	}

	if (PyList_Sort(revs) == -1)
		goto bail;

	for (i = 0; i < revcount; i++) {
		int n = (int)PyLong_AsLong(PyList_GET_ITEM(revs, i));
		long b = 1l << i;
		depth[n] = 1;
		seen[n] = b;
		interesting[b] = 1;
	}

	/* invariant: ninteresting is the number of non-zero entries in
	 * interesting. */
	ninteresting = (int)revcount;

	for (v = maxrev; v >= 0 && ninteresting > 1; v--) {
		int dv = depth[v];
		int parents[2];
		long sv;

		if (dv == 0)
			continue;

		sv = seen[v];
		if (index_get_parents(self, v, parents, maxrev) < 0)
			goto bail;

		for (i = 0; i < 2; i++) {
			int p = parents[i];
			long sp;
			int dp;

			if (p == -1)
				continue;

			dp = depth[p];
			sp = seen[p];
			if (dp <= dv) {
				depth[p] = dv + 1;
				if (sp != sv) {
					interesting[sv] += 1;
					seen[p] = sv;
					if (sp) {
						interesting[sp] -= 1;
						if (interesting[sp] == 0)
							ninteresting -= 1;
					}
				}
			} else if (dv == dp - 1) {
				long nsp = sp | sv;
				if (nsp == sp)
					continue;
				seen[p] = nsp;
				interesting[sp] -= 1;
				if (interesting[sp] == 0)
					ninteresting -= 1;
				if (interesting[nsp] == 0)
					ninteresting += 1;
				interesting[nsp] += 1;
			}
		}
		interesting[sv] -= 1;
		if (interesting[sv] == 0)
			ninteresting -= 1;
	}

	final = 0;
	j = ninteresting;
	for (i = 0; i < (int)(2 << revcount) && j > 0; i++) {
		if (interesting[i] == 0)
			continue;
		final |= i;
		j -= 1;
	}
	if (final == 0) {
		keys = PyList_New(0);
		goto bail;
	}

	dict = PyDict_New();
	if (dict == NULL)
		goto bail;

	for (i = 0; i < revcount; i++) {
		PyObject *key;

		if ((final & (1 << i)) == 0)
			continue;

		key = PyList_GET_ITEM(revs, i);
		Py_INCREF(key);
		Py_INCREF(Py_None);
		if (PyDict_SetItem(dict, key, Py_None) == -1) {
			Py_DECREF(key);
			Py_DECREF(Py_None);
			goto bail;
		}
	}

	keys = PyDict_Keys(dict);

bail:
	free(depth);
	free(seen);
	free(interesting);
	Py_XDECREF(dict);

	return keys;
}

/*
 * Given a (possibly overlapping) set of revs, return all the
 * common ancestors heads: heads(::args[0] and ::a[1] and ...)
 */
static PyObject *index_commonancestorsheads(indexObject *self, PyObject *args)
{
	PyObject *ret = NULL;
	Py_ssize_t argcount, i, len;
	bitmask repeat = 0;
	int revcount = 0;
	int *revs;

	argcount = PySequence_Length(args);
	revs = PyMem_Malloc(argcount * sizeof(*revs));
	if (argcount > 0 && revs == NULL)
		return PyErr_NoMemory();
	len = index_length(self);

	for (i = 0; i < argcount; i++) {
		static const int capacity = 24;
		PyObject *obj = PySequence_GetItem(args, i);
		bitmask x;
		long val;

		if (!PyLong_Check(obj)) {
			PyErr_SetString(PyExc_TypeError,
			                "arguments must all be ints");
			Py_DECREF(obj);
			goto bail;
		}
		val = PyLong_AsLong(obj);
		Py_DECREF(obj);
		if (val == -1) {
			ret = PyList_New(0);
			goto done;
		}
		if (val < 0 || val >= len) {
			PyErr_SetString(PyExc_IndexError, "index out of range");
			goto bail;
		}
		/* this cheesy bloom filter lets us avoid some more
		 * expensive duplicate checks in the common set-is-disjoint
		 * case */
		x = 1ull << (val & 0x3f);
		if (repeat & x) {
			int k;
			for (k = 0; k < revcount; k++) {
				if (val == revs[k])
					goto duplicate;
			}
		} else
			repeat |= x;
		if (revcount >= capacity) {
			PyErr_Format(PyExc_OverflowError,
			             "bitset size (%d) > capacity (%d)",
			             revcount, capacity);
			goto bail;
		}
		revs[revcount++] = (int)val;
	duplicate:;
	}

	if (revcount == 0) {
		ret = PyList_New(0);
		goto done;
	}
	if (revcount == 1) {
		PyObject *obj;
		ret = PyList_New(1);
		if (ret == NULL)
			goto bail;
		obj = PyLong_FromLong(revs[0]);
		if (obj == NULL)
			goto bail;
		PyList_SET_ITEM(ret, 0, obj);
		goto done;
	}

	ret = find_gca_candidates(self, revs, revcount);
	if (ret == NULL)
		goto bail;

done:
	PyMem_Free(revs);
	return ret;

bail:
	PyMem_Free(revs);
	Py_XDECREF(ret);
	return NULL;
}

/*
 * Given a (possibly overlapping) set of revs, return the greatest
 * common ancestors: those with the longest path to the root.
 */
static PyObject *index_ancestors(indexObject *self, PyObject *args)
{
	PyObject *ret;
	PyObject *gca = index_commonancestorsheads(self, args);
	if (gca == NULL)
		return NULL;

	if (PyList_GET_SIZE(gca) <= 1) {
		return gca;
	}

	ret = find_deepest(self, gca);
	Py_DECREF(gca);
	return ret;
}

/*
 * Invalidate any trie entries introduced by added revs.
 */
static void index_invalidate_added(indexObject *self, Py_ssize_t start)
{
	Py_ssize_t i, len;

	len = self->length + self->new_length;
	i = start - self->length;
	if (i < 0)
		return;

	for (i = start; i < len; i++) {
		const char *node = index_node(self, i);
		nt_delete_node(&self->nt, node);
	}

	self->new_length = start - self->length;
}

/*
 * Delete a numeric range of revs, which must be at the end of the
 * range.
 */
static int index_slice_del(indexObject *self, PyObject *item)
{
	Py_ssize_t start, stop, step, slicelength;
	Py_ssize_t length = index_length(self) + 1;
	int ret = 0;

	if (PySlice_GetIndicesEx(item, length, &start, &stop, &step,
	                         &slicelength) < 0)
		return -1;

	if (slicelength <= 0)
		return 0;

	if ((step < 0 && start < stop) || (step > 0 && start > stop))
		stop = start;

	if (step < 0) {
		stop = start + 1;
		start = stop + step * (slicelength - 1) - 1;
		step = -step;
	}

	if (step != 1) {
		PyErr_SetString(PyExc_ValueError,
		                "revlog index delete requires step size of 1");
		return -1;
	}

	if (stop != length - 1) {
		PyErr_SetString(PyExc_IndexError,
		                "revlog index deletion indices are invalid");
		return -1;
	}

	if (start < self->length) {
		if (self->ntinitialized) {
			Py_ssize_t i;

			for (i = start; i < self->length; i++) {
				const char *node = index_node_existing(self, i);
				if (node == NULL)
					return -1;

				nt_delete_node(&self->nt, node);
			}
			if (self->new_length)
				index_invalidate_added(self, self->length);
			if (self->ntrev > start)
				self->ntrev = (int)start;
		} else if (self->new_length) {
			self->new_length = 0;
		}

		self->length = start;
		goto done;
	}

	if (self->ntinitialized) {
		index_invalidate_added(self, start);
		if (self->ntrev > start)
			self->ntrev = (int)start;
	} else {
		self->new_length = start - self->length;
	}
done:
	Py_CLEAR(self->headrevs);
	return ret;
}

/*
 * Supported ops:
 *
 * slice deletion
 * string assignment (extend node->rev mapping)
 * string deletion (shrink node->rev mapping)
 */
static int index_assign_subscript(indexObject *self, PyObject *item,
                                  PyObject *value)
{
	char *node;
	long rev;

	if (PySlice_Check(item) && value == NULL)
		return index_slice_del(self, item);

	if (node_check(self->nodelen, item, &node) == -1)
		return -1;

	if (value == NULL)
		return self->ntinitialized ? nt_delete_node(&self->nt, node)
		                           : 0;
	rev = PyLong_AsLong(value);
	if (rev > INT_MAX || rev < 0) {
		if (!PyErr_Occurred())
			PyErr_SetString(PyExc_ValueError, "rev out of range");
		return -1;
	}

	if (index_init_nt(self) == -1)
		return -1;
	return nt_insert(&self->nt, node, (int)rev);
}

/*
 * Find all RevlogNG entries in an index that has inline data. Update
 * the optional "offsets" table with those entries.
 */
static Py_ssize_t inline_scan(indexObject *self, const char **offsets)
{
	const char *data = (const char *)self->buf.buf;
	Py_ssize_t pos = 0;
	Py_ssize_t end = self->buf.len;
	long incr = self->entry_size;
	Py_ssize_t len = 0;

	while (pos + self->entry_size <= end && pos >= 0) {
		uint32_t comp_len, sidedata_comp_len = 0;
		/* 3rd element of header is length of compressed inline data */
		if (self->format_version == format_v1) {
			comp_len =
			    getbe32(data + pos + entry_v1_offset_comp_len);
			sidedata_comp_len = 0;
		} else if (self->format_version == format_v2) {
			comp_len =
			    getbe32(data + pos + entry_v2_offset_comp_len);
			sidedata_comp_len = getbe32(
			    data + pos + entry_v2_offset_sidedata_comp_len);
		} else {
			raise_revlog_error();
			return -1;
		}
		incr = self->entry_size + comp_len + sidedata_comp_len;
		if (offsets)
			offsets[len] = data + pos;
		len++;
		pos += incr;
	}

	if (pos != end) {
		if (!PyErr_Occurred())
			PyErr_SetString(PyExc_ValueError, "corrupt index file");
		return -1;
	}

	return len;
}

static int index_init(indexObject *self, PyObject *args, PyObject *kwargs)
{
	PyObject *data_obj, *inlined_obj;
	Py_ssize_t size;

	static char *kwlist[] = {"data", "inlined", "format", NULL};

	/* Initialize before argument-checking to avoid index_dealloc() crash.
	 */
	self->added = NULL;
	self->new_length = 0;
	self->added_length = 0;
	self->data = NULL;
	memset(&self->buf, 0, sizeof(self->buf));
	self->headrevs = NULL;
	self->filteredrevs = Py_None;
	Py_INCREF(Py_None);
	self->ntinitialized = 0;
	self->offsets = NULL;
	self->nodelen = 20;
	self->nullentry = NULL;
	self->rust_ext_compat = 0;
	self->format_version = format_v1;

	if (!PyArg_ParseTupleAndKeywords(args, kwargs, "OO|l", kwlist,
	                                 &data_obj, &inlined_obj,
	                                 &(self->format_version)))
		return -1;
	if (!PyObject_CheckBuffer(data_obj)) {
		PyErr_SetString(PyExc_TypeError,
		                "data does not support buffer interface");
		return -1;
	}
	if (self->nodelen < 20 || self->nodelen > (Py_ssize_t)sizeof(nullid)) {
		PyErr_SetString(PyExc_RuntimeError, "unsupported node size");
		return -1;
	}

	if (self->format_version == format_v1) {
		self->entry_size = v1_entry_size;
	} else if (self->format_version == format_v2) {
		self->entry_size = v2_entry_size;
	} else if (self->format_version == format_cl2) {
		self->entry_size = cl2_entry_size;
	}

	self->nullentry = Py_BuildValue(
	    "iiiiiiiy#iiBBi", 0, 0, 0, -1, -1, -1, -1, nullid, self->nodelen, 0,
	    0, comp_mode_inline, comp_mode_inline, rank_unknown);

	if (!self->nullentry)
		return -1;
	PyObject_GC_UnTrack(self->nullentry);

	if (PyObject_GetBuffer(data_obj, &self->buf, PyBUF_SIMPLE) == -1)
		return -1;
	size = self->buf.len;

	self->inlined = inlined_obj && PyObject_IsTrue(inlined_obj);
	self->data = data_obj;

	self->ntlookups = self->ntmisses = 0;
	self->ntrev = -1;
	Py_INCREF(self->data);

	if (self->inlined) {
		Py_ssize_t len = inline_scan(self, NULL);
		if (len == -1)
			goto bail;
		self->length = len;
	} else {
		if (size % self->entry_size) {
			PyErr_SetString(PyExc_ValueError, "corrupt index file");
			goto bail;
		}
		self->length = size / self->entry_size;
	}

	return 0;
bail:
	return -1;
}

static PyObject *index_nodemap(indexObject *self)
{
	Py_INCREF(self);
	return (PyObject *)self;
}

static void _index_clearcaches(indexObject *self)
{
	if (self->offsets) {
		PyMem_Free((void *)self->offsets);
		self->offsets = NULL;
	}
	if (self->ntinitialized) {
		nt_dealloc(&self->nt);
	}
	self->ntinitialized = 0;
	Py_CLEAR(self->headrevs);
}

static PyObject *index_clearcaches(indexObject *self)
{
	_index_clearcaches(self);
	self->ntrev = -1;
	self->ntlookups = self->ntmisses = 0;
	Py_RETURN_NONE;
}

static void index_dealloc(indexObject *self)
{
	_index_clearcaches(self);
	Py_XDECREF(self->filteredrevs);
	if (self->buf.buf) {
		PyBuffer_Release(&self->buf);
		memset(&self->buf, 0, sizeof(self->buf));
	}
	Py_XDECREF(self->data);
	PyMem_Free(self->added);
	Py_XDECREF(self->nullentry);
	PyObject_Del(self);
}

static PySequenceMethods index_sequence_methods = {
    (lenfunc)index_length,      /* sq_length */
    0,                          /* sq_concat */
    0,                          /* sq_repeat */
    (ssizeargfunc)index_get,    /* sq_item */
    0,                          /* sq_slice */
    0,                          /* sq_ass_item */
    0,                          /* sq_ass_slice */
    (objobjproc)index_contains, /* sq_contains */
};

static PyMappingMethods index_mapping_methods = {
    (lenfunc)index_length,                 /* mp_length */
    (binaryfunc)index_getitem,             /* mp_subscript */
    (objobjargproc)index_assign_subscript, /* mp_ass_subscript */
};

static PyMethodDef index_methods[] = {
    {"ancestors", (PyCFunction)index_ancestors, METH_VARARGS,
     "return the gca set of the given revs"},
    {"headrevsdiff", (PyCFunction)index_headrevsdiff, METH_VARARGS,
     "return the set of heads removed/added by a range of commits"},
    {"commonancestorsheads", (PyCFunction)index_commonancestorsheads,
     METH_VARARGS,
     "return the heads of the common ancestors of the given revs"},
    {"clearcaches", (PyCFunction)index_clearcaches, METH_NOARGS,
     "clear the index caches"},
    {"get", (PyCFunction)index_m_get, METH_VARARGS, "get an index entry"},
    {"get_rev", (PyCFunction)index_m_get, METH_VARARGS,
     "return `rev` associated with a node or None"},
    {"has_node", (PyCFunction)index_m_has_node, METH_O,
     "return True if the node exist in the index"},
    {"rev", (PyCFunction)index_m_rev, METH_O,
     "return `rev` associated with a node or raise RevlogError"},
    {"computephasesmapsets", (PyCFunction)compute_phases_map_sets, METH_VARARGS,
     "compute phases"},
    {"reachableroots2", (PyCFunction)reachableroots2, METH_VARARGS,
     "reachableroots"},
    {"replace_sidedata_info", (PyCFunction)index_replace_sidedata_info,
     METH_VARARGS, "replace an existing index entry with a new value"},
    {"headrevs", (PyCFunction)index_headrevs, METH_VARARGS,
     "get head revisions"}, /* Can do filtering since 3.2 */
    {"headrevsfiltered", (PyCFunction)index_headrevs, METH_VARARGS,
     "get filtered head revisions"}, /* Can always do filtering */
    {"issnapshot", (PyCFunction)index_issnapshot, METH_O,
     "True if the object is a snapshot"},
    {"findsnapshots", (PyCFunction)index_findsnapshots, METH_VARARGS,
     "Gather snapshot data in a cache dict"},
    {"deltachain", (PyCFunction)index_deltachain, METH_VARARGS,
     "determine revisions with deltas to reconstruct fulltext"},
    {"slicechunktodensity", (PyCFunction)index_slicechunktodensity,
     METH_VARARGS, "determine revisions with deltas to reconstruct fulltext"},
    {"append", (PyCFunction)index_append, METH_O, "append an index entry"},
    {"partialmatch", (PyCFunction)index_partialmatch, METH_VARARGS,
     "match a potentially ambiguous node ID"},
    {"shortest", (PyCFunction)index_shortest, METH_VARARGS,
     "find length of shortest hex nodeid of a binary ID"},
    {"stats", (PyCFunction)index_stats, METH_NOARGS, "stats for the index"},
    {"entry_binary", (PyCFunction)index_entry_binary, METH_O,
     "return an entry in binary form"},
    {"pack_header", (PyCFunction)index_pack_header, METH_VARARGS,
     "pack the revlog header information into binary"},
    {NULL} /* Sentinel */
};

static PyGetSetDef index_getset[] = {
    {"nodemap", (getter)index_nodemap, NULL, "nodemap", NULL},
    {NULL} /* Sentinel */
};

static PyMemberDef index_members[] = {
    {"entry_size", T_LONG, offsetof(indexObject, entry_size), 0,
     "size of an index entry"},
    {"rust_ext_compat", T_LONG, offsetof(indexObject, rust_ext_compat), 0,
     "size of an index entry"},
    {NULL} /* Sentinel */
};

PyTypeObject HgRevlogIndex_Type = {
    PyVarObject_HEAD_INIT(NULL, 0) /* header */
    "parsers.index",               /* tp_name */
    sizeof(indexObject),           /* tp_basicsize */
    0,                             /* tp_itemsize */
    (destructor)index_dealloc,     /* tp_dealloc */
    0,                             /* tp_print */
    0,                             /* tp_getattr */
    0,                             /* tp_setattr */
    0,                             /* tp_compare */
    0,                             /* tp_repr */
    0,                             /* tp_as_number */
    &index_sequence_methods,       /* tp_as_sequence */
    &index_mapping_methods,        /* tp_as_mapping */
    0,                             /* tp_hash */
    0,                             /* tp_call */
    0,                             /* tp_str */
    0,                             /* tp_getattro */
    0,                             /* tp_setattro */
    0,                             /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT,            /* tp_flags */
    "revlog index",                /* tp_doc */
    0,                             /* tp_traverse */
    0,                             /* tp_clear */
    0,                             /* tp_richcompare */
    0,                             /* tp_weaklistoffset */
    0,                             /* tp_iter */
    0,                             /* tp_iternext */
    index_methods,                 /* tp_methods */
    index_members,                 /* tp_members */
    index_getset,                  /* tp_getset */
    0,                             /* tp_base */
    0,                             /* tp_dict */
    0,                             /* tp_descr_get */
    0,                             /* tp_descr_set */
    0,                             /* tp_dictoffset */
    (initproc)index_init,          /* tp_init */
    0,                             /* tp_alloc */
};

/*
 * returns a tuple of the form (index, cache) with elements as
 * follows:
 *
 * index: an index object that lazily parses Revlog (v1 or v2) records
 * cache: if data is inlined, a tuple (0, index_file_content), else None
 *        index_file_content could be a string, or a buffer
 *
 * added complications are for backwards compatibility
 */
PyObject *parse_index2(PyObject *self, PyObject *args, PyObject *kwargs)
{
	PyObject *cache = NULL;
	indexObject *idx;
	int ret;

	idx = PyObject_New(indexObject, &HgRevlogIndex_Type);
	if (idx == NULL)
		goto bail;

	ret = index_init(idx, args, kwargs);
	if (ret == -1)
		goto bail;

	if (idx->inlined) {
		cache = Py_BuildValue("iO", 0, idx->data);
		if (cache == NULL)
			goto bail;
	} else {
		cache = Py_None;
		Py_INCREF(cache);
	}

	return Py_BuildValue("NN", idx, cache);

bail:
	Py_XDECREF(idx);
	Py_XDECREF(cache);
	return NULL;
}

static Revlog_CAPI CAPI = {
    /* increment the abi_version field upon each change in the Revlog_CAPI
       struct or in the ABI of the listed functions */
    3, index_length, index_node, index_fast_rank, HgRevlogIndex_GetParents,
};

void revlog_module_init(PyObject *mod)
{
	PyObject *caps = NULL;
	HgRevlogIndex_Type.tp_new = PyType_GenericNew;
	if (PyType_Ready(&HgRevlogIndex_Type) < 0)
		return;
	Py_INCREF(&HgRevlogIndex_Type);
	PyModule_AddObject(mod, "index", (PyObject *)&HgRevlogIndex_Type);

	nodetreeType.tp_new = PyType_GenericNew;
	if (PyType_Ready(&nodetreeType) < 0)
		return;
	Py_INCREF(&nodetreeType);
	PyModule_AddObject(mod, "nodetree", (PyObject *)&nodetreeType);

	caps = PyCapsule_New(&CAPI, "mercurial.cext.parsers.revlog_CAPI", NULL);
	if (caps != NULL)
		PyModule_AddObject(mod, "revlog_CAPI", caps);
}
