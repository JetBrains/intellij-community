// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FSRecordsCacheInvalidator : CachesInvalidator() {
  override fun getDescription(): String = IdeCoreBundle.message("checkbox.invalidate.caches.invalidates.vfs")

  override fun optionalCheckboxDefaultValue(): Boolean = Registry.`is`("idea.invalidate.caches.invalidates.vfs")

  override fun invalidateCaches() {
    FSRecords.getInstance().scheduleRebuild("By FSRecordsCacheInvalidator request", /* error: */ null)
  }
}
