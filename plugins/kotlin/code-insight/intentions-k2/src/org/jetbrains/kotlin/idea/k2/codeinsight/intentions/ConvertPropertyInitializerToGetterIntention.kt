// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.canConvertPropertyInitializerToGetterByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.convertPropertyInitializerToGetter
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.hasReferenceToPrimaryConstructorParameter
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty

internal class ConvertPropertyInitializerToGetterIntention :
    KotlinApplicableModCommandAction<KtProperty, CallableReturnTypeUpdaterUtils.TypeInfo>(KtProperty::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.property.initializer.to.getter")
    override fun isApplicableByPsi(element: KtProperty): Boolean = canConvertPropertyInitializerToGetterByPsi(element)

    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean {
        // do not work inside lambda's in initializer - they can be too big
        return element is KtDeclaration || super.stopSearchAt(element, context)
    }

    override fun KaSession.prepareContext(element: KtProperty): CallableReturnTypeUpdaterUtils.TypeInfo? {
        val initializer = element.initializer ?: return null
        if (initializer.hasReferenceToPrimaryConstructorParameter()) return null

        return CallableReturnTypeUpdaterUtils.getTypeInfo(element)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: CallableReturnTypeUpdaterUtils.TypeInfo,
        updater: ModPsiUpdater,
    ) {
        convertPropertyInitializerToGetter(actionContext.project, element, elementContext, updater)
    }
}
