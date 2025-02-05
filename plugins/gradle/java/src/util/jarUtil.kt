// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.inputStream

fun isValidJar(path: Path): Boolean {
  try {
    return path.inputStream(StandardOpenOption.READ).use {
      val head = it.readNBytes(2)
      if (head.size < 2) {
        return false
      }
      return@use head[0] == 0x50.toByte() && head[1] == 0x4b.toByte()
    }
  }
  catch (e: Exception) {
    throw IllegalArgumentException("Unable to verify file $path: ${e.message}")
  }
}