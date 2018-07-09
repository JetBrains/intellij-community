// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic

import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType

private open class PsiParameterMirror(original: PsiParameter) : PsiParameter by original

fun PsiParameter.withType(newType: PsiType): PsiParameter {
  return object : PsiParameterMirror(this) {
    override fun getType(): PsiType = newType
  }
}
