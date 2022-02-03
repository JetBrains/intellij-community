// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

public final class FileTypeCopyrightConfigurableFactory {

  private FileTypeCopyrightConfigurableFactory() {
  }

  public static Configurable createFileTypeConfigurable(Project project, FileType fileType, TemplateCommentPanel parentPanel) {
    if (fileType.equals(StdFileTypes.JAVA)) {
      return new TemplateCommentPanel(fileType, parentPanel, project,
                                      CopyrightBundle.message("radio.location.in.file.before.package"),
                                      CopyrightBundle.message("radio.location.in.file.before.imports"),
                                      CopyrightBundle.message("radio.location.in.file.before.class"));
    }
    if (fileType.equals(XmlFileType.INSTANCE) ||
        fileType.equals(HtmlFileType.INSTANCE) || fileType.equals(StdFileTypes.JSP) || fileType.equals(StdFileTypes.JSPX)) {
      return new TemplateCommentPanel(fileType, parentPanel, project,
                                      CopyrightBundle.message("radio.location.in.file.before.doctype"),
                                      CopyrightBundle.message("radio.location.in.file.before.root.tag"));
    }
    return new TemplateCommentPanel(fileType, parentPanel, project);
  }
}