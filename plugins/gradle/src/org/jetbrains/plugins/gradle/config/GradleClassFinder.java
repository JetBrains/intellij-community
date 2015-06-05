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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.externalSystem.psi.search.ExternalModuleBuildGlobalSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.List;

/**
 * @author peter
 */
public class GradleClassFinder extends NonClasspathClassFinder {

  @NotNull private final GradleBuildClasspathManager myBuildClasspathManager;

  public GradleClassFinder(Project project, @NotNull GradleBuildClasspathManager buildClasspathManager) {
    super(project, JavaFileType.DEFAULT_EXTENSION, GroovyFileType.DEFAULT_EXTENSION);
    myBuildClasspathManager = buildClasspathManager;
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    // do not use default NonClasspathClassFinder caching strategy based on PSI change
    // the caching performed in GradleBuildClasspathManager
    throw new AssertionError();
  }

  @Override
  protected List<VirtualFile> getClassRoots() {
    return myBuildClasspathManager.getAllClasspathEntries();
  }

  @Override
  protected List<VirtualFile> getClassRoots(@Nullable GlobalSearchScope scope) {
    if (scope instanceof ExternalModuleBuildGlobalSearchScope) {
      ExternalModuleBuildGlobalSearchScope externalModuleBuildGlobalSearchScope = (ExternalModuleBuildGlobalSearchScope)scope;
      return myBuildClasspathManager.getModuleClasspathEntries(externalModuleBuildGlobalSearchScope.getExternalModulePath());
    }
    return myBuildClasspathManager.getAllClasspathEntries();
  }
}