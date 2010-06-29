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

package com.intellij.openapi.vcs.configurable;

import com.intellij.CommonBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class VcsDirectoryConfigurationPanel extends PanelWithButtons implements Configurable {
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final TableView<VcsDirectoryMapping> myDirectoryMappingTable;
  private final ComboboxWithBrowseButton myVcsComboBox = new ComboboxWithBrowseButton();
  private final List<ModuleVcsListener> myListeners = new ArrayList<ModuleVcsListener>();

  private final ColumnInfo<VcsDirectoryMapping, String> DIRECTORY = new ColumnInfo<VcsDirectoryMapping, String>(VcsBundle.message("column.info.configure.vcses.directory")) {
    public String valueOf(final VcsDirectoryMapping mapping) {
      String directory = mapping.getDirectory();
      if (directory.length() == 0) {
        return "<Project Root>";
      }
      VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir != null) {
        return FileUtil.getRelativePath(new File(baseDir.getPath()), new File(directory));
      }
      return directory;
    }
  };


  private final ColumnInfo<VcsDirectoryMapping, String> VCS_SETTING = new ColumnInfo<VcsDirectoryMapping, String>(VcsBundle.message("comumn.name.configure.vcses.vcs")) {
    public String valueOf(final VcsDirectoryMapping object) {
      return object.getVcs();
    }

    public boolean isCellEditable(final VcsDirectoryMapping o) {
      return true;
    }

    public void setValue(final VcsDirectoryMapping o, final String aValue) {
      Collection<AbstractVcs> activeVcses = getActiveVcses();
      o.setVcs(aValue);
      checkNotifyListeners(activeVcses);
    }

    public TableCellRenderer getRenderer(final VcsDirectoryMapping p0) {
      return new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          final String vcsName = p0.getVcs();
          String text;
          if (vcsName.length() == 0) {
            text = VcsBundle.message("none.vcs.presentation");
          }
          else {
            final AbstractVcs vcs = myVcsManager.findVcsByName(vcsName);
            if (vcs != null) {
              text = vcs.getDisplayName();
            }
            else {
              text = VcsBundle.message("unknown.vcs.presentation", vcsName);
            }
          }
          append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, table.getForeground()));
        }
      };
    }

    @Override
    public TableCellEditor getEditor(final VcsDirectoryMapping o) {
      return new AbstractTableCellEditor() {
        public Object getCellEditorValue() {
          final VcsWrapper selectedVcs = (VcsWrapper) myVcsComboBox.getComboBox().getSelectedItem();
          return ((selectedVcs == null) || (selectedVcs.getOriginal() == null)) ? "" : selectedVcs.getOriginal().getName();
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          String vcsName = (String) value;
          myVcsComboBox.getComboBox().setSelectedItem(VcsWrapper.fromName(myProject, vcsName));
          return myVcsComboBox;
        }
      };
    }
  };
  private ListTableModel<VcsDirectoryMapping> myModel;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;

  public VcsDirectoryConfigurationPanel(final Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);

    myDirectoryMappingTable = new TableView<VcsDirectoryMapping>();
    initializeModel();

    myVcsComboBox.getComboBox().setModel(buildVcsWrappersModel(myProject));
    myVcsComboBox.getComboBox().setRenderer(new EditorComboBoxRenderer(myVcsComboBox.getComboBox().getEditor()));
    myVcsComboBox.getComboBox().addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        if (myDirectoryMappingTable != null && myDirectoryMappingTable.isEditing()) {
          myDirectoryMappingTable.stopEditing();
        }
      }
    });
    myVcsComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VcsWrapper vcsWrapper = ((VcsWrapper)myVcsComboBox.getComboBox().getSelectedItem());
        AbstractVcs abstractVcs = null;
        if (vcsWrapper != null){
          abstractVcs = vcsWrapper.getOriginal();
        }
        new VcsConfigurationsDialog(project, myVcsComboBox.getComboBox(), abstractVcs).show();
      }
    });

    myDirectoryMappingTable.setRowHeight(myVcsComboBox.getPreferredSize().height);
    myDirectoryMappingTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtons();
      }
    });
    initPanel();
    updateButtons();
  }

  private void initializeModel() {
    List<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>();
    for(VcsDirectoryMapping mapping: ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()));
    }
    myModel = new ListTableModel<VcsDirectoryMapping>(new ColumnInfo[]{DIRECTORY, VCS_SETTING}, mappings, 0);
    myDirectoryMappingTable.setModel(myModel);
  }

  private void updateButtons() {
    final boolean hasSelection = myDirectoryMappingTable.getSelectedObject() != null;
    myEditButton.setEnabled(hasSelection);
    myRemoveButton.setEnabled(hasSelection);
  }

  public static DefaultComboBoxModel buildVcsWrappersModel(final Project project) {
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(project).getAllVcss();
    VcsWrapper[] vcsWrappers = new VcsWrapper[vcss.length+1];
    vcsWrappers [0] = new VcsWrapper(null);
    for(int i=0; i<vcss.length; i++) {
      vcsWrappers [i+1] = new VcsWrapper(vcss [i]);
    }
    return new DefaultComboBoxModel(vcsWrappers);
  }

  protected String getLabelText() {
    return null;
  }

  protected JButton[] createButtons() {
    myAddButton = new JButton(CommonBundle.message("button.add"));
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        addMapping();
      }
    });
    myEditButton = new JButton(CommonBundle.message("button.edit"));
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        editMapping();
      }
    });
    myRemoveButton = new JButton(CommonBundle.message("button.remove"));
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        removeMapping();
      }
    });
    return new JButton[] {myAddButton, myEditButton, myRemoveButton};
  }

  private void addMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.add.title"));
    dlg.show();
    if (dlg.isOK()) {
      VcsDirectoryMapping mapping = new VcsDirectoryMapping();
      dlg.saveToMapping(mapping);
      List<VcsDirectoryMapping> items = new ArrayList<VcsDirectoryMapping>(myModel.getItems());
      items.add(mapping);
      myModel.setItems(items);
      checkNotifyListeners(activeVcses);
    }
  }

  private void editMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.remove.title"));
    final VcsDirectoryMapping mapping = myDirectoryMappingTable.getSelectedObject();
    dlg.setMapping(mapping);
    dlg.show();
    if (dlg.isOK()) {
      dlg.saveToMapping(mapping);
      myModel.fireTableDataChanged();
      checkNotifyListeners(activeVcses);
    }
  }

  private void removeMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    ArrayList<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>(myModel.getItems());
    int index = myDirectoryMappingTable.getSelectionModel().getMinSelectionIndex();
    Collection<VcsDirectoryMapping> selection = myDirectoryMappingTable.getSelection();
    mappings.removeAll(selection);
    myModel.setItems(mappings);
    if (mappings.size() > 0) {
      if (index >= mappings.size()) {
        index = mappings.size()-1;
      }
      myDirectoryMappingTable.getSelectionModel().setSelectionInterval(index, index);
    }
    checkNotifyListeners(activeVcses);
  }

  protected JComponent createMainComponent() {
    return new JBScrollPane(myDirectoryMappingTable);
  }

  public void reset() {
    initializeModel();
  }

  public void apply() {
    myVcsManager.setDirectoryMappings(myModel.getItems());
    initializeModel();
  }

  public boolean isModified() {
    return !myModel.getItems().equals(myVcsManager.getDirectoryMappings());
  }

  public void addVcsListener(final ModuleVcsListener moduleVcsListener) {
    myListeners.add(moduleVcsListener);
  }

  public void removeVcsListener(final ModuleVcsListener moduleVcsListener) {
    myListeners.remove(moduleVcsListener);
  }

  private void checkNotifyListeners(Collection<AbstractVcs> oldVcses) {
    Collection<AbstractVcs> vcses = getActiveVcses();
    if (!vcses.equals(oldVcses)) {
      for(ModuleVcsListener listener: myListeners) {
        listener.activeVcsSetChanged(vcses);
      }
    }
  }

  public Collection<AbstractVcs> getActiveVcses() {
    Set<AbstractVcs> vcses = new HashSet<AbstractVcs>();
    for(VcsDirectoryMapping mapping: myModel.getItems()) {
      if (mapping.getVcs().length() > 0) {
        vcses.add(myVcsManager.findVcsByName(mapping.getVcs()));
      }
    }
    return vcses;
  }

  @Nls
  public String getDisplayName() {
    return "Mappings";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return this;
  }

  public void disposeUIResources() {
  }
}
