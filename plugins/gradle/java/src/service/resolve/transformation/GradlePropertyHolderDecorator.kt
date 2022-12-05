// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.transformation

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightClass
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PropertyUtilBase.*
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROVIDER_PROPERTY

/**
 * It is a case that gradle generates setter methods by getters whose type is [org.gradle.api.provider.Property]
 * in runtime.
 * We need to mimic this behavior by decorating affected classes with new methods
 * See [gradle docs](https://docs.gradle.org/current/userguide/lazy_configuration.html#lazy_configuration)
 */
class GradlePropertyHolderDecorator(delegate: PsiClass) : LightClass(delegate) {

  private val syntheticSetters = run {
    val allMethods = delegate.allMethods
    val methodNames = allMethods.mapTo(HashSet()) { it.name }
    allMethods.mapNotNull { getSetter(it, methodNames) }
  }

  private fun getSetter(method: PsiMethod?, names: Set<String>): PsiMethod? {
    method ?: return null
    if (!isSimplePropertyGetter(method)) {
      return null
    }
    val simpleName = getPropertyName(method) ?: return null
    val setterName = getAccessorName(simpleName, PropertyKind.SETTER)
    if (names.contains(setterName)) {
      return null
    }
    val returnType = method.returnType ?: return null
    val returnClassType = returnType.asSafely<PsiClassType>()
    if (returnClassType?.resolve()?.qualifiedName != GRADLE_API_PROVIDER_PROPERTY) return null
    return with(LightMethodBuilder(delegate.manager, setterName)) {
      navigationElement = method
      setMethodReturnType(PsiType.VOID)
      val innerParameter = returnClassType.parameters.singleOrNull() ?: return null
      addParameter("value", innerParameter)
      this
    }
  }

  override fun getMethods(): Array<PsiMethod> {
    return super.getMethods() + syntheticSetters
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
    return super.processDeclarations(processor, state, lastParent, place) && run {
      for (innerSetter in syntheticSetters) {
        if (!processor.execute(innerSetter, state)) {
          return@run false
        }
      }
      true
    }
  }
}