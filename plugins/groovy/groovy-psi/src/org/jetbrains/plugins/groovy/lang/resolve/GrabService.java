/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface GrabService extends PersistentStateComponent<GrabService.PersistentState> {
  void scheduleUpdate(@NotNull GlobalSearchScope scope);

  @NotNull
  @Override
  GrabService.PersistentState getState();

  @Override
  void loadState(@NotNull GrabService.PersistentState persistentState);

  @NotNull
  List<VirtualFile> getDependencies(@NotNull SearchScope scope);

  Set<VirtualFile> getJars();

  @NotNull
  List<VirtualFile> getDependencies(@NotNull VirtualFile file);

  @NotNull
  static GrabService getInstance(@NotNull Project project) {
    return ObjectUtils.notNull(ServiceManager.getService(project, GrabService.class));
  }

  class PersistentState {
    public Map<String, List<String>> fileMap;

    public PersistentState(Map<String, List<VirtualFile>> map) {
      fileMap = new HashMap<>();
      map.forEach((s, files) -> fileMap.put(s, files.stream().map(VirtualFile::getCanonicalPath).collect(Collectors.toList())));
    }

    @SuppressWarnings("unused")
    public PersistentState() {
    }
  }
}
