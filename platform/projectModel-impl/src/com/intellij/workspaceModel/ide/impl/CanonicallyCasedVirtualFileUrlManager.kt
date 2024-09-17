// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CanonicallyCasedVirtualFileUrlManager(delegate: VirtualFileUrlManager) : VirtualFileUrlManager by delegate {
  override fun getOrCreateFromUrl(uri: String): VirtualFileUrl = getOrCreateFromCanonicallyCasedUrl(uri)
}
