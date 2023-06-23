package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.impl.writer.*

val ExtProperty<*, *>.wsCode: String
  get() = lines {
    val isChild = valueType.getRefType().child
    val annotation = if (isChild) "@${Child} " else ""
    sectionNoBrackets("var ${receiver.javaBuilderName}.$name: $annotation${valueType.javaType}") {
      line("by WorkspaceEntity.extension()")
    }
  }
