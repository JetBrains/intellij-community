// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.notification.NotificationListener
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class EelFileWatcherShutdownTest {
  @Test
  fun `dispose does not reset remote watches`() {
    val resetRequests = mutableListOf<Boolean>()
    val watcher = EelFileWatcher()
    watcher.addWatchedEelForTest(TEST_DESCRIPTOR, resetRequests::add)

    watcher.dispose()

    assertThat(resetRequests).containsExactly(false)
  }

  @Test
  @RegistryKey(key = "use.eel.file.watcher", value = "true")
  fun `setting roots during shutdown does not reset remote watches`() {
    val resetRequests = mutableListOf<Boolean>()
    val watcher = EelFileWatcher()
    watcher.startup()
    watcher.addWatchedEelForTest(TEST_DESCRIPTOR, resetRequests::add)

    watcher.setWatchRoots(emptyList(), emptyList(), true)

    assertThat(resetRequests).containsExactly(false)
  }

  @Test
  fun `live root removal resets remote watches`() {
    val resetRequests = mutableListOf<Boolean>()
    val watcher = EelFileWatcher()
    watcher.addWatchedEelForTest(TEST_DESCRIPTOR, resetRequests::add)

    watcher.retainWatchedEelsForTest(emptySet())

    assertThat(resetRequests).containsExactly(true)
  }

  @Test
  fun `restart cancels the old watch without a remote reset`() {
    val resetRequests = mutableListOf<Boolean>()
    val watcher = EelFileWatcher()
    watcher.initialize(SILENT_SINK)
    watcher.addWatchedEelForTest(TEST_DESCRIPTOR, resetRequests::add)

    // No environment is behind the test descriptor, so the new watch fails with a warning and refreshes nothing.
    val job = checkNotNull(watcher.restartWatching(TEST_DESCRIPTOR))
    runBlocking { job.join() }

    assertThat(resetRequests).containsExactly(false)
  }

  @Test
  fun `restart of an unwatched descriptor returns no job`() {
    val watcher = EelFileWatcher()

    assertThat(watcher.restartWatching(TEST_DESCRIPTOR)).isNull()
  }

  private companion object {
    val TEST_DESCRIPTOR = object : EelDescriptor {
      override val name: String = "test"
      override val osFamily: EelOsFamily = EelOsFamily.Posix
    }

    /** A failed start reports its roots to the sink. */
    val SILENT_SINK = object : FileWatcherNotificationSink {
      override fun notifyMapping(mapping: Collection<Pair<String, String>>) {}
      override fun notifyDirtyPath(path: String) {}
      override fun notifyPathCreatedOrDeleted(path: String) {}
      override fun notifyDirtyDirectory(path: String) {}
      override fun notifyDirtyPathRecursive(path: String) {}
      override fun notifyReset(path: String?) {}
      override fun notifyUserOnFailure(cause: String, listener: NotificationListener?) {}
    }
  }
}
