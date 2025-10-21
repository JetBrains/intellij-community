// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import com.intellij.modcommand.ModCommand.chooseAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.addUseSiteTarget
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddAnnotationUseSiteTargetUtils.getApplicableUseSiteTargets
import org.jetbrains.kotlin.idea.quickfix.K2EnableUnsupportedFeatureFix
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal object WrongAnnotationTargetFixFactories {
    private const val ANNOTATION_DEFAULT_TARGET_FLAG = "-Xannotation-default-target=param-property"

    val addAnnotationUseSiteTargetFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.WrongAnnotationTarget ->
        val applicableUseSiteTargets = diagnostic.psi.getApplicableUseSiteTargets().takeIf {
            it.isNotEmpty()
        } ?: return@ModCommandBased emptyList()

        buildList {
            if (applicableUseSiteTargets.size == 1) {
                add(AddAnnotationUseSiteTargetFix(diagnostic.psi, applicableUseSiteTargets.single()))
            } else {
                add(ChooseAnnotationUseSiteTargetFix(diagnostic.psi, applicableUseSiteTargets))
            }
        }
    }

    val addAnnotationUseSiteTargetForConstructorParameterFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AnnotationWillBeAppliedAlsoToPropertyOrField ->
            val annotationEntry = diagnostic.psi
            val useSiteTargets = annotationEntry.getApplicableUseSiteTargets()
            val (constructorParameterTarget, restTargets) = useSiteTargets.partition { target ->
                target == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER
            }
            if (constructorParameterTarget.size != 1) return@ModCommandBased emptyList()
            diagnostic.useSiteDescription
            buildList {
                add(AddAnnotationUseSiteTargetFix(annotationEntry, constructorParameterTarget.single()))
                addAll(restTargets.map { target -> ChangeConstructorParameterUseSiteTargetFix(annotationEntry, target) })
            }
        }

    val enableFutureAnnotationTargetModeFactory =
        KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.AnnotationWillBeAppliedAlsoToPropertyOrField> { diagnostic ->
            val annotationEntry = diagnostic.psi
            val module = annotationEntry.containingKtFile.module ?: return@IntentionBased emptyList()
            val feature = LanguageFeature.PropertyParamAnnotationDefaultTargetMode
            listOf(
                K2EnableUnsupportedFeatureFix(
                    annotationEntry, module, feature,
                    alternativeActionText = KotlinBundle.message("text.enable.annotation.target.feature", ANNOTATION_DEFAULT_TARGET_FLAG)
                ),
            )
        }

    private class ChooseAnnotationUseSiteTargetFix(
        element: KtAnnotationEntry,
        private val useSiteTargets: List<AnnotationUseSiteTarget>,
    ) : PsiBasedModCommandAction<KtAnnotationEntry>(element) {
        override fun perform(
            context: ActionContext,
            element: KtAnnotationEntry
        ): ModCommand {
            return chooseAction(
                KotlinBundle.message("title.choose.use.site.target"),
                useSiteTargets.map { target -> AddAnnotationUseSiteTargetFix(element, target) }
            )
        }

        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("add.use.site.target")
        }
    }

    private class AddAnnotationUseSiteTargetFix(
        element: KtAnnotationEntry,
        private val useSiteTarget: AnnotationUseSiteTarget,
    ) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

        override fun getFamilyName(): String {
            return KotlinBundle.message("text.add.use.site.target.0", useSiteTarget.renderName)
        }

        override fun invoke(
            context: ActionContext,
            element: KtAnnotationEntry,
            updater: ModPsiUpdater,
        ) {
            element.addUseSiteTarget(useSiteTarget)
        }
    }

    private class ChangeConstructorParameterUseSiteTargetFix(
        annotationEntry: KtAnnotationEntry,
        private val useSiteTarget: AnnotationUseSiteTarget,
    ) : PsiUpdateModCommandAction<KtAnnotationEntry>(annotationEntry) {
        override fun invoke(
            context: ActionContext,
            element: KtAnnotationEntry,
            updater: ModPsiUpdater
        ) {
            element.addUseSiteTarget(useSiteTarget)
        }

        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("text.change.use.site.target.0", useSiteTarget.renderName)
        }
    }
}
