// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.jimfs

import com.google.common.jimfs.Jimfs
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AnnotatedElementContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.io.TempDirFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * to be used as factory for [org.junit.jupiter.api.io.TempDir]
 * See [JimFsTempDir]
 */
@TestOnly
class JimFsTempDirFactory : TempDirFactory {

  private val fs = Jimfs.newFileSystem()

  override fun createTempDirectory(elementContext: AnnotatedElementContext, extensionContext: ExtensionContext): Path {
    return Files.createTempDirectory(fs.rootDirectories.first(), "IJTests")
  }

  override fun close() {
    fs.close()
  }
}
