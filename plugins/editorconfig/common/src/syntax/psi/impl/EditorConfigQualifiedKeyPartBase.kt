// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedKeyPart
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

abstract class EditorConfigQualifiedKeyPartBase(node: ASTNode) : EditorConfigIdentifierElementBase(node), EditorConfigQualifiedKeyPart {

  final override fun setName(newName: String): PsiElement {
    val factory = EditorConfigElementFactory.getInstance(project)
    val result = factory.createKeyPart(newName)
    replace(result)
    return result
  }
}
