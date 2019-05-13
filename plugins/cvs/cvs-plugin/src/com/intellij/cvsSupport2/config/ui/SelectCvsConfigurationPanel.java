/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class SelectCvsConfigurationPanel extends JPanel {
  private final DefaultListModel myModel = new DefaultListModel();
  private final JList myList = new JBList(myModel);
  private CvsRootConfiguration mySelection = null;
  private final Project myProject;

  public SelectCvsConfigurationPanel(Project project) {
    super(new BorderLayout(2, 4));
    myProject = project;
    add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    add(createButtonPanel(), BorderLayout.EAST);
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        mySelection = (CvsRootConfiguration)myList.getSelectedValue();
        fireSelectionValueChanged(e.getFirstIndex(), e.getLastIndex(), e.getValueIsAdjusting());
      }
    });
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fillModel(null);
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    listenerList.add(ListSelectionListener.class, listener);
  }

  public void removeListSelectionListener(ListSelectionListener listener) {
    listenerList.remove(ListSelectionListener.class, listener);
  }

  private void fireSelectionValueChanged(int firstIndex, int lastIndex, boolean isAdjusting) {
    final ListSelectionListener[] listeners = getListeners(ListSelectionListener.class);
    if( listeners.length == 0) return;
    final ListSelectionEvent event = new ListSelectionEvent(this, firstIndex, lastIndex, isAdjusting);
    for (ListSelectionListener listener : listeners) {
      listener.valueChanged(event);
    }
  }

  private Component createButtonPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JButton button = new JButton(CvsBundle.message("button.text.configure.cvs.roots"));
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        editConfigurations();
      }
    });
    panel.add(button, BorderLayout.NORTH);
    return panel;
  }

  public void editConfigurations() {
    final CvsApplicationLevelConfiguration configuration = CvsApplicationLevelConfiguration.getInstance();
    final CvsConfigurationsListEditor cvsConfigurationsListEditor =
      new CvsConfigurationsListEditor(new ArrayList<>(configuration.CONFIGURATIONS), myProject);
    final CvsRootConfiguration selectedConfiguration = getSelectedConfiguration();
    if (selectedConfiguration != null) {
      cvsConfigurationsListEditor.selectConfiguration(selectedConfiguration);
    }
    if (cvsConfigurationsListEditor.showAndGet()) {
      configuration.CONFIGURATIONS = cvsConfigurationsListEditor.getConfigurations();
      fillModel(cvsConfigurationsListEditor.getSelectedConfiguration());
    }
  }

  private void fillModel(@Nullable CvsRootConfiguration configurationToSelect) {
    final CvsRootConfiguration selection = configurationToSelect == null ? mySelection : configurationToSelect;
    myModel.removeAllElements();
    final List<CvsRootConfiguration> configurations = CvsApplicationLevelConfiguration.getInstance().CONFIGURATIONS;
    for (CvsRootConfiguration configuration : configurations) {
      if (configuration.CVS_ROOT.isEmpty()) continue;
      myModel.addElement(configuration);
    }
    if (selection != null) myList.setSelectedValue(selection, true);
    if (myList.getSelectedIndex() < 0 && myList.getModel().getSize() > 0) {
      myList.setSelectedIndex(0);
    }
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return mySelection;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }
}
