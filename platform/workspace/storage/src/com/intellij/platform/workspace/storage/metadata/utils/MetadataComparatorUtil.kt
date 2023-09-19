// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.diff.MetadataComparator

/**
 * Used in [MetadataComparator] to compare classes fqns and to resolve type fqn from [StorageTypeMetadata]
 *
 * The interface is needed to add separate logic during testing.
 * See [com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestMetadataComparatorUtil]
 */
internal interface MetadataComparatorUtil {
  fun compareFqns(cache: String, current: String): Boolean

  fun getTypeFqn(typeMetadata: StorageTypeMetadata): String

  companion object {
    fun getInstance(): MetadataComparatorUtil =
      ApplicationManager.getApplication().getService(MetadataComparatorUtil::class.java)!!
  }
}

internal class MetadataComparatorUtilImpl: MetadataComparatorUtil {
  override fun compareFqns(cache: String, current: String): Boolean {
    return cache == current
  }

  override fun getTypeFqn(typeMetadata: StorageTypeMetadata): String {
    return typeMetadata.fqName
  }
}