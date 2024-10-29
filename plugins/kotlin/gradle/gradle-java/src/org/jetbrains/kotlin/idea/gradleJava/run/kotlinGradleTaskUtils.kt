// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK_CONTAINER
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION


private const val GRADLE_KOTLIN_PROJECT_DELEGATE = "org.gradle.kotlin.dsl.support.delegates.ProjectDelegate"

@ApiStatus.Internal
fun isInGradleKotlinScript(psiElement: PsiElement): Boolean {
    val file = psiElement.containingFile?.virtualFile ?: return false
    return file.name.endsWith(".$KOTLIN_DSL_SCRIPT_EXTENSION")
}

internal fun isRunTaskInGutterCandidate(element: PsiElement): Boolean {
    if (element !is LeafPsiElement) return false
    return isOpenQuoteOfStringArgumentInCall(element)
            || isIdentifierInPropertyWithDelegate(element)
}

@ApiStatus.Internal
fun findTaskNameAround(element: PsiElement): String? {
    return findTaskNameInSurroundingCallExpression(element)
        ?: findTaskNameInSurroundingProperty(element)
}

/** Returns `true` for the first quote `"` before`taskName` in example: `tasks.register("taskName")` */
private fun isOpenQuoteOfStringArgumentInCall(leafElement: LeafPsiElement) =
    leafElement.takeIf { it.elementType == KtTokens.OPEN_QUOTE }
        ?.parent.safeAs<KtStringTemplateExpression>()
        ?.parent.safeAs<KtValueArgument>()
        ?.parent.safeAs<KtValueArgumentList>()
        ?.parent is KtCallExpression

/** Returns `true` for `taskName` element in example: `val taskName by tasks.registering() { }` */
private fun isIdentifierInPropertyWithDelegate(leafElement: LeafPsiElement): Boolean =
    leafElement.safeAs<LeafPsiElement>()
        ?.takeIf { it.elementType == KtTokens.IDENTIFIER }
        ?.parent.safeAs<KtProperty>()?.hasDelegateExpression()
        ?: false

private fun findTaskNameInSurroundingCallExpression(element: PsiElement): String? {
    val callExpression = element.getParentOfType<KtCallExpression>(false, KtScriptInitializer::class.java) ?: return null
    if (!hasNameRelatedToTasks(callExpression)) return null
    return analyze(callExpression) {
        val resolvedCall = callExpression.resolveToCall() ?: return null
        val functionCall = resolvedCall.singleFunctionCallOrNull() ?: return null
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

/**
 * Allows avoiding resolve (analysis) of PSI elements not related to Gradle tasks configuration
 */
private fun hasNameRelatedToTasks(callExpression: KtCallExpression): Boolean {
    val methodName = callExpression.getCallNameExpression()?.getReferencedNameAsName()?.identifier ?: return false
    return methodName in taskContainerMethods || methodName == "task"
}

private fun findTaskNameInSurroundingProperty(element: PsiElement): String? {
    val stopAt = arrayOf(KtScriptInitializer::class.java, KtLambdaExpression::class.java)
    // A property element could contain e.g., `val taskName by tasks.registering{}` or `val taskName by tasks.creating{}`
    val property = element.getParentOfType<KtProperty>(false, *stopAt) ?: return null
    // `tasks.registering{}` would be a delegateExpression for the example above
    val delegateExpression = property.delegateExpression ?: return null
    return analyze(delegateExpression) {
        val resolvedCall = delegateExpression.resolveToCall() ?: return null
        val singleCall = resolvedCall.singleCallOrNull<KaCallableMemberCall<*, *>>() ?: return null
        if (!doesCustomizeTask(singleCall)) return null
        val taskName = property.symbol.name.identifier
        taskName
    }
}

private fun doesCustomizeTask(functionCall: KaCallableMemberCall<*, *>): Boolean {
    val callableId = functionCall.partiallyAppliedSymbol.symbol.callableId ?: return false
    val methodName = callableId.callableName.identifier
    val classFqName = getReceiverClassFqName(functionCall)
        ?: callableId.classId?.asSingleFqName()
        ?: return false
    return isMethodOfTaskContainer(methodName, classFqName)
            || isMethodOfProject(methodName, classFqName)
}

private fun getReceiverClassFqName(functionCall: KaCallableMemberCall<*, *>): FqName? {
    val type = functionCall.partiallyAppliedSymbol.extensionReceiver?.type
        ?: functionCall.partiallyAppliedSymbol.dispatchReceiver?.type
    return type?.symbol?.classId?.asSingleFqName()
}

private val taskContainerMethods = setOf("register", "create", "named", "registering", "creating")

private fun isMethodOfTaskContainer(methodName: String, fqClassName: FqName) =
    fqClassName == FqName(GRADLE_API_TASK_CONTAINER)
            && methodName in taskContainerMethods

private fun isMethodOfProject(methodName: String, fqClassName: FqName) =
    (methodName == "task") && (fqClassName == FqName(GRADLE_API_PROJECT)
            || fqClassName == FqName(GRADLE_KOTLIN_PROJECT_DELEGATE)
            || fqClassName == FqName("Build_gradle")) // Could be resolved instead of ProjectDelegate on Gradle 6.0
