// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import java.nio.file.Path
import kotlin.io.path.exists

fun Path.createNioFile(): Path {
  check(!exists()) {
    "File already exists: $this"
  }
  return findOrCreateNioFile()
}

fun Path.createNioDirectory(): Path {
  check(!exists()) {
    "Directory already exists: $this"
  }
  return findOrCreateNioDirectory()
}

fun Path.createNioFile(relativePath: String): Path {
  return getResolvedNioPath(relativePath).createNioFile()
}

fun Path.createNioDirectory(relativePath: String): Path {
  return getResolvedNioPath(relativePath).createNioDirectory()
}