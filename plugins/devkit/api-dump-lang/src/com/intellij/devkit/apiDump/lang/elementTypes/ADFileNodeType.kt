// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.elementTypes

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IFileElementType
import com.intellij.devkit.apiDump.lang.ADLanguage
import com.intellij.devkit.apiDump.lang.ast.ADFileNode

internal object ADFileNodeType : IFileElementType(ADLanguage) {
  override fun createNode(text: CharSequence?): ASTNode =
    ADFileNode(text)
}