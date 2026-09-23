// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest

/**
 * The teardown of a test-level [projectFixture] checks only the editors of its own project.
 * The editor of a class-level project is still open then, so it is neither reported as a leak nor released.
 */
@TestApplication
class ProjectFixtureEditorReleaseCheckTest {
  private companion object {
    val classProject = projectFixture()
    val classModule = classProject.moduleFixture()
    val classSourceRoot = classModule.sourceRootFixture()
    val classFile = classSourceRoot.psiFileFixture("class.txt", "class")
    val classEditor = classFile.editorFixture()
  }

  @Suppress("unused")
  private val testProject = projectFixture()

  // the second repetition sees what the teardown of the first test project did to the class-level editor
  @RepeatedTest(2)
  fun classLevelEditorOutlivesTestProject() {
    assertThat(classEditor.get().isDisposed).isFalse()
  }
}
