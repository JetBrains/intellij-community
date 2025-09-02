// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigIdentifierElement
import com.intellij.lang.ASTNode

abstract class EditorConfigIdentifierElementBase(node: ASTNode) : EditorConfigDescribableElementBase(node), EditorConfigIdentifierElement {
  final override fun getName(): String = text
  final override fun getNameIdentifier(): EditorConfigIdentifierElementBase = this
}
