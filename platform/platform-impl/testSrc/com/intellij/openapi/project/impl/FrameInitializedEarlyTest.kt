// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.OpenProjectImplOptions
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.mock.MockProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class FrameInitializedEarlyTest {
  private val disposable = Disposer.newDisposable()

  @AfterEach
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun `an existing project initializes its frame early`() {
    assertThat(isFrameInitializedEarly(OpenProjectTask {})).isTrue()
  }

  @Test
  fun `a new project initializes its frame early only when its opener asks`() {
    assertThat(isFrameInitializedEarly(OpenProjectTask { isNewProject = true })).isFalse()
    assertThat(isFrameInitializedEarly(OpenProjectTask {
      isNewProject = true
      initFrameEarlyForNewProject = true
    })).isTrue()
  }

  @Test
  fun `a before-open task or a given project keeps the frame initialization late`() {
    assertThat(isFrameInitializedEarly(OpenProjectTask {
      isNewProject = true
      initFrameEarlyForNewProject = true
      beforeOpen = { true }
    })).isFalse()
    assertThat(isFrameInitializedEarly(OpenProjectTask {
      initFrameEarlyForNewProject = true
      project = MockProject(null, disposable)
    })).isFalse()
  }

  @Test
  fun `a frame guest initializes its frame early, so its record exists before its startup activities`() {
    assertThat(isFrameInitializedEarly(OpenProjectTask {
      isNewProject = true
      implOptions = OpenProjectImplOptions(frameOwner = MockProject(null, disposable))
    })).isTrue()
  }

  @Test
  fun `a frame guest has no project to close`() {
    val owner = MockProject(null, disposable)
    val options = OpenProjectTask {
      projectToClose = owner
      implOptions = OpenProjectImplOptions(frameOwner = owner)
    }
    assertThat(resolveProjectToClose(options, listOf(owner), lastFocusedProject = { owner })).isNull()
  }

  @Test
  fun `a project to close that is a frame guest stands for the owner of its frame`() {
    val owner = MockProject(null, disposable)
    val guest = MockProject(null, disposable)
    val ownerOf = { project: Project -> if (project === guest) owner else project }

    assertThat(resolveProjectToClose(OpenProjectTask { projectToClose = guest }, listOf(owner, guest), { null }, ownerOf)).isSameAs(owner)
    assertThat(resolveProjectToClose(OpenProjectTask {}, listOf(owner, guest), { null }, ownerOf)).isSameAs(owner)
    assertThat(resolveProjectToClose(OpenProjectTask {}, listOf(owner, guest), { guest }, ownerOf)).isSameAs(owner)
    assertThat(resolveProjectToClose(OpenProjectTask {}, emptyList(), { null }, ownerOf)).isNull()
  }

  @Test
  fun `the last focused project is read only when no project to close is given`() {
    val owner = MockProject(null, disposable)
    var reads = 0
    val lastFocusedProject = { reads++; null }

    assertThat(resolveProjectToClose(OpenProjectTask { projectToClose = owner }, listOf(owner), lastFocusedProject)).isSameAs(owner)
    assertThat(resolveProjectToClose(OpenProjectTask {}, emptyList(), lastFocusedProject)).isNull()
    assertThat(reads).isZero()
    assertThat(resolveProjectToClose(OpenProjectTask {}, listOf(owner), lastFocusedProject)).isSameAs(owner)
    assertThat(reads).isOne()
  }
}
