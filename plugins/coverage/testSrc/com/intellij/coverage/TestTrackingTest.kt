// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.rt.coverage.data.ProjectData
import org.junit.Assert
import org.junit.Test

class TestTrackingTest {

  /**
   * @see com.intellij.coverage.listeners.java.CoverageListener.getData
   */
  @Test
  fun `test ProjectData API compatibility`() {
    Assert.assertNull(ProjectData.getProjectData())
  }
}
