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

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.mock.MockProject;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExtensionAreas;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.*;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.impl.libraries.LibraryTablesRegistrarImpl;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;

/**
 * @author yole
 */
public class ProjectModelEnvironment {
  public static void register(CoreEnvironment env) {
    Extensions.registerAreaClass(ExtensionAreas.IDEA_MODULE, null);
    PathMacrosImpl pathMacros = new PathMacrosImpl();
    env.registerApplicationComponent(PathMacros.class, pathMacros);
    CoreEnvironment.registerApplicationExtensionPoint(OrderRootType.EP_NAME, OrderRootType.class);
    CoreEnvironment.registerApplicationExtensionPoint(SdkFinder.EP_NAME, SdkFinder.class);
    CoreEnvironment.registerApplicationExtensionPoint(PathMacroFilter.EP_NAME, PathMacroFilter.class);
    env.getApplication().registerService(ProjectJdkTable.class, new CoreProjectJdkTable());
    env.getApplication().registerService(ApplicationLibraryTable.class, new ApplicationLibraryTable());
    env.getApplication().registerService(LibraryTablesRegistrar.class, new LibraryTablesRegistrarImpl());

    final MockProject project = env.getProject();
    env.registerProjectComponent(ModuleManager.class, new CoreModuleManager(project, env.getParentDisposable()));
    env.registerProjectComponent(PathMacroManager.class, new ProjectPathMacroManager(pathMacros, project));
    env.registerProjectExtensionPoint(DirectoryIndexExcludePolicy.EP_NAME, DirectoryIndexExcludePolicy.class);
    DirectoryIndex index = new DirectoryIndexImpl(project);
    env.registerProjectComponent(DirectoryIndex.class, index);
    env.registerProjectComponent(ProjectRootManager.class, new ProjectRootManagerImpl(project, index));
    project.registerService(ProjectLibraryTable.class, new ProjectLibraryTable());
  }
}
