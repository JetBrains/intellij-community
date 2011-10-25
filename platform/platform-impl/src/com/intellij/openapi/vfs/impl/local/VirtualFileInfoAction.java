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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.DumbAware;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * author: lesya
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class VirtualFileInfoAction extends AnAction implements DumbAware {

  public static final DateFormat DATE_FORMAT =
    SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);


  @Override
  public void actionPerformed(AnActionEvent e) {
    String pathToFile = Messages.showInputDialog("Path to file: ",
                                 "Virtual File Info",
                                 Messages.getQuestionIcon());
    if (pathToFile == null) return;
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(pathToFile));
    if (virtualFile == null){
      Messages.showErrorDialog("Cannot find virtual file", "Virtual File Info");
      return;
    } else {
      StringBuffer info = new StringBuffer();
      info.append("Path: ");
      info.append(virtualFile.getPath());
      info.append("\n");
      info.append("Time stamp: ");
      info.append(DATE_FORMAT.format(new Date(virtualFile.getTimeStamp())));
      info.append("\n");
      info.append("isValid: ");
      info.append(String.valueOf(virtualFile.isValid()));
      info.append("\n");
      info.append("isWritable: ");
      info.append(String.valueOf(virtualFile.isWritable()));
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


}
