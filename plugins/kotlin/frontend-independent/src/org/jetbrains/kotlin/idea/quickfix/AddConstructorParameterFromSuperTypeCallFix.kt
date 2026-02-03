// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.createPrimaryConstructorIfAbsent
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class AddConstructorParameterFromSuperTypeCallFix(
    element: KtValueArgumentList,
    private val parameterName: String,
    private val parameterTypeSourceCode: String
) : PsiUpdateModCommandAction<KtValueArgumentList>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("fix.add.constructor.parameter", parameterName)

    override fun invoke(
        context: ActionContext,
        element: KtValueArgumentList,
        updater: ModPsiUpdater
    ) {
        val constructorParamList = element.containingClass()?.createPrimaryConstructorIfAbsent()?.valueParameterList ?: return
        val psiFactory = KtPsiFactory(context.project)
        val constructorParam = constructorParamList.addParameter(psiFactory.createParameter("$parameterName: $parameterTypeSourceCode"))
        val superTypeCallArg = element.addArgument(psiFactory.createArgument(parameterName))

        ShortenReferencesFacility.getInstance().shorten(constructorParam)
        updater.moveCaretTo(superTypeCallArg.endOffset)
    }
}