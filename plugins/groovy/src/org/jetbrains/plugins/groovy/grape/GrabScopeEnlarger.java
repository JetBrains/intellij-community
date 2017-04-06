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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author a.afanasiev
 */
public class GrabScopeEnlarger extends ResolveScopeEnlarger {
  @Override
  @Nullable
  public SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
    if (file.getFileType().equals(GroovyFileType.GROOVY_FILE_TYPE)) {
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      Module module = ModuleUtilCore.findModuleForFile(file, project);
      List<VirtualFile> roots;
      boolean isSource = fileIndex.isInSource(file);
      if (module == null || !isSource) {
        roots = GrabService.getInstance(project).getDependencies(file);
      } else {
        boolean isInTest = fileIndex.isInTestSourceContent(file);
        roots = GrabService.getInstance(project).getDependencies(module.getModuleScope(isInTest));
      }
      GrabService.LOG.trace("Grab scope enlarger " + file + " roots " + String.join(",", roots.stream().map(String::valueOf).collect(
        Collectors.toList())));
      return NonClasspathDirectoriesScope.compose(roots);
    }
    return null;
  }
}

