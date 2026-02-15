// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompactFileReader {
  public static final int FORMAT_VERSION = 2;

  public static RawRuntimeModuleRepositoryData loadFromFile(@NotNull Path filePath) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(filePath)))) {
      int formatVersion = in.readInt();
      if (formatVersion != FORMAT_VERSION) {
        throw new MalformedRepositoryException("'" + filePath + "' has unsupported format '" + formatVersion + "'");
      }
      return CompactFileReaderForVersion2.loadFromInputStream(filePath, in);
    }
  }

  public static @NotNull String @Nullable [] loadBootstrapClasspath(@NotNull Path binaryFile, @NotNull String bootstrapModuleName) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(binaryFile)))) {
      int formatVersion = in.readInt();
      if (formatVersion != FORMAT_VERSION) return null;
      
      in.readInt();

      boolean hasBootstrap = in.readBoolean();
      if (!hasBootstrap) return null;
      
      String actualBootstrapModuleName = in.readUTF();
      if (!actualBootstrapModuleName.equals(bootstrapModuleName)) return null;
      
      int size = in.readInt();
      String[] classpath = new String[size];
      for (int i = 0; i < size; i++) {
        classpath[i] = in.readUTF();
      }
      return classpath;
    }
  }

  static void skipGeneratorVersionAndBootstrapClasspath(DataInputStream in) throws IOException {
    in.readInt();//generator version

    boolean hasBootstrapClasspath = in.readBoolean();
    if (hasBootstrapClasspath) {
      in.readUTF();
      int size = in.readInt();
      for (int i = 0; i < size; i++) {
        in.readUTF();
      }
    }
  }
}
