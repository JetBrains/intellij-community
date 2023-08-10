// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor

class GdslMemberContributor : NonCodeMembersContributor() {

  override fun processDynamicElements(qualifierType: PsiType, processor: PsiScopeProcessor, place: PsiElement, state: ResolveState) {
    if (qualifierType !is PsiClassType) {
      return
    }
    GroovyDslFileIndex.processExecutors(qualifierType, place) { holder, descriptor ->
      holder.processMembers(descriptor, processor, state)
    }
  }
}
