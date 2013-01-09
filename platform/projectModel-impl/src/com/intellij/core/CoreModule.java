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
import com.intellij.openapi.roots.ModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
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
    CoreApplicationEnvironment.registerExtensionPoint(Extensions.getArea(this), ModuleExtension.EP_NAME, ModuleExtension.class);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        Extensions.disposeArea(CoreModule.this);
      }
    });
    initModuleExtensions();

    final ModuleRootManagerImpl moduleRootManager =
      new ModuleRootManagerImpl(this,
                                DirectoryIndex.getInstance(project),
                                ProjectRootManagerImpl.getInstanceImpl(project),
                                VirtualFilePointerManager.getInstance()) {
        @Override
        public void loadState(ModuleRootManagerState object) {
          loadState(object, false);
        }
      };
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        moduleRootManager.disposeComponent();
      }
    });
    getPicoContainer().registerComponentInstance(ModuleRootManager.class, moduleRootManager);
    getPicoContainer().registerComponentInstance(PathMacroManager.class, new ModulePathMacroManager(PathMacros.getInstance(), this));
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
  public void clearOption(@NotNull String optionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getOptionValue(@NotNull String optionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleScope() {
    return myModuleScopeProvider.getModuleScope();
  }

  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    return myModuleScopeProvider.getModuleScope(includeTests);
  }

  @Override
  public GlobalSearchScope getModuleWithLibrariesScope() {
    return myModuleScopeProvider.getModuleWithLibrariesScope();
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesScope() {
    return myModuleScopeProvider.getModuleWithDependenciesScope();
  }

  @Override
  public GlobalSearchScope getModuleContentScope() {
    return myModuleScopeProvider.getModuleContentScope();
  }

  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    return myModuleScopeProvider.getModuleContentWithDependenciesScope();
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return myModuleScopeProvider.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  @Override
  public GlobalSearchScope getModuleWithDependentsScope() {
    return myModuleScopeProvider.getModuleWithDependentsScope();
  }

  @Override
  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    return myModuleScopeProvider.getModuleTestsWithDependentsScope();
  }

  @Override
  public GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    return myModuleScopeProvider.getModuleRuntimeScope(includeTests);
  }
}
