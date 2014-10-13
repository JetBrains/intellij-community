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
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
class Win32FsCache {
  private final IdeaWin32 myKernel = IdeaWin32.getInstance();
  private Reference<Map<String, FileAttributes>> myCache;

  void clearCache() {
    myCache = null;
  }

  @NotNull
  private Map<String, FileAttributes> getMap() {
    Map<String, FileAttributes> map = com.intellij.reference.SoftReference.dereference(myCache);
    if (map == null) {
      map = new THashMap<String, FileAttributes>(FileUtil.PATH_HASHING_STRATEGY);
      myCache = new SoftReference<Map<String, FileAttributes>>(map);
    }
    return map;
  }

  @NotNull
  String[] list(@NotNull String path) {
    FileInfo[] fileInfo = myKernel.listChildren(path);
    if (fileInfo == null || fileInfo.length == 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    if (!StringUtil.endsWithChar(path, '/')) path += "/";
    String[] names = new String[fileInfo.length];
    Map<String, FileAttributes> map = getMap();
    for (int i = 0, length = fileInfo.length; i < length; i++) {
      FileInfo info = fileInfo[i];
      String name = info.getName();
      map.put(path + name, info.toFileAttributes());
      names[i] = name;
    }
    return names;
  }

  @Nullable
  FileAttributes getAttributes(@NotNull VirtualFile file) {
    String path = file.getPath();
    Map<String, FileAttributes> map = getMap();
    FileAttributes attributes = map.get(path);
    if (attributes == null) {
      FileInfo info = myKernel.getInfo(path);
      if (info == null) {
        return null;
      }
      attributes = info.toFileAttributes();
      map.put(path, attributes);
    }
    return attributes;
  }
}
