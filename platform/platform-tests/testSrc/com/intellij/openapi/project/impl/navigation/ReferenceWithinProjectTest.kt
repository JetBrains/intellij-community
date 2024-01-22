// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.navigation

import com.intellij.navigation.target.LocationToOffsetConverter
import com.intellij.navigation.target.ReferenceWithinProject
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.containers.ComparatorUtil.max
import org.junit.ClassRule
import org.junit.Test

class ReferenceWithinProjectTest : NavigationTestBase() {
  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  @Test fun pathTabInLinePositionCharacterOneBased() = runNavigationTest(
    navigationAction = { navigateByPath("A.java:3:20", locationToOffsetAsCharacterOneBased) }
  ) {
    assertThat(getCurrentElement().containingFile.name).isEqualTo("A.java")
    with(getCurrentCharacterZeroBasedPosition()) {
      assertThat(line + 1).isEqualTo(3)
      assertThat(column + 1).isEqualTo(20)
    }
  }

  @Test fun pathTabInLinePositionLogical() = runNavigationTest(
    navigationAction = { navigateByPath("A.java:2:10", locationToOffsetAsLogicalPosition) }
  ) {
    assertThat(getCurrentElement().containingFile.name).isEqualTo("A.java")
    with(getCurrentLogicalPosition()) {
      assertThat(line).isEqualTo(2)
      assertThat(column).isEqualTo(10)
    }
  }

  @Test fun pathNegativeOffset() = runNavigationTest (
    navigationAction = { navigateByPath("A.java:3:5", locationToOffsetNegativeOffset) }
  ) {
    assertThat(getCurrentElement().containingFile.name).isEqualTo("A.java")
    with(getCurrentCharacterZeroBasedPosition()) {
      assertThat(line).isEqualTo(0)
      assertThat(column).isEqualTo(0)
    }
  }

  @Test fun pathNoColumn() = runNavigationTest(
    navigationAction = { navigateByPath("A.java:3", locationToOffsetAsCharacterOneBased) }
  ) {
    assertThat(getCurrentElement().containingFile.name).isEqualTo("A.java")
    with(getCurrentCharacterZeroBasedPosition()) {
      assertThat(line + 1).isEqualTo(3)
      assertThat(column).isEqualTo(0)
    }
  }

  @Test fun pathNoLineNoColumn() = runNavigationTest(
    navigationAction = { navigateByPath("A.java", locationToOffsetAsCharacterOneBased) }
  ) {
    assertThat(getCurrentElement().containingFile.name).isEqualTo("A.java")
    with(getCurrentCharacterZeroBasedPosition()) {
      assertThat(line).isEqualTo(0)
      assertThat(column).isEqualTo(0)
    }
  }

  private suspend fun navigateByPath(path: String, locationToOffsetConverter: LocationToOffsetConverter) =
    ReferenceWithinProject(project, mapOf("path" to path), locationToOffsetConverter)
      .navigate()

  private val locationToOffsetAsLogicalPosition: LocationToOffsetConverter = { locationInFile, editor ->
    editor.logicalPositionToOffset(LogicalPosition(locationInFile.line, locationInFile.column))
  }

  private val locationToOffsetAsCharacterOneBased: LocationToOffsetConverter = { locationInFile, editor ->
    val offsetOfLine = editor.logicalPositionToOffset(LogicalPosition(max(locationInFile.line - 1, 0), 0))
    val offsetInLine = max(locationInFile.column - 1, 0)
    offsetOfLine + offsetInLine
  }

  private val locationToOffsetNegativeOffset: LocationToOffsetConverter = { _, _ -> -1 }
}
