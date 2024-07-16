// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path


private const val TMP_DIR_PREFIX = "IJTests"

/**
 * Create temp dir in local temporary dir or [rootDirForTemp] (in-memory nio path could be provided)
 */
@TestOnly
suspend fun createTempDirectory(rootDirForTemp: Path? = null): Path = withContext(Dispatchers.IO) {
  if (rootDirForTemp == null) Files.createTempDirectory(TMP_DIR_PREFIX) else Files.createTempDirectory(rootDirForTemp, TMP_DIR_PREFIX)
}


