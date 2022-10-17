// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.facet.KotlinFacet

abstract class AbstractChangeFeatureSupportLevelFix(
    element: PsiElement,
    protected val feature: LanguageFeature,
    protected val featureSupport: LanguageFeature.State,
    private val featureShortName: String = feature.presentableName
) : KotlinQuickFixAction<PsiElement>(element) {
    protected val featureSupportEnabled: Boolean
        get() = featureSupport == LanguageFeature.State.ENABLED || featureSupport == LanguageFeature.State.ENABLED_WITH_WARNING

    final override fun getFamilyName() = KotlinBundle.message("fix.change.feature.support.family", featureShortName)

    override fun getText(): String = getFixText(featureSupport, featureShortName)

    companion object {
        @Nls
        fun getFixText(state: LanguageFeature.State, featureShortName: String): String {
            return when (state) {
                LanguageFeature.State.ENABLED -> {
                    KotlinBundle.message("fix.change.feature.support.enabled", featureShortName)
                }
                LanguageFeature.State.ENABLED_WITH_WARNING -> {
                    KotlinBundle.message("fix.change.feature.support.enabled.warning", featureShortName)
                }
                LanguageFeature.State.DISABLED -> {
                    KotlinBundle.message("fix.change.feature.support.disabled", featureShortName)
                }
            }
        }
    }

    abstract class FeatureSupportIntentionActionsFactory : KotlinIntentionActionsFactory() {
        protected fun shouldConfigureInProject(module: Module): Boolean {
            val facetSettings = KotlinFacet.get(module)?.configuration?.settings
            return (facetSettings == null || facetSettings.useProjectSettings) &&
                    module.getBuildSystemType() == BuildSystemType.JPS
        }

        protected fun doCreateActions(
            diagnostic: Diagnostic,
            feature: LanguageFeature,
            quickFixConstructor: (PsiElement, LanguageFeature, LanguageFeature.State) -> IntentionAction
        ): List<IntentionAction> {
            val newFeatureSupports = when (diagnostic.factory) {
                Errors.EXPERIMENTAL_FEATURE_WARNING -> {
                    if (Errors.EXPERIMENTAL_FEATURE_WARNING.cast(diagnostic).a.first != feature) return emptyList()
                    listOf(LanguageFeature.State.ENABLED)
                }
                else -> return emptyList()
            }

            return newFeatureSupports.map { quickFixConstructor(diagnostic.psiElement, feature, it) }
        }
    }
}
