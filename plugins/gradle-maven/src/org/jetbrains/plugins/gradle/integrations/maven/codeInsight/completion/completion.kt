// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("Completions")
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.psi.PsiElement


internal fun runCompletion(argument: PsiElement, context: InsertionContext) {
  val position = argument.textRange.endOffset - 1
  context.editor.caretModel.moveToOffset(position)
  context.setLaterRunnable { CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(context.project, context.editor) }
}