// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty

/**
 * @param name name of a key in a map
 * @param context element to obtain project and resolve scope
 */
class GroovyMapPropertyImpl(
  private val type: PsiClassType,
  private val name: String,
  context: PsiElement
) : LightElement(context.manager, GroovyLanguage), GroovyMapProperty {

  private val scope = context.resolveScope

  override fun isValid(): Boolean = type.isValid

  override fun getName(): String = name

  private fun computePropertyType(): PsiType? {
    val clazz = type.resolve() ?: return null
    val mapClass = JavaPsiFacade.getInstance(project).findClass(JAVA_UTIL_MAP, scope) ?: return null
    if (mapClass.typeParameters.size != 2) return null
    val mapSubstitutor = TypeConversionUtil.getClassSubstitutor(mapClass, clazz, PsiSubstitutor.EMPTY)
    return mapSubstitutor?.substitute(mapClass.typeParameters[1])
  }

  private val myPropertyType by lazy(::computePropertyType)

  override fun getPropertyType(): PsiType? = myPropertyType

  override fun toString(): String = "Groovy Map Property"
}
