// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.toArray
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.createSignature
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

fun HighlightSink.highlightUnknownArgs(highlightElement: PsiElement) {
  registerProblem(highlightElement, ProblemHighlightType.WEAK_WARNING, message("cannot.infer.argument.types"))
}

fun HighlightSink.highlightCannotApplyError(invokedText: String, typesString: String, highlightElement: PsiElement) {
  registerError(highlightElement, message("cannot.apply.method.or.closure", invokedText, typesString))
}

fun HighlightSink.highlightAmbiguousMethod(highlightElement: PsiElement) {
  registerError(highlightElement, message("constructor.call.is.ambiguous"))
}

fun HighlightSink.highlightInapplicableMethod(result: GroovyMethodResult,
                                              arguments: List<Argument>,
                                              argumentList: GrArgumentList?,
                                              highlightElement: PsiElement) {
  val method = result.element
  val containingClass = if (method is GrGdkMethod) method.staticMethod.containingClass else method.containingClass

  val argumentString = argumentsString(arguments)
  val methodName = method.name
  if (containingClass == null) {
    highlightCannotApplyError(methodName, argumentString, highlightElement)
    return
  }

  val message: String
  if (method is DefaultConstructor) {
    message = message("cannot.apply.default.constructor", methodName)
  }
  else {
    val factory = JavaPsiFacade.getElementFactory(method.project)
    val containingType = factory.createType(containingClass, result.substitutor)
    val canonicalText = containingType.internalCanonicalText
    if (method.isConstructor) {
      message = message("cannot.apply.constructor", methodName, canonicalText, argumentString)
    }
    else {
      message = message("cannot.apply.method1", methodName, canonicalText, argumentString)
    }
  }
  val fixes = generateCastFixes(result, arguments, argumentList)
  registerProblem(highlightElement, ProblemHighlightType.GENERIC_ERROR, message, *fixes)
}

private fun argumentsString(arguments: List<Argument>): String {
  return arguments.joinToString(", ", "(", ")") {
    it.type?.internalCanonicalText ?: "?"
  }
}

private fun generateCastFixes(result: GroovyMethodResult, arguments: Arguments, argumentList: GrArgumentList?): Array<out LocalQuickFix> {
  val signature = createSignature(result.element, result.substitutor)
  return GroovyTypeCheckVisitorHelper.genCastFixes(signature, arguments.map(Argument::type).toArray(PsiType.EMPTY_ARRAY), argumentList)
}
