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

/**
 * @author peter
 */
public class MockModule extends MockComponentManager implements Module {
  private final Project myProject;
  private String myName = "MockModule";

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
  public Path getModuleNioFile() {
    return Paths.get("");
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
}
