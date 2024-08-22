// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.main.extern;

import java.util.jar.Manifest;

public interface IResultSaver {
  long STABLE_ZIP_TIMESTAMP = 0x386D4380; // 01/01/2000 00:00:00 java 8 breaks when using 0.

  void saveFolder(String path);

  void copyFile(String source, String path, String entryName);

  void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping);

  void createArchive(String path, String archiveName, Manifest manifest);

  void saveDirEntry(String path, String archiveName, String entryName);

  void copyEntry(String source, String path, String archiveName, String entry);

  void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content);

  void closeArchive(String path, String archiveName);
}
