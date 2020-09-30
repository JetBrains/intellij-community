// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.indexedProperty

import com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder

internal const val indexedPropertyFqn = "groovy.transform.IndexedProperty"
@NonNls
internal const val indexedPropertyOriginInfo = "by @IndexedProperty"
const val indexedMethodKind: String = "groovy.transform.IndexedProperty.kind"

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
