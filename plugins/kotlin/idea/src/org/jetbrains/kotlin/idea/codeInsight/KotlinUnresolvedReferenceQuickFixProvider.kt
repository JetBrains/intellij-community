// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.psi.KtElement

class KotlinUnresolvedReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(reference: PsiReference, registrar: QuickFixActionRegistrar) {
        val element = reference.element as? KtElement ?: return

        val quickFixProvider = Fe10QuickFixProvider.getInstance(element.project)
        val documentWindow = (element.containingFile.virtualFile as? VirtualFileWindow)?.documentWindow

        quickFixProvider.createUnresolvedReferenceQuickFixesForElement(element)
            .forEach { (diagnosticElement, quickFixes) ->
                val textRange = diagnosticElement.textRange
                val textRangeInHost = documentWindow?.injectedToHost(textRange) ?: textRange
                for (quickFix in quickFixes) {
                    registrar.register(textRangeInHost, quickFix, null)
                }
            }
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}