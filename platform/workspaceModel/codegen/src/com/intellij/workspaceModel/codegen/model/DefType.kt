package com.intellij.workspaceModel.codegen.deft.model

import com.intellij.workspaceModel.codegen.SKIPPED_TYPES
import com.intellij.workspaceModel.storage.EntitySource
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import com.intellij.workspaceModel.codegen.deft.ObjModule
import com.intellij.workspaceModel.codegen.deft.ObjType
import com.intellij.workspaceModel.codegen.deft.Field

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

  init {
    open = def.open
    abstract = def.abstract
    sealed = def.sealed
  }

  val utilityType get() = superTypes.any { it.name == EntitySource::class.simpleName } || name in SKIPPED_TYPES

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