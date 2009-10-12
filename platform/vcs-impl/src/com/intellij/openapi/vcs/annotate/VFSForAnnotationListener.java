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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

import java.util.List;

public class VFSForAnnotationListener extends VirtualFileAdapter {
  private final VirtualFile myFile;
  private final List<AnnotationListener> myListeners;

  public VFSForAnnotationListener(final VirtualFile file, final List<AnnotationListener> listeners) {
    myListeners = listeners;
    myFile = file;
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    if (myFile != event.getFile()) return;
    if (! event.isFromRefresh()) return;

    if (event.getPropertyName().equals(VirtualFile.PROP_WRITABLE)) {
      if (((Boolean)event.getOldValue()).booleanValue()) {
        fireAnnotationChanged();
      }
    }
  }

  public void contentsChanged(VirtualFileEvent event) {
    if (myFile != event.getFile()) return;
    if (! event.isFromRefresh()) return;
    if (! myFile.isWritable()) {
      fireAnnotationChanged();
    }
  }

  private void fireAnnotationChanged() {
    final AnnotationListener[] listeners = myListeners.toArray(new AnnotationListener[myListeners.size()]);
    for (AnnotationListener listener : listeners) {
      listener.onAnnotationChanged();
    }
  }
}
