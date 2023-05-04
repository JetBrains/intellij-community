// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

internal object VfsSnapshotUtils {
  fun VfsSnapshot.VirtualFileSnapshot.fullPath(): List<String?> =
    if (parentId.getOrNull() != null) {
      (parent.getOrNull()?.fullPath() ?: emptyList()) + listOf(name.getOrNull())
    }
    else {
      listOf(name.getOrNull())
    }
}