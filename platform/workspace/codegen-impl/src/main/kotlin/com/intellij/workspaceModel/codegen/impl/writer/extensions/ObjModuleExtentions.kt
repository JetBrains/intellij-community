package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.ObjModule

internal val ObjModule.implPackage: String
  get() = "$name.impl"