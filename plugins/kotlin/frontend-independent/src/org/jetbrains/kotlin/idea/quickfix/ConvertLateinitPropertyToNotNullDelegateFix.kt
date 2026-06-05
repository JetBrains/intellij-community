// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

class ConvertLateinitPropertyToNotNullDelegateFix(
    element: KtProperty,
    private val type: String
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtProperty>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.notnull.delegate")

    override fun invoke(
        context: ActionContext,
        element: KtProperty,
        updater: ModPsiUpdater,
    ) {
        val typeReference = element.typeReference ?: return
        val psiFactory = KtPsiFactory(context.project)
        element.removeModifier(KtTokens.LATEINIT_KEYWORD)
        val propertyDelegate = psiFactory.createPropertyDelegate(
            psiFactory.createExpression("kotlin.properties.Delegates.notNull<$type>()")
        )
        element.addAfter(propertyDelegate, typeReference)
        element.typeReference = null
        ShortenReferencesFacility.getInstance().shorten(element)
    }
}
