// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.backend

import com.intellij.ide.rpc.OpenFileChooserApiImpl
import com.intellij.ide.rpc.OpenFileChooserApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class OpenFileChooserApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<OpenFileChooserApi>()) {
      OpenFileChooserApiImpl()
    }
  }
}