// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.util.TextRange

/**
 * Represents scope of class or function.
 *
 * For example,
 * ```java
 * @Override
 * public void run() { // <- primary line
 *   //       ^ navigation offset
 *   // ...
 * }                   // <- scope line
 * ```
 */
internal interface StickyLine : Comparable<StickyLine> {

  /**
   * The first logical line of scope used to pin corresponding editor's line on sticky panel.
   *
   * Usually it is `lineOf(psiElement.textOffset)`
   */
  fun primaryLine(): Int

  /**
   * The last logical line of scope used to unpin corresponding editor's line from sticky panel.
   *
   * Usually it is `lineOf(psiElement.endOffset)`
   */
  fun scopeLine(): Int

  /**
   * Offset where editor's caret put on mouse click.
   *
   * Usually it is `psiElement.textOffset`
   */
  fun navigateOffset(): Int

  /**
   * Range between primary line and scope line.
   *
   * Usually it is `TextRange(psiElement.textOffset, psiElement.endOffset)`
   */
  fun textRange(): TextRange

  /**
   * Short text of psi element representing the sticky line.
   *
   * Usually it is `psiElement.toString`. Maybe null if debug mode is disabled
   */
  fun debugText(): String?

  /**
   * Compares lines according scope order
   */
  override fun compareTo(other: StickyLine): Int
}
