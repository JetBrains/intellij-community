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
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

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
      Messages.showWarningDialog(project, AntBundle.message("cannot.add.build.files.from.excluded.directories.error.message",
                                                            e.getFile().getPresentableUrl()),
                                          AntBundle.message("cannot.add.build.file.dialog.title"));
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    boolean isXml = StdFileTypes.XML.equals(FileTypeManager.getInstance().getFileTypeByFile(file));
    if (!isXml) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    for (final AntBuildFile buildFile : AntConfiguration.getInstance(project).getBuildFiles()) {
      if (file.equals(buildFile.getVirtualFile())) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
  }
}

