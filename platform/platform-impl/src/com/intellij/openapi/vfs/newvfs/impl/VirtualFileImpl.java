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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;

public class VirtualFileImpl extends VirtualFileSystemEntry {
  public VirtualFileImpl(final String name, final VirtualDirectoryImpl parent, final int id) {
    super(name, parent, id);
  }

  @Nullable
  public NewVirtualFile findChild(@NotNull @NonNls final String name) {
    return null;
  }

  public Collection<VirtualFile> getCachedChildren() {
    return Collections.emptyList();
  }

  @Override
  public Iterable<VirtualFile> iterInDbChildren() {
    return ContainerUtil.emptyIterable();
  }

  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    final VirtualFileSystemEntry parent = getParent();
    assert parent != null;

    return parent.getFileSystem();
  }

  @Nullable
  public NewVirtualFile refreshAndFindChild(final String name) {
    return null;
  }

  @Nullable
  public NewVirtualFile findChildIfCached(final String name) {
    return null;
  }

  public VirtualFile[] getChildren() {
    return EMPTY_ARRAY;
  }

  public boolean isDirectory() {
    return false;
  }

  @NotNull
  public InputStream getInputStream() throws IOException {
    return ourPersistence.getInputStream(this);
  }

  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return ourPersistence.contentsToByteArray(this);
  }

  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    return ourPersistence.getOutputStream(this, requestor, modStamp, timeStamp);
  }

  public NewVirtualFile findChildById(int id) {
    return null;
  }
}