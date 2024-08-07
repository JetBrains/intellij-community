// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.utils.sure

class AddStarProjectionsFix(
    element: KtUserType,
    private val argumentCount: Int,
) : PsiUpdateModCommandAction<KtUserType>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtUserType,
        updater: ModPsiUpdater,
    ) {
        assert(element.typeArguments.isEmpty())
        val typeString = StarProjectionUtils.getTypeNameAndStarProjectionsString(element.text, argumentCount)
        val psiFactory = KtPsiFactory(actionContext.project)
        val replacement = psiFactory.createType(typeString).typeElement.sure { "No type element after parsing $typeString" }
        element.replace(replacement)
    }

    override fun getFamilyName(): String {
        return KotlinBundle.message(
            "fix.add.star.projection.text",
            StarProjectionUtils.getTypeNameAndStarProjectionsString("", argumentCount))
    }
}
