// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.ml

import com.intellij.completion.ml.features.CompletionFeaturesPolicy

class GradleGroovyCompletionFeaturesPolicy : CompletionFeaturesPolicy {
  override fun useNgramModel(): Boolean = true
}