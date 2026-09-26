// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * author: lesya
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public final class VirtualFileInfoAction extends AnAction implements DumbAware {

  public static final DateFormat DATE_FORMAT =
    SimpleDateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);


  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String pathToFile = Messages.showInputDialog("Path to file: ",
                                 "Virtual File Info",
                                 Messages.getQuestionIcon());
    if (pathToFile == null) return;
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(pathToFile));
    if (virtualFile == null){
      Messages.showErrorDialog("Cannot find virtual file", "Virtual File Info");
      return;
    } else {
      StringBuilder info = new StringBuilder();
      info.append("Path: ");
      info.append(virtualFile.getPath());
      info.append("\n");
      info.append("Time stamp: ");
      info.append(DATE_FORMAT.format(new Date(virtualFile.getTimeStamp())));
      info.append("\n");
      info.append("isValid: ");
      info.append(virtualFile.isValid());
      info.append("\n");
      info.append("isWritable: ");
      info.append(virtualFile.isWritable());
      info.append("\n");
      info.append("Content: ");
      try {
        info.append(VfsUtil.loadText(virtualFile));
      }
      catch (IOException e1) {
        info.append("<unable to load content>");
        info.append(e1.getMessage());
      }
      info.append("\n");

      Messages.showMessageDialog(info.toString(), "Virtual File Info", Messages.getInformationIcon());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
