// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Internal
fun removeDummySuffix(value: String?): String {
  if (value == null) {
    return ""
  }
  val index = value.indexOf(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
  val result = if (index >= 0) {
    value.take(index)
  } else {
    value
  }
  return result.trim()
}

@ApiStatus.Internal
fun CompletionParameters.getCompletionContext(): DependencyCompletionContext =
  DependencyCompletionContextImpl(originalFile.virtualFile.toNioPath().getEelDescriptor(), GradleConstants.SYSTEM_ID)