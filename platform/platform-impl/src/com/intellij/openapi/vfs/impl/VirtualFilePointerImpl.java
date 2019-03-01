/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TraceableDisposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class VirtualFilePointerImpl extends TraceableDisposable implements VirtualFilePointer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerImpl");

  private final VirtualFilePointerListener myListener;
  private static final boolean TRACE_CREATION = LOG.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode();

  volatile FilePointerPartNode myNode; // null means disposed
  boolean recursive; // true if the validityChanged() event should be fired for any change under this directory. Used for library jar directories.

  VirtualFilePointerImpl(@Nullable VirtualFilePointerListener listener) {
    super(TRACE_CREATION);
    myListener = listener;
  }

  @Override
  @NotNull
  public String getFileName() {
    FilePointerPartNode node = myNode;
    if (!checkDisposed(node)) return "";
    Pair<VirtualFile, String> result = update(node);
    if (result == null) return "";
    VirtualFile file = result.first;
    if (file != null) {
      return file.getName();
    }
    String url = result.second;
    int index = url.lastIndexOf('/');
    return index >= 0 ? url.substring(index + 1) : url;
  }

  private Pair<VirtualFile, String> update(FilePointerPartNode node) {
    while (true) {
      Pair<VirtualFile, String> result = node.update();
      if (result != null) {
        return result;
      }
      node = myNode;
      if (node == null) return null;
      // otherwise the node is becoming invalid, retry
    }
  }

  @Override
  public VirtualFile getFile() {
    FilePointerPartNode node = myNode;
    if (!checkDisposed(node)) return null;
    Pair<VirtualFile, String> result = update(node);
    return result == null ? null : result.first;
  }

  @Override
  @NotNull
  public String getUrl() {
    FilePointerPartNode node = myNode;
    if (isDisposed(node)) return "";
    Pair<VirtualFile, String> result = update(node);
    return result == null ? "" : result.second;
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    FilePointerPartNode node = myNode;
    if (!checkDisposed(node)) return "";
    Pair<VirtualFile, String> result = update(node);
    return result == null ? "" : PathUtil.toPresentableUrl(result.second);
  }

  private boolean checkDisposed(FilePointerPartNode node) {
    if (isDisposed(node)) {
      ProgressManager.checkCanceled();
      LOG.error("Already disposed: URL='" + this + "'");
      return false;
    }
    return true;
  }


  @Override
  public boolean isValid() {
    FilePointerPartNode node = myNode;
    Pair<VirtualFile, String> result = isDisposed(node) ? null : update(node);
    return result != null && result.first != null;
  }

  @Override
  public String toString() {
    FilePointerPartNode node = myNode;
    return node == null ? "(disposed)" : node.myFileAndUrl.second;
  }

  public void dispose() {
    FilePointerPartNode node = myNode;
    checkDisposed(node);
    if (node.incrementUsageCount(-1) == 0) {
      kill("URL when die: " + this);
      VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
      if (pointerManager instanceof VirtualFilePointerManagerImpl) {
        ((VirtualFilePointerManagerImpl)pointerManager).removeNodeFrom(this);
      }
    }
  }

  public boolean isDisposed() {
    return isDisposed(myNode);
  }
  private static boolean isDisposed(FilePointerPartNode node) {
    return node == null;
  }

  VirtualFilePointerListener getListener() {
    return myListener;
  }

  int incrementUsageCount(int delta) {
    return myNode.incrementUsageCount(delta);
  }

  @Override
  public boolean isRecursive() {
    return recursive;
  }
}
