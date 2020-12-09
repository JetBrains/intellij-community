// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

class EditorConfigParser : EditorConfigParserBase() {
  override fun parse(t: IElementType, b: PsiBuilder): ASTNode {
    val data = EditorConfigSkippedWhitespaceData()
    b.putUserData(EditorConfigParserUtil.KEY, data)
    b.setWhitespaceSkippedCallback { type, start, end -> data.start = start; data.end = end }
    return super.parse(t, b)
  }
}