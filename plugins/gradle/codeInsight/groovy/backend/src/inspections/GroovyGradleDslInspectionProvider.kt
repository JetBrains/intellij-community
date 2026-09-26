// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.groovy.backend.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.gradle.codeInsight.backend.inspections.GradleDslInspectionProvider
import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleRedundantKotlinStdLibInspection.Companion.KOTLIN_STDLIB_DEFAULT_DEPENDENCY_PROPERTY
import com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors.GroovyAvoidDependencyNamedArgumentsNotationInspectionVisitor
import com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors.GroovyConfigurationAvoidanceVisitor
import com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors.GroovyDeprecatedConfigurationInspectionVisitor
import com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors.GroovyForeignDelegateInspectionVisitor
import com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors.GroovyIncorrectDependencyNotationArgumentInspectionVisitor
import com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors.GroovyPluginDslStructureInspectionVisitor
import com.intellij.gradle.codeInsight.groovy.backend.inspections.visitors.GroovyRedundantKotlinStdLibInspectionVisitor
import com.intellij.gradle.properties.findGradleProperty
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor

internal class GroovyGradleDslInspectionProvider : GradleDslInspectionProvider {

  override fun getConfigurationAvoidanceInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    GroovyPsiElementVisitor(GroovyConfigurationAvoidanceVisitor(holder))

  override fun getForeignDelegateInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    GroovyPsiElementVisitor(GroovyForeignDelegateInspectionVisitor(holder))

  override fun getIncorrectDependencyNotationArgumentInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    GroovyPsiElementVisitor(GroovyIncorrectDependencyNotationArgumentInspectionVisitor(holder))

  override fun getDeprecatedConfigurationInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    GroovyPsiElementVisitor(GroovyDeprecatedConfigurationInspectionVisitor(holder))

  override fun getPluginDslStructureInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    GroovyPsiElementVisitor(GroovyPluginDslStructureInspectionVisitor(holder))

  override fun isAvoidDependencyNamedArgumentsNotationInspectionAvailable(file: PsiFile): Boolean =
    FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)

  override fun getAvoidDependencyNamedArgumentsNotationInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    GroovyPsiElementVisitor(GroovyAvoidDependencyNamedArgumentsNotationInspectionVisitor(holder))

  override fun isRedundantKotlinStdLibInspectionAvailable(file: PsiFile): Boolean {
    return FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)
           && findGradleProperty(file, KOTLIN_STDLIB_DEFAULT_DEPENDENCY_PROPERTY) != "false"
  }

  override fun getRedundantKotlinStdLibInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    GroovyPsiElementVisitor(GroovyRedundantKotlinStdLibInspectionVisitor(holder))
}