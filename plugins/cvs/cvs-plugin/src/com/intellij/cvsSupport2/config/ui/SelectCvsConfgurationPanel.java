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

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;

/**
 * author: lesya
 */
public class SelectCvsConfgurationPanel extends JPanel {
  private final DefaultListModel myModel = new DefaultListModel();
  private final JList myList = new JBList(myModel);
  private CvsRootConfiguration mySelection;
  private final Project myProject;
  private final MyObservable myObservable;

  public SelectCvsConfgurationPanel(Project project) {
    super(new BorderLayout(2, 4));
    myProject = project;
    add(createListPanel(), BorderLayout.CENTER);
    add(createButtonPanel(), BorderLayout.EAST);
    myObservable = new MyObservable();
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        mySelection = (CvsRootConfiguration)myList.getSelectedValue();
        myObservable.setChanged();
        myObservable.notifyObservers(mySelection);
      }
    });

    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    fillModel(null);


  }

  private Component createButtonPanel() {
    JPanel result = new JPanel(new BorderLayout());
    JButton jButton = new JButton(com.intellij.CvsBundle.message("button.text.configure.cvs.roots"));
    jButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editConfigurations();
      }
    });
    result.add(jButton, BorderLayout.NORTH);
    return result;
  }

  private JPanel createListPanel() {
    JPanel result = new JPanel(new BorderLayout());
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
    result.add(scrollPane, BorderLayout.CENTER);
    scrollPane.setFocusable(false);
    return result;
  }

  public void editConfigurations() {
    CvsConfigurationsListEditor cvsConfigurationsListEditor =
      new CvsConfigurationsListEditor(new ArrayList<CvsRootConfiguration>(getConfigurationList()), myProject);
    CvsRootConfiguration selectedConfiguration = getSelectedConfiguration();
    if (selectedConfiguration != null) {
      cvsConfigurationsListEditor.selectConfiguration(selectedConfiguration);
    }
    cvsConfigurationsListEditor.show();
    if (cvsConfigurationsListEditor.isOK()) {
      getConfiguration().CONFIGURATIONS =
      new ArrayList<CvsRootConfiguration>(cvsConfigurationsListEditor.getConfigurations());
      fillModel(cvsConfigurationsListEditor.getSelectedConfiguration());
    }
  }

  private void fillModel(Object selectedConfiguration) {
    Object oldSelection = selectedConfiguration == null ? myList.getSelectedValue() : selectedConfiguration;
    myModel.removeAllElements();
    java.util.List configurations = getConfigurationList();
    for (Iterator each = configurations.iterator(); each.hasNext();) {
      CvsRootConfiguration configuration = (CvsRootConfiguration)each.next();
      myModel.addElement(configuration);
    }
    myList.setSelectedValue(oldSelection, true);

    if (myList.getSelectedIndex() < 0 && myList.getModel().getSize() > 0) {
      myList.setSelectedIndex(0);
    }
  }

  private java.util.List<CvsRootConfiguration> getConfigurationList() {
    return getConfiguration().CONFIGURATIONS;
  }

  private CvsApplicationLevelConfiguration getConfiguration() {
    return CvsApplicationLevelConfiguration.getInstance();
  }


  public CvsRootConfiguration getSelectedConfiguration() {
    return mySelection;
  }

  public Observable getObservable() {
    return myObservable;
  }

  public Component getJList() {
    return myList;
  }

  private static class MyObservable extends Observable {
    public synchronized void setChanged() {
      super.setChanged();
    }
  }

}
