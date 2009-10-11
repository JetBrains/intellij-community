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
package com.intellij.cvsSupport2.ui.experts;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfgurationPanel;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.util.Observable;
import java.util.Observer;

/**
 * author: lesya
 */
public class SelectCVSConfigurationStep extends WizardStep{
  private final SelectCvsConfgurationPanel mySelectCvsConfgurationPanel;
  private final Observer myObserver;


  public SelectCVSConfigurationStep(Project project, CvsWizard wizard) {
    super(com.intellij.CvsBundle.message("dialog.title.select.cvs.configuration"), wizard);
    mySelectCvsConfgurationPanel = new SelectCvsConfgurationPanel(project);
    myObserver = new Observer() {
          public void update(Observable o, Object arg) {
            getWizard().updateStep();
          }
        };
    mySelectCvsConfgurationPanel.getObservable().addObserver(myObserver);
    init();
  }

  protected void dispose() {
    mySelectCvsConfgurationPanel.getObservable().deleteObserver(myObserver);
  }

  public boolean nextIsEnabled() {
    return mySelectCvsConfgurationPanel.getSelectedConfiguration() != null;
  }

  public boolean setActive() {
    return true;
  }

  protected JComponent createComponent() {
    JPanel result = new JPanel(new BorderLayout());
    result.add(mySelectCvsConfgurationPanel, BorderLayout.CENTER);
    JPanel buttonPanel = new JPanel(new BorderLayout());
    result.add(buttonPanel, BorderLayout.SOUTH);
    return result;
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return mySelectCvsConfgurationPanel.getSelectedConfiguration();
  }

  public Component getPreferredFocusedComponent() {
    return mySelectCvsConfgurationPanel.getJList();
  }
}
