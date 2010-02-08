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
package com.intellij.mock;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

public class MockVirtualFileSystem extends DeprecatedVirtualFileSystem {
  private final MyVirtualFile myRoot = new MyVirtualFile("", null);
  public static final String PROTOCOL = "mock";

  public VirtualFile findFileByPath(@NotNull String path) {
    path = path.replace(File.separatorChar, '/');
    path = path.replace('/', ':');
    if (StringUtil.startsWithChar(path, ':')) path = path.substring(1);
    String[] components = path.split(":");
    MyVirtualFile file = myRoot;
    for (String component : components) {
      file = file.getOrCreate(component);
    }
    return file;
  }

  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  public void refresh(boolean asynchronous) {
  }

  public void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
  }

  public void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
  }

  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent, @NotNull final String copyName) throws IOException {
    return null;
  }

  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
  }

  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    return null;
  }

  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw new IOException();
  }

  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  public class MyVirtualFile extends LightVirtualFile {
    private final HashMap<String, MyVirtualFile> myChildren = new HashMap<String, MyVirtualFile>();
    private final MyVirtualFile myParent;

    public MyVirtualFile(String name, MyVirtualFile parent) {
      super(name);
      myParent = parent;
    }

    @NotNull
    public VirtualFileSystem getFileSystem() {
      return MockVirtualFileSystem.this;
    }

    public MyVirtualFile getOrCreate(String name) {
      MyVirtualFile file = myChildren.get(name);
      if (file == null) {
        file = new MyVirtualFile(name, this);
        myChildren.put(name, file);
      }
      return file;
    }

    public boolean isDirectory() {
      return myChildren.size() != 0;
    }

    public String getPath() {
      final MockVirtualFileSystem.MyVirtualFile parent = getParent();
      return parent == null ? getName() : parent.getPath() + "/" + getName();
    }

    public MyVirtualFile getParent() {
      return myParent;
    }

    public VirtualFile[] getChildren() {
      Collection<MyVirtualFile> children = myChildren.values();
      return children.toArray(new MyVirtualFile[children.size()]);
    }
  }
}
