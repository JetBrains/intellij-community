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
package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class AddOneFileOptionsDialog extends AbstractAddOptionsDialog{
  private final AddedFileInfo myAddedFileInfo;
  private JPanel myConfirmationPanel;
  private JPanel myPanel;

  public AddOneFileOptionsDialog(Project project,
                                 Options options, AddedFileInfo file) {
    super(project, options);
    myAddedFileInfo = file;
    myAddedFileInfo.setIncluded(true);
    setTitle(CvsBundle.message("dialog.title.add.file.to.cvs", file.getFile().getName()));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    myConfirmationPanel.setLayout(new BorderLayout());
    myConfirmationPanel.add(AbstractAddFileConfirmationPanel.createOn(myAddedFileInfo).getPanel(),
                            BorderLayout.CENTER);
    return myPanel;
  }

}
