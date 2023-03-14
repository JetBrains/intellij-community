package com.intellij.workspaceModel.codegen.writer.fields

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.writer.getRefType
import com.intellij.workspaceModel.codegen.writer.javaBuilderName
import com.intellij.workspaceModel.codegen.writer.fqn
import com.intellij.workspaceModel.codegen.writer.lines
import org.jetbrains.deft.annotations.Child

val ExtProperty<*, *>.wsCode: String
  get() = lines {
    val isChild = valueType.getRefType().child
    val annotation = if (isChild) "@${Child::class.fqn} " else ""
    sectionNoBrackets("var ${receiver.javaBuilderName}.$name: $annotation${valueType.javaType}") {
      line("by WorkspaceEntity.extension()")
    }
  }
