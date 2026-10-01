// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EelDataTest {
  @Test
  fun `flat roots covered by a recursive root are omitted`() {
    val data = EelData(TEST_DESCRIPTOR)
    val path = EelPath.parse("/project", TEST_DESCRIPTOR)
    data.recursive.add(path)
    data.flat.add(path)
    data.flat.add(EelPath.parse("/project/nested", TEST_DESCRIPTOR))
    val independentFlatPath = EelPath.parse("/another-project", TEST_DESCRIPTOR)
    data.flat.add(independentFlatPath)

    val watchedPaths = data.getWatchedPaths()
    assertThat(watchedPaths).hasSize(2)
    assertThat(watchedPaths.single { it.path == path }.recursive).isTrue()
    assertThat(watchedPaths.single { it.path == independentFlatPath }.recursive).isFalse()
  }

  private companion object {
    val TEST_DESCRIPTOR = object : EelDescriptor {
      override val name: String = "test"
      override val osFamily: EelOsFamily = EelOsFamily.Posix
    }
  }
}
