// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

open class CodeFragment(val offset: Int, val length: Int) {
  private val children = mutableListOf<CodeElement>()
  lateinit var path: String
  lateinit var text: String

  fun getChildren(): List<CodeElement> = children.sortedBy { it.offset }

  fun addChild(token: CodeElement) {
    if (children.any { it.offset == token.offset && it.text.length == token.text.length }) return
    children.add(token)
  }
}

class CodeFragmentWithPsi(
  offset: Int,
  length: Int,
  val psi: Pointer<PsiElement>
): CodeFragment(offset, length) {
  constructor(
    offset: Int,
    length: Int,
    element: PsiElement
  ): this(offset, length, psi = SmartPointerManager.createPointer(element))
}
