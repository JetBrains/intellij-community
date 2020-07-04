// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.configurationStore.DefaultStateSerializerKt;
import com.intellij.mock.MockProject;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.stores.DirectoryStorageUtil;
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryStateSplitter;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

/**
 * @author yole
 */
public final class CoreProjectLoader {
  public static boolean loadProject(MockProject project, @NotNull VirtualFile virtualFile) throws IOException, JDOMException {
    VirtualFile ideaDir = ProjectKt.getProjectStoreDirectory(virtualFile);
    if (ideaDir != null) {
      project.setBaseDir(virtualFile);
      loadDirectoryProject(project, ideaDir);
      return true;
    }

    // TODO load .ipr
    return false;
  }

  private static void loadDirectoryProject(MockProject project, @NotNull VirtualFile dotIdea) throws IOException, JDOMException {
    VirtualFile modulesXml = dotIdea.findChild("modules.xml");
    if (modulesXml == null)
      throw new FileNotFoundException("Missing 'modules.xml' in " + dotIdea.getPath());

    Map<String, Element> storageData = loadStorageFile(project, modulesXml);
    final Element moduleManagerState = storageData.get("ProjectModuleManager");
    if (moduleManagerState == null) {
      throw new JDOMException("cannot find ProjectModuleManager state in modules.xml");
    }
    final CoreModuleManager moduleManager = (CoreModuleManager)ModuleManager.getInstance(project);
    moduleManager.loadState(moduleManagerState);

    VirtualFile miscXml = dotIdea.findChild("misc.xml");
    if (miscXml != null) {
      storageData = loadStorageFile(project, miscXml);
      final Element projectRootManagerState = storageData.get("ProjectRootManager");
      if (projectRootManagerState == null) {
        throw new JDOMException("cannot find ProjectRootManager state in misc.xml");
      }
      ((ProjectRootManagerImpl)ProjectRootManager.getInstance(project)).loadState(projectRootManagerState);
    }

    VirtualFile libraries = dotIdea.findChild("libraries");
    if (libraries != null) {
      Map<String, Element> data = DirectoryStorageUtil.loadFrom(libraries, PathMacroManager.getInstance(project));
      Element libraryTable = DefaultStateSerializerKt.deserializeState(DirectoryStorageUtil.getCompositeState(data, new LibraryStateSplitter()), Element.class, null);
      ((LibraryTableBase) LibraryTablesRegistrar.getInstance().getLibraryTable(project)).loadState(libraryTable);
    }

    moduleManager.loadModules();
    project.projectOpened();
  }

  static @NotNull Map<String, Element> loadStorageFile(@NotNull ComponentManager componentManager, @NotNull VirtualFile modulesXml) throws JDOMException, IOException {
    return FileStorageCoreUtil.load(JDOMUtil.load(modulesXml.getInputStream()), PathMacroManager.getInstance(componentManager), false);
  }
}
