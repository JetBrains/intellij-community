// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.caches.CachesInvalidator

class WorkspaceModelCachesInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    WorkspaceModelCacheImpl.invalidateCaches()
    GlobalWorkspaceModelCacheImpl.invalidateCaches()
  }
}