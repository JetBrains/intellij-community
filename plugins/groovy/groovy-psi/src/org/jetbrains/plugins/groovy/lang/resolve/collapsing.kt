// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod

@Suppress("RemoveExplicitTypeArguments")
fun Collection<PsiElement>.collapseReflectedMethods(): Collection<PsiElement> {
  return mapTo(mutableSetOf<PsiElement>()) {
    (it as? GrReflectedMethod)?.baseMethod ?: it
  }
}

fun Collection<PsiElement>.collapseAccessors(): Collection<PsiElement> {
  val fields = filterIsInstance<GrField>()
  return filter { it !is GrAccessorMethod || it.property !in fields }
}
