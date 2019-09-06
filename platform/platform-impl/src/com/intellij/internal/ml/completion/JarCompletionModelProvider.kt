// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.completion

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.ModelMetadata
import com.intellij.internal.ml.ResourcesMetadataReader

abstract class JarCompletionModelProvider(private val displayName: String,
                                          private val resourceDirectory: String) : RankingModelProvider {
  private val lazyModel: DecisionFunction by lazy {
    val metadata = FeaturesInfo.buildInfo(ResourcesMetadataReader(this::class.java, resourceDirectory))
    createModel(metadata)
  }

  override fun getModel(): DecisionFunction = lazyModel

  override fun getDisplayNameInSettings(): String = displayName

  protected abstract fun createModel(metadata: ModelMetadata): DecisionFunction
}
