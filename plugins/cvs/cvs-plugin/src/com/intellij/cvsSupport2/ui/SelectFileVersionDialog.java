/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnVirtualFiles;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.FilePath;

import javax.swing.*;
import java.util.Collections;

/**
 * author: lesya
 */
public class SelectFileVersionDialog extends DialogWrapper {
  private final DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;
  private final Project myProject;

  public SelectFileVersionDialog(FilePath file, Project project) {
    super(true);
    myProject = project;
    myDateOrRevisionOrTagSettings =
    new DateOrRevisionOrTagSettings(new TagsProviderOnVirtualFiles(Collections.singleton(file)),
                                    myProject);

    myDateOrRevisionOrTagSettings.updateFrom(CvsConfiguration.getInstance(project).SHOW_CHANGES_REVISION_SETTINGS);
    setTitle(CvsBundle.message("dialog.title.select.tag.or.date"));
    init();
  }

  @Override
  protected void doOKAction() {
    try {
      myDateOrRevisionOrTagSettings.saveTo(CvsConfiguration.getInstance(myProject).SHOW_CHANGES_REVISION_SETTINGS);
    }
    finally {
      super.doOKAction();
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myDateOrRevisionOrTagSettings.getPanel();
  }

  public DateOrRevisionSettings getRevisionOrDate() {
    return CvsConfiguration.getInstance(myProject).SHOW_CHANGES_REVISION_SETTINGS;
  }
}
