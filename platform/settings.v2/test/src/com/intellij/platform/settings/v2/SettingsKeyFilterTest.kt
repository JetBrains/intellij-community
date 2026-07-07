// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the [SettingsKey.contextFilter] and the [SettingsKey.layerFilter] of a key, on a read and on a write. The
 * layers come from committed JSON that the production codec parses, so each test asserts a value that a config file
 * held.
 *
 * A context filter confines a key to some [SettingsContextKind] values. A layer filter confines a key to named layers.
 * On a read, both filters drop a contribution that would win otherwise. On a write, both steer the write to an
 * accepted layer, or refuse it.
 */
@TestApplication
class SettingsKeyFilterTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    // Application-only: rejects every narrower context. Its unfiltered twin [plain] is the control.
    private val scope = SettingsKey("test.filter.scope", default = "def", contextFilter = { it.kind == SettingsContextKind.APPLICATION })
    private val plain = SettingsKey("test.filter.plain", default = "def")

    // Confined to the "allowed" layer. Its unfiltered twin [shared] is the control.
    private val exclusive = SettingsKey("test.filter.exclusive", default = "def", layerFilter = { it is TestLayerProvider && it.id == "allowed" })
    private val shared = SettingsKey("test.filter.shared", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `context filter confines a key to the application, dropping narrower layers on read`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()
    val file = VfsTestUtil.createFile(sourceRoot, "sub/a.txt")
    val fileCtx = readAction { SettingsContext.file(file, project) }

    provider.seed(app, decode("app.json"))
    provider.seed(SettingsContext.project(project), decode("project.json"))
    provider.seed(fileCtx, decode("file.json"))

    awaitEquals("app") { scope[fileCtx] }   // file, its directories and the project are all rejected → resolves at app
    awaitEquals("file") { plain[fileCtx] }  // unfiltered control: the narrowest layer wins as usual
  }

  @Test
  fun `context filter widens a write from a narrower context up to the application`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()
    val file = VfsTestUtil.createFile(sourceRoot, "sub/b.txt")
    val fileCtx = readAction { SettingsContext.file(file, project) }

    // Writing the app-only key from a file context is not rejected: the walk passes over the rejected file,
    // directory and project nodes and lands in the application layer.
    scope[fileCtx] = "written"
    awaitEquals("written") { scope[app] }      // persisted at the application
    awaitEquals("written") { scope[fileCtx] }  // and resolves back through the chain from the file context
  }

  @Test
  fun `layer filter ignores an excluded layer on read even when it outranks the allowed one`(): Unit = runBlocking {
    val allowed = TestLayerProvider("allowed")
    val excluded = TestLayerProvider("excluded")
    // "excluded" is last → highest read priority (the vault reverses provider order); it would win without the filter.
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(allowed, excluded), disposable)
    val app = SettingsContext.app()

    allowed.seed(app, decode("allowedLayer.json"))
    excluded.seed(app, decode("excludedLayer.json"))

    awaitEquals("allowed") { exclusive[app] }  // the higher-ranked excluded layer is filtered out
    awaitEquals("excluded") { shared[app] }    // unfiltered control: the higher-ranked layer wins as usual
  }

  @Test
  fun `layer filter skips an excluded layer on write, steering it to the allowed one`(): Unit = runBlocking {
    val allowed = TestLayerProvider("allowed")
    val excluded = TestLayerProvider("excluded")
    // "excluded" is first in write iteration → it would take the write if the filter did not skip it.
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(excluded, allowed), disposable)
    val app = SettingsContext.app()

    exclusive[app] = "written"
    // The read is confined to the allowed layer, so seeing "written" proves the write landed there rather than in excluded.
    awaitEquals("written") { exclusive[app] }
  }

  private fun decode(name: String): MapValue =
    JsonSettingsCodec.decode(Files.readString(Path.of(testResourcePath("keyFilter/$name"))))
}
