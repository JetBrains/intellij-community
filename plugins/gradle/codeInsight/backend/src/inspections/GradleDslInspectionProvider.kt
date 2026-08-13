// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.backend.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface GradleDslInspectionProvider {

  companion object {
    val INSTANCE = LanguageExtension<GradleDslInspectionProvider>("org.jetbrains.plugins.gradle.dslInspectionProvider")
  }

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleConfigurationAvoidanceInspection
   */
  fun getConfigurationAvoidanceInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleForeignDelegateInspection
   */
  fun getForeignDelegateInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleIncorrectDependencyNotationArgumentInspection
   */
  fun getIncorrectDependencyNotationArgumentInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleDeprecatedConfigurationInspection
   */
  fun getDeprecatedConfigurationInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradlePluginDslStructureInspection
   */
  fun getPluginDslStructureInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleLatestMinorVersionInspection
   */
  fun isLatestMinorVersionInspectionAvailable(file: PsiFile): Boolean = false
  fun getLatestMinorVersionInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidDependencyNamedArgumentsNotationInspection
   */
  fun isAvoidDependencyNamedArgumentsNotationInspectionAvailable(file: PsiFile): Boolean = false
  fun getAvoidDependencyNamedArgumentsNotationInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleRedundantKotlinStdLibInspection
   */
  fun isRedundantKotlinStdLibInspectionAvailable(file: PsiFile): Boolean = false
  fun getRedundantKotlinStdLibInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidApplyPluginMethodInspection
   */
  fun isAvoidApplyPluginMethodInspectionAvailable(file: PsiFile): Boolean = false
  fun getAvoidApplyPluginMethodInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.AvoidRepositoriesInBuildGradleInspection
   */
  fun isAvoidRepositoriesInBuildGradleInspectionAvailable(file: PsiFile): Boolean = false
  fun getAvoidRepositoriesInBuildGradleInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidDuplicateDependenciesInspection
   */
  fun isAvoidDuplicateDependenciesInspectionAvailable(file: PsiFile): Boolean = false
  fun getAvoidDuplicateDependenciesInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleTaskMissingDescriptionInspection
   */
  fun isTaskMissingDescriptionInspectionAvailable(file: PsiFile): Boolean = false
  fun getTaskMissingDescriptionInspectionVisitor(holder: ProblemsHolder, onTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR

  /**
   * @see com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidDuplicateRepositoriesInspection
   */
  fun isAvoidDuplicateRepositoriesInspectionAvailable(file: PsiFile): Boolean = false
  fun getAvoidDuplicateRepositoriesInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    PsiElementVisitor.EMPTY_VISITOR
}