// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics

import com.intellij.openapi.extensions.ExtensionPointName

interface RemoteTopicListener<E : Any> {
  val topic: RemoteTopic<E>

  fun handleEvent(event: E)

  companion object {
    val EP_NAME: ExtensionPointName<RemoteTopicListener<*>> = ExtensionPointName<RemoteTopicListener<*>>("com.intellij.platform.rpc.remoteTopicListener")
  }
}