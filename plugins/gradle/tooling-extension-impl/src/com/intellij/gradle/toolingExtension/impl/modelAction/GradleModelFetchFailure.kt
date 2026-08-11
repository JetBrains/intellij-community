// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import org.gradle.tooling.Failure
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
data class GradleModelFetchFailure(
  val message: String?,
  val description: String?,
  val causes: List<GradleModelFetchFailure>,
) : Serializable {

  constructor(failure: Failure): this(
    failure.message,
    failure.description,
    failure.causes.map { GradleModelFetchFailure(it) }
  )
}
