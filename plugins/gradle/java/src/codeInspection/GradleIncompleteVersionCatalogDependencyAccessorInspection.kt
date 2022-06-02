// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiFile
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.gradle.config.GradleFileType
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GradleIncompleteVersionCatalogDependencyAccessorInspection : GroovyLocalInspectionTool() {

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    return if (file.fileType == GradleFileType) super.checkFile(file, manager, isOnTheFly) else null
  }

  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor = object : GroovyElementVisitor() {
    override fun visitExpression(expression: GrExpression) {
      val parent = expression.parent
      if (parent is GrReferenceExpression && parent.qualifierExpression == expression) {
        return
      }
      if (parent is GrMethodCall && parent.invokedExpression == expression) {
        return
      }
      if (expression !is GrMethodCall && expression !is GrReferenceExpression) {
        return
      }
      val resolvedClass = expression.type.resolve()
      val containingClasses = listOfNotNull(resolvedClass?.containingClass, resolvedClass?.containingClass?.containingClass)
      for (containingClass in containingClasses) {
        if (InheritanceUtil.isInheritor(containingClass, "org.gradle.api.internal.catalog.AbstractExternalDependencyFactory")) {
          holder.registerProblem(expression, GradleInspectionBundle.message("inspection.display.name.incomplete.version.catalog.dependency.accessor"), ProblemHighlightType.WEAK_WARNING)
          return
        }
      }
    }
  }
}