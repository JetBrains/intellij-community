// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
final class Win32FsCache {
  private final IdeaWin32 myKernel = IdeaWin32.getInstance();
  private Reference<Int2ObjectMap<Map<String, FileAttributes>>> myCache;

  void clearCache() {
    myCache = null;
  }

  private @NotNull Int2ObjectMap<Map<String, FileAttributes>> getMap() {
    Int2ObjectMap<Map<String, FileAttributes>> map = com.intellij.reference.SoftReference.dereference(myCache);
    if (map == null) {
      map = new Int2ObjectOpenHashMap<>();
      myCache = new SoftReference<>(map);
    }
    return map;
  }

  String @NotNull [] list(@NotNull VirtualFile file) {
    String path = file.getPath();
    FileInfo[] fileInfo = myKernel.listChildren(path);
    if (fileInfo == null || fileInfo.length == 0) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    String[] names = new String[fileInfo.length];
    Int2ObjectMap<Map<String, FileAttributes>> map = getMap();
    int parentId = ((VirtualFileWithId)file).getId();
    Map<String, FileAttributes> nestedMap = map.get(parentId);
    if (nestedMap == null) {
      nestedMap = CollectionFactory.createFilePathMap(fileInfo.length, file.isCaseSensitive());
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
    Int2ObjectMap<Map<String, FileAttributes>> map = getMap();
    Map<String, FileAttributes> nestedMap = map.get(parentId);
    String name = file.getName();
    FileAttributes attributes = nestedMap != null ? nestedMap.get(name) : null;

    if (attributes == null) {
      FileInfo info = myKernel.getInfo(file.getPath());
      if (info == null) {
        return null;
      }
      attributes = info.toFileAttributes();
      if (nestedMap == null) {
        nestedMap = createIncompleteChildrenMap();
        map.put(parentId, nestedMap);
      }
      nestedMap.put(name, attributes);
    }
    return attributes;
  }

  private static <V> @NotNull Map<String, V> createIncompleteChildrenMap() {
    return SystemInfoRt.isFileSystemCaseSensitive ? new HashMap<>() : CollectionFactory.createCaseInsensitiveStringMap();
  }
}
