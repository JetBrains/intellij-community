// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl.navigation

import com.intellij.navigation.LocationToOffsetConverter
import com.intellij.navigation.NavigatorWithinProject
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.containers.ComparatorUtil.max
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertNull

class NavigatorWithinProjectTest: NavigationTestBase() {
  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  @Test fun pathTabInLinePositionCharacterOneBased() = runNavigationTest(
    navigationAction = { navigateByPath("A.java:3:20", locationToOffsetAsCharacterOneBased) }
  ) {
    assertThat(currentElement.containingFile.name).isEqualTo("A.java")
    with(currentCharacterZeroBasedPosition) {
      assertThat(line + 1).isEqualTo(3)
      assertThat(column + 1).isEqualTo(20)
    }
  }

  @Test fun pathTabInLinePositionLogical() = runNavigationTest(
    navigationAction = { navigateByPath("A.java:2:10", locationToOffsetAsLogicalPosition) }
  ) {
    assertThat(currentElement.containingFile.name).isEqualTo("A.java")
    with(currentLogicalPosition) {
      assertThat(line).isEqualTo(2)
      assertThat(column).isEqualTo(10)
    }
  }

  @Test fun pathNegativeOffset() = runNavigationTest (
    navigationAction = { navigateByPath("A.java:3:5", locationToOffsetNegativeOffset) }
  ) {
    assertThat(currentElement.containingFile.name).isEqualTo("A.java")
    with(currentCharacterZeroBasedPosition) {
      assertThat(line).isEqualTo(0)
      assertThat(column).isEqualTo(0)
    }
  }

  @Test fun pathNoColumn() = runNavigationTest(
    navigationAction = { navigateByPath("A.java:3", locationToOffsetAsCharacterOneBased) }
  ) {
    assertThat(currentElement.containingFile.name).isEqualTo("A.java")
    with(currentCharacterZeroBasedPosition) {
      assertThat(line + 1).isEqualTo(3)
      assertThat(column).isEqualTo(0)
    }
  }

  @Test fun pathNoLineNoColumn() = runNavigationTest(
    navigationAction = { navigateByPath("A.java", locationToOffsetAsCharacterOneBased) }
  ) {
    assertThat(currentElement.containingFile.name).isEqualTo("A.java")
    with(currentCharacterZeroBasedPosition) {
      assertThat(line).isEqualTo(0)
      assertThat(column).isEqualTo(0)
    }
  }

  @Test fun parseValidNavigationPathFull() {
    val (file, line, column) = NavigatorWithinProject.parseNavigationPath("A.java:1:10")
    assertThat(file).isEqualTo("A.java")
    assertThat(line).isEqualTo("1")
    assertThat(column).isEqualTo("10")
  }

  @Test fun parseValidNavigationPathNoColumn() {
    val (file, line, column) = NavigatorWithinProject.parseNavigationPath("A.java:1")
    assertThat(file).isEqualTo("A.java")
    assertThat(line).isEqualTo("1")
    assertNull(column)
  }

  @Test fun parseValidNavigationPathNoLineNoColumn() {
    val (file, line, column) = NavigatorWithinProject.parseNavigationPath("A.java")
    assertThat(file).isEqualTo("A.java")
    assertNull(line)
    assertNull(column)
  }

  @Test fun parseInvalidNavigationPathNoFile() {
    val (file, line, column) = NavigatorWithinProject.parseNavigationPath(":1:10")
    assertNull(file)
    assertNull(line)
    assertNull(column)
  }

  @Test fun parseInvalidNavigationPathNoLine() {
    val (file, line, column) = NavigatorWithinProject.parseNavigationPath("A.java::10")
    assertNull(file)
    assertNull(line)
    assertNull(column)
  }

  @Test fun parseInvalidNavigationPathNoValues() {
    val (file, line, column) = NavigatorWithinProject.parseNavigationPath("::")
    assertNull(file)
    assertNull(line)
    assertNull(column)
  }

  @Test fun parseInvalidNavigationPathEmpty() {
    val (file, line, column) = NavigatorWithinProject.parseNavigationPath("")
    assertNull(file)
    assertNull(line)
    assertNull(column)
  }

  private fun navigateByPath(path: String, locationToOffsetConverter: LocationToOffsetConverter) =
    NavigatorWithinProject(project, mapOf("path" to path), locationToOffsetConverter)
      .navigate(listOf(NavigatorWithinProject.NavigationKeyPrefix.PATH))

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