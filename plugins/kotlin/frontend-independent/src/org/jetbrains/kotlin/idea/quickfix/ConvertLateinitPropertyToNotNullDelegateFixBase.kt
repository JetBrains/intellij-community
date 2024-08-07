// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class ConvertLateinitPropertyToNotNullDelegateFixBase(
    element: KtProperty,
    private val type: String
) : PsiUpdateModCommandAction<KtProperty>(element) {

    override fun getFamilyName() = KotlinBundle.message("convert.to.notnull.delegate")

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        updater: ModPsiUpdater,
    ) {
        val typeReference = element.typeReference ?: return
        val psiFactory = KtPsiFactory(actionContext.project)
        element.removeModifier(KtTokens.LATEINIT_KEYWORD)
        val propertyDelegate = psiFactory.createPropertyDelegate(
            psiFactory.createExpression("kotlin.properties.Delegates.notNull<$type>()")
        )
        element.addAfter(propertyDelegate, typeReference)
        element.typeReference = null
        shortenReferences(element)
    }

    abstract fun shortenReferences(element: KtProperty)
}
