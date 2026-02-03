// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class JUnit5ProjectFixtureTest {

  private companion object {
    val sharedProject0 = projectFixture()
    val sharedProject1 = projectFixture()
    val openedProject = projectFixture(openAfterCreation = true)

    var seenProject: Project? = null
  }

  private val localProject0 = projectFixture()
  private val localProject1 = projectFixture()

  @Test
  fun `open after creation`() {
    assertTrue(openedProject.get().isOpen)
  }

  @Test
  fun `fixture returns same instance`() {
    assertSame(sharedProject0.get(), sharedProject0.get())
    assertSame(sharedProject1.get(), sharedProject1.get())
    assertSame(localProject0.get(), localProject0.get())
    assertSame(localProject1.get(), localProject1.get())
  }

  @Test
  fun `projects are different`() {
    assertNotSame(sharedProject0.get(), sharedProject1.get())
    assertNotSame(localProject0.get(), localProject1.get())
    assertNotSame(localProject0.get(), sharedProject0.get())
    assertNotSame(localProject0.get(), sharedProject1.get())
    assertNotSame(localProject1.get(), sharedProject0.get())
    assertNotSame(localProject1.get(), sharedProject1.get())
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `shared project is kept between tests`(id: Int) {
    val project = sharedProject0.get()
    assertFalse((project as ProjectEx).isDisposed)
    if (id == 0) {
      seenProject = project
    }
    else {
      assertSame(seenProject, project)
      seenProject = null
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `local project is recreated between tests`(id: Int) {
    val project = localProject0.get()
    assertFalse((project as ProjectEx).isDisposed)
    if (id == 0) {
      seenProject = project
    }
    else {
      assertNotSame(seenProject, project)
      assertTrue((seenProject as ProjectEx).isDisposed)
      seenProject = null
    }
  }
}
