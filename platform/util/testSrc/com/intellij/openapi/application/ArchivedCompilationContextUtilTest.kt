// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class ArchivedCompilationContextUtilTest {
  @Test
  fun `absolute targets file does not go through Bazel runfiles`(@TempDir tempDir: Path) {
    val propertyName = ArchivedCompilationContextUtil.BAZEL_TARGETS_JSON_FILE_PROPERTY
    val previousValue = System.getProperty(propertyName)
    val targetsFile = tempDir.resolve("bazel-targets.json").toAbsolutePath()
    try {
      System.setProperty(propertyName, targetsFile.toString())

      assertThat(ArchivedCompilationContextUtil.getBazelTargetsJsonPath(tempDir)).isEqualTo(targetsFile)
    }
    finally {
      if (previousValue == null) {
        System.clearProperty(propertyName)
      }
      else {
        System.setProperty(propertyName, previousValue)
      }
    }
  }
}
