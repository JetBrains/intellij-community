// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

import com.intellij.openapi.vfs.VirtualFile

class KtFile(val module: KtObjModule, val content: () -> CharSequence, val name: String, val virtualFile: VirtualFile?) {
  override fun toString(): String = "[file://$name]"

  lateinit var pkg: KtPackage private set
  val scope = KtScope(null, this)

  init {
    scope.owner = this
  }

}