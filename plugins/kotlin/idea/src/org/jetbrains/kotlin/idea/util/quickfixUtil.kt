// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.quickfix.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.resolve.getDataFlowValueFactory
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoAfter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.ifEmpty

inline fun <reified T : PsiElement> Diagnostic.createIntentionForFirstParentOfType(
    factory: (T) -> KotlinQuickFixAction<T>?
) = psiElement.getNonStrictParentOfType<T>()?.let(factory)


fun createIntentionFactory(
    factory: (Diagnostic) -> IntentionAction?
) = object : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic) = factory(diagnostic)
}

fun KtPrimaryConstructor.addConstructorKeyword(): PsiElement? {
    val modifierList = this.modifierList ?:  return null
    val psiFactory = KtPsiFactory(this)
    val constructor = if (valueParameterList == null) {
        psiFactory.createPrimaryConstructor("constructor()")
    } else {
        psiFactory.createConstructorKeyword()
    }
    return addAfter(constructor, modifierList)
}

fun getDataFlowAwareTypes(
    expression: KtExpression,
    bindingContext: BindingContext = expression.analyze(),
    originalType: KotlinType? = bindingContext.getType(expression)
): Collection<KotlinType> {
    if (originalType == null) return emptyList()
    val dataFlowInfo = bindingContext.getDataFlowInfoAfter(expression)

    val dataFlowValueFactory = expression.getResolutionFacade().getDataFlowValueFactory()
    val expressionType = bindingContext.getType(expression) ?: return listOf(originalType)
    val dataFlowValue = dataFlowValueFactory.createDataFlowValue(
        expression, expressionType, bindingContext, expression.getResolutionFacade().moduleDescriptor
    )
    return dataFlowInfo.getCollectedTypes(dataFlowValue, expression.languageVersionSettings).ifEmpty { listOf(originalType) }
}
