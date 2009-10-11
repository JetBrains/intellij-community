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

import com.intellij.ide.startup.CacheUpdater;
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

  public VirtualFileSystem[] getFileSystems() {
    return new VirtualFileSystem[0];
  }

  public VirtualFileSystem getFileSystem(String protocol) {
    return null;
  }

  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    refresh(asynchronous);
  }

  public void refresh(boolean asynchronous) {
  }

  public void refresh(boolean asynchronous, Runnable postAction) {
  }

  public VirtualFile findFileByUrl(@NotNull String url) {
    return null;
  }

  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    return null;
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
  }

  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  public void addModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
  }

  public void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
  }

  public void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files) {
  }

  public void addVirtualFileManagerListener(VirtualFileManagerListener listener) {
  }

  public void removeVirtualFileManagerListener(VirtualFileManagerListener listener) {
  }

  public void beforeRefreshStart(boolean asynchronous, ModalityState modalityState, Runnable postAction) {
  }

  public void afterRefreshFinish(boolean asynchronous, ModalityState modalityState) {
  }

  public void addEventToFireByRefresh(Runnable action, boolean asynchronous, ModalityState modalityState) {
  }

  public void registerRefreshUpdater(CacheUpdater updater) {
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
  }

  public void registerFileSystem(VirtualFileSystem fileSystem) {
  }

  public void unregisterFileSystem(VirtualFileSystem fileSystem) {
  }

  public void fireAfterRefreshFinish(final boolean asynchronous) {

  }

  public void fireBeforeRefreshStart(final boolean asynchronous) {
    
  }

  public long getModificationCount() {
    return ModificationTracker.EVER_CHANGED.getModificationCount();
  }
}
