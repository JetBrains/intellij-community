// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Checks from end to end how the real providers route a write and a read across the three writable scopes. The test
 * writes the same key once at the application, the project and the file scope of a directory. Each write lands in its
 * own layer. A read at each scope returns the value of that scope, so the narrower scope shadows the wider one.
 */
@TestApplication
class SettingsLayerScopeWriteTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.multiscope.value", default = "")
  }

  private val project get() = projectFixture.get()
  private val dir get() = sourceRootFixture.get().virtualFile

  @Test
  fun `the same key written at app, project and file scope reads back per scope, narrower shadowing wider`(): Unit = runBlocking {
    val appCtx = SettingsContext.app()
    val projectCtx = SettingsContext.project(project)
    val fileCtx = readAction { SettingsContext.file(dir, project) }

    key[appCtx] = "app"
    key[projectCtx] = "project"
    key[fileCtx] = "file"

    assertEquals("app", key[appCtx])
    assertEquals("project", key[projectCtx])
    assertEquals("file", key[fileCtx])
  }
}
