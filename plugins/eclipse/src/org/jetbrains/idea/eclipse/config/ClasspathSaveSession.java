/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.SafeWriteRequestor;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.conversion.DotProjectFileHelper;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter;
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

final class ClasspathSaveSession implements StateStorage.ExternalizationSession, StateStorage.SaveSession, SafeWriteRequestor {
  private final Map<String, Element> modifiedContent = new THashMap<>();
  private final Set<String> deletedContent = new THashSet<>();

  private final Module module;

  ClasspathSaveSession(@NotNull Module module) {
    this.module = module;
  }

  private void update(@NotNull Element content, @NotNull String name) {
    modifiedContent.put(name, content);
    deletedContent.remove(name);
  }

  void delete(@NotNull String name) {
    modifiedContent.remove(name);
    deletedContent.add(name);
  }

  @Override
  public void setState(Object component, @NotNull String componentName, @NotNull Object state) {
    try {
      CachedXmlDocumentSet fileSet = EclipseClasspathStorageProvider.getFileCache(module);

      Element oldClassPath;
      try {
        oldClassPath = fileSet.load(EclipseXml.CLASSPATH_FILE, true);
      }
      catch (Exception e) {
        EclipseClasspathWriter.LOG.warn(e);
        oldClassPath = null;
      }

      ModuleRootManagerImpl moduleRootManager = (ModuleRootManagerImpl)component;
      if (oldClassPath != null || moduleRootManager.getSourceRoots().length > 0 || moduleRootManager.getOrderEntries().length > 2) {
        Element newClassPathElement = new EclipseClasspathWriter().writeClasspath(oldClassPath, moduleRootManager);
        if (oldClassPath == null || !JDOMUtil.areElementsEqual(newClassPathElement, oldClassPath)) {
          update(newClassPathElement, EclipseXml.CLASSPATH_FILE);
        }
      }

      if (fileSet.getFile(EclipseXml.PROJECT_FILE, true) == null) {
        DotProjectFileHelper.saveDotProjectFile(module, fileSet.getParent(EclipseXml.PROJECT_FILE));
      }

      Element ideaSpecific = new Element(IdeaXml.COMPONENT_TAG);
      String emlFilename = moduleRootManager.getModule().getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;
      if (IdeaSpecificSettings.writeIdeaSpecificClasspath(ideaSpecific, moduleRootManager)) {
        update(ideaSpecific, emlFilename);
      }
      else {
        delete(emlFilename);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  @Override
  public StateStorage.SaveSession createSaveSession() {
    return modifiedContent.isEmpty() && deletedContent.isEmpty() ? null : this;
  }

  @Override
  public void save() throws IOException {
    CachedXmlDocumentSet fileSet = EclipseClasspathStorageProvider.getFileCache(module);

    AccessToken token = WriteAction.start();
    try {
      for (String key : modifiedContent.keySet()) {
        Element content = modifiedContent.get(key);
        String path = fileSet.getParent(key) + '/' + key;
        Writer writer = new OutputStreamWriter(StorageUtil.getOrCreateVirtualFile(this, Paths.get(path)).getOutputStream(this), CharsetToolkit.UTF8_CHARSET);
        try {
          EclipseJDOMUtil.output(content, writer, module.getProject());
        }
        finally {
          writer.close();
        }
      }

      if (deletedContent.isEmpty()) {
        return;
      }

      for (String deleted : deletedContent) {
        VirtualFile file = fileSet.getFile(deleted, false);
        if (file != null) {
          try {
            file.delete(this);
          }
          catch (IOException ignore) {
          }
        }
      }
      deletedContent.clear();
    }
    finally {
      token.finish();
    }
  }
}
