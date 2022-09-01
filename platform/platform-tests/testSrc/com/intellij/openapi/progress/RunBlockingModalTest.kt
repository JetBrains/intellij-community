// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
class RunBlockingModalTest {

  @Test
  fun `normal completion`(): Unit = timeoutRunBlocking {
    val result = withContext(Dispatchers.EDT) {
      runBlockingModal(ModalTaskOwner.guess(), "") { 42 }
    }
    Assertions.assertEquals(42, result)
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    val t: Throwable = object : Throwable() {}
    withContext(Dispatchers.EDT) {
      val thrown = assertThrows<Throwable> {
        runBlockingModal<Unit>(ModalTaskOwner.guess(), "") {
          throw t // fail the scope
        }
      }
      Assertions.assertSame(t, thrown)
    }
  }
}
