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
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public abstract class AbstractVcsVirtualFile extends VirtualFile {

  protected final String myName;
  protected final String myPath;
  protected String myRevision;
  private final VirtualFile myParent;
  protected int myModificationStamp = 0;
  private final VirtualFileSystem myFileSystem;
  protected boolean myProcessingBeforeContentsChange;

  protected AbstractVcsVirtualFile(String path, VirtualFileSystem fileSystem) {
    myFileSystem = fileSystem;
    myPath = path;
    File file = new File(myPath);
    myName = file.getName();
    if (!isDirectory())
      myParent = new VcsVirtualFolder(file.getParent(), this, myFileSystem);
    else
      myParent = null;
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    return myPath;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public String getPresentableName() {
    if (myRevision == null)
      return myName;
    else
      return myName + " (" + myRevision + ")";
  }

  public boolean isWritable() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public VirtualFile getParent() {
    return myParent;

  }

  public VirtualFile[] getChildren() {
    return null;
  }

  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this);
  }

  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new RuntimeException(VcsFileSystem.COULD_NOT_IMPLEMENT_MESSAGE);
  }

  @NotNull
  public abstract byte[] contentsToByteArray() throws IOException;

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public long getTimeStamp() {
    return myModificationStamp;
  }

  public long getLength() {
    try {
      return contentsToByteArray().length;
    } catch (IOException e) {
      return 0;
    }
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    if (postRunnable != null)
      postRunnable.run();
  }

  protected void setRevision(String revision) {
    myRevision = revision;
  }

  protected void fireBeforeContentsChange() {
    myProcessingBeforeContentsChange = true;
    try {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          ((VcsFileSystem)getFileSystem()).fireBeforeContentsChange(this, AbstractVcsVirtualFile.this);
        }
      });
    }
    finally {
      myProcessingBeforeContentsChange = false;
    }
  }
}
