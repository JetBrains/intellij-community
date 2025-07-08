// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

public interface ClassSearcher {
  public fun findClass(place: PsiElement): PsiClass?
}
