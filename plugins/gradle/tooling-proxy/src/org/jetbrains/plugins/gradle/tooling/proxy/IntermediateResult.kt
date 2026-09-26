// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy

import java.io.Serializable

class IntermediateResult(
  val type: IntermediateResultType,
  val value: ByteArray
) : org.gradle.launcher.daemon.protocol.Message(), Serializable

enum class IntermediateResultType {
  PROJECT_LOADED, BUILD_FINISHED, STREAMED_VALUE
}
