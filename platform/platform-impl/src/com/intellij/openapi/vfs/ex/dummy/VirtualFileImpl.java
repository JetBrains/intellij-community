
/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.testFramework.DummyFileIdGenerator;
import org.jetbrains.annotations.NotNull;

abstract class VirtualFileImpl extends VirtualFile implements VirtualFileWithId {
  private final DummyFileSystem myFileSystem;
  private final VirtualFileDirectoryImpl myParent;
  private String myName;
  protected boolean myIsValid = true;
  private final int myId = DummyFileIdGenerator.next();

  protected VirtualFileImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    myFileSystem = fileSystem;
    myParent = parent;
    myName = name;
  }

  public int getId() {
    return myId;
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    if (myParent == null) {
      return myName;
    } else {
      return myParent.getPath() + "/" + myName;
    }
  }

  @NotNull
  public String getName() {
    return myName;
  }

  void setName(final String name) {
    myName = name;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isValid() {
    return myIsValid;
  }

  public VirtualFile getParent() {
    return myParent;
  }

  public long getTimeStamp() {
    return -1;
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }
}
