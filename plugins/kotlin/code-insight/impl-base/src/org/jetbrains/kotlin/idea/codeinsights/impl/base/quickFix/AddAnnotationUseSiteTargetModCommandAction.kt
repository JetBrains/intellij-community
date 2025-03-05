// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommand.chooseAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.modcommand.PsiUpdateModCommandAction
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

/**
 * A mod command action for adding an explicit use-site target to a [KtAnnotationEntry].
 * If there are one or more applicable targets, [com.intellij.modcommand.ModChooseAction] is used.
 * Otherwise, the action is no-op.
 *
 * @param targets precomputed use-site targets. If null is passed, the applicable targets will
 * be computed based on the [KtAnnotationEntry] passed to the mod command action. Note that it's the
 * responsibility of the caller to make sure the passed non-null targets match the passed element.
 */
open class AddAnnotationUseSiteTargetModCommandAction(
    val targets: List<AnnotationUseSiteTarget>?,
) : PsiBasedModCommandAction<KtAnnotationEntry>(null, KtAnnotationEntry::class.java) {
    override fun perform(
        context: ActionContext,
        element: KtAnnotationEntry,
    ): ModCommand {
        val targets = targets ?: getAnnotationTargets(element)
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
        val targets = targets ?: getAnnotationTargets(element)
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

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("text.add.use.site.target.0", target.renderName)
}
