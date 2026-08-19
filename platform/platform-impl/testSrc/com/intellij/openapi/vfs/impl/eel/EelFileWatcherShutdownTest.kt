// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
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

  private companion object {
    val TEST_DESCRIPTOR = object : EelDescriptor {
      override val name: String = "test"
      override val osFamily: EelOsFamily = EelOsFamily.Posix
    }
  }
}
