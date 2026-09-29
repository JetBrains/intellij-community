// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class IntelliJPlatformGradleConsoleFoldingTest : LightJavaCodeInsightFixtureTestCase() {

  fun testShouldFoldLine() {
    val folding = IntelliJPlatformGradleConsoleFolding()

    assertTrue(
      folding.shouldFoldLine(
        project,
        "Layout component 'vcs-log' has some nonexistent 'action' elements: 'Vcs.Log.Action'"
      )
    )

    assertTrue(
      folding.shouldFoldLine(
        project,
        "[org.jetbrains.intellij.platform] Layout component 'actions' has some nonexistent 'group' elements: 'CustomGroup'"
      )
    )

    assertTrue(
      folding.shouldFoldLine(
        project,
        "> Task :buildPlugin: Layout component 'foo' has some nonexistent 'bar' elements: 'baz'"
      )
    )

    assertFalse(folding.shouldFoldLine(project, "> Task :buildPlugin"))
    assertFalse(folding.shouldFoldLine(project, "BUILD SUCCESSFUL in 2s"))
    assertFalse(folding.shouldFoldLine(project, "Layout component is loaded successfully"))
  }

  fun testShouldBeAttachedToThePreviousLine() {
    val folding = IntelliJPlatformGradleConsoleFolding()
    assertFalse(folding.shouldBeAttachedToThePreviousLine())
  }

  fun testGetPlaceholderText() {
    val folding = IntelliJPlatformGradleConsoleFolding()

    assertNull(folding.getPlaceholderText(project, emptyList()))

    assertEquals(
      "<1 layout component warning>",
      folding.getPlaceholderText(
        project,
        listOf("Layout component 'a' has some nonexistent 'b' elements: 'c'")
      )
    )

    assertEquals(
      "<5 layout component warnings>",
      folding.getPlaceholderText(
        project,
        List(5) { "Layout component '$it' has some nonexistent 'b' elements: 'c'" }
      )
    )
  }
}
