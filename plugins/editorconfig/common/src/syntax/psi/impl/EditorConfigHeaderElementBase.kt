// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeaderElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

abstract class EditorConfigHeaderElementBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigHeaderElement {
  final override val section: EditorConfigSection
    get() = requiredParentOfType()

  final override val header: EditorConfigHeader
    get() = requiredParentOfType()
}
