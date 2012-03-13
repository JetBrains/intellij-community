/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class VfsImplUtil {
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.VfsImplUtil");
  
  @NonNls private static final String FILE_SEPARATORS = "/" + File.separator;

  private VfsImplUtil() {
  }

  @Nullable
  public static VirtualFile findFileByPath(NewVirtualFileSystem vfs, @NotNull @NonNls final String path) {
    final String normalizedPath = vfs.normalize(path);
    if (normalizedPath == null) return null;
    final String basePath = vfs.extractRootPath(normalizedPath);
    NewVirtualFile file = ManagingFS.getInstance().findRoot(basePath, vfs);
    if (file == null || !file.exists()) return null;

    if (normalizedPath.length() < basePath.length()) {
      return null;
    }
    for (String pathElement : StringUtil.tokenize(normalizedPath.substring(basePath.length()), FILE_SEPARATORS)) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.findChild(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  @Nullable
  public static VirtualFile findFileByPathIfCached(NewVirtualFileSystem vfs, @NotNull @NonNls String path) {
    final String normalizedPath = vfs.normalize(path);
    if (normalizedPath == null) return null;
    final String basePath = vfs.extractRootPath(normalizedPath);
    NewVirtualFile file = ManagingFS.getInstance().findRoot(basePath, vfs);
    if (file == null || !file.exists()) return null;

    for (String pathElement : StringUtil.tokenize(normalizedPath.substring(basePath.length()), FILE_SEPARATORS)) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.findChildIfCached(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  @Nullable
  public static VirtualFile refreshAndFindFileByPath(NewVirtualFileSystem vfs, @NotNull final String path) {
    final String normalizedPath = vfs.normalize(path);
    if (normalizedPath == null) return null;
    final String basePath = vfs.extractRootPath(normalizedPath);
    NewVirtualFile file = ManagingFS.getInstance().findRoot(basePath, vfs);
    if (file == null || !file.exists()) return null;

    LOG.assertTrue(basePath.length() <= normalizedPath.length(), vfs + " failed to extract root path: " + basePath + " from " + normalizedPath);
    for (String pathElement : StringUtil.tokenize(normalizedPath.substring(basePath.length()), FILE_SEPARATORS)) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.refreshAndFindChild(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }
}
