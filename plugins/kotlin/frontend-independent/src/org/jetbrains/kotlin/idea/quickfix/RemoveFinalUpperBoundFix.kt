// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes

class RemoveFinalUpperBoundFix(element: KtTypeReference) : PsiUpdateModCommandAction<KtTypeReference>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeReference,
        updater: ModPsiUpdater,
    ) {
        val parent = element.getParentOfTypes(strict = true, KtTypeParameter::class.java, KtTypeConstraint::class.java)
        when (parent) {
            is KtTypeParameter -> parent.extendsBound = null
            is KtTypeConstraint -> {
                val constraintList = parent.parent as KtTypeConstraintList
                if (constraintList.constraints.size == 1) {
                    constraintList.siblings(forward = false).firstOrNull { it.elementType == KtTokens.WHERE_KEYWORD }?.delete()
                    constraintList.delete()
                } else {
                    EditCommaSeparatedListHelper.removeItem(parent)
                }
            }
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("remove.final.upper.bound")
}
