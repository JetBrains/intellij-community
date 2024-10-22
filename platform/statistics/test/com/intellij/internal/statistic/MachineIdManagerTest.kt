// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MachineIdManagerTest {
  @Test fun smoke() {
    assertThat(MachineIdManager.getAnonymizedMachineId("test"))
      .isNotNull
  }

  @Test fun contract() {
    assertThrows(IllegalArgumentException::class.java) {
      MachineIdManager.getAnonymizedMachineId("")
    }
  }
}
