// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware

internal class GradleTomlCompletionContributor() : CompletionContributor(), DumbAware {
  init {
    extend(CompletionType.BASIC, insideLibrariesTable(), GradleTomlCompletionProvider())
  }
}
