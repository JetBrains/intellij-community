// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.Disposable
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.IgnoredTraverseEntry
import com.intellij.util.ref.IgnoredTraverseReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

@TestApplication
class LeakHunterIgnoreReferencesTest {
  @Test
  fun `leaked disposable ignored`(): Unit = timeoutRunBlocking(60.seconds) {
    val ref = ReferenceToDisposable(TestDisposable())
    assertThrows<AssertionError> { checkReferenced(ref, listOf()) }
    assertDoesNotThrow { checkReferenced(ref, listOf(IgnoredTraverseReference("com.intellij.openapi.util.ReferenceToDisposable.ref", -1))) }
  }
}

fun checkReferenced(root: Any, ignoredTraverseEntries: List<IgnoredTraverseEntry>) {
  val rootSupplier: Supplier<Map<Any, String>> = Supplier {
    mapOf(root to "root")
  }
  LeakHunter.checkLeak(rootSupplier, TestDisposable::class.java, ignoredTraverseEntries) { true }
}

class TestDisposable : Disposable {
  override fun dispose() {}
}

class ReferenceToDisposable(val ref: Disposable)