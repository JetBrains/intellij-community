// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.vfs;

import com.intellij.codeInsight.daemon.SyntheticPsiFileSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
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

  protected final @NotNull @NlsSafe String myName;
  protected final @NotNull @NlsSafe String myPath;
  private final @Nullable VirtualFile myParent;

  protected @NlsSafe String myRevision;
  protected int myModificationStamp = 0;

  /**
   * @deprecated {@link VcsFileSystem} cannot be overwritten
   */
  @Deprecated
  protected AbstractVcsVirtualFile(@NotNull @NlsSafe String path, @NotNull VirtualFileSystem ignored) {
    this(path);
  }

  protected AbstractVcsVirtualFile(@NotNull @NlsSafe String path) {
    myPath = path;
    File file = new File(myPath);
    myName = file.getName();
    if (!isDirectory()) {
      myParent = new VcsVirtualFolder(file.getParent(), this);
    }
    else
      myParent = null;

    SyntheticPsiFileSupport.markFile(this);
  }

  /**
   * @deprecated {@link VcsFileSystem} cannot be overwritten
   */
  @Deprecated
  protected AbstractVcsVirtualFile(@Nullable VirtualFile parent, @NotNull String name, @NotNull VirtualFileSystem ignored) {
    this(parent, name);
  }

  protected AbstractVcsVirtualFile(@Nullable VirtualFile parent, @NotNull String name) {
    myPath = parent != null && !StringUtil.isEmpty(parent.getPath()) ? parent.getPath() + "/" + name : name;
    myName = name;
    myParent = parent;

    SyntheticPsiFileSupport.markFile(this);
  }

  protected AbstractVcsVirtualFile(@Nullable VirtualFile parent, @NotNull FilePath path) {
    myPath = path.getPath();
    myName = path.getName();
    myParent = parent;

    markSyntheticFile(this, path);
  }

  protected AbstractVcsVirtualFile(@NotNull FilePath path) {
    myPath = path.getPath();
    myName = path.getName();

    FilePath parentPath = !isDirectory() ? path.getParentPath() : null;
    myParent = parentPath != null ? new VcsVirtualFolder(parentPath, this) : null;

    markSyntheticFile(this, path);
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return VcsFileSystem.getInstance();
  }

  @Override
  public @NotNull String getPath() {
    return myPath;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getPresentableName() {
    return getPresentableName(myName);
  }

  protected @NotNull @Nls String getPresentableName(@NotNull @Nls String baseName) {
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
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
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

  private static void markSyntheticFile(@NotNull VirtualFile file, @Nullable FilePath originalPath) {
    SyntheticPsiFileSupport.markFile(file, originalPath != null ? originalPath.getPath() : null);
  }
}
