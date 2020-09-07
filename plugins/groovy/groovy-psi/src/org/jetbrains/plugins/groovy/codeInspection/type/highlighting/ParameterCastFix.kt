// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix
import org.jetbrains.plugins.groovy.codeInspection.assignment.GrCastFix
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class ParameterCastFix(
  expression: GrExpression,
  position: Int,
  private val myType: PsiType
) : GroovyFix() {

  @Nls(capitalization = Sentence)
  private val myName: String = GroovyBundle.message("parameter.cast.fix", position + 1, myType.presentableText)
  override fun getName(): String = myName
  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.add.parameter.cast")

  private val myExpression: Pointer<GrExpression> = expression.createSmartPointer()

  override fun doFix(project: Project, descriptor: ProblemDescriptor) {
    val expression = myExpression.dereference() ?: return
    GrCastFix.doSafeCast(project, myType, expression)
  }
}
