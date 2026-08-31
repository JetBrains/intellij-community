// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.canConvertToInitializer
import org.jetbrains.kotlin.idea.codeinsight.utils.convertSingleExpressionGetterToInitializer
import org.jetbrains.kotlin.psi.KtPropertyAccessor

internal class ConvertPropertyGetterToInitializerIntention :
    KotlinApplicableModCommandAction.Simple<KtPropertyAccessor>(KtPropertyAccessor::class) {

    override fun stopSearchAt(
        element: PsiElement,
        context: ActionContext,
    ): Boolean = false

    override fun getFamilyName() =
        KotlinBundle.message("convert.property.getter.to.initializer")

    override fun isApplicableByPsi(element: KtPropertyAccessor): Boolean {
        return element.canConvertToInitializer()
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtPropertyAccessor,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        element.convertSingleExpressionGetterToInitializer(updater)
    }
}
