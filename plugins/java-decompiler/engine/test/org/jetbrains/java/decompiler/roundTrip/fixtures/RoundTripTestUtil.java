// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RoundTripTestUtil {
  private RoundTripTestUtil() { }

  /**
   * Decompiles the classes related to {@code sourceFile} from in-memory bytecode,
   * returning the decompiled Java source as a string. No files are read from or
   * written to disk.
   *
   * <p>Only the class matching {@code sourceFile} and its inner classes
   * (e.g. {@code sourceFile + "$Inner"}) are fed to the decompiler.
   */
  public static String decompileInMemory(Map<String, Object> options, Map<String, byte[]> classBytes, String sourceFile) {
    // Build a path-keyed lookup so the IBytecodeProvider can resolve fake File paths
    // regardless of how File.getAbsolutePath() formats them on the current platform.
    // ALL compiled classes go into byAbsPath; companion classes are registered via
    // addLibrary so the decompiler can resolve type hierarchies without decompiling them.
    Map<String, byte[]> byAbsPath = new LinkedHashMap<>();
    List<File> sourceFakeFiles = new ArrayList<>();
    List<File> libraryFakeFiles = new ArrayList<>();
    for (Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
      String key = entry.getKey();
      File fakeFile = new File("/mem/" + key + ".class");
      byAbsPath.put(fakeFile.getAbsolutePath(), entry.getValue());
      if (key.equals(sourceFile) || key.startsWith(sourceFile + "$")) {
        sourceFakeFiles.add(fakeFile);
      }
      else {
        libraryFakeFiles.add(fakeFile);
      }
    }
    if (sourceFakeFiles.isEmpty()) {
      throw new IllegalArgumentException("No compiled classes found for source file: " + sourceFile);
    }

    DecompilerResultSaver saver = new DecompilerResultSaver();
    BaseDecompiler decompiler = new BaseDecompiler(
      (externalPath, internalPath) -> {
        byte[] bytes = byAbsPath.get(externalPath);
        if (bytes == null) throw new IOException("No in-memory bytes for: " + externalPath);
        return bytes;
      },
      saver,
      options,
      new PrintStreamLogger(System.out)
    );

    for (File fakeFile : sourceFakeFiles) {
      decompiler.addSource(fakeFile);
    }
    for (File libraryFile : libraryFakeFiles) {
      decompiler.addLibrary(libraryFile);
    }
    decompiler.decompileContext();

    return saver.getContent();
  }
}
