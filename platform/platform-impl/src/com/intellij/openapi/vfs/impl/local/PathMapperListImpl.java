/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;

public class PathMapperListImpl implements CanonicalPathMap.PathMapper {
  @NotNull private final List<Pair<String, String>> myMapping;

  public PathMapperListImpl() {
    this.myMapping = ContainerUtil.createConcurrentList();
  }

  @Override
  public void addMappings(@NotNull Collection<Pair<String, String>> mapping) {
    myMapping.addAll(mapping);
  }

  @Override
  public void addMapping(@NotNull String reportedPath, @NotNull String watchedPath) {
    myMapping.add(pair(reportedPath, watchedPath));
  }

  @Override
  @NotNull
  public Collection<String> applyMapping(@NotNull String reportedPath) {
    Collection<String> affectedPaths = ContainerUtil.newSmartList(reportedPath);
    for (Pair<String, String> map : myMapping) {
      if (FileUtil.startsWith(reportedPath, map.first)) {
        affectedPaths.add(map.second + reportedPath.substring(map.first.length()));
      }
      else if (FileUtil.startsWith(reportedPath, map.second)) {
        affectedPaths.add(map.first + reportedPath.substring(map.second.length()));
      }
    }
    return affectedPaths;
  }
}
