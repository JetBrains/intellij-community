// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement

abstract class EditorConfigCompletionProviderBase : CompletionProvider<CompletionParameters>() {
  abstract val destination: PsiElementPattern.Capture<PsiElement>
}
