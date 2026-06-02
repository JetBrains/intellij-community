// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.asSafely
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

internal fun isGradleDependenciesCompletionEnabled(parameters: CompletionParameters): Boolean =
  useDependencyCompletionService() && !parameters.isAndroidProject()

private fun CompletionParameters.isAndroidProject(): Boolean {
  val snapshot = this.originalFile.manager.project.workspaceModel.currentSnapshot
  return snapshot.entities<FacetEntity>().any { it.name == "Android" }
}

internal const val PLUGINS = "plugins"
internal const val DEPENDENCIES = "dependencies"

internal val BUILD_GRADLE_KTS_FILE_PATTERN = psiFile().withName(KOTLIN_DSL_SCRIPT_NAME)

internal inline fun <reified I : PsiElement> psiElement(): PsiElementPattern.Capture<I> {
  return psiElement(I::class.java)
}

internal fun insideBuildGradleKts() = psiElement().inFile(BUILD_GRADLE_KTS_FILE_PATTERN)

/**
 * Matches any PSI element inside the curly brackets `{...}`, coming after the given [blockName].
 * @param blockName is `dependencies`, `plugins`, `repositories` or any other Gradle block name.
 */
internal fun insideScriptBlockPattern(blockName: String) = insideBuildGradleKts()
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
  val pattern = psiElement()
    .withSuperParent(2, psiElement<KtBinaryExpression>()) // spring-boot-starter-mail
    .withSuperParent(3, scriptBlockElementPattern(blockName))

  return pattern.accepts(this)
}

private fun PsiElement.isOnTheTopLevelOfScriptBlockDot(blockName: String): Boolean {
  val pattern = psiElement()
    .withSuperParent(2, psiElement<KtDotQualifiedExpression>()) // org.springframework.boot
    .withSuperParent(3, scriptBlockElementPattern(blockName))

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
 */
internal fun PsiElement.isSingleDependencyArgumentInsideQuotes(): Boolean =
  this.isDependencyArgumentInsideQuotes()
  && this.surroundingArgumentsSize == 1
  && (this.argumentName.isEmpty() || this.argumentName == "dependencyNotation")

/**
 * Checks whether the current [PsiElement] is a string literal used as an argument
 * in a Gradle dependency configuration call (e.g., `implementation("g", "a", "v")`)
 */
internal fun PsiElement.isPositionalOrNamedDependencyArgument(): Boolean {
  if (!this.isDependencyArgumentInsideQuotes()) return false

  val argumentsSize = this.surroundingArgumentsSize
  val argumentName = this.argumentName
  val namedArgumentNamesOfCoordinates = listOf("group", "name", "version")

  if (argumentsSize == 1 && argumentName in namedArgumentNamesOfCoordinates) return true
  if (argumentsSize !in 2..6) return false
  return argumentName in namedArgumentNamesOfCoordinates || (argumentName.isEmpty() && this.argumentIndex in 0..2)
}

internal fun PsiElement.isExcludeArgument(): Boolean {
  val leaf = this.asSafely<LeafPsiElement>() ?: return false
  val stringTemplateExpr =
    // exclude("leaf<caret>) - open quote only
    leaf.parent.asSafely<KtStringTemplateExpression>() ?:
    // exclude("leaf<caret>") - both quotes
    leaf.parent.asSafely<KtLiteralStringTemplateEntry>()
      ?.parent.asSafely<KtStringTemplateExpression>() ?: return false

  val callExpression = stringTemplateExpr.parent.asSafely<KtValueArgument>()
                         ?.parent.asSafely<KtValueArgumentList>()
                         ?.parent.asSafely<KtCallExpression>() ?: return false
  return callExpression.isCallWithReceiverSubtypeDumbAware(
    FqName("org.gradle.api.artifacts.Dependency"),
    setOf("exclude")
  )
}

internal fun PsiElement.isDependencyArgumentInsideQuotes(): Boolean {
  val leaf = this.asSafely<LeafPsiElement>() ?: return false
  val stringTemplateExpr =
    // exclude("leaf<caret>) - open quote only
    leaf.parent.asSafely<KtStringTemplateExpression>() ?:
    // exclude("leaf<caret>") - both quotes
    leaf.parent.asSafely<KtLiteralStringTemplateEntry>()
      ?.parent.asSafely<KtStringTemplateExpression>() ?: return false

  val callExpr = stringTemplateExpr.parent.asSafely<KtValueArgument>()
                   ?.parent.asSafely<KtValueArgumentList>()
                   ?.parent.asSafely<KtCallExpression>() ?: return false
  return callExpr.isDependencyConfiguration()
         || callExpr.acceptsStringCoordinatesArgument()
}

// Matches: implementation(<caret>), implementation(platf<caret>)
internal fun PsiElement.isSingleDependencyArgumentWithoutQuotesAndDots(): Boolean {
  val valueArgumentList = this.asSafely<LeafPsiElement>()
                            ?.parent.asSafely<KtNameReferenceExpression>()
                            ?.parent.asSafely<KtValueArgument>()
                            ?.parent.asSafely<KtValueArgumentList>() ?: return false
  if (valueArgumentList.arguments.size > 1) return false
  val callExpr = valueArgumentList.parent.asSafely<KtCallExpression>() ?: return false
  return callExpr.isDependencyConfiguration()
}

internal fun PsiElement.isDependencyArgumentWithoutQuotes(): Boolean {
  val refExpr = this.asSafely<LeafPsiElement>()
                  ?.parent.asSafely<KtNameReferenceExpression>() ?: return false

  val valueArgument = if (refExpr.parent is KtDotQualifiedExpression) {
    // implementation(libs.input.<caret>)
    refExpr.parent.parent.asSafely<KtValueArgument>() ?: return false
  }
  else {
    // implementation(lib<caret>)
    refExpr.parent.asSafely<KtValueArgument>() ?: return false
  }
  val valueArgumentList = valueArgument.parent.asSafely<KtValueArgumentList>() ?: return false
  if (valueArgumentList.arguments.size > 1) return false

  val callExpr = valueArgumentList.parent.asSafely<KtCallExpression>() ?: return false
  return callExpr.isDependencyConfiguration()
         || callExpr.acceptsVersionCatalogDependencyArgument()
}

private fun KtCallExpression.isDependencyConfiguration(): Boolean {
  val callName = when (calleeExpression) {
                   // implementation(input<caret>)
                   is KtNameReferenceExpression -> calleeExpression?.text
                   // "implementation"(input<caret>)
                   is KtStringTemplateExpression -> calleeExpression?.getChildOfType<KtLiteralStringTemplateEntry>()?.text
                   else -> null
                 } ?: return false

  val dependencyConfigurations = findConfigurationsForDependencies(this) ?: setOf(
    "implementation", "api", "compileOnly", "compileOnlyApi", "runtimeOnly",
    "testImplementation", "testCompileOnly", "testRuntimeOnly",
    "annotationProcessor", "testAnnotationProcessor",
  )
  return dependencyConfigurations.contains(callName)
}

private val DEPENDENCY_HANDLER_FQN = FqName("org.gradle.api.artifacts.dsl.DependencyHandler")

private fun KtCallExpression.acceptsVersionCatalogDependencyArgument(): Boolean {
  return isCallWithReceiverSubtypeDumbAware(DEPENDENCY_HANDLER_FQN, setOf("platform", "enforcedPlatform", "testFixtures", "variantOf"))
}

private fun KtCallExpression.acceptsStringCoordinatesArgument(): Boolean =
  isCallWithReceiverSubtypeDumbAware(DEPENDENCY_HANDLER_FQN, setOf("platform", "enforcedPlatform", "testFixtures"))

/**
 * For Gradle 8.2+ returns only configurations that can declare dependencies (e.g., scopes, annotation processors)
 * For older versions returns all configurations, even those that could not be used in the `dependencies { }` block.
 *
 * Returns null if the module or Gradle extensions data is not available.
 */
internal fun findConfigurationsForDependencies(psiElement: PsiElement): List<String>? {
  val module = ModuleUtilCore.findModuleForPsiElement(psiElement) ?: return null
  val extensionsData = GradleExtensionsSettings.getInstance(psiElement.project).getExtensionsFor(module) ?: return null
  val configurations = extensionsData.configurations.values
  return configurations
    .filter { it.canBeUsedInDependenciesBlock() }
    .filter { it.name.matches(kotlinMethodNamePattern) }
    .map { it.name }
}

/**
 * @return true if a configuration can declare dependencies and it's Gradle 8.2+.
 * For older versions, returns true for each configuration.
 */
private fun GradleExtensionsSettings.GradleConfiguration.canBeUsedInDependenciesBlock(): Boolean =
  this.canDeclareDependencies != false

/** Starts with a letter or underscore, then might contain only letters, digits, and underscores */
private val kotlinMethodNamePattern = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

internal fun isConfigurationNamePsiResolvable(configurationName: String, context: PsiElement): Boolean =
  isNameResolvableToMethod(
    methodName = configurationName,
    returnType = GRADLE_DEPENDENCY_CLASS_ID,
    receiverType = GRADLE_DEPENDENCY_HANDLER_CLASS_ID,
    context
  )

private val PsiElement.surroundingArgumentsSize
  get(): Int =
    this.getParentOfType<KtValueArgumentList>(strict = true, stopAt = arrayOf(KtCallExpression::class.java))?.arguments?.size ?: 0

internal val PsiElement.argumentName: String
  get() {
    val valueArgument =
      this.getParentOfType<KtValueArgument>(strict = true, stopAt = arrayOf(KtCallExpression::class.java)) ?: return ""
    val valueArgumentName = valueArgument.children[0] as? KtValueArgumentName ?: return ""
    return valueArgumentName.text
  }

internal val PsiElement.argumentIndex: Int
  get() {
    val valueArgument =
      this.getParentOfType<KtValueArgument>(strict = true, stopAt = arrayOf(KtCallExpression::class.java)) ?: return -1
    val argumentList = valueArgument.parent as? KtValueArgumentList ?: return -1
    return argumentList.arguments.indexOf(valueArgument)
  }

internal fun PsiElement.getGroupPrefix(): String = getCoordinatePrefix("group", 0)
internal fun PsiElement.getArtifactPrefix(): String = getCoordinatePrefix("name", 1)
internal fun PsiElement.getVersionPrefix(): String = getCoordinatePrefix("version", 2)

internal fun PsiElement.getExcludeArtifactPrefix(): String = getCoordinatePrefix("module", 1)

internal fun PsiElement.getCoordinatePrefix(text: String, index: Int): String {
  val valueArgument = this.getParentOfType<KtValueArgument>(strict = true, stopAt = arrayOf(KtCallExpression::class.java)) ?: return ""
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
