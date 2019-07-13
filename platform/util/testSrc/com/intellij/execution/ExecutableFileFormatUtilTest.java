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
    final File binFile = getBundledBinFile("../linux/fsnotifier");
    assertEquals(MachineType.I386, ExecutableFileFormatUtil.readElfMachineType(binFile));
  }

  @Test
  public void testReadElfMachineType64bit() throws Exception {
    final File binFile = getBundledBinFile("../linux/fsnotifier64");
    assertEquals(MachineType.AMD64, ExecutableFileFormatUtil.readElfMachineType(binFile));
  }

  @Test
  public void testReadPeMachineType32bit() throws Exception {
    final File binFile = getBundledBinFile("../win/fsnotifier.exe");
    assertEquals(MachineType.I386, ExecutableFileFormatUtil.readPeMachineType(binFile));
  }

  @Test
  public void testReadPeMachineType64bit() throws Exception {
    final File binFile = getBundledBinFile("../win/fsnotifier64.exe");
    assertEquals(MachineType.AMD64, ExecutableFileFormatUtil.readPeMachineType(binFile));
  }

  @Test
  public void testReadPeMachineTypeUnknown() {
    final File binFile = getBundledBinFile("../linux/fsnotifier");
    assertEquals(MachineType.UNKNOWN, ExecutableFileFormatUtil.tryReadPeMachineType(binFile.getPath()));
    //noinspection ResultOfMethodCallIgnored
    Assertions.assertThatExceptionOfType(IOException.class).isThrownBy(() -> ExecutableFileFormatUtil.readPeMachineType(binFile));
  }

  @Test
  public void testReadElfMachineTypeUnknown() {
    final File binFile = getBundledBinFile("../win/fsnotifier.exe");
    assertEquals(MachineType.UNKNOWN, ExecutableFileFormatUtil.tryReadElfMachineType(binFile.getPath()));
    //noinspection ResultOfMethodCallIgnored
    Assertions.assertThatExceptionOfType(IOException.class).isThrownBy(() -> ExecutableFileFormatUtil.readElfMachineType(binFile));
  }

  @NotNull
  private static File getBundledBinFile(@NotNull String binFileName) {
    final File binFile = PathManager.findBinFile(binFileName);
    assertNotNull("Couldn't find bundled " + binFileName, binFile);
    return binFile;
  }
}
