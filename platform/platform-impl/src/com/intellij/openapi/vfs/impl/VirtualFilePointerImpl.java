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
import com.intellij.openapi.util.TraceableDisposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

class VirtualFilePointerImpl extends TraceableDisposable implements VirtualFilePointer {
  private static final Logger LOG = Logger.getInstance(VirtualFilePointerImpl.class);

  private static final boolean TRACE_CREATION = LOG.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode();

  volatile FilePartNode myNode; // null means disposed
  private int useCount;
  boolean recursive; // true if the validityChanged() event should be fired for any change under this directory. Used for library jar directories.
  final VirtualFilePointerListener myListener;

  VirtualFilePointerImpl(VirtualFilePointerListener listener) {
    super(TRACE_CREATION);
    myListener = listener;
  }

  @Override
  @NotNull
  public String getFileName() {
    FilePartNode node = checkDisposed(myNode);
    if (node == null) return "";
    Object result = node.myFileOrUrl;
    if (result instanceof VirtualFile) {
      return ((VirtualFile)result).getName();
    }
    String url = (String)result;
    int index = url.lastIndexOf('/');
    return index >= 0 ? url.substring(index + 1) : url;
  }

  @Override
  public VirtualFile getFile() {
    FilePartNode node = checkDisposed(myNode);
    if (node == null) return null;
    return FilePartNode.myFile(node.myFileOrUrl);
  }

  @Override
  @NotNull
  public String getUrl() {
    FilePartNode node = myNode;
    if (node == null) return "";
    return FilePartNode.myUrl(node.myFileOrUrl);
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    return PathUtil.toPresentableUrl(getUrl());
  }

  private FilePartNode checkDisposed(FilePartNode node) {
    if (node == null) {
      ProgressManager.checkCanceled();
      LOG.error("Already disposed: URL='" + this + "'");
    }
    return node;
  }


  @Override
  public boolean isValid() {
    FilePartNode node = myNode;
    return node != null && FilePartNode.myFile(node.myFileOrUrl) != null;
  }

  @Override
  public String toString() {
    FilePartNode node = myNode;
    return node == null ? "(disposed)" : FilePartNode.myUrl(node.myFileOrUrl);
  }

  public void dispose() {
    VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    boolean shouldKill;
    if (pointerManager instanceof VirtualFilePointerManagerImpl) {
      shouldKill = ((VirtualFilePointerManagerImpl)pointerManager).decrementUsageCount(this);
    }
    else {
      shouldKill = incrementUsageCount(-1) == 0;
    }

    if (shouldKill) {
      kill("URL when die: " + this);
    }
  }

  int incrementUsageCount(int delta) {
    return useCount += delta;
  }

  boolean isRecursive() {
    return recursive;
  }
}
