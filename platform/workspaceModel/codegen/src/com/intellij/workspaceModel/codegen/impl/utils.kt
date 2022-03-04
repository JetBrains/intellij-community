package org.jetbrains.deft.impl

import org.jetbrains.deft.DataStorage
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.obj.impl.ObjImplWrapper

fun DataStorage.LoadResult.Loaded.toObj(graph: ObjStorageImpl.ObjGraph): ObjImplWrapper {
    if (objDebugMarkers) check(input.readLong() == objStartDebugMarker)
    val moduleId = input.readString()
    val typeIdInt = input.readInt()
    val typeId = ObjType.Id<Obj, ObjBuilder<Obj>>(ObjModule.Id(moduleId), typeIdInt)
    val obj = ObjModule.modules[typeId]._newInstance()
    val objImpl = (obj as ObjImplWrapper).impl
    objImpl.unfreeze(loading = true)
    objImpl._id = id
    objImpl.ensureInGraph(graph)
    objImpl.loadFrom(input)
    if (objDebugMarkers) check(input.readLong() == objEndDebugMarker)
    if (objDebugCheckLoaded) objImpl.freeze()
    return obj
}

fun <T : Obj> ObjType<T, *>.refType(): TRef<T> = TRef(module.id.notation, id)

val Obj._implObj: ObjImpl
    get() = when (this) {
        is ObjBuilderImpl<*> -> result
        else -> (this as ObjImplWrapper).impl
    } as ObjImpl