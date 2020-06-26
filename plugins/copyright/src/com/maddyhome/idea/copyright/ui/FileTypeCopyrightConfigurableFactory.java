// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.ui;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

public final class FileTypeCopyrightConfigurableFactory {
  private static final String[] LOCATIONS_IN_FILE = new String[]{"Before Doctype", "Before Root Tag"};

  private FileTypeCopyrightConfigurableFactory() {
  }

  public static Configurable createFileTypeConfigurable(Project project, FileType fileType, TemplateCommentPanel parentPanel) {
    if (fileType.equals(StdFileTypes.JAVA)) {
      return new TemplateCommentPanel(fileType, parentPanel, new String[]{"Before Package", "Before Imports", "Before Class"}, project);
    }
    else if (fileType.equals(XmlFileType.INSTANCE)) {
      return new TemplateCommentPanel(fileType, parentPanel, LOCATIONS_IN_FILE, project);
    }
    else if (fileType.equals(HtmlFileType.INSTANCE)) {
      return new TemplateCommentPanel(fileType, parentPanel, LOCATIONS_IN_FILE, project);
    }
    else if (fileType.equals(StdFileTypes.JSP)) {
      return new TemplateCommentPanel(fileType, parentPanel, LOCATIONS_IN_FILE, project);
    }
    else if (fileType.equals(StdFileTypes.JSPX)) {
      return new TemplateCommentPanel(fileType, parentPanel, LOCATIONS_IN_FILE, project);
    }
    else {
      return new TemplateCommentPanel(fileType, parentPanel, null, project);
    }
  }


}