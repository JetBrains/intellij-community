// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import fleet.rpc.remoteApiDescriptor

class ShelfApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfApi>()) {
      BackendShelfApi()
    }
  }
}

class BackendShelfApi : RemoteShelfApi {

}

