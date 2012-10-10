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

import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
class Win32FsCache {
  private final IdeaWin32 myKernel = IdeaWin32.getInstance();
  private Reference<Map<String, FileInfo>> myCache;

  void clearCache() {
    myCache = null;
  }

  @NotNull
  private Map<String, FileInfo> getMap() {
    Reference<Map<String, FileInfo>> cache = myCache;
    Map<String, FileInfo> map = cache == null ? null : cache.get();
    if (map == null) {
      map = new THashMap<String, FileInfo>();
      myCache = new SoftReference<Map<String, FileInfo>>(map);
    }
    return map;
  }

  @NotNull
  String[] list(@NotNull String path) {
    FileInfo[] fileInfo = myKernel.listChildren(path);
    if (fileInfo == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    List<String> names = new ArrayList<String>(fileInfo.length);
    Map<String, FileInfo> map = getMap();
    for (FileInfo info : fileInfo) {
      String name = info.getName();
      map.put(path + "/" + name, info);
      names.add(name);
    }

    return ArrayUtil.toStringArray(names);
  }

  @Nullable
  FileInfo getInfo(@NotNull VirtualFile file) {
    String path = file.getPath();
    Map<String, FileInfo> map = getMap();
    FileInfo info = map.get(path);
    if (info == null) {
      info = myKernel.getInfo(path);
      if (info == null) {
        return null;
      }
      map.put(path, info);
    }
    return info;
  }
}
