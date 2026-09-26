// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.Serializable

@ApiStatus.Internal
class GradleModelFetchFailureResult(
  val targetPath: File?,
  val failures: List<GradleModelFetchFailure>,
) : Serializable
