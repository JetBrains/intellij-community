// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@TestApplication
class ImplicitReadTest {
  companion object {
    const val DISABLE_IR_PROPERTY = "idea.disable.implicit.read.on.edt"
  }

  @Test
  @EnabledIfSystemProperty(named = DISABLE_IR_PROPERTY, matches = "^true$", disabledReason = "Need this property set to enable functionality under test")
  fun implicitReadOnEDTIsDisabled() {
      val app = ApplicationManager.getApplication()
      val f = CompletableFuture<Boolean>()
      SwingUtilities.invokeLater {
        f.complete(app.isReadAccessAllowed)
      }
      assertFalse(f.get())
  }

  @Test
  @EnabledIfSystemProperty(named = DISABLE_IR_PROPERTY, matches = "^true$", disabledReason = "Need this property set to enable functionality under test")
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
  @EnabledIfSystemProperty(named = DISABLE_IR_PROPERTY, matches = "^true$", disabledReason = "Need this property set to enable functionality under test")
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
  @EnabledIfSystemProperty(named = DISABLE_IR_PROPERTY, matches = "^true$", disabledReason = "Need this property set to enable functionality under test")
  fun implicitReadWorksInApplicationActionAlways() {
    val app = ApplicationManager.getApplication()
    val f = CompletableFuture<Boolean>()
    app.invokeLater {
      f.complete(app.isReadAccessAllowed)
    }
    assertTrue(f.get())
  }
}