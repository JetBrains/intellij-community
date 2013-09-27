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

package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class GradleClassFinder extends NonClasspathClassFinder {

  @NotNull private final GradleInstallationManager myLibraryManager;

  public GradleClassFinder(Project project, @NotNull GradleInstallationManager manager) {
    super(project, true, true);
    myLibraryManager = manager;
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    final List<VirtualFile> roots = myLibraryManager.getClassRoots(myProject);
    if (roots != null) {
      return roots;
    }
    return Collections.emptyList();
  }
}