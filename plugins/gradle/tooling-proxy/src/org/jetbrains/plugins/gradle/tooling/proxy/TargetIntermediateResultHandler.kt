// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy

fun interface TargetIntermediateResultHandler {

  fun onResult(type: IntermediateResultType, result: Any?)

  fun then(other: TargetIntermediateResultHandler): TargetIntermediateResultHandler {
    return TargetIntermediateResultHandler { type, result ->
      this.onResult(type, result)
      other.onResult(type, result)
    }
  }

  companion object {

    @JvmStatic
    val NOOP = TargetIntermediateResultHandler { _, _ -> }
  }
}