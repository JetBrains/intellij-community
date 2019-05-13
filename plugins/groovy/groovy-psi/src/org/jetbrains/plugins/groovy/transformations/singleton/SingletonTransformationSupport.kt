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
package org.jetbrains.plugins.groovy.transformations.singleton

import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags.*
import org.jetbrains.plugins.groovy.lang.psi.impl.booleanValue
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext
import org.jetbrains.plugins.groovy.transformations.plusAssign

class SingletonTransformationSupport : AstTransformationSupport {

  override fun applyTransformation(context: TransformationContext) {
    val annotation = context.getAnnotation(singletonFqn) ?: return
    val name = annotation.getPropertyName()
    val lazy = annotation.findDeclaredDetachedValue("lazy").booleanValue() ?: false

    context += GrLightField(context.codeClass, name, context.classType, annotation).apply {
      val modifiers = STATIC_MASK or if (lazy) PRIVATE_MASK else PUBLIC_MASK or FINAL_MASK
      modifierList.setModifiers(modifiers)
      navigationElement = annotation
      originInfo = singletonOriginInfo
    }

    context += context.memberBuilder.constructor {
      setModifiers(PRIVATE_MASK)
      originInfo = singletonOriginInfo
    }

    context += context.memberBuilder.method("get${name.capitalize()}") {
      setModifiers(PUBLIC_MASK or STATIC_MASK)
      returnType = context.classType
      navigationElement = annotation
      originInfo = singletonOriginInfo
    }
  }
}