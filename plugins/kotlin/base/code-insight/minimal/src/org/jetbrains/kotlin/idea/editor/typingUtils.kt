// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunctionLiteral


fun isLambdaLBrace(file: PsiFile, offset: Int): Boolean {
    val element = file.findElementAt(offset)
    return element != null && element.getNode().getElementType() === KtTokens.LBRACE && element.getParent() is KtFunctionLiteral
}