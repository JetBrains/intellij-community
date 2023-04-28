// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class Junit5LaterInvocatorTest {
  @Test
  @Timeout(60)
  fun invokeLaterAlwaysSchedulesFlush() {
    runBlocking {
      withContext(Dispatchers.EDT) {
        val executed = AtomicBoolean()
        repeat(1_000_000) {
          executed.set(false)
          @Suppress("ForbiddenInSuspectContextMethod")
          ApplicationManager.getApplication().invokeLater {
            executed.set(true)
          }
          EDT.dispatchAllInvocationEvents()
          check(executed.get())
        }
      }
    }
  }
}