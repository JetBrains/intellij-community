package org.jetbrains.deft.codegen.model

import com.intellij.workspaceModel.codegen.skippedGenTypes
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.fields.Field

class DefType(
  module: ObjModule,
  override val name: String,
  base: DefType?,
  val def: KtInterface,
) : ObjType<Obj, ObjBuilder<Obj>>(
  module,
  def.module.nextTypeId(),
  base
) {
  override val packageName: String
    get() = def.file?.pkg?.fqn ?: ""

  val ktModule: KtObjModule get() = module as KtObjModule
  val subtypes = mutableListOf<DefType>()

  init {
    open = def.open
    abstract = def.abstract
    sealed = def.sealed
    base?.subtypes?.add(this)
  }

  val singleton get() = Object::class in def.annotations
  val utilityType get() = superTypes.any { it.name == EntitySource::class.simpleName } || name in skippedGenTypes

  fun verify(diagnostics: Diagnostics) {
    val base = base
    if (base != null) {
      if (!base.inheritanceAllowed) {
        diagnostics.add(
          def.nameRange,
          "Inheritance not allowed: $base is not `@Open` or `@Enum`"
        )
      }
    }

    structure.allFields.forEach {
      verify(diagnostics, it)
    }
  }

  private fun verify(diagnostics: Diagnostics, it: Field<out Obj, Any?>) {
    val base = it.base
    if (base != null) {
      if (!base.open) diagnostics.add(it.exDef!!.nameRange,
                                      "Inheritance not allowed: ${fieldDef(base)} is not `@Open`"
      )
    }
  }

  private fun fieldDef(field: Field<*, *>): String {
    val def = field.exDef
    return if (def != null) "`$def` of ${field.owner}" else "`$field`"
  }

  override fun toString(): String = "`$name` defined at ${def.nameRange.last}"
}