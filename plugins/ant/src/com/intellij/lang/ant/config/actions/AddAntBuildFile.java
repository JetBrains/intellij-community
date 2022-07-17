// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AddAntBuildFile extends AnAction {
  @Override
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

    final Set<VirtualFile> files = new HashSet<>(List.of(contextFiles));
    for (AntBuildFile buildFile : antConfiguration.getBuildFileList()) {
      VirtualFile virtualFile = buildFile.getVirtualFile();
      if (virtualFile != null) {
        files.remove(virtualFile);
      }
    }

    int filesAdded = 0;
    @Nls final StringBuilder errors = new StringBuilder();

    for (VirtualFile file : files) {
      try {
        if (antConfiguration.addBuildFile(file) != null) {
          filesAdded++;
        }
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
      ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.ANT_BUILD);
      if (window == null) {
        window = ActivateAntToolWindowAction.createToolWindow(project);
      }
      window.activate(null);
    }
  }

  @Override
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
    presentation.setEnabledAndVisible(true);
  }

  private static void disable(Presentation presentation) {
    presentation.setEnabledAndVisible(false);
  }
}

