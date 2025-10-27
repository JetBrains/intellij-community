// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions")

package com.intellij.platform.util.impl

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.EelSharedSecrets
import java.nio.file.Path

internal class EelPlatformUtilFallbackAccessor : EelSharedSecrets.PlatformUtilAccessor {
  override val priority: Int = 500

  override fun deleteRecursively(fileOrDirectory: Path) {
    NioFiles.deleteRecursively(fileOrDirectory)
  }
}