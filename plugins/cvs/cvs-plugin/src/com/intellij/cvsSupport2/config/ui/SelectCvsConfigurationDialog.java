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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfgurationPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.util.Observable;
import java.util.Observer;

/**
 * author: lesya
 */
public class SelectCvsConfigurationDialog extends DialogWrapper {
  private final SelectCvsConfgurationPanel myPanel;

  public SelectCvsConfigurationDialog(Project project) {
    super(true);
    myPanel = new SelectCvsConfgurationPanel(project);
    setOKActionEnabled(myPanel.getSelectedConfiguration() != null);

    myPanel.getObservable().addObserver(new Observer() {
      public void update(Observable o, Object arg) {
        setOKActionEnabled(myPanel.getSelectedConfiguration() != null);
      }
    });
    setTitle(com.intellij.CvsBundle.message("dialog.title.select.cvs.root.configuration"));

    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return (JComponent)myPanel.getJList();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return myPanel.getSelectedConfiguration();
  }


}
