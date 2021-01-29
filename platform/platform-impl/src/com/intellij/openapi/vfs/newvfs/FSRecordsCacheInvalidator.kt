// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs

import com.intellij.ide.IdeBundle
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords

class FSRecordsCacheInvalidator : CachesInvalidator() {
  override fun getDescription(): String = IdeBundle.message("checkbox.invalidate.caches.invalidates.vfs")

  override fun optionalCheckboxDefaultValue() = Registry.`is`("idea.invalidate.caches.invalidates.vfs")

  override fun invalidateCaches() {
    FSRecords.invalidateCaches()
  }
}
