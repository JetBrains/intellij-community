// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Covers [registerProjectIdAlias]: Remote Development reconciles a project's identity between peers
 * (see [setNewProjectId]), and an id captured before the reconciliation must still resolve — without
 * disturbing the canonical id.
 */
@TestApplication
class ProjectIdAliasTest {

  private val project get() = ProjectManager.getInstance().defaultProject

  @AfterEach
  fun tearDown() {
    unregisterProjectId(project)
  }

  @Test
  fun `an alias resolves to the project while the canonical id stays adopted`() {
    val originalId = ProjectId.deserializeFromString("original-id")
    val adoptedId = ProjectId.deserializeFromString("adopted-id")

    setNewProjectId(project, originalId)
    assertSame(project, originalId.findProjectOrNull())

    setNewProjectId(project, adoptedId)
    registerProjectIdAlias(project, aliasId = originalId)

    assertEquals(adoptedId, project.projectId())
    assertSame(project, adoptedId.findProjectOrNull())
    assertSame(project, originalId.findProjectOrNull())
  }

  @Test
  fun `unregistering the project drops its aliases`() {
    val originalId = ProjectId.deserializeFromString("stale-original-id")
    val adoptedId = ProjectId.deserializeFromString("current-adopted-id")

    setNewProjectId(project, adoptedId)
    registerProjectIdAlias(project, aliasId = originalId)

    unregisterProjectId(project)

    assertNull(originalId.findProjectOrNull())
    assertNull(adoptedId.findProjectOrNull())
  }

  @Test
  fun `a canonical id wins over an alias with the same value`() {
    val defaultProject = project
    val sharedId = ProjectId.deserializeFromString("shared-id")

    setNewProjectId(defaultProject, sharedId)
    registerProjectIdAlias(defaultProject, aliasId = sharedId)

    assertSame(defaultProject, sharedId.findProjectOrNull())
  }
}
