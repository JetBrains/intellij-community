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

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsActionPlaces;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.ui.CvsRootChangeListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * author: lesya
 */
public class CvsConfigurationsListEditor extends DialogWrapper implements DataProvider{
  private final BorderLayout myCenterPanelLayout = new BorderLayout();
  private final JPanel myCenterPanel = new JPanel(myCenterPanelLayout);
  private final JList myList = new JBList();
  private final DefaultListModel myModel = new DefaultListModel();
  private CvsRootConfiguration mySelection;

  private final Cvs2SettingsEditPanel myCvs2SettingsEditPanel;
  @NonNls private static final String SAMPLE_CVSROOT = ":pserver:user@host/server/home/user/cvs";
  private boolean myIsReadOnly = false;

  public CvsConfigurationsListEditor(List<CvsRootConfiguration> configs, Project project) {
    super(true);
    myCvs2SettingsEditPanel = new Cvs2SettingsEditPanel(project);
    setTitle(CvsBundle.message("operation.name.edit.configurations"));
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectNone();
    fillModel(configs);

    myCvs2SettingsEditPanel.addCvsRootChangeListener(new CvsRootChangeListener() {
      public void onCvsRootChanged() {
        if (mySelection == null) return;
        myCvs2SettingsEditPanel.saveTo(mySelection, false);
        myList.repaint();
      }
    });

    setTitle(CvsBundle.message("dialog.title.cvs.roots"));

    if (!configs.isEmpty()) {
      myList.setSelectedIndex(0);
    }
    init();

  }

  @Nullable
  public static CvsRootConfiguration reconfigureCvsRoot(String root, Project project){
    CvsApplicationLevelConfiguration configuration = CvsApplicationLevelConfiguration.getInstance();
    CvsRootConfiguration selectedConfig = configuration.getConfigurationForCvsRoot(root);
    ArrayList<CvsRootConfiguration> modifiableList = new ArrayList<CvsRootConfiguration>(configuration.CONFIGURATIONS);
    CvsConfigurationsListEditor editor = new CvsConfigurationsListEditor(modifiableList, project);
    editor.select(selectedConfig);
    editor.setReadOnly();
    editor.show();
    if (editor.isOK()){
      configuration.CONFIGURATIONS = modifiableList;
      return configuration.getConfigurationForCvsRoot(root);
    } else {
      return null;
    }
  }

  private void setReadOnly() {
    myIsReadOnly = true;
    myList.setEnabled(false);
    myCvs2SettingsEditPanel.setReadOnly();
  }

  protected Action[] createLeftSideActions() {
    AbstractAction globalSettingsAction = new AbstractAction(CvsBundle.message("button.text.global.settings")) {
      public void actionPerformed(ActionEvent e) {
        new ConfigureCvsGlobalSettingsDialog().show();
      }
    };
    return new Action[]{globalSettingsAction};
  }

  protected void doOKAction() {
    if (saveSelectedConfiguration()) {
      super.doOKAction();
    }
  }


  private void fillModel(List<CvsRootConfiguration> configurations) {
    for (final CvsRootConfiguration configuration : configurations) {
      myModel.addElement(configuration.getMyCopy());
    }
  }

  private JComponent createListPanel() {
    return ScrollPaneFactory.createScrollPane(myList);
  }

  private JPanel createActionsPanel() {
    DefaultActionGroup commonActionGroup = new DefaultActionGroup();
    commonActionGroup.add(new MyAddAction());
    commonActionGroup.add(new MyRemoveAction());
    commonActionGroup.add(new MyCopyAction());

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(
      CvsActionPlaces.CVS_CONFIGURATIONS_TOOLBAR,
      commonActionGroup, true);

    JPanel actionPanel = new JPanel(new BorderLayout());
    actionPanel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    return actionPanel;
  }

  protected JComponent createCenterPanel() {
    myList.setCellRenderer(new CvsListCellRenderer());

    myCenterPanelLayout.setHgap(6);

    myCenterPanel.add(createActionsPanel(), BorderLayout.NORTH);
    JComponent listPanel = createListPanel();

    myCenterPanel.add(listPanel, BorderLayout.CENTER);
    myCenterPanel.add(createCvsConfigurationPanel(), BorderLayout.EAST);
    myCenterPanel.add(new JSeparator(JSeparator.HORIZONTAL), BorderLayout.SOUTH);

    myList.setModel(myModel);

    addSelectionListener();


    int minWidth = myList.getFontMetrics(myList.getFont()).stringWidth(SAMPLE_CVSROOT) + 40;
    Dimension minSize = new Dimension(minWidth, myList.getMaximumSize().height);
    listPanel.setMinimumSize(minSize);
    listPanel.setPreferredSize(minSize);
    return myCenterPanel;
  }

  private JComponent createCvsConfigurationPanel() {
    return myCvs2SettingsEditPanel.getPanel();
  }

  private boolean saveSelectedConfiguration() {
    if (getSelectedConfiguration() == null) return true;
    return myCvs2SettingsEditPanel.saveTo(getSelectedConfiguration(), true);
  }

  private void copySelectedConfiguration() {
    if (!saveSelectedConfiguration()) return;
    CvsRootConfiguration newConfig = mySelection.getMyCopy();
    myModel.addElement(newConfig);
    myList.setSelectedValue(newConfig, true);
  }

  private void editSelectedConfiguration() {
    editConfiguration(mySelection);
    myList.repaint();
  }

  private void removeSelectedConfiguration() {
    int oldSelection = myList.getSelectedIndex();
    myModel.removeElement(mySelection);
    int size = myList.getModel().getSize();
    int newSelection = oldSelection < size ? oldSelection : size - 1;
    if (newSelection >= 0 && newSelection < size) {
      myList.setSelectedIndex(newSelection);
    }
  }

  private void createNewConfiguration() {
    if (!saveSelectedConfiguration()) return;
    myList.setSelectedValue(null, false);
    CvsRootConfiguration newConfig = CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance());
    myModel.addElement(newConfig);
    myList.setSelectedValue(newConfig, true);
  }

  private void editConfiguration(CvsRootConfiguration newConfig) {
    myCvs2SettingsEditPanel.updateFrom(newConfig);
  }

  private void addSelectionListener() {
    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= myModel.getSize()) {
          selectNone();
        }
        else {
          CvsRootConfiguration newSelection = (CvsRootConfiguration)myModel.getElementAt(selectedIndex);
          if (newSelection == mySelection) return;
          if (!select(newSelection)) {
            myList.setSelectedValue(mySelection, true);
          }
        }
      }
    });
  }

  private boolean select(CvsRootConfiguration cvs2Configuration) {
    if (mySelection != null) {
      if (!myCvs2SettingsEditPanel.saveTo(mySelection, true)) {
        return false;
      }
    }
    mySelection = cvs2Configuration;
    editSelectedConfiguration();
    return true;
  }

  private void selectNone() {
    myCvs2SettingsEditPanel.disable();
    mySelection = null;
  }

  public ArrayList<CvsRootConfiguration> getConfigurations() {
    ArrayList<CvsRootConfiguration> result = new ArrayList<CvsRootConfiguration>();
    Enumeration each = myModel.elements();
    while (each.hasMoreElements()) result.add((CvsRootConfiguration)each.nextElement());
    return result;
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return mySelection;
  }

  public void selectConfiguration(CvsRootConfiguration selectedConfiguration) {
    myList.setSelectedValue(selectedConfiguration, true);
  }

  private class MyAddAction extends AnAction {
    public MyAddAction() {
      super(CvsBundle.message("action.name.add"), null, IconLoader.getIcon("/general/add.png"));
      registerCustomShortcutSet(CommonShortcuts.INSERT, myList);

    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myIsReadOnly);
    }

    public void actionPerformed(AnActionEvent e) {
      createNewConfiguration();
    }
  }

  private class MyRemoveAction extends AnAction {
    public MyRemoveAction() {
      super(CvsBundle.message("action.name.remove"), null, IconLoader.getIcon("/general/remove.png"));
      registerCustomShortcutSet(CommonShortcuts.DELETE, myList);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedConfiguration() != null && !myIsReadOnly);
    }

    public void actionPerformed(AnActionEvent e) {
      removeSelectedConfiguration();
    }
  }

  private class MyCopyAction extends AnAction {
    public MyCopyAction() {
      super(CvsBundle.message("action.name.copy"), null, IconLoader.getIcon("/general/copy.png"));
      registerCustomShortcutSet(new CustomShortcutSet(
        KeyStroke.getKeyStroke(KeyEvent.VK_C, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)),
                                myList);
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedConfiguration() != null && !myIsReadOnly);
    }

    public void actionPerformed(AnActionEvent e) {
      copySelectedConfiguration();
    }
  }

  @NonNls
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)){
      return "reference.versioncontrol.cvs.roots";
    }
    return null;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.versioncontrol.cvs.roots");
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    if (myModel.isEmpty()) return null;
    return myCvs2SettingsEditPanel.getPreferredFocusedComponent();
  }
}
