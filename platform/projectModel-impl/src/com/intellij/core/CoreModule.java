/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.mock.MockComponentManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CoreModule extends MockComponentManager implements ModuleEx {
  private final Project myProject;

  public CoreModule(@NotNull Disposable parentDisposable, Project project) {
    super(project.getPicoContainer(), parentDisposable);
    myProject = project;
  }

  @Override
  public void init() {
  }

  @Override
  public void loadModuleComponents() {
  }

  @Override
  public void moduleAdded() {
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void rename(String newName) {
  }

  @Override
  public VirtualFile getModuleFile() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getModuleFilePath() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public String getName() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  @Override
  public void setOption(@NotNull String optionName, @NotNull String optionValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearOption(@NotNull String optionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getOptionValue(@NotNull String optionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithLibrariesScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleContentScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithDependentsScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    throw new UnsupportedOperationException();
  }
}
