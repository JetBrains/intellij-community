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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.AntNoFileException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
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
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AddAntBuildFile extends AnAction {
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final VirtualFile[] contextFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (contextFiles == null || contextFiles.length == 0) {
      return;
    }
    final AntConfiguration antConfiguration = AntConfiguration.getInstance(project);

    final Set<VirtualFile> files = new HashSet<>();
    files.addAll(Arrays.asList(contextFiles));
    for (AntBuildFile buildFile : antConfiguration.getBuildFileList()) {
      files.remove(buildFile.getVirtualFile());
    }
    
    int filesAdded = 0;
    final StringBuilder errors = new StringBuilder();

    for (VirtualFile file : files) {
      try {
        antConfiguration.addBuildFile(file);
        filesAdded++;
      }
      catch (AntNoFileException ex) {
        String message = ex.getMessage();
        if (message == null || message.length() == 0) {
          message = AntBundle.message("cannot.add.build.files.from.excluded.directories.error.message", ex.getFile().getPresentableUrl());
        }
        if (errors.length() > 0) {
          errors.append("\n");
        }
        errors.append(message);
      }
    }

    if (errors.length() > 0) {
      Messages.showWarningDialog(project, errors.toString(), AntBundle.message("cannot.add.build.file.dialog.title"));
    }
    if (filesAdded > 0) {
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.ANT_BUILD).activate(null);
    }
  }

  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getProject();
    if (project != null) {
      final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (files != null && files.length > 0) {
        for (VirtualFile file : files) {
          final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (!(psiFile instanceof XmlFile)) {
            continue;
          }
          final XmlFile xmlFile = (XmlFile)psiFile;
          final XmlDocument document = xmlFile.getDocument();
          if (document == null) {
            continue;
          }
          final XmlTag rootTag = document.getRootTag();
          if (rootTag == null) {
            continue;
          }
          if (!"project".equals(rootTag.getName())) {
            continue;
          }
          if (AntConfigurationBase.getInstance(project).getAntBuildFile(psiFile) != null) {
            continue;
          }
          // found at least one candidate file
          enable(presentation);
          return;
        }
      }
    }

    disable(presentation);
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

