// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.backend

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.scopes.ScopeModelApi
import com.intellij.platform.scopes.ScopeModelApiImpl
import fleet.rpc.remoteApiDescriptor

internal class ScopesStateApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ScopeModelApi>()) {
      ScopeModelApiImpl()
    }
  }
}
