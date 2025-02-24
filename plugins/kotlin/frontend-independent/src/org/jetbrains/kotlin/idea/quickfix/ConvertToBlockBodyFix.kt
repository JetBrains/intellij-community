// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyContext
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.psi.KtDeclarationWithBody

class ConvertToBlockBodyFix(
    element: KtDeclarationWithBody,
    private val elementContext: ConvertToBlockBodyContext
): PsiUpdateModCommandAction<KtDeclarationWithBody>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.block.body")

    override fun invoke(context: ActionContext, element: KtDeclarationWithBody, updater: ModPsiUpdater) {
        ConvertToBlockBodyUtils.convert(element, elementContext)
    }
}
