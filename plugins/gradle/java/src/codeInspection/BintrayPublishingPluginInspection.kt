// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl

class BintrayPublishingPluginInspection: GradleBaseInspection() {
  override fun buildVisitor(): BaseInspectionVisitor {
    return MyVisitor()
  }

  class MyVisitor: BaseInspectionVisitor() {

    override fun visitLiteralExpression(literal: GrLiteral) {
      val file: PsiFile = literal.containingFile
      if (!FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return
      super.visitLiteralExpression(literal)
      if (literal !is GrLiteralImpl || !literal.isStringLiteral) return
      if ("com.jfrog.bintray" == literal.value) {
        if (isPluginDSL(literal) || isApplyPlugin(literal)) {
          registerError(literal, GradleInspectionBundle.message("bintray.publishing.plugin"), emptyArray(), ProblemHighlightType.WARNING)
        }
      }
    }

    private fun isApplyPlugin(literal: GrLiteralImpl): Boolean {
      return (literal.parent?.parent?.parent as? GrCall)?.resolveMethod()?.let {
        "org.gradle.api.plugins.PluginAware" == it.containingClass?.qualifiedName
        && "apply" == it.name
      } ?: false
    }

    private fun isPluginDSL(literal: GrLiteralImpl): Boolean {
      return (literal.parent?.parent as? GrCall)?.resolveMethod()?.let {
        "org.gradle.plugin.use.PluginDependenciesSpec" == it.containingClass?.qualifiedName
        && "id" == it.name
      } ?: false
    }


  }
}