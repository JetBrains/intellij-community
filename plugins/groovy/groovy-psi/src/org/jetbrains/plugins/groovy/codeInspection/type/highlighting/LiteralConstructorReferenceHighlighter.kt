// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference
import org.jetbrains.plugins.groovy.lang.typing.GrCollectionConstructorConverter.Companion.hasCollectionApplicableConstructor

class LiteralConstructorReferenceHighlighter(
  reference: GroovyConstructorReference,
  override val highlightElement: PsiElement,
  sink: HighlightSink
) : ConstructorCallHighlighter(reference, sink) {

  override fun shouldHighlightInapplicable(): Boolean {
    val clazz = (reference as GroovyConstructorReference).resolveClass()?.element as? PsiClass
    return clazz == null || !hasCollectionApplicableConstructor(clazz, highlightElement)
  }

  override fun highlightMethodApplicability(): Boolean {
    val results: Collection<GroovyResolveResult> = reference.resolve(false)
    if (results.isEmpty()) {
      val clazz = (reference as GroovyConstructorReference).resolveClass()?.element as? PsiClass
      if (clazz == null) {
        return false
      }
      sink.registerError(highlightElement, GroovyBundle.message("cannot.instantiate.interface", clazz.name))
      return true
    }
    else {
      return super.highlightMethodApplicability()
    }
  }
}
