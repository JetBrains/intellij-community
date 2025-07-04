// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.topics.backend

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.topics.impl.RemoteTopicApi
import fleet.rpc.remoteApiDescriptor

private class BackendRemoteTopicApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteTopicApi>()) {
      BackendRemoteTopicApi()
    }
  }
}