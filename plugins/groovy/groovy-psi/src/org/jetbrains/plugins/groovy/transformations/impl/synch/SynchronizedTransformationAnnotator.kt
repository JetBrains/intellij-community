/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
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
        holder.createErrorAnnotation(element, "@Synchronized not allowed on abstract method")
      }
    }
    else if (element.node.elementType == GroovyTokenTypes.mIDENT) {
      val field = element.parent as? GrField ?: return
      val staticField = field.isStatic()
      if (!staticField) {
        val hasStaticMethods = getMethodsReferencingLock(field).any { it.isStatic() }
        if (hasStaticMethods) {
          holder.createErrorAnnotation(element, "Lock field '${field.name}' must be static")
        }
      }
      else if (field.name == LOCK_NAME) {
        val hasInstanceMethods = getMethodsReferencingLock(field).any { !it.isStatic() }
        if (hasInstanceMethods) {
          holder.createErrorAnnotation(element, "Lock field '$LOCK_NAME' must not be static")
        }
      }
    }
    else if (PATTERN.accepts(element)) {
      element as GrLiteral
      val reference = element.reference ?: return
      val field = reference.resolve() as? GrField
      if (field == null) {
        val range = reference.rangeInElement.shiftRight(element.textRange.startOffset)
        holder.createErrorAnnotation(range, "Lock field '${element.value}' not found")
      }
    }
  }
}
