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
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

class VirtualFilePointerImpl extends TraceableDisposable implements VirtualFilePointer {
  private static final Logger LOG = Logger.getInstance(VirtualFilePointerImpl.class);

  private static final boolean TRACE_CREATION = LOG.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode();

  volatile FilePointerPartNode myNode; // null means disposed
  boolean recursive; // true if the validityChanged() event should be fired for any change under this directory. Used for library jar directories.

  VirtualFilePointerImpl() {
    super(TRACE_CREATION);
  }

  @Override
  @NotNull
  public String getFileName() {
    FilePointerPartNode node = checkDisposed(myNode);
    if (node == null) return "";
    Pair<VirtualFile, String> result = node.update();
    if (result == null) return "";
    VirtualFile file = result.first;
    if (file != null) {
      return file.getName();
    }
    String url = result.second;
    int index = url.lastIndexOf('/');
    return index >= 0 ? url.substring(index + 1) : url;
  }

  @Override
  public VirtualFile getFile() {
    FilePointerPartNode node = checkDisposed(myNode);
    if (node == null) return null;
    Pair<VirtualFile, String> result = node.update();
    return result == null ? null : result.first;
  }

  @Override
  @NotNull
  public String getUrl() {
    FilePointerPartNode node = myNode;
    if (node == null) return "";
    // optimization: when file is null we shouldn't try to do expensive findFileByUrl() just to return the url
    Pair<VirtualFile, String> fileAndUrl = node.myFileAndUrl;
    if (fileAndUrl != null && fileAndUrl.getFirst() == null) {
      return fileAndUrl.getSecond();
    }
    Pair<VirtualFile, String> result = node.update();
    return result == null ? "" : result.second;
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    FilePointerPartNode node = checkDisposed(myNode);
    if (node == null) return "";
    Pair<VirtualFile, String> result = node.update();
    return result == null ? "" : PathUtil.toPresentableUrl(result.second);
  }

  private FilePointerPartNode checkDisposed(FilePointerPartNode node) {
    if (node == null) {
      ProgressManager.checkCanceled();
      LOG.error("Already disposed: URL='" + this + "'");
    }
    return node;
  }


  @Override
  public boolean isValid() {
    FilePointerPartNode node = myNode;
    Pair<VirtualFile, String> result = node == null ? null : node.update();
    return result != null && result.first != null;
  }

  @Override
  public String toString() {
    FilePointerPartNode node = myNode;
    Pair<VirtualFile, String> fileAndUrl;
    return node == null ? "(disposed)" : (fileAndUrl = node.myFileAndUrl) == null ? "?" : fileAndUrl.second;
  }

  public void dispose() {
    FilePointerPartNode node = checkDisposed(myNode);
    if (node.incrementUsageCount(-1) == 0) {
      kill("URL when die: " + this);
      VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
      if (pointerManager instanceof VirtualFilePointerManagerImpl) {
        ((VirtualFilePointerManagerImpl)pointerManager).removeNodeFrom(this);
      }
    }
  }

  int incrementUsageCount(int delta) {
    FilePointerPartNode node = checkDisposed(myNode);
    if (node == null) return 1;
    return node.incrementUsageCount(delta);
  }

  @Override
  public boolean isRecursive() {
    return recursive;
  }
}
