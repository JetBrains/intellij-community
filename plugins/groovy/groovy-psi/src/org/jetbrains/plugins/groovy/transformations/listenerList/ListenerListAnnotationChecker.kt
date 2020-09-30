// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.listenerList

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiWildcardType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.checkers.CustomAnnotationChecker
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration

class ListenerListAnnotationChecker : CustomAnnotationChecker() {

  override fun checkApplicability(holder: AnnotationHolder, annotation: GrAnnotation): Boolean {
    if (annotation.qualifiedName != listenerListFqn) return false
    val modifierList = annotation.owner as? GrModifierList
    val parent = modifierList?.parent as? GrVariableDeclaration
    val field = parent?.variables?.singleOrNull() as? GrField ?: return true

    when (field.getListenerType()) {
      null -> holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("listener.list.field.must.have.a.generic.collection.type")).range(annotation).create()
      is PsiWildcardType -> holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("listener.list.field.with.generic.wildcards.not.supported")).range(annotation).create()
    }

    return true
  }
}