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
package com.intellij.cvsSupport2.connections.local.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.LocalSettings;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class LocalConnectionSettingsPanel {
  private TextFieldWithBrowseButton myPathToCvsClient;
  private JPanel myPanel;

  public LocalConnectionSettingsPanel(Project project) {
    CvsConfigurationPanel.addBrowseHandler(project, myPathToCvsClient, CvsBundle.message("dialog.title.select.path.to.cvs.client"));
  }

  public void updateFrom(LocalSettings localConfiguration) {
    myPathToCvsClient.setText(localConfiguration.PATH_TO_CVS_CLIENT);
  }

  public boolean equalsTo(LocalSettings local_configuration) {
    return myPathToCvsClient.getText().equals(local_configuration.PATH_TO_CVS_CLIENT);
  }

  public void saveTo(LocalSettings localConfiguration) {
    localConfiguration.PATH_TO_CVS_CLIENT = myPathToCvsClient.getText().trim();
    localConfiguration.setCvsClientVerified(false);
  }

  public Component getPanel() {
    return myPanel;
  }
}
