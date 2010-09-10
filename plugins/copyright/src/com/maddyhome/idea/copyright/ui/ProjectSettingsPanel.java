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

package com.maddyhome.idea.copyright.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.scopeChooser.PackageSetChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.PanelWithButtons;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.*;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

public class ProjectSettingsPanel extends PanelWithButtons {
    private final Project myProject;
  private final CopyrightProfilesPanel myProfilesModel;
  private final CopyrightManager myManager;

    private final TableView<ScopeSetting> myScopeMappingTable;
    private final ListTableModel<ScopeSetting> myScopeMappingModel;
    private final JComboBox myProfilesComboBox = new JComboBox();

    private JButton myAddButton;
    private JButton myRemoveButton;
    private JButton myMoveUpButton;
    private JButton myMoveDownButton;

    private final HyperlinkLabel myScopesLink = new HyperlinkLabel();

    public ProjectSettingsPanel(Project project, CopyrightProfilesPanel profilesModel) {
        myProject = project;
        myProfilesModel = profilesModel;
        myProfilesModel.addItemsChangeListener(new Runnable(){
          public void run() {
            final Object selectedItem = myProfilesComboBox.getSelectedItem();
            fillCopyrightProfiles();
            myProfilesComboBox.setSelectedItem(selectedItem);
            final ArrayList<ScopeSetting> toRemove = new ArrayList<ScopeSetting>();
            for (ScopeSetting setting : myScopeMappingModel.getItems()) {
              if (setting.getProfile() == null) {
                toRemove.add(setting);
              }
            }
            for (ScopeSetting setting : toRemove) {
              myScopeMappingModel.removeRow(myScopeMappingModel.indexOf(setting));
            }
            updateButtons();
          }
        });
        myManager = CopyrightManager.getInstance(project);

        myScopeMappingModel = new ListTableModel<ScopeSetting>(new ColumnInfo[]{SCOPE, SETTING}, new ArrayList<ScopeSetting>(), 0);
        myScopeMappingTable = new TableView<ScopeSetting>(myScopeMappingModel);

      fillCopyrightProfiles();
        myProfilesComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText("No copyright");
                } else {
                    setText(((CopyrightProfile) value).getName());
                }
                return rendererComponent;
            }
        });

        myScopeMappingTable.setRowHeight(myProfilesComboBox.getPreferredSize().height);
        myScopeMappingTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(final ListSelectionEvent e) {
                updateButtons();
            }
        });
        initPanel();

        myScopesLink.setVisible(!myProject.isDefault());
        myScopesLink.setHyperlinkText("Select Scopes to add new scopes or modify existing ones");
        myScopesLink.addHyperlinkListener(new HyperlinkListener() {
          public void hyperlinkUpdate(final HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              final OptionsEditor optionsEditor = OptionsEditor.KEY.getData(DataManager.getInstance().getDataContext());
              if (optionsEditor != null) {
                optionsEditor.select(ScopeChooserConfigurable.class);
              }
            }
          }
        });
    }

    private void updateButtons() {
        myAddButton.setEnabled(!myProfilesModel.getAllProfiles().isEmpty());
        int index = myScopeMappingTable.getSelectedRow();
        if (0 <= index && index < myScopeMappingModel.getRowCount()) {
          myRemoveButton.setEnabled(true);
          myMoveUpButton.setEnabled(index > 0);
          myMoveDownButton.setEnabled(index < myScopeMappingModel.getRowCount() - 1);
        }
        else {
          myRemoveButton.setEnabled(false);
          myMoveUpButton.setEnabled(false);
          myMoveDownButton.setEnabled(false);
        }
    }

    @Nullable
    protected String getLabelText() {
        return null;
    }

    protected JButton[] createButtons() {
        myAddButton = new JButton("Add");
        myAddButton.setMnemonic('d');
        myAddButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                TableUtil.stopEditing(myScopeMappingTable);
                List<ScopeSetting> newList = new ArrayList<ScopeSetting>(myScopeMappingModel.getItems());
                newList.add(new ScopeSetting(DefaultScopesProvider.getAllScope(),
                        myProfilesModel.getAllProfiles().values().iterator().next()));
                myScopeMappingModel.setItems(newList);
                TableUtil.editCellAt(myScopeMappingTable, myScopeMappingModel.getRowCount() - 1, 0);
            }
        });
        myRemoveButton = new JButton("Remove");
        myRemoveButton.setMnemonic('R');
        myRemoveButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                TableUtil.stopEditing(myScopeMappingTable);
                int index = myScopeMappingTable.getSelectedRow();
                if (0 <= index && index < myScopeMappingModel.getRowCount()) {
                    myScopeMappingModel.removeRow(index);
                    if (index < myScopeMappingModel.getRowCount()) {
                        myScopeMappingTable.setRowSelectionInterval(index, index);
                    } else {
                        if (index > 0) {
                            myScopeMappingTable.setRowSelectionInterval(index - 1, index - 1);
                        }
                    }
                    updateButtons();
                }
                myScopeMappingTable.requestFocus();
            }
        });

        myMoveUpButton = new JButton("Move Up");
        myMoveUpButton.setMnemonic('U');
        myMoveUpButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        TableUtil.moveSelectedItemsUp(myScopeMappingTable);
                    }
                }
        );

        myMoveDownButton = new JButton("Move Down");
        myMoveDownButton.setMnemonic('D');
        myMoveDownButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        TableUtil.moveSelectedItemsDown(myScopeMappingTable);
                    }
                }
        );

        return new JButton[]{myAddButton, myRemoveButton, myMoveUpButton, myMoveDownButton};
    }

    private void fillCopyrightProfiles() {
        final DefaultComboBoxModel boxModel = (DefaultComboBoxModel) myProfilesComboBox.getModel();
        boxModel.removeAllElements();
        boxModel.addElement(null);
        for (CopyrightProfile profile : myProfilesModel.getAllProfiles().values()) {
            boxModel.addElement(profile);
        }
    }


    protected JComponent createMainComponent() {
      return ScrollPaneFactory.createScrollPane(myScopeMappingTable);
    }

    public JComponent getMainComponent() {
        final JPanel panel = new JPanel(new BorderLayout(0, 10));
        final LabeledComponent<JComboBox> component = new LabeledComponent<JComboBox>();
        component.setText("Default &project copyright:");
        component.setLabelLocation(BorderLayout.WEST);
        component.setComponent(myProfilesComboBox);
        panel.add(component, BorderLayout.NORTH);
        panel.add(this, BorderLayout.CENTER);
        panel.add(myScopesLink, BorderLayout.SOUTH);
        return panel;
    }

    public boolean isModified() {
        final CopyrightProfile defaultCopyright = myManager.getDefaultCopyright();
        final Object selected = myProfilesComboBox.getSelectedItem();
        if (defaultCopyright != selected) {
            if (selected == null) return true;
            if (defaultCopyright == null) return true;
            if (!defaultCopyright.equals(selected)) return true;
        }
        final Map<String, String> map = myManager.getCopyrightsMapping();
        if (map.size() != myScopeMappingModel.getItems().size()) return true;
        final Iterator<String> iterator = map.keySet().iterator();
        for (ScopeSetting setting : myScopeMappingModel.getItems()) {
            final NamedScope scope = setting.getScope();
            if (!iterator.hasNext()) return true;
            final String scopeName = iterator.next();
            if (!Comparing.strEqual(scopeName, scope.getName())) return true;
            final String profileName = map.get(scope.getName());
            if (profileName == null) return true;
            if (!profileName.equals(setting.getProfileName())) return true;
        }
        return false;
    }

    public void apply() {
        Collection<CopyrightProfile> profiles = new ArrayList<CopyrightProfile>(myManager.getCopyrights());
        myManager.clearCopyrights();
        for (CopyrightProfile profile : profiles) {
            myManager.addCopyright(profile);
        }
        final List<ScopeSetting> settingList = myScopeMappingModel.getItems();
        for (ScopeSetting scopeSetting : settingList) {
            myManager.mapCopyright(scopeSetting.getScope().getName(), scopeSetting.getProfileName());
        }
        myManager.setDefaultCopyright((CopyrightProfile) myProfilesComboBox.getSelectedItem());
    }

    public void reset() {
        myProfilesComboBox.setSelectedItem(myManager.getDefaultCopyright());
        final List<ScopeSetting> mappings = new ArrayList<ScopeSetting>();
        final Map<String, String> copyrights = myManager.getCopyrightsMapping();
        final DependencyValidationManager manager = DependencyValidationManager.getInstance(myProject);
        for (final String scopeName : copyrights.keySet()) {
            final NamedScope scope = manager.getScope(scopeName);
            if (scope != null) {
                mappings.add(new ScopeSetting(scope, copyrights.get(scopeName)));
            } else {
                myManager.unmapCopyright(scopeName);
            }
        }
        myScopeMappingModel.setItems(mappings);
        updateButtons();
    }


    private class ScopeSetting {
      private NamedScope myScope;
      private CopyrightProfile myProfile;
      private String myProfileName;

      private ScopeSetting(NamedScope scope, CopyrightProfile profile) {
            myScope = scope;
            myProfile = profile;
            if (myProfile != null) {
              myProfileName = myProfile.getName();
            }
        }

      public ScopeSetting(NamedScope scope, String profile) {
         myScope = scope;
        myProfileName = profile;
      }

      public CopyrightProfile getProfile() {
            if (myProfileName != null) {
              myProfile = myProfilesModel.getAllProfiles().get(getProfileName());
            }
            return myProfile;
        }

        public void setProfile(@NotNull CopyrightProfile profile) {
          myProfile = profile;
          myProfileName = profile.getName();
        }

        public NamedScope getScope() {
            return myScope;
        }

        public void setScope(NamedScope scope) {
            myScope = scope;
        }

      public String getProfileName() {
        return myProfile != null ? myProfile.getName() : myProfileName;
      }
    }


    private final ColumnInfo<ScopeSetting, CopyrightProfile> SETTING = new ColumnInfo<ScopeSetting, CopyrightProfile>("Copyright") {
        public CopyrightProfile valueOf(final ScopeSetting object) {
            return object.getProfile();
        }

        public boolean isCellEditable(final ScopeSetting o) {
            return true;
        }


        public TableCellRenderer getRenderer(final ScopeSetting scopeSetting) {
            return new DefaultTableCellRenderer() {
                // implements javax.swing.table.TableCellRenderer
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    final Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (!isSelected)
                        setForeground(myProfilesModel.getAllProfiles().get(scopeSetting.getProfileName()) == null ? Color.red : UIUtil.getTableForeground());
                    setText(scopeSetting.getProfileName());
                    return rendererComponent;
                }
            };
        }

        public TableCellEditor getEditor(ScopeSetting scopeSetting) {
            return new AbstractTableCellEditor() {
                private ComboBox myProfilesCombo;

                public Object getCellEditorValue() {
                    return myProfilesCombo.getSelectedItem();
                }

                public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, int row, int column) {
                    final Collection<CopyrightProfile> copyrights = myProfilesModel.getAllProfiles().values();
                    myProfilesCombo = new ComboBox(copyrights.toArray(new CopyrightProfile[copyrights.size()]), 60);
                  myProfilesCombo.addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                      if (table.isEditing()) {
                        stopCellEditing();
                      }
                    }
                  });
                    myProfilesCombo.setRenderer(new DefaultListCellRenderer() {
                        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                            Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                            setText(((CopyrightProfile) value).getName());
                            return rendererComponent;
                        }
                    });
                    return myProfilesCombo;
                }
            };
        }

        public void setValue(ScopeSetting scopeSetting, CopyrightProfile copyrightProfile) {
          if (copyrightProfile != null) {
            scopeSetting.setProfile(copyrightProfile);
          }
        }
    };

    private final ColumnInfo<ScopeSetting, NamedScope> SCOPE = new ColumnInfo<ScopeSetting, NamedScope>("Scope") {

        public boolean isCellEditable(ScopeSetting mapping) {
            return true;
        }

        public TableCellRenderer getRenderer(ScopeSetting mapping) {
            return new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(JTable table,
                                                               Object value,
                                                               boolean isSelected,
                                                               boolean hasFocus,
                                                               int row,
                                                               int column) {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    setText(value == null ? "" : ((NamedScope) value).getName());
                    return this;
                }
            };
        }

        public TableCellEditor getEditor(ScopeSetting mapping) {
            return new AbstractTableCellEditor() {
                private PackageSetChooserCombo myCombo;

                public Object getCellEditorValue() {
                    return myCombo.getSelectedScope();
                }

                public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, int row, int column) {
                    myCombo = new PackageSetChooserCombo(myProject, value == null ? null : ((NamedScope) value).getName(), false);
                  myCombo.getComboBox().addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                      if (table.isEditing()) {
                        stopCellEditing();
                      }
                    }
                  });
                    return new CellEditorComponentWithBrowseButton(myCombo, this);
                }
            };
        }


        public NamedScope valueOf(ScopeSetting mapping) {
            return mapping.getScope();
        }

        public void setValue(ScopeSetting mapping, NamedScope set) {
            mapping.setScope(set);
        }
    };

}
