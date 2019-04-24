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
import org.jetbrains.plugins.groovy.lang.resolve.api.*

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

  fun highlightInapplicableMethod(result: GroovyMethodResult, arguments: Arguments) {
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

    val fixes = generateFixes(result)
    val message = getInapplicableMethodMessage(result, containingType, arguments)
    sink.registerProblem(highlightElement, ProblemHighlightType.GENERIC_ERROR, message, *fixes)
  }

  fun highlightMethod(): Boolean {
    val userArguments = reference.arguments ?: run {
      highlightUnknownArgs()
      return true
    }

    val results = reference.resolve(false)
    var hasUnknownResults = false
    for (result in results) {
      if (result !is GroovyMethodResult) continue
      val candidate = result.candidate ?: continue
      val mapping = candidate.argumentMapping
      if (mapping == null) {
        highlightInapplicableMethod(result, userArguments)
        return true
      }
      val applicability = mapping.applicability(result.substitutor, false)
      when (applicability) {
        Applicability.inapplicable -> {
          highlightInapplicableMethod(result, userArguments)
          return true
        }
        Applicability.canBeApplicable -> hasUnknownResults = true
        else -> Unit
      }
    }

    if (results.size > 1) {
      if (hasUnknownResults) {
        highlightUnknownArgs()
        return true
      }
      else {
        highlightAmbiguousMethod()
        return true
      }
    }
    return false
  }

  fun generateFixes(result: GroovyMethodResult): Array<LocalQuickFix> {
    val applicabilities = result.candidate?.argumentMapping?.highlightApplicabilities(result.substitutor)
                          ?: return emptyArray()
    return applicabilities.entries
      .mapNotNull { (argument, applicabilities) -> buildFix(argument, applicabilities) }
      .toTypedArray()
  }
}
