// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyContext
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody

internal class ConvertToBlockBodyIntention :
    AbstractKotlinApplicableIntentionWithContext<KtDeclarationWithBody, ConvertToBlockBodyContext>(KtDeclarationWithBody::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.block.body")

    override fun getActionName(element: KtDeclarationWithBody, context: ConvertToBlockBodyContext): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDeclarationWithBody> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtDeclarationWithBody) = ConvertToBlockBodyUtils.isConvertibleByPsi(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtDeclarationWithBody): ConvertToBlockBodyContext? {
        return ConvertToBlockBodyUtils.createContext(element, ::shortenReferences, reformat = true)
    }

    override fun apply(element: KtDeclarationWithBody, context: ConvertToBlockBodyContext, project: Project, editor: Editor?) {
        ConvertToBlockBodyUtils.convert(element, context)
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) =
        element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)
}
