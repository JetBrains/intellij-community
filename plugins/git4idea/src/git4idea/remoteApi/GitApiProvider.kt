// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.vcs.git.shared.rpc.GitRepositoryApi
import com.intellij.vcs.git.shared.rpc.GitWidgetApi
import fleet.rpc.remoteApiDescriptor

internal class GitApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<GitWidgetApi>()) {
      GitWidgetApiImpl()
    }
    remoteApi(remoteApiDescriptor<GitRepositoryApi>()) {
      GitRepositoryApiImpl()
    }
  }
}