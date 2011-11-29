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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * author: lesya
 */
public class SelectCvsConfigurationDialog extends DialogWrapper {
  private final SelectCvsConfigurationPanel myPanel;
  private final ListSelectionListener myListener;

  public SelectCvsConfigurationDialog(Project project) {
    super(true);
    myPanel = new SelectCvsConfigurationPanel(project);
    setOKActionEnabled(myPanel.getSelectedConfiguration() != null);

    myListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        setOKActionEnabled(myPanel.getSelectedConfiguration() != null);
      }
    };
    myPanel.addListSelectionListener(myListener);
    setTitle(CvsBundle.message("dialog.title.select.cvs.root.configuration"));
    init();
  }

  @Override
  protected void dispose() {
    myPanel.removeListSelectionListener(myListener);
    super.dispose();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return myPanel.getSelectedConfiguration();
  }
}
