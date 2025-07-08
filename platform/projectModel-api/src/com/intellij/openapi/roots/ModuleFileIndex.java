/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides information about files contained in a module. Should be used from a read action.
 *
 * @see ModuleRootManager#getFileIndex()
 */
@ApiStatus.NonExtendable
public interface ModuleFileIndex extends FileIndex {
  /**
   * Returns the order entry to which the specified file or directory
   * belongs.
   *
   * @param fileOrDir the file or directory to check.
   * @return the order entry to which the file or directory belongs, or null if
   * it does not belong to any order entry.
   */
  @RequiresReadLock
  @Nullable
  OrderEntry getOrderEntryForFile(@NotNull VirtualFile fileOrDir);

  /**
   * Returns the list of all order entries to which the specified file or directory
   * belongs.
   *
   * @param fileOrDir the file or directory to check.
   * @return the list of order entries to which the file or directory belongs.
   */
  @RequiresReadLock
  @NotNull List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile fileOrDir);

  /**
   * Processes all files and directories under content roots of the given module, skipping excluded and ignored files and directories.
   * <br>
   * <strong>It doesn't work efficiently if you need to process content of multiple modules</strong>
   * {@code ProjectFileIndex.getInstance(project).iterateContent()} should be used instead in such cases.
   * 
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  @Override
  boolean iterateContent(@NotNull ContentIterator processor);

  /**
   * Same as {@link #iterateContent(ContentIterator)} but allows to pass {@code filter} to
   * provide filtering in condition for directories.
   * <br>
   * <strong>It doesn't work efficiently if you need to process content of multiple modules</strong>
   * {@code ProjectFileIndex.getInstance(project).iterateContent()} should be used instead in such cases.
   */
  @Override
  boolean iterateContent(@NotNull ContentIterator processor, @Nullable VirtualFileFilter filter);
}
