// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.jimfs

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.FileSystem

/**
 * Creates Jim FS in memory
 */
@TestOnly
@JvmOverloads
fun jimFsFixture(
  configuration: Configuration = Configuration.forCurrentPlatform(),
): TestFixture<FileSystem> = testFixture {
  val fs = Jimfs.newFileSystem(configuration)
  initialized(fs) {
    withContext(Dispatchers.IO) {
      fs.close()
    }
  }
}
