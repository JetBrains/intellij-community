// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractInlineVariableCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty

internal class KotlinInlinePropertyCompletionCommandProvider : AbstractInlineVariableCompletionCommandProvider() {
    override val presentableName: @Nls String
        get() = KotlinBundle.message("title.inline.property")

    override fun findElementToInline(offset: Int, psiFile: PsiFile, editor: Editor?, forExecution: Boolean): PsiElement? =
        findInlinableProperty(offset, psiFile, permitEdtResolve = forExecution)
}

private fun findInlinableProperty(offset: Int, psiFile: PsiFile, permitEdtResolve: Boolean): KtProperty? {
    if (offset == 0) return null
    var element = getCommandContext(offset, psiFile) ?: return null
    if (element is PsiWhiteSpace) {
        element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
    }
    if (!element.isWritable) return null
    val currentOffset = element.textRange?.endOffset ?: offset

    // Declaration: caret right after the property name.
    val declaration = element.parentOfType<KtProperty>()
    if (declaration != null && declaration.nameIdentifier?.textRange?.endOffset == currentOffset) {
        return declaration.takeIf { isPropertyInlinable(it) }
    }

    // Usage: a property reference whose name ends at the caret.
    val reference = element.parentOfType<KtNameReferenceExpression>()
    if (reference == null || reference.textRange?.endOffset != currentOffset) return null
    val resolved = resolveToProperty(reference, permitEdtResolve) ?: return null
    return resolved.takeIf { isPropertyInlinable(it) }
}

private fun resolveToProperty(reference: KtNameReferenceExpression, permitEdtResolve: Boolean): KtProperty? {
    val onEdt = EDT.isCurrentThreadEdt()
    if (onEdt && !permitEdtResolve) return null
    fun doResolve(): PsiElement? = analyze(reference) { reference.mainReference.resolve() }
    val resolved = if (onEdt) {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), KotlinBundle.message("title.inline.property"))
        { readAction { doResolve() } }
    } else {
        doResolve()
    }
    return resolved as? KtProperty
}

private fun isPropertyInlinable(property: KtProperty): Boolean {
    if (property.name == null) return false
    if (property.containingKtFile.isCompiled) return false
    if (!property.hasBody()) return false
    val hasAccessorBody = property.getter?.hasBody() == true || property.setter?.hasBody() == true
    if (hasAccessorBody && property.initializer != null) return false
    return true
}
