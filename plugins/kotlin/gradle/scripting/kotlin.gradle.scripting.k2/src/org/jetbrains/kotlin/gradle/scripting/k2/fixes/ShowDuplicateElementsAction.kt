// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.fixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

/**
 * An action that shows a list of duplicates and allows navigation to them.
 * Inspired by [com.intellij.codeInsight.daemon.impl.quickfix.ShowDuplicateElementsFix]
 */
internal class ShowDuplicateElementsAction(
    private val duplicateName: String,
    duplicates: List<NavigatablePsiElement>,
    private val showDuplicatesFamilyName: @IntentionFamilyName String,
    private val chooseDuplicatePopupTitle: @NlsContexts.PopupTitle String,
    private val navigateToDuplicateFamilyName: @IntentionFamilyName String
) : PsiBasedModCommandAction<NavigatablePsiElement>(NavigatablePsiElement::class.java) {
    private val myNavigatablePsiElements = duplicates.map { it.createSmartPointer() }

    override fun getFamilyName(): @IntentionFamilyName String = showDuplicatesFamilyName

    override fun getPresentation(context: ActionContext, section: NavigatablePsiElement): Presentation {
        val showDuplicatesName = GradleInspectionBundle.message("intention.name.show.duplicates", duplicateName)
        return Presentation.of(showDuplicatesName)
    }

    override fun perform(context: ActionContext, element: NavigatablePsiElement): ModCommand {
        val navigateActions = this.duplicatePsiElements.map { NavigateToAction(it, navigateToDuplicateFamilyName) }
        return ModCommand.chooseAction(chooseDuplicatePopupTitle, navigateActions)
    }

    override fun generatePreview(context: ActionContext?, element: NavigatablePsiElement?): IntentionPreviewInfo {
        val chunks = duplicatePsiElements.map {
            HtmlBuilder()
                .append(HtmlChunk.htmlEntity("&rarr;"))
                .append(" ")
                .append(getLineMessage(it.containingFile, it.textRange))
                .toFragment()
        }
        val content = HtmlBuilder().appendWithSeparators(HtmlChunk.br(), chunks).toFragment()
        return IntentionPreviewInfo.Html(content)
    }

    private val duplicatePsiElements: List<NavigatablePsiElement>
        get() = myNavigatablePsiElements.mapNotNull { it.element }

    private class NavigateToAction(
        navigatablePsiElement: NavigatablePsiElement,
        private val navigateToDuplicateFamilyName: @IntentionFamilyName String
    ) : PsiBasedModCommandAction<NavigatablePsiElement>(navigatablePsiElement) {

        override fun getFamilyName(): @IntentionFamilyName String = navigateToDuplicateFamilyName

        override fun getPresentation(context: ActionContext, element: NavigatablePsiElement): Presentation {
            val message = getLineMessage(element.containingFile, element.textRange)
            return Presentation.of(message).withHighlighting(element.getTextRange())
        }

        override fun generatePreview(context: ActionContext, element: NavigatablePsiElement): IntentionPreviewInfo {
            return IntentionPreviewInfo.snippet(element)
        }

        override fun perform(context: ActionContext, element: NavigatablePsiElement): ModCommand {
            return ModCommand.select(element)
        }
    }

    companion object {
        private fun getLineMessage(file: PsiFile, textRange: TextRange): @IntentionName String {
            val firstLineNumber = file.fileDocument.getLineNumber(textRange.startOffset) + 1
            val lastLineNumber = file.fileDocument.getLineNumber(textRange.endOffset) + 1
            return if (firstLineNumber == lastLineNumber) {
                GradleInspectionBundle.message("intention.name.duplicate.dependency.line.number", firstLineNumber)
            } else {
                GradleInspectionBundle.message(
                    "intention.name.duplicate.dependency.line.number.range",
                    firstLineNumber,
                    lastLineNumber
                )
            }
        }
    }
}