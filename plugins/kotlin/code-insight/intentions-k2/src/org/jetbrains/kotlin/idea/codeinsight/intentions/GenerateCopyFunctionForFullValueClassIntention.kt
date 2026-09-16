// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.addMemberDeclaration
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.copyFunctionText
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.generationParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.isFullValueClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class GenerateCopyFunctionForFullValueClassIntention :
    KotlinApplicableModCommandAction.Simple<KtClass>(KtClass::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("intention.generate.copy.function.family.name")

    override fun isApplicableByPsi(element: KtClass): Boolean {
        if (element.nameIdentifier == null) return false
        if (!element.isFullValueClass()) return false
        if (element.declaredCopyFunction() != null) return false
        return element.generationParameters() != null
    }

    override fun getApplicableRanges(element: KtClass): List<TextRange> {
        val nameIdentifier = element.nameIdentifier ?: return emptyList()
        val valueKeyword = element.modifierList?.getModifier(KtTokens.VALUE_KEYWORD) ?: return emptyList()
        val range = TextRange(valueKeyword.startOffset, nameIdentifier.endOffset).shiftLeft(element.startOffset)
        return listOf(range)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtClass,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val parameters = element.generationParameters() ?: return
        val psiFactory = KtPsiFactory(actionContext.project, markGenerated = true)
        val copyFunction = element.copyFunctionText(parameters) ?: return
        element.addMemberDeclaration(psiFactory.createFunction(copyFunction))
    }

    private fun KtClass.declaredCopyFunction(): KtNamedFunction? =
        body?.functions?.firstOrNull { it.name == "copy" }

}