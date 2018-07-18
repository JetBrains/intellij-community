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

import com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder

internal val indexedPropertyFqn = "groovy.transform.IndexedProperty"
internal val indexedPropertyOriginInfo = "by @IndexedProperty"
val indexedMethodKind: String = "groovy.transform.IndexedProperty.kind"

internal fun GrField.getIndexedComponentType() = CachedValuesManager.getCachedValue(this) {
  Result.create(doGetIndexedComponentType(this), containingFile)
}

private fun doGetIndexedComponentType(field: GrField): PsiType? {
  val fieldType = field.type
  return when (fieldType) {
    is PsiArrayType -> fieldType.componentType
    is PsiClassType -> PsiUtil.substituteTypeParameter(fieldType, JAVA_UTIL_LIST, 0, true)
    else -> null
  }
}

internal fun findIndexedPropertyMethods(field: GrField) = field.containingClass?.methods?.filter {
  it is GrLightMethodBuilder && it.methodKind == indexedMethodKind && it.navigationElement == field
}
