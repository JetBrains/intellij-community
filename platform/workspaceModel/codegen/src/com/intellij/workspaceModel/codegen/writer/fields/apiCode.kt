package com.intellij.workspaceModel.codegen.fields

import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.fields.javaBuilderType
import com.intellij.workspaceModel.codegen.fields.javaType
import com.intellij.workspaceModel.codegen.deft.Field

val Field<*, *>.api: String
  get() = "${override(isOverride)}val $javaName: ${type.javaType}"

val Field<*, *>.builderApi: String
  get() = "override var $javaName: ${type.javaBuilderType}"
