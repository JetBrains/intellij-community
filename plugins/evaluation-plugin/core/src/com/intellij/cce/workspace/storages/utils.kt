// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import java.nio.file.Files
import java.nio.file.Paths

fun ensureDirExists(dir: String) {
  val path = Paths.get(dir)
  if (!Files.exists(path)) Files.createDirectories(path)
}