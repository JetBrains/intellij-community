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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

/**
 * @author Dmitry Avdeev
 */
class Win32FsCache {
  private final IdeaWin32 myKernel = IdeaWin32.getInstance();
  private Reference<TIntObjectHashMap<THashMap<String, FileAttributes>>> myCache;

  void clearCache() {
    myCache = null;
  }

  @NotNull
  private TIntObjectHashMap<THashMap<String, FileAttributes>> getMap() {
    TIntObjectHashMap<THashMap<String, FileAttributes>> map = com.intellij.reference.SoftReference.dereference(myCache);
    if (map == null) {
      map = new TIntObjectHashMap<>();
      myCache = new SoftReference<>(map);
    }
    return map;
  }

  @NotNull
  String[] list(@NotNull VirtualFile file) {
    String path = file.getPath();
    FileInfo[] fileInfo = myKernel.listChildren(path);
    if (fileInfo == null || fileInfo.length == 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    String[] names = new String[fileInfo.length];
    TIntObjectHashMap<THashMap<String, FileAttributes>> map = getMap();
    int parentId = ((VirtualFileWithId)file).getId();
    THashMap<String, FileAttributes> nestedMap = map.get(parentId);
    if (nestedMap == null) {
      nestedMap = new THashMap<>(fileInfo.length, FileUtil.PATH_HASHING_STRATEGY);
      map.put(parentId, nestedMap);
    }
    for (int i = 0, length = fileInfo.length; i < length; i++) {
      FileInfo info = fileInfo[i];
      String name = info.getName();
      nestedMap.put(name, info.toFileAttributes());
      names[i] = name;
    }
    return names;
  }

  @Nullable
  FileAttributes getAttributes(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    int parentId = parent instanceof VirtualFileWithId ? ((VirtualFileWithId)parent).getId() : -((VirtualFileWithId)file).getId();
    TIntObjectHashMap<THashMap<String, FileAttributes>> map = getMap();
    THashMap<String, FileAttributes> nestedMap = map.get(parentId);
    String name = file.getName();
    FileAttributes attributes = nestedMap != null ? nestedMap.get(name) : null;

    if (attributes == null) {
      if (nestedMap != null && !(nestedMap instanceof IncompleteChildrenMap)) {
        return null; // our info from parent doesn't mention the child in this refresh session
      }
      FileInfo info = myKernel.getInfo(file.getPath());
      if (info == null) {
        return null;
      }
      attributes = info.toFileAttributes();
      if (nestedMap == null) {
        nestedMap = new IncompleteChildrenMap<>(FileUtil.PATH_HASHING_STRATEGY);
        map.put(parentId, nestedMap);
      }
      nestedMap.put(name, attributes);
    }
    return attributes;
  }

  private static class IncompleteChildrenMap<K, V> extends THashMap<K,V> {
    IncompleteChildrenMap(TObjectHashingStrategy<K> strategy) {
      super(strategy);
    }
  }
}
