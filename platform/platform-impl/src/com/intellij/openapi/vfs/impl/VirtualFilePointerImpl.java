/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;

public class VirtualFilePointerImpl extends UserDataHolderBase implements VirtualFilePointer, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerImpl");

  private Pair<VirtualFile, String> myFileAndUrl; // must not be both null
  private final VirtualFileManager myVirtualFileManager;
  private final VirtualFilePointerListener myListener;
  private boolean disposed = false;
  int useCount;
  private long myLastUpdated = -1;

  private static final Key<Throwable> CREATE_TRACE = Key.create("CREATION_TRACE");
  private static final Key<Throwable> KILL_TRACE = Key.create("KILL_TRACE");
  private static final boolean TRACE_CREATION = /*true || */LOG.isDebugEnabled();

  VirtualFilePointerImpl(VirtualFile file,
                         @NotNull String url,
                         @NotNull VirtualFileManager virtualFileManager,
                         VirtualFilePointerListener listener,
                         @NotNull Disposable parentDisposable) {
    myFileAndUrl = Pair.create(file, url);
    myVirtualFileManager = virtualFileManager;
    myListener = listener;
    useCount = 0;
    if (TRACE_CREATION) {
      putUserData(CREATE_TRACE, new Throwable("parent = '"+parentDisposable+"' ("+parentDisposable.getClass()+")"));
    }
  }

  @Override
  @NotNull
  public String getFileName() {
    Pair<VirtualFile, String> result = update();
    if (result == null) {
      checkDisposed();
      return "";
    }
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
    Pair<VirtualFile, String> result = update();
    if (result == null) {
      checkDisposed();
      return null;
    }
    return result.first;
  }

  @Override
  @NotNull
  public String getUrl() {
    update();
    return getUrlNoUpdate();
  }

  private String getUrlNoUpdate() {
    Pair<VirtualFile, String> fileAndUrl = myFileAndUrl;
    VirtualFile file = fileAndUrl.first;
    String url = fileAndUrl.second;
    return url == null ? file.getUrl() : url;
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    checkDisposed();
    return PathUtil.toPresentableUrl(getUrl());
  }

  private void checkDisposed() {
    if (disposed) throw new MyException("Already disposed: "+toString(), getUserData(CREATE_TRACE), getUserData(KILL_TRACE));
  }

  public void throwNotDisposedError(String msg) throws RuntimeException {
    Throwable trace = getUserData(CREATE_TRACE);
    throw new RuntimeException(msg+"\n" +
                               "url=" + this +
                               "\nCreation trace " + (trace==null?"null":"some")+
                               "\n", trace);
  }

  public int incrementUsageCount() {
    return ++useCount;
  }

  private static class MyException extends RuntimeException {
    private final Throwable e1;
    private final Throwable e2;

    private MyException(String message, Throwable e1, Throwable e2) {
      super(message);
      this.e1 = e1;
      this.e2 = e2;
    }

    @Override
    public void printStackTrace(PrintStream s) {
      //noinspection IOResourceOpenedButNotSafelyClosed
      printStackTrace(new PrintWriter(s));
    }

    @Override
    public void printStackTrace(PrintWriter s) {
      super.printStackTrace(s);
      if (e1 != null) {
        s.println("--------------Creation trace: ");
        e1.printStackTrace(s);
      }
      if (e2 != null) {
        s.println("--------------Kill trace: ");
        e2.printStackTrace(s);
      }
    }
  }

  @Override
  public boolean isValid() {
    Pair<VirtualFile, String> result = update();
    return result != null && result.first != null;
  }

  @Nullable
  Pair<VirtualFile, String> update() {
    if (disposed) return null;
    long lastUpdated = myLastUpdated;
    Pair<VirtualFile, String> fileAndUrl = myFileAndUrl;
    VirtualFile file = fileAndUrl.first;
    String url = fileAndUrl.second;
    long fsModCount = myVirtualFileManager.getModificationCount();
    if (lastUpdated == fsModCount) return fileAndUrl;

    if (file != null && !file.isValid()) {
      url = file.getUrl();
      file = null;
    }
    if (file == null) {
      LOG.assertTrue(url != null, "Both file & url are null");
      file = myVirtualFileManager.findFileByUrl(url);
      if (file != null) {
        url = null;
      }
    }
    if (file != null && !file.exists()) {
      url = file.getUrl();
      file = null;
    }
    Pair<VirtualFile, String> result = Pair.create(file, url);
    myFileAndUrl = result;
    myLastUpdated = fsModCount;
    return result;
  }

  @Override
  public String toString() {
    return getUrlNoUpdate();
  }

  @Override
  public void dispose() {
    if (disposed) {
      throw new MyException("Punching the dead horse.\nURL=" + toString(), getUserData(CREATE_TRACE), getUserData(KILL_TRACE));
    }
    if (--useCount == 0) {
      if (TRACE_CREATION) {
        putUserData(KILL_TRACE, new Throwable());
      }
      String url = getUrlNoUpdate();
      disposed = true;
      ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).clearPointerCaches(url, myListener);
    }
  }

  public boolean isDisposed() {
    return disposed;
  }
}
