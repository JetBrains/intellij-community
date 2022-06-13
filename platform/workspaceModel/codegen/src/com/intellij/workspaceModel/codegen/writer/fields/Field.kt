package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.TBoolean
import com.intellij.workspaceModel.codegen.deft.TInt
import com.intellij.workspaceModel.codegen.deft.TOptional
import com.intellij.workspaceModel.codegen.deft.Field
import com.intellij.workspaceModel.codegen.deft.MemberOrExtField
import java.util.*

val MemberOrExtField<*, *>.javaName: String
  get() = name

val MemberOrExtField<*, *>.implFieldName: String
  get() = when (type) {
    is TInt, is TBoolean -> javaName

    is TOptional<*> -> {
      when (type.type) {
        is TInt, is TBoolean -> javaName
        else -> "_$javaName"
      }
    }

    else -> "_$javaName"
  }

val Field<*, *>.suspendableGetterName: String
  get() = "get${javaName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"

val Field<*, *>.javaMetaName: String
  get() = if (javaName in reservedObjTypeNames) "${javaName}Field" else javaName

val Field<*, *>.isOverride: Boolean
  get() = base != null
          || name == "parent"
          || name == "name"

val reservedObjTypeNames = mutableSetOf(
  "factory",
  "name",
  "parent",
  "inheritanceAllowed",
  "module",
  "fullId",
  "structure",
  "ival",
  "ivar",
  "module"
)
