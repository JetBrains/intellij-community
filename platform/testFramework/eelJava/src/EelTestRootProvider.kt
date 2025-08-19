// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.eelJava

import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively

@ApiStatus.Internal
object EelTestRootProvider {

  @OptIn(ExperimentalPathApi::class)
  fun getTestRoot(testName: String): Path {
    val testBaseRoot = when (getFixtureEngine()) {
      EelFixtureEngine.DOCKER -> getFileSystemMount()
      EelFixtureEngine.WSL -> getFileSystemMount().resolve("/tmp/")
      EelFixtureEngine.NONE -> Paths.get(FileUtilRt.getTempDirectory())
    }
    val testRoot = testBaseRoot.resolve(testName)
    testRoot.deleteRecursively()
    testRoot.createDirectories()
    return testRoot
  }
}