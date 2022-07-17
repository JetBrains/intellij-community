package com.intellij.workspaceModel.codegen.deft

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

abstract class ObjType<T : Obj, B : ObjBuilder<T>>(private val _module: ObjModule, val id: Int, base: ObjType<*, *>? = null) : Type<T, B>(
  base), Obj {
  val module: ObjModule
    get() = _module.require()

  data class Id(val id: Int)

  val structure: TStructure<T, B> = TStructure(this, base?.structure)

  override val name: String
    get() = super.name

  fun link(linker: ObjModule) {
    structure.link(linker)
  }
}