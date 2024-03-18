// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleTaskToRegisterFix
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleWithTypeFix
import org.jetbrains.plugins.gradle.codeInspection.fix.isReturnTypeValueUsed
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.groovy.intentions.GrReplaceMethodCallQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty

class GroovyConfigurationAvoidanceVisitor(val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitMethodCall(call: GrMethodCall) {
    if (!lazyApiAvailable(call)) return
    val method = call.resolveMethod() ?: return
    val callExpression = call.invokedExpression
    val elementToHighlight = if (callExpression is GrReferenceExpression) callExpression.referenceNameElement ?: callExpression else callExpression
    processMethod(method, elementToHighlight, holder, call.isReturnTypeValueUsed())
  }

  override fun visitIndexProperty(expression: GrIndexProperty) {
    if (!lazyApiAvailable(expression)) return
    val method = expression.rValueReference?.resolve().asSafely<PsiMethod>() ?: return
    val elementToHighlight = expression.argumentList
    processMethod(method, elementToHighlight, holder, expression.isReturnTypeValueUsed())
  }

  private fun processMethod(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder, isReturnTypeValueUsed: Boolean) {
    val containingClass = method.containingClass ?: return
    if (isInheritor(containingClass, GRADLE_API_TASK_CONTAINER)) processTaskContainer(method, elementToHighlight, holder, isReturnTypeValueUsed)
    if (isInheritor(containingClass, GRADLE_API_DOMAIN_OBJECT_COLLECTION)) processDomainObjectCollection(method, elementToHighlight,
                                                                                                         holder)
    if (isInheritor(containingClass, GRADLE_API_PROJECT)) processProject(method, elementToHighlight, holder, isReturnTypeValueUsed)
    if (isInheritor(containingClass, GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION)) processNamedDomainObjectCollection(method, elementToHighlight, holder)
    if (isInheritor(containingClass, GRADLE_API_TASK_COLLECTION)) processTaskCollection(method, elementToHighlight, holder)

  }
}


private fun lazyApiAvailable(call: PsiElement): Boolean {
  val linkedProjectPath = call.getLinkedGradleProjectPath() ?: return false
  val gradleVersion = GradleSettings.getInstance(call.project).getLinkedProjectSettings(linkedProjectPath)?.resolveGradleVersion()
                      ?: return false
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "4.9")
}

private fun processNamedDomainObjectCollection(method: PsiMethod,
                                               elementToHighlight: PsiElement,
                                               holder: ProblemsHolder) {
  if (method.name == "findByName") {
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "named"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }
}

private fun processProject(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder, isReturnTypeValueUsed: Boolean) {
  if (method.name == "task") {
    val fixes = if (!isReturnTypeValueUsed) arrayOf(GradleTaskToRegisterFix()) else emptyArray()
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "tasks.register"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes)
  }
}

private fun processDomainObjectCollection(method: PsiMethod,
                                          elementToHighlight: PsiElement,
                                          holder: ProblemsHolder) {
  if (method.name == "all" || method.name == "whenObjectAdded" || method.name == "each") {
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "configureEach"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, GrReplaceMethodCallQuickFix(method.name, "configureEach"))
  } else if (method.name == "withType" && method.parameters.size > 1) {
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "withType(...).configureEach"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, GradleWithTypeFix())
  } else if (method.name == "getByName" || method.name == "getAt") {
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "named"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }
}

private fun processTaskContainer(method: PsiMethod,
                                 elementToHighlight: PsiElement,
                                 holder: ProblemsHolder,
                                 isReturnTypeValueUsed: Boolean) {
  if (method.name == "create") {
    val fixes = if (!isReturnTypeValueUsed) arrayOf(GrReplaceMethodCallQuickFix(method.name, "register")) else emptyArray()
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "register"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, *fixes)
  }
  if (method.name == "getByPath" || method.name == "findByPath") {
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.0.requires.ordering", method.name), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
  }
}

private fun processTaskCollection(method: PsiMethod,
                                  elementToHighlight: PsiElement,
                                  holder: ProblemsHolder) {
  if (method.name == "whenTaskAdded") {
    holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "configureEach"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, GrReplaceMethodCallQuickFix(method.name, "configureEach"))
  }
}