// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.indexedProperty

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.checkers.CustomAnnotationChecker
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration

class IndexedPropertyAnnotationChecker : CustomAnnotationChecker() {

  override fun checkApplicability(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (annotation.qualifiedName != indexedPropertyFqn) return false
    val modifierList = annotation.owner as? GrModifierList ?: return true
    val parent = modifierList.parent as? GrVariableDeclaration ?: return true
    val field = parent.variables.singleOrNull() as? GrField ?: return true

    if (!field.isProperty) {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("indexed.property.is.applicable.to.properties.only")).range(annotation).create()
      return true
    }

    if (field.getIndexedComponentType() == null) {
      val message = GroovyBundle.message("inspection.message.property.not.indexable.type.must.be.array.or.list.but.found.0",
                                         field.type.presentableText)
      holder.newAnnotation(HighlightSeverity.ERROR, message).create()
    }

    return true
  }
}
