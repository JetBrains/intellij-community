/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntNoFileException;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class AddAntBuildFile extends AnAction {
  public void actionPerformed(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    AntConfiguration antConfiguration = AntConfiguration.getInstance(project);
    try {
      antConfiguration.addBuildFile(file);
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.ANT_BUILD).activate(null);
    }
    catch (AntNoFileException e) {
      String message = e.getMessage();
      if (message == null || message.length() == 0) {
        message = AntBundle.message("cannot.add.build.files.from.excluded.directories.error.message", e.getFile().getPresentableUrl());
      }

      Messages.showWarningDialog(project, message, AntBundle.message("cannot.add.build.file.dialog.title"));
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      disable(presentation);
      return;
    }

    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) {
      disable(presentation);
      return;
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (!(psiFile instanceof XmlFile)) {
      disable(presentation);
      return;
    }

    final XmlFile xmlFile = (XmlFile)psiFile;
    final XmlDocument document = xmlFile.getDocument();
    if (document == null) {
      disable(presentation);
      return;
    }

    final XmlTag rootTag = document.getRootTag();
    if (rootTag == null) {
      disable(presentation);
      return;
    }

    if (!"project".equals(rootTag.getName())) {
      disable(presentation);
      return;
    }

    for (final AntBuildFile buildFile : AntConfiguration.getInstance(project).getBuildFiles()) {
      if (file.equals(buildFile.getVirtualFile())) {
        disable(presentation);
        return;
      }
    }

    enable(presentation);
  }

  private static void enable(Presentation presentation) {
    presentation.setEnabled(true);
    presentation.setVisible(true);
  }

  private static void disable(Presentation presentation) {
    presentation.setEnabled(false);
    presentation.setVisible(false);
  }
}

