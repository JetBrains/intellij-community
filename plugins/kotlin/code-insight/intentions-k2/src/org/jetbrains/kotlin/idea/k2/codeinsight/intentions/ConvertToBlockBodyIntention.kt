// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyContext
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody

internal class ConvertToBlockBodyIntention :
    KotlinApplicableModCommandAction<KtDeclarationWithBody, ConvertToBlockBodyContext>(KtDeclarationWithBody::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.block.body")

    override fun isApplicableByPsi(element: KtDeclarationWithBody): Boolean = ConvertToBlockBodyUtils.isConvertibleByPsi(element)

    override fun KaSession.prepareContext(element: KtDeclarationWithBody): ConvertToBlockBodyContext? =
        ConvertToBlockBodyUtils.createContext(element, reformat = true)

    override fun invoke(
      actionContext: ActionContext,
      element: KtDeclarationWithBody,
      elementContext: ConvertToBlockBodyContext,
      updater: ModPsiUpdater,
    ) {
        ConvertToBlockBodyUtils.convert(element, elementContext)
    }

    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean {
        return element is KtDeclaration || super.stopSearchAt(element, context)
    }
}
