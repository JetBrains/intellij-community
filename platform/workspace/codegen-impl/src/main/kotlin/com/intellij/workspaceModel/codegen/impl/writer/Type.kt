package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass

val ObjClass<*>.javaFullName: QualifiedName
  get() = fqn(module.name, name)

val ObjClass<*>.javaBuilderName: String
  get() = "$name.Builder"

val ObjClass<*>.javaImplName: String
  get() = "${name.replace(".", "")}Impl"

val ObjClass<*>.javaImplBuilderName
  get() = "${javaImplName}.Builder"
