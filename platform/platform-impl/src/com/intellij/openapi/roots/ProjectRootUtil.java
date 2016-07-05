/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ProjectRootUtil {
  @NotNull
  public static VirtualFile findSymlinkedFileInContent(@NotNull Project project, @NotNull VirtualFile forFile) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);

    if (scope.contains(forFile)) return forFile;

    VirtualFile canonicalForFile = forFile.getCanonicalFile();
    if (canonicalForFile == null) canonicalForFile = forFile; 
    
    Collection<VirtualFile> projectFiles =
      FilenameIndex.getVirtualFilesByName(project, canonicalForFile.getName(), true, scope);

    for (VirtualFile eachContentFile : projectFiles) {
      if (canonicalForFile.equals(eachContentFile.getCanonicalFile())) return eachContentFile;
    }
    
    return forFile;
  }
}
