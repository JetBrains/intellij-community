package com.intellij.workspace.legacyBridge.intellij

import com.intellij.ide.caches.CachesInvalidator

class ProjectModelCachesInvalidator : CachesInvalidator() {
  override fun invalidateCaches() = ProjectModelCacheImpl.invalidateCaches()
}