// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.JpsFileEntitySource;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeUtils;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.conversion.DotProjectFileHelper;
import org.jetbrains.idea.eclipse.conversion.EPathUtil;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import java.io.IOException;
import java.util.function.Function;

public final class EclipseClasspathStorageProvider implements ClasspathStorageProvider {
  @NotNull
  @Override
  @NonNls
  public String getID() {
    return JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID;
  }

  @NotNull
  @Override
  @Nls
  public String getDescription() {
    return getDescr();
  }

  @Override
  public void assertCompatible(@NotNull final ModuleRootModel model) throws ConfigurationException {
    final String moduleName = model.getModule().getName();
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry libraryEntry) {
        if (libraryEntry.isModuleLevel()) {
          final Library library = libraryEntry.getLibrary();
          if (library == null ||
              libraryEntry.getRootUrls(OrderRootType.CLASSES).length != 1 ||
              library.isJarDirectory(library.getUrls(OrderRootType.CLASSES)[0])) {
            throw new ConfigurationException(
              EclipseBundle.message("incompatible.eclipse.module.format.message.library.root", entry.getPresentableName(), moduleName));
          }
        }
      }
    }
    if (model.getContentRoots().length == 0) {
      throw new ConfigurationException(EclipseBundle.message("incompatible.eclipse.module.format.message.no.content.roots", moduleName));
    }
    final String output = model.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputUrl();
    final String contentRoot = getContentRoot(model);
    if (output == null ||
        !StringUtil.startsWith(VfsUtilCore.urlToPath(output), contentRoot) &&
        PathMacroManager.getInstance(model.getModule()).collapsePath(output).equals(output)) {
      throw new ConfigurationException(EclipseBundle.message("incompatible.eclipse.module.format.message.output", moduleName));
    }
  }

  @Override
  public void detach(@NotNull Module module) {
    EclipseModuleManagerImpl.getInstance(module).setDocumentSet(null);
    updateEntitySource(module, source -> ((EclipseProjectFile)source).getInternalSource());
  }

  private static void updateEntitySource(Module module, Function<? super EntitySource, ? extends EntitySource> updateSource) {
    ModuleBridge moduleBridge = (ModuleBridge)module;
    EntityStorage moduleEntityStorage = moduleBridge.getEntityStorage().getCurrent();
    ModuleEntity moduleEntity = ModuleBridgeUtils.findModuleEntity(moduleBridge, moduleEntityStorage);
    if (moduleEntity != null) {
      EntitySource entitySource = moduleEntity.getEntitySource();
      ModuleManagerBridgeImpl
        .changeModuleEntitySource(moduleBridge, moduleEntityStorage, updateSource.apply(entitySource), moduleBridge.getDiff());
    }
  }

  @Override
  public void attach(@NotNull ModuleRootModel model) {
    updateEntitySource(model.getModule(), source -> {
      VirtualFileUrlManager virtualFileUrlManager = WorkspaceModel.getInstance(model.getModule().getProject()).getVirtualFileUrlManager();
      String contentRoot = getContentRoot(model);
      String classpathFileUrl = VfsUtilCore.pathToUrl(contentRoot) + "/" + EclipseXml.CLASSPATH_FILE;
      return new EclipseProjectFile(virtualFileUrlManager.getOrCreateFromUri(classpathFileUrl), (JpsFileEntitySource)source);
    });
  }

  @Override
  public String getContentRoot(@NotNull ModuleRootModel model) {
    VirtualFile contentRoot = EPathUtil.getContentRoot(model);
    return contentRoot == null ? model.getContentRoots()[0].getPath() : contentRoot.getPath();
  }

  @Override
  public void modulePathChanged(@NotNull Module module) {
    final EclipseModuleManagerImpl moduleManager = EclipseModuleManagerImpl.getInstance(module);
    if (moduleManager != null) {
      moduleManager.setDocumentSet(null);
    }
  }

  @NotNull
  static CachedXmlDocumentSet getFileCache(@NotNull Module module) {
    EclipseModuleManagerImpl moduleManager = EclipseModuleManagerImpl.getInstance(module);
    CachedXmlDocumentSet fileCache = moduleManager != null ? moduleManager.getDocumentSet() : null;
    if (fileCache == null) {
      fileCache = new CachedXmlDocumentSet();
      if (moduleManager != null) {
        moduleManager.setDocumentSet(fileCache);
      }

      String storageRoot = ClasspathStorage.getStorageRootFromOptions(module);
      fileCache.register(EclipseXml.CLASSPATH_FILE, storageRoot);
      fileCache.register(EclipseXml.PROJECT_FILE, storageRoot);
      fileCache.register(EclipseXml.PLUGIN_XML_FILE, storageRoot);
      fileCache.register(module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX, ModuleUtilCore.getModuleDirPath(module));
    }
    return fileCache;
  }

  @Override
  public void moduleRenamed(@NotNull Module module, @NotNull String oldName, @NotNull String newName) {
    try {
      CachedXmlDocumentSet fileSet = getFileCache(module);
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath(ModuleUtilCore.getModuleDirPath(module));
      VirtualFile source = root == null ? null : root.findChild(oldName + EclipseXml.IDEA_SETTINGS_POSTFIX);
      if (source != null && source.isValid()) {
        WriteAction.run(() -> source.rename(this, newName + EclipseXml.IDEA_SETTINGS_POSTFIX));
      }

      DotProjectFileHelper.saveDotProjectFile(module, fileSet.getParent(EclipseXml.PROJECT_FILE));
      fileSet.unregister(oldName + EclipseXml.IDEA_SETTINGS_POSTFIX);
      fileSet.register(newName + EclipseXml.IDEA_SETTINGS_POSTFIX, ModuleUtilCore.getModuleDirPath(module));
    }
    catch (IOException e) {
      EclipseClasspathWriter.LOG.warn(e);
    }
  }

  public static @Nls String getDescr() {
    return EclipseBundle.message("eclipse.classpath.storage.description");
  }
}
