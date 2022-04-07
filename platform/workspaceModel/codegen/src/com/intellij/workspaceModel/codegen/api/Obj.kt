package org.jetbrains.deft

import org.jetbrains.deft.impl.ExtensibleProvider
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.runtime.Runtime

interface Struct

// used as type parameter bounds in RelationsBuilder to workaround conflicts after generics erasing.
interface _Obj0 : Struct
interface _Obj1 : _Obj0

interface Obj : _Obj1 {
    val factory: ObjType<*, *>
    val name: String?
    val parent: Obj?

    fun <V> getValue(field: Field<*, V>): V = TODO()
}

interface TypeToken<T>

interface Root : Obj {
    interface Builder : Root, ObjBuilder<Root>, ExtensibleProvider
    companion object : ObjType<Root, Builder>(Runtime, 1)
}

interface ObjBuilder<T : Obj> {
    val factory: ObjType<T, *>
    fun build(): T
}
