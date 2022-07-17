package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.deft.ExtField
import com.intellij.workspaceModel.codegen.getRefType
import com.intellij.workspaceModel.codegen.javaBuilderName
import com.intellij.workspaceModel.codegen.utils.fqn
import com.intellij.workspaceModel.codegen.utils.lines
import org.jetbrains.deft.annotations.Child

val ExtField<*, *>.wsCode: String
  get() = lines {
    val isChild = type.getRefType().child
    val annotation = if (isChild) "@${Child::class.fqn} " else ""
    sectionNoBrackets("var ${owner.javaBuilderName}.$name: $annotation${type.javaType}") {
      line("by WorkspaceEntity.extension()")
    }
  }
