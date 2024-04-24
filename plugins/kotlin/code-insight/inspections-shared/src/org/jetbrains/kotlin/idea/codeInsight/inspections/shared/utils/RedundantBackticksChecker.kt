// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils

import com.intellij.lang.ASTNode
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier

fun isRedundantBackticks(node: ASTNode): Boolean {
    val identifier = node.text
    if (!(identifier.startsWith("`") && identifier.endsWith("`"))) return false
    val unquotedText = identifier.unquoteKotlinIdentifier()
    if (!unquotedText.isIdentifier() || isKeyword(unquotedText)) return false
    val simpleNameStringTemplateEntry = node.psi.getStrictParentOfType<KtSimpleNameStringTemplateEntry>()
    return simpleNameStringTemplateEntry == null || canPlaceAfterSimpleNameEntry(simpleNameStringTemplateEntry.nextSibling)
}

private fun isKeyword(text: String): Boolean =
    text == "yield" || text.all { it == '_' } || (KtTokens.KEYWORDS.types + KtTokens.SOFT_KEYWORDS.types).any { it.toString() == text }