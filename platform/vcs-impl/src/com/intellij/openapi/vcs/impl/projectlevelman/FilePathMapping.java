// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class FilePathMapping<T> {
  private final boolean myCaseSensitive;

  private final Map<String, T> myPathMap;
  private final IntSet myPathHashSet = new IntOpenHashSet();

  public FilePathMapping(boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
    myPathMap = caseSensitive ? new HashMap<>() : CollectionFactory.createCaseInsensitiveStringMap();
  }

  public void add(@NotNull String filePath, @NotNull T value) {
    String path = StringUtil.trimTrailing(filePath, '/');
    myPathMap.put(path, value);
    myPathHashSet.add(pathHashCode(myCaseSensitive, path));
  }

  public void remove(@NotNull String filePath) {
    String path = StringUtil.trimTrailing(filePath, '/');
    myPathMap.remove(path);
    // We do not update myPathHashSet, so hash collisions might become worse over time.
  }

  @NotNull
  public Collection<T> values() {
    return myPathMap.values();
  }

  @Nullable
  public T getMappingFor(@NotNull FilePath filePath) {
    String path = filePath.getPath();

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

      prefixHash = pathHashCode(myCaseSensitive, path, index, nextIndex, prefixHash);

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

  private static int pathHashCode(boolean caseSensitive, @NotNull String path) {
    return pathHashCode(caseSensitive, path, 0, path.length(), 0);
  }

  private static int pathHashCode(boolean caseSensitive, @NotNull String path, int offset1, int offset2, int prefixHash) {
    if (caseSensitive) {
      return StringUtil.stringHashCode(path, offset1, offset2, prefixHash);
    }
    else {
      return StringUtil.stringHashCodeInsensitive(path, offset1, offset2, prefixHash);
    }
  }
}
