// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.psi.PsiElement

/**
 * Appends [text] to the [builder] cutting length of [left] text from the start and length of [right] text from the end.
 */
internal fun appendTextBetween(builder: StringBuilder, text: String, left: PsiElement?, right: PsiElement?) {
  val start = left?.textLength ?: 0
  val end = text.length - (right?.textLength ?: 0)
  builder.append(text, start, end)
}

/**
 * Appends text of elements to the [builder] between [start] and [stop].
 * If [stop] is `null` then all siblings of [start] are processed.
 */
internal fun appendElements(builder: StringBuilder, start: PsiElement, stop: PsiElement) {
  var current: PsiElement? = start.nextSibling
  while (current !== null && current !== stop) {
    builder.append(current.text)
    current = current.nextSibling
  }
}
