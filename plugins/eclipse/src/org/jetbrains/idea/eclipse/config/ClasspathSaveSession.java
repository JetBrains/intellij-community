// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config;

import com.intellij.configurationStore.SaveSession;
import com.intellij.configurationStore.SaveSessionProducer;
import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.SafeWriteRequestor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableRunnable;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

final class ClasspathSaveSession implements SaveSessionProducer, SaveSession, SafeWriteRequestor {
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
  public void setState(Object component, @NotNull String componentName, @Nullable Object state) throws IOException {
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

  @Nullable
  @Override
  public SaveSession createSaveSession() {
    return modifiedContent.isEmpty() && deletedContent.isEmpty() ? null : this;
  }

  @Override
  public void save() throws IOException {
    CachedXmlDocumentSet fileSet = EclipseClasspathStorageProvider.getFileCache(module);

    ThrowableRunnable<IOException> runnable = () -> {
      for (String key : modifiedContent.keySet()) {
        Element content = modifiedContent.get(key);
        VirtualFile virtualFile = StorageUtilKt.getOrCreateVirtualFile(Paths.get(fileSet.getParent(key) + '/' + key), this);
        try (Writer writer = new OutputStreamWriter(virtualFile.getOutputStream(this), StandardCharsets.UTF_8)) {
          EclipseJDOMUtil.output(content, writer, module.getProject());
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
    };

    Application app = ApplicationManager.getApplication();
    // platform doesn't check isWriteAccessAllowed because wants to track all write action starter classes,
    // but for our case more important to avoid write action start code execution for performance reasons
    if (app.isWriteAccessAllowed()) {
      runnable.run();
    }
    else {
      WriteAction.run(runnable);
    }
  }
}
