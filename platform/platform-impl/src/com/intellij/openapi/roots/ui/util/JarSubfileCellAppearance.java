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
package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public class JarSubfileCellAppearance extends ValidFileCellAppearance {
  public JarSubfileCellAppearance(VirtualFile file) {
    super(file);
  }

  @Override
  protected Icon getIcon() {
    return FileTypes.ARCHIVE.getIcon();
  }

  @Override
  protected int getSplitUrlIndex(String url) {
    int jarNameEnd = url.lastIndexOf(JarFileSystem.JAR_SEPARATOR.charAt(0));
    String jarUrl = jarNameEnd >= 0 ? url.substring(0, jarNameEnd) : url;
    return super.getSplitUrlIndex(jarUrl);
  }
}
