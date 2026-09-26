// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.jimfs.showcase

import com.intellij.platform.testFramework.junit5.jimfs.jimFsFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import org.junit.jupiter.api.Test

/**
 * Inject in-memory fs
 */
@TestApplication
class JUnit5ClassLevelTempFsTest {

  private companion object {
    val fsFixture = jimFsFixture()
  }

  @Test
  fun test() {
    val fs = fsFixture.get()
    fs.rootDirectories.first().resolve("file").write("D")
  }
}
