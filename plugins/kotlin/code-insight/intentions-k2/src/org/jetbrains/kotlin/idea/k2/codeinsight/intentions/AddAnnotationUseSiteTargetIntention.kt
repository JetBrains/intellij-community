// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import com.intellij.modcommand.ModCommand.chooseAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.addUseSiteTarget
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.getApplicableUseSiteTargets
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBlockExpression

internal class AddAnnotationUseSiteTargetIntention : PsiBasedModCommandAction<KtAnnotationEntry>(null, KtAnnotationEntry::class.java) {
    override fun perform(
        context: ActionContext,
        element: KtAnnotationEntry
    ): ModCommand {
        val targets = getAnnotationTargets(element)
        when (targets.size) {
            0 -> return ModCommand.nop()
            else -> return chooseAction(
                KotlinBundle.message("title.choose.use.site.target"),
                targets.map { AddSingleTargetAction(element, it) }
            )
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("add.use.site.target")

    override fun stopSearchAt(
        element: PsiElement,
        context: ActionContext,
    ): Boolean = element is KtBlockExpression

    override fun isElementApplicable(element: KtAnnotationEntry, context: ActionContext): Boolean {
        val targets = getAnnotationTargets(element)
        return targets.isNotEmpty()
    }

    private fun getAnnotationTargets(element: KtAnnotationEntry): List<AnnotationUseSiteTarget> {
        return if (element.isPhysical) analyze(element) {
            element.getApplicableUseSiteTargets()
        } else analyzeCopy(element, KaDanglingFileResolutionMode.PREFER_SELF) {
            element.getApplicableUseSiteTargets()
        }
    }
}

private class AddSingleTargetAction(
    entry: KtAnnotationEntry,
    private val target: AnnotationUseSiteTarget,
) : PsiUpdateModCommandAction<KtAnnotationEntry>(entry) {
    override fun invoke(
        context: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater
    ) {
        element.addUseSiteTarget(listOf(target), null)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("text.add.use.site.target.0", target.renderName)
}
