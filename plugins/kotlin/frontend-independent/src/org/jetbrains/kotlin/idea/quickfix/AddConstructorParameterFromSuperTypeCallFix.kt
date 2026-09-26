// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.appendParameter
import org.jetbrains.kotlin.idea.base.psi.appendValueArgument
import org.jetbrains.kotlin.idea.base.psi.getOrCreatePrimaryConstructor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class AddConstructorParameterFromSuperTypeCallFix(
    element: KtValueArgumentList,
    private val parameterName: String,
    private val parameterTypeSourceCode: String
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtValueArgumentList>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("fix.add.constructor.parameter", parameterName)

    override fun invoke(
        context: ActionContext,
        element: KtValueArgumentList,
        updater: ModPsiUpdater
    ) {
        val constructorParamList = element.containingClass()?.getOrCreatePrimaryConstructor()?.valueParameterList ?: return
        val psiFactory = KtPsiFactory(context.project)
        val constructorParam = constructorParamList.appendParameter(psiFactory.createParameter("$parameterName: $parameterTypeSourceCode"))
        val superTypeCallArg = element.appendValueArgument(psiFactory.createArgument(parameterName))

        ShortenReferencesFacility.getInstance().shorten(constructorParam)
        updater.moveCaretTo(superTypeCallArg.endOffset)
    }
}