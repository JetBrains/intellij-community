// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PathManagerEx")
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Absolute canonical path to system cache dir.
 */
val appSystemDir: Path
  get() {
    val path = Paths.get(PathManager.getSystemPath())
    try {
      return path.toRealPath()
    }
    catch (e: IOException) {
      Logger.getInstance(PathManager::class.java).warn(e)
    }
    return path
  }
