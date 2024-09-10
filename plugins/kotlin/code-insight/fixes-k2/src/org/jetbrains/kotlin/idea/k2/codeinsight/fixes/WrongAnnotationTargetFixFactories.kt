// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.addUseSiteTarget
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.getApplicableUseSiteTargets
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal object WrongAnnotationTargetFixFactories {

    val addAnnotationUseSiteTargetFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.WrongAnnotationTarget ->
        val applicableUseSiteTargets = diagnostic.psi.getApplicableUseSiteTargets().takeIf {
            it.isNotEmpty()
        } ?: return@ModCommandBased emptyList()

        listOf(
            AddAnnotationUseSiteTargetFix(diagnostic.psi, applicableUseSiteTargets)
        )
    }

    private class AddAnnotationUseSiteTargetFix(
        element: KtAnnotationEntry,
        private val useSiteTargets: List<AnnotationUseSiteTarget>,
    ) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

        override fun getFamilyName(): String {
            return if (useSiteTargets.size == 1) {
                KotlinBundle.message("text.add.use.site.target.0", useSiteTargets.first().renderName)
            } else {
                KotlinBundle.message("add.use.site.target")
            }
        }

        override fun invoke(
            context: ActionContext,
            element: KtAnnotationEntry,
            updater: ModPsiUpdater,
        ) {
            element.addUseSiteTarget(useSiteTargets, null)
        }
    }
}
