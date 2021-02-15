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
import com.intellij.psi.codeStyle.VariableKind.PARAMETER
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import kotlin.math.max
import kotlin.math.min

internal abstract class CreateExecutableFromGroovyUsageRequest<out T : GrCall>(
  call: T,
  private val modifiers: Collection<JvmModifier>
) : CreateExecutableRequest {

  private val psiManager = call.manager
  private val project = psiManager.project
  private val callPointer: SmartPsiElementPointer<T> = call.createSmartPointer(project)
  protected val call: T get() = callPointer.element ?: error("dead pointer")

  abstract fun getArguments(): List<Argument>?

  override fun isValid() = callPointer.element != null

  override fun getAnnotations() = emptyList<AnnotationRequest>()

  override fun getModifiers() = modifiers

  override fun getTargetSubstitutor() = PsiJvmSubstitutor(project, getTargetSubstitutor(call))

  override fun getExpectedParameters(): List<ExpectedParameter> {
    val argumentTypes = getArgumentTypes() ?: return emptyList()

    val codeStyleManager: JavaCodeStyleManager = project.service()

    val names = argumentTypes.map { (type, _) -> type to codeStyleManager.suggestNames(emptyList(), PARAMETER, type).names.toList() }

    val nameSupplier = ParametersNameSupplier(names)

    return names.map { (type, names) ->
      //TODO add semantic names based on expression
      expectedParameter(expectedTypes(type, ExpectedType.Kind.SUPERTYPE), names.map { nameSupplier.supplyName(it) })
    }
  }

  fun getArgumentTypes(): List<Pair<PsiType, GrExpression?>>? {
    return getArguments()?.map {
      if (it is ExpressionArgument) it.type to it.expression
      else it.type to null
    }?.map {(type, expression) ->
      val expectedType = anonymousClassesToBase(type) ?: TypesUtil.getJavaLangObject(call)
      expectedType to expression
    }
  }

  private fun anonymousClassesToBase(type: PsiType?): PsiType? {
    if (type !is PsiClassType) return type
    val resolved = type.resolve()
    return if (resolved is GrAnonymousClassDefinition) resolved.baseClassType else type
  }

  private class ParametersNameSupplier(suggested: List<Pair<PsiType, List<String>>>) {

    private val namesCount = suggested
      .flatMap { it.second }
      .groupingBy { it }
      .eachCount()
      .mapValues { min(it.value - 1, 1) }
      .toMutableMap()

    private val usedNames = mutableSetOf<String>()

    fun supplyName(suggestion: String): String {
      val usageCount = namesCount[suggestion] ?: return suggestion
      if (usageCount == 0 && suggestion !in usedNames) {
        usedNames.add(suggestion)
        return suggestion
      }

      val index = nextIndex(suggestion, usageCount)
      val name = "$suggestion$index"
      namesCount[suggestion] = index + 1
      usedNames.add(name)
      return name
    }

    private fun nextIndex(suggestion: String, start: Int): Int {
      var index = max(start, 1)
      while ("$suggestion$index" in usedNames) {
        index++
      }
      return index
    }
  }
}
