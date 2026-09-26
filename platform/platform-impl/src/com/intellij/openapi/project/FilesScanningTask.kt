// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import org.jetbrains.annotations.ApiStatus


/** A task in [UnindexedFilesScannerExecutor] */
@ApiStatus.Internal
interface FilesScanningTask {
  fun isFullIndexUpdate(): Boolean?
  fun close()
}