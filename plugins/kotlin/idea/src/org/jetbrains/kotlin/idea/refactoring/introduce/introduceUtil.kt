// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

@K1Deprecation
fun findStringTemplateFragment(file: KtFile, startOffset: Int, endOffset: Int, kind: ElementKind): KtExpression? {
    if (kind != ElementKind.EXPRESSION) return null

    val startEntry = file.findElementAt(startOffset)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null
    val endEntry = file.findElementAt(endOffset - 1)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null

    if (startEntry.parent !is KtStringTemplateExpression || startEntry.parent != endEntry.parent) return null

    val prefixOffset = startOffset - startEntry.startOffset
    if (startEntry !is KtLiteralStringTemplateEntry && prefixOffset > 0) return null

    val suffixOffset = endOffset - endEntry.startOffset
    if (endEntry !is KtLiteralStringTemplateEntry && suffixOffset < endEntry.textLength) return null

    val prefix = startEntry.text.substring(0, prefixOffset)
    val suffix = endEntry.text.substring(suffixOffset)

    return K1ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix).createExpression()
}
