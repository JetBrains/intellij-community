// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.applyIf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

/**
 * An action that displays a list of conflicting declarations and allows navigation to them.
 *
 * The original declaration is shown last in the list and marked as 'current' to avoid confusion.
 *
 * Inspired by [com.intellij.codeInsight.daemon.impl.quickfix.ShowDuplicateElementsFix].
 */
internal class ShowConflictingDeclarationsAction(
    originalDeclaration: NavigatablePsiElement,
    conflictingDeclarations: List<NavigatablePsiElement>
) : PsiBasedModCommandAction<NavigatablePsiElement>(originalDeclaration) {

    private val conflictingDeclarationsPointers: List<SmartPsiElementPointer<NavigatablePsiElement>> =
        conflictingDeclarations.map { SmartPointerManager.createPointer(it) }

    private val conflictingDeclarations: List<NavigatablePsiElement>
        get() = conflictingDeclarationsPointers.mapNotNull { it.element }

    override fun getFamilyName(): String = KotlinBundle.message("show.conflicting.declarations.family")

    override fun getPresentation(context: ActionContext, element: NavigatablePsiElement): Presentation? {
        val elementName = (element as? PsiNameIdentifierOwner)?.name ?: return null
        return Presentation.of(KotlinBundle.message("show.conflicting.declarations.text", elementName))
    }

    override fun perform(context: ActionContext, element: NavigatablePsiElement): ModCommand {
        val title = KotlinBundle.message("show.conflicting.declarations.popup.title")
        val navigateActions = buildList {
            for (conflictingDeclaration in conflictingDeclarations) {
                add(NavigateToConflictingDeclarationAction(conflictingDeclaration))
            }

            add(NavigateToConflictingDeclarationAction(element, isOriginalDeclaration = true))
        }
        return ModCommand.chooseAction(title, navigateActions)
    }

    override fun generatePreview(context: ActionContext, element: NavigatablePsiElement): IntentionPreviewInfo {
        val builder = HtmlBuilder().apply {
            for (conflictingDeclaration in conflictingDeclarations) {
                append(IntentionPreviewInfo.navigatePreviewHtmlChunk(conflictingDeclaration.containingFile, conflictingDeclaration.textOffset))
                br()
            }

            append(IntentionPreviewInfo.navigatePreviewHtmlChunk(element.containingFile, element.textOffset))
            append(HtmlChunk.text(KotlinBundle.message("show.conflicting.declarations.current.marker.text")).bold())
        }

        return IntentionPreviewInfo.Html(builder.toFragment())
    }
}

private class NavigateToConflictingDeclarationAction(
    declaration: NavigatablePsiElement,
    val isOriginalDeclaration: Boolean = false
) : PsiBasedModCommandAction<NavigatablePsiElement>(declaration) {

    override fun getFamilyName(): String = KotlinBundle.message("show.conflicting.declarations.navigate.family")

    override fun getPresentation(context: ActionContext, element: NavigatablePsiElement): Presentation {
        val fileName = element.containingFile.name
        val title = KotlinBundle.message(
            "show.conflicting.declarations.navigate.text",
            fileName,
            if (isOriginalDeclaration) 1 else 0
        )

        return Presentation.of(title)
            .withIcon(element.containingFile.getIcon(0))
            .applyIf(element.containingFile == context.file) {
                // we want to highlight the declaration ONLY if it comes from the current file,
                // otherwise the range would be incorrect
                withHighlighting(element.textRange)
            }
    }

    override fun generatePreview(context: ActionContext, element: NavigatablePsiElement): IntentionPreviewInfo =
        IntentionPreviewInfo.snippet(element)

    override fun perform(context: ActionContext, element: NavigatablePsiElement): ModCommand {
        val target = (element as? PsiNameIdentifierOwner)?.nameIdentifier ?: element
        return ModCommand.select(target)
    }
}
