// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.icons.AllIcons
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.repository.search.completion.api.BaseDependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.SERVER
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleConstants
import javax.swing.Icon

private const val MIN_DEPENDENCY_AUTO_POPUP_PREFIX_LENGTH = 3

/**
 * Whether an auto-popup has to be suppressed because too few characters of the dependency coordinate have been
 * typed yet. Explicitly invoked completion is never suppressed, so this is always `false` for it.
 */
@ApiStatus.Internal
fun CompletionParameters.isBelowDependencyAutoPopupThreshold(prefix: String): Boolean =
  isAutoPopup && prefix.length < MIN_DEPENDENCY_AUTO_POPUP_PREFIX_LENGTH

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

internal val DependencyCompletionContext.eelDescriptor: EelDescriptor
  get() = project.getEelDescriptor()

@ApiStatus.Internal
fun CompletionParameters.getCompletionContext(): DependencyCompletionContext =
  DependencyCompletionContextImpl(originalFile.project, GradleConstants.SYSTEM_ID)

@get:ApiStatus.Internal
val BaseDependencyCompletionResult.icon: Icon
  get() = when(source) {
    LOCAL -> AllIcons.Build.CompletionLocalCache
    SERVER -> AllIcons.Build.CompletionCloud
  }