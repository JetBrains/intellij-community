// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.recursionSafeLazy
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyBase

/**
 * @param name name of a key in a map
 * @param context element to obtain project and resolve scope
 */
class GroovyMapPropertyImpl(
  private val type: PsiClassType,
  name: String,
  context: PsiElement
) : GroovyPropertyBase(name, context), GroovyMapProperty {

  private val scope = context.resolveScope

  override fun isValid(): Boolean = type.isValid

  private fun computePropertyType(): PsiType? {
    if (type is GrMapType) {
      val typeByKey = type.getTypeByStringKey(name)
      if (typeByKey != null) {
        return typeByKey
      }
    }
    val clazz = type.resolve() ?: return null
    val mapClass = JavaPsiFacade.getInstance(project).findClass(JAVA_UTIL_MAP, scope) ?: return null
    if (mapClass.typeParameters.size != 2) return null
    val mapSubstitutor = TypeConversionUtil.getClassSubstitutor(mapClass, clazz, PsiSubstitutor.EMPTY)
    return mapSubstitutor?.substitute(mapClass.typeParameters[1])
  }

  private val myPropertyType by recursionSafeLazy(initializer = ::computePropertyType)

  override fun getPropertyType(): PsiType? = myPropertyType

  override fun toString(): String = "Groovy Map Property"
}
