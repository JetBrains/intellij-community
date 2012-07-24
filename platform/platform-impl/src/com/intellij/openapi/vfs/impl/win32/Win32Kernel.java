/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
class Win32Kernel {
  private final IdeaWin32 myKernel = IdeaWin32.getInstance();
  private final Map<String, FileInfo> myCache = new THashMap<String, FileInfo>();

  void clearCache() {
    myCache.clear();
  }

  @NotNull
  public String[] list(@NotNull String absolutePath) {
    FileInfo[] fileInfos = myKernel.listChildren(absolutePath.replace('/', '\\') + "\\*.*");
    if (fileInfos == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    ArrayList<String> names = new ArrayList<String>(fileInfos.length);
    for (FileInfo info : fileInfos) {
      if (info.name.equals(".")) {
        myCache.put(absolutePath, info);
        continue;
      }
      if (info.name.equals("..")) {
        continue;
      }
      myCache.put(absolutePath + "/" + info.name, info);
      names.add(info.name);
    }

    return ArrayUtil.toStringArray(names);
  }

  public void exists(@NotNull String path) throws FileNotFoundException {
    getInfo(path);
  }

  public boolean isDirectory(@NotNull String path) throws FileNotFoundException {
    FileInfo data = getInfo(path);
    return (data.attributes & FileInfo.FILE_ATTRIBUTE_DIRECTORY) != 0;
  }

  public boolean isWritable(@NotNull String path) throws FileNotFoundException {
    FileInfo fileInfo = getInfo(path);
    myCache.remove(path);
    return (fileInfo.attributes & FileInfo.FILE_ATTRIBUTE_READONLY) == 0;
  }

  public long getTimeStamp(@NotNull String path) throws FileNotFoundException {
    long timestamp = getInfo(path).timestamp;
    return timestamp / 10000 - 11644473600000l;
  }

  public long getLength(@NotNull String path) throws FileNotFoundException {
    return getInfo(path).length;
  }

  @NotNull
  private FileInfo getInfo(@NotNull String path) throws FileNotFoundException {
    FileInfo info = doGetInfo(path);
    if (info == null) {
      throw new FileNotFoundException(path);
    }
    return info;
  }

  @Nullable
  FileInfo doGetInfo(@NotNull String path) {
    FileInfo info = myCache.get(path);
    if (info == null) {
      info = myKernel.getInfo(path.replace('/', '\\'));
      if (info == null) {
        return null;
      }
      myCache.put(path, info);
    }
    return info;
  }

  @FileUtil.FileBooleanAttributes
  public int getBooleanAttributes(@NotNull String path, @FileUtil.FileBooleanAttributes int flags) throws FileNotFoundException {
    FileInfo info = getInfo(path);
    int result = 0;
    if ((flags & FileUtil.BA_EXISTS) != 0) {
      result |= FileUtil.BA_EXISTS;
    }
    if ((flags & FileUtil.BA_DIRECTORY) != 0) {
      result |= (info.attributes & FileInfo.FILE_ATTRIBUTE_DIRECTORY) == 0 ? 0 : FileUtil.BA_DIRECTORY;
    }
    if ((flags & FileUtil.BA_REGULAR) != 0) {
      result |= (info.attributes & (FileInfo.FILE_ATTRIBUTE_DIRECTORY | FileInfo.FILE_ATTRIBUTE_DEVICE | FileInfo.FILE_ATTRIBUTE_REPARSE_POINT)) != 0
                ? 0 : FileUtil.BA_REGULAR;
    }
    if ((flags & FileUtil.BA_HIDDEN) != 0) {
      result |= (info.attributes & FileInfo.FILE_ATTRIBUTE_HIDDEN) == 0 ? 0 : FileUtil.BA_HIDDEN;
    }
    return result;
  }
}
