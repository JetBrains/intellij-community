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

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.mock.MockProject;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.*;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.impl.libraries.LibraryTablesRegistrarImpl;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ProjectModel {

  public static class InitApplicationEnvironment {
    protected final CoreApplicationEnvironment myApplicationEnvironment;

    public InitApplicationEnvironment(CoreApplicationEnvironment env) {
      myApplicationEnvironment = env;
      Extensions.registerAreaClass(ExtensionAreas.IDEA_MODULE, null);
      CoreApplicationEnvironment.registerApplicationExtensionPoint(OrderRootType.EP_NAME, OrderRootType.class);
      CoreApplicationEnvironment.registerApplicationExtensionPoint(SdkFinder.EP_NAME, SdkFinder.class);
      CoreApplicationEnvironment.registerApplicationExtensionPoint(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME, PathMacroFilter.class);
      env.registerApplicationComponent(PathMacros.class, createPathMacros());
      env.getApplication().registerService(ProjectJdkTable.class, createProjectJdkTable());
      env.getApplication().registerService(ApplicationLibraryTable.class, createApplicationLibraryTable());
      env.getApplication().registerService(LibraryTablesRegistrar.class, createLibraryTablesRegistar());
    }

    protected PathMacros createPathMacros() {
      return new PathMacrosImpl();
    }

    protected LibraryTablesRegistrar createLibraryTablesRegistar() {
      return new LibraryTablesRegistrarImpl();
    }

    protected ApplicationLibraryTable createApplicationLibraryTable() {
      return new ApplicationLibraryTable();
    }

    protected ProjectJdkTable createProjectJdkTable() {
      return new CoreProjectJdkTable();
    }
  }

  public static class InitProjectEnvironment {
    protected final CoreProjectEnvironment myProjectEnvironment;
    protected final Project myProject;

    public InitProjectEnvironment(CoreProjectEnvironment env) {
      myProjectEnvironment = env;
      final MockProject project = env.getProject();
      myProject = project;

      env.registerProjectExtensionPoint(DirectoryIndexExcludePolicy.EP_NAME, DirectoryIndexExcludePolicy.class);
      env.registerProjectExtensionPoint(ProjectExtension.EP_NAME, ProjectExtension.class);

      env.registerProjectComponent(ModuleManager.class, createModuleManager());
      env.registerProjectComponent(PathMacroManager.class, createProjectPathMacroManager());
      DirectoryIndex index = createDirectoryIndex();
      //env.registerProjectComponent(DirectoryIndex.class, index);
      project.registerService(DirectoryIndex.class, index);
      env.registerProjectComponent(ProjectRootManager.class, createProjectRootManager(index));
      project.registerService(ProjectLibraryTable.class, createProjectLibraryTable());
      project.registerService(ProjectFileIndex.class, createProjectFileIndex(index));
    }

    protected ProjectFileIndexImpl createProjectFileIndex(DirectoryIndex index) {
      return new ProjectFileIndexImpl(myProject, index, FileTypeRegistry.getInstance());
    }

    protected ProjectLibraryTable createProjectLibraryTable() {
      return new ProjectLibraryTable();
    }

    protected ProjectRootManager createProjectRootManager(DirectoryIndex index) {
      return new ProjectRootManagerImpl(myProject, index);
    }

    protected DirectoryIndex createDirectoryIndex() {
      return new DirectoryIndexImpl(myProject) {
        @NotNull
        @Override
        public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
          throw new UnsupportedOperationException();
        }
      };
    }

    private ProjectPathMacroManager createProjectPathMacroManager() {
      return new ProjectPathMacroManager(PathMacros.getInstance(), myProject);
    }

    protected ModuleManager createModuleManager() {
      return new CoreModuleManager(myProject, myProjectEnvironment.getParentDisposable());
    }
  }

}
