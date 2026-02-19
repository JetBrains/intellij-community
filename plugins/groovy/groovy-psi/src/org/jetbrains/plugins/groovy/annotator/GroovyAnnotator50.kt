// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase

class GroovyAnnotator50(private val holder: AnnotationHolder) : GroovyElementVisitor() {
  override fun visitFile(file: GroovyFileBase) {
    val duplicateVisitor = DuplicateVisitor()
    file.accept(duplicateVisitor)
    val nameToElementMap = duplicateVisitor.getDuplicateElements()
    for ((name, elements) in nameToElementMap) {
      elements.sortedBy { it.textRange.startOffset }.drop(1).forEach { element ->
        holder.newAnnotation(
          HighlightSeverity.ERROR,
          GroovyBundle.message("name.is.already.defined", name)
        )
          .range(element)
          .create()
      }
    }
  }
}