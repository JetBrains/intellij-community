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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class PathMapperImpl implements CanonicalPathMap.PathMapper {

  @NotNull private final MultiMap<String, String> pathMapping;

  public PathMapperImpl() {
    pathMapping = MultiMap.createConcurrentSet();
  }

  // The file constructor is used instead of FileUtil.join to join path components because it has better behavior when one of the path
  // components is the empty string.
  @NotNull
  @Override
  public Collection<String> applyMapping(@NotNull String reportedPath) {
    List<String> results = ContainerUtil.newSmartList(reportedPath);
    List<String> pathComponents = FileUtil.splitPath(reportedPath);

    File runningPath = null;
    for (int i = 0; i < pathComponents.size(); ++i) {
      String currentPathComponent = pathComponents.get(i);
      if (runningPath == null) {
        runningPath = new File(currentPathComponent + File.separator);
      } else {
        runningPath = new File(runningPath, currentPathComponent);
      }
      Collection<String> mappedPaths = pathMapping.get(runningPath.getPath());
      for (String mappedPath : mappedPaths) {
        // Append the specific file suffix to the mapped watch root.
        String fileSuffix = StringUtil.join(pathComponents.subList(i + 1, pathComponents.size()), File.separator);
        results.add(new File(mappedPath, fileSuffix).getPath());
      }
    }

    return results;
  }

  @Override
  public void addMappings(@NotNull Collection<Pair<String, String>> mappings) {
    for (Pair<String, String> mapping : mappings) {
      addMapping(mapping.first, mapping.second);
    }
  }

  @Override
  public void addMapping(@NotNull String reportedPath, @NotNull String watchedPath) {
    // See if we are adding a mapping that itself should be mapped to a different path
    // Example: /foo/realpath -> /foo/symlink, /foo/remapped_path -> /foo/realpath
    // In this case, if the file watcher returns /foo/remapped_path/file.txt, we want to report /foo/symlink/file.txt back to IntelliJ.
    Collection<String> preRemapPathToWatchedPaths = pathMapping.get(watchedPath);
    for (String realWatchedPath : preRemapPathToWatchedPaths) {
      Collection<String> remappedPathMappings = pathMapping.getModifiable(reportedPath);
      remappedPathMappings.add(realWatchedPath);
    }

    // Since there can be more than one file watcher and REMAPPING is an implementation detail of the native file watcher, add the mapping
    // as usual even if we added data above.
    Collection<String> symLinksToCanonicalPath = pathMapping.getModifiable(reportedPath);
    symLinksToCanonicalPath.add(watchedPath);
  }
}
