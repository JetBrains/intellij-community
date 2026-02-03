// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression

fun addStatementsInBlock(
  block: KtBlockExpression,
  statements: Array<PsiElement?>
) {
  val lBrace = block.firstChild
  block.addRangeAfter(statements[0], statements[statements.size - 1], lBrace)
}