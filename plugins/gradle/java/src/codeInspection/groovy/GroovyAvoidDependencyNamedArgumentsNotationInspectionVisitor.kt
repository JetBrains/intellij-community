// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.groovy

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.OriginInfoAwareElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.GradleDependencyHandlerContributor
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class GroovyAvoidDependencyNamedArgumentsNotationInspectionVisitor(val holder: ProblemsHolder) : GroovyElementVisitor() {
  override fun visitMethodCall(call: GrMethodCall) {
    val method = call.resolveMethod() ?: return
    if (method !is OriginInfoAwareElement || method.originInfo != GradleDependencyHandlerContributor.DEPENDENCY_NOTATION) {
      return
    }
    val arguments = call.argumentList.expressionArguments
    val namedArguments = call.namedArguments
    if (arguments.isNotEmpty()) return
    // check that there are only group, name and version named arguments
    if (namedArguments.size != 3) return
    if (namedArguments.map { it.labelName }.intersect(listOf("group", "name", "version")).size != 3) return
    // check that all named arguments are string literals
    for (argument in namedArguments.map { it.expression }) {
      if (argument !is GrLiteral) return
      val type = argument.type ?: return
      if (
        !InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_CHAR_SEQUENCE) &&
        !type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING)
      ) return
    }

    val fix = object : LocalQuickFixOnPsiElement(call) {
      override fun getText(): @IntentionName String {
        return "Convert to GAV string notation"
      }

      override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val call = startElement as GrMethodCall
        val group = call.namedArguments.find { it.labelName == "group" }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"")
                    ?: return
        val name = call.namedArguments.find { it.labelName == "name" }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"")
                   ?: return
        val version = call.namedArguments.find { it.labelName == "version" }?.expression?.text?.removeSurrounding("'")?.removeSurrounding("\"")
                      ?: return
        call.namedArguments.forEach { it.delete() }
        call.argumentList.add(GroovyPsiElementFactory.getInstance(project).createLiteralFromValue("$group:$name:$version"))
      }

      override fun getFamilyName(): @IntentionFamilyName String {
        return text
      }

    }

    holder.registerProblem(
      call.argumentList,
      GradleInspectionBundle.message("inspection.message.avoid.dependency.named.arguments.notation.descriptor"),
      ProblemHighlightType.WEAK_WARNING,
      fix
    )
  }
}