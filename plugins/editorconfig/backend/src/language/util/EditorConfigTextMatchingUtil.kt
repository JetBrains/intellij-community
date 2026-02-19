// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.psi.PsiElement

object EditorConfigTextMatchingUtil {
  fun textMatchesToIgnoreCase(element: PsiElement, other: PsiElement): Boolean {
    if (element.textLength != other.textLength) return false
    if (element.textMatches(other)) return true
    return textMatchesToIgnoreCase(element, other.text)
  }

  fun textMatchesToIgnoreCase(element: PsiElement, other: CharSequence): Boolean {
    if (element.textLength != other.length) return false
    if (element.textMatches(other)) return true
    return element.text.regionMatches(0, other, 0, other.length, true)
  }

  fun textMatchesToIgnoreCase(first: CharSequence, second: CharSequence): Boolean {
    if (first.length != second.length) return false
    return first.regionMatches(0, second, 0, first.length, true)
  }
}
