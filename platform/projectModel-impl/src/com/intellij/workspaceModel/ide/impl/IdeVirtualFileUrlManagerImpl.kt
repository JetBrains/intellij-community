// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl

class IdeVirtualFileUrlManagerImpl : VirtualFileUrlManagerImpl() {
  override fun createVirtualFileUrl(id: Int, manager: VirtualFileUrlManagerImpl, protocol: String?): VirtualFileUrlImpl {
    return VirtualFileUrlBridge(id, manager, protocol != StandardFileSystems.JAR_PROTOCOL)
  }
}