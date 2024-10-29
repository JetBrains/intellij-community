// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import org.junit.jupiter.api.Test
import java.io.*
import kotlin.test.assertEquals

internal class LimbedNecromancyTest {

  @Test
  fun testSerde() {
    val expected = listOf(0, 1, 2, 3, 4)
    val zombie = TestLimbedZombie(expected)
    val baos = ByteArrayOutputStream()
    DataOutputStream(baos).use { output ->
      TestLimbedNecromancy.buryZombie(output, zombie)
    }
    val actual = DataInputStream(ByteArrayInputStream(baos.toByteArray())).use { input ->
      TestLimbedNecromancy.exhumeZombie(input)
    }.limbs()
    assertEquals(expected, actual)
  }

  private class TestLimbedZombie(limbs: List<Int>) : LimbedZombie<Int>(limbs)

  private object TestLimbedNecromancy : LimbedNecromancy<TestLimbedZombie, Int>(0) {
    override fun buryLimb(grave: DataOutput, limb: Int) = grave.writeInt(limb)
    override fun exhumeLimb(grave: DataInput): Int = grave.readInt()
    override fun formZombie(limbs: List<Int>): TestLimbedZombie = TestLimbedZombie(limbs)
  }
}
