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
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
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
    DataContext dataContext = e.getDataContext();
    Presentation presentation = e.getPresentation();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
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

