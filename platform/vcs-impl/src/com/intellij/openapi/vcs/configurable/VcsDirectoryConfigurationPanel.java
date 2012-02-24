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

package com.intellij.openapi.vcs.configurable;

import com.intellij.CommonBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
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
  private final String myProjectMessage;
  private final ProjectLevelVcsManager myVcsManager;
  private final TableView<VcsDirectoryMapping> myDirectoryMappingTable;
  private final ComboboxWithBrowseButton myVcsComboBox = new ComboboxWithBrowseButton();
  private final List<ModuleVcsListener> myListeners = new ArrayList<ModuleVcsListener>();

  private final MyDirectoryRenderer myDirectoryRenderer;
  private final ColumnInfo<VcsDirectoryMapping, VcsDirectoryMapping> DIRECTORY;
  private JCheckBox myBaseRevisionTexts;
  private ListTableModel<VcsDirectoryMapping> myModel;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private final Map<String, VcsDescriptor> myAllVcss;
  private VcsContentAnnotationConfigurable myRecentlyChangedConfigurable;
  private final boolean myIsDisabled;
  private final VcsConfiguration myVcsConfiguration;

  private static class MyDirectoryRenderer extends ColoredTableCellRenderer {
    private final Project myProject;

    public MyDirectoryRenderer(Project project) {
      myProject = project;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof VcsDirectoryMapping){
        if (((VcsDirectoryMapping)value).isDefaultMapping()) {
          append(VcsDirectoryMapping.PROJECT_CONSTANT);
          return;
        }
        final VcsDirectoryMapping mapping = (VcsDirectoryMapping) value;
        String directory = mapping.getDirectory();
        VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir != null) {
          final File directoryFile = new File(directory);
          File ioBase = new File(baseDir.getPath());
          if (directoryFile.isAbsolute() && ! FileUtil.isAncestor(ioBase, directoryFile, false)) {
            append(directoryFile.getPath());
            return;
          }
          String relativePath = FileUtil.getRelativePath(ioBase, directoryFile);
          if (".".equals(relativePath)) {
            append(ioBase.getPath());
          }
          else {
            append(relativePath);
            append(" (" + ioBase + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      }
    }
  }

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
            final VcsDescriptor vcs = myAllVcss.get(vcsName);
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
          final VcsDescriptor selectedVcs = (VcsDescriptor) myVcsComboBox.getComboBox().getSelectedItem();
          return ((selectedVcs == null) || selectedVcs.isNone()) ? "" : selectedVcs.getName();
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          String vcsName = (String) value;
          myVcsComboBox.getComboBox().setSelectedItem(myAllVcss.get(vcsName));
          return myVcsComboBox;
        }
      };
    }
  };

  public VcsDirectoryConfigurationPanel(final Project project) {
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(myProject);
    myProjectMessage = "<html>" + StringUtil.escapeXml(VcsDirectoryMapping.PROJECT_CONSTANT) + " - " +
                       DefaultVcsRootPolicy.getInstance(myProject).getProjectConfigurationMessage(myProject).replace('\n', ' ') + "</html>";
    myIsDisabled = myProject.isDefault();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    final VcsDescriptor[] vcsDescriptors = myVcsManager.getAllVcss();
    myAllVcss = new HashMap<String, VcsDescriptor>();
    for (VcsDescriptor vcsDescriptor : vcsDescriptors) {
      myAllVcss.put(vcsDescriptor.getName(), vcsDescriptor);
    }

    myDirectoryMappingTable = new TableView<VcsDirectoryMapping>();
    myBaseRevisionTexts = new JCheckBox("Store on shelf base revision texts for files under DVCS");
    initPanel();
    myDirectoryRenderer = new MyDirectoryRenderer(myProject);
    DIRECTORY = new ColumnInfo<VcsDirectoryMapping, VcsDirectoryMapping>(VcsBundle.message("column.info.configure.vcses.directory")) {
      public VcsDirectoryMapping valueOf(final VcsDirectoryMapping mapping) {
        return mapping;
      }

      @Override
      public TableCellRenderer getRenderer(VcsDirectoryMapping vcsDirectoryMapping) {
        return myDirectoryRenderer;
      }
    };
    initializeModel();

    final JComboBox comboBox = myVcsComboBox.getComboBox();
    comboBox.setModel(buildVcsWrappersModel(myProject));
    comboBox.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        if (myDirectoryMappingTable.isEditing()) {
          myDirectoryMappingTable.stopEditing();
        }
      }
    });
    myVcsComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VcsDescriptor vcsWrapper = ((VcsDescriptor)comboBox.getSelectedItem());
        new VcsConfigurationsDialog(project, comboBox, vcsWrapper).show();
      }
    });

    myDirectoryMappingTable.setRowHeight(myVcsComboBox.getPreferredSize().height);
    myDirectoryMappingTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtons();
      }
    });
    updateButtons();
    if (myIsDisabled) {
      myDirectoryMappingTable.setEnabled(false);
      myAddButton.setEnabled(false);
    }
  }

  private void initializeModel() {
    List<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>();
    for(VcsDirectoryMapping mapping: ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()));
    }
    myModel = new ListTableModel<VcsDirectoryMapping>(new ColumnInfo[]{DIRECTORY, VCS_SETTING}, mappings, 0);
    myDirectoryMappingTable.setModelAndUpdateColumns(myModel);

    myRecentlyChangedConfigurable.reset();
    myBaseRevisionTexts.setSelected(myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
  }

  private void updateButtons() {
    final boolean hasSelection = myDirectoryMappingTable.getSelectedObject() != null;
    myEditButton.setEnabled((! myIsDisabled) && hasSelection);
    myRemoveButton.setEnabled((! myIsDisabled) && hasSelection);
  }

  public static DefaultComboBoxModel buildVcsWrappersModel(final Project project) {
    final VcsDescriptor[] vcsDescriptors = ProjectLevelVcsManager.getInstance(project).getAllVcss();
    final VcsDescriptor[] result = new VcsDescriptor[vcsDescriptors.length + 1];
    result[0] = VcsDescriptor.createFictive();
    System.arraycopy(vcsDescriptors, 0, result, 1, vcsDescriptors.length);
    return new DefaultComboBoxModel(result);
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
    // due to wonderful UI designer bug
    dlg.initProjectMessage();
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
    JPanel panel = new JPanel(new BorderLayout());
    final JScrollPane scroll = ScrollPaneFactory.createScrollPane(myDirectoryMappingTable);
    panel.add(scroll, BorderLayout.CENTER);
    final JPanel wrapper = new JPanel(new BorderLayout());
    myRecentlyChangedConfigurable = new VcsContentAnnotationConfigurable(myProject);
    final JBLabel label = new JBLabel(myProjectMessage);
    label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    label.setFontColor(UIUtil.FontColor.BRIGHTER);
    label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
    wrapper.add(label, BorderLayout.CENTER);
    final JBLabel noteLabel = new JBLabel("File texts bigger than " + VcsConfiguration.ourMaximumFileForBaseRevisionSize / 1000 + "K are not stored");
    noteLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    noteLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    noteLabel.setBorder(BorderFactory.createEmptyBorder(2, 25, 5, 0));
    final JPanel twoPanel = new JPanel(new BorderLayout());
    twoPanel.add(myBaseRevisionTexts, BorderLayout.NORTH);
    twoPanel.add(noteLabel, BorderLayout.SOUTH);
    final JPanel wr2 = new JPanel(new BorderLayout());
    wr2.add(twoPanel, BorderLayout.WEST);

    myBaseRevisionTexts.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));

    final JPanel wr3 = new JPanel(new BorderLayout());
    wr3.add(wr2, BorderLayout.NORTH);
    wr3.add(myRecentlyChangedConfigurable.createComponent(), BorderLayout.SOUTH);
    wrapper.add(wr3, BorderLayout.SOUTH);
    panel.add(wrapper, BorderLayout.SOUTH);
    return panel;
  }

  public void reset() {
    initializeModel();
  }

  public void apply() throws ConfigurationException {
    myVcsManager.setDirectoryMappings(myModel.getItems());
    myRecentlyChangedConfigurable.apply();
    myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF = myBaseRevisionTexts.isSelected();
    initializeModel();
  }

  public boolean isModified() {
    if (myRecentlyChangedConfigurable.isModified()) return true;
    if (myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF != myBaseRevisionTexts.isSelected()) return true;
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
