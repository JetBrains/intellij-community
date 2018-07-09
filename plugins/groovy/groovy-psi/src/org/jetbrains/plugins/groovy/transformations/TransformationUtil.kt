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
package org.jetbrains.plugins.groovy.transformations

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

class TransformationResult(
  val methods: Array<PsiMethod>,
  val fields: Array<GrField>,
  val innerClasses: Array<PsiClass>,
  val implementsTypes: Array<PsiClassType>,
  val extendsTypes: Array<PsiClassType>
)

private val ourTransformationContext = object : ThreadLocal<MutableSet<GrTypeDefinition>>() {
  override fun initialValue(): MutableSet<GrTypeDefinition> = HashSet()
}

private inline val transformationContext get() = ourTransformationContext.get()

fun transformDefinition(definition: GrTypeDefinition): TransformationResult {
  assert(transformationContext.add(definition))
  try {
    val transformationContext = TransformationContextImpl(definition)
    for (transformation in AstTransformationSupport.EP_NAME.extensions) {
      ProgressManager.checkCanceled()
      transformation.applyTransformation(transformationContext)
    }
    return transformationContext.transformationResult
  }
  finally {
    transformationContext.remove(definition)
  }
}

fun isUnderTransformation(clazz: PsiClass?): Boolean {
  return clazz is GrTypeDefinition && clazz in transformationContext
}

infix operator fun TransformationContext.plusAssign(method: PsiMethod): Unit = addMethod(method)

infix operator fun TransformationContext.plusAssign(field: GrField): Unit = addField(field)
