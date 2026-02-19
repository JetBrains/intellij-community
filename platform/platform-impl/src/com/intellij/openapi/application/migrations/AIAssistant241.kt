// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.application.PathManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files

private const val NOT_MIGRATED_FILENAME = ".ai-migration-disabled"

@ApiStatus.Internal
fun isAIDisabledBeforeMigrated(): Boolean {
  return Files.exists(PathManager.getConfigDir().resolve(NOT_MIGRATED_FILENAME))
}
