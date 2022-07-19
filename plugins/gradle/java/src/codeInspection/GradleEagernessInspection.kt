// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.util.castSafelyTo
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleWithTypeFix
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.groovy.intentions.GrReplaceMethodCallQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty

class GradleEagernessInspection : GradleBaseInspection() {

  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor {
    return object: GroovyElementVisitor() {
      override fun visitMethodCall(call: GrMethodCall) {
        if (!lazyApiAvailable(call)) return
        val method = call.resolveMethod() ?: return
        val callExpression = call.invokedExpression
        val elementToHighlight = if (callExpression is GrReferenceExpression) callExpression.referenceNameElement ?: callExpression else callExpression
        processMethod(method, elementToHighlight, holder)
      }

      override fun visitIndexProperty(expression: GrIndexProperty) {
        if (!lazyApiAvailable(expression)) return
        val method = expression.rValueReference?.resolve().castSafelyTo<PsiMethod>() ?: return
        val elementToHighlight = expression.argumentList
        processMethod(method, elementToHighlight, holder)
      }

      fun processMethod(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder) {
        val containingClass = method.containingClass ?: return
        if (isInheritor(containingClass, GRADLE_API_TASK_CONTAINER)) processTaskContainer(method, elementToHighlight, holder)
        if (isInheritor(containingClass, GRADLE_API_DOMAIN_OBJECT_COLLECTION)) processDomainObjectCollection(method, elementToHighlight, holder)
        if (isInheritor(containingClass, GRADLE_API_PROJECT)) processProject(method, elementToHighlight, holder)
        if (isInheritor(containingClass, GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION)) processNamedDomainObjectCollection(method, elementToHighlight, holder)
        if (isInheritor(containingClass, GRADLE_API_TASK_COLLECTION)) processTaskCollection(method, elementToHighlight, holder)
      }
    }
  }

  private fun lazyApiAvailable(call: PsiElement): Boolean {
    val linkedProjectPath = call.getLinkedGradleProjectPath() ?: return false
    val gradleVersion = GradleSettings.getInstance(call.project).getLinkedProjectSettings(linkedProjectPath)?.resolveGradleVersion()
                        ?: return false
    if (gradleVersion < GradleVersion.version("4.9")) {
      return false
    }
    return true
  }

  private fun processNamedDomainObjectCollection(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder) {
    if (method.name == "findByName") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "named"))
    }
  }

  private fun processProject(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder) {
    if (method.name == "task") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "tasks.register"))
    }
  }

  private fun processDomainObjectCollection(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder) {
    if (method.name == "all" || method.name == "whenObjectAdded" || method.name == "each") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "configureEach"), GrReplaceMethodCallQuickFix(method.name, "configureEach"))
    } else if (method.name == "withType" && method.parameters.size > 1) {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "withType(...).configureEach"), GradleWithTypeFix())
    } else if (method.name == "getByName" || method.name == "getAt") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "named"))
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

  private fun processTaskCollection(method: PsiMethod, elementToHighlight: PsiElement, holder: ProblemsHolder) {
    if (method.name == "whenTaskAdded") {
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "configureEach"), GrReplaceMethodCallQuickFix(method.name, "configureEach"))
    }
  }
}