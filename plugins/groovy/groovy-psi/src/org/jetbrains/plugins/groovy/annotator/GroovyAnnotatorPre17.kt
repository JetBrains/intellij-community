// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*

class GroovyAnnotatorPre17(private val holder: AnnotationHolder, private val version: @NlsSafe String) : GroovyElementVisitor() {

  private fun highlightInnerClass(typeDefinition: GrTypeDefinition) {
    if (typeDefinition.containingClass == null) return
    holder.newAnnotation(HighlightSeverity.ERROR, message("unsupported.inner.class.0", version)).range(
      typeDefinition.nameIdentifierGroovy).create()
  }

  override fun visitClassDefinition(classDefinition: GrClassDefinition) {
    highlightInnerClass(classDefinition)
  }

  override fun visitInterfaceDefinition(interfaceDefinition: GrInterfaceDefinition) {
    highlightInnerClass(interfaceDefinition)
  }

  override fun visitAnnotationTypeDefinition(annotationTypeDefinition: GrAnnotationTypeDefinition) {
    highlightInnerClass(annotationTypeDefinition)
  }

  override fun visitAnonymousClassDefinition(anonymousClassDefinition: GrAnonymousClassDefinition) {
    holder.newAnnotation(HighlightSeverity.ERROR, message("unsupported.anonymous.class.0", version)).range(anonymousClassDefinition.nameIdentifierGroovy).create()
  }
}
