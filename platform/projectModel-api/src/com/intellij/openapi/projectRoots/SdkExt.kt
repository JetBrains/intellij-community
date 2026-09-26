// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

/**
 * Check if [Sdk.getHomePath] belongs to a certain [eelDescriptor] (i.e. path points to it)
 */
@ApiStatus.Internal
fun Sdk.belongsToEel(eelDescriptor: EelDescriptor): Boolean {
  val homePathStr = homePath ?: return false
  val homePath = try {
    Path(homePathStr)
  }
  catch (e: InvalidPathException) {
    // SDK is definitely NOT on this eel. It could be target-based or broken
    logger.warn("$this has wrong home path: $homePathStr", e)
    return false
  }

  return homePath.getEelDescriptor() == eelDescriptor
}

private val logger = fileLogger()