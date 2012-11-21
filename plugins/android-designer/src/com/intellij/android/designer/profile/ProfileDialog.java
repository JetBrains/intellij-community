/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.profile;

import com.intellij.designer.ModuleProvider;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class ProfileDialog extends DialogWrapper {
  private final JPanel myContentPanel;

  private final JBList myProfileList;
  private final CollectionListModel<Profile> myProfileModel;

  private final JTextField myProfileNameField;
  private DocumentListener myDocumentListener;

  private final ProfileElement myDeviceElement;
  private final ProfileElement myDeviceConfigurationElement;
  private final ProfileElement myTargetElement;
  private final ProfileElement myLocaleElement;
  private final ProfileElement myDockModeElement;
  private final ProfileElement myNightModeElement;
  private final ProfileElement myThemeElement;

  private final ProfileManager myProfileManager;

  public ProfileDialog(ModuleProvider moduleProvider, List<Profile> profiles) {
    super(moduleProvider.getProject(), false);

    setTitle("Edit Profiles");
    getOKAction().putValue(DEFAULT_ACTION, null);
    getCancelAction().putValue(DEFAULT_ACTION, null);

    myProfileModel = new CollectionListModel<Profile>();
    for (Profile profile : profiles) {
      myProfileModel.add(new ProfileWrapper(profile));
    }

    // TODO: DND reorder elements
    myProfileList = new JBList();
    myProfileList.setModel(myProfileModel);
    myProfileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myProfileList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Profile profile = (Profile)value;
        String name = profile.getName();
        if (StringUtil.isEmpty(name)) {
          name = "[None]";
        }
        return super.getListCellRendererComponent(list, name, index, isSelected, cellHasFocus);
      }
    });
    myProfileList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateProfilePanel();
      }
    });

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myProfileList);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        addProfile();
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        removeProfile();
      }
    });
    decorator.setMoveUpAction(null);
    decorator.setMoveDownAction(null);

    myProfileManager = new ProfileManager(moduleProvider, EmptyRunnable.INSTANCE, EmptyRunnable.INSTANCE);
    Disposer.register(myDisposable, myProfileManager);

    myContentPanel = new JPanel(new GridBagLayout());
    myContentPanel.setPreferredSize(new Dimension(600, 500));

    myContentPanel.add(decorator.createPanel(),
                       new GridBagConstraints(0, 0, 3, 1, 0, 1, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
                                              new Insets(0, 0, 0, 0),
                                              0, 0));

    myContentPanel.add(new JLabel("Profile name:"),
                       new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
                                              new Insets(10, 0, 10, 0),
                                              0, 0));
    myProfileNameField = new JTextField();
    myProfileNameField.setEditable(false);
    myContentPanel.add(myProfileNameField,
                       new GridBagConstraints(1, 1, 2, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                              new Insets(10, 0, 10, 0),
                                              0, 0));

    myDeviceElement = new ProfileElement(myContentPanel, "Device:", myProfileManager.getDeviceAction());
    myDeviceConfigurationElement = new ProfileElement(myContentPanel, "Configuration:", myProfileManager.getDeviceConfigurationAction());
    myTargetElement = new ProfileElement(myContentPanel, "Target:", myProfileManager.getTargetAction());
    myLocaleElement = new ProfileElement(myContentPanel, "Locale:", myProfileManager.getLocaleAction());
    myDockModeElement = new ProfileElement(myContentPanel, "Dock Mode:", myProfileManager.getDockModeAction());
    myNightModeElement = new ProfileElement(myContentPanel, "Night Mode:", myProfileManager.getNightModeAction());
    myThemeElement = new ProfileElement(myContentPanel, "Theme:", myProfileManager.getThemeAction());

    init();
    initValidation();

    if (!profiles.isEmpty()) {
      myProfileList.setSelectedIndex(0);
    }
  }

  public List<Profile> getResult() {
    List<Profile> profiles = new ArrayList<Profile>();

    for (Profile profile : myProfileModel.getItems()) {
      if (profile instanceof ProfileWrapper) {
        ProfileWrapper wrapper = (ProfileWrapper)profile;
        profiles.add(wrapper.unwrap());
      }
      else {
        profiles.add(profile);
      }
    }

    return profiles;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProfileList;
  }

  @Override
  protected ValidationInfo doValidate() {
    Set<String> names = new HashSet<String>();
    names.add(Profile.FULL);

    StringBuffer errors = new StringBuffer();

    int size = myProfileModel.getSize();
    for (int i = 0; i < size; i++) {
      String name = myProfileModel.getElementAt(i).getName();
      if (StringUtil.isEmpty(name)) {
        errors.append("Profile(" + (i + 1) + ") has no name. ");
      }
      else if (!names.add(name)) {
        errors.append("Profile(" + (i + 1) + ") name is duplicate. ");
      }
    }

    return errors.length() == 0 ? null : new ValidationInfo(errors.toString(), myProfileNameField);
  }

  private void addProfile() {
    Profile profile = new Profile();
    profile.setName("New Profile");
    myProfileModel.add(profile);
    myProfileList.setSelectedValue(profile, true);
    myProfileNameField.requestFocus();
  }

  private void removeProfile() {
    int index = myProfileList.getSelectedIndex();
    myProfileModel.remove(index);

    int size = myProfileModel.getSize();
    if (index >= size) {
      index = size - 1;
    }
    if (index != -1) {
      myProfileList.setSelectedIndex(index);
    }
  }

  private void updateProfilePanel() {
    int index = myProfileList.getSelectedIndex();
    final Profile profile = index == -1 ? null : myProfileModel.getElementAt(index);
    myProfileManager.setProfile(profile);

    if (myDocumentListener != null) {
      myProfileNameField.getDocument().removeDocumentListener(myDocumentListener);
      myDocumentListener = null;
    }

    if (profile == null) {
      myProfileNameField.setEditable(false);
      myProfileNameField.setText(null);

      myDeviceElement.setVisible(false, true, null);
      myDeviceConfigurationElement.setVisible(false, true, null);
      myTargetElement.setVisible(false, true, null);
      myLocaleElement.setVisible(false, true, null);
      myDockModeElement.setVisible(false, true, null);
      myNightModeElement.setVisible(false, true, null);
      myThemeElement.setVisible(false, true, null);
    }
    else {
      myProfileNameField.setEditable(true);
      myProfileNameField.setText(profile.getName());
      myDocumentListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          updateName(profile);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          updateName(profile);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
          updateName(profile);
        }
      };
      myProfileNameField.getDocument().addDocumentListener(myDocumentListener);

      myProfileManager.updateActions();

      myDeviceElement.setVisible(true, profile.isShowDevice(), new VisibleListener() {
        @Override
        public void setVisible(boolean value) {
          profile.setShowDevice(value);
        }
      });
      myDeviceConfigurationElement.setVisible(true, profile.isShowDeviceConfiguration(), new VisibleListener() {
        @Override
        public void setVisible(boolean value) {
          profile.setShowDeviceConfiguration(value);
        }
      });
      myTargetElement.setVisible(true, profile.isShowTarget(), new VisibleListener() {
        @Override
        public void setVisible(boolean value) {
          profile.setShowTarget(value);
        }
      });
      myLocaleElement.setVisible(true, profile.isShowLocale(), new VisibleListener() {
        @Override
        public void setVisible(boolean value) {
          profile.setShowLocale(value);
        }
      });
      myDockModeElement.setVisible(true, profile.isShowDockMode(), new VisibleListener() {
        @Override
        public void setVisible(boolean value) {
          profile.setShowDockMode(value);
        }
      });
      myNightModeElement.setVisible(true, profile.isShowNightMode(), new VisibleListener() {
        @Override
        public void setVisible(boolean value) {
          profile.setShowNightMode(value);
        }
      });
      myThemeElement.setVisible(true, profile.isShowTheme(), new VisibleListener() {
        @Override
        public void setVisible(boolean value) {
          profile.setShowTheme(value);
        }
      });
    }
  }

  private void updateName(Profile profile) {
    profile.setName(myProfileNameField.getText());
    myProfileList.repaint();
  }

  private static class ProfileElement {
    private final JCheckBox myVisible;
    private ActionListener myListener;

    public ProfileElement(JPanel parent, String name, ComboBoxAction action) {
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.anchor = GridBagConstraints.LINE_START;

      gbc.gridx = 0;
      parent.add(new JLabel(name), gbc);

      gbc.gridx++;
      gbc.weightx = 0.4;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      parent.add(action.createCustomComponent(action.getTemplatePresentation()), gbc);

      myVisible = new JCheckBox("Visible");
      setVisible(false, true, null);

      gbc.gridx++;
      gbc.weightx = 0.6;
      gbc.fill = GridBagConstraints.NONE;
      gbc.insets = new Insets(0, 10, 0, 0);
      parent.add(myVisible, gbc);
    }

    public void setVisible(boolean enabled, boolean value, @Nullable final VisibleListener listener) {
      if (myListener != null) {
        myVisible.removeActionListener(myListener);
        myListener = null;
      }

      myVisible.setSelected(value);
      myVisible.setEnabled(enabled);

      if (enabled) {
        myListener = new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            listener.setVisible(myVisible.isSelected());
          }
        };
        myVisible.addActionListener(myListener);
      }
    }
  }

  private interface VisibleListener {
    void setVisible(boolean value);
  }
}