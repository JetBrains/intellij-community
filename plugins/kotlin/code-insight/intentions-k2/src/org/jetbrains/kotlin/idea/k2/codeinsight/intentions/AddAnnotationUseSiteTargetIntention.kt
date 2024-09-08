// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.addUseSiteTarget
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.getApplicableUseSiteTargets
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal class AddAnnotationUseSiteTargetIntention :
    KotlinApplicableModCommandAction<KtAnnotationEntry, List<AnnotationUseSiteTarget>>(KtAnnotationEntry::class) {

    override fun getFamilyName(): String = KotlinBundle.message("add.use.site.target")

    context(KaSession)
    override fun prepareContext(element: KtAnnotationEntry): List<AnnotationUseSiteTarget>? {
        val useSiteTargets = element.getApplicableUseSiteTargets()
        return useSiteTargets.takeIf { it.isNotEmpty() }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtAnnotationEntry,
        elementContext: List<AnnotationUseSiteTarget>,
        updater: ModPsiUpdater,
    ) {
        element.addUseSiteTarget(elementContext, null)
    }
}
