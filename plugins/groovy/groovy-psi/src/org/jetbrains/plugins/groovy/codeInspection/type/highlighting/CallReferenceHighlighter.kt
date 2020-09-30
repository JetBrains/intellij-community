// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider.checkProviders
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.*
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder

abstract class CallReferenceHighlighter(protected val reference: GroovyCallReference, protected val sink: HighlightSink) {

  protected open val ambiguousMethodMessage: String get() = GroovyBundle.message("method.call.is.ambiguous")

  @Nls
  protected open fun getInapplicableMethodMessage(result: GroovyMethodResult, containingType: PsiType, arguments: Arguments): String {
    val typeText = containingType.internalCanonicalText
    val argumentsString = argumentsString(arguments)
    return GroovyBundle.message("cannot.apply.method1", result.element.name, typeText, argumentsString)
  }

  protected abstract val highlightElement: PsiElement

  protected fun highlightCannotApplyError(invokedText: String, typesString: String) {
    sink.registerError(highlightElement, GroovyBundle.message("cannot.apply.method.or.closure", invokedText, typesString))
  }

  protected fun highlightUnknownArgs() {
    // TODO since arguments are available not as types but original expression could be obtained
    //  it's possible to highlight particular arguments which have unknown types
    //  instead of highlighting the whole argument list
    sink.registerProblem(highlightElement, ProblemHighlightType.WEAK_WARNING, GroovyBundle.message("cannot.infer.argument.types"))
  }

  protected fun highlightAmbiguousMethod() {
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

    val fixes = (buildCastFixes(results) + buildFixes()).toArray(LocalQuickFix.EMPTY_ARRAY)
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

    val methodResults: List<GroovyMethodResult> = results
      .filterIsInstance<GroovyMethodResult>()
      .filter {
        !it.isInvokedOnProperty
      }
    if (methodResults.isEmpty()) {
      return highlightClosureInvocation(results, userArguments)
    }

    val resultApplicabilities: List<Pair<GroovyMethodResult, ApplicabilityResult>> = methodResults.mapNotNull { result ->
      result.candidate?.argumentMapping?.let { mapping ->
        Pair(result, mapping.highlightingApplicabilities(result.substitutor))
      }
    }

    val totalApplicability = totalApplicability(resultApplicabilities.map {
      it.second.applicability
    })

    when (totalApplicability) {
      inapplicable -> {
        if (!shouldHighlightInapplicable()) {
          return false
        }
        val singleResult = methodResults.singleOrNull()
        if (singleResult != null && checkProviders(userArguments.map(::ErasedArgument), singleResult.element) == applicable) {
          return false
        }
        val inapplicableResults: Collection<GroovyMethodResult> = resultApplicabilities.mapNotNull { (result, applicabilityResult) ->
          if (applicabilityResult.applicability == inapplicable) result else null
        }
        highlightInapplicableMethod(inapplicableResults, userArguments)
        return true
      }
      canBeApplicable -> {
        if (methodResults.size == 1 || userArguments.any { it.type == null }) {
          // TODO Reconsider this check. Most probably we should always highlight unknown args and then return if there are any.
          highlightUnknownArgs()
          return true
        }
      }
      applicable -> {
        if (resultApplicabilities.size > 1) {
          highlightAmbiguousMethod()
          return true
        }
        else {
          return highlightApplicable(resultApplicabilities.single().first)
        }
      }
    }
    return false
  }

  protected open fun highlightClosureInvocation(results: Collection<GroovyResolveResult>, arguments: Arguments): Boolean = false

  protected open fun highlightApplicable(result: GroovyMethodResult): Boolean = false

  private fun buildCastFixes(results: Collection<GroovyMethodResult>): List<LocalQuickFix> {
    return results.flatMap(::buildCastFixes)
  }

  private fun buildCastFixes(result: GroovyMethodResult): List<LocalQuickFix> {
    val candidate = result.candidate ?: return emptyList()
    val mapping = candidate.argumentMapping ?: return emptyList()
    val applicabilities = candidate.argumentMapping?.highlightingApplicabilities(result.substitutor) ?: return emptyList()
    val notApplicableArguments = applicabilities.argumentApplicabilities.filter { (_, data) -> data.applicability == inapplicable }.keys

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

  protected open fun buildFixes(): List<LocalQuickFix> = emptyList()
}
