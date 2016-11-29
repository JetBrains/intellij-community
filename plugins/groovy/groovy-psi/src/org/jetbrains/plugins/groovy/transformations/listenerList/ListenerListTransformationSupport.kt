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

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrMethodWrapper
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign

class ListenerListTransformationSupport : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    for (field in context.fields) {
      val annotation = PsiImplUtil.getAnnotation(field, listenerListFqn) ?: continue
      val listenerType = field.getListenerType() as? PsiClassType ?: continue
      val listenerClass = listenerType.resolve() ?: continue
      val declaredName = annotation.findDeclaredDetachedValue("name").stringValue()
      val name = StringUtil.nullize(declaredName) ?: listenerClass.name ?: continue
      context += context.memberBuilder.method("add${name.capitalize()}") {
        addModifier(GrModifierFlags.PUBLIC_MASK)
        returnType = PsiType.VOID
        addParameter("listener", listenerType)
        navigationElement = field
        originInfo = listenerListOriginInfo
      }
      context += context.memberBuilder.method("remove${name.capitalize()}") {
        addModifier(GrModifierFlags.PUBLIC_MASK)
        returnType = PsiType.VOID
        addParameter("listener", listenerType)
        navigationElement = field
        originInfo = listenerListOriginInfo
      }
      context += context.memberBuilder.method("get${name.capitalize()}s") {
        addModifier(GrModifierFlags.PUBLIC_MASK)
        returnType = listenerType.createArrayType()
        navigationElement = field
        originInfo = listenerListOriginInfo
      }

      val fireCandidates = listenerClass.methods.filter {
        it.hasModifierProperty(PsiModifier.PUBLIC) && !it.hasModifierProperty(PsiModifier.STATIC)
      }

      for (listenerMethod in fireCandidates) {
        context += GrMethodWrapper.wrap(listenerMethod, "fire${listenerMethod.name.capitalize()}").apply {
          addModifier(GrModifierFlags.PUBLIC_MASK)
          modifierList.removeModifier(GrModifierFlags.ABSTRACT_MASK)
          returnType = PsiType.VOID
          originInfo = listenerListOriginInfo
        }
      }
    }
  }
}