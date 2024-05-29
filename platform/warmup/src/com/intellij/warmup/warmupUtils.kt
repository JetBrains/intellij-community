// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.components.serviceAsync
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx

internal suspend fun waitIndexInitialization() {
  val fileBasedIndex = serviceAsync<FileBasedIndex>() as FileBasedIndexEx
  fileBasedIndex.loadIndexes()
  fileBasedIndex.waitUntilIndicesAreInitialized()
}