// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5Jimfs.showcases.resouces

import com.intellij.platform.testFramework.junit5Jimfs.JimfsResource
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem

/**
 * Inject in-memory fs
 */
@TestApplication
@JimfsResource
class JUnit5ClassLevelTempFsTest {

  @Test
  fun test(fs: FileSystem) {
    fs.rootDirectories.first().resolve("file").write("D")
  }

}