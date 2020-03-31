// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl.namedVariant

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType

class NamedParamData(
  val name: String,
  val type: PsiType?,
  val origin: PsiParameter,
  val navigationElement: PsiElement,
  val required: Boolean = false
)