// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths;

import com.intellij.util.UriUtil;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class FilePathMapping<T> {
  private final boolean myCaseSensitive;

  private final Map<String, T> myPathMap;
  private final IntSet myPathHashSet = new IntOpenHashSet();

  public FilePathMapping(boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
    myPathMap = caseSensitive ? new HashMap<>() : CollectionFactory.createCaseInsensitiveStringMap();
  }

  public void add(@NotNull String filePath, @NotNull T value) {
    String path = UriUtil.trimTrailingSlashes(filePath);
    myPathMap.put(path, value);
    myPathHashSet.add(FilePathHashUtil.pathHashCode(myCaseSensitive, path));
  }

  public void remove(@NotNull String filePath) {
    String path = UriUtil.trimTrailingSlashes(filePath);
    myPathMap.remove(path);
    // We do not update myPathHashSet, so hash collisions might become worse over time.
  }

  public void clear() {
    myPathMap.clear();
    myPathHashSet.clear();
  }

  @NotNull
  public Collection<T> values() {
    return myPathMap.values();
  }

  public boolean containsKey(@NotNull String filePath) {
    String path = UriUtil.trimTrailingSlashes(filePath);
    return myPathMap.containsKey(path);
  }

  @Nullable
  public T getMappingFor(@NotNull String filePath) {
    String path = UriUtil.trimTrailingSlashes(filePath);

    int index = 0;
    int prefixHash = 0;
    IntList matches = new IntArrayList();

    // check empty string for FS root
    if (myPathHashSet.contains(prefixHash)) {
      matches.add(index);
    }

    while (index < path.length()) {
      int nextIndex = path.indexOf('/', index + 1);
      if (nextIndex == -1) nextIndex = path.length();

      prefixHash = FilePathHashUtil.pathHashCode(myCaseSensitive, path, index, nextIndex, prefixHash);

      if (myPathHashSet.contains(prefixHash)) {
        matches.add(nextIndex);
      }

      index = nextIndex;
    }

    for (int i = matches.size() - 1; i >= 0; i--) {
      String prefix = path.substring(0, matches.getInt(i));
      T root = myPathMap.get(prefix);
      if (root != null) return root;
    }

    return null;
  }
}
