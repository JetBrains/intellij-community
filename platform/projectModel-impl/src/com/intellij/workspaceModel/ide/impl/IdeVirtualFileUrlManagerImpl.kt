// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IdeVirtualFileUrlManagerImpl : VirtualFileUrlManagerImpl() {
  override fun createVirtualFileUrl(id: Int, manager: VirtualFileUrlManagerImpl): VirtualFileUrlImpl {
    return VirtualFileUrlBridge(id, manager)
  }
}