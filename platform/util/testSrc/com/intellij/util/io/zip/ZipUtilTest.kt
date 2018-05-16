// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.delete
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class ZipUtilTest {
  @Test
  fun doNotGoOutside() {
    val evilFile = Paths.get("/tmp/evil.txt")
    evilFile.delete()

    val tempDirectory = FileUtilRt.createTempDirectory("doNotGoOutside", null)
    try {
      ZipUtil.extract(File(getTestDataPath(), "zip-slip.zip"), tempDirectory, null);
      assertThat(evilFile).doesNotExist()
    }
    finally {
      FileUtilRt.delete(tempDirectory)
    }
  }
}

private fun getTestDataPath(): File {
  val communityDir = File(PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/'))
  return File(communityDir, "platform/util/testData")
}