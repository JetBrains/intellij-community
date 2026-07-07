// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
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
 * Pins that a child key inside a [SettingsMapKey] resolves through the same precedence as a plain key, within its
 * selected slot. The context chain comes first and the provider order second. So for a shared slot, a nested value at
 * `dir/lower` beats one at `project/higher`, `dir/higher` beats `dir/lower`, and a value only at the application
 * still resolves.
 */
@TestApplication
class SettingsNestedPrecedenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val profileKey = SettingsKey("test.np.profile", default = "default")
    private val profileData = SettingsMapKey("test.np.profileData", discriminatorKey = profileKey)
    private val contextWins = SettingsKey("test.np.contextWins", default = "contextWinsDefault", storageKey = profileData)
    private val providerWins = SettingsKey("test.np.providerWins", default = "providerWinsDefault", storageKey = profileData)
    private val deepFallback = SettingsKey("test.np.deepFallback", default = "deepFallbackDefault", storageKey = profileData)

    /**
     * Returns the stored value of [profileData] with only the "default" slot.
     *
     * @param children the identity and the string value of each child key in the slot
     */
    private fun slot(vararg children: Pair<String, String>): MapValue =
      MapValue(mapOf("default" to MapValue(children.associate { it.first to StringValue(it.second) })))
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val lower = TestLayerProvider()
  private val higher = TestLayerProvider()

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `nested keys resolve through context chain and provider order`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower, higher), disposable)

    val dir = VfsTestUtil.createFile(sourceRoot, "sub/anchor.txt").parent
    val app = SettingsContext.app()
    val proj = SettingsContext.project(project)
    val dirContext = readAction { SettingsContext.file(dir, project) }

    higher.put(proj, profileData.identity, slot(contextWins.identity to "projectHigher"))
    lower.put(dirContext, profileData.identity, slot(contextWins.identity to "dirLower", providerWins.identity to "dirLower"))
    higher.put(dirContext, profileData.identity, slot(providerWins.identity to "dirHigher"))
    lower.put(app, profileData.identity, slot(deepFallback.identity to "appLower"))

    awaitEquals("dirLower") { contextWins[dirContext] }    // dir/lower beats project/higher — context dominates
    awaitEquals("dirHigher") { providerWins[dirContext] }  // dir/higher beats dir/lower — provider order within a context
    awaitEquals("appLower") { deepFallback[dirContext] }   // only application/lower defines it
  }
}
