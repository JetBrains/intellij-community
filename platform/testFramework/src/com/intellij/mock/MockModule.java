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
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class MockModule extends MockComponentManager implements Module {
  private final Project myProject;

  public MockModule(@NotNull Disposable parentDisposable) {
    this(null, parentDisposable);
  }

  public MockModule(@Nullable final Project project, @NotNull Disposable parentDisposable) {
    super(project == null ? null : project.getPicoContainer(), parentDisposable);
    myProject = project;
  }

  @Override
  public VirtualFile getModuleFile() {
    throw new UnsupportedOperationException("Method getModuleFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public String getModuleFilePath() {
    return "";
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleRuntimeScope(final boolean includeTests) {
    return new MockGlobalSearchScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleScope() {
    return new MockGlobalSearchScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    return new MockGlobalSearchScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    return new MockGlobalSearchScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(final boolean includeTests) {
    return new MockGlobalSearchScope();

    //throw new UnsupportedOperationException( "Method getModuleWithDependenciesAndLibrariesScope is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithDependenciesScope() {
    return new MockGlobalSearchScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    throw new UnsupportedOperationException("Method getModuleContentWithDependenciesScope is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentScope() {
    throw new UnsupportedOperationException("Method getModuleContentScope is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithDependentsScope() {
    throw new UnsupportedOperationException("Method getModuleWithDependentsScope is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithLibrariesScope() {
    throw new UnsupportedOperationException("Method getModuleWithLibrariesScope is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public String getName() {
    return "MockModule";
  }

  @Override
  public boolean isLoaded() {
    return !isDisposed();
  }

  @Override
  @Nullable
  public String getOptionValue(@NotNull final String optionName) {
    throw new UnsupportedOperationException("Method getOptionValue is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public void setOption(@NotNull final String optionName, @NotNull final String optionValue) {
    throw new UnsupportedOperationException("Method setOption is not yet implemented in " + getClass().getName());
  }

  @Override
  public void clearOption(@NotNull String optionName) {
    throw new UnsupportedOperationException("Method clearOption is not yet implemented in " + getClass().getName());
  }
}
