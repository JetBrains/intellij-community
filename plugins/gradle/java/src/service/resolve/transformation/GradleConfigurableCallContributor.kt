// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.transformation

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.light.LightParameter
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PropertyUtilBase
import org.jetbrains.plugins.gradle.config.isGradleFile
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods

/**
 * The properties of type `org.gradle.api.NamedDomainObjectContainer` are extended with implicit .call method
 * We cannot provide implicit call reference because groovy plugin determines it statically, so we apply a little cheating here
 * @see org.gradle.internal.extensibility.MixInClosurePropertiesAsMethodsDynamicObject
 */
class GradleConfigurableCallContributor : NonCodeMembersContributor() {


  override fun getParentClassName(): String? = null

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (!processor.shouldProcessMethods()) {
      return
    }
    val name = processor.getName(state)
    if (name.isNullOrEmpty()) {
      return
    }
    if (aClass == null) {
      return
    }
    if (place.containingFile.isGradleFile().not()) {
      return
    }
    if (place !is GrReferenceExpression) {
      return
    }
    val getter = PropertyUtilBase.findPropertyGetter(aClass, name, false, true, false) ?: return
    val resolvedClassType = getter.returnType as? PsiClassType ?: return
    val resolvedClass = resolvedClassType.resolve() ?: return
    if (!InheritanceUtil.isInheritor(resolvedClass, GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER)) {
      return
    }
    val newMethod = LightMethodBuilder(place.manager, GroovyLanguage, place.referenceName).apply {
      containingClass = aClass
      setMethodReturnType(resolvedClassType)
      addParameter(LightParameter("cl", GroovyPsiElementFactory.getInstance(project).createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE), place))
      putUserData(GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY, resolvedClassType)
      originInfo = "Created by augmenting properties with .configure function"
    }
    processor.execute(newMethod, state)
  }
}