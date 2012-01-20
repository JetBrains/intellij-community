/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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

  @Override
  public GlobalSearchScope getModuleRuntimeScope(final boolean includeTests) {
    return new MockGlobalSearchScope();
  }

  @Override
  public GlobalSearchScope getModuleScope() {
    return new MockGlobalSearchScope();
  }

  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    return new MockGlobalSearchScope();
  }

  @Override
  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    return new MockGlobalSearchScope();
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(final boolean includeTests) {
    return new MockGlobalSearchScope();

    //throw new UnsupportedOperationException( "Method getModuleWithDependenciesAndLibrariesScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesScope() {
    return new MockGlobalSearchScope();
  }

  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    throw new UnsupportedOperationException("Method getModuleContentWithDependenciesScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public GlobalSearchScope getModuleContentScope() {
    throw new UnsupportedOperationException("Method getModuleContentScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public GlobalSearchScope getModuleWithDependentsScope() {
    throw new UnsupportedOperationException("Method getModuleWithDependentsScope is not yet implemented in " + getClass().getName());
  }

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
