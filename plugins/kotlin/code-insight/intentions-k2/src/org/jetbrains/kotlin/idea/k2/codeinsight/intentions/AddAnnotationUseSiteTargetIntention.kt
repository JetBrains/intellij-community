// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.ContextProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.getElementContext
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.getApplicableUseSiteTargets
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddAnnotationUseSiteTargetModCommandAction
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal class AddAnnotationUseSiteTargetIntention :
    AddAnnotationUseSiteTargetModCommandAction(),
    ContextProvider<KtAnnotationEntry, List<AnnotationUseSiteTarget>> {

    override fun KaSession.prepareContext(element: KtAnnotationEntry): List<AnnotationUseSiteTarget> =
        element.getApplicableUseSiteTargets()

    override fun getAnnotationTargets(
        context: ActionContext,
        element: KtAnnotationEntry
    ): List<AnnotationUseSiteTarget> = getElementContext(element).orEmpty()
}
