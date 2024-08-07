// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference

abstract class ChangeAccessorTypeFixBase(
    element: KtPropertyAccessor,
    private val typePresentation: String,
    private val typeSourceCode: String,
) : PsiUpdateModCommandAction<KtPropertyAccessor>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.change.accessor.family")

    override fun getPresentation(
        context: ActionContext,
        element: KtPropertyAccessor,
    ): Presentation {
        val actionName = if (element.isGetter) {
            KotlinBundle.message("fix.change.accessor.getter", typePresentation)
        } else {
            KotlinBundle.message("fix.change.accessor.setter.parameter", typePresentation)
        }
        return Presentation.of(actionName)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtPropertyAccessor,
        updater: ModPsiUpdater,
    ) {
        val newTypeReference = KtPsiFactory(actionContext.project).createType(typeSourceCode)
        val typeReference = if (element.isGetter) element.returnTypeReference else element.parameter!!.typeReference

        val insertedTypeRef = typeReference!!.replaced(newTypeReference)
        shortenReferences(insertedTypeRef)
    }

    abstract fun shortenReferences(element: KtTypeReference)
}
