// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyContext
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody

internal class ConvertToBlockBodyIntention :
    AbstractKotlinModCommandWithContext<KtDeclarationWithBody, ConvertToBlockBodyContext>(KtDeclarationWithBody::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.block.body")

    override fun getActionName(element: KtDeclarationWithBody, context: ConvertToBlockBodyContext): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDeclarationWithBody> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtDeclarationWithBody): Boolean = ConvertToBlockBodyUtils.isConvertibleByPsi(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtDeclarationWithBody): ConvertToBlockBodyContext? =
        ConvertToBlockBodyUtils.createContext(element, ShortenReferencesFacility.getInstance(), reformat = true)

    override fun apply(element: KtDeclarationWithBody, context: AnalysisActionContext<ConvertToBlockBodyContext>, updater: ModPsiUpdater) {
        ConvertToBlockBodyUtils.convert(element, context.analyzeContext)
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean =
        element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)
}
