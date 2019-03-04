// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference

fun processConstructor(reference: GroovyCallReference,
                       argumentList: GrArgumentList?,
                       highlightElement: PsiElement,
                       sink: HighlightSink): Boolean {
  val userArguments = reference.arguments ?: run {
    sink.highlightUnknownArgs(highlightElement)
    return true
  }

  val results = reference.resolve(false)
  var hasUnknownResults = false
  for (result in results) {
    if (result !is GroovyMethodResult) continue
    val candidate = result.candidate ?: continue
    val mapping = candidate.argumentMapping
    if (mapping == null) {
      sink.highlightInapplicableMethod(result, userArguments, argumentList, highlightElement)
      return true
    }
    val applicability = mapping.applicability(result.substitutor, false)
    when (applicability) {
      Applicability.inapplicable -> {
        sink.highlightInapplicableMethod(result, mapping.arguments, argumentList, highlightElement)
        return true
      }
      Applicability.canBeApplicable -> hasUnknownResults = true
      else -> Unit
    }
  }

  if (results.size > 1) {
    if (hasUnknownResults) {
      sink.highlightUnknownArgs(highlightElement)
      return true
    }
    else {
      sink.highlightAmbiguousMethod(highlightElement)
      return true
    }
  }
  return false
}
