/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;

public class ConfigTabFactory {
  public static Configurable createConfigTab(Project project, FileType fileType, TemplateCommentPanel parentPanel) {
    // NOTE: If any change is made here you need to update LanguageOptionsFactory and UpdateCopyrightFactory too.
    if (fileType.equals(StdFileTypes.JAVA)) {
      return new TemplateCommentPanel(fileType, parentPanel, new String[]{"Before Package", "Before Imports", "Before Class"}, project);
    }
    else if (fileType.equals(StdFileTypes.XML)) {
      return new TemplateCommentPanel(fileType, parentPanel, new String[]{"Before Doctype", "Before Root Tag"}, project);
    }
    else if (fileType.equals(StdFileTypes.HTML)) {
      return new TemplateCommentPanel(fileType, parentPanel, new String[]{"Before Doctype", "Before Root Tag"}, project);
    }
    else if (fileType.equals(StdFileTypes.JSP)) {
      return new TemplateCommentPanel(fileType, parentPanel, new String[]{"Before Doctype", "Before Root Tag"}, project);
    }
    else {
      return new TemplateCommentPanel(fileType, parentPanel, new String[]{"Top Of File"}, project);
    }
  }

  private ConfigTabFactory() {
  }
}