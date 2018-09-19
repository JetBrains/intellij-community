// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*

class GroovyAnnotatorPre17(private val holder: AnnotationHolder, private val version: String) : GroovyElementVisitor() {

  private fun highlightInnerClass(typeDefinition: GrTypeDefinition) {
    if (typeDefinition.containingClass == null) return
    holder.createErrorAnnotation(typeDefinition.nameIdentifierGroovy, message("unsupported.inner.class.0", version))
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
    holder.createErrorAnnotation(anonymousClassDefinition.nameIdentifierGroovy, message("unsupported.anonymous.class.0", version))
  }
}
