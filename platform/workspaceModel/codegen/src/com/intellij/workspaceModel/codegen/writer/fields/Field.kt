package com.intellij.workspaceModel.codegen

import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.writer.javaName
import com.intellij.workspaceModel.codegen.writer.type

val ObjProperty<*, *>.implFieldName: String
  get() = when (type) {
    is ValueType.Int, is ValueType.Boolean -> javaName

    is ValueType.Optional<*> -> {
      when ((type as ValueType.Optional<*>).type) {
        is ValueType.Int, is ValueType.Boolean -> javaName
        else -> "_$javaName"
      }
    }

    else -> "_$javaName"
  }
