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
package com.intellij.cvsSupport2.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * author: lesya
 */
public class CvsVfsUtil {

  private static final Logger LOG = Logger.getInstance(CvsVfsUtil.class);

  public static VirtualFile findFileByPath(final String path) {
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  public static VirtualFile findChild(final VirtualFile file, final String name) {
    if (file == null) {
      return LocalFileSystem.getInstance().findFileByIoFile(new File(name));
    }
    else {
      return file.findChild(name);
    }
  }

  public static VirtualFile getParentFor(final File file) {
    return findFileByIoFile(file.getParentFile());
  }

  public static File getFileFor(final VirtualFile file) {
    if (file == null) return null;
    return new File(file.getPath());
  }

  public static File getFileFor(final VirtualFile parent, String name) {
    if (parent == null) {
      return new File(name);
    }
    else {
      return new File(parent.getPath(), name);
    }
  }

  public static VirtualFile findFileByIoFile(final File file) {
    if (file == null) return null;
    return LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  public static VirtualFile refreshAndFindFileByIoFile(@NotNull final File file) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  public static VirtualFile @NotNull [] getChildrenOf(final VirtualFile directory) {
    if (!directory.isValid()) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final VirtualFile[] children = directory.getChildren();
    return (children == null) ? VirtualFile.EMPTY_ARRAY : children;
  }

  public static long getTimeStamp(final VirtualFile file) {
    return file.getTimeStamp();
  }

  public static boolean isWritable(final VirtualFile virtualFile) {
    return virtualFile.isWritable();
  }

  public static String getPresentablePathFor(final VirtualFile root) {
    return root.getPresentableUrl();
  }

  public static VirtualFile refreshAndfFindChild(VirtualFile parent, String fileName) {
    return refreshAndFindFileByIoFile(new File(CvsVfsUtil.getFileFor(parent), fileName));
  }
}
