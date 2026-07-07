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
 * Pins the write routing: [SettingsVault.set] walks *up* the context chain, and the first layer that accepts wins. So
 * a write that a layer **refuses** at its context does not fail. It climbs to a wider context and silently widens its
 * scope. Here the only layer refuses one file context, so a write for that file lands at its directory and leaks to a
 * sibling file. With the normal providers the owning layer always accepts, so this does not occur in practice.
 * Whether such a write should fail is an open question of `settings-writes.spec.md`.
 */
@TestApplication
class SettingsWriteFallthroughTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.fallthrough.value", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @Test
  fun `a write the layer refuses at the file climbs to the directory and leaks to a sibling`(): Unit = runBlocking {
    val foo = VfsTestUtil.createFile(sourceRoot, "fallthrough/foo.txt")
    val dir = readAction { foo.parent }
    val bar = VfsTestUtil.createFile(dir, "bar.txt")
    val fooCtx = readAction { SettingsContext.file(foo, project) }
    val barCtx = readAction { SettingsContext.file(bar, project) }
    val dirCtx = readAction { SettingsContext.file(dir, project) }
    val rootCtx = readAction { SettingsContext.file(sourceRoot, project) }

    // The sole layer rejects a write to foo's own context but accepts any wider one.
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(RefusingProvider(refusedIdentity = fooCtx.identity)), disposable)

    key[fooCtx] = "x"

    assertEquals("x", key[dirCtx])   // refused at the file, the write climbed to the directory
    assertEquals("x", key[barCtx])   // a sibling that never set the key now reads it too — the leak
    assertEquals("x", key[fooCtx])   // and foo itself reads it, inherited from the directory
    assertEquals("def", key[rootCtx])  // the write stopped at the first accepting context, below the root
  }

  /** Offers every locality except at [refusedIdentity], to force the up-the-chain fall-through of the vault. */
  private class RefusingProvider(private val refusedIdentity: String) : LayerProvider {
    override fun availableLocalities(context: SettingsContext): Set<LayerLocality> =
      if (context.realContext.identity == refusedIdentity) emptySet() else LayerLocality.entries.toSet()
  }
}
