// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.InvocationEvent

class PotemkinProgressTest {

  @Test
  fun `forced runnable is urgent`() {
    val event = InvocationEvent(Any(), object: SuvorovProgress.ForcedWriteActionRunnable() {
      override fun run() {
      }
    })
    assertThat(EventStealer.isUrgentInvocationEvent(event)).isTrue
  }

  @Test
  fun `generic invocation event is not urgent`() {
    val event = InvocationEvent(Any()) { }
    assertThat(EventStealer.isUrgentInvocationEvent(event)).isFalse
  }


  @Test
  fun `native mac menu runnable is not urgent`() {
    val event = InvocationEvent(Any(), object: Runnable {
      override fun run() {
      }

      override fun toString(): String {
        return $$$"com.intellij.platform.ide.menu.MacNativeActionMenuKt$$Lambda"
      }
    })
    assertThat(EventStealer.isUrgentInvocationEvent(event)).isFalse
  }
}