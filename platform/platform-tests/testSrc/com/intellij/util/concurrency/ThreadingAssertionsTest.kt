// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


@TestApplication
internal class ThreadingAssertionsTest {

  @Test
  fun softAssertBackgroundThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.EDT) {
      LoggedErrorProcessor.executeAndReturnLoggedError {
        ThreadingAssertions.softAssertBackgroundThread()
      }
    }
    assertThat(err).isNotNull.hasMessageContaining("Access from Event Dispatch Thread (EDT) is not allowed")
  }

  @Test
  fun assertBackgroundThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.EDT) {
      runCatching {
        ThreadingAssertions.assertBackgroundThread()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Access from Event Dispatch Thread (EDT) is not allowed")
  }

  @Test
  fun softAssertEventDispatchThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      LoggedErrorProcessor.executeAndReturnLoggedError {
        ThreadingAssertions.softAssertEventDispatchThread()
      }
    }
    assertThat(err).isNotNull.hasMessageContaining("Access is allowed from Event Dispatch Thread (EDT) only")
  }

  @Test
  fun assertEventDispatchThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertEventDispatchThread()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Access is allowed from Event Dispatch Thread (EDT) only")
  }

  @Test
  fun softAssertReadAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      LoggedErrorProcessor.executeAndReturnLoggedError {
        ThreadingAssertions.softAssertReadAccess()
      }
    }
    assertThat(err).isNotNull.hasMessageContaining("Read access is allowed from inside read-action only")
  }

  @Test
  fun assertReadAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertReadAccess()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Read access is allowed from inside read-action only")
  }

  @Test
  fun assertWriteAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertWriteAccess()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Write access is allowed inside write-action only")
  }

  @Test
  fun assertWriteIntentReadAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertWriteIntentReadAccess()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Access is allowed from write thread only")
  }

  @Test
  fun assertNoOwnReadAccessRead(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        readAction {
          ThreadingAssertions.assertNoOwnReadAccess()
        }
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Must not execute inside read action")
  }

  @Test
  fun assertNoOwnReadAccessWrite(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        writeAction {
          ThreadingAssertions.assertNoReadAccess()
        }
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Must not execute inside read action")
  }

}
