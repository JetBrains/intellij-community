// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyName
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods

/**
 * Exposes managed properties as setters of corresponding type.
 *
 * https://docs.gradle.org/7.0.2/userguide/custom_gradle_types.html#configuration_using_properties
 */
class GradleExtensionMembersContributor : NonCodeMembersContributor() {

  override fun processDynamicElements(qualifierType: PsiType, processor: PsiScopeProcessor, place: PsiElement, state: ResolveState) {
    if (qualifierType !is GradleExtensionType) {
      return
    }
    if (!processor.shouldProcessMethods()) {
      return
    }
    val requiredMethodName = processor.getName(state)
    val requiredPropertyName = if (requiredMethodName == null) {
      // if there is no required method name, then process all properties as setters
      null
    }
    else {
      PropertyKind.SETTER.getPropertyName(requiredMethodName)
      ?: return // if the required method name is not a setter name, then do nothing
    }
    val clazz = qualifierType.resolve() ?: return
    val masqueradingProcessor = GradleManagedPropertyAsSetterProcessor(processor, place.manager, requiredPropertyName)
    clazz.processDeclarations(masqueradingProcessor, state, null, place)
  }
}
