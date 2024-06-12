// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK_CONTAINER
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION


private const val GRADLE_KOTLIN_PROJECT_DELEGATE = "org.gradle.kotlin.dsl.support.delegates.ProjectDelegate"

internal fun isGradleConfiguration(configuration: GradleRunConfiguration) =
    GradleConstants.SYSTEM_ID == configuration.settings.externalSystemId

internal fun isInGradleKotlinScript(psiElement: PsiElement): Boolean {
    val file = psiElement.containingFile.virtualFile ?: return false
    return file.name.endsWith(".$KOTLIN_DSL_SCRIPT_EXTENSION")
}

internal fun isTaskNameCandidate(element: PsiElement): Boolean =
    element.safeAs<KtStringTemplateExpression>()
        ?.parent.safeAs<KtValueArgument>()
        ?.parent.safeAs<KtValueArgumentList>()
        ?.parent is KtCallExpression

internal fun findTaskNameAround(element: PsiElement): String? {
    val referenceExpression = element.getParentOfType<KtCallExpression>(false, KtScriptInitializer::class.java) ?: return null
    return analyze(referenceExpression) {
        val resolveCall = referenceExpression.resolveCallOld() ?: return null
        val functionCall = resolveCall.singleFunctionCallOrNull() ?: return null
        if (!doesCustomizeTask(functionCall)) return null
        val nameArgument = functionCall.argumentMapping
            .filter { it.value.name.identifier == "name" }
            .keys.singleOrNull() ?: return null
        val taskName = nameArgument
            .evaluate()
            ?.value.safeAs<String>()
        taskName
    }
}

private fun doesCustomizeTask(functionCall: KaFunctionCall<*>): Boolean {
    val callableId = functionCall.partiallyAppliedSymbol.symbol.callableId ?: return false
    val fqOutermostClass = callableId.classId?.outermostClassId?.asSingleFqName() ?: return false
    val methodName = callableId.callableName.identifier
    return isMethodOfTaskContainer(methodName, fqOutermostClass)
            || isMethodOfProject(methodName, fqOutermostClass)
}


private fun isMethodOfTaskContainer(methodName: String, fqClassName: FqName) =
    fqClassName == FqName(GRADLE_API_TASK_CONTAINER)
            && (methodName == "register" || methodName == "create" || methodName == "named")


private fun isMethodOfProject(methodName: String, fqClassName: FqName) =
    (fqClassName == FqName(GRADLE_API_PROJECT) || fqClassName == FqName(GRADLE_KOTLIN_PROJECT_DELEGATE))
            && (methodName == "task")
