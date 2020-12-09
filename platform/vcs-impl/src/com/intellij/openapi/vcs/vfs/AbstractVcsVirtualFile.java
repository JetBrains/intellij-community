/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.vfs;

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class AbstractVcsVirtualFile extends VirtualFile {
  @NlsSafe
  protected final String myName;
  @NlsSafe
  protected final String myPath;
  @NlsSafe
  protected String myRevision;
  private final VirtualFile myParent;
  protected int myModificationStamp = 0;
  @NotNull
  private final VirtualFileSystem myFileSystem;

  protected AbstractVcsVirtualFile(String path, @NotNull VirtualFileSystem fileSystem) {
    myFileSystem = fileSystem;
    myPath = path;
    File file = new File(myPath);
    myName = file.getName();
    if (!isDirectory())
      myParent = new VcsVirtualFolder(file.getParent(), this, myFileSystem);
    else
      myParent = null;

    OutsidersPsiFileSupport.markFile(this);
  }

  protected AbstractVcsVirtualFile(@Nullable VirtualFile parent, @NotNull String name, @NotNull VirtualFileSystem fileSystem) {
    myFileSystem = fileSystem;
    myPath = parent != null && !StringUtil.isEmpty(parent.getPath()) ? parent.getPath() + "/" + name : name;
    myName = name;
    myParent = parent;

    OutsidersPsiFileSupport.markFile(this);
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @Override
  @NotNull
  public String getPath() {
    return myPath;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public @NotNull String getPresentableName() {
    return getPresentableName(myName);
  }

  @NotNull
  @Nls
  String getPresentableName(@NotNull @Nls String baseName) {
    if (myRevision == null) return baseName;
    return baseName + " (" + myRevision + ")";
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return myParent;

  }

  @Override
  public VirtualFile[] getChildren() {
    return null;
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
    throw new RuntimeException(VcsFileSystem.getCouldNotImplementMessage());
  }

  @Override
  public abstract byte @NotNull [] contentsToByteArray() throws IOException;

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  @Override
  public long getTimeStamp() {
    return myModificationStamp;
  }

  @Override
  public long getLength() {
    try {
      return contentsToByteArray().length;
    } catch (IOException e) {
      return 0;
    }
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    if (postRunnable != null)
      postRunnable.run();
  }

  protected void setRevision(String revision) {
    myRevision = revision;
  }

  protected void showLoadingContentFailedMessage(@NotNull VcsException e) {
    ApplicationManager.getApplication().invokeLater(() -> Messages.showMessageDialog(
      VcsBundle.message("message.text.could.not.load.virtual.file.content", getPresentableUrl(), e.getLocalizedMessage()),
      VcsBundle.message("message.title.could.not.load.content"),
      Messages.getInformationIcon()));
  }
}
