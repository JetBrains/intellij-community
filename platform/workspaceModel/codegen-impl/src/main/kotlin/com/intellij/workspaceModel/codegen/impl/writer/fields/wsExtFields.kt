package com.intellij.workspaceModel.codegen.impl.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.impl.writer.getRefType
import com.intellij.workspaceModel.codegen.impl.writer.javaBuilderName
import com.intellij.workspaceModel.codegen.impl.writer.fqn
import com.intellij.workspaceModel.codegen.impl.writer.lines
import org.jetbrains.deft.annotations.Child

val ExtProperty<*, *>.wsCode: String
  get() = lines {
    val isChild = valueType.getRefType().child
    val annotation = if (isChild) "@${Child::class.fqn} " else ""
    sectionNoBrackets("var ${receiver.javaBuilderName}.$name: $annotation${valueType.javaType}") {
      line("by WorkspaceEntity.extension()")
    }
  }
