// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

class KtPackage(val fqn: String?) {
  override fun toString(): String = "[package: ${fqn ?: "<anonymous>"}]"

  val scope = KtScope(null, this)
  val files = mutableListOf<KtFile>()

  init {
    scope.owner = this
  }
}