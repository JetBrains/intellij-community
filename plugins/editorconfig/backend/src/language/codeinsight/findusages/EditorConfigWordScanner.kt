// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.findusages

import com.intellij.editorconfig.common.syntax.lexer.EditorConfigLexerAdapter
import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet

class EditorConfigWordScanner : DefaultWordsScanner(
  EditorConfigLexerAdapter(),
  TokenSet.create(EditorConfigElementTypes.IDENTIFIER),
  TokenSet.create(EditorConfigElementTypes.LINE_COMMENT),
  TokenSet.EMPTY
)
