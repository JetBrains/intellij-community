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
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 5/16/2014
 */
public class GradleBuildClasspathResolveScopeEnlarger extends ResolveScopeEnlarger {

  @Override
  public SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, Project project) {
    String fileExtension = file.getExtension();
    if (GroovyFileType.DEFAULT_EXTENSION.equals(fileExtension)) {
      GradleClassFinder gradleClassFinder = Extensions.findExtension(PsiElementFinder.EP_NAME, project, GradleClassFinder.class);
      final List<VirtualFile> roots = gradleClassFinder.getClassRoots();
      for (VirtualFile root : roots) {
        if (VfsUtilCore.isAncestor(root, file, true)) {
          return NonClasspathDirectoriesScope.compose(roots);
        }
      }
    }
    return null;
  }
}
