// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.navigation

import com.intellij.navigation.finder.VirtualFileFinder
import com.intellij.openapi.progress.assertInstanceOf
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class VirtualFileFinderTest : NavigationTestBase() {
  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  @Test
  fun parseValidNavigationPathFull() = runInProjectTest {
    val findResult = VirtualFileFinder().find(project, mapOf("path" to "A.java:1:10"))

    assertInstanceOf<VirtualFileFinder.FindResult.Success>(findResult)
    if (findResult is VirtualFileFinder.FindResult.Success) {
      assertThat(findResult.virtualFile.name).isEqualTo("A.java")
      assertThat(findResult.locationInFile.line).isEqualTo(1)
      assertThat(findResult.locationInFile.column).isEqualTo(10)
    }
  }

  @Test
  fun parseValidNavigationPathNoColumn() = runInProjectTest {
    val findResult = VirtualFileFinder().find(project, mapOf("path" to "A.java:1"))

    assertInstanceOf<VirtualFileFinder.FindResult.Success>(findResult)
    if (findResult is VirtualFileFinder.FindResult.Success) {
      assertThat(findResult.virtualFile.name).isEqualTo("A.java")
      assertThat(findResult.locationInFile.line).isEqualTo(1)
      assertThat(findResult.locationInFile.column).isEqualTo(0)
    }
  }

  @Test
  fun parseValidNavigationPathNoLineNoColumn() = runInProjectTest {
    val findResult = VirtualFileFinder().find(project, mapOf("path" to "A.java"))

    assertInstanceOf<VirtualFileFinder.FindResult.Success>(findResult)
    if (findResult is VirtualFileFinder.FindResult.Success) {
      assertThat(findResult.virtualFile.name).isEqualTo("A.java")
      assertThat(findResult.locationInFile.line).isEqualTo(0)
      assertThat(findResult.locationInFile.column).isEqualTo(0)
    }
  }

  @Test
  fun parseInvalidNavigationPathNoFile() = runInProjectTest {
    val findResult = VirtualFileFinder().find(project, mapOf("path" to ":1:10"))

    assertInstanceOf<VirtualFileFinder.FindResult.Error>(findResult)
  }

  @Test
  fun parseInvalidNavigationPathNoLine() = runInProjectTest {
    val findResult = VirtualFileFinder().find(project, mapOf("path" to "A.java::10"))

    assertInstanceOf<VirtualFileFinder.FindResult.Error>(findResult)
  }

  @Test
  fun parseInvalidNavigationPathNoValues() = runInProjectTest {
    val findResult = VirtualFileFinder().find(project, mapOf("path" to "::"))

    assertInstanceOf<VirtualFileFinder.FindResult.Error>(findResult)
  }

  @Test
  fun parseInvalidNavigationPathEmpty() = runInProjectTest {
    val findResult = VirtualFileFinder().find(project, mapOf("path" to ""))

    assertInstanceOf<VirtualFileFinder.FindResult.Error>(findResult)
  }
}
