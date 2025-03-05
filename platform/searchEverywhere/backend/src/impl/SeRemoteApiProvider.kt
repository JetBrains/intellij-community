// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
private class SeRemoteApiProvider: RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<SeRemoteApi>()) {
      SeRemoteApiImpl()
    }
  }
}