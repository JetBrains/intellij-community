// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.getTargetSubstitutor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.components.service
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

internal abstract class CreateExecutableFromGroovyUsageRequest<out T : GrCallExpression>(
  call: T,
  private val modifiers: Collection<JvmModifier>
) : CreateExecutableRequest {

  private val psiManager = call.manager
  private val project = psiManager.project
  private val callPointer: SmartPsiElementPointer<T> = call.createSmartPointer(project)
  protected val call: T get() = callPointer.element ?: error("dead pointer")

  override fun isValid() = callPointer.element != null

  override fun getAnnotations() = emptyList<AnnotationRequest>()

  override fun getModifiers() = modifiers

  override fun getTargetSubstitutor() = PsiJvmSubstitutor(project, getTargetSubstitutor(call))

  override fun getExpectedParameters(): List<ExpectedParameter> {
    val argumentTypes = getArgumentTypes() ?: return emptyList()

    val codeStyleManager: JavaCodeStyleManager = project.service()
    return argumentTypes.map {(type, _) ->
      //if (expression != null) codeStyleManager.suggestSemanticNames(expression) //TODO add semantic names based on expression
      val names = codeStyleManager.suggestNames(emptyList(), VariableKind.PARAMETER, type).names
      expectedParameter(expectedTypes(type, ExpectedType.Kind.SUPERTYPE), names.toList())
    }
  }

  fun getArgumentTypes(): List<Pair<PsiType, GrExpression?>>? {
    val result = mutableListOf<Pair<PsiType, GrExpression?>>()
    val namedArguments = call.namedArguments
    if (namedArguments.isNotEmpty()) {
      result.add(GrMapType.createFromNamedArgs(call, namedArguments) to null)
    }

    val expressionArguments = call.expressionArguments
    for (expression in expressionArguments) {
      val type = anonymousClassesToBase(expression.type)
      if (expression is GrSpreadArgument) {
        if (type is GrTupleType) {
          type.componentTypes.forEach { result.add(it to null) }
        }
        else {
          return null
        }
      }
      else {
        val expectedType = type ?: TypesUtil.getJavaLangObject(expression)
        result.add(expectedType to expression)
      }
    }

    val closureArguments = call.closureArguments
    for (closure in closureArguments) {
      val expectedType = closure.type ?: TypesUtil.getJavaLangObject(closure)
      result.add(expectedType to null)
    }

    return result
  }

  private fun anonymousClassesToBase(type: PsiType?): PsiType? {
    if (type !is PsiClassType) return type
    val resolved = type.resolve()
    return if (resolved is GrAnonymousClassDefinition) resolved.baseClassType else type
  }

  override fun getParameters() = getParameters(expectedParameters, project)
}
