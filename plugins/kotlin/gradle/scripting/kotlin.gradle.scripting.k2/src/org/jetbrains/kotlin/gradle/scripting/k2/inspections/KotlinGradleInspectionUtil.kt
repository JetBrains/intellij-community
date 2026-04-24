// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendants
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_DEPENDENCY
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_REPOSITORY_HANDLER
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.kotlin.KotlinStringTemplateUPolyadicExpression
import org.jetbrains.uast.toUElementOfType

internal const val KOTLIN_GROUP_ID: String = "org.jetbrains.kotlin"
internal const val GRADLE_KOTLIN_PACKAGE: String = "org.gradle.kotlin.dsl"

internal enum class DependencyType {
    SINGLE_ARGUMENT, NAMED_ARGUMENTS, OTHER
}

/**
 * @return dependency argument type or null if the expression is not a dependency call
 */
internal fun findDependencyType(expression: KtCallExpression): DependencyType? = analyze(expression) {
    val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null
    if (symbol.callableId?.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return null
    val returnType = symbol.returnType.symbol?.classId?.asSingleFqName() ?: return null

    if (returnType == FqName(GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
        || returnType == FqName(GRADLE_API_ARTIFACTS_DEPENDENCY)
    ) {
        when (symbol.valueParameters.firstOrNull()?.name?.identifier) {
            "group" -> return DependencyType.NAMED_ARGUMENTS
            "dependencyNotation" -> return DependencyType.SINGLE_ARGUMENT
            else -> return DependencyType.OTHER
        }
    } else if (symbol.callableId?.callableName?.asString() == "invoke" && returnType == FqName("kotlin.Unit")) {
        // customConf(libs.version.catalog.library) case
        if (symbol.valueParameters.firstOrNull()?.name?.identifier == "dependency") return DependencyType.SINGLE_ARGUMENT
        else DependencyType.OTHER
    }
    return null
}

/**
 * Find an argument expression by its parameter name and index.
 * Works with any legal mix/order of named and positional arguments since positional arguments have a strict order.
 */
internal fun KtValueArgumentList.findNamedOrPositionalArgument(parameterName: String, expectedIndex: Int): KtExpression? {
    val argument = this.arguments.find {
        it.getArgumentName()?.asName?.identifier == parameterName
    } ?: this.arguments.getOrNull(expectedIndex).takeIf { it?.isNamed() == false }
    return argument?.getArgumentExpression()
}

internal fun KtFile.findScriptInitializers(startsWith: String): Sequence<KtScriptInitializer> =
    this.descendants(false) { it !is KtScriptInitializer }.filterIsInstance<KtScriptInitializer>().filter { it.text.startsWith(startsWith) }

internal fun KtFile.findScriptInitializer(startsWith: String): KtScriptInitializer? =
    this.findScriptInitializers(startsWith).firstOrNull()

internal fun KtScriptInitializer.getBlock(): KtBlockExpression? =
    PsiTreeUtil.findChildOfType(this, KtCallExpression::class.java)?.getBlock()

internal fun KtCallExpression.getBlock(): KtBlockExpression? =
    (valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression
        ?: lambdaArguments.lastOrNull()?.getLambdaExpression()?.bodyExpression

internal fun KtBlockExpression.findBlock(name: String): KtBlockExpression? {
    return getChildrenOfType<KtCallExpression>().find {
        it.calleeExpression?.text == name &&
                it.valueArguments.singleOrNull()?.getArgumentExpression() is KtLambdaExpression
    }?.getBlock()
}

internal fun PsiElement.findParentBlock(name: String): PsiElement? {
    val parent = PsiTreeUtil.findFirstParent(this) { elem ->
        (elem is KtCallExpression && elem.calleeExpression?.text?.contains(name) == true)
                || (elem is KtDotQualifiedExpression && elem.text?.contains(name) == true)
    }
    return when (parent) {
        is KtCallExpression -> parent.getBlock()
        is KtDotQualifiedExpression -> parent
        else -> null
    }
}

internal fun KtExpression.evaluateString(): String? {
    val uExpression = this.toUElementOfType<UExpression>() ?: return null
    val string = uExpression.evaluateString()
    if (string != null) return string

    val parts = (uExpression as? KotlinStringTemplateUPolyadicExpression)?.operands?.map { it.evaluateString() } ?: return null
    return if (parts.any { it == null }) null
    else parts.joinToString("")
}

internal fun KtCallExpression.isGradleRepositoriesBlock(): Boolean = analyze(this) {
    if (this@isGradleRepositoriesBlock.calleeExpression?.text != "repositories") return@analyze false
    val symbol = this@isGradleRepositoriesBlock.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return@analyze false
    val callableId = symbol.callableId ?: return@analyze false
    if (callableId.asSingleFqName() !in REPOSITORIES_FQ_NAMES) return@analyze false
    // check if the call parameter is of type `RepositoryHandler.() -> Unit` or `org.gradle.api.Action`
    val paramType = symbol.valueParameters.singleOrNull()?.returnType ?: return@analyze false
    if (paramType.isFunctionType && paramType is KaFunctionType) {
        val leftParam = paramType.typeArguments.getOrNull(0)?.type as? KaClassType ?: return@analyze false
        val rightParam = paramType.typeArguments.getOrNull(1)?.type as? KaClassType ?: return@analyze false
        if (leftParam.classId.asSingleFqName() != FqName(GRADLE_API_REPOSITORY_HANDLER)) return@analyze false
        if (rightParam.classId.asSingleFqName() != FqName("kotlin.Unit")) return@analyze false
        return@analyze true
    } else if (paramType.symbol?.classId?.asSingleFqName() == FqName("org.gradle.api.Action")) {
        return@analyze true
    } else {
        return@analyze false
    }
}

private val REPOSITORIES_FQ_NAMES = setOf(
    FqName("org.gradle.kotlin.dsl.repositories"),
    FqName("org.gradle.kotlin.dsl.ScriptHandlerScope.repositories"),
    FqName("org.gradle.plugin.management.PluginManagementSpec.repositories"),
    FqName("org.gradle.api.initialization.resolve.DependencyResolutionManagement.repositories")
)

internal fun isInheritor(useSiteElement: KtElement, targetClassId: ClassId, baseClassId: ClassId): Boolean {
    if (targetClassId == baseClassId) return true
    return analyze(useSiteElement) {
        val targetClass = findClass(targetClassId) ?: return@analyze false
        val baseClass = findClass(baseClassId) ?: return@analyze false
        return targetClass.isSubClassOf(baseClass)
    }
}