// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.lang.ASTNode
import org.editorconfig.language.psi.interfaces.EditorConfigIdentifierElement

abstract class EditorConfigIdentifierElementBase(node: ASTNode) : EditorConfigDescribableElementBase(node), EditorConfigIdentifierElement {
  final override fun getName(): String = text
  final override fun getNameIdentifier(): EditorConfigIdentifierElementBase = this
}
