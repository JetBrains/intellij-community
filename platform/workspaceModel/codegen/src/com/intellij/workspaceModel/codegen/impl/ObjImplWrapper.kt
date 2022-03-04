package org.jetbrains.deft.obj.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ObjImpl

interface ObjImplWrapper: Obj {
    val impl: ObjImpl
}