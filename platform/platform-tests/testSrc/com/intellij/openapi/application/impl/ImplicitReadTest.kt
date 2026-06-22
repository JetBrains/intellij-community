// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class ImplicitReadTest {
  @BeforeEach
  fun setup() {
    assertNotNull(IdeEventQueue.getInstance()) // ensure invokeLaters would go through our IdeEventQueue
  }

  @Test
  fun implicitReadOnEDTIsDisabled() {
      val app = ApplicationManager.getApplication()
      val f = CompletableFuture<Boolean>()
      SwingUtilities.invokeLater {
        f.complete(app.isReadAccessAllowed)
      }
      assertFalse(f.get())
  }

  @Test
  fun explicitReadWorkInReadActionOnEDT() {
    val app = ApplicationManager.getApplication()
    val f = CompletableFuture<Boolean>()
    SwingUtilities.invokeLater {
      app.runReadAction {
        f.complete(app.isReadAccessAllowed)
      }
    }
    assertTrue(f.get())
  }

  @Test
  fun explicitReadWorkInWriteActionOnEDT() {
    val app = ApplicationManager.getApplication()
    val f = CompletableFuture<Boolean>()
    SwingUtilities.invokeLater {
      app.runWriteAction {
        f.complete(app.isReadAccessAllowed)
      }
    }
    assertTrue(f.get())

  }

  @Test
  fun implicitReadWorksInApplicationActionAlways() {
    val app = ApplicationManager.getApplication()
    val f = CompletableFuture<Boolean>()
    app.invokeLater {
      f.complete(app.isReadAccessAllowed)
    }
    assertTrue(f.get())
  }
}