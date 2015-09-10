/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.local;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public abstract class CanonicalFileMapper {
  public enum MappingType {FLAT, RECURSIVE}

  public static CanonicalFileMapper create() {
    return new CanonicalFileTrie();
  }

  /**
   * @return the canonical path for {@param file}
   */
  @Nullable
  public abstract String addMapping(@NotNull File file, @NotNull MappingType mappingType);

  /**
   * @return the canonical path for each item in {@param paths}
   */
  @NotNull
  public final List<String> addMappings(@NotNull Collection<String> paths, @NotNull MappingType mappingType) {
    List<String> canonicalPaths = Lists.newArrayList();
    for (String path : paths) {
      String canonicalPath = addMapping(new File(path), mappingType);
      if (canonicalPath != null) {
        canonicalPaths.add(canonicalPath);
      }
    }
    return canonicalPaths;
  }

  @NotNull
  public abstract List<String> getMapping(@NotNull String canonicalPath);

  @NotNull
  public final List<String> getMapping(@NotNull Collection<String> paths) {
    List<String> nonCanonicalPaths = Lists.newArrayList();
    for (String path : paths) {
      nonCanonicalPaths.addAll(getMapping(path));
    }
    return nonCanonicalPaths;
  }

  @Nullable
  static String symLinkToRealPath(@NotNull File possibleSymLink) {
    return FileSystemUtil.resolveSymLink(possibleSymLink);
  }

  @NotNull
  static String combine(@NotNull String symLink, List<String> pathComponents, int startIndex) {
    String fileSuffix = StringUtil.join(pathComponents.subList(startIndex, pathComponents.size()), File.separator);
    return FileUtil.join(symLink, fileSuffix);
  }
}