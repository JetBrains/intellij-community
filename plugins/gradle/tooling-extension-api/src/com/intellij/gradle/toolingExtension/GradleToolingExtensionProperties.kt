// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object GradleToolingExtensionProperties {

  const val PARALLEL_MODEL_FETCH_PROPERTY_KEY: String = "idea.parallelModelFetch.enabled"

  const val USE_RESILIENT_MODEL_FETCH_SYSTEM_PROPERTY_KEY: String = "idea.use.resilient.model.fetch"

  @JvmStatic
  fun isParallelModelFetchEnabled(): Boolean =
    java.lang.Boolean.getBoolean(PARALLEL_MODEL_FETCH_PROPERTY_KEY)

  @JvmStatic
  fun isResilientModelFetchApiUsed(): Boolean =
    java.lang.Boolean.getBoolean(USE_RESILIENT_MODEL_FETCH_SYSTEM_PROPERTY_KEY)
}
