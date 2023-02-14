package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.utils.QualifiedName
import com.intellij.workspaceModel.codegen.utils.fqn

val ObjClass<*>.javaFullName: QualifiedName
  get() = fqn(module.name, name)

val ObjClass<*>.javaBuilderName: String
  get() = "$name.Builder"

val ObjClass<*>.javaImplName: String
  get() = "${name.replace(".", "")}Impl"

val ObjClass<*>.javaImplBuilderName
  get() = "${javaImplName}.Builder"
