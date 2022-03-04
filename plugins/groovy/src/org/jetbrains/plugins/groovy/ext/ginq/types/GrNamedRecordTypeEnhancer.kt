// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.ext.ginq.ast.ginqBinding
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrReferenceTypeEnhancer

class GrNamedRecordTypeEnhancer : GrReferenceTypeEnhancer() {
  override fun getReferenceType(ref: GrReferenceExpression?, resolved: PsiElement?): PsiType? {
    ref ?: return null
    resolved ?: return null
    val referenceName = ref.referenceName ?: return null
    val qualifier = ref.qualifierExpression ?: return null
    if (resolved.getUserData(ginqBinding) == null) {
      return null
    }
    val namedRecordClass = qualifier.type?.resolve()?.castSafelyTo<GrSyntheticNamedRecordClass>() ?: return null
    return namedRecordClass[referenceName]
  }
}