// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Internal
public final class IdentityVirtualFilePointer extends VirtualFilePointerImpl implements VirtualFilePointer, Disposable {
  private final Map<String, IdentityVirtualFilePointer> myUrlToIdentity;
  private final VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private final VirtualFile myFile;
  private final String myUrl;

  IdentityVirtualFilePointer(VirtualFile file, @NotNull String url, Map<String, IdentityVirtualFilePointer> urlToIdentity,
                             @NotNull VirtualFilePointerManagerImpl virtualFilePointerManager,
                             VirtualFilePointerListener listener) {
    super(listener);
    myVirtualFilePointerManager = virtualFilePointerManager;
    myUrlToIdentity = urlToIdentity;
    myFile = file;
    myUrl = url;
  }

  @Override
  public @NotNull String getFileName() {
    return getUrl();
  }

  @Override
  public VirtualFile getFile() {
    return isValid() ? myFile : null;
  }

  @Override
  public @NotNull String getUrl() {
    return myUrl;
  }

  @Override
  public @NotNull String getPresentableUrl() {
    return getUrl();
  }

  @Override
  public boolean isValid() {
    return myFile == null || myFile.isValid();
  }

  @Override
  public void dispose() {
    synchronized (myVirtualFilePointerManager) {
      incrementUsageCount(-1);
      myUrlToIdentity.remove(myUrl);
    }
  }

  @Override
  public String toString() {
    return "identity: url='" + myUrl + "'; file=" + myFile;
  }
}