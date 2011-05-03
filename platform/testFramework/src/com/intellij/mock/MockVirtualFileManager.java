/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.mock;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.NotNull;

public class MockVirtualFileManager extends VirtualFileManagerEx {
  public MockVirtualFileManager() {
    super();
  }

  @Override
  public VirtualFileSystem getFileSystem(String protocol) {
    return null;
  }

  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    refresh(asynchronous);
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public void refresh(boolean asynchronous, Runnable postAction) {
  }

  @Override
  public VirtualFile findFileByUrl(@NotNull String url) {
    return null;
  }

  @Override
  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    return null;
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  @Override
  public void addModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
  }

  @Override
  public void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
  }

  @Override
  public void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files) {
  }

  @Override
  public void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener) {
  }

  @Override
  public void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener) {
  }

  public void beforeRefreshStart(boolean asynchronous, ModalityState modalityState, Runnable postAction) {
  }

  public void afterRefreshFinish(boolean asynchronous, ModalityState modalityState) {
  }

  public void registerRefreshUpdater(CacheUpdater updater) {
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
  }

  @Override
  public void registerFileSystem(VirtualFileSystem fileSystem) {
  }

  @Override
  public void unregisterFileSystem(VirtualFileSystem fileSystem) {
  }

  @Override
  public void fireAfterRefreshFinish(final boolean asynchronous) {

  }

  @Override
  public void fireBeforeRefreshStart(final boolean asynchronous) {
    
  }

  @Override
  public long getModificationCount() {
    return ModificationTracker.EVER_CHANGED.getModificationCount();
  }
}
