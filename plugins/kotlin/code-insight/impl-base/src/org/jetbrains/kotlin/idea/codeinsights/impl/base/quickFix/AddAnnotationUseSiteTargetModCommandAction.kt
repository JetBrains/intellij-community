// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.addUseSiteTarget
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBlockExpression

/**
 * A mod command action for adding an explicit use-site target to a [KtAnnotationEntry].
 * If there are one or more applicable targets, [com.intellij.modcommand.ModChooseAction] is used.
 * Otherwise, the action is no-op.
 */
abstract class AddAnnotationUseSiteTargetModCommandAction() :
    PsiBasedModCommandAction<KtAnnotationEntry>(null, KtAnnotationEntry::class.java) {

    abstract fun getAnnotationTargets(
        context: ActionContext,
        element: KtAnnotationEntry,
    ): List<AnnotationUseSiteTarget>

    override fun perform(
        context: ActionContext,
        element: KtAnnotationEntry,
    ): ModCommand {
        val targets = getAnnotationTargets(context, element)
        return when (targets.size) {
            0 -> ModCommand.nop()
            else -> ModCommand.chooseAction(
                KotlinBundle.message("title.choose.use.site.target"),
                targets.map { AddSingleTargetModCommandAction(element, it) },
            )
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("add.use.site.target")

    override fun stopSearchAt(
        element: PsiElement,
        context: ActionContext,
    ): Boolean = element is KtBlockExpression

    override fun isElementApplicable(
        element: KtAnnotationEntry,
        context: ActionContext,
    ): Boolean = getAnnotationTargets(context, element).isNotEmpty()
}

private class AddSingleTargetModCommandAction(
    element: KtAnnotationEntry,
    private val target: AnnotationUseSiteTarget,
) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

    override fun invoke(
        context: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater,
    ) {
        element.addUseSiteTarget(target)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("text.add.use.site.target.0", target.renderName)
}
