package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;

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
  private final JList myList = new JList(myModel);
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
