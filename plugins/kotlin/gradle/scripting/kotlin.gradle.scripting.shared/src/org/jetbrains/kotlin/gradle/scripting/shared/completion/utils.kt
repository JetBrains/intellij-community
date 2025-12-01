// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.completion

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns.string
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import java.io.IOException

private val LOG = Logger.getInstance("KotlinGradleScriptCompletionUtils")

internal fun readLinesFromFile(path: String): List<String> {
    try {
        val stream = object {}::class.java.getResourceAsStream(path) ?: return emptyList()
        return stream.bufferedReader().readLines()
    } catch (e: IOException) {
        LOG.error("Failed to read from $path", e)
        return emptyList()
    }
}

internal const val DEPENDENCIES = "dependencies"

internal val BUILD_GRADLE_KTS_FILE_PATTERN = psiFile().withName(KOTLIN_DSL_SCRIPT_NAME)

internal inline fun <reified I : PsiElement> psiElement(): PsiElementPattern.Capture<I> {
    return psiElement(I::class.java)
}

/**
 * Matches any PSI element inside the curly brackets `{...}`, coming after the given [blockName].
 * @param blockName is `dependencies`, `plugins`, `repositories` or any other Gradle block name.
 */
internal fun insideScriptBlockPattern(blockName: String) = psiElement()
    .inFile(BUILD_GRADLE_KTS_FILE_PATTERN)
    .inside(scriptBlockElementPattern(blockName))

internal fun PsiElement.isOnTheTopLevelOfScriptBlock(blockName: String): Boolean {
    return this.isOnTheTopLevelOfScriptBlockSimple(blockName)
            || this.isOnTheTopLevelOfScriptBlockDash(blockName)
            || this.isOnTheTopLevelOfScriptBlockDot(blockName)
}

private fun PsiElement.isOnTheTopLevelOfScriptBlockSimple(blockName: String): Boolean {
    val pattern = psiElement().withParent(
        psiElement().andOr(
            psiElement<KtNameReferenceExpression>(), // junit
            psiElement<PsiErrorElement>(), // junit:junit
        ).withParent(
            scriptBlockElementPattern(blockName)
        )
    )
    return pattern.accepts(this)
}

private fun PsiElement.isOnTheTopLevelOfScriptBlockDash(blockName: String): Boolean {
    val pattern = psiElement().withSuperParent(
        2, psiElement<KtBinaryExpression>() // spring-boot-starter-mail
    ).withAncestor(10, scriptBlockElementPattern(blockName))

    return pattern.accepts(this)
}

private fun PsiElement.isOnTheTopLevelOfScriptBlockDot(blockName: String): Boolean {
    val pattern = psiElement().withSuperParent(
        2, psiElement<KtDotQualifiedExpression>() // org.springframework.boot
    ).withAncestor(10, scriptBlockElementPattern(blockName))

    return pattern.accepts(this)
}

internal fun getDependencyCompletionStartOffset(text: String, offset: Int): Int {
    var start = offset - 1
    while (start > 0 && text[start].isAllowedInDependencyCompletion()) {
        start--
    }
    return start + 1
}

internal fun getDependencyCompletionEndOffset(text: String, offset: Int): Int {
    var end = offset
    while (end < text.length && text[end].isAllowedInDependencyCompletion()) {
        end++
    }
    return end
}

private fun Char.isAllowedInDependencyCompletion(): Boolean {
    return isLetterOrDigit() || this == '.' || this == '-' || this == '_' || this == ':'
}

/**
 * Checks whether the current [PsiElement] is a string literal used as an argument
 * in a Gradle dependency configuration call (e.g., `implementation("...")`, `api("...")`, etc.)
 *
 * @param dependencyConfigurations the names of supported dependency functions
 *        such as `"implementation"`, `"api"`, `"testImplementation"`, etc.
 */
internal fun PsiElement.isSingleDependencyArgument(dependencyConfigurations: List<String>): Boolean =
    this.isDependencyArgument(dependencyConfigurations) && this.argumentsSize == 1

/**
 * Checks whether the current [PsiElement] is a string literal used as an argument
 * in a Gradle dependency configuration call (e.g., `implementation("g", "a", "v")`)
 *
 * @param dependencyConfigurations the names of supported dependency functions
 *        such as `"implementation"`, `"api"`, `"testImplementation"`, etc.
 */
internal fun PsiElement.isPositionalOrNamedDependencyArgument(dependencyConfigurations: List<String>): Boolean {
    if (!this.isDependencyArgument(dependencyConfigurations)) return false

    val argumentsSize = this.argumentsSize

    if (argumentsSize == 1 && this.argumentName in setOf("group", "name", "version")) return true

    return argumentsSize in 2..6 // group, name, version, configuration, classifier, ext
}

internal fun PsiElement.isDependencyArgument(dependencyConfigurations: Collection<String>): Boolean {
    val pattern = psiElement<LeafPsiElement>().withSuperParent(1, KtLiteralStringTemplateEntry::class.java)
        .withSuperParent(2, KtStringTemplateExpression::class.java)
        .withSuperParent(3, KtValueArgument::class.java)
        .withSuperParent(4, psiElement<KtValueArgumentList>())
        .withSuperParent(5, psiElement<KtCallExpression>().withChild(
            psiElement<KtNameReferenceExpression>().withText(string().oneOf(dependencyConfigurations))
        ))

    return pattern.accepts(this)
}

private val PsiElement.argumentsSize get(): Int = (this.parent?.parent?.parent?.parent as? KtValueArgumentList)?.arguments?.size ?: 0

private val PsiElement.argumentName: String
    get() {
        val valueArgument = this.parent?.parent?.parent as? KtValueArgument ?: return ""
        val valueArgumentName = valueArgument.children[0] as? KtValueArgumentName ?: return ""
        return valueArgumentName.text
    }

internal fun PsiElement.getGroupPrefix(): String = getCoordinatePrefix("group", 0)
internal fun PsiElement.getArtifactPrefix(): String = getCoordinatePrefix("name", 1)
internal fun PsiElement.getVersionPrefix(): String = getCoordinatePrefix("version", 2)

internal fun PsiElement.getExcludeArtifactPrefix(): String = getCoordinatePrefix("module", 1)

internal fun PsiElement.getCoordinatePrefix(text: String, index: Int): String {
    val valueArgument = this.parent?.parent?.parent as? KtValueArgument ?: return ""
    val argumentList = valueArgument.parent as? KtValueArgumentList ?: return ""
    for (arg in argumentList.arguments) {
        val children = arg.children
        if (children.size == 2) {
            val firstChild = children.firstOrNull() as? KtValueArgumentName ?: continue
            if (text == firstChild.text) return children.getOrNull(1)?.children?.firstOrNull()?.text ?: ""
        }
    }
    val arg = argumentList.arguments.getOrNull(index)?.children?.firstOrNull()?.children?.firstOrNull() ?: return ""
    return (arg as? KtLiteralStringTemplateEntry)?.text ?: ""
}

/**
 * Matches the parent element for everything written inside the curly brackets `{}`, coming after the given [blockName].
 */
private fun scriptBlockElementPattern(blockName: String) = psiElement<KtBlockExpression>()
    .inFile(BUILD_GRADLE_KTS_FILE_PATTERN)
    .withParent(
        psiElement<KtFunctionLiteral>().withParent(
            psiElement<KtLambdaExpression>().withParent(
                psiElement<KtLambdaArgument>().withParent(
                    callExpressionElementWithLambdaPattern(blockName)
                )
            )
        )
    )

/**
 * Matches the element consisting of both [blockName] and the lambda argument coming after it. For example, `dependencies { ... }`.
 */
internal fun callExpressionElementWithLambdaPattern(blockName: String): PsiElementPattern.Capture<KtCallExpression> =
    callExpressionWithName(blockName)
        .withLastChild(psiElement<KtLambdaArgument>())

internal fun callExpressionWithName(blockName: String): PsiElementPattern.Capture<KtCallExpression> = psiElement<KtCallExpression>()
    .withFirstChild(psiElement<KtNameReferenceExpression>().withText(blockName))

internal fun useDependencyCompletionService() : Boolean {
    return Registry.`is`("gradle.dependency.completion.service", true)
}