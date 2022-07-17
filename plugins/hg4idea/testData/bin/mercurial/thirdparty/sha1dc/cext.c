#define PY_SSIZE_T_CLEAN
#include <Python.h>

#include "lib/sha1.h"

#if PY_MAJOR_VERSION >= 3
#define IS_PY3K
#endif

/* helper to switch things like string literal depending on Python version */
#ifdef IS_PY3K
#define PY23(py2, py3) py3
#else
#define PY23(py2, py3) py2
#endif

static char sha1dc_doc[] = "Efficient detection of SHA1 collision constructs.";

/* clang-format off */
typedef struct {
	PyObject_HEAD
	SHA1_CTX ctx;
} pysha1ctx;
/* clang-format on */

static int pysha1ctx_init(pysha1ctx *self, PyObject *args)
{
	Py_buffer data;
	data.obj = NULL;

	SHA1DCInit(&(self->ctx));
	/* We don't want "safe" sha1s, wherein sha1dc can give you a
	   different hash for something that's trying to give you a
	   collision. We just want to detect collisions.
	 */
	SHA1DCSetSafeHash(&(self->ctx), 0);
	if (!PyArg_ParseTuple(args, PY23("|s*", "|y*"), &data)) {
		return -1;
	}
	if (data.obj) {
		if (!PyBuffer_IsContiguous(&data, 'C') || data.ndim > 1) {
			PyErr_SetString(PyExc_BufferError,
			                "buffer must be contiguous and single dimension");
			PyBuffer_Release(&data);
			return -1;
		}

		SHA1DCUpdate(&(self->ctx), data.buf, data.len);
		PyBuffer_Release(&data);
	}
	return 0;
}

static void pysha1ctx_dealloc(pysha1ctx *self)
{
	PyObject_Del(self);
}

static PyObject *pysha1ctx_update(pysha1ctx *self, PyObject *args)
{
	Py_buffer data;
	if (!PyArg_ParseTuple(args, PY23("s*", "y*"), &data)) {
		return NULL;
	}
	if (!PyBuffer_IsContiguous(&data, 'C') || data.ndim > 1) {
		PyErr_SetString(PyExc_BufferError,
		                "buffer must be contiguous and single dimension");
		PyBuffer_Release(&data);
		return NULL;
	}
	SHA1DCUpdate(&(self->ctx), data.buf, data.len);
	PyBuffer_Release(&data);
	Py_RETURN_NONE;
}

/* it is intentional that this take a ctx by value, as that clones the
   context so we can keep using .update() without poisoning the state
   with padding.
*/
static int finalize(SHA1_CTX ctx, unsigned char *hash_out)
{
	if (SHA1DCFinal(hash_out, &ctx)) {
		PyErr_SetString(PyExc_OverflowError,
		                "sha1 collision attack detected");
		return 0;
	}
	return 1;
}

static PyObject *pysha1ctx_digest(pysha1ctx *self)
{
	unsigned char hash[20];
	if (!finalize(self->ctx, hash)) {
		return NULL;
	}
	return PyBytes_FromStringAndSize((char *)hash, 20);
}

static PyObject *pysha1ctx_hexdigest(pysha1ctx *self)
{
	static const char hexdigit[] = "0123456789abcdef";
	unsigned char hash[20];
	char hexhash[40];
	int i;
	if (!finalize(self->ctx, hash)) {
		return NULL;
	}
	for (i = 0; i < 20; ++i) {
		hexhash[i * 2] = hexdigit[hash[i] >> 4];
		hexhash[i * 2 + 1] = hexdigit[hash[i] & 15];
	}
	return PY23(PyString_FromStringAndSize, PyUnicode_FromStringAndSize)(hexhash, 40);
}

static PyTypeObject sha1ctxType;

static PyObject *pysha1ctx_copy(pysha1ctx *self)
{
	pysha1ctx *clone = (pysha1ctx *)PyObject_New(pysha1ctx, &sha1ctxType);
	if (!clone) {
		return NULL;
	}
	clone->ctx = self->ctx;
	return (PyObject *)clone;
}

static PyMethodDef pysha1ctx_methods[] = {
    {"update", (PyCFunction)pysha1ctx_update, METH_VARARGS,
     "Update this hash object's state with the provided bytes."},
    {"digest", (PyCFunction)pysha1ctx_digest, METH_NOARGS,
     "Return the digest value as a string of binary data."},
    {"hexdigest", (PyCFunction)pysha1ctx_hexdigest, METH_NOARGS,
     "Return the digest value as a string of hexadecimal digits."},
    {"copy", (PyCFunction)pysha1ctx_copy, METH_NOARGS,
     "Return a copy of the hash object."},
    {NULL},
};

/* clang-format off */
static PyTypeObject sha1ctxType = {
	PyVarObject_HEAD_INIT(NULL, 0)                    /* header */
	"sha1dc.sha1",                                    /* tp_name */
	sizeof(pysha1ctx),                                /* tp_basicsize */
	0,                                                /* tp_itemsize */
	(destructor)pysha1ctx_dealloc,                    /* tp_dealloc */
	0,                                                /* tp_print */
	0,                                                /* tp_getattr */
	0,                                                /* tp_setattr */
	0,                                                /* tp_compare */
	0,                                                /* tp_repr */
	0,                                                /* tp_as_number */
	0,                                                /* tp_as_sequence */
	0,                                                /* tp_as_mapping */
	0,                                                /* tp_hash */
	0,                                                /* tp_call */
	0,                                                /* tp_str */
	0,                                                /* tp_getattro */
	0,                                                /* tp_setattro */
	0,                                                /* tp_as_buffer */
	Py_TPFLAGS_DEFAULT,                               /* tp_flags */
	"sha1 implementation that looks for collisions",  /* tp_doc */
	0,                                                /* tp_traverse */
	0,                                                /* tp_clear */
	0,                                                /* tp_richcompare */
	0,                                                /* tp_weaklistoffset */
	0,                                                /* tp_iter */
	0,                                                /* tp_iternext */
	pysha1ctx_methods,                                /* tp_methods */
	0,                                                /* tp_members */
	0,                                                /* tp_getset */
	0,                                                /* tp_base */
	0,                                                /* tp_dict */
	0,                                                /* tp_descr_get */
	0,                                                /* tp_descr_set */
	0,                                                /* tp_dictoffset */
	(initproc)pysha1ctx_init,                         /* tp_init */
	0,                                                /* tp_alloc */
};
/* clang-format on */

static PyMethodDef methods[] = {
    {NULL, NULL},
};

static void module_init(PyObject *mod)
{
	sha1ctxType.tp_new = PyType_GenericNew;
	if (PyType_Ready(&sha1ctxType) < 0) {
		return;
	}
	Py_INCREF(&sha1ctxType);

	PyModule_AddObject(mod, "sha1", (PyObject *)&sha1ctxType);
}

#ifdef IS_PY3K
static struct PyModuleDef sha1dc_module = {PyModuleDef_HEAD_INIT, "sha1dc",
                                           sha1dc_doc, -1, methods};

PyMODINIT_FUNC PyInit_sha1dc(void)
{
	PyObject *mod = PyModule_Create(&sha1dc_module);
	module_init(mod);
	return mod;
}
#else
PyMODINIT_FUNC initsha1dc(void)
{
	PyObject *mod = Py_InitModule3("sha1dc", methods, sha1dc_doc);
	module_init(mod);
}
#endif
