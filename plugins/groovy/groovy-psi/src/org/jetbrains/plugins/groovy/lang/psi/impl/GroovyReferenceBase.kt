// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference

abstract class GroovyReferenceBase<T : PsiElement>(
  element: T
) : PsiPolyVariantReferenceBase<T>(element), GroovyReference {

  override fun getVariants(): Array<out Any> = ArrayUtil.EMPTY_OBJECT_ARRAY

  override fun resolve(): PsiElement? = super<GroovyReference>.resolve()
}
