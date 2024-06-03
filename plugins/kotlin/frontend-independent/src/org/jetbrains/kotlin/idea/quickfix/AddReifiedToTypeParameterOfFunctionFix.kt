// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class AddReifiedToTypeParameterOfFunctionFix(
    element: KtTypeParameter,
    function: KtNamedFunction,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtTypeParameter, Unit>(element, Unit) {

    private val elementName = "'${element.name}'"
    private val functionName = "'${function.name}'"

    @IntentionFamilyName
    private val familyName: String = KotlinBundle.message("fix.make.type.parameter.reified", elementName, functionName)

    override fun getFamilyName(): String = familyName

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeParameter,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        element.addModifier(KtTokens.REIFIED_KEYWORD)
        function.addModifier(KtTokens.INLINE_KEYWORD)
    }
}
