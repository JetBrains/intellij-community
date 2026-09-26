// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.inspections.visitors

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.gradle.codeInsight.GradleInspectionBundle
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.DependencyType
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.evaluateString
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.findDependencyType
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.findNamedOrPositionalArgument
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.findScriptInitializers
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.fixes.ShowDuplicateElementsAction
import com.intellij.gradle.codeInsight.kotlin.backend.inspections.getBlock
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.service.resolve.GradleVersionCatalogPsiResolverUtil.getResolvedDependency
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings

internal class KotlinAvoidDuplicateDependenciesInspectionVisitor(
  private val holder: ProblemsHolder,
  private val isOnTheFly: Boolean,
) : KtVisitorVoid() {
  override fun visitKtFile(file: KtFile) {
    val configurationExtensions = file.findConfigurationExtensions() ?: return
    val dependencyBlocks = file.findScriptInitializers("dependencies").mapNotNull { it.getBlock() }

    dependencyBlocks.flatMap { it.descendantsOfType<KtCallExpression>() }
      .mapNotNull { callExpr ->
        val dependencyType = findDependencyType(callExpr)
        if (dependencyType == DependencyType.SINGLE_ARGUMENT || dependencyType == DependencyType.NAMED_ARGUMENTS) {
          val key = extractDependencyKey(callExpr, dependencyType) ?: return@mapNotNull null
          key to callExpr
        }
        else {
          null
        }
      }
      .groupBy { it.first }
      .mapNotNull { entry ->
        entry.key to entry.value.map {
          val configName = it.second.calleeExpression?.text?.trim('"') ?: return@mapNotNull null
          configName to it.second
        }
      }
      .forEach { (key, dependencies) ->
        if (isOnTheFly) reportProblems(key, dependencies, configurationExtensions)
        else reportProblemsInBatchMode(key, dependencies, configurationExtensions)
      }
  }

  /**
   * Tries to evaluate the dependency coordinates which act as the key
   *
   * Will return null if any part of evaluation fails
   */
  private fun extractDependencyKey(
    dependency: KtCallExpression,
    type: DependencyType,
  ): String? {
    return when (type) {
      DependencyType.SINGLE_ARGUMENT -> extractSingleArgumentKey(dependency)
      DependencyType.NAMED_ARGUMENTS -> extractNamedArgumentsKey(dependency)
      else -> null
    }
  }

  private fun extractSingleArgumentKey(dependency: KtCallExpression): String? {
    val argumentExpression = dependency.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null

    // string or direct constant reference to a string argument
    val stringArgument = argumentExpression.evaluateString()
    if (stringArgument != null) {
      return stringArgument
    }

    // kotlin(id) argument
    if (argumentExpression is KtCallExpression && argumentExpression.calleeExpression?.text == "kotlin") {
      val argList = argumentExpression.valueArgumentList ?: return null
      val module = argList.findNamedOrPositionalArgument("module", 0)?.evaluateString() ?: return null
      val version = argList.findNamedOrPositionalArgument("version", 1)?.evaluateString()
      return "org.jetbrains.kotlin:kotlin-$module${version?.let { ":$version" } ?: ""}"
    }

    // version catalog argument
    if (argumentExpression is KtDotQualifiedExpression) {
      val resolved = argumentExpression.selectorExpression?.mainReference?.resolve() as? PsiMethod ?: return null
      getResolvedDependency(resolved, argumentExpression)?.toString()?.let { return it }
    }

    return argumentExpression.text
  }

  private fun extractNamedArgumentsKey(dependency: KtCallExpression): String? {
    val argList = dependency.valueArgumentList ?: return null

    val group = argList.findNamedOrPositionalArgument("group", 0)?.evaluateString() ?: return null

    val name = argList.findNamedOrPositionalArgument("name", 1)?.evaluateString() ?: return null

    // if the version argument is missing, return a key without a version
    val versionArg = argList.findNamedOrPositionalArgument("version", 2) ?: return "$group:$name"

    val version = versionArg.evaluateString() ?: return null

    return "$group:$name:$version"
  }

  private fun reportProblems(
    key: String,
    dependencies: List<Pair<String, KtCallExpression>>,
    configurationExtensions: ConfigurationExtensions,
  ) {
    dependencies.forEach { dependency ->
      val otherDependencies = dependencies - dependency
      reportProblem(key, dependency.first, dependency.second, otherDependencies, configurationExtensions)
    }
  }

  /**
   * Similar to [reportProblems], but ensures only one [RemoveExactDuplicateDependencies] quick-fix
   * per exact duplicate dependency group, so that the 'Apply all fixes in file' works correctly
   * and doesn't remove *all* duplicates.
   */
  private fun reportProblemsInBatchMode(
    key: String,
    dependencies: List<Pair<String, KtCallExpression>>,
    configurationExtensions: ConfigurationExtensions,
  ) {
    val dependenciesByConfigName = dependencies.groupBy { it.first }
    dependenciesByConfigName.forEach { (_, duplicateDependencies) ->
      duplicateDependencies.groupBy { it.second.text }.forEach { (_, exactDuplicateDependencies) ->
        val exactDuplicateDependency = exactDuplicateDependencies.first()
        val otherDependencies = duplicateDependencies - exactDuplicateDependency
        reportProblemDuplicates(key, exactDuplicateDependency.second, otherDependencies)
      }
    }
    reportProblems(key, dependencies, configurationExtensions)
  }

  /**
   * Report a dependency that is duplicate (same coordinates and configuration)
   * or inheriting from a super configuration which also declares the same coordinates
   * based on [otherDependencies].
   *
   * - Offer an intention to navigate to the duplicates / relevant super config dependencies.
   * - Offer a quick fix to remove only exact duplicates if available.
   */
  private fun reportProblem(
    key: String,
    configName: String,
    callExpr: KtCallExpression,
    otherDependencies: List<Pair<String, KtCallExpression>>,
    configurationExtensions: ConfigurationExtensions,
  ) {
    val superConfigNames = configurationExtensions.withExtendedConfigurations(configName)
    val inheritedDependencies = otherDependencies.filter { (otherConfigName, _) ->
      superConfigNames.contains(otherConfigName)
    }
    if (inheritedDependencies.isEmpty()) return

    val (duplicateDependencies, dependenciesFromSuperConfigurations) = inheritedDependencies.partition { it.first == configName }

    if (isOnTheFly) {
      reportProblemDuplicates(key, callExpr, duplicateDependencies)
    }

    if (dependenciesFromSuperConfigurations.isNotEmpty()) {
      holder.problem(
        callExpr,
        GradleInspectionBundle.message("inspection.message.avoid.duplicate.dependencies.inherited.descriptor", key)
      )
        .fix(ShowDuplicateElementsAction.forDependencies(key, dependenciesFromSuperConfigurations.map { it.second }))
        .register()
    }
  }

  private fun reportProblemDuplicates(
    key: String,
    callExpr: KtCallExpression,
    duplicateDependencies: List<Pair<String, KtCallExpression>>,
  ) {
    if (duplicateDependencies.isEmpty()) return
    val duplicateCallExprs = duplicateDependencies.map { it.second }
    val exactDuplicateCallExprs = duplicateCallExprs.filter { it.text == callExpr.text }
    val potentialRemoveFix =
      if (exactDuplicateCallExprs.isNotEmpty()) RemoveExactDuplicateDependencies(exactDuplicateCallExprs)
      else null
    holder.problem(
      callExpr,
      GradleInspectionBundle.message("inspection.message.avoid.duplicate.dependencies.descriptor", key)
    )
      .maybeFix(potentialRemoveFix)
      .fix(ShowDuplicateElementsAction.forDependencies(key, duplicateDependencies.map { it.second }))
      .register()
  }
}

/**
 * @return the configuration hierarchy of the Gradle project related to this script,
 * or `null` if the project has not been synced yet
 */
private fun KtFile.findConfigurationExtensions(): ConfigurationExtensions? {
  val module = module ?: return null
  val extensionsData = GradleExtensionsSettings.getInstance(project).getExtensionsFor(module) ?: return null
  // buildscript configurations are not a part of this map, which is intended: only the top-level `dependencies` block is inspected
  val configurations = extensionsData.configurations.takeIf { it.isNotEmpty() } ?: return null
  return ConfigurationExtensions(configurations.mapValues { (_, configuration) -> configuration.extendsFrom })
}

/**
 * The actual configuration hierarchy of a Gradle project, as reported by Gradle during the project sync.
 *
 * @param extendsFrom maps a configuration to the configurations it extends from directly.
 */
private class ConfigurationExtensions(private val extendsFrom: Map<String, List<String>>) {
  private val cache = mutableMapOf<String, Set<String>>()

  fun withExtendedConfigurations(configuration: String): Set<String> =
    cache.getOrPut(configuration) { collectExtendedConfigurations(configuration) }

  private fun collectExtendedConfigurations(configuration: String): Set<String> {
    val result = mutableSetOf(configuration)
    val initialExtendedConfigurations = extendsFrom[configuration] ?: return result
    val configurationsToVisit = ArrayDeque(initialExtendedConfigurations)
    while (configurationsToVisit.isNotEmpty()) {
      val extendedConfiguration = configurationsToVisit.removeFirst()
      // `result` also guards against cycles, which Gradle forbids but does not detect until resolution
      if (!result.add(extendedConfiguration)) continue
      configurationsToVisit.addAll(extendsFrom[extendedConfiguration] ?: emptyList())
    }
    return result
  }
}

private class RemoveExactDuplicateDependencies(
  dependenciesToRemove: List<KtCallExpression>,
) : KotlinModCommandQuickFix<KtCallExpression>() {
  private val dependencyToRemovePointers = dependenciesToRemove.map { it.createSmartPointer() }

  override fun getName(): @IntentionName String = GradleInspectionBundle.message("intention.name.remove.duplicate.dependencies")
  override fun getFamilyName(): @IntentionFamilyName String = name

  override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
    val dependenciesToRemove = dependencyToRemovePointers.mapNotNull { updater.getWritable(it.element) }
    dependenciesToRemove.forEach { it.delete() }
  }
}
