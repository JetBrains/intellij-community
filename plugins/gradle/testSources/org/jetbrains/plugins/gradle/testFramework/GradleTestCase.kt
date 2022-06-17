// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion

abstract class GradleTestCase : GradleBaseTestCase() {

  fun isGradleAtLeast(version: String): Boolean =
    gradleFixture.gradleVersion.baseVersion >= GradleVersion.version(version)

  fun isGradleOlderThan(version: String): Boolean =
    gradleFixture.gradleVersion.baseVersion < GradleVersion.version(version)

  fun findOrCreateFile(relativePath: String, text: String): VirtualFile {
    gradleFixture.fileFixture.snapshot(relativePath)
    return runWriteActionAndGet {
      gradleFixture.fileFixture.root.findOrCreateFile(relativePath)
        .also { it.text = text }
    }
  }

  fun getFile(relativePath: String): VirtualFile {
    return runReadAction {
      gradleFixture.fileFixture.root.getFile(relativePath)
    }
  }
}