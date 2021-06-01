// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROVIDER_PROPERTY
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.getAccessorName
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyName

internal class GradleManagedPropertyAsSetterProcessor(
  private val delegate: PsiScopeProcessor,
  private val manager: PsiManager,
  private val requiredPropertyName: String?,
) : ProcessorWithHints() {

  init {
    if (requiredPropertyName != null) {
      val getterName: String = PropertyKind.GETTER.getAccessorName(requiredPropertyName)
      hint(NameHint.KEY, NameHint { getterName })
    }
    hint(ElementClassHint.KEY, ElementClassHint { it == ElementClassHint.DeclarationKind.METHOD })
  }

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element !is PsiMethod) {
      return true
    }
    val propertyName = PropertyKind.GETTER.getPropertyName(element)
    if (propertyName == null) {
      return true
    }
    if (requiredPropertyName != null && requiredPropertyName != propertyName) {
      return true
    }
    if (element.hasModifierProperty(PsiModifier.STATIC)) {
      return true
    }
    val setterName = PropertyKind.SETTER.getAccessorName(propertyName)
    val setter = GrLightMethodBuilder(manager, setterName).apply {
      returnType = PsiType.VOID
      navigationElement = element
      containingClass = element.containingClass
      addParameter("value", element.returnType?.let(::propertyType))
    }
    return delegate.execute(setter, state)
  }

  private fun propertyType(returnType: PsiType): PsiType? {
    if (InheritanceUtil.isInheritor(returnType, GRADLE_API_PROVIDER_PROPERTY)) {
      return PsiUtil.substituteTypeParameter(returnType, GRADLE_API_PROVIDER_PROPERTY, 0, false)
    }
    else {
      return null
    }
  }
}
