// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleWithTypeFix
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DOMAIN_OBJECT_COLLECTION
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK_CONTAINER
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.groovy.intentions.GrReplaceMethodCallQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GradleEagernessInspection : GradleBaseInspection() {

  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor {
    return object: GroovyElementVisitor() {
      override fun visitMethodCall(call: GrMethodCall) {
        val linkedProjectPath = call.getLinkedGradleProjectPath() ?: return
        val gradleVersion = GradleSettings.getInstance(call.project).getLinkedProjectSettings(linkedProjectPath)?.resolveGradleVersion() ?: return
        if (gradleVersion < GradleVersion.version("4.9")) {
          return
        }
        val method = call.resolveMethod() ?: return
        val containingClass = method.containingClass ?: return
        val callExpression = call.invokedExpression
        val elementToHighlight = if (callExpression is GrReferenceExpression) callExpression.referenceNameElement ?: callExpression else callExpression
        if (isInheritor(containingClass, GRADLE_API_TASK_CONTAINER)) processTaskContainer(method, elementToHighlight, holder)
        if (isInheritor(containingClass, GRADLE_API_DOMAIN_OBJECT_COLLECTION)) processDomainObjectCollection(method, elementToHighlight, holder)
      }
    }
  }

  private fun processDomainObjectCollection(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder) {
    if (method.name == "all" || method.name == "whenObjectAdded") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "configureEach"), GrReplaceMethodCallQuickFix(method.name, "configureEach"))
    }
    if (method.name == "withType" && method.parameters.size > 1) {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "withType(...).configureEach"), GradleWithTypeFix())
    }
  }

  private fun processTaskContainer(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder) {
    if (method.name == "create") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "register"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
    if (method.name == "getByPath" || method.name == "findByPath") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.0.requires.ordering", method.name), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
  }
}