// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import kotlinx.coroutines.channels.SendChannel

internal class ChannelTransformCollector<R>(private val ch: SendChannel<R>) : TransformCollector<R> {

  override suspend fun out(value: R) {
    ch.send(value)
  }
}
