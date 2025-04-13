// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.psi.interfaces.EditorConfigHeaderElement
import org.editorconfig.language.util.requiredParentOfType

abstract class EditorConfigHeaderElementBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigHeaderElement {
  final override val section: EditorConfigSection
    get() = requiredParentOfType()

  final override val header: EditorConfigHeader
    get() = requiredParentOfType()
}
