// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereRemoteApi
import fleet.rpc.remoteApiDescriptor

private class SearchEverywhereRemoteApiProvider: RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<SearchEverywhereRemoteApi>()) {
      SearchEverywhereRemoteApiImpl()
    }
  }
}