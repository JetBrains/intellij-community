// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IdeVirtualFileUrlManagerImpl : VirtualFileUrlManagerImpl() {
  override fun createVirtualFileUrl(name: String, manager: VirtualFileUrlManagerImpl, parent: VirtualFileUrl?): VirtualFileUrl {
    return VirtualFileUrlBridge(name, manager, parent)
  }

  override val virtualFileUrlImplementationClass: Class<out VirtualFileUrl>
    get() = VirtualFileUrlBridge::class.java
}
