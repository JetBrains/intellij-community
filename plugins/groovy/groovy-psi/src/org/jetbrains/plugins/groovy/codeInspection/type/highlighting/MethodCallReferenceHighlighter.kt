// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests.generateCreateMethodActions
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.isClosureCall
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.*
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType
import org.jetbrains.plugins.groovy.lang.typing.getReadPropertyType

class MethodCallReferenceHighlighter(
  private val methodReference: GroovyMethodCallReference,
  override val highlightElement: PsiElement,
  sink: HighlightSink
) : CallReferenceHighlighter(methodReference, sink) {

  override fun highlightClosureInvocation(results: Collection<GroovyResolveResult>, arguments: Arguments): Boolean {
    require(results.isNotEmpty())
    val result = results.singleOrNull() ?: return false
    if (!result.isInvokedOnProperty) {
      return false
    }
    val propertyType = getReadPropertyType(result)
    if (propertyType !is GroovyClosureType && !InheritanceUtil.isInheritor(propertyType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      highlightCannotApplyError(methodReference.methodName, argumentsString(arguments))
      return true
    }
    if (propertyType is GroovyClosureType) {
      return highlightClosureCall(propertyType, arguments)
    }
    // todo: @ClosureParams also induces restrictions on arguments
    return false
  }

  /**
   * [groovy.lang.Closure.call] has vararg [Object] parameters, so it is applicable to anything.
   * In this case we want to highlight closure call against known signatures.
   */
  override fun highlightApplicable(result: GroovyMethodResult): Boolean {
    val candidate: GroovyMethodCandidate = result.candidate ?: return false
    if (!candidate.method.isClosureCall()) {
      return false
    }
    val receiver = candidate.receiverType as? GroovyClosureType ?: return false
    val arguments = candidate.argumentMapping?.arguments ?: return false
    return highlightClosureCall(receiver, arguments)
  }

  private fun highlightClosureCall(receiver: GroovyClosureType, arguments: Arguments): Boolean {
    val mappings = receiver.applyTo(arguments)
    val totalApplicability = if (mappings.isEmpty()) {
      inapplicable
    }
    else {
      totalApplicability(mappings.map {
        it.applicability()
      })
    }

    when (totalApplicability) {
      inapplicable -> {
        highlightCannotApplyError("call", argumentsString(arguments))
        return true
      }
      canBeApplicable -> {
        if (mappings.size > 1) {
          highlightUnknownArgs()
          return true
        }
      }
      applicable -> {
        if (mappings.size > 1) {
          highlightAmbiguousMethod()
          return true
        }
      }
    }
    return false
  }

  override fun buildFixes(): List<LocalQuickFix> {
    // TODO get rid of this hack by building fixes by reference. It will also result into fixes available on operator calls
    val call: GrMethodCall = methodReference.element as? GrMethodCall ?: return emptyList()
    val actions: List<IntentionAction> = generateCreateMethodActions(call)
    return IntentionWrapper.wrapToQuickFixes(actions, call.containingFile)
  }
}
