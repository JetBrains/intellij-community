// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeParameter

class AddGenericUpperBoundFix(
    element: KtTypeParameter,
    private val fqName: String,
    private val shortName: String,
) : PsiUpdateModCommandAction<KtTypeParameter>(element) {

    override fun getPresentation(
        context: ActionContext,
        element: KtTypeParameter,
    ): Presentation {
        return Presentation.of(KotlinBundle.message("fix.add.generic.upperbound.text", shortName, element.name.toString()))
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.generic.upperbound.family")

    override fun invoke(
        context: ActionContext,
        element: KtTypeParameter,
        updater: ModPsiUpdater,
    ) {
        assert(element.extendsBound == null) { "Don't know what to do with existing bounds" }

        val typeReference = KtPsiFactory(context.project).createType(fqName)
        val insertedTypeReference = element.setExtendsBound(typeReference)!!

        ShortenReferencesFacility.getInstance().shorten(insertedTypeReference)
    }
}
