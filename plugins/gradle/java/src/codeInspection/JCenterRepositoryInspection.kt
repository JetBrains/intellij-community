// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo

class JCenterRepositoryInspection : GradleBaseInspection() {
  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor = object : GroovyElementVisitor() {

    override fun visitLiteralExpression(literal: GrLiteral) {
      val file: PsiFile = literal.containingFile
      if (!FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return
      super.visitLiteralExpression(literal)
      if (!literal.isString) return
      val value = literal.value ?: return
      if ("https://jcenter.bintray.com" == value || value.toString().startsWith("https://dl.bintray.com/")) {
        val closure = literal.parentOfType<GrClosableBlock>() ?: return
        val typeToDelegate = getDelegatesToInfo(closure)?.typeToDelegate ?: return
        if (typeToDelegate.canonicalText == GradleCommonClassNames.GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY) {
          holder.registerProblem(literal, GradleInspectionBundle.message("jcenter.repository"), ProblemHighlightType.WARNING)
        }
      }
    }

    override fun visitMethodCall(call: GrMethodCall) {
      val file: PsiFile = call.containingFile
      if (!FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return
      super.visitMethodCall(call)
      if ("jcenter" != call.callReference?.methodName) return
      val closure = call.parentOfType<GrClosableBlock>() ?: return
      val typeToDelegate = getDelegatesToInfo(closure)?.typeToDelegate ?: return
      if (typeToDelegate.canonicalText == GradleCommonClassNames.GRADLE_API_REPOSITORY_HANDLER) {
        holder.registerProblem(call, GradleInspectionBundle.message("jcenter.repository"), ProblemHighlightType.WARNING)
      }
    }
  }
}