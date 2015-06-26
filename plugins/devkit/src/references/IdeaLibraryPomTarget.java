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
package org.jetbrains.idea.devkit.references;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.PomNamedTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class IdeaLibraryPomTarget implements PomNamedTarget {
  private final Project myProject;
  private final Library myLibrary;

  public IdeaLibraryPomTarget(@NotNull Project project, @NotNull Library library) {
    myProject = project;
    myLibrary = library;
  }

  @Override
  public String getName() {
    return myLibrary.getName();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void navigate(boolean requestFocus) {
    ProjectSettingsService.getInstance(myProject).openLibrary(myLibrary);
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }
}
