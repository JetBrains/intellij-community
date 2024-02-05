// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

open class CodeToken(override val text: String,
                override val offset: Int,
                val properties: TokenProperties = TokenProperties.UNKNOWN
) : CodeElement

class CodeTokenWithPsi(
  text: String,
  offset: Int,
  properties: TokenProperties = TokenProperties.UNKNOWN,
  val psi: Pointer<PsiElement>
): CodeToken(text, offset, properties) {
  constructor(
    text: String,
    offset: Int,
    properties: TokenProperties = TokenProperties.UNKNOWN,
    element: PsiElement
  ): this(text, offset, properties, psi = SmartPointerManager.createPointer(element))
}
