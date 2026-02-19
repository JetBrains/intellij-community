// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType

open class LazyTypeProperty(
  name: String,
  type: String,
  protected val myContext: PsiElement
) : GroovyPropertyBase(name, myContext) {

  override fun isValid(): Boolean = super.isValid() && myContext.isValid

  private val myType by lazyPub {
    createType(type, myContext)
  }

  final override fun getPropertyType(): PsiType = myType
}
