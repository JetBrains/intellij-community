// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

class GradleNamedDomainCollectionContributor : NonCodeMembersContributor() {

  override fun getParentClassName(): String = GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER

  override fun processDynamicElements(qualifierType: PsiType,
                                      clazz: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (clazz == null) return
    if (state[DELEGATED_TYPE] == true) return

    val domainObjectName = processor.getName(state) ?: return // don't complete anything because we don't know what is in container
    val processProperties = processor.shouldProcessProperties()
    val processMethods = processor.shouldProcessMethods()
    if (!processProperties && !processMethods) {
      return
    }
    val domainObjectType = substituteTypeParameter(qualifierType, GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER, 0, false) ?: return

    val containingFile = place.containingFile
    val manager = containingFile.manager

    if (processProperties) {
      val property = GradleDomainObjectProperty(domainObjectName, domainObjectType, containingFile)
      if (!processor.execute(property, state)) {
        return
      }
    }
    if (processMethods) {
      val method = GrLightMethodBuilder(manager, domainObjectName).apply {
        returnType = domainObjectType
        addParameter("configuration", createType(GROOVY_LANG_CLOSURE, containingFile))
        originInfo = NAMED_DOMAIN_DECLARATION
      }
      if (!processor.execute(method, state)) {
        return
      }
    }
  }

  companion object {
    const val NAMED_DOMAIN_DECLARATION = "by NamedDomainCollection"
  }
}
