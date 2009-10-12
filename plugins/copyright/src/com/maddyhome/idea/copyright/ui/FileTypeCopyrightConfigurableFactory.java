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

package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

public class FileTypeCopyrightConfigurableFactory {
  private static final String[] LOCATIONS_IN_FILE = new String[]{"Before Doctype", "Before Root Tag"};

  private FileTypeCopyrightConfigurableFactory() {
  }

  public static Configurable createFileTypeConfigurable(Project project, FileType fileType, TemplateCommentPanel parentPanel) {
    if (fileType.equals(StdFileTypes.JAVA)) {
      return new TemplateCommentPanel(fileType, parentPanel, new String[]{"Before Package", "Before Imports", "Before Class"}, project);
    }
    else if (fileType.equals(StdFileTypes.XML)) {
      return new TemplateCommentPanel(fileType, parentPanel, LOCATIONS_IN_FILE, project);
    }
    else if (fileType.equals(StdFileTypes.HTML)) {
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