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
package com.intellij.mock;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class MockVirtualFileSystem extends DeprecatedVirtualFileSystem {
  private final MyVirtualFile myRoot = new MyVirtualFile("", null);
  public static final String PROTOCOL = "mock";

  @Override
  @NotNull
  public VirtualFile findFileByPath(@NotNull String path) {
    path = path.replace(File.separatorChar, '/');
    path = path.replace('/', ':');
    if (StringUtil.startsWithChar(path, ':')) path = path.substring(1);
    MyVirtualFile file = myRoot;
    for (String component : StringUtil.split(path, ":")) {
      file = file.getOrCreate(component);
    }
    return file;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @Override
  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  public class MyVirtualFile extends LightVirtualFile {
    private final Map<String, MyVirtualFile> myChildren = new THashMap<String, MyVirtualFile>();
    private final MyVirtualFile myParent;

    public MyVirtualFile(@NotNull String name, @Nullable MyVirtualFile parent) {
      super(name);

      myParent = parent;
    }

    @Override
    @NotNull
    public VirtualFileSystem getFileSystem() {
      return MockVirtualFileSystem.this;
    }

    @NotNull
    public MyVirtualFile getOrCreate(@NotNull String name) {
      MyVirtualFile file = myChildren.get(name);
      if (file == null) {
        file = new MyVirtualFile(name, this);
        myChildren.put(name, file);
      }
      return file;
    }

    @Override
    public boolean isDirectory() {
      return !myChildren.isEmpty();
    }

    @NotNull
    @Override
    public String getPath() {
      final MockVirtualFileSystem.MyVirtualFile parent = getParent();
      return parent == null ? getName() : parent.getPath() + "/" + getName();
    }

    @Override
    public MyVirtualFile getParent() {
      return myParent;
    }

    @Override
    public VirtualFile[] getChildren() {
      Collection<MyVirtualFile> children = myChildren.values();
      return children.toArray(new MyVirtualFile[children.size()]);
    }
  }
}
