// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.callUnderOwnClassLoader
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Predicate

/**
 * A `cancelOnDispose` registration names its caller, and it keeps no class loader of that caller. A plugin that registers
 * a job under a parent of another plugin can then unload while the parent stays.
 */
internal class CancelOnDisposeTest {
  @Test
  fun `a registration names its caller`() {
    val parent = Disposer.newDisposable("the parent of a named registration")
    val job = Job()
    try {
      register(job, parent)

      val names = ArrayList<String>()
      Disposer.getTree().assertNoReferenceKeptInTree(Predicate {
        val name = it.toString()
        if (name.startsWith("Cancel on dispose spawned in `")) names.add(name)
        false
      })
      assertThat(names).anySatisfy {
        assertThat(it).contains("${CancelOnDisposeTest::class.java.name}.register(")
      }
    }
    finally {
      Disposer.dispose(parent)
    }
  }

  @Test
  fun `a registration under a disposed parent cancels the job`() {
    val parent = Disposer.newDisposable("the disposed parent")
    Disposer.dispose(parent)
    val job = Job()
    job.cancelOnDispose(parent)
    assertThat(job.isCancelled).isTrue()
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
