// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;

import java.io.File;
import java.io.IOException;

public final class DotProjectFileHelper {
  private static final Logger LOG = Logger.getInstance(DotProjectFileHelper.class);

  private DotProjectFileHelper() {
  }

  public static void saveDotProjectFile(@NotNull Module module, @NotNull String storageRoot) throws IOException {
    try {
      Element rootElement;
      if (ModuleType.get(module) instanceof JavaModuleType) {
        rootElement = JDOMUtil.load(DotProjectFileHelper.class.getResource("template.project.xml"));
      }
      else {
        rootElement = JDOMUtil.load(DotProjectFileHelper.class.getResource("template.empty.project.xml"));
      }

      rootElement.getChild(EclipseXml.NAME_TAG).setText(module.getName());

      final File projectFile = new File(storageRoot, EclipseXml.PROJECT_FILE);
      if (!FileUtil.createIfDoesntExist(projectFile)) {
        return;
      }

      EclipseJDOMUtil.output(rootElement, projectFile, module.getProject());
      ApplicationManager.getApplication().runWriteAction(() -> {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(projectFile.getPath()));
      });
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
  }
}
