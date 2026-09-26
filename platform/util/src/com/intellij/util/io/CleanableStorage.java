// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import java.io.IOException;

/**
 * Trait: storage knows how to clean (delete) its files.
 * <p/>
 * One could just .close() the storage and delete its file -- but:
 * <ol>
 * <li>Some storages uses >1 file, and the specific number/naming of the files used is an 'inside knowledge'</li>
 * <li>Some trickery could be needed to remove/clear the storage -- e.g. with memory-mapped storages, it is not
 * that easy to delete mmapped file on Windows</li>
 * </ol>
 * For such cases, storage could provide a way to clean for itself.
 */
public interface CleanableStorage {
  /**
   * Closes the storage (if not yet) and clean the underlying file(s). It's ok to call if storage already closed.
   * <p/>
   * 'Clean' defined as 'new storage opened on top of the same file will be completely empty, same as if there was
   * no file(s) at all'.
   * The simplest way to implement it is to actually delete the files, but implementation is free to choose other ways,
   * e.g. truncate the file, or zero its content.
   */
  void closeAndClean() throws IOException;
}
