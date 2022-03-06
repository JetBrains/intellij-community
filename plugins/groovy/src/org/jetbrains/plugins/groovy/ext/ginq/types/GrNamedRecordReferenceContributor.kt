// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

class GrNamedRecordReferenceContributor : NonCodeMembersContributor() {

  override fun processDynamicElements(qualifierType: PsiType, processor: PsiScopeProcessor, place: PsiElement, state: ResolveState) {
    val resolvedClass = qualifierType.resolve()
    if (resolvedClass !is GrSyntheticNamedRecordClass) {
      return
    }
    val name = ResolveUtil.getNameHint(processor) ?: return
    if (!processor.shouldProcessProperties()) return
    val type = resolvedClass[name]
    if (type != null) {
      val prop = GrLightField(resolvedClass, name, type, resolvedClass)
      processor.execute(prop, state)
    }
    // todo resolvings
  }
}