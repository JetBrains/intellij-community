// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.entityApi

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext
import com.intellij.workspaceModel.codegen.impl.dsl.annotation
import com.intellij.workspaceModel.codegen.impl.dsl.generateCode
import com.intellij.workspaceModel.codegen.impl.dsl.packageDirective
import com.intellij.workspaceModel.codegen.impl.writer.extensions.javaImplName

fun GeneratorContext.generateTopLevelCode(objClass: ObjClass<*>): String = generateCode {
  annotation("file:JvmName(\"${objClass.name}Modifications\")")
  packageDirective(objClass.module.name)
  if (objClass.openness.instantiatable) {
    +"import ${objClass.module.name}.impl.${objClass.javaImplName}"
  }
  generateBuilderCode(objClass)
  if (objClass.openness.instantiatable) {
    generateEntityTypeObject(objClass)
  }
  generateModifyAndExtensionCode(objClass)
  generateConstructorCode(objClass)
}
