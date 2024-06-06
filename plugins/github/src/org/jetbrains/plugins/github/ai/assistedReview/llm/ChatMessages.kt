// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview.llm

import com.intellij.ml.llm.core.chat.messages.*
import com.intellij.ml.llm.core.models.openai.FormattedString
import com.intellij.ml.llm.privacy.trustedStringBuilders.privacyUnsafeDoNotUse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun CompletableMessage.waitForCompletion(): String {
  var error = false
  stateFlow
    .mapLatest {
      when (it) {
        is ThinkingState -> Unit
        is ReadyState -> {
          text
          throw CancellationException()
        }
        is CancelledState, is ErrorState -> {
          error = true
          throw CancellationException()
        }
        else -> error("Unexpected state: $it")
      }
    }
    .collect()
  if (error) {
    error("Error happened during waiting for completion")
  }
  return text.unwrap()
}

fun String.toFormattedString(): FormattedString = FormattedString(privacyUnsafeDoNotUse)