// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MockModule extends MockComponentManager implements Module {
  private final Project myProject;
  private String myName = "MockModule";

  public MockModule(@NotNull Disposable parentDisposable) {
    this(null, parentDisposable);
  }

  public MockModule(@Nullable MockProject project, @NotNull Disposable parentDisposable) {
    super(project == null ? null : project.getPicoContainer(), parentDisposable);
    myProject = project;
  }

  @Override
  public VirtualFile getModuleFile() {
    throw new UnsupportedOperationException("Method getModuleFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull Path getModuleNioFile() {
    return Paths.get("");
  }

  @Override
  public @NotNull GlobalSearchScope getModuleRuntimeScope(final boolean includeTests) {
    return new MockGlobalSearchScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleProductionSourceScope() {
    throw new UnsupportedOperationException("Method getModuleProductionSourceScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull GlobalSearchScope getModuleTestSourceScope() {
    throw new UnsupportedOperationException("Method getModuleTestSourceScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull GlobalSearchScope getModuleScope() {
    return new MockGlobalSearchScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleScope(boolean includeTests) {
    return new MockGlobalSearchScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleTestsWithDependentsScope() {
    return new MockGlobalSearchScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(final boolean includeTests) {
    return new MockGlobalSearchScope();

    //throw new UnsupportedOperationException( "Method getModuleWithDependenciesAndLibrariesScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependenciesScope() {
    return new MockGlobalSearchScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleContentWithDependenciesScope() {
    throw new UnsupportedOperationException("Method getModuleContentWithDependenciesScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull GlobalSearchScope getModuleContentScope() {
    throw new UnsupportedOperationException("Method getModuleContentScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependentsScope() {
    throw new UnsupportedOperationException("Method getModuleWithDependentsScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithLibrariesScope() {
    throw new UnsupportedOperationException("Method getModuleWithLibrariesScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  public MockModule setName(String name) {
    myName = name;
    return this;
  }

  @Override
  public boolean isLoaded() {
    return !isDisposed();
  }

  @Override
  public @Nullable String getOptionValue(@NotNull String optionName) {
    throw new UnsupportedOperationException("Method getOptionValue is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public void setOption(@NotNull String optionName, @Nullable String optionValue) {
    throw new UnsupportedOperationException("Method setOption is not yet implemented in " + getClass().getName());
  }
}
