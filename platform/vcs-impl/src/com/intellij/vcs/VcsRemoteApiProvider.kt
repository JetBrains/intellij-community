// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfActionsApi
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfApi
import com.intellij.vcs.shelf.BackendShelfActionsApi
import com.intellij.vcs.shelf.BackendShelfApi
import fleet.rpc.remoteApiDescriptor

internal class VcsRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfApi>()) {
      BackendShelfApi()
    }

    remoteApi(remoteApiDescriptor<RemoteShelfActionsApi>()) {
      BackendShelfActionsApi()
    }
  }
}
