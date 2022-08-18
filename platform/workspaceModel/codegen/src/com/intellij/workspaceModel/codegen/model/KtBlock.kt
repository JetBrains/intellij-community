// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

open class KtBlock(
  val src: Src,
  val parent: KtBlock?,
  val isStub: Boolean = false,
  val scope: KtScope? = null,
) {
  val children = mutableListOf<KtBlock>()
  val defs = mutableListOf<DefField>()
  var _generatedCode: IntRange? = null
  var _extensionCode: IntRange? = null
  var range: SrcRange? = null
  val text: String? get() = range?.text
}