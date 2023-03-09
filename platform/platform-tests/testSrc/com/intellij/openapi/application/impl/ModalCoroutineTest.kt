// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach

@TestApplication
abstract class ModalCoroutineTest {

  @BeforeEach
  @AfterEach
  fun checkNotModal() {
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        assertFalse(LaterInvocator.isInModalContext()) {
          "Expect no modal entries. Probably some of the previous tests didn't left their entries. " +
          "Top entry is: " + LaterInvocator.getCurrentModalEntities().firstOrNull()
        }
      }
    }
  }
}
