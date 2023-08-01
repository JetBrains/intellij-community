// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.util.io.isFile
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class NioPathAssertion : FileAssertion<Path>() {

  override fun createAssertion() = NioPathAssertion()
  override fun isEmpty(file: Path) = file.listDirectoryEntries().isEmpty()
  override fun exists(file: Path) = file.exists()
  override fun isFile(file: Path) = file.isFile()
  override fun isDirectory(file: Path) = file.isDirectory()

  companion object {

    suspend fun assertNioPath(init: suspend () -> Path?): FileAssertion<Path> {
      return NioPathAssertion().init { init() }
    }
  }
}