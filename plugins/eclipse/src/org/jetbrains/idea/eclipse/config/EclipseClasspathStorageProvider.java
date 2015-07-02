/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.conversion.DotProjectFileHelper;
import org.jetbrains.idea.eclipse.conversion.EPathUtil;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import java.io.IOException;

/**
 * @author Vladislav.Kaznacheev
 */
public class EclipseClasspathStorageProvider implements ClasspathStorageProvider {
  public static final String DESCR = EclipseBundle.message("eclipse.classpath.storage.description");

  @Override
  @NonNls
  public String getID() {
    return JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID;
  }

  @Override
  @Nls
  public String getDescription() {
    return DESCR;
  }

  @Override
  public void assertCompatible(final ModuleRootModel model) throws ConfigurationException {
    final String moduleName = model.getModule().getName();
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
        if (libraryEntry.isModuleLevel()) {
          final Library library = libraryEntry.getLibrary();
          if (library == null ||
              libraryEntry.getRootUrls(OrderRootType.CLASSES).length != 1 ||
              library.isJarDirectory(library.getUrls(OrderRootType.CLASSES)[0])) {
            throw new ConfigurationException(
              "Library \'" +
              entry.getPresentableName() +
              "\' from module \'" +
              moduleName +
              "\' dependencies is incompatible with eclipse format which supports only one library content root");
          }
        }
      }
    }
    if (model.getContentRoots().length == 0) {
      throw new ConfigurationException("Module \'" + moduleName + "\' has no content roots thus is not compatible with eclipse format");
    }
    final String output = model.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputUrl();
    final String contentRoot = getContentRoot(model);
    if (output == null ||
        !StringUtil.startsWith(VfsUtilCore.urlToPath(output), contentRoot) &&
        PathMacroManager.getInstance(model.getModule()).collapsePath(output).equals(output)) {
      throw new ConfigurationException("Module \'" +
                                       moduleName +
                                       "\' output path is incompatible with eclipse format which supports output under content root only.\nPlease make sure that \"Inherit project compile output path\" is not selected");
    }
  }

  @Override
  public void detach(@NotNull Module module) {
    EclipseModuleManagerImpl.getInstance(module).setDocumentSet(null);
  }

  @Nullable
  @Override
  public ClasspathConverter createConverter(Module module) {
    return new EclipseClasspathConverter(module);
  }

  @Override
  public String getContentRoot(@NotNull ModuleRootModel model) {
    VirtualFile contentRoot = EPathUtil.getContentRoot(model);
    return contentRoot == null ? model.getContentRoots()[0].getPath() : contentRoot.getPath();
  }

  @Override
  public void modulePathChanged(Module module, String path) {
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
      fileCache.register(module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX, ClasspathStorage.getModuleDir(module));
    }
    return fileCache;
  }

  @Override
  public void moduleRenamed(@NotNull Module module, @NotNull String newName) {
    try {
      CachedXmlDocumentSet fileSet = getFileCache(module);
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath(ClasspathStorage.getModuleDir(module));
      VirtualFile source = root == null ? null : root.findChild(module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX);
      if (source != null && source.isValid()) {
        AccessToken token = WriteAction.start();
        try {
          source.rename(this, newName + EclipseXml.IDEA_SETTINGS_POSTFIX);
        }
        finally {
          token.finish();
        }
      }

      DotProjectFileHelper.saveDotProjectFile(module, fileSet.getParent(EclipseXml.PROJECT_FILE));
      fileSet.register(newName + EclipseXml.IDEA_SETTINGS_POSTFIX, ClasspathStorage.getModuleDir(module));
    }
    catch (IOException e) {
      EclipseClasspathWriter.LOG.warn(e);
    }
  }
}
