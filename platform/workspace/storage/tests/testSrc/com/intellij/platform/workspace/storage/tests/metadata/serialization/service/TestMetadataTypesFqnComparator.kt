// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization.service

import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.utils.MetadataTypesFqnComparator
import com.intellij.platform.workspace.storage.tests.metadata.serialization.replaceCacheVersion

internal object TestMetadataTypesFqnComparator: MetadataTypesFqnComparator {
  override fun compareFqns(cache: String, current: String): Boolean {
    return cache.replaceCacheVersion() == current
  }

  override fun getTypeFqn(typeMetadata: StorageTypeMetadata): String {
    return typeMetadata.fqName.replaceCacheVersion()
  }
}