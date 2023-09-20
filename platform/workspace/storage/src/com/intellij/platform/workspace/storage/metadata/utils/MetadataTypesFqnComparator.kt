// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.utils

import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.diff.MetadataComparator
import org.jetbrains.annotations.TestOnly

/**
 * Used in [MetadataComparator] to compare classes fqns and to resolve type fqn from [StorageTypeMetadata]
 *
 * The interface is needed to add separate logic during testing.
 * See [com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestMetadataComparatorUtil]
 */
internal interface MetadataTypesFqnComparator {
  fun compareFqns(cache: String, current: String): Boolean

  fun getTypeFqn(typeMetadata: StorageTypeMetadata): String

  companion object {
    internal fun getInstance(): MetadataTypesFqnComparator = INSTANCE

    @TestOnly
    internal fun replaceMetadataTypesFqnComparator(metadataTypesFqnComparator: MetadataTypesFqnComparator) {
      INSTANCE = metadataTypesFqnComparator
    }

    private var INSTANCE: MetadataTypesFqnComparator = MetadataTypesFqnComparatorImpl
  }
}

internal object MetadataTypesFqnComparatorImpl: MetadataTypesFqnComparator {
  override fun compareFqns(cache: String, current: String): Boolean {
    return cache == current
  }

  override fun getTypeFqn(typeMetadata: StorageTypeMetadata): String {
    return typeMetadata.fqName
  }
}