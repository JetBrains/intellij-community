// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference

abstract class ConstructorCallHighlighter(reference: GroovyCallReference, sink: HighlightSink) : CallReferenceHighlighter(reference, sink) {

  override fun getInapplicableMethodMessage(result: GroovyMethodResult, containingType: PsiType, arguments: Arguments): String {
    val method = result.element
    val methodName = method.name
    if (method is DefaultConstructor) {
      return GroovyBundle.message("cannot.apply.default.constructor", methodName)
    }
    else {
      val canonicalText = containingType.internalCanonicalText
      return GroovyBundle.message("cannot.apply.constructor", methodName, canonicalText, argumentsString(arguments))
    }
  }

  fun highlight(): Boolean = highlightMethodApplicability()
}
