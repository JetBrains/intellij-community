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
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class LocalFileStorage {
  private final File myStorageIODirectory;

  public LocalFileStorage() {
    myStorageIODirectory = new File(PathManager.getSystemPath(), "httpFileSystem");
    myStorageIODirectory.mkdirs();
  }

  public File createLocalFile(@NotNull String url) throws IOException {
    int ast = url.indexOf('?');
    if (ast != -1) {
      url = url.substring(0, ast);
    }
    int last = url.lastIndexOf('/');
    String baseName;
    if (last == url.length() - 1) {
      baseName = url.substring(url.lastIndexOf('/', last-1) + 1, last);
    }
    else {
      baseName = url.substring(last + 1);
    }

    int index = baseName.lastIndexOf('.');
    String prefix = index == -1 ? baseName : baseName.substring(0, index);
    String suffix = index == -1 ? "" : baseName.substring(index+1);
    prefix = PathUtil.suggestFileName(prefix);
    suffix = PathUtil.suggestFileName(suffix);
    File file = FileUtil.findSequentNonexistentFile(myStorageIODirectory, prefix, suffix);
    FileUtil.createIfDoesntExist(file);
    return file;
  }

  public void deleteDownloadedFiles() {
    FileUtil.delete(myStorageIODirectory);
  }
}
