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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.io.PrintWriter;

public class VirtualFilePointerImpl extends UserDataHolderBase implements VirtualFilePointer, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerImpl");
  private String myUrl; // is null when myFile is not null
  private VirtualFile myFile;
  private final VirtualFileManager myVirtualFileManager;
  private final VirtualFilePointerListener myListener;
  private boolean disposed = false;
  int useCount;
  private long myLastUpdated = -1;

  private static final Key<Throwable> CREATE_TRACE = Key.create("CREATION_TRACE");
  private static final Key<Throwable> KILL_TRACE = Key.create("KILL_TRACE");
  private static final boolean TRACE_CREATION = /*true || */LOG.isDebugEnabled();

  VirtualFilePointerImpl(VirtualFile file, @NotNull String url, @NotNull VirtualFileManager virtualFileManager, VirtualFilePointerListener listener, @NotNull Disposable parentDisposable) {
    myFile = file;
    myUrl = url;
    myVirtualFileManager = virtualFileManager;
    myListener = listener;
    useCount = 0;
    if (TRACE_CREATION) {
      putUserData(CREATE_TRACE, new Throwable("parent = '"+parentDisposable+"' ("+parentDisposable.getClass()+")"));
    }
  }

  @NotNull
  public String getFileName() {
    update();

    if (myFile != null) {
      return myFile.getName();
    }
    else {
      int index = myUrl.lastIndexOf('/');
      return index >= 0 ? myUrl.substring(index + 1) : myUrl;
    }
  }

  public VirtualFile getFile() {
    checkDisposed();

    update();
    if (myFile != null && !myFile.isValid()) {
      myUrl = myFile.getUrl();
      myFile = null;
      update();
    }
    return myFile;
  }

  @NotNull
  public String getUrl() {
    //checkDisposed(); no check here since Disposer might want to compute hashcode during dispose()
    update();
    return myUrl == null ? myFile.getUrl() : myUrl;
  }

  @NotNull
  public String getPresentableUrl() {
    checkDisposed();
    update();

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

  public boolean isValid() {
    update();
    return !disposed && myFile != null;
  }

  void update() {
    if (disposed) return;
    long fsModCount = myVirtualFileManager.getModificationCount();
    if (myLastUpdated == fsModCount) return;
    myLastUpdated = fsModCount;

    if (myFile == null) {
      LOG.assertTrue(myUrl != null, "Both file & url are null");
      myFile = myVirtualFileManager.findFileByUrl(myUrl);
      if (myFile != null) {
        myUrl = null;
      }
    }
    else if (!myFile.exists()) {
      myUrl = myFile.getUrl();
      myFile = null;
    }
  }

  @Override
  public String toString() {
    return myFile == null ? myUrl : myFile.getUrl();
  }

  public void dispose() {
    if (disposed) {
      throw new MyException("Punching the dead horse.\nurl="+toString(), getUserData(CREATE_TRACE), getUserData(KILL_TRACE));
    }
    if (--useCount == 0) {
      if (TRACE_CREATION) {
        putUserData(KILL_TRACE, new Throwable());
      }
      String url = getUrl();
      disposed = true;
      ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).clearPointerCaches(url, myListener);
    }
  }
}
