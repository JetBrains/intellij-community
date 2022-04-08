package org.jetbrains.deft.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

abstract class ObjType<T : Obj, B : ObjBuilder<T>>(private val _module: ObjModule, id: Int, base: ObjType<*, *>? = null) : Type<T, B>(id, base), Obj {
    val module: ObjModule
        get() = _module.require()

    val fullId get() = Id<T, B>(module.id, id)

    data class Id<T : Obj, B : ObjBuilder<T>>(val module: ObjModule.Id, val id: Int)

    val structure: TStructure<T, B> = TStructure(this, base?.structure)

  override val name: String
    get() = super.name

  @ObjModule.InitApi
    fun link(linker: ObjModules) {
        structure.link(linker)
    }
}