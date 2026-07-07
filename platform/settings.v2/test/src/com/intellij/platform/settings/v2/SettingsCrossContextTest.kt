// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Checks the resolution across the context chain. The test writes values at the application, the project and a
 * directory, and reads them at the directory. A narrower context shadows a wider one. A missing value falls back to
 * the nearest context that defines it, or to the default of the key.
 */
@TestApplication
class SettingsCrossContextTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `a narrower context shadows a wider one, missing values fall back through the chain`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)

    val dir = VfsTestUtil.createFile(sourceRoot, "sub/anchor.txt").parent
    val app = SettingsContext.app()
    val proj = SettingsContext.project(project)
    val dirContext = readAction { SettingsContext.file(dir, project) }

    val shadowed = SettingsKey("test.cc.shadowed", default = "shadowedDefault")
    val fromProject = SettingsKey("test.cc.fromProject", default = "fromProjectDefault")
    val fromApp = SettingsKey("test.cc.fromApp", default = "fromAppDefault")
    val missing = SettingsKey("test.cc.missing", default = "missingDefault")

    shadowed[app] = "app"
    shadowed[proj] = "project"
    shadowed[dirContext] = "dir"
    fromProject[proj] = "project"
    fromApp[app] = "app"

    awaitEquals("dir") { shadowed[dirContext] }        // the directory value wins over project and app
    awaitEquals("project") { fromProject[dirContext] } // no directory value → inherited from the project
    awaitEquals("app") { fromApp[dirContext] }         // defined only at the application → inherited
    assertEquals("missingDefault", missing[dirContext]) // defined nowhere in the chain → the key's default
  }
}
