/*
 util.h - utility functions for interfacing with the various python APIs.

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#ifndef _HG_UTIL_H_
#define _HG_UTIL_H_

#if PY_MAJOR_VERSION >= 3

#define IS_PY3K
#define PyInt_FromLong PyLong_FromLong
#define PyInt_AsLong PyLong_AsLong

/*
 Mapping of some of the python < 2.x PyString* functions to py3k's PyUnicode.

 The commented names below represent those that are present in the PyBytes
 definitions for python < 2.6 (below in this file) that don't have a direct
 implementation.
*/

#define PyStringObject PyUnicodeObject
#define PyString_Type PyUnicode_Type

#define PyString_Check PyUnicode_Check
#define PyString_CheckExact PyUnicode_CheckExact
#define PyString_CHECK_INTERNED PyUnicode_CHECK_INTERNED
#define PyString_AS_STRING PyUnicode_AsLatin1String
#define PyString_GET_SIZE PyUnicode_GET_SIZE

#define PyString_FromStringAndSize PyUnicode_FromStringAndSize
#define PyString_FromString PyUnicode_FromString
#define PyString_FromFormatV PyUnicode_FromFormatV
#define PyString_FromFormat PyUnicode_FromFormat
/* #define PyString_Size PyUnicode_GET_SIZE */
/* #define PyString_AsString */
/* #define PyString_Repr */
#define PyString_Concat PyUnicode_Concat
#define PyString_ConcatAndDel PyUnicode_AppendAndDel
#define _PyString_Resize PyUnicode_Resize
/* #define _PyString_Eq */
#define PyString_Format PyUnicode_Format
/* #define _PyString_FormatLong */
/* #define PyString_DecodeEscape */
#define _PyString_Join PyUnicode_Join
#define PyString_Decode PyUnicode_Decode
#define PyString_Encode PyUnicode_Encode
#define PyString_AsEncodedObject PyUnicode_AsEncodedObject
#define PyString_AsEncodedString PyUnicode_AsEncodedString
#define PyString_AsDecodedObject PyUnicode_AsDecodedObject
#define PyString_AsDecodedString PyUnicode_AsDecodedUnicode
/* #define PyString_AsStringAndSize */
#define _PyString_InsertThousandsGrouping _PyUnicode_InsertThousandsGrouping

#endif /* PY_MAJOR_VERSION */

/* Backports from 2.6 */
#if PY_VERSION_HEX < 0x02060000

#define Py_TYPE(ob) (ob)->ob_type
#define Py_SIZE(ob) (ob)->ob_size
#define PyVarObject_HEAD_INIT(type, size) PyObject_HEAD_INIT(type) size,

/* Shamelessly stolen from bytesobject.h */
#define PyBytesObject PyStringObject
#define PyBytes_Type PyString_Type

#define PyBytes_Check PyString_Check
#define PyBytes_CheckExact PyString_CheckExact
#define PyBytes_CHECK_INTERNED PyString_CHECK_INTERNED
#define PyBytes_AS_STRING PyString_AS_STRING
#define PyBytes_GET_SIZE PyString_GET_SIZE
#define Py_TPFLAGS_BYTES_SUBCLASS Py_TPFLAGS_STRING_SUBCLASS

#define PyBytes_FromStringAndSize PyString_FromStringAndSize
#define PyBytes_FromString PyString_FromString
#define PyBytes_FromFormatV PyString_FromFormatV
#define PyBytes_FromFormat PyString_FromFormat
#define PyBytes_Size PyString_Size
#define PyBytes_AsString PyString_AsString
#define PyBytes_Repr PyString_Repr
#define PyBytes_Concat PyString_Concat
#define PyBytes_ConcatAndDel PyString_ConcatAndDel
#define _PyBytes_Resize _PyString_Resize
#define _PyBytes_Eq _PyString_Eq
#define PyBytes_Format PyString_Format
#define _PyBytes_FormatLong _PyString_FormatLong
#define PyBytes_DecodeEscape PyString_DecodeEscape
#define _PyBytes_Join _PyString_Join
#define PyBytes_Decode PyString_Decode
#define PyBytes_Encode PyString_Encode
#define PyBytes_AsEncodedObject PyString_AsEncodedObject
#define PyBytes_AsEncodedString PyString_AsEncodedString
#define PyBytes_AsDecodedObject PyString_AsDecodedObject
#define PyBytes_AsDecodedString PyString_AsDecodedString
#define PyBytes_AsStringAndSize PyString_AsStringAndSize
#define _PyBytes_InsertThousandsGrouping _PyString_InsertThousandsGrouping

#endif /* PY_VERSION_HEX */

#if (PY_VERSION_HEX < 0x02050000)
/* Definitions to get compatibility with python 2.4 and earlier which
   does not have Py_ssize_t. See also PEP 353.
   Note: msvc (8 or earlier) does not have ssize_t, so we use Py_ssize_t.
*/
typedef int Py_ssize_t;
typedef Py_ssize_t (*lenfunc)(PyObject *);
typedef PyObject *(*ssizeargfunc)(PyObject *, Py_ssize_t);
#define PyInt_FromSsize_t PyInt_FromLong

#if !defined(PY_SSIZE_T_MIN)
#define PY_SSIZE_T_MAX INT_MAX
#define PY_SSIZE_T_MIN INT_MIN
#endif
#endif

#ifdef _WIN32
#ifdef _MSC_VER
/* msvc 6.0 has problems */
#define inline __inline
typedef unsigned char uint8_t;
typedef unsigned long uint32_t;
typedef unsigned __int64 uint64_t;
#else
#include <stdint.h>
#endif
#else
/* not windows */
#include <sys/types.h>
#if defined __BEOS__ && !defined __HAIKU__
#include <ByteOrder.h>
#else
#include <arpa/inet.h>
#endif
#include <inttypes.h>
#endif

#if defined __hpux || defined __SUNPRO_C || defined _AIX
#define inline
#endif

#ifdef __linux
#define inline __inline
#endif

static inline uint32_t getbe32(const char *c)
{
	const unsigned char *d = (const unsigned char *)c;

	return ((d[0] << 24) |
		(d[1] << 16) |
		(d[2] << 8) |
		(d[3]));
}

static inline void putbe32(uint32_t x, char *c)
{
	c[0] = (x >> 24) & 0xff;
	c[1] = (x >> 16) & 0xff;
	c[2] = (x >> 8) & 0xff;
	c[3] = (x) & 0xff;
}

#endif /* _HG_UTIL_H_ */
