/*
 * _inotify.c - Python extension interfacing to the Linux inotify subsystem
 *
 * Copyright 2006 Bryan O'Sullivan <bos@serpentine.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of version 2.1 of the GNU Lesser General
 * Public License or any later version.
 */

#include <Python.h>
#include <alloca.h>
#include <sys/inotify.h>
#include <stdint.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <util.h>

/* Variables used in the event string representation */
static PyObject *join;
static PyObject *er_wm;
static PyObject *er_wmc;
static PyObject *er_wmn;
static PyObject *er_wmcn;

static PyObject *init(PyObject *self, PyObject *args)
{
	PyObject *ret = NULL;
	int fd = -1;

	if (!PyArg_ParseTuple(args, ":init"))
		goto bail;

	Py_BEGIN_ALLOW_THREADS;
	fd = inotify_init();
	Py_END_ALLOW_THREADS;

	if (fd == -1) {
		PyErr_SetFromErrno(PyExc_OSError);
		goto bail;
	}

	ret = PyInt_FromLong(fd);
	if (ret == NULL)
		goto bail;

	goto done;

bail:
	if (fd != -1)
		close(fd);

	Py_CLEAR(ret);

done:
	return ret;
}

PyDoc_STRVAR(
	init_doc,
	"init() -> fd\n"
	"\n"
	"Initialize an inotify instance.\n"
	"Return a file descriptor associated with a new inotify event queue.");

static PyObject *add_watch(PyObject *self, PyObject *args)
{
	PyObject *ret = NULL;
	uint32_t mask;
	int wd = -1;
	char *path;
	int fd;

	if (!PyArg_ParseTuple(args, "isI:add_watch", &fd, &path, &mask))
		goto bail;

	Py_BEGIN_ALLOW_THREADS;
	wd = inotify_add_watch(fd, path, mask);
	Py_END_ALLOW_THREADS;

	if (wd == -1) {
		PyErr_SetFromErrnoWithFilename(PyExc_OSError, path);
		goto bail;
	}

	ret = PyInt_FromLong(wd);
	if (ret == NULL)
		goto bail;

	goto done;

bail:
	if (wd != -1)
		inotify_rm_watch(fd, wd);

	Py_CLEAR(ret);

done:
	return ret;
}

PyDoc_STRVAR(
	add_watch_doc,
	"add_watch(fd, path, mask) -> wd\n"
	"\n"
	"Add a watch to an inotify instance, or modify an existing watch.\n"
	"\n"
	"        fd: file descriptor returned by init()\n"
	"        path: path to watch\n"
	"        mask: mask of events to watch for\n"
	"\n"
	"Return a unique numeric watch descriptor for the inotify instance\n"
	"mapped by the file descriptor.");

static PyObject *remove_watch(PyObject *self, PyObject *args)
{
	uint32_t wd;
	int fd;
	int r;

	if (!PyArg_ParseTuple(args, "iI:remove_watch", &fd, &wd))
		return NULL;

	Py_BEGIN_ALLOW_THREADS;
	r = inotify_rm_watch(fd, wd);
	Py_END_ALLOW_THREADS;

	if (r == -1) {
		PyErr_SetFromErrno(PyExc_OSError);
		return NULL;
	}

	Py_INCREF(Py_None);
	return Py_None;
}

PyDoc_STRVAR(
	remove_watch_doc,
	"remove_watch(fd, wd)\n"
	"\n"
	"        fd: file descriptor returned by init()\n"
	"        wd: watch descriptor returned by add_watch()\n"
	"\n"
	"Remove a watch associated with the watch descriptor wd from the\n"
	"inotify instance associated with the file descriptor fd.\n"
	"\n"
	"Removing a watch causes an IN_IGNORED event to be generated for this\n"
	"watch descriptor.");

#define bit_name(x) {x, #x}

static struct {
	int bit;
	const char *name;
	PyObject *pyname;
} bit_names[] = {
	bit_name(IN_ACCESS),
	bit_name(IN_MODIFY),
	bit_name(IN_ATTRIB),
	bit_name(IN_CLOSE_WRITE),
	bit_name(IN_CLOSE_NOWRITE),
	bit_name(IN_OPEN),
	bit_name(IN_MOVED_FROM),
	bit_name(IN_MOVED_TO),
	bit_name(IN_CREATE),
	bit_name(IN_DELETE),
	bit_name(IN_DELETE_SELF),
	bit_name(IN_MOVE_SELF),
	bit_name(IN_UNMOUNT),
	bit_name(IN_Q_OVERFLOW),
	bit_name(IN_IGNORED),
	bit_name(IN_ONLYDIR),
	bit_name(IN_DONT_FOLLOW),
	bit_name(IN_MASK_ADD),
	bit_name(IN_ISDIR),
	bit_name(IN_ONESHOT),
	{0}
};

static PyObject *decode_mask(int mask)
{
	PyObject *ret = PyList_New(0);
	int i;

	if (ret == NULL)
		goto bail;

	for (i = 0; bit_names[i].bit; i++) {
		if (mask & bit_names[i].bit) {
			if (bit_names[i].pyname == NULL) {
				bit_names[i].pyname = PyString_FromString(bit_names[i].name);
				if (bit_names[i].pyname == NULL)
					goto bail;
			}
			Py_INCREF(bit_names[i].pyname);
			if (PyList_Append(ret, bit_names[i].pyname) == -1)
				goto bail;
		}
	}

	goto done;

bail:
	Py_CLEAR(ret);

done:
	return ret;
}

static PyObject *pydecode_mask(PyObject *self, PyObject *args)
{
	int mask;

	if (!PyArg_ParseTuple(args, "i:decode_mask", &mask))
		return NULL;

	return decode_mask(mask);
}

PyDoc_STRVAR(
	decode_mask_doc,
	"decode_mask(mask) -> list_of_strings\n"
	"\n"
	"Decode an inotify mask value into a list of strings that give the\n"
	"name of each bit set in the mask.");

static char doc[] = "Low-level inotify interface wrappers.";

static void define_const(PyObject *dict, const char *name, uint32_t val)
{
	PyObject *pyval = PyInt_FromLong(val);
	PyObject *pyname = PyString_FromString(name);

	if (!pyname || !pyval)
		goto bail;

	PyDict_SetItem(dict, pyname, pyval);

bail:
	Py_XDECREF(pyname);
	Py_XDECREF(pyval);
}

static void define_consts(PyObject *dict)
{
	define_const(dict, "IN_ACCESS", IN_ACCESS);
	define_const(dict, "IN_MODIFY", IN_MODIFY);
	define_const(dict, "IN_ATTRIB", IN_ATTRIB);
	define_const(dict, "IN_CLOSE_WRITE", IN_CLOSE_WRITE);
	define_const(dict, "IN_CLOSE_NOWRITE", IN_CLOSE_NOWRITE);
	define_const(dict, "IN_OPEN", IN_OPEN);
	define_const(dict, "IN_MOVED_FROM", IN_MOVED_FROM);
	define_const(dict, "IN_MOVED_TO", IN_MOVED_TO);

	define_const(dict, "IN_CLOSE", IN_CLOSE);
	define_const(dict, "IN_MOVE", IN_MOVE);

	define_const(dict, "IN_CREATE", IN_CREATE);
	define_const(dict, "IN_DELETE", IN_DELETE);
	define_const(dict, "IN_DELETE_SELF", IN_DELETE_SELF);
	define_const(dict, "IN_MOVE_SELF", IN_MOVE_SELF);
	define_const(dict, "IN_UNMOUNT", IN_UNMOUNT);
	define_const(dict, "IN_Q_OVERFLOW", IN_Q_OVERFLOW);
	define_const(dict, "IN_IGNORED", IN_IGNORED);

	define_const(dict, "IN_ONLYDIR", IN_ONLYDIR);
	define_const(dict, "IN_DONT_FOLLOW", IN_DONT_FOLLOW);
	define_const(dict, "IN_MASK_ADD", IN_MASK_ADD);
	define_const(dict, "IN_ISDIR", IN_ISDIR);
	define_const(dict, "IN_ONESHOT", IN_ONESHOT);
	define_const(dict, "IN_ALL_EVENTS", IN_ALL_EVENTS);
}

struct event {
	PyObject_HEAD
	PyObject *wd;
	PyObject *mask;
	PyObject *cookie;
	PyObject *name;
};

static PyObject *event_wd(PyObject *self, void *x)
{
	struct event *evt = (struct event *)self;
	Py_INCREF(evt->wd);
	return evt->wd;
}

static PyObject *event_mask(PyObject *self, void *x)
{
	struct event *evt = (struct event *)self;
	Py_INCREF(evt->mask);
	return evt->mask;
}

static PyObject *event_cookie(PyObject *self, void *x)
{
	struct event *evt = (struct event *)self;
	Py_INCREF(evt->cookie);
	return evt->cookie;
}

static PyObject *event_name(PyObject *self, void *x)
{
	struct event *evt = (struct event *)self;
	Py_INCREF(evt->name);
	return evt->name;
}

static struct PyGetSetDef event_getsets[] = {
	{"wd", event_wd, NULL,
	 "watch descriptor"},
	{"mask", event_mask, NULL,
	 "event mask"},
	{"cookie", event_cookie, NULL,
	 "rename cookie, if rename-related event"},
	{"name", event_name, NULL,
	 "file name"},
	{NULL}
};

PyDoc_STRVAR(
	event_doc,
	"event: Structure describing an inotify event.");

static PyObject *event_new(PyTypeObject *t, PyObject *a, PyObject *k)
{
	return (*t->tp_alloc)(t, 0);
}

static void event_dealloc(struct event *evt)
{
	Py_XDECREF(evt->wd);
	Py_XDECREF(evt->mask);
	Py_XDECREF(evt->cookie);
	Py_XDECREF(evt->name);

	Py_TYPE(evt)->tp_free(evt);
}

static PyObject *event_repr(struct event *evt)
{
	int cookie = evt->cookie == Py_None ? -1 : PyInt_AsLong(evt->cookie);
	PyObject *ret = NULL, *pymasks = NULL, *pymask = NULL;
	PyObject *tuple = NULL, *formatstr = NULL;

	pymasks = decode_mask(PyInt_AsLong(evt->mask));
	if (pymasks == NULL)
		goto bail;

	pymask = _PyString_Join(join, pymasks);
	if (pymask == NULL)
		goto bail;

	if (evt->name != Py_None) {
		if (cookie == -1) {
			formatstr = er_wmn;
			tuple = PyTuple_Pack(3, evt->wd, pymask, evt->name);
		}
		else {
			formatstr = er_wmcn;
			tuple = PyTuple_Pack(4, evt->wd, pymask,
					     evt->cookie, evt->name);
		}
	} else {
		if (cookie == -1) {
			formatstr = er_wm;
			tuple = PyTuple_Pack(2, evt->wd, pymask);
		}
		else {
			formatstr = er_wmc;
			tuple = PyTuple_Pack(3, evt->wd, pymask, evt->cookie);
		}
	}

	if (tuple == NULL)
		goto bail;

	ret = PyNumber_Remainder(formatstr, tuple);

	if (ret == NULL)
		goto bail;

	goto done;
bail:
	Py_CLEAR(ret);

done:
	Py_XDECREF(pymask);
	Py_XDECREF(pymasks);
	Py_XDECREF(tuple);

	return ret;
}

static PyTypeObject event_type = {
	PyVarObject_HEAD_INIT(NULL, 0)
	"_inotify.event",             /*tp_name*/
	sizeof(struct event), /*tp_basicsize*/
	0,                         /*tp_itemsize*/
	(destructor)event_dealloc, /*tp_dealloc*/
	0,                         /*tp_print*/
	0,                         /*tp_getattr*/
	0,                         /*tp_setattr*/
	0,                         /*tp_compare*/
	(reprfunc)event_repr,      /*tp_repr*/
	0,                         /*tp_as_number*/
	0,                         /*tp_as_sequence*/
	0,                         /*tp_as_mapping*/
	0,                         /*tp_hash */
	0,                         /*tp_call*/
	0,                         /*tp_str*/
	0,                         /*tp_getattro*/
	0,                         /*tp_setattro*/
	0,                         /*tp_as_buffer*/
	Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, /*tp_flags*/
	event_doc,           /* tp_doc */
	0,                         /* tp_traverse */
	0,                         /* tp_clear */
	0,                         /* tp_richcompare */
	0,                         /* tp_weaklistoffset */
	0,                         /* tp_iter */
	0,                         /* tp_iternext */
	0,                         /* tp_methods */
	0,                         /* tp_members */
	event_getsets,      /* tp_getset */
	0,                         /* tp_base */
	0,                         /* tp_dict */
	0,                         /* tp_descr_get */
	0,                         /* tp_descr_set */
	0,                         /* tp_dictoffset */
	0,                         /* tp_init */
	0,                         /* tp_alloc */
	event_new,          /* tp_new */
};

PyObject *read_events(PyObject *self, PyObject *args)
{
	PyObject *ctor_args = NULL;
	PyObject *pybufsize = NULL;
	PyObject *ret = NULL;
	int bufsize = 65536;
	char *buf = NULL;
	int nread, pos;
	int fd;

	if (!PyArg_ParseTuple(args, "i|O:read", &fd, &pybufsize))
		goto bail;

	if (pybufsize && pybufsize != Py_None)
		bufsize = PyInt_AsLong(pybufsize);

	ret = PyList_New(0);
	if (ret == NULL)
		goto bail;

	if (bufsize <= 0) {
		int r;

		Py_BEGIN_ALLOW_THREADS;
		r = ioctl(fd, FIONREAD, &bufsize);
		Py_END_ALLOW_THREADS;

		if (r == -1) {
			PyErr_SetFromErrno(PyExc_OSError);
			goto bail;
		}
		if (bufsize == 0)
			goto done;
	}
	else {
		static long name_max;
		static long name_fd = -1;
		long min;

		if (name_fd != fd) {
			name_fd = fd;
			Py_BEGIN_ALLOW_THREADS;
			name_max = fpathconf(fd, _PC_NAME_MAX);
			Py_END_ALLOW_THREADS;
		}

		min = sizeof(struct inotify_event) + name_max + 1;

		if (bufsize < min) {
			PyErr_Format(PyExc_ValueError,
				     "bufsize must be at least %d", (int)min);
			goto bail;
		}
	}

	buf = alloca(bufsize);

	Py_BEGIN_ALLOW_THREADS;
	nread = read(fd, buf, bufsize);
	Py_END_ALLOW_THREADS;

	if (nread == -1) {
		PyErr_SetFromErrno(PyExc_OSError);
		goto bail;
	}

	ctor_args = PyTuple_New(0);

	if (ctor_args == NULL)
		goto bail;

	pos = 0;

	while (pos < nread) {
		struct inotify_event *in = (struct inotify_event *)(buf + pos);
		struct event *evt;
		PyObject *obj;

		obj = PyObject_CallObject((PyObject *)&event_type, ctor_args);

		if (obj == NULL)
			goto bail;

		evt = (struct event *)obj;

		evt->wd = PyInt_FromLong(in->wd);
		evt->mask = PyInt_FromLong(in->mask);
		if (in->mask & IN_MOVE)
			evt->cookie = PyInt_FromLong(in->cookie);
		else {
			Py_INCREF(Py_None);
			evt->cookie = Py_None;
		}
		if (in->len)
			evt->name = PyString_FromString(in->name);
		else {
			Py_INCREF(Py_None);
			evt->name = Py_None;
		}

		if (!evt->wd || !evt->mask || !evt->cookie || !evt->name)
			goto mybail;

		if (PyList_Append(ret, obj) == -1)
			goto mybail;

		pos += sizeof(struct inotify_event) + in->len;
		continue;

	mybail:
		Py_CLEAR(evt->wd);
		Py_CLEAR(evt->mask);
		Py_CLEAR(evt->cookie);
		Py_CLEAR(evt->name);
		Py_DECREF(obj);

		goto bail;
	}

	goto done;

bail:
	Py_CLEAR(ret);

done:
	Py_XDECREF(ctor_args);

	return ret;
}

static int init_globals(void)
{
	join = PyString_FromString("|");
	er_wm = PyString_FromString("event(wd=%d, mask=%s)");
	er_wmn = PyString_FromString("event(wd=%d, mask=%s, name=%s)");
	er_wmc = PyString_FromString("event(wd=%d, mask=%s, cookie=0x%x)");
	er_wmcn = PyString_FromString("event(wd=%d, mask=%s, cookie=0x%x, name=%s)");

	return join && er_wm && er_wmn && er_wmc && er_wmcn;
}

PyDoc_STRVAR(
	read_doc,
	"read(fd, bufsize[=65536]) -> list_of_events\n"
	"\n"
	"\nRead inotify events from a file descriptor.\n"
	"\n"
	"        fd: file descriptor returned by init()\n"
	"        bufsize: size of buffer to read into, in bytes\n"
	"\n"
	"Return a list of event objects.\n"
	"\n"
	"If bufsize is > 0, block until events are available to be read.\n"
	"Otherwise, immediately return all events that can be read without\n"
	"blocking.");

static PyMethodDef methods[] = {
	{"init", init, METH_VARARGS, init_doc},
	{"add_watch", add_watch, METH_VARARGS, add_watch_doc},
	{"remove_watch", remove_watch, METH_VARARGS, remove_watch_doc},
	{"read", read_events, METH_VARARGS, read_doc},
	{"decode_mask", pydecode_mask, METH_VARARGS, decode_mask_doc},
	{NULL},
};

#ifdef IS_PY3K
static struct PyModuleDef _inotify_module = {
	PyModuleDef_HEAD_INIT,
	"_inotify",
	doc,
	-1,
	methods
};

PyMODINIT_FUNC PyInit__inotify(void)
{
	PyObject *mod, *dict;

	mod = PyModule_Create(&_inotify_module);

	if (mod == NULL)
		return NULL;

	if (!init_globals())
		return;

	dict = PyModule_GetDict(mod);

	if (dict)
		define_consts(dict);

	return mod;
}
#else
void init_inotify(void)
{
	PyObject *mod, *dict;

	if (PyType_Ready(&event_type) == -1)
		return;

	if (!init_globals())
		return;

	mod = Py_InitModule3("_inotify", methods, doc);

	dict = PyModule_GetDict(mod);

	if (dict)
		define_consts(dict);
}
#endif
