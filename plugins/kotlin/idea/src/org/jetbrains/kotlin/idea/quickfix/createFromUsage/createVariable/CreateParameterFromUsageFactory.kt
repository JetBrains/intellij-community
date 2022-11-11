// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable

import com.intellij.openapi.editor.Editor
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactoryWithDelegate
import org.jetbrains.kotlin.idea.quickfix.QuickFixWithDelegateFactory
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.psi.KtElement

data class CreateParameterData<out E : KtElement>(
    val parameterInfo: KotlinParameterInfo,
    val originalExpression: E,
    val createSilently: Boolean = false,
    val onComplete: ((Editor?) -> Unit)? = null
)

abstract class CreateParameterFromUsageFactory<E : KtElement> :
    KotlinSingleIntentionActionFactoryWithDelegate<E, CreateParameterData<E>>() {

    override fun createFixes(
        originalElementPointer: SmartPsiElementPointer<E>,
        diagnostic: Diagnostic,
        quickFixDataFactory: (E) -> CreateParameterData<E>?
    ): List<QuickFixWithDelegateFactory> = QuickFixWithDelegateFactory(actionPriority) {
        originalElementPointer.element?.let { element ->
            CreateParameterFromUsageFix(element, quickFixDataFactory).takeIf { it.isAvailable(element.project, null, element.containingKtFile ) }
        }
    }.let(::listOfNotNull)

    override fun createFix(originalElement: E, data: CreateParameterData<E>) = throw UnsupportedOperationException("should not be invoked")
}