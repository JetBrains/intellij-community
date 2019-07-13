// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * @author eldar
 */
public class ExecutableFileFormatUtil {
  @NotNull
  public static MachineType tryReadElfMachineType(@NotNull String path) {
    try {
      return readElfMachineType(path);
    }
    catch (IOException e) {
      return MachineType.UNKNOWN;
    }
  }

  @NotNull
  public static MachineType tryReadPeMachineType(@NotNull String path) {
    try {
      return readPeMachineType(path);
    }
    catch (IOException e) {
      return MachineType.UNKNOWN;
    }
  }

  @NotNull
  public static MachineType readElfMachineType(@NotNull String path) throws IOException {
    final File file = new File(path);
    return readElfMachineType(file);
  }

  @NotNull
  public static MachineType readPeMachineType(@NotNull String path) throws IOException {
    final File file = new File(path);
    return readPeMachineType(file);
  }

  @NotNull
  public static MachineType readElfMachineType(@NotNull File file) throws IOException {
    if (!file.isFile() || !file.canRead()) {
      throw new IOException("Not a readable file");
    }
    final long len = file.length();
    if (len < 0) throw new IOException("File length reported negative");

    try (FileInputStream stream = new FileInputStream(file)) {
      final FileChannel channel = stream.getChannel();

      final int ELF_HEADER_LEN = 0x14;
      final ByteBuffer elfHeader = ByteBuffer.allocate(ELF_HEADER_LEN);
      if (channel.read(elfHeader) < ELF_HEADER_LEN) throw new IOException("Not a valid ELF executable: ELF header is too short");
      elfHeader.flip();
      if (elfHeader.getInt() != 0x7F454C46 /* 0x75 ELF */) throw new IOException("Not a valid ELF executable: missing ELF magic");
      elfHeader.position(0x12);
      final short elfMachineType = elfHeader.order(ByteOrder.LITTLE_ENDIAN).getShort();

      return MachineType.forElfMachineTypeCode(elfMachineType);
    }
  }

  @NotNull
  public static MachineType readPeMachineType(@NotNull File file) throws IOException {
    if (!file.isFile() || !file.canRead()) {
      throw new IOException("Not a readable file");
    }
    final long len = file.length();
    if (len < 0) throw new IOException("File length reported negative");

    try (FileInputStream stream = new FileInputStream(file)) {
      final FileChannel channel = stream.getChannel();

      final int DOS_HEADER_LEN = 64;
      final ByteBuffer dosHeader = ByteBuffer.allocate(DOS_HEADER_LEN);
      if (channel.read(dosHeader) < DOS_HEADER_LEN) throw new IOException("Not a valid PE executable: DOS header is too short");
      dosHeader.flip();
      if (dosHeader.getShort() != 0x4D5A /* MZ */) throw new IOException("Not a valid PE executable: missing DOS magic");
      dosHeader.position(DOS_HEADER_LEN - 4);
      final int peOffset = dosHeader.order(ByteOrder.LITTLE_ENDIAN).getInt();

      channel.position(peOffset);

      final int PE_HEADER_LEN = 6;  // we only need 6 bytes
      final ByteBuffer peHeader = ByteBuffer.allocate(PE_HEADER_LEN);
      if (channel.read(peHeader) < PE_HEADER_LEN) throw new IOException("Not a valid PE executable: PE header is too short");
      peHeader.flip();
      if (peHeader.getInt() != 0x50450000 /* PE\0\0 */) throw new IOException("Not a valid PE executable: missing PE magic");
      final short peMachineType = peHeader.order(ByteOrder.LITTLE_ENDIAN).getShort();

      return MachineType.forPeMachineTypeCode(peMachineType);
    }
  }
}