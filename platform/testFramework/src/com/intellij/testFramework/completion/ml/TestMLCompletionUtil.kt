// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.completion.ml

import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.openapi.util.Key

fun createTestMLCompletionEnvironmentDuringCompletion(): CompletionEnvironment {
  val completionProgressIndicator = CompletionService.getCompletionService().getCurrentCompletion() as? CompletionProgressIndicator
                                    ?: error("No completion available")
  return createTestMLCompletionEnvironment(completionProgressIndicator)
}

fun createTestMLCompletionEnvironment(completionProgressIndicator: CompletionProgressIndicator): CompletionEnvironment =
  object : CompletionEnvironment {
    override fun <T> getUserData(key: Key<T>) = completionProgressIndicator.getUserData(key)
    override fun <T> putUserData(key: Key<T>, value: T?) = completionProgressIndicator.putUserData(key, value)
    override fun getLookup() = completionProgressIndicator.lookup
    override fun getParameters() = completionProgressIndicator.parameters ?: error("parameters are missing during completion")
  }
