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
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class VirtualFileUrlChangeAdapter extends VirtualFileAdapter {
  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    String oldUrl = event.getOldParent().getUrl() + "/" + event.getFileName();
    String newUrl = event.getNewParent().getUrl() + "/" + event.getFileName();
    fileUrlChanged(oldUrl, newUrl);
  }

  protected abstract void fileUrlChanged(String oldUrl, String newUrl);

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      final VirtualFile parent = event.getFile().getParent();
      if (parent != null) {
        final String parentUrl = parent.getUrl();
        fileUrlChanged(parentUrl + "/" + event.getOldValue(), parentUrl + "/" + event.getNewValue());
      }
    }
  }
}
