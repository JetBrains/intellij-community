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

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class VcsVirtualFolder extends AbstractVcsVirtualFile {
  private final VirtualFile myChild;
  public VcsVirtualFolder(String name, VirtualFile child, VirtualFileSystem fileSystem) {
    super(name == null ? "" : name, fileSystem);
    myChild = child;
  }

  public VirtualFile[] getChildren() {
    return new VirtualFile[]{myChild};
  }

  public boolean isDirectory() {
    return true;
  }

  @NotNull
  public byte[] contentsToByteArray() {
    throw new RuntimeException(VcsBundle.message("exception.text.internal.error.method.should.not.be.called"));
  }
}
