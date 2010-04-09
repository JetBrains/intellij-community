/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ilyas
 */
public class GrSourceFilterScope extends GlobalSearchScope {
  private final GlobalSearchScope myDelegate;
  private final ProjectFileIndex myIndex;

  public GrSourceFilterScope(final GlobalSearchScope delegate, final Project project) {
    super(project);
    myDelegate = delegate;
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  public boolean contains(final VirtualFile file) {
    return (myDelegate == null || myDelegate.contains(file)) && myIndex.isInSourceContent(file) && GroovyFileType.GROOVY_FILE_TYPE == file.getFileType();
    }
    
  public int compare(final VirtualFile file1, final VirtualFile file2) {
    return myDelegate != null ? myDelegate.compare(file1, file2) : 0;
  }

  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return myDelegate == null || myDelegate.isSearchInModuleContent(aModule);
  }

  public boolean isSearchInLibraries() {
    return myDelegate == null || myDelegate.isSearchInLibraries();
  }
}
