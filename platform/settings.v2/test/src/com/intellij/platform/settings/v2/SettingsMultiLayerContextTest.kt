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
import org.junit.jupiter.api.Test

/**
 * Pins the precedence of the context chain over the locality and the provider order (ADR 0073). A narrower context
 * wins first. Within a context, the higher-ordered provider wins, so a value at `dir/lower` beats one at
 * `project/higher`. A narrower context also beats a narrower locality of a wider context.
 */
@TestApplication
class SettingsMultiLayerContextTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val lower = TestLayerProvider()
  private val higher = TestLayerProvider()

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `context dominates provider order, with fallback across both`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower, higher), disposable)

    val dir = VfsTestUtil.createFile(sourceRoot, "sub/anchor.txt").parent
    val app = SettingsContext.app()
    val proj = SettingsContext.project(project)
    val dirContext = readAction { SettingsContext.file(dir, project) }

    // narrower context beats a higher provider in a wider context
    val contextWins = SettingsKey("test.mlc.contextWins", default = "contextWinsDefault")
    higher.seed(proj, contextWins, "projectHigher")
    lower.seed(dirContext, contextWins, "dirLower")

    // within the same context, the higher provider wins
    val providerWins = SettingsKey("test.mlc.providerWins", default = "providerWinsDefault")
    higher.seed(dirContext, providerWins, "dirHigher")
    lower.seed(dirContext, providerWins, "dirLower")

    // project beats application when the directory has nothing
    val midChain = SettingsKey("test.mlc.midChain", default = "midChainDefault")
    higher.seed(proj, midChain, "projectHigher")
    lower.seed(app, midChain, "appLower")

    // deepest fallback: only the application/lower layer defines it
    val deepFallback = SettingsKey("test.mlc.deepFallback", default = "deepFallbackDefault")
    lower.seed(app, deepFallback, "appLower")

    awaitEquals("dirLower") { contextWins[dirContext] }
    awaitEquals("dirHigher") { providerWins[dirContext] }
    awaitEquals("projectHigher") { midChain[dirContext] }
    awaitEquals("appLower") { deepFallback[dirContext] }
  }

  @Test
  fun `a narrower context wins over a narrower locality of a wider context`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower), disposable)
    val app = SettingsContext.app()
    val proj = SettingsContext.project(project)
    val key = SettingsKey("test.mlc.contextOverLocality", default = "contextOverLocalityDefault")

    lower.seed(proj, key, "projectTeam", LayerLocality.TEAM)
    lower.seed(app, key, "appLocal", LayerLocality.LOCAL)
    // The vault takes the pushes in order, so once the application reads its local value, both pushes are in the model.
    awaitEquals("appLocal") { key[app] }

    awaitEquals("projectTeam") { key[proj] }
  }
}
