/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.core;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.mock.MockComponentManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ModulePathMacroManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.ModuleFileIndexImpl;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CoreModule extends MockComponentManager implements ModuleEx {
  private final String myPath;
  @NotNull private final Disposable myLifetime;
  @NotNull private final Project myProject;
  @NotNull private final ModuleScopeProvider myModuleScopeProvider;

  public CoreModule(@NotNull Disposable parentDisposable, @NotNull Project project, String moduleFilePath) {
    super(project.getPicoContainer(), parentDisposable);
    myLifetime = parentDisposable;
    myProject = project;
    myPath = moduleFilePath;

    Extensions.instantiateArea(ExtensionAreas.IDEA_MODULE, this, null);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        Extensions.disposeArea(CoreModule.this);
      }
    });
    initModuleExtensions();

    final ModuleRootManagerImpl moduleRootManager =
      new ModuleRootManagerImpl(this,
                                ProjectRootManagerImpl.getInstanceImpl(project),
                                VirtualFilePointerManager.getInstance()) {
        @Override
        public void loadState(@NotNull ModuleRootManagerState object) {
          loadState(object, false);
        }
      };
    Disposer.register(parentDisposable, moduleRootManager);
    getPicoContainer().registerComponentInstance(ModuleRootManager.class, moduleRootManager);
    getPicoContainer().registerComponentInstance(PathMacroManager.class, createModulePathMacroManager(project));
    getPicoContainer().registerComponentInstance(ModuleFileIndex.class, createModuleFileIndex(project));
    myModuleScopeProvider = createModuleScopeProvider();
  }

  protected void initModuleExtensions() {
  }

  protected <T> void addModuleExtension(final ExtensionPointName<T> name, final T extension) {
    final ExtensionPoint<T> extensionPoint = Extensions.getArea(this).getExtensionPoint(name);
    extensionPoint.registerExtension(extension);
    Disposer.register(myLifetime, new Disposable() {
      @Override
      public void dispose() {
        extensionPoint.unregisterExtension(extension);
      }
    });
  }

  protected ModuleScopeProvider createModuleScopeProvider() {
    return new CoreModuleScopeProvider();
  }

  // used by Upsource
  protected PathMacroManager createModulePathMacroManager(@SuppressWarnings("unused") @NotNull Project project) {
    return new ModulePathMacroManager(PathMacros.getInstance(), this);
  }

  protected ModuleFileIndex createModuleFileIndex(@NotNull Project project) {
    return new ModuleFileIndexImpl(this, DirectoryIndex.getInstance(project));
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
  public String getModuleFilePath() {
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
    return StringUtil.trimEnd(PathUtil.getFileName(myPath), ModuleFileType.DOT_DEFAULT_EXTENSION);
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
