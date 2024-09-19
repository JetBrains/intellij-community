/*
 revlog.h - efficient revlog parsing

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.
*/

#ifndef _HG_REVLOG_H_
#define _HG_REVLOG_H_

#include <Python.h>

extern PyTypeObject HgRevlogIndex_Type;

#define HgRevlogIndex_Check(op) PyObject_TypeCheck(op, &HgRevlogIndex_Type)

#endif /* _HG_REVLOG_H_ */
