/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.jetbrains.annotations.NotNull;

public class VFSForAnnotationListener extends VirtualFileAdapter {
  private final VirtualFile myFile;
  private final FileAnnotation myFileAnnotation;

  public VFSForAnnotationListener(final VirtualFile file, final FileAnnotation fileAnnotation) {
    myFileAnnotation = fileAnnotation;
    myFile = file;
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (! Comparing.equal(myFile, event.getFile())) return;
    if (! event.isFromRefresh()) return;

    if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
      if (((Boolean)event.getOldValue()).booleanValue()) {
        myFileAnnotation.close();
      }
    }
  }

  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    if (! Comparing.equal(myFile, event.getFile())) return;
    if (! event.isFromRefresh()) return;
    if (! myFile.isWritable()) {
      myFileAnnotation.close();
    }
  }
}
