// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.codeInspection.GradleDslInspectionProvider
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor

class GroovyGradleDslInspectionProvider : GradleDslInspectionProvider {

  override fun getConfigurationAvoidanceInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) : PsiElementVisitor
    = GroovyPsiElementVisitor(GroovyConfigurationAvoidanceVisitor(holder))

  override fun getForeignDelegateInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
    = GroovyPsiElementVisitor(GroovyForeignDelegateInspectionVisitor(holder))

  override fun getIncorrectDependencyNotationArgumentInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
    = GroovyPsiElementVisitor(GroovyIncorrectDependencyNotationArgumentInspectionVisitor(holder))

  override fun getDeprecatedConfigurationInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
    = GroovyPsiElementVisitor(GroovyDeprecatedConfigurationInspectionVisitor(holder))

  override fun getPluginDslStructureInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
    = GroovyPsiElementVisitor(GroovyPluginDslStructureInspectionVisitor(holder))

  override fun isAvoidDependencyNamedArgumentsNotationInspectionAvailable(file: PsiFile): Boolean = FileUtilRt.extensionEquals(file.name, "gradle")
  override fun getAvoidDependencyNamedArgumentsNotationInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
    = GroovyPsiElementVisitor(GroovyAvoidDependencyNamedArgumentsNotationInspectionVisitor(holder))
}