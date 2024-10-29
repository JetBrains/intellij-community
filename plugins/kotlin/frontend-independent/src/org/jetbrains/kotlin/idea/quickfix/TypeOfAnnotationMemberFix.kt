// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference

class TypeOfAnnotationMemberFix(
    element: KtTypeReference,
    private val fixedType: String,
) : PsiUpdateModCommandAction<KtTypeReference>(element) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeReference,
        updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(actionContext.project).createType(fixedType))
    }

    override fun getFamilyName(): String = KotlinBundle.message("replace.array.of.boxed.with.array.of.primitive")
}
