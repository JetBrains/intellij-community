// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

class BintrayPublishingPluginInspection: GradleBaseInspection() {

    override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor = object : GroovyElementVisitor() {

    override fun visitLiteralExpression(literal: GrLiteral) {
      val file: PsiFile = literal.containingFile
      if (!FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return
      super.visitLiteralExpression(literal)
      if (!literal.isString) return
      if ("com.jfrog.bintray" == literal.value) {
        if (isPluginDSL(literal) || isApplyPlugin(literal)) {
          holder.registerProblem(literal, GradleInspectionBundle.message("bintray.publishing.plugin"), ProblemHighlightType.WARNING)
        }
      }
    }

    private fun isApplyPlugin(literal: GrLiteral): Boolean {
      return (literal.parent?.parent?.parent as? GrCall)?.resolveMethod()?.let {
        "org.gradle.api.plugins.PluginAware" == it.containingClass?.qualifiedName
        && "apply" == it.name
      } ?: false
    }

    private fun isPluginDSL(literal: GrLiteral): Boolean {
      return (literal.parent?.parent as? GrCall)?.resolveMethod()?.let {
        "org.gradle.plugin.use.PluginDependenciesSpec" == it.containingClass?.qualifiedName
        && "id" == it.name
      } ?: false
    }
  }
}