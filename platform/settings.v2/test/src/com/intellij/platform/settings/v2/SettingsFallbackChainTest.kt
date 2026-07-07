// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Checks that a key can fall back to another key instead of a constant. The [defaultFactory][SettingsKey] of a key
 * gets the resolution context, so the default can read another key at that context.
 *
 * Two slotted keys with their own discriminators and a plain key form the chain
 * `slottedB → slottedA → direct → constant default`. A read of `slottedB` with different discriminator overlays walks
 * the chain down to the first level that holds a value for that context.
 */
@TestApplication
class SettingsFallbackChainTest {
  companion object {
    // Bottom of the chain: a plain key with a constant default.
    private val direct = SettingsKey("test.chain.direct", default = "constDefault")

    // Middle: sliced by discA; defaults to the plain key when its selected slot is empty.
    private val discA = SettingsKey("test.chain.discA", default = "aDef")
    private val dataA = SettingsMapKey("test.chain.dataA", discriminatorKey = discA)
    private val slottedA = SettingsKey("test.chain.slottedA", StringSerializer, storageKey = dataA, defaultFactory = { direct[it] })

    // Top: sliced by its own discB; defaults to the middle key when its selected slot is empty.
    private val discB = SettingsKey("test.chain.discB", default = "bDef")
    private val dataB = SettingsMapKey("test.chain.dataB", discriminatorKey = discB)
    private val slottedB = SettingsKey("test.chain.slottedB", StringSerializer, storageKey = dataB, defaultFactory = { slottedA[it] })
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  @Test
  fun `reading the top key resolves at the first chain level that holds a value for the context`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    // One fixed stored state, one value at each chain level; only the read context's discriminators vary below.
    direct[app] = "fromDirect"
    slottedA[app.access { this[discA] = "a1" }] = "fromA"
    slottedB[app.access { this[discB] = "b1" }] = "fromB"

    awaitEquals("fromB") { slottedB[app.access { this[discB] = "b1" }] }  // top slot filled → top value

    // Top slot empty for discB="bX", middle slot filled for discA="a1" → falls to the middle value.
    awaitEquals("fromA") {
      slottedB[app.access { this[discB] = "bX"; this[discA] = "a1" }]
    }

    // Both slotted levels empty for these discriminators → falls to the plain key's stored value.
    awaitEquals("fromDirect") {
      slottedB[app.access { this[discB] = "bX"; this[discA] = "aX" }]
    }
  }

  @Test
  fun `the chain bottoms out at the constant default when nothing downstream is stored`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    // Only the top slot "b1" is stored; neither the middle key nor the plain key is written anywhere.
    slottedB[app.access { this[discB] = "b1" }] = "fromB"

    awaitEquals("fromB") { slottedB[app.access { this[discB] = "b1" }] }  // stored slot resolves

    // Switching to an empty top slot walks the whole chain: middle slot empty, plain key unset → constant default.
    awaitEquals("constDefault") { slottedB[app.access { this[discB] = "bX" }] }
  }

  @Test
  fun `changing the stored discriminator values reselects the slot and walks the chain`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    // Fixed slot contents at each level; from here on only the discriminators' stored values change, no pinning.
    direct[app] = "fromDirect"
    slottedA[app.access { this[discA] = "a1" }] = "fromA"
    slottedB[app.access { this[discB] = "b1" }] = "fromB"

    discB[app] = "b1"
    awaitEquals("fromB") { slottedB[app] }  // discB selects the filled top slot

    // Move discB off the filled slot: the top slot is empty, discA still defaults → middle slot empty → plain value.
    discB[app] = "bX"
    awaitEquals("fromDirect") { slottedB[app] }

    // Point discA at the filled middle slot: the empty top slot now falls to the middle value.
    discA[app] = "a1"
    awaitEquals("fromA") { slottedB[app] }

    // Bring discB back onto the filled top slot: the top value wins again over the middle.
    discB[app] = "b1"
    awaitEquals("fromB") { slottedB[app] }
  }
}
