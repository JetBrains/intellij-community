// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.function.BiConsumer

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
      registerFromOwnClassLoader(job, parent).ensureCollected()
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

  /** Registers [job] from a class of a class loader of its own, as a plugin does, and answers a watcher of that class loader. */
  private fun registerFromOwnClassLoader(job: Job, parent: Disposable): GCWatcher {
    val loader = CallerClassLoader(CancelOnDisposeTest::class.java.classLoader)
    @Suppress("UNCHECKED_CAST")
    val caller = loader.loadClass(CancelOnDisposeCaller::class.java.name).getConstructor().newInstance() as BiConsumer<Job, Disposable>
    caller.accept(job, parent)
    return GCWatcher.tracking(loader)
  }
}

/** Registers a job with [cancelOnDispose]. [CallerClassLoader] defines its own copy of this class. */
internal class CancelOnDisposeCaller : BiConsumer<Job, Disposable> {
  override fun accept(job: Job, parent: Disposable) {
    job.cancelOnDispose(parent)
  }
}

/** Defines [CancelOnDisposeCaller] itself and delegates every other class to its parent, as a plugin class loader does. */
private class CallerClassLoader(parent: ClassLoader) : ClassLoader(parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    if (name != CancelOnDisposeCaller::class.java.name) {
      return super.loadClass(name, resolve)
    }
    return synchronized(getClassLoadingLock(name)) {
      findLoadedClass(name) ?: run {
        val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
        defineClass(name, bytes, 0, bytes.size)
      }
    }
  }
}
