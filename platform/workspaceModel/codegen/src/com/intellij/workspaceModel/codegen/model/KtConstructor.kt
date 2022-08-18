// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

open class KtConstructor(
  val scope: KtScope? = null,
) {
  val children = mutableListOf<KtConstructor>()
  val defs = mutableListOf<DefField>()
  var range: SrcRange? = null
  val text: String? get() = range?.text
}