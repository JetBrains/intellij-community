// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

public class WinProcessManagerTest {
  @Test
  public void testGetWinProcessMachineType() {
    assumeTrue("Windows-only test", SystemInfo.isWindows);

    final int currentPid = WinProcessManager.getCurrentProcessId();
    final ProcessMachineType machineType = WinProcessManager.getProcessMachineType(currentPid);
    assertEquals(SystemInfo.is32Bit ? ProcessMachineType.I386 : ProcessMachineType.AMD64, machineType);
  }

  @Test
  public void testReadPeMachineType32bit() throws Exception {
    doTestReadPeMachineTypeOfBundledBinary("../win/fsnotifier.exe", ProcessMachineType.I386);
  }

  @Test
  public void testReadPeMachineType64bit() throws Exception {
    doTestReadPeMachineTypeOfBundledBinary("../win/fsnotifier64.exe", ProcessMachineType.AMD64);
  }

  @Test
  public void testReadPeMachineTypeUnknown() {
    //noinspection ResultOfMethodCallIgnored
    Assertions.assertThatExceptionOfType(IOException.class).isThrownBy(
      () -> doTestReadPeMachineTypeOfBundledBinary("../linux/fsnotifier", ProcessMachineType.UNKNOWN));
  }

  private static void doTestReadPeMachineTypeOfBundledBinary(@NotNull String binFileName,
                                                             @NotNull ProcessMachineType expected) throws Exception {
    final File binFile = PathManager.findBinFile(binFileName);
    assertNotNull("Couldn't find bundled " + binFileName, binFile);
    final ProcessMachineType machineType = WinProcessManager.readPeMachineType(binFile);
    assertEquals(expected, machineType);
  }
}