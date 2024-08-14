/*
Copyright (c) 2013-2015, Facebook, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

 * Neither the name Facebook nor the names of its contributors may be used to
   endorse or promote products derived from this software without specific
   prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <bytesobject.h>
#ifdef _MSC_VER
#define inline __inline
#if _MSC_VER >= 1800
#include <stdint.h>
#else
// The compiler associated with Python 2.7 on Windows doesn't ship
// with stdint.h, so define the small subset that we use here.
typedef __int8 int8_t;
typedef __int16 int16_t;
typedef __int32 int32_t;
typedef __int64 int64_t;
typedef unsigned __int8 uint8_t;
typedef unsigned __int16 uint16_t;
typedef unsigned __int32 uint32_t;
typedef unsigned __int64 uint64_t;
#define UINT32_MAX 4294967295U
#endif
#endif

// clang-format off
/* Return the smallest size int that can store the value */
#define INT_SIZE(x) (((x) == ((int8_t)x))  ? 1 :    \
                     ((x) == ((int16_t)x)) ? 2 :    \
                     ((x) == ((int32_t)x)) ? 4 : 8)

#define BSER_ARRAY     0x00
#define BSER_OBJECT    0x01
#define BSER_BYTESTRING 0x02
#define BSER_INT8      0x03
#define BSER_INT16     0x04
#define BSER_INT32     0x05
#define BSER_INT64     0x06
#define BSER_REAL      0x07
#define BSER_TRUE      0x08
#define BSER_FALSE     0x09
#define BSER_NULL      0x0a
#define BSER_TEMPLATE  0x0b
#define BSER_SKIP      0x0c
#define BSER_UTF8STRING 0x0d
// clang-format on

// An immutable object representation of BSER_OBJECT.
// Rather than build a hash table, key -> value are obtained
// by walking the list of keys to determine the offset into
// the values array.  The assumption is that the number of
// array elements will be typically small (~6 for the top
// level query result and typically 3-5 for the file entries)
// so that the time overhead for this is small compared to
// using a proper hash table.  Even with this simplistic
// approach, this is still faster for the mercurial use case
// as it helps to eliminate creating N other objects to
// represent the stat information in the hgwatchman extension
// clang-format off
typedef struct {
  PyObject_HEAD
  PyObject *keys;   // tuple of field names
  PyObject *values; // tuple of values
} bserObject;
// clang-format on

static Py_ssize_t bserobj_tuple_length(PyObject* o) {
  bserObject* obj = (bserObject*)o;

  return PySequence_Length(obj->keys);
}

static PyObject* bserobj_tuple_item(PyObject* o, Py_ssize_t i) {
  bserObject* obj = (bserObject*)o;

  return PySequence_GetItem(obj->values, i);
}

// clang-format off
static PySequenceMethods bserobj_sq = {
  bserobj_tuple_length,      /* sq_length */
  0,                         /* sq_concat */
  0,                         /* sq_repeat */
  bserobj_tuple_item,        /* sq_item */
  0,                         /* sq_ass_item */
  0,                         /* sq_contains */
  0,                         /* sq_inplace_concat */
  0                          /* sq_inplace_repeat */
};
// clang-format on

static void bserobj_dealloc(PyObject* o) {
  bserObject* obj = (bserObject*)o;

  Py_CLEAR(obj->keys);
  Py_CLEAR(obj->values);
  PyObject_Del(o);
}

static PyObject* bserobj_getattrro(PyObject* o, PyObject* name) {
  bserObject* obj = (bserObject*)o;
  Py_ssize_t i, n;
  PyObject* name_bytes = NULL;
  PyObject* ret = NULL;
  const char* namestr = NULL;

  if (PyIndex_Check(name)) {
    i = PyNumber_AsSsize_t(name, PyExc_IndexError);
    if (i == -1 && PyErr_Occurred()) {
      goto bail;
    }

    if (i == 8 && PySequence_Size(obj->values) < 9) {
      // Hack alert: Python 3 removed support for os.stat().st_mtime
      // being an integer.Instead, if you need an integer, you have to
      // use os.stat()[stat.ST_MTIME] instead. stat.ST_MTIME is 8, and
      // our stat tuples are shorter than that, so we can detect
      // requests for index 8 on tuples shorter than that and return
      // st_mtime instead.
      namestr = "st_mtime";
    } else {
      ret = PySequence_GetItem(obj->values, i);
      goto bail;
    }
  } else {
    // We can be passed in Unicode objects here -- we don't support anything other
    // than UTF-8 for keys.
    if (PyUnicode_Check(name)) {
      name_bytes = PyUnicode_AsUTF8String(name);
      if (name_bytes == NULL) {
        goto bail;
      }
      namestr = PyBytes_AsString(name_bytes);
    } else {
      namestr = PyBytes_AsString(name);
    }
  }

  if (namestr == NULL) {
    goto bail;
  }
  // hack^Wfeature to allow mercurial to use "st_size" to reference "size"
  if (!strncmp(namestr, "st_", 3)) {
    namestr += 3;
  }

  n = PyTuple_GET_SIZE(obj->keys);
  for (i = 0; i < n; i++) {
    const char* item_name = NULL;
    PyObject* key = PyTuple_GET_ITEM(obj->keys, i);

    if (PyUnicode_Check(key)) {
#if PY_MAJOR_VERSION >= 3
      item_name = PyUnicode_AsUTF8(key);
#else
      PyObject* utf = PyUnicode_AsEncodedString(key, "utf-8", "ignore");
      if (utf == NULL) {
        goto bail;
      }
      item_name = PyBytes_AsString(utf);
#endif
    } else {
      item_name = PyBytes_AsString(key);
    }
    if (item_name == NULL) {
      goto bail;
    }
    if (!strcmp(item_name, namestr)) {
      ret = PySequence_GetItem(obj->values, i);
      goto bail;
    }
  }

  PyErr_Format(
      PyExc_AttributeError, "bserobject has no attribute '%.400s'", namestr);
bail:
  Py_XDECREF(name_bytes);
  return ret;
}

// clang-format off
static PyMappingMethods bserobj_map = {
  bserobj_tuple_length,     /* mp_length */
  bserobj_getattrro,        /* mp_subscript */
  0                         /* mp_ass_subscript */
};

PyTypeObject bserObjectType = {
  PyVarObject_HEAD_INIT(NULL, 0)
  "bserobj_tuple",           /* tp_name */
  sizeof(bserObject),        /* tp_basicsize */
  0,                         /* tp_itemsize */
  bserobj_dealloc,           /* tp_dealloc */
  0,                         /* tp_print */
  0,                         /* tp_getattr */
  0,                         /* tp_setattr */
  0,                         /* tp_compare */
  0,                         /* tp_repr */
  0,                         /* tp_as_number */
  &bserobj_sq,               /* tp_as_sequence */
  &bserobj_map,              /* tp_as_mapping */
  0,                         /* tp_hash  */
  0,                         /* tp_call */
  0,                         /* tp_str */
  bserobj_getattrro,         /* tp_getattro */
  0,                         /* tp_setattro */
  0,                         /* tp_as_buffer */
  Py_TPFLAGS_DEFAULT,        /* tp_flags */
  "bserobj tuple",           /* tp_doc */
  0,                         /* tp_traverse */
  0,                         /* tp_clear */
  0,                         /* tp_richcompare */
  0,                         /* tp_weaklistoffset */
  0,                         /* tp_iter */
  0,                         /* tp_iternext */
  0,                         /* tp_methods */
  0,                         /* tp_members */
  0,                         /* tp_getset */
  0,                         /* tp_base */
  0,                         /* tp_dict */
  0,                         /* tp_descr_get */
  0,                         /* tp_descr_set */
  0,                         /* tp_dictoffset */
  0,                         /* tp_init */
  0,                         /* tp_alloc */
  0,                         /* tp_new */
};
// clang-format on

typedef struct loads_ctx {
  int mutable;
  const char* value_encoding;
  const char* value_errors;
  uint32_t bser_version;
  uint32_t bser_capabilities;
} unser_ctx_t;

static PyObject*
bser_loads_recursive(const char** ptr, const char* end, const unser_ctx_t* ctx);

static const char bser_true = BSER_TRUE;
static const char bser_false = BSER_FALSE;
static const char bser_null = BSER_NULL;
static const char bser_bytestring_hdr = BSER_BYTESTRING;
static const char bser_array_hdr = BSER_ARRAY;
static const char bser_object_hdr = BSER_OBJECT;

static inline uint32_t next_power_2(uint32_t n) {
  n |= (n >> 16);
  n |= (n >> 8);
  n |= (n >> 4);
  n |= (n >> 2);
  n |= (n >> 1);
  return n + 1;
}

// A buffer we use for building up the serialized result
struct bser_buffer {
  char* buf;
  int wpos, allocd;
  uint32_t bser_version;
  uint32_t capabilities;
};
typedef struct bser_buffer bser_t;

static int bser_append(bser_t* bser, const char* data, uint32_t len) {
  int newlen = next_power_2(bser->wpos + len);
  if (newlen > bser->allocd) {
    char* nbuf = realloc(bser->buf, newlen);
    if (!nbuf) {
      return 0;
    }

    bser->buf = nbuf;
    bser->allocd = newlen;
  }

  memcpy(bser->buf + bser->wpos, data, len);
  bser->wpos += len;
  return 1;
}

static int bser_init(bser_t* bser, uint32_t version, uint32_t capabilities) {
  bser->allocd = 8192;
  bser->wpos = 0;
  bser->buf = malloc(bser->allocd);
  bser->bser_version = version;
  bser->capabilities = capabilities;
  if (!bser->buf) {
    return 0;
  }

// Leave room for the serialization header, which includes
// our overall length.  To make things simpler, we'll use an
// int32 for the header
#define EMPTY_HEADER "\x00\x01\x05\x00\x00\x00\x00"

// Version 2 also carries an integer indicating the capabilities. The
// capabilities integer comes before the PDU size.
#define EMPTY_HEADER_V2 "\x00\x02\x00\x00\x00\x00\x05\x00\x00\x00\x00"
  if (version == 2) {
    bser_append(bser, EMPTY_HEADER_V2, sizeof(EMPTY_HEADER_V2) - 1);
  } else {
    bser_append(bser, EMPTY_HEADER, sizeof(EMPTY_HEADER) - 1);
  }

  return 1;
}

static void bser_dtor(bser_t* bser) {
  free(bser->buf);
  bser->buf = NULL;
}

static int bser_long(bser_t* bser, int64_t val) {
  int8_t i8;
  int16_t i16;
  int32_t i32;
  int64_t i64;
  char sz;
  int size = INT_SIZE(val);
  char* iptr;

  switch (size) {
    case 1:
      sz = BSER_INT8;
      i8 = (int8_t)val;
      iptr = (char*)&i8;
      break;
    case 2:
      sz = BSER_INT16;
      i16 = (int16_t)val;
      iptr = (char*)&i16;
      break;
    case 4:
      sz = BSER_INT32;
      i32 = (int32_t)val;
      iptr = (char*)&i32;
      break;
    case 8:
      sz = BSER_INT64;
      i64 = (int64_t)val;
      iptr = (char*)&i64;
      break;
    default:
      PyErr_SetString(PyExc_RuntimeError, "Cannot represent this long value!?");
      return 0;
  }

  if (!bser_append(bser, &sz, sizeof(sz))) {
    return 0;
  }

  return bser_append(bser, iptr, size);
}

static int bser_bytestring(bser_t* bser, PyObject* sval) {
  char* buf = NULL;
  Py_ssize_t len;
  int res;
  PyObject* utf = NULL;

  if (PyUnicode_Check(sval)) {
    utf = PyUnicode_AsEncodedString(sval, "utf-8", "ignore");
    sval = utf;
  }

  res = PyBytes_AsStringAndSize(sval, &buf, &len);
  if (res == -1) {
    res = 0;
    goto out;
  }

  if (!bser_append(bser, &bser_bytestring_hdr, sizeof(bser_bytestring_hdr))) {
    res = 0;
    goto out;
  }

  if (!bser_long(bser, len)) {
    res = 0;
    goto out;
  }

  if (len > UINT32_MAX) {
    PyErr_Format(PyExc_ValueError, "string too big");
    res = 0;
    goto out;
  }

  res = bser_append(bser, buf, (uint32_t)len);

out:
  if (utf) {
    Py_DECREF(utf);
  }

  return res;
}

static int bser_recursive(bser_t* bser, PyObject* val) {
  if (PyBool_Check(val)) {
    if (val == Py_True) {
      return bser_append(bser, &bser_true, sizeof(bser_true));
    }
    return bser_append(bser, &bser_false, sizeof(bser_false));
  }

  if (val == Py_None) {
    return bser_append(bser, &bser_null, sizeof(bser_null));
  }

// Python 3 has one integer type.
#if PY_MAJOR_VERSION < 3
  if (PyInt_Check(val)) {
    return bser_long(bser, PyInt_AS_LONG(val));
  }
#endif // PY_MAJOR_VERSION < 3

  if (PyLong_Check(val)) {
    return bser_long(bser, PyLong_AsLongLong(val));
  }

  if (PyBytes_Check(val) || PyUnicode_Check(val)) {
    return bser_bytestring(bser, val);
  }

  if (PyFloat_Check(val)) {
    double dval = PyFloat_AS_DOUBLE(val);
    char sz = BSER_REAL;

    if (!bser_append(bser, &sz, sizeof(sz))) {
      return 0;
    }

    return bser_append(bser, (char*)&dval, sizeof(dval));
  }

  if (PyList_Check(val)) {
    Py_ssize_t i, len = PyList_GET_SIZE(val);

    if (!bser_append(bser, &bser_array_hdr, sizeof(bser_array_hdr))) {
      return 0;
    }

    if (!bser_long(bser, len)) {
      return 0;
    }

    for (i = 0; i < len; i++) {
      PyObject* ele = PyList_GET_ITEM(val, i);

      if (!bser_recursive(bser, ele)) {
        return 0;
      }
    }

    return 1;
  }

  if (PyTuple_Check(val)) {
    Py_ssize_t i, len = PyTuple_GET_SIZE(val);

    if (!bser_append(bser, &bser_array_hdr, sizeof(bser_array_hdr))) {
      return 0;
    }

    if (!bser_long(bser, len)) {
      return 0;
    }

    for (i = 0; i < len; i++) {
      PyObject* ele = PyTuple_GET_ITEM(val, i);

      if (!bser_recursive(bser, ele)) {
        return 0;
      }
    }

    return 1;
  }

  if (PyMapping_Check(val)) {
    Py_ssize_t len = PyMapping_Length(val);
    Py_ssize_t pos = 0;
    PyObject *key, *ele;

    if (!bser_append(bser, &bser_object_hdr, sizeof(bser_object_hdr))) {
      return 0;
    }

    if (!bser_long(bser, len)) {
      return 0;
    }

    while (PyDict_Next(val, &pos, &key, &ele)) {
      if (!bser_bytestring(bser, key)) {
        return 0;
      }
      if (!bser_recursive(bser, ele)) {
        return 0;
      }
    }

    return 1;
  }

  PyErr_SetString(PyExc_ValueError, "Unsupported value type");
  return 0;
}

static PyObject* bser_dumps(PyObject* self, PyObject* args, PyObject* kw) {
  PyObject *val = NULL, *res;
  bser_t bser;
  uint32_t len, bser_version = 1, bser_capabilities = 0;

  static char* kw_list[] = {"val", "version", "capabilities", NULL};

  if (!PyArg_ParseTupleAndKeywords(
          args,
          kw,
          "O|ii:dumps",
          kw_list,
          &val,
          &bser_version,
          &bser_capabilities)) {
    return NULL;
  }

  if (!bser_init(&bser, bser_version, bser_capabilities)) {
    return PyErr_NoMemory();
  }

  if (!bser_recursive(&bser, val)) {
    bser_dtor(&bser);
    if (errno == ENOMEM) {
      return PyErr_NoMemory();
    }
    // otherwise, we've already set the error to something reasonable
    return NULL;
  }

  // Now fill in the overall length
  if (bser_version == 1) {
    len = bser.wpos - (sizeof(EMPTY_HEADER) - 1);
    memcpy(bser.buf + 3, &len, sizeof(len));
  } else {
    len = bser.wpos - (sizeof(EMPTY_HEADER_V2) - 1);
    // The BSER capabilities block comes before the PDU length
    memcpy(bser.buf + 2, &bser_capabilities, sizeof(bser_capabilities));
    memcpy(bser.buf + 7, &len, sizeof(len));
  }

  res = PyBytes_FromStringAndSize(bser.buf, bser.wpos);
  bser_dtor(&bser);

  return res;
}

int bunser_int(const char** ptr, const char* end, int64_t* val) {
  int needed;
  const char* buf = *ptr;
  int8_t i8;
  int16_t i16;
  int32_t i32;
  int64_t i64;

  switch (buf[0]) {
    case BSER_INT8:
      needed = 2;
      break;
    case BSER_INT16:
      needed = 3;
      break;
    case BSER_INT32:
      needed = 5;
      break;
    case BSER_INT64:
      needed = 9;
      break;
    default:
      PyErr_Format(
          PyExc_ValueError, "invalid bser int encoding 0x%02x", buf[0]);
      return 0;
  }
  if (end - buf < needed) {
    PyErr_SetString(PyExc_ValueError, "input buffer to small for int encoding");
    return 0;
  }
  *ptr = buf + needed;
  switch (buf[0]) {
    case BSER_INT8:
      memcpy(&i8, buf + 1, sizeof(i8));
      *val = i8;
      return 1;
    case BSER_INT16:
      memcpy(&i16, buf + 1, sizeof(i16));
      *val = i16;
      return 1;
    case BSER_INT32:
      memcpy(&i32, buf + 1, sizeof(i32));
      *val = i32;
      return 1;
    case BSER_INT64:
      memcpy(&i64, buf + 1, sizeof(i64));
      *val = i64;
      return 1;
    default:
      return 0;
  }
}

static int bunser_bytestring(
    const char** ptr,
    const char* end,
    const char** start,
    int64_t* len) {
  const char* buf = *ptr;

  // skip string marker
  buf++;
  if (!bunser_int(&buf, end, len)) {
    return 0;
  }

  if (buf + *len > end) {
    PyErr_Format(PyExc_ValueError, "invalid string length in bser data");
    return 0;
  }

  *ptr = buf + *len;
  *start = buf;
  return 1;
}

static PyObject*
bunser_array(const char** ptr, const char* end, const unser_ctx_t* ctx) {
  const char* buf = *ptr;
  int64_t nitems, i;
  int mutable = ctx->mutable;
  PyObject* res;

  // skip array header
  buf++;
  if (!bunser_int(&buf, end, &nitems)) {
    return 0;
  }
  *ptr = buf;

  if (nitems > LONG_MAX) {
    PyErr_Format(PyExc_ValueError, "too many items for python array");
    return NULL;
  }

  if (mutable) {
    res = PyList_New((Py_ssize_t)nitems);
  } else {
    res = PyTuple_New((Py_ssize_t)nitems);
  }

  for (i = 0; i < nitems; i++) {
    PyObject* ele = bser_loads_recursive(ptr, end, ctx);

    if (!ele) {
      Py_DECREF(res);
      return NULL;
    }

    if (mutable) {
      PyList_SET_ITEM(res, i, ele);
    } else {
      PyTuple_SET_ITEM(res, i, ele);
    }
    // DECREF(ele) not required as SET_ITEM steals the ref
  }

  return res;
}

static PyObject*
bunser_object(const char** ptr, const char* end, const unser_ctx_t* ctx) {
  const char* buf = *ptr;
  int64_t nitems, i;
  int mutable = ctx->mutable;
  PyObject* res;
  bserObject* obj;

  // skip array header
  buf++;
  if (!bunser_int(&buf, end, &nitems)) {
    return 0;
  }
  *ptr = buf;

  if (mutable) {
    res = PyDict_New();
  } else {
    obj = PyObject_New(bserObject, &bserObjectType);
    obj->keys = PyTuple_New((Py_ssize_t)nitems);
    obj->values = PyTuple_New((Py_ssize_t)nitems);
    res = (PyObject*)obj;
  }

  for (i = 0; i < nitems; i++) {
    const char* keystr;
    int64_t keylen;
    PyObject* key;
    PyObject* ele;

    if (!bunser_bytestring(ptr, end, &keystr, &keylen)) {
      Py_DECREF(res);
      return NULL;
    }

    if (keylen > LONG_MAX) {
      PyErr_Format(PyExc_ValueError, "string too big for python");
      Py_DECREF(res);
      return NULL;
    }

    if (mutable) {
      // This will interpret the key as UTF-8.
      key = PyUnicode_FromStringAndSize(keystr, (Py_ssize_t)keylen);
    } else {
      // For immutable objects we'll manage key lookups, so we can avoid going
      // through the Unicode APIs. This avoids a potentially expensive and
      // definitely unnecessary conversion to UTF-16 and back for Python 2.
      // TODO: On Python 3 the Unicode APIs are smarter: we might be able to use
      // Unicode keys there without an appreciable performance loss.
      key = PyBytes_FromStringAndSize(keystr, (Py_ssize_t)keylen);
    }

    if (!key) {
      Py_DECREF(res);
      return NULL;
    }

    ele = bser_loads_recursive(ptr, end, ctx);

    if (!ele) {
      Py_DECREF(key);
      Py_DECREF(res);
      return NULL;
    }

    if (mutable) {
      PyDict_SetItem(res, key, ele);
      Py_DECREF(key);
      Py_DECREF(ele);
    } else {
      /* PyTuple_SET_ITEM steals ele, key */
      PyTuple_SET_ITEM(obj->values, i, ele);
      PyTuple_SET_ITEM(obj->keys, i, key);
    }
  }

  return res;
}

static PyObject*
bunser_template(const char** ptr, const char* end, const unser_ctx_t* ctx) {
  const char* buf = *ptr;
  int64_t nitems, i;
  int mutable = ctx->mutable;
  PyObject* arrval;
  PyObject* keys;
  Py_ssize_t numkeys, keyidx;
  unser_ctx_t keys_ctx = {0};
  if (mutable) {
    keys_ctx.mutable = 1;
    // Decode keys as UTF-8 in this case.
    keys_ctx.value_encoding = "utf-8";
    keys_ctx.value_errors = "strict";
  } else {
    // Treat keys as bytestrings in this case -- we'll do Unicode conversions at
    // lookup time.
  }

  if (buf[1] != BSER_ARRAY) {
    PyErr_Format(PyExc_ValueError, "Expect ARRAY to follow TEMPLATE");
    return NULL;
  }

  // skip header
  buf++;
  *ptr = buf;

  // Load template keys.
  // For keys we don't want to do any decoding right now.
  keys = bunser_array(ptr, end, &keys_ctx);
  if (!keys) {
    return NULL;
  }

  numkeys = PySequence_Length(keys);

  // Load number of array elements
  if (!bunser_int(ptr, end, &nitems)) {
    Py_DECREF(keys);
    return 0;
  }

  if (nitems > LONG_MAX) {
    PyErr_Format(PyExc_ValueError, "Too many items for python");
    Py_DECREF(keys);
    return NULL;
  }

  arrval = PyList_New((Py_ssize_t)nitems);
  if (!arrval) {
    Py_DECREF(keys);
    return NULL;
  }

  for (i = 0; i < nitems; i++) {
    PyObject* dict = NULL;
    bserObject* obj = NULL;

    if (mutable) {
      dict = PyDict_New();
    } else {
      obj = PyObject_New(bserObject, &bserObjectType);
      if (obj) {
        obj->keys = keys;
        Py_INCREF(obj->keys);
        obj->values = PyTuple_New(numkeys);
      }
      dict = (PyObject*)obj;
    }
    if (!dict) {
    fail:
      Py_DECREF(keys);
      Py_DECREF(arrval);
      return NULL;
    }

    for (keyidx = 0; keyidx < numkeys; keyidx++) {
      PyObject* key;
      PyObject* ele;

      if (**ptr == BSER_SKIP) {
        *ptr = *ptr + 1;
        ele = Py_None;
        Py_INCREF(ele);
      } else {
        ele = bser_loads_recursive(ptr, end, ctx);
      }

      if (!ele) {
        goto fail;
      }

      if (mutable) {
        key = PyList_GET_ITEM(keys, keyidx);
        PyDict_SetItem(dict, key, ele);
        Py_DECREF(ele);
      } else {
        PyTuple_SET_ITEM(obj->values, keyidx, ele);
        // DECREF(ele) not required as SET_ITEM steals the ref
      }
    }

    PyList_SET_ITEM(arrval, i, dict);
    // DECREF(obj) not required as SET_ITEM steals the ref
  }

  Py_DECREF(keys);

  return arrval;
}

static PyObject* bser_loads_recursive(
    const char** ptr,
    const char* end,
    const unser_ctx_t* ctx) {
  const char* buf = *ptr;

  switch (buf[0]) {
    case BSER_INT8:
    case BSER_INT16:
    case BSER_INT32:
    case BSER_INT64: {
      int64_t ival;
      if (!bunser_int(ptr, end, &ival)) {
        return NULL;
      }
// Python 3 has one integer type.
#if PY_MAJOR_VERSION >= 3
      return PyLong_FromLongLong(ival);
#else
      if (ival < LONG_MIN || ival > LONG_MAX) {
        return PyLong_FromLongLong(ival);
      }
      return PyInt_FromSsize_t(Py_SAFE_DOWNCAST(ival, int64_t, Py_ssize_t));
#endif // PY_MAJOR_VERSION >= 3
    }

    case BSER_REAL: {
      double dval;
      memcpy(&dval, buf + 1, sizeof(dval));
      *ptr = buf + 1 + sizeof(double);
      return PyFloat_FromDouble(dval);
    }

    case BSER_TRUE:
      *ptr = buf + 1;
      Py_INCREF(Py_True);
      return Py_True;

    case BSER_FALSE:
      *ptr = buf + 1;
      Py_INCREF(Py_False);
      return Py_False;

    case BSER_NULL:
      *ptr = buf + 1;
      Py_INCREF(Py_None);
      return Py_None;

    case BSER_BYTESTRING: {
      const char* start;
      int64_t len;

      if (!bunser_bytestring(ptr, end, &start, &len)) {
        return NULL;
      }

      if (len > LONG_MAX) {
        PyErr_Format(PyExc_ValueError, "string too long for python");
        return NULL;
      }

      if (ctx->value_encoding != NULL) {
        return PyUnicode_Decode(
            start, (long)len, ctx->value_encoding, ctx->value_errors);
      } else {
        return PyBytes_FromStringAndSize(start, (long)len);
      }
    }

    case BSER_UTF8STRING: {
      const char* start;
      int64_t len;

      if (!bunser_bytestring(ptr, end, &start, &len)) {
        return NULL;
      }

      if (len > LONG_MAX) {
        PyErr_Format(PyExc_ValueError, "string too long for python");
        return NULL;
      }

      return PyUnicode_Decode(start, (long)len, "utf-8", "strict");
    }

    case BSER_ARRAY:
      return bunser_array(ptr, end, ctx);

    case BSER_OBJECT:
      return bunser_object(ptr, end, ctx);

    case BSER_TEMPLATE:
      return bunser_template(ptr, end, ctx);

    default:
      PyErr_Format(PyExc_ValueError, "unhandled bser opcode 0x%02x", buf[0]);
  }

  return NULL;
}

static int _pdu_info_helper(
    const char* data,
    const char* end,
    uint32_t* bser_version_out,
    uint32_t* bser_capabilities_out,
    int64_t* expected_len_out,
    off_t* position_out) {
  uint32_t bser_version;
  uint32_t bser_capabilities = 0;
  int64_t expected_len;

  const char* start;
  start = data;
  // Validate the header and length
  if (memcmp(data, EMPTY_HEADER, 2) == 0) {
    bser_version = 1;
  } else if (memcmp(data, EMPTY_HEADER_V2, 2) == 0) {
    bser_version = 2;
  } else {
    PyErr_SetString(PyExc_ValueError, "invalid bser header");
    return 0;
  }

  data += 2;

  if (bser_version == 2) {
    // Expect an integer telling us what capabilities are supported by the
    // remote server (currently unused).
    if (!memcpy(&bser_capabilities, &data, sizeof(bser_capabilities))) {
      return 0;
    }
    data += sizeof(bser_capabilities);
  }

  // Expect an integer telling us how big the rest of the data
  // should be
  if (!bunser_int(&data, end, &expected_len)) {
    return 0;
  }

  *bser_version_out = bser_version;
  *bser_capabilities_out = (uint32_t)bser_capabilities;
  *expected_len_out = expected_len;
  *position_out = (off_t)(data - start);
  return 1;
}

// This function parses the PDU header and provides info about the packet
// Returns false if unsuccessful
static int pdu_info_helper(
    PyObject* self,
    PyObject* args,
    uint32_t* bser_version_out,
    uint32_t* bser_capabilities_out,
    int64_t* total_len_out) {
  const char* start = NULL;
  const char* data = NULL;
  Py_ssize_t datalen = 0;
  const char* end;
  int64_t expected_len;
  off_t position;

  if (!PyArg_ParseTuple(args, "s#", &start, &datalen)) {
    return 0;
  }
  data = start;
  end = data + datalen;

  if (!_pdu_info_helper(
          data,
          end,
          bser_version_out,
          bser_capabilities_out,
          &expected_len,
          &position)) {
    return 0;
  }
  *total_len_out = (int64_t)(expected_len + position);
  return 1;
}

// Expected use case is to read a packet from the socket and then call
// bser.pdu_info on the packet.  It returns the BSER version, BSER capabilities,
// and the total length of the entire response that the peer is sending,
// including the bytes already received. This allows the client  to compute the
// data size it needs to read before it can decode the data.
static PyObject* bser_pdu_info(PyObject* self, PyObject* args) {
  uint32_t version, capabilities;
  int64_t total_len;
  if (!pdu_info_helper(self, args, &version, &capabilities, &total_len)) {
    return NULL;
  }
  return Py_BuildValue("kkL", version, capabilities, total_len);
}

static PyObject* bser_pdu_len(PyObject* self, PyObject* args) {
  uint32_t version, capabilities;
  int64_t total_len;
  if (!pdu_info_helper(self, args, &version, &capabilities, &total_len)) {
    return NULL;
  }
  return Py_BuildValue("L", total_len);
}

static PyObject* bser_loads(PyObject* self, PyObject* args, PyObject* kw) {
  const char* data = NULL;
  Py_ssize_t datalen = 0;
  const char* start;
  const char* end;
  int64_t expected_len;
  off_t position;
  PyObject* mutable_obj = NULL;
  const char* value_encoding = NULL;
  const char* value_errors = NULL;
  unser_ctx_t ctx = {1, 0};

  static char* kw_list[] = {
      "buf", "mutable", "value_encoding", "value_errors", NULL};

  if (!PyArg_ParseTupleAndKeywords(
          args,
          kw,
          "s#|Ozz:loads",
          kw_list,
          &start,
          &datalen,
          &mutable_obj,
          &value_encoding,
          &value_errors)) {
    return NULL;
  }

  if (mutable_obj) {
    ctx.mutable = PyObject_IsTrue(mutable_obj) > 0 ? 1 : 0;
  }
  ctx.value_encoding = value_encoding;
  if (value_encoding == NULL) {
    ctx.value_errors = NULL;
  } else if (value_errors == NULL) {
    ctx.value_errors = "strict";
  } else {
    ctx.value_errors = value_errors;
  }
  data = start;
  end = data + datalen;

  if (!_pdu_info_helper(
          data,
          end,
          &ctx.bser_version,
          &ctx.bser_capabilities,
          &expected_len,
          &position)) {
    return NULL;
  }

  data = start + position;
  // Verify
  if (expected_len + data != end) {
    PyErr_SetString(PyExc_ValueError, "bser data len != header len");
    return NULL;
  }

  return bser_loads_recursive(&data, end, &ctx);
}

static PyObject* bser_load(PyObject* self, PyObject* args, PyObject* kw) {
  PyObject* load;
  PyObject* load_method;
  PyObject* string;
  PyObject* load_method_args;
  PyObject* load_method_kwargs;
  PyObject* fp = NULL;
  PyObject* mutable_obj = NULL;
  PyObject* value_encoding = NULL;
  PyObject* value_errors = NULL;

  static char* kw_list[] = {
      "fp", "mutable", "value_encoding", "value_errors", NULL};

  if (!PyArg_ParseTupleAndKeywords(
          args,
          kw,
          "O|OOO:load",
          kw_list,
          &fp,
          &mutable_obj,
          &value_encoding,
          &value_errors)) {
    return NULL;
  }

  load = PyImport_ImportModule("pywatchman.load");
  if (load == NULL) {
    return NULL;
  }
  load_method = PyObject_GetAttrString(load, "load");
  if (load_method == NULL) {
    return NULL;
  }
  // Mandatory method arguments
  load_method_args = Py_BuildValue("(O)", fp);
  if (load_method_args == NULL) {
    return NULL;
  }
  // Optional method arguments
  load_method_kwargs = PyDict_New();
  if (load_method_kwargs == NULL) {
    return NULL;
  }
  if (mutable_obj) {
    PyDict_SetItemString(load_method_kwargs, "mutable", mutable_obj);
  }
  if (value_encoding) {
    PyDict_SetItemString(load_method_kwargs, "value_encoding", value_encoding);
  }
  if (value_errors) {
    PyDict_SetItemString(load_method_kwargs, "value_errors", value_errors);
  }
  string = PyObject_Call(load_method, load_method_args, load_method_kwargs);
  Py_DECREF(load_method_kwargs);
  Py_DECREF(load_method_args);
  Py_DECREF(load_method);
  Py_DECREF(load);
  return string;
}

// clang-format off
static PyMethodDef bser_methods[] = {
  {"loads", (PyCFunction)bser_loads, METH_VARARGS | METH_KEYWORDS,
   "Deserialize string."},
  {"load", (PyCFunction)bser_load, METH_VARARGS | METH_KEYWORDS,
   "Deserialize a file object"},
  {"pdu_info", (PyCFunction)bser_pdu_info, METH_VARARGS,
   "Extract PDU information."},
  {"pdu_len", (PyCFunction)bser_pdu_len, METH_VARARGS,
   "Extract total PDU length."},
  {"dumps",  (PyCFunction)bser_dumps, METH_VARARGS | METH_KEYWORDS,
   "Serialize string."},
  {NULL, NULL, 0, NULL}
};

#if PY_MAJOR_VERSION >= 3
static struct PyModuleDef bser_module = {
  PyModuleDef_HEAD_INIT,
  "bser",
  "Efficient encoding and decoding of BSER.",
  -1,
  bser_methods
};
// clang-format on

PyMODINIT_FUNC PyInit_bser(void) {
  PyObject* mod;

  mod = PyModule_Create(&bser_module);
  PyType_Ready(&bserObjectType);

  return mod;
}
#else

PyMODINIT_FUNC initbser(void) {
  (void)Py_InitModule("bser", bser_methods);
  PyType_Ready(&bserObjectType);
}
#endif // PY_MAJOR_VERSION >= 3

/* vim:ts=2:sw=2:et:
 */
