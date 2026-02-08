// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.lang.parameterInfo.ParameterInfoUtils.findParentOfType
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.plugins.gradle.service.resolve.GradlePluginReference

private val GRADLE_DSL_ID: Name = Name.identifier("id")
private val PLUGIN_DEPENDENCIES_SPEC = FqName("PluginDependenciesSpec")

private val KOTLIN_PROJECT_SCRIPT_TEMPLATE = FqName("KotlinProjectScriptTemplate")
private val PLUGINS: Name = Name.identifier("plugins")

class KotlinGradlePluginReferenceProvider : AbstractKotlinGradleReferenceProvider() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun getImplicitReference(
        element: PsiElement,
        offsetInElement: Int
    ): PsiSymbolReference? = when (element) {
        is KtNameReferenceExpression -> createFromNameReference(element)
        is KtCallExpression -> createFromCall(element)
        else -> null
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun createFromCall(element: KtCallExpression): GradlePluginReference? {
        val maybePluginId = getSingleFunctionCallableId(element)
        if (maybePluginId == null || !maybePluginId.isPluginId()) return null

        val literal = PsiTreeUtil.findChildOfType(element, KtLiteralStringTemplateEntry::class.java) ?: return null
        val range = TextRange(0, literal.textRange.length)

        return GradlePluginReference(literal, range, literal.text)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun createFromNameReference(element: KtNameReferenceExpression): GradlePluginReference? {
        val parentCallExpression = findParentOfType(element, KtCallExpression::class.java) ?: return null
        val maybePluginsSection = getSingleFunctionCallableId(parentCallExpression)
        if (maybePluginsSection == null || !maybePluginsSection.isPluginsSection()) return null

        val pluginCallableId = getSingleVariableCallableId(element)
        if (pluginCallableId == null || pluginCallableId.packageName != GRADLE_DSL_PACKAGE) return null

        val range = TextRange(0, element.textRange.length)
        return GradlePluginReference(element, range, pluginCallableId.callableName.identifier)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun getSingleFunctionCallableId(callExpression: KtCallExpression) = allowAnalysisOnEdt {
        analyze(callExpression) {
            callExpression.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun getSingleVariableCallableId(nameReferenceExpression: KtNameReferenceExpression) = allowAnalysisOnEdt {
        analyze(nameReferenceExpression) {
            nameReferenceExpression.resolveToCall()?.singleVariableAccessCall()?.symbol?.callableId
        }
    }

    private fun CallableId.isPluginsSection(): Boolean =
        callableName == PLUGINS && className == KOTLIN_PROJECT_SCRIPT_TEMPLATE

    private fun CallableId.isPluginId(): Boolean =
        callableName == GRADLE_DSL_ID && (packageName == GRADLE_DSL_PACKAGE || className == PLUGIN_DEPENDENCIES_SPEC)
}