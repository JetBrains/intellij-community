// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion.ml

import com.intellij.completion.ml.features.CompletionFeaturesPolicy

class GradleGroovyCompletionFeaturesPolicy : CompletionFeaturesPolicy {
  override fun useNgramModel(): Boolean = true
}