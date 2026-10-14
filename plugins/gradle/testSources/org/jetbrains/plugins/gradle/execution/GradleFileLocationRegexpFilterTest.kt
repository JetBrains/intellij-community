// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Test

internal class GradleFileLocationRegexpFilterTest : GradleConsoleFilterTestCase() {

  override fun getFilter(project: Project): Filter = GradleConsoleFilterProvider.GradleFileLocationRegexpFilter(project)

  @Test
  fun `garbage line is not matched`() {
    "foo".shouldNotMatch()
    "/foo/bar".shouldNotMatch()
    "Build file '/foo/bar'".shouldNotMatch()
  }

  @Test
  fun `java errors matched`() {
    assertMatch("  ", "/Users/somebody/project/src/main/java/sample/broken/Broken.java", ":11: error:")
  }

  @Test
  fun `java errors with no message matched`() {
    assertMatch(" ", "/Users/somebody/project/src/main/java/sample/broken/Broken.java", ":5:")
  }

  @Test
  fun `kotlin location matched`() {
    assertMatch("         Location: ", "/Users/somebody/project/src/main/kotlin/Main.kt", ":9")
  }

  @Test
  fun `locations with column matched`() {
    assertMatch("", "/etc/passwd", ":42:42: Error text")
  }

  @Test
  fun `windows location matched`() {
    assertMatch("> ", """c:\autoexec.bat""", ":42")
  }
}
