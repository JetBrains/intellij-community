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

/*
 * User: anna
 * Date: 16-Mar-2009
 */
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;

import java.io.File;
import java.io.IOException;

public class DotProjectFileHelper {
  private static final Logger LOG = Logger.getInstance("#" + DotProjectFileHelper.class.getName());

  private DotProjectFileHelper() {
  }

  public static void saveDotProjectFile(@NotNull Module module, @NotNull String storageRoot) throws IOException {
    try {
      Document doc;
      if (ModuleType.get(module) instanceof JavaModuleType) {
        doc = JDOMUtil.loadDocument(DotProjectFileHelper.class.getResource("template.project.xml"));
      }
      else {
        doc = JDOMUtil.loadDocument(DotProjectFileHelper.class.getResource("template.empty.project.xml"));
      }

      doc.getRootElement().getChild(EclipseXml.NAME_TAG).setText(module.getName());

      final File projectFile = new File(storageRoot, EclipseXml.PROJECT_FILE);
      if (!FileUtil.createIfDoesntExist(projectFile)) {
        return;
      }

      EclipseJDOMUtil.output(doc.getRootElement(), projectFile, module.getProject());
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(projectFile.getPath()));
        }
      });
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
  }
}
