// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

/**
 * @author peter
 */
class DynamicMembersContributor : NonCodeMembersContributor() {

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (aClass == null) return
    val manager = DynamicManager.getInstance(aClass.project)
    for (qName in ClassUtil.getSuperClassesWithCache(aClass).keys) {
      for (method in manager.getMethods(qName)) {
        if (!ResolveUtil.processElement(processor, method, state)) return
      }

      for (property in manager.getProperties(qName)) {
        if (!ResolveUtil.processElement(processor, property, state)) return
      }
    }
  }
}
