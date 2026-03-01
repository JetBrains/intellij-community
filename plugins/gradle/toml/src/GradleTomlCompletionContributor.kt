// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toml

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType

internal class GradleTomlCompletionContributor() : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, insideLibrariesTable(), GradleTomlCompletionProvider())
  }
}
