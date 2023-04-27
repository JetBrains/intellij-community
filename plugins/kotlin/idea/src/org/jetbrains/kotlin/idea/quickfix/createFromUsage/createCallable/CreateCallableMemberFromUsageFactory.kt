// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.UnresolvedReferenceQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.IntentionActionPriority
import org.jetbrains.kotlin.idea.quickfix.KotlinIntentionActionFactoryWithDelegate
import org.jetbrains.kotlin.idea.quickfix.QuickFixWithDelegateFactory
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.CallableInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
import org.jetbrains.kotlin.psi.KtElement

abstract class CreateCallableMemberFromUsageFactory<E : KtElement>(
    private val extensionsSupported: Boolean = true
) : KotlinIntentionActionFactoryWithDelegate<E, List<CallableInfo>>(), UnresolvedReferenceQuickFixFactory {

    private fun newCallableQuickFix(
        originalElementPointer: SmartPsiElementPointer<E>,
        priority: IntentionActionPriority,
        quickFixFactory: (E) -> IntentionAction?
    ): QuickFixWithDelegateFactory = QuickFixWithDelegateFactory(priority) {
        originalElementPointer.element?.let { element -> quickFixFactory(element)}
    }

    protected open fun createCallableInfo(element: E, diagnostic: Diagnostic): CallableInfo? = null

    override fun extractFixData(element: E, diagnostic: Diagnostic): List<CallableInfo> =
        listOfNotNull(createCallableInfo(element, diagnostic))

    override fun createFixes(
        originalElementPointer: SmartPsiElementPointer<E>,
        diagnostic: Diagnostic,
        quickFixDataFactory: (E) -> List<CallableInfo>?
    ): List<QuickFixWithDelegateFactory> {
        val fixes = ArrayList<QuickFixWithDelegateFactory>(3)

        newCallableQuickFix(originalElementPointer, IntentionActionPriority.NORMAL) { element ->
            CreateCallableFromUsageFix(element, quickFixDataFactory)
        }.let { fixes.add(it) }

        newCallableQuickFix(originalElementPointer, IntentionActionPriority.NORMAL) { element->
            CreateParameterFromUsageFix.createFixForPrimaryConstructorPropertyParameter(element, quickFixDataFactory)
        }.let { fixes.add(it) }

        if (extensionsSupported) {
            newCallableQuickFix(originalElementPointer, IntentionActionPriority.LOW) { element ->
                CreateExtensionCallableFromUsageFix(element) { e ->
                    quickFixDataFactory(e)?.takeUnless { callableInfos ->
                        callableInfos.any { it.isAbstract }
                    }
                }
            }.let { fixes.add(it) }
        }

        return fixes
    }

}