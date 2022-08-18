// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.quickfix.KotlinIntentionActionFactoryWithDelegate
import org.jetbrains.kotlin.idea.quickfix.QuickFixWithDelegateFactory
import org.jetbrains.kotlin.idea.quickfix.detectPriority
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

object PlatformUnresolvedProvider : KotlinIntentionActionFactoryWithDelegate<KtNameReferenceExpression, String>() {
    override fun getElementOfInterest(diagnostic: Diagnostic): KtNameReferenceExpression? {
        return diagnostic.psiElement.findParentOfType(strict = false)
    }

    override fun extractFixData(element: KtNameReferenceExpression, diagnostic: Diagnostic) = element.getReferencedName()

    override fun createFixes(
        originalElementPointer: SmartPsiElementPointer<KtNameReferenceExpression>,
        diagnostic: Diagnostic,
        quickFixDataFactory: (KtNameReferenceExpression) -> String?
    ): List<QuickFixWithDelegateFactory> {
        val result = ArrayList<QuickFixWithDelegateFactory>()

        originalElementPointer.element?.references?.filterIsInstance<KtSimpleNameReference>()?.firstOrNull()?.let { reference ->
            UnresolvedReferenceQuickFixProvider.registerReferenceFixes(reference, object : QuickFixActionRegistrar {
                override fun register(action: IntentionAction) {
                    result.add(QuickFixWithDelegateFactory(action.detectPriority()) { action })
                }

                override fun register(fixRange: TextRange, action: IntentionAction, key: HighlightDisplayKey?) {
                    register(action)
                }
            })
        }

        return result
    }

}