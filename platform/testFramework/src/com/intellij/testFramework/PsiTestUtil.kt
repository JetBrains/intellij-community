// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference

@JvmOverloads
fun PsiFile.findReferenceByText(str: String, refOffset: Int = str.length / 2): PsiReference {
  val index = this.text.indexOf(str).takeIf { it != -1 } ?: throw AssertionError("can't find '$str'")
  var offset = refOffset
  if (offset < 0) {
    offset += str.length
  }
  return this.findReferenceAt(index + offset) ?: throw AssertionError("can't find reference for '$str'")
}