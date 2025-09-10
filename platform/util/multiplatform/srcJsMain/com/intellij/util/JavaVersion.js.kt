// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.lang.JavaVersion
import fleet.util.multiplatform.Actual
import org.jetbrains.annotations.ApiStatus

/**
 * actual for [com.intellij.util.currentJavaVersionPlatformSpecific]
 */
@Actual("currentJavaVersionPlatformSpecific")
@ApiStatus.Internal
fun currentJavaVersionPlatformSpecificJs(): JavaVersion {
  return JavaVersion.compose(21, 0, 0, 0, false)
}