package com.intellij.workspaceModel.codegen

import org.jetbrains.deft.Obj
import org.jetbrains.deft.Type
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.deft.TStructure
import com.intellij.workspaceModel.codegen.deft.Field

val Type<*, *>.javaFullName
  get() = fqn(packageName, name)

val Type<*, *>.javaSimpleName
  get() = name.substringAfterLast('.')

val Type<*, *>.javaBuilderName
  get() = "$name.Builder"

val Type<*, *>.javaImplName
  get() = "${name.replace(".", "")}Impl"

val Type<*, *>.javaImplFqn
  get() = fqn(packageName, javaImplName)

val Type<*, *>.javaImplBuilderName
  get() = "${javaImplName}.Builder"

val Type<*, *>.javaSuperType
  get() = if (base == null) "Obj" else base!!.javaSimpleName

val Type<*, *>.javaImplSuperType
  get() = if (base == null) "ObjImpl" else base!!.javaImplFqn

val TStructure<*, *>.fieldsToStore: List<Field<out Obj, Any?>>
  get() = newFields.filter { !it.isOverride && it.hasDefault == Field.Default.none }

val TStructure<*, *>.builderFields: List<Field<out Obj, Any?>>
  get() = allFields.filter { it.hasDefault == Field.Default.none }

val TStructure<*, *>.allNonSystemFields: List<Field<out Obj, Any?>>
  get() = allFields.filter { it.name != "parent" && it.name != "name" }
