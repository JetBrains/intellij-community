// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.application.PathManager;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author eldar
 */
public class ExecutableFileFormatUtilTest {

  @Test
  public void testReadElfMachineType32bit() throws Exception {
    doTestReadElfMachineTypeOfBundledBinary("../linux/fsnotifier", MachineType.I386);
  }

  @Test
  public void testReadElfMachineType64bit() throws Exception {
    doTestReadElfMachineTypeOfBundledBinary("../linux/fsnotifier64", MachineType.AMD64);
  }

  @Test
  public void testReadPeMachineType32bit() throws Exception {
    doTestReadPeMachineTypeOfBundledBinary("../win/fsnotifier.exe", MachineType.I386);
  }

  @Test
  public void testReadPeMachineType64bit() throws Exception {
    doTestReadPeMachineTypeOfBundledBinary("../win/fsnotifier64.exe", MachineType.AMD64);
  }

  @Test
  public void testReadPeMachineTypeUnknown() {
    //noinspection ResultOfMethodCallIgnored
    Assertions.assertThatExceptionOfType(IOException.class).isThrownBy(
      () -> doTestReadPeMachineTypeOfBundledBinary("../linux/fsnotifier", MachineType.UNKNOWN));
  }

  @Test
  public void testReadElfMachineTypeUnknown() {
    //noinspection ResultOfMethodCallIgnored
    Assertions.assertThatExceptionOfType(IOException.class).isThrownBy(
      () -> doTestReadElfMachineTypeOfBundledBinary("../win/fsnotifier.exe", MachineType.UNKNOWN));
  }

  private static void doTestReadPeMachineTypeOfBundledBinary(@NotNull String binFileName,
                                                             @NotNull MachineType expected) throws Exception {
    final File binFile = PathManager.findBinFile(binFileName);
    assertNotNull("Couldn't find bundled " + binFileName, binFile);
    final MachineType machineType = ExecutableFileFormatUtil.readPeMachineType(binFile);
    assertEquals(expected, machineType);
  }

  private static void doTestReadElfMachineTypeOfBundledBinary(@NotNull String binFileName,
                                                              @NotNull MachineType expected) throws Exception {
    final File binFile = PathManager.findBinFile(binFileName);
    assertNotNull("Couldn't find bundled " + binFileName, binFile);
    final MachineType machineType = ExecutableFileFormatUtil.readElfMachineType(binFile);
    assertEquals(expected, machineType);
  }
}
