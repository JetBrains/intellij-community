// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.*
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder

abstract class CallReferenceHighlighter(protected val reference: GroovyCallReference, protected val sink: HighlightSink) {

  protected open val ambiguousMethodMessage: String get() = GroovyBundle.message("method.call.is.ambiguous")

  protected open fun getInapplicableMethodMessage(result: GroovyMethodResult, containingType: PsiType, arguments: Arguments): String {
    val typeText = containingType.internalCanonicalText
    val argumentsString = argumentsString(arguments)
    return GroovyBundle.message("cannot.apply.method1", result.element.name, typeText, argumentsString)
  }

  protected abstract val highlightElement: PsiElement

  private fun highlightCannotApplyError(invokedText: String, typesString: String) {
    sink.registerError(highlightElement, GroovyBundle.message("cannot.apply.method.or.closure", invokedText, typesString))
  }

  private fun highlightUnknownArgs() {
    sink.registerProblem(highlightElement, ProblemHighlightType.WEAK_WARNING, GroovyBundle.message("cannot.infer.argument.types"))
  }

  private fun highlightAmbiguousMethod() {
    sink.registerError(highlightElement, ambiguousMethodMessage)
  }

  private fun highlightInapplicableMethod(results: Collection<GroovyMethodResult>, arguments: Arguments) {
    val result = results.firstOrNull() ?: return
    val method = result.element
    val containingClass = if (method is GrGdkMethod) method.staticMethod.containingClass else method.containingClass

    val methodName = method.name
    val highlightElement = highlightElement
    val argumentsString = argumentsString(arguments)
    if (containingClass == null) {
      highlightCannotApplyError(methodName, argumentsString)
      return
    }
    val factory = JavaPsiFacade.getElementFactory(method.project)
    val containingType = factory.createType(containingClass, result.substitutor)

    val fixes = generateFixes(results)
    val message = getInapplicableMethodMessage(result, containingType, arguments)
    sink.registerProblem(highlightElement, ProblemHighlightType.GENERIC_ERROR, message, *fixes)
  }

  protected open fun shouldHighlightInapplicable(): Boolean = true

  open fun highlightMethodApplicability(): Boolean {
    val userArguments = reference.arguments ?: run {
      highlightUnknownArgs()
      return true
    }

    val results: Collection<GroovyResolveResult> = reference.resolve(false)
    if (results.isEmpty()) {
      // will be highlighted by GrUnresolvedAccessInspection
      return false
    }
    val resultApplicabilities: List<Pair<GroovyMethodResult, Applicability>> = results
      .filterIsInstance(GroovyMethodResult::class.java)
      .map {
        Pair(it, it.candidate?.argumentMapping?.applicability(it.substitutor, false) ?: inapplicable)
      }

    val totalApplicability = resultApplicabilities.fold(applicable) { status, (_, applicability) ->
      when {
        status == inapplicable -> inapplicable
        applicability == inapplicable -> inapplicable
        else -> applicability
      }
    }

    when (totalApplicability) {
      inapplicable -> {
        if (!shouldHighlightInapplicable()) {
          return false
        }
        val inapplicableResults: Collection<GroovyMethodResult> = resultApplicabilities.mapNotNull { (result, applicability) ->
          if (applicability == inapplicable) result else null
        }
        highlightInapplicableMethod(inapplicableResults, userArguments)
        return true
      }
      canBeApplicable -> {
        if (resultApplicabilities.size > 1) {
          highlightUnknownArgs()
          return true
        }
      }
      applicable -> {
        if (resultApplicabilities.size > 1) {
          highlightAmbiguousMethod()
          return true
        }
      }
    }
    return false
  }

  protected open fun generateFixes(results: Collection<GroovyMethodResult>): Array<LocalQuickFix> {
    return results.flatMap(::generateFixes).toTypedArray()
  }

  private fun generateFixes(result: GroovyMethodResult): List<LocalQuickFix> {
    val candidate = result.candidate ?: return emptyList()
    val mapping = candidate.argumentMapping ?: return emptyList()
    val applicabilities = candidate.argumentMapping?.highlightingApplicabilities(result.substitutor) ?: return emptyList()
    val notApplicableArguments = applicabilities.filter { (_, data) -> data.applicability == inapplicable }.keys

    val substitutor = GroovyInferenceSessionBuilder(result.element, candidate, result.contextSubstitutor)
      .resolveMode(false)
      .ignoreArguments(notApplicableArguments)
      .build().inferSubst()

    return notApplicableArguments.filterIsInstance<ExpressionArgument>().mapNotNull { argument ->
      val fixType = substitutor.substitute(mapping.expectedType(argument))
      if (fixType != null) buildCastFix(argument, fixType)
      else null
    }
  }

  protected open fun buildCastFix(argument: ExpressionArgument, expectedType: PsiType): LocalQuickFix? {
    val arguments = reference.arguments ?: return null
    val position = arguments.indexOf(argument)
    if (position < 0) {
      return null
    }
    return ParameterCastFix(argument.expression, position, expectedType)
  }
}
