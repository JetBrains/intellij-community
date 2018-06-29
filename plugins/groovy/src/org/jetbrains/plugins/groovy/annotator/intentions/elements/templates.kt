// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiType
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression
import org.jetbrains.plugins.groovy.template.expressions.ParameterNameExpression
import org.jetbrains.plugins.groovy.template.expressions.SuggestedParameterNameExpression


internal fun setupParameters(method: GrMethod, parameters: ExpectedParameters): List<ChooseTypeExpression> {
  val project = method.project
  val factory = GroovyPsiElementFactory.getInstance(project)
  if (parameters.isEmpty()) return emptyList()
  val postprocessReformattingAspect = PostprocessReformattingAspect.getInstance(project)
  val parameterList = method.parameterList

  //255 is the maximum number of method parameters
  var paramTypesExpressions = listOf<ChooseTypeExpression>()
  for (i in 0 until minOf(parameters.size, 255)) {
    val parameterInfo = parameters[i]
    val dummyParameter = factory.createParameter("p$i", PsiType.INT)
    postprocessReformattingAspect.postponeFormattingInside(Computable {
      parameterList.add(dummyParameter)
    }) as GrParameter

    paramTypesExpressions += setupTypeElement(method, createConstraints(project, parameterInfo.expectedTypes))
  }

  return paramTypesExpressions
}

internal fun setupTypeElement(method: GrMethod, constraints: List<TypeConstraint>): ChooseTypeExpression {
  return ChooseTypeExpression(constraints.toTypedArray(), method.manager, method.resolveScope, false)
}

internal fun setupNameExpressions(parameters: ExpectedParameters, project: Project): List<ParameterNameExpression> {
  val codeStyleManager = JavaCodeStyleManager.getInstance(project)
  return parameters.map {
    val type = it.expectedTypes.firstOrNull()?.theType as? PsiType
    val nameInfo = codeStyleManager.suggestNames(it.semanticNames, VariableKind.PARAMETER, type)
    SuggestedParameterNameExpression(nameInfo)
  }
}