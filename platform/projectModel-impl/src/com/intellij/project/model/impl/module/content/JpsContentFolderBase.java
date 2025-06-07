// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project.model.impl.module.content;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.annotations.NotNull;

public class JpsContentFolderBase implements Disposable, ContentFolder {
  protected final JpsContentEntry myContentEntry;
  protected VirtualFilePointer myFilePointer;

  public JpsContentFolderBase(String url, JpsContentEntry contentEntry) {
    myFilePointer = VirtualFilePointerManager.getInstance().create(url, this, null);
    myContentEntry = contentEntry;
  }

  @Override
  public VirtualFile getFile() {
    return myFilePointer.getFile();
  }

  @Override
  public @NotNull ContentEntry getContentEntry() {
    return myContentEntry;
  }

  @Override
  public @NotNull String getUrl() {
    return myFilePointer.getUrl();
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public void dispose() {
  }
}
