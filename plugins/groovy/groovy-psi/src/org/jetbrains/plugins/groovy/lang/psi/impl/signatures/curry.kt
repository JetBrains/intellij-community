// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature

fun curry(signatures: List<GrSignature>, args: Array<PsiType>, position: Int, context: PsiElement): List<GrSignature> {
  return signatures.flatMap {
    GrClosureSignatureUtil.curryImpl(it, args, position, context)
  }
}
