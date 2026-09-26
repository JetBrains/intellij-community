// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.psi.PsiDocumentManager
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
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isKotlinDslScriptsModelImportSupported
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

internal fun isGradleDependenciesCompletionEnabled(parameters: CompletionParameters): Boolean =
  useDependencyCompletionService() && !parameters.isAndroidProject()

private fun CompletionParameters.isAndroidProject(): Boolean {
  val snapshot = this.originalFile.manager.project.workspaceModel.currentSnapshot
  return snapshot.entities<FacetEntity>().any { it.name == "Android" }
}

internal const val PLUGINS = "plugins"
internal const val DEPENDENCIES = "dependencies"
internal const val KOTLIN_SHORTCUT_GROUP = "org.jetbrains.kotlin"
internal const val KOTLIN_SHORTCUT_ARTIFACT_PREFIX = "kotlin-"
private const val KOTLIN_SHORTCUT_MODULE_ARGUMENT = "module"
private const val KOTLIN_SHORTCUT_VERSION_ARGUMENT = "version"

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

/**
 * Checks whether the current [PsiElement] is a string literal used as the module argument
 * of a Kotlin shortcut dependency call (e.g., `kotlin("std<caret>")`, `embeddedKotlin(module = "std<caret>")`).
 */
internal fun PsiElement.isKotlinShortcutModuleArgument(): Boolean {
  val callType = this.getKotlinShortcutCall() ?: return false

  val argumentsSize = this.surroundingArgumentsSize
  val maxArguments = if (callType == KotlinShortcutCall.KOTLIN) 2 else 1
  if (argumentsSize !in 1..maxArguments) return false

  val argumentName = this.argumentName
  if (argumentName == KOTLIN_SHORTCUT_MODULE_ARGUMENT) return true
  if (argumentName == KOTLIN_SHORTCUT_VERSION_ARGUMENT) return false
  return argumentName.isEmpty() && this.argumentIndex == 0
}

/**
 * Checks whether the current [PsiElement] is a string literal used as the version argument
 * of a Kotlin shortcut dependency call (e.g., `kotlin("stdlib", "1.9<caret>")`,
 * `kotlin(module = "stdlib", version = "1.9<caret>")`).
 *
 * Note: `embeddedKotlin` does not accept a version argument, so this function returns `false` for it.
 */
internal fun PsiElement.isKotlinShortcutVersionArgument(): Boolean {
  if (this.getKotlinShortcutCall() != KotlinShortcutCall.KOTLIN) return false

  val argumentsSize = this.surroundingArgumentsSize
  if (argumentsSize !in 1..2) return false

  val argumentName = this.argumentName
  if (argumentName == KOTLIN_SHORTCUT_VERSION_ARGUMENT) return true
  if (argumentName == KOTLIN_SHORTCUT_MODULE_ARGUMENT) return false
  return argumentName.isEmpty() && this.argumentIndex == 1
}

/**
 * Returns the module text of the first (module) argument of the enclosing Kotlin shortcut call,
 * e.g., `"stdlib"` for `kotlin("stdlib", "1.9.0")` or `"stdlib:1.9.0"` for `kotlin("stdlib:1.9.0")`.
 * Returns an empty string when not inside a Kotlin shortcut call or when the argument is missing.
 */
internal fun PsiElement.getKotlinShortcutModuleText(): String = getCoordinatePrefix(KOTLIN_SHORTCUT_MODULE_ARGUMENT, 0)

/**
 * Returns `true` when the module argument of the enclosing Kotlin shortcut call already encodes a version,
 * e.g., `kotlin("stdlib:1.9.0", ...)`.
 */
internal fun PsiElement.kotlinShortcutModuleHasVersion(): Boolean = ":" in getKotlinShortcutModuleText()

private fun PsiElement.getEnclosingCallExpressionOfStringArgument(): KtCallExpression? {
  val leaf = this.asSafely<LeafPsiElement>() ?: return null
  val stringTemplateExpr =
    // callExpression("leaf<caret>) - open quote only
    leaf.parent.asSafely<KtStringTemplateExpression>() ?:
    // callExpression("leaf<caret>") - both quotes
    leaf.parent.asSafely<KtLiteralStringTemplateEntry>()
      ?.parent.asSafely<KtStringTemplateExpression>() ?: return null

  return stringTemplateExpr.parent.asSafely<KtValueArgument>()
    ?.parent.asSafely<KtValueArgumentList>()
    ?.parent.asSafely<KtCallExpression>()
}

internal enum class KotlinShortcutCall { KOTLIN, EMBEDDED_KOTLIN }

private val DEPENDENCY_HANDLER_SCOPE_FQN = FqName("org.gradle.kotlin.dsl.DependencyHandlerScope")

/**
 * Returns the kind of the enclosing Kotlin shortcut call (`kotlin(...)` or `embeddedKotlin(...)`), or `null` if the argument
 * is not inside a Kotlin shortcut call.
 */
internal fun PsiElement.getKotlinShortcutCall(): KotlinShortcutCall? {
  val callExpression = this.getEnclosingCallExpressionOfStringArgument() ?: return null
  return when {
    callExpression.isCallWithReceiverSubtypeDumbAware(DEPENDENCY_HANDLER_SCOPE_FQN, setOf("kotlin")) -> KotlinShortcutCall.KOTLIN
    callExpression.isCallWithReceiverSubtypeDumbAware(DEPENDENCY_HANDLER_SCOPE_FQN, setOf("embeddedKotlin")) ->
      KotlinShortcutCall.EMBEDDED_KOTLIN
    else -> null
  }
}

internal fun PsiElement.isExcludeArgument(): Boolean {
  val callExpression = this.getEnclosingCallExpressionOfStringArgument() ?: return false
  return callExpression.isCallWithReceiverSubtypeDumbAware(
    FqName("org.gradle.api.artifacts.Dependency"),
    setOf("exclude")
  )
}

internal fun PsiElement.isDependencyArgumentInsideQuotes(): Boolean {
  val callExpression = this.getEnclosingCallExpressionOfStringArgument() ?: return false
  return callExpression.isDependencyConfiguration() || callExpression.acceptsStringCoordinatesArgument()
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

internal fun isConfigurationNamePsiResolvable(configurationName: String, context: PsiElement, file: VirtualFile?): Boolean {
  if (DumbService.isDumb(context.project)) return true
  // For Gradle < 6.0, the Kotlin DSL scripts model is not imported by the IDE,
  // so generated accessor extension functions are not available in the PSI scope.
  // Skip the PSI check for these versions to avoid incorrectly quoting standard configurations.
  val containingFile = context.containingFile ?: return true
  val virtualFile = containingFile.virtualFile ?: file
  // If we can't determine Gradle version reliably--skip the PSI check
  if (null == virtualFile) return true
  val gradleVersion = GradleUtil.getGradleVersion(context.project, virtualFile.path)
  return !isKotlinDslScriptsModelImportSupported(gradleVersion) || isNameResolvableToMethod(
    methodName = configurationName,
    returnType = GRADLE_DEPENDENCY_CLASS_ID,
    receiverType = GRADLE_DEPENDENCY_HANDLER_CLASS_ID,
    context
  )
}

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
