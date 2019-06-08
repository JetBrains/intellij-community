// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.ArrayUtilRt;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectHashingStrategy;
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
  private Reference<TIntObjectHashMap<Map<String, FileAttributes>>> myCache;

  void clearCache() {
    myCache = null;
  }

  @NotNull
  private TIntObjectHashMap<Map<String, FileAttributes>> getMap() {
    TIntObjectHashMap<Map<String, FileAttributes>> map = com.intellij.reference.SoftReference.dereference(myCache);
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
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    String[] names = new String[fileInfo.length];
    TIntObjectHashMap<Map<String, FileAttributes>> map = getMap();
    int parentId = ((VirtualFileWithId)file).getId();
    Map<String, FileAttributes> nestedMap = map.get(parentId);
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
    TIntObjectHashMap<Map<String, FileAttributes>> map = getMap();
    Map<String, FileAttributes> nestedMap = map.get(parentId);
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
    IncompleteChildrenMap(@NotNull TObjectHashingStrategy<K> strategy) {
      super(strategy);
    }
  }
}
