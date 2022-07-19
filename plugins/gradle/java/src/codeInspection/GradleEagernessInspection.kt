// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiMethod
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath
import org.jetbrains.plugins.gradle.settings.GradleSettings
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
        when (method.containingClass?.qualifiedName) {
          GradleCommonClassNames.GRADLE_API_TASK_CONTAINER -> processTaskContainer(method, call, holder)
        }
      }
    }
  }

  private fun processTaskContainer(method: PsiMethod, call: GrMethodCall, holder: ProblemsHolder) {
    if (method.name == "create") {
      val callExpression = call.invokedExpression
      val elementToHighlight = if (callExpression is GrReferenceExpression) callExpression.referenceNameElement ?: callExpression else callExpression
      holder.registerProblem(elementToHighlight, GradleInspectionBundle.message("inspection.message.consider.using.0.to.utilize.lazy.behavior", "register"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
  }


}