// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

fun setUpClosuresSignature(inferenceSession: GroovyInferenceSession,
                           closureParameter: ParametrizedClosure) {
  val refs = ReferencesSearch.search(closureParameter.parameter, closureParameter.parameter.useScope).findAll()
  for (call in refs.mapNotNull { it.element.parentOfType(GrCall::class) }) {
    for (index in call.expressionArguments.indices) {
      inferenceSession.addConstraint(
        ExpressionConstraint(inferenceSession.substituteWithInferenceVariables(closureParameter.typeParameters[index].type()),
                             call.expressionArguments[index]))
    }
  }
}


fun tryToExtractUnqualifiedName(className: String): String {
  val location = GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES.firstOrNull { className.startsWith(it) }
  return when {
    location != null -> className.substringAfter("$location.")
    (className in GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES) -> className.substringAfterLast(".");
    else -> className
  }

}
