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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.*
import org.jetbrains.plugins.groovy.lang.resolve.api.ApplicabilityData
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference

abstract class CallReferenceHighlighter(val reference: GroovyCallReference, val sink: HighlightSink) {

  abstract val unknownArgsMessage: String

  abstract val ambiguousMethodMessage: String

  abstract fun getInapplicableMethodMessage(result: GroovyMethodResult, containingType: PsiType, arguments: Arguments): String

  abstract fun getHighlightElement(): PsiElement

  abstract fun buildFix(argument: Argument, applicabilityData: ApplicabilityData): LocalQuickFix?

  fun highlightCannotApplyError(invokedText: String, typesString: String) {
    sink.registerError(getHighlightElement(), GroovyBundle.message("cannot.apply.method.or.closure", invokedText, typesString))
  }

  fun highlightUnknownArgs() {
    sink.registerProblem(getHighlightElement(), ProblemHighlightType.WEAK_WARNING, unknownArgsMessage)
  }

  fun highlightAmbiguousMethod() {
    sink.registerError(getHighlightElement(), ambiguousMethodMessage)
  }

  fun highlightInapplicableMethod(results: Set<GroovyMethodResult>, arguments: Arguments) {
    val result = results.firstOrNull() ?: return
    val method = result.element
    val containingClass = if (method is GrGdkMethod) method.staticMethod.containingClass else method.containingClass

    val methodName = method.name
    val highlightElement = getHighlightElement()
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

  fun highlightMethodApplicability(): Boolean {
    val userArguments = reference.arguments ?: run {
      highlightUnknownArgs()
      return true
    }

    val results = reference.resolve(false).filterIsInstance(GroovyMethodResult::class.java).associate {
      it to (it.candidate?.argumentMapping?.applicability(it.substitutor, false) ?: inapplicable)
    }

    val totalApplicability = results.entries.fold(applicable) { status, (_, applicability) ->
      when {
        status == inapplicable -> inapplicable
        applicability == inapplicable -> inapplicable
        else -> applicability
      }
    }

    when (totalApplicability) {
      inapplicable -> {
        highlightInapplicableMethod(results.filter { it.value == inapplicable }.keys, userArguments)
        return true
      }
      canBeApplicable -> {
        if (results.size > 1) {
          highlightUnknownArgs()
          return true
        }
      }
      applicable -> {
        if (results.size > 1) {
          highlightAmbiguousMethod()
          return true
        }
      }
    }
    return false
  }

  fun generateFixes(results: Set<GroovyMethodResult>): Array<LocalQuickFix> {
    return results.flatMap { result ->
      val applicabilities = result.candidate?.argumentMapping?.highlightingApplicabilities(result.substitutor) ?: return emptyArray()
      applicabilities.entries.mapNotNull { (argument, applicabilities) -> buildFix(argument, applicabilities) }
    }.toTypedArray()
  }
}
