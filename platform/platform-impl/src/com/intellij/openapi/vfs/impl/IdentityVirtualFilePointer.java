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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class IdentityVirtualFilePointer implements VirtualFilePointer {
  private final VirtualFile myFile;
  private final String myUrl;

  IdentityVirtualFilePointer(VirtualFile file, @NotNull String url) {
    myFile = file;
    myUrl = url;
  }

  @NotNull
  public String getFileName() {
    return getUrl();
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getPresentableUrl() {
    return getUrl();
  }

  public boolean isValid() {
    return myFile == null || myFile.isValid();
  }
}