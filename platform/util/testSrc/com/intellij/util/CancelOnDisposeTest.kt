// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.callUnderOwnClassLoader
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * A `cancelOnDispose` registration names its caller, and it keeps no class loader of that caller. A plugin that registers
 * a job under a parent of another plugin can then unload while the parent stays.
 */
internal class CancelOnDisposeTest {
  @Test
  fun `a registration names its caller`() {
    val parent = Disposer.newDisposable("the disposed parent")
    Disposer.dispose(parent)
    val job = Job()
    try {
      assertThatThrownBy { register(job, parent) }
        .isInstanceOf(IncorrectOperationException::class.java)
        .hasMessageContaining("Cancel on dispose spawned in `")
        .hasMessageContaining("${CancelOnDisposeTest::class.java.name}.register(")
    }
    finally {
      job.cancel()
    }
  }

  @Test
  fun `a live registration keeps no class loader of its caller`() {
    val parent = Disposer.newDisposable("the parent of a live registration")
    val job = Job()
    try {
      callUnderOwnClassLoader { job.cancelOnDispose(parent) }.loader.ensureCollected()
      assertThat(job.isActive).isTrue()
    }
    finally {
      Disposer.dispose(parent)
    }
    assertThat(job.isCancelled).isTrue()
  }

  private fun register(job: Job, parent: Disposable) {
    job.cancelOnDispose(parent)
  }
}
