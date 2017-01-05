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
package org.jetbrains.plugins.groovy.transformations.indexedProperty

import com.intellij.lang.annotation.AnnotationHolder
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
      holder.createErrorAnnotation(annotation, "@IndexedProperty is applicable to properties only")
      return true
    }

    if (field.getIndexedComponentType() == null) {
      val elementToHighlight = field.typeElementGroovy ?: field.nameIdentifierGroovy
      val message = "Property is not indexable. Type must be array or list but found ${field.type.presentableText}"
      holder.createErrorAnnotation(elementToHighlight, message)
    }

    return true
  }
}