package org.jetbrains.deft

import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.fields.Field

interface Struct

// used as type parameter bounds in RelationsBuilder to workaround conflicts after generics erasing.
interface _Obj0 : Struct
interface _Obj1 : _Obj0

interface Obj : _Obj1 {
    val name: String?
}

interface ObjBuilder<T : Obj> {
    fun build(): T
}
