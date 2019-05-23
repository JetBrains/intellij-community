// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.MachineType;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class UnixProcessManagerTest {
  @Test
  public void testGetLinuxProcessMachineType() {
    assumeTrue("Linux-only test", SystemInfo.isLinux);

    final int currentPid = UnixProcessManager.getCurrentProcessId();
    final MachineType machineType = UnixProcessManager.getProcessMachineType(currentPid);
    assertEquals(SystemInfo.is32Bit ? MachineType.I386 : MachineType.AMD64, machineType);
  }
}