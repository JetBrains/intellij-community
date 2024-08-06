// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference

class ConvertExtensionToFunctionTypeFix(
    element: KtTypeReference,
    private val targetTypeStringShort: String,
    private val targetTypeStringLong: String
) : PsiUpdateModCommandAction<KtTypeReference>(element)  {
    override fun getPresentation(context: ActionContext, element: KtTypeReference): Presentation? {
        return Presentation.of(KotlinBundle.message("convert.supertype.to.0", targetTypeStringShort))
    }

    override fun getFamilyName(): String = KotlinBundle.message("convert.extension.function.type.to.regular.function.type")

    override fun invoke(
        context: ActionContext,
        element: KtTypeReference,
        updater: ModPsiUpdater
    ) {
        val replaced = element.replaced(KtPsiFactory(context.project).createType(targetTypeStringLong))
        ShortenReferencesFacility.getInstance().shorten(replaced)
    }
}