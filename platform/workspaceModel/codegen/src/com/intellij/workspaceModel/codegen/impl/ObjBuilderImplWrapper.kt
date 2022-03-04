package org.jetbrains.deft.obj.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ObjBuilderImpl
import org.jetbrains.deft.impl.ObjImpl

interface ObjBuilderImplWrapper<T : Obj>: ObjImplWrapper {
    val unsafeResultInstance: T

    override val impl: ObjImpl
        get() = (unsafeResultInstance as ObjImplWrapper).impl
}