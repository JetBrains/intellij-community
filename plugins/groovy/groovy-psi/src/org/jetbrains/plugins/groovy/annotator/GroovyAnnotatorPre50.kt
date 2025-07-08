// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrPatternVariable

class GroovyAnnotatorPre50(val holder : AnnotationHolder) : GroovyElementVisitor() {
  override fun visitPatternVariable(variable: GrPatternVariable) {
    holder.newAnnotation(
      HighlightSeverity.ERROR,
      GroovyBundle.message("inspection.message.instanceof.pattern.variable.with.groovy.5.or.later")
    ).range(variable).create()
  }
}