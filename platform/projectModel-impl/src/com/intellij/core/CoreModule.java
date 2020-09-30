// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.mock.MockComponentManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ModulePathMacroManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleFileIndexImpl;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author yole
 */
public class CoreModule extends MockComponentManager implements ModuleEx {
  private final Path myPath;
  @NotNull private final Disposable myLifetime;
  @NotNull private final Project myProject;
  @NotNull private final ModuleScopeProvider myModuleScopeProvider;

  public CoreModule(@NotNull Disposable parentDisposable, @NotNull Project project, @NotNull Path moduleFilePath) {
    super(project.getPicoContainer(), parentDisposable);
    myLifetime = parentDisposable;
    myProject = project;
    myPath = moduleFilePath;

    initModuleExtensions();

    Disposable moduleRootManager = new ModuleRootManagerImpl(this) {
      @Override
      public void loadState(@NotNull ModuleRootManagerState object) {
        loadState(object, false);
      }
    };
    Disposer.register(parentDisposable, moduleRootManager);
    getPicoContainer().registerComponentInstance(ModuleRootManager.class, moduleRootManager);
    getPicoContainer().registerComponentInstance(PathMacroManager.class, createModulePathMacroManager(project));
    getPicoContainer().registerComponentInstance(ModuleFileIndex.class, createModuleFileIndex());
    myModuleScopeProvider = createModuleScopeProvider();
  }

  protected void initModuleExtensions() {
  }

  protected <T> void addModuleExtension(@NotNull ExtensionPointName<T> name, @NotNull T extension) {
    //noinspection TestOnlyProblems
    name.getPoint(this).registerExtension(extension, myLifetime);
  }

  protected ModuleScopeProvider createModuleScopeProvider() {
    return new CoreModuleScopeProvider();
  }

  // used by Upsource
  protected PathMacroManager createModulePathMacroManager(@SuppressWarnings("unused") @NotNull Project project) {
    return new ModulePathMacroManager(this);
  }

  protected ModuleFileIndex createModuleFileIndex() {
    return new ModuleFileIndexImpl(this);
  }

  @Override
  public void clearScopesCache() {
    myModuleScopeProvider.clearCache();
  }

  @Override
  public VirtualFile getModuleFile() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Path getModuleNioFile() {
    return myPath;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public String getName() {
    return StringUtil.trimEnd(myPath.getFileName().toString(), ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  @Override
  public void setOption(@NotNull String optionName, @Nullable String optionValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getOptionValue(@NotNull String optionName) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleScope() {
    return myModuleScopeProvider.getModuleScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    return myModuleScopeProvider.getModuleScope(includeTests);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithLibrariesScope() {
    return myModuleScopeProvider.getModuleWithLibrariesScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithDependenciesScope() {
    return myModuleScopeProvider.getModuleWithDependenciesScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentScope() {
    return myModuleScopeProvider.getModuleContentScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    return myModuleScopeProvider.getModuleContentWithDependenciesScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return myModuleScopeProvider.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleWithDependentsScope() {
    return myModuleScopeProvider.getModuleWithDependentsScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    return myModuleScopeProvider.getModuleTestsWithDependentsScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    return myModuleScopeProvider.getModuleRuntimeScope(includeTests);
  }
}
