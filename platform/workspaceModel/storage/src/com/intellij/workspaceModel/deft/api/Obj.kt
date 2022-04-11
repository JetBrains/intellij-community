package org.jetbrains.deft

interface Struct

// used as type parameter bounds in RelationsBuilder to workaround conflicts after generics erasing.
interface _Obj0 : Struct
interface _Obj1 : _Obj0

interface Obj : _Obj1

interface ObjBuilder<T : Obj> {
    fun build(): T
}
