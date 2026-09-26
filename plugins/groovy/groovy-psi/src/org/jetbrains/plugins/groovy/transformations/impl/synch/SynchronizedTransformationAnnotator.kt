// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil

class SynchronizedTransformationAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is GrAnnotation && element.qualifiedName == ANNO_FQN) {
      val method = (element.owner as? GrModifierList)?.parent as? GrMethod ?: return
      if (GrTraitUtil.isMethodAbstract(method)) {
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("synchronized.not.allowed.on.abstract.method")).range(element).create()
      }
    }
    else if (element.node.elementType == GroovyTokenTypes.mIDENT) {
      val field = element.parent as? GrField ?: return
      val staticField = field.isStatic()
      if (!staticField) {
        val hasStaticMethods = getMethodsReferencingLock(field).any { it.isStatic() }
        if (hasStaticMethods) {
          holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("lock.field.0.must.be.static", field.name)).range(element).create()
        }
      }
      else if (field.name == LOCK_NAME) {
        val hasInstanceMethods = getMethodsReferencingLock(field).any { !it.isStatic() }
        if (hasInstanceMethods) {
          holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("lock.field.0.must.not.be.static", LOCK_NAME)).range(element).create()
        }
      }
    }
    else if (PATTERN.accepts(element)) {
      element as GrLiteral
      val reference = element.reference ?: return
      val field = reference.resolve() as? GrField
      if (field == null) {
        val range = reference.rangeInElement.shiftRight(element.textRange.startOffset)
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("lock.field.0.not.found", element.value)).range(range).create()
      }
    }
  }
}
