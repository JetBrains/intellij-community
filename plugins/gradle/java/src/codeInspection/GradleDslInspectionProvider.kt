// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface GradleDslInspectionProvider {

  companion object {
    val INSTANCE = LanguageExtension<GradleDslInspectionProvider>("org.jetbrains.plugins.gradle.dslInspectionProvider")
  }

  /**
   * @see GradleConfigurationAvoidanceInspection
   */
  fun getConfigurationAvoidanceInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) : PsiElementVisitor

  /**
   * @see GradleForeignDelegateInspection
   */
  fun getForeignDelegateInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) : PsiElementVisitor

  /**
   * @see GradleIncorrectDependencyNotationArgumentInspection
   */
  fun getIncorrectDependencyNotationArgumentInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) : PsiElementVisitor

  /**
   * @see GradleDeprecatedConfigurationInspection
   */
  fun getDeprecatedConfigurationInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) : PsiElementVisitor

  /**
   * @see GradlePluginDslStructureInspection
   */
  fun getPluginDslStructureInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) : PsiElementVisitor

}