// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupModCommandFix
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class MissingConstructorBracketsFix(
    element: KtPrimaryConstructor
) : KotlinPsiUpdateModCommandAction.ElementBased<KtPrimaryConstructor, Unit>(element, Unit), CleanupModCommandFix {
    override fun getFamilyName(): String = KotlinBundle.message("add.empty.brackets.after.primary.constructor")

    override fun invoke(
        actionContext: ActionContext,
        element: KtPrimaryConstructor,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val constructorKeyword = element.getConstructorKeyword() ?: return
        if (element.valueParameterList != null) return
        val endOffset = constructorKeyword.endOffset
        val constructorKeywordWithBrackets = KtPsiFactory(constructorKeyword.project).createPrimaryConstructor("constructor()")
        constructorKeyword.replace(constructorKeywordWithBrackets)
        updater.moveCaretTo(endOffset + 1)
    }
}
