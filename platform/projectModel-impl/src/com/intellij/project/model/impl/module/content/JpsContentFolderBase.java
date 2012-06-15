/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.project.model.impl.module.content;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
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

  @NotNull
  @Override
  public ContentEntry getContentEntry() {
    return myContentEntry;
  }

  @NotNull
  @Override
  public String getUrl() {
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
