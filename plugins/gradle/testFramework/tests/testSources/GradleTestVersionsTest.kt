// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.tests

import com.intellij.gradle.testFramework.GradleTestVersions
import org.assertj.core.api.BDDAssertions.then
import org.junit.Test
import java.io.InputStream

class GradleTestVersionsTest {
  @Test
  fun testBaseVersionIncludedInTheFullList() {
    then(GradleTestVersions.SUPPORTED_GRADLE_VERSIONS).contains(GradleTestVersions.BASE_GRADLE_VERSION)
  }

  @Test
  fun testFullListInSyncWithResourceFile() {
    val stream: InputStream? = GradleTestVersions::class.java.getResourceAsStream("/gradle.versions.list")
    requireNotNull(stream) { "Failed to load resource file" }
    val inFile = stream.bufferedReader()
      .readLines()
      .map { it.trim() }
      .filterNot { it.startsWith("#") }
      .filter { it.isNotBlank() }

    val inCode = GradleTestVersions.SUPPORTED_GRADLE_VERSIONS.toList()

    if (inFile != inCode) {
      val toAdd = inCode - inFile.toSet()
      val toRemove = inFile - inCode.toSet()
      throw AssertionError(
        "The list of supported Gradle versions in the GradleTestVersions.SUPPORTED_GRADLE_VERSIONS and the list in the resource file 'gradle.versions.list' are not in sync." +
        "Please update the list in the resource file.\n" +
        "Add values: ${toAdd}\n"+
        "Remove values: ${toRemove}\n"
      )
    }

  }
}