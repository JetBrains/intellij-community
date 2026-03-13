// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures;

import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.util.jar.Manifest;

/**
 * An {@link IResultSaver} that captures the decompiled source from
 * {@link #saveClassFile} as a string. All other operations are no-ops.
 */
public final class DecompilerResultSaver implements IResultSaver {
  private String content;

  @Override public void saveFolder(String path) {}
  @Override public void copyFile(String source, String path, String entryName) {}
  @Override public void createArchive(String path, String archiveName, Manifest manifest) {}
  @Override public void saveDirEntry(String path, String archiveName, String entryName) {}
  @Override public void copyEntry(String source, String path, String archiveName, String entry) {}
  @Override public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {}
  @Override public void closeArchive(String path, String archiveName) {}

  @Override
  public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
    this.content = content;
  }

  public String getContent() {
    return content;
  }
}
