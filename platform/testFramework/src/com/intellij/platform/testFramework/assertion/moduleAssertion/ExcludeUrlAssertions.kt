// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.moduleAssertion

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

object ExcludeUrlAssertions {
  @JvmStatic
  fun assertExcludedUrlsOrdered(moduleEntity: ModuleEntity, expectedExclusions: List<VirtualFileUrl>) {
    val actualRoots = moduleEntity.contentRoots.flatMap { it.excludedUrls.map { it.url } }
    CollectionAssertions.assertEqualsOrdered(expectedExclusions, actualRoots)
  }
}