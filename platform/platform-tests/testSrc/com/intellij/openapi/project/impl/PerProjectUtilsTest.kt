// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.junit.Test
import java.nio.file.Path

class PerProjectUtilsTest : BareTestFixtureTestCase() {
  @Test
  fun isChildProcessPathTest() {
    // empty path
    Assertions.assertThat(ProjectManagerEx.isChildProcessPath(Path.of(""))).isFalse

    // just per project suffix
    Assertions.assertThat(ProjectManagerEx.isChildProcessPath(Path.of(ProjectManagerEx.PER_PROJECT_SUFFIX))).isTrue

    // realistic case
    val realisticPath = "/Users/Nikita.Vlaev/Library/Application Support/JetBrains/IntelliJIdea2023.1/${ProjectManagerEx.PER_PROJECT_SUFFIX}/Users/Nikita.Vlaev/IdeaProjects/pug1"
    Assertions.assertThat(ProjectManagerEx.isChildProcessPath(Path.of(realisticPath))).isTrue
  }

  @Test
  fun lockedPathsPathManagerTest() {
    Assertions.assertThat(PathManager.getPerProjectLockedPaths()).isEmpty()

    val path1 = Path.of("p1")
    val path2 = Path.of("p2")
    PathManager.lockPerProjectPath(path1)
    Assertions.assertThat(PathManager.getPerProjectLockedPaths()).contains(path1)

    PathManager.lockPerProjectPath(path2)
    Assertions.assertThat(PathManager.getPerProjectLockedPaths()).contains(path2)

    PathManager.unlockPerProjectPath(path2)
    PathManager.unlockPerProjectPath(path1)

    Assertions.assertThat(PathManager.getPerProjectLockedPaths()).isEmpty()
  }
}