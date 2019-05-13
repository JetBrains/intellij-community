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
package org.jetbrains.plugins.groovy.transformations.listenerList

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiWildcardType
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
      null -> holder.createErrorAnnotation(annotation, "@ListenerList field must have a generic Collection type")
      is PsiWildcardType -> holder.createErrorAnnotation(annotation, "@ListenerList field with generic wildcards not supported")
    }

    return true
  }
}