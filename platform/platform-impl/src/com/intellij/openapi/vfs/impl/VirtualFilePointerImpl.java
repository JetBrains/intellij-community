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

import com.intellij.openapi.Disposable;
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

class VirtualFilePointerImpl extends TraceableDisposable implements VirtualFilePointer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerImpl");

  private final VirtualFilePointerListener myListener;
  private static final boolean TRACE_CREATION = LOG.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode();

  volatile FilePointerPartNode myNode; // null means disposed

  VirtualFilePointerImpl(VirtualFilePointerListener listener, @NotNull Disposable parentDisposable, Pair<VirtualFile, String> fileAndUrl) {
    super(TRACE_CREATION);
    myListener = listener;
  }

  @Override
  @NotNull
  public String getFileName() {
    if (!checkDisposed()) return "";
    Pair<VirtualFile, String> result = myNode.update();
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
    if (!checkDisposed()) return null;
    Pair<VirtualFile, String> result = myNode.update();
    return result.first;
  }

  @Override
  @NotNull
  public String getUrl() {
    if (isDisposed()) return "";
    Pair<VirtualFile, String> update = myNode.update();
    return update.second;
  }

  @NotNull
  String getUrlNoUpdate() {
    return isDisposed() ? "" : myNode.myFileAndUrl.second;
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    if (!checkDisposed()) return "";
    return PathUtil.toPresentableUrl(getUrl());
  }

  private boolean checkDisposed() {
    if (isDisposed()) {
      ProgressManager.checkCanceled();
      LOG.error("Already disposed: URL='" + this + "'");
      return false;
    }
    return true;
  }


  @Override
  public boolean isValid() {
    Pair<VirtualFile, String> result = isDisposed() ? null : myNode.update();
    return result != null && result.first != null;
  }

  @Override
  public String toString() {
    return getUrlNoUpdate();
  }

  public void dispose() {
    checkDisposed();
    if (myNode.incrementUsageCount(-1) == 0) {
      kill("URL when die: "+ toString());
      VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
      if (pointerManager instanceof VirtualFilePointerManagerImpl) {
        ((VirtualFilePointerManagerImpl)pointerManager).removeNode(myNode, myListener); // remove from the tree
      }
      myNode = null;
    }
  }

  public boolean isDisposed() {
    return myNode == null;
  }

  VirtualFilePointerListener getListener() {
    return myListener;
  }
}
