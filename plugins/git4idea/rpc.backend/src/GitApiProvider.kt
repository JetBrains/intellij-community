// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.rpc.backend

import com.intellij.openapi.components.service
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.vcs.git.rpc.GitIncomingOutgoingStateApi
import com.intellij.vcs.git.rpc.GitOperationsApi
import com.intellij.vcs.git.rpc.GitRepositoryApi
import com.intellij.vcs.git.rpc.GitUiSettingsApi
import com.intellij.vcs.git.rpc.GitWidgetApi
import fleet.rpc.remoteApiDescriptor

internal class GitApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<GitWidgetApi>()) { service<GitWidgetApi>() }
    remoteApi(remoteApiDescriptor<GitRepositoryApi>()) { service<GitRepositoryApi>() }
    remoteApi(remoteApiDescriptor<GitIncomingOutgoingStateApi>()) { service<GitIncomingOutgoingStateApi>() }
    remoteApi(remoteApiDescriptor<GitUiSettingsApi>()) { service<GitUiSettingsApi>() }
    remoteApi(remoteApiDescriptor<GitOperationsApi>()) { service<GitOperationsApi>() }
  }
}

