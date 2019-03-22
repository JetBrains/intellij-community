// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrMethodWrapper
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods

class GradleSetterAsMethodContributor : NonCodeMembersContributor() {

  override fun getParentClassName(): String? = GradleCommonClassNames.GRADLE_API_PROJECT

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (aClass == null || !processor.shouldProcessMethods()) {
      return
    }
    val setterName = processor.getName(state)?.let { "set${it.capitalize()}" }
    aClass.processDeclarations(SetterAsMethodProcessor(setterName, processor), state, null, place)
  }

  private class SetterAsMethodProcessor(
    setterName: String?,
    private val delegate: PsiScopeProcessor
  ) : ProcessorWithHints() {

    init {
      if (setterName != null) {
        hint(NameHint.KEY, NameHint { setterName })
      }
      hint(ElementClassHint.KEY, ElementClassHint { it == ElementClassHint.DeclarationKind.METHOD })
    }

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
      val method = element as? PsiMethod ?: return true
      val propertyName = checkAndExtractPropertyName(method) ?: return true
      return delegate.execute(GrMethodWrapper.wrap(method, propertyName), state)
    }

    private fun checkAndExtractPropertyName(method: PsiMethod): String? {
      val methodName = method.name
      if (!methodName.startsWith(PropertyKind.SETTER.prefix)) return null
      val propertyName = methodName.removePrefix(PropertyKind.SETTER.prefix).decapitalize()
      if (propertyName.isEmpty()) return null
      if (method.returnType != PsiType.VOID) return null
      return propertyName
    }
  }
}
