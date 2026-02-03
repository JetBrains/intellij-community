// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.impl.ModalityStateEx
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import org.junit.jupiter.api.Test
import kotlin.test.assertSame

@Suppress("UsagesOfObsoleteApi")
@TestApplication
class DefaultModalityTest : CancellationTest() {

  private fun createFakeModality(): ModalityState {
    return (ModalityState.nonModal() as ModalityStateEx).appendJob(Job())
  }

  private fun assertModality(modality: ModalityState?) {
    assertSame(modality, ModalityState.defaultModalityState())
  }

  @Test
  fun `outside contexts`() {
    assertModality(ModalityState.nonModal())
  }

  @Test
  fun `indicator context`() {
    val outerModality = createFakeModality()
    val nestedModality = createFakeModality()
    val nestedModality2 = createFakeModality()
    withIndicator(EmptyProgressIndicator(outerModality)) {
      assertModality(outerModality)
      withIndicator(EmptyProgressIndicator(nestedModality)) {
        assertModality(outerModality) // weird, but this is how it behaved from the very beginning
        runBlockingCancellable {
          assertSame(outerModality, currentCoroutineContext().contextModality()) // inherits indicator modality
          assertModality(outerModality) // via implicit blocking context
          coroutineToIndicator { // inherits coroutine modality
            assertModality(outerModality)
            withIndicator(EmptyProgressIndicator(nestedModality2)) {
              assertModality(outerModality) // because coroutineToIndicator installs a top level indicator
            }
            assertModality(outerModality)
          }
          assertModality(outerModality)
          withIndicator(EmptyProgressIndicator(nestedModality2)) {
            assertModality(nestedModality2) // because this is a top-level indicator
          }
          assertModality(outerModality)
        }
        assertModality(outerModality)
      }
      assertModality(outerModality)
    }
  }

  @Test
  fun `indicator context within thread context`() {
    val outerModality = createFakeModality()
    val nestedModality = createFakeModality()
    val nestedModality2 = createFakeModality()
    installThreadContext(outerModality.asContextElement()) {
      assertModality(outerModality)
      withIndicator(EmptyProgressIndicator(nestedModality)) {
        assertModality(nestedModality) // IJPL-155640
        runBlockingCancellable {
          assertSame(nestedModality, currentCoroutineContext().contextModality()) // inherits indicator modality
          assertModality(nestedModality) // via implicit blocking context
          coroutineToIndicator {
            assertModality(nestedModality)
            withIndicator(EmptyProgressIndicator(nestedModality2)) {
              assertModality(nestedModality)  // because coroutineToIndicator installs a top level indicator
            }
            assertModality(nestedModality)
          }
          assertModality(nestedModality)
          withIndicator(EmptyProgressIndicator(nestedModality2)) {
            assertModality(nestedModality2) // because this is a top-level indicator
          }
          assertModality(nestedModality)
        }
        assertModality(nestedModality)
      }
      assertModality(outerModality)
    }
  }
}
