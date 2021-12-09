// Header file providing new functions of the Python C API to old Python
// versions.
//
// File distributed under the MIT license.
//
// Homepage:
// https://github.com/pythoncapi/pythoncapi_compat
//
// Latest version:
// https://raw.githubusercontent.com/pythoncapi/pythoncapi_compat/master/pythoncapi_compat.h

#ifndef PYTHONCAPI_COMPAT
#define PYTHONCAPI_COMPAT

#ifdef __cplusplus
extern "C" {
#endif

#include <Python.h>
#include "frameobject.h"          // PyFrameObject, PyFrame_GetBack()


/* VC 2008 doesn't know about the inline keyword. */
#if defined(_MSC_VER) && _MSC_VER < 1900
#define inline __forceinline
#endif

// Cast argument to PyObject* type.
#ifndef _PyObject_CAST
#  define _PyObject_CAST(op) ((PyObject*)(op))
#endif


// bpo-42262 added Py_NewRef() to Python 3.10.0a3
#if PY_VERSION_HEX < 0x030a00A3 && !defined(Py_NewRef)
static inline PyObject* _Py_NewRef(PyObject *obj)
{
    Py_INCREF(obj);
    return obj;
}
#define Py_NewRef(obj) _Py_NewRef(_PyObject_CAST(obj))
#endif


// bpo-42262 added Py_XNewRef() to Python 3.10.0a3
#if PY_VERSION_HEX < 0x030a00A3 && !defined(Py_XNewRef)
static inline PyObject* _Py_XNewRef(PyObject *obj)
{
    Py_XINCREF(obj);
    return obj;
}
#define Py_XNewRef(obj) _Py_XNewRef(_PyObject_CAST(obj))
#endif


// bpo-39573 added Py_SET_REFCNT() to Python 3.9.0a4
#if PY_VERSION_HEX < 0x030900A4 && !defined(Py_SET_REFCNT)
static inline void _Py_SET_REFCNT(PyObject *ob, Py_ssize_t refcnt)
{
    ob->ob_refcnt = refcnt;
}
#define Py_SET_REFCNT(ob, refcnt) _Py_SET_REFCNT((PyObject*)(ob), refcnt)
#endif


// bpo-39573 added Py_SET_TYPE() to Python 3.9.0a4
#if PY_VERSION_HEX < 0x030900A4 && !defined(Py_SET_TYPE)
static inline void
_Py_SET_TYPE(PyObject *ob, PyTypeObject *type)
{
    ob->ob_type = type;
}
#define Py_SET_TYPE(ob, type) _Py_SET_TYPE((PyObject*)(ob), type)
#endif


// bpo-39573 added Py_SET_SIZE() to Python 3.9.0a4
#if PY_VERSION_HEX < 0x030900A4 && !defined(Py_SET_SIZE)
static inline void
_Py_SET_SIZE(PyVarObject *ob, Py_ssize_t size)
{
    ob->ob_size = size;
}
#define Py_SET_SIZE(ob, size) _Py_SET_SIZE((PyVarObject*)(ob), size)
#endif


// bpo-40421 added PyFrame_GetCode() to Python 3.9.0b1
#if PY_VERSION_HEX < 0x030900B1
static inline PyCodeObject*
PyFrame_GetCode(PyFrameObject *frame)
{
    PyCodeObject *code;
    assert(frame != NULL);
    code = frame->f_code;
    assert(code != NULL);
    Py_INCREF(code);
    return code;
}
#endif

static inline PyCodeObject*
_PyFrame_GetCodeBorrow(PyFrameObject *frame)
{
    PyCodeObject *code = PyFrame_GetCode(frame);
    Py_DECREF(code);
    return code;  // borrowed reference
}


// bpo-40421 added PyFrame_GetCode() to Python 3.9.0b1
#if PY_VERSION_HEX < 0x030900B1
static inline PyFrameObject*
PyFrame_GetBack(PyFrameObject *frame)
{
    PyFrameObject *back;
    assert(frame != NULL);
    back = frame->f_back;
    Py_XINCREF(back);
    return back;
}
#endif

static inline PyFrameObject*
_PyFrame_GetBackBorrow(PyFrameObject *frame)
{
    PyFrameObject *back = PyFrame_GetBack(frame);
    Py_XDECREF(back);
    return back;  // borrowed reference
}


// bpo-39947 added PyThreadState_GetInterpreter() to Python 3.9.0a5
#if PY_VERSION_HEX < 0x030900A5
static inline PyInterpreterState *
PyThreadState_GetInterpreter(PyThreadState *tstate)
{
    assert(tstate != NULL);
    return tstate->interp;
}
#endif


// bpo-40429 added PyThreadState_GetFrame() to Python 3.9.0b1
#if PY_VERSION_HEX < 0x030900B1
static inline PyFrameObject*
PyThreadState_GetFrame(PyThreadState *tstate)
{
    PyFrameObject *frame;
    assert(tstate != NULL);
    frame = tstate->frame;
    Py_XINCREF(frame);
    return frame;
}
#endif

static inline PyFrameObject*
_PyThreadState_GetFrameBorrow(PyThreadState *tstate)
{
    PyFrameObject *frame = PyThreadState_GetFrame(tstate);
    Py_XDECREF(frame);
    return frame;  // borrowed reference
}


// bpo-39947 added PyInterpreterState_Get() to Python 3.9.0a5
#if PY_VERSION_HEX < 0x030900A5
static inline PyInterpreterState *
PyInterpreterState_Get(void)
{
    PyThreadState *tstate;
    PyInterpreterState *interp;

    tstate = PyThreadState_GET();
    if (tstate == NULL) {
        Py_FatalError("GIL released (tstate is NULL)");
    }
    interp = tstate->interp;
    if (interp == NULL) {
        Py_FatalError("no current interpreter");
    }
    return interp;
}
#endif


// bpo-39947 added PyInterpreterState_Get() to Python 3.9.0a6
#if 0x030700A1 <= PY_VERSION_HEX && PY_VERSION_HEX < 0x030900A6
static inline uint64_t
PyThreadState_GetID(PyThreadState *tstate)
{
    assert(tstate != NULL);
    return tstate->id;
}
#endif


// bpo-37194 added PyObject_CallNoArgs() to Python 3.9.0a1
#if PY_VERSION_HEX < 0x030900A1
static inline PyObject*
PyObject_CallNoArgs(PyObject *func)
{
    return PyObject_CallFunctionObjArgs(func, NULL);
}
#endif


// bpo-39245 made PyObject_CallOneArg() public (previously called
// _PyObject_CallOneArg) in Python 3.9.0a4
#if PY_VERSION_HEX < 0x030900A4
static inline PyObject*
PyObject_CallOneArg(PyObject *func, PyObject *arg)
{
    return PyObject_CallFunctionObjArgs(func, arg, NULL);
}
#endif


// bpo-40024 added PyModule_AddType() to Python 3.9.0a5
#if PY_VERSION_HEX < 0x030900A5
static inline int
PyModule_AddType(PyObject *module, PyTypeObject *type)
{
    const char *name, *dot;

    if (PyType_Ready(type) < 0) {
        return -1;
    }

    // inline _PyType_Name()
    name = type->tp_name;
    assert(name != NULL);
    dot = strrchr(name, '.');
    if (dot != NULL) {
        name = dot + 1;
    }

    Py_INCREF(type);
    if (PyModule_AddObject(module, name, (PyObject *)type) < 0) {
        Py_DECREF(type);
        return -1;
    }

    return 0;
}
#endif


// bpo-40241 added PyObject_GC_IsTracked() to Python 3.9.0a6.
// bpo-4688 added _PyObject_GC_IS_TRACKED() to Python 2.7.0a2.
#if PY_VERSION_HEX < 0x030900A6
static inline int
PyObject_GC_IsTracked(PyObject* obj)
{
    return (PyObject_IS_GC(obj) && _PyObject_GC_IS_TRACKED(obj));
}
#endif

// bpo-40241 added PyObject_GC_IsFinalized() to Python 3.9.0a6.
// bpo-18112 added _PyGCHead_FINALIZED() to Python 3.4.0 final.
#if PY_VERSION_HEX < 0x030900A6 && PY_VERSION_HEX >= 0x030400F0
static inline int
PyObject_GC_IsFinalized(PyObject *obj)
{
    return (PyObject_IS_GC(obj) && _PyGCHead_FINALIZED((PyGC_Head *)(obj)-1));
}
#endif


// bpo-39573 added Py_IS_TYPE() to Python 3.9.0a4
#if PY_VERSION_HEX < 0x030900A4 && !defined(Py_IS_TYPE)
static inline int
_Py_IS_TYPE(const PyObject *ob, const PyTypeObject *type) {
    return ob->ob_type == type;
}
#define Py_IS_TYPE(ob, type) _Py_IS_TYPE((const PyObject*)(ob), type)
#endif


#ifdef __cplusplus
}
#endif
#endif  // PYTHONCAPI_COMPAT
