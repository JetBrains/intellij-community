package org.jetbrains.deft.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder

abstract class ObjType1<T : Obj, B : ObjBuilder<T>, P>(module: ObjModule, id: Int) : ObjType<T, B>(module, id) {
    abstract fun setArg(obj: B, value: P)

    inline operator fun invoke(
        target: P,
        init: B.() -> Unit = {},
    ) = invoke {
        setArg(this, target)
        init()
    }
}