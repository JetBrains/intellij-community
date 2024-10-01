// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtilRt;
import com.intellij.util.Url;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

@ApiStatus.Internal
public final class LocalFileStorage {
  private final File myStorageIODirectory;

  public LocalFileStorage() {
    myStorageIODirectory = new File(PathManager.getSystemPath(), "httpFileSystem");
    //noinspection ResultOfMethodCallIgnored
    myStorageIODirectory.mkdirs();
  }

  public File createLocalFile(@NotNull Url url) throws IOException {
    String baseName = PathUtilRt.getFileName(url.getPath());
    int index = baseName.lastIndexOf('.');
    String prefix = index == -1 ? baseName : baseName.substring(0, index);
    String suffix = index == -1 ? "" : baseName.substring(index+1);
    prefix = PathUtilRt.suggestFileName(prefix);
    suffix = PathUtilRt.suggestFileName(suffix);
    File file = FileUtil.findSequentNonexistentFile(myStorageIODirectory, prefix, suffix);
    FileUtilRt.createIfNotExists(file);
    return file;
  }

  public void deleteDownloadedFiles() {
    FileUtil.delete(myStorageIODirectory);
  }
}
