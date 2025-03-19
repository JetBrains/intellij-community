// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.*
import java.io.DataInput
import java.io.DataOutput
import kotlin.coroutines.EmptyCoroutineContext

internal class GraveImplTest : LightPlatformTestCase() {

  fun testBuryExhumeZombie() = runBlocking {
    withGrave { grave ->
      val expected = FingerprintedZombieImpl(1, TestZombie(2))
      grave.buryZombie(0, expected)
      val actual = grave.exhumeZombie(0)
      assertEquals(expected, actual)
    }
  }

  fun testBuryNull() = runBlocking {
    withGrave { grave ->
      grave.buryZombie(0, FingerprintedZombieImpl(1, TestZombie(2)))
      grave.buryZombie(0, null)
      assertNull(grave.exhumeZombie(0))
    }
  }

  fun testBuryTwice() = runBlocking {
    withGrave { grave ->
      grave.buryZombie(0, FingerprintedZombieImpl(1, TestZombie(2)))
      val expected = FingerprintedZombieImpl(3, TestZombie(4))
      grave.buryZombie(0, expected)
      val actual = grave.exhumeZombie(0)
      assertEquals(expected, actual)
    }
  }

  private suspend fun withGrave(action: suspend (Grave<TestZombie>) -> Unit) {
    withContext(Dispatchers.IO) {
      @Suppress("SSBasedInspection")
      val coroutineScope = CoroutineScope(EmptyCoroutineContext)
      val grave = GraveImpl("test-grave", TestNecromancy, project, coroutineScope)
      try {
        action(grave)
      } finally {
        coroutineScope.cancel()
      }
    }
  }

  private data class TestZombie(val value: Int) : Zombie

  private object TestNecromancy : Necromancy<TestZombie> {
    override fun spellLevel(): Int = 0
    override fun exhumeZombie(grave: DataInput): TestZombie = TestZombie(grave.readInt())
    override fun buryZombie(grave: DataOutput, zombie: TestZombie) = grave.writeInt(zombie.value)
    override fun isDeepBury(): Boolean = false
  }
}
