// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.nextSiblingOfSameType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import kotlin.apply
import kotlin.collections.indexOf
import kotlin.sequences.joinToString
import kotlin.sequences.map
import kotlin.text.substring

abstract class ExtractableSubstringInfo(
  val startEntry: KtStringTemplateEntry,
  val endEntry: KtStringTemplateEntry,
  val prefix: String,
  val suffix: String,
) {
    abstract val isString: Boolean

    val template: KtStringTemplateExpression = startEntry.parent as KtStringTemplateExpression

    val content = with(entries.map { it.text }.joinToString(separator = "")) { substring(prefix.length, length - suffix.length) }

    val contentRange: TextRange
        get() = TextRange(startEntry.startOffset + prefix.length, endEntry.endOffset - suffix.length)

    val relativeContentRange: TextRange
        get() = contentRange.shiftRight(-template.startOffset)

    val entries: Sequence<KtStringTemplateEntry>
        get() = generateSequence(startEntry) { if (it != endEntry) it.nextSiblingOfSameType() else null }

    fun createExpression(): KtExpression {
        val quote = template.firstChild.text
        val literalValue = if (isString) "$quote$content$quote" else content
        return KtPsiFactory(template.project).createExpression(literalValue)
            .apply { extractableSubstringInfo = this@ExtractableSubstringInfo }
    }

    fun copy(newTemplate: KtStringTemplateExpression): ExtractableSubstringInfo {
        val oldEntries = template.entries
        val newEntries = newTemplate.entries
        val startIndex = oldEntries.indexOf(startEntry)
        val endIndex = oldEntries.indexOf(endEntry)
        if (startIndex < 0 || startIndex >= newEntries.size || endIndex < 0 || endIndex >= newEntries.size) {
            throw KotlinExceptionWithAttachments("Old template($startIndex..$endIndex): $template, new template: $newTemplate")
                .withPsiAttachment("template", template)
                .withPsiAttachment("newTemplate", newTemplate)
        }
        return copy(newEntries[startIndex], newEntries[endIndex])
    }

    abstract fun copy(newStartEntry: KtStringTemplateEntry, newEndEntry: KtStringTemplateEntry): ExtractableSubstringInfo
}

var KtExpression.extractableSubstringInfo: ExtractableSubstringInfo? by UserDataProperty(Key.create("EXTRACTED_SUBSTRING_INFO"))

val KtExpression.substringContextOrThis: KtExpression
    get() = extractableSubstringInfo?.template ?: this

val PsiElement.substringContextOrThis: PsiElement
    get() = (this as? KtExpression)?.extractableSubstringInfo?.template ?: this