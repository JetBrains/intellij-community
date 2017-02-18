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

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags.PUBLIC_MASK
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getGetterNameNonBoolean
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getSetterName
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign

class IndexedPropertyTransformationSupport : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    for (field in context.fields) {
      if (!field.isProperty) continue
      PsiImplUtil.getAnnotation(field, indexedPropertyFqn) ?: continue
      val componentType = field.getIndexedComponentType() ?: continue

      val fieldName = field.name

      context += context.memberBuilder.method(getGetterNameNonBoolean(fieldName)) {
        addModifier(PUBLIC_MASK)
        returnType = componentType
        addParameter("index", PsiType.INT)
        navigationElement = field
        originInfo = indexedPropertyOriginInfo
        methodKind = indexedMethodKind
      }
      context += context.memberBuilder.method(getSetterName(fieldName)) {
        addModifier(PUBLIC_MASK)
        returnType = PsiType.VOID
        addParameter("index", PsiType.INT)
        addParameter("value", componentType)
        navigationElement = field
        originInfo = indexedPropertyOriginInfo
        methodKind = indexedMethodKind
      }
    }
  }
}
