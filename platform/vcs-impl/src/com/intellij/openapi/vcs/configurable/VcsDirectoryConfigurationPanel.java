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

import com.intellij.openapi.actionSystem.AnActionEvent;
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
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
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

import static com.intellij.openapi.vcs.VcsConfiguration.getInstance;
import static com.intellij.openapi.vcs.VcsConfiguration.ourMaximumFileForBaseRevisionSize;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

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
  private final Map<String, VcsDescriptor> myAllVcss;
  private VcsContentAnnotationConfigurable myRecentlyChangedConfigurable;
  private final boolean myIsDisabled;
  private final VcsConfiguration myVcsConfiguration;
  private final @NotNull Map<String, VcsRootChecker> myCheckers;
  private JCheckBox myShowVcsRootErrorNotification;
  private JCheckBox myShowChangedRecursively;
  private VcsLimitHistoryConfigurable myLimitHistory;

  private class MyDirectoryRenderer extends ColoredTableCellRenderer {
    private final Project myProject;

    public MyDirectoryRenderer(Project project) {
      myProject = project;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof VcsDirectoryMapping) {
        VcsDirectoryMapping mapping = (VcsDirectoryMapping)value;
        if (mappingIsError(mapping)) {
          setForeground(Color.RED);
        }
        if (mapping.isDefaultMapping()) {
          append(VcsDirectoryMapping.PROJECT_CONSTANT);
          return;
        }
        String directory = mapping.getDirectory();
        VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir != null) {
          final File directoryFile = new File(StringUtil.trimEnd(StringUtil.trimEnd(directory, "/"), "\\") + "/");
          File ioBase = new File(baseDir.getPath());
          if (directoryFile.isAbsolute() && !FileUtil.isAncestor(ioBase, directoryFile, false)) {
            append(new File(directory).getPath());
            return;
          }
          String relativePath = FileUtil.getRelativePath(ioBase, directoryFile);
          if (".".equals(relativePath) || relativePath == null) {
            append(ioBase.getPath());
          }
          else {
            append(relativePath);
            append(" (" + ioBase + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      }
    }

    private boolean mappingIsError(VcsDirectoryMapping mapping) {
      String vcs = mapping.getVcs();
      VcsRootChecker checker = myCheckers.get(vcs);
      return checker != null && checker.isInvalidMapping(mapping);
    }
  }

  private final ColumnInfo<VcsDirectoryMapping, String> VCS_SETTING =
    new ColumnInfo<VcsDirectoryMapping, String>(VcsBundle.message("column.name.configure.vcses.vcs")) {
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
            final VcsDescriptor selectedVcs = (VcsDescriptor)myVcsComboBox.getComboBox().getSelectedItem();
            return ((selectedVcs == null) || selectedVcs.isNone()) ? "" : selectedVcs.getName();
          }

          public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            String vcsName = (String)value;
            myVcsComboBox.getComboBox().setSelectedItem(myAllVcss.get(vcsName));
            return myVcsComboBox;
          }
        };
      }
    };

  public VcsDirectoryConfigurationPanel(final Project project) {
    myProject = project;
    myVcsConfiguration = getInstance(myProject);
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

    myCheckers = new HashMap<String, VcsRootChecker>();
    updateRootCheckers();

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
    if (myIsDisabled) {
      myDirectoryMappingTable.setEnabled(false);
    }
  }

  private void updateRootCheckers() {
    myCheckers.clear();
    for (VcsDescriptor descriptor : myVcsManager.getAllVcss()) {
      String name = descriptor.getName();
      AbstractVcs vcs = myVcsManager.findVcsByName(name);
      if (vcs == null) {
        continue;
      }
      VcsRootChecker checker = vcs.getRootChecker();
      if (checker != null) {
        myCheckers.put(name, checker);
      }
    }
  }

  private void initializeModel() {
    List<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>();
    for (VcsDirectoryMapping mapping : ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()));
    }
    myModel = new ListTableModel<VcsDirectoryMapping>(new ColumnInfo[]{DIRECTORY, VCS_SETTING}, mappings, 0);
    myDirectoryMappingTable.setModelAndUpdateColumns(myModel);

    myRecentlyChangedConfigurable.reset();
    myLimitHistory.reset();
    myBaseRevisionTexts.setSelected(myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
    myShowChangedRecursively.setSelected(myVcsConfiguration.SHOW_DIRTY_RECURSIVELY);
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
    return new JButton[]{};
  }

  private void addMapping() {
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.add.title"));
    // due to wonderful UI designer bug
    dlg.initProjectMessage();
    dlg.show();
    if (dlg.isOK()) {
      VcsDirectoryMapping mapping = new VcsDirectoryMapping();
      dlg.saveToMapping(mapping);
      addMapping(mapping);
    }
  }

  private void addMapping(VcsDirectoryMapping mapping) {
    List<VcsDirectoryMapping> items = new ArrayList<VcsDirectoryMapping>(myModel.getItems());
    items.add(mapping);
    myModel.setItems(items);
    checkNotifyListeners(getActiveVcses());
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
        index = mappings.size() - 1;
      }
      myDirectoryMappingTable.getSelectionModel().setSelectionInterval(index, index);
    }
    checkNotifyListeners(activeVcses);
  }

  protected JComponent createMainComponent() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag()
      .setDefaultInsets(new Insets(0, 0, DEFAULT_VGAP, DEFAULT_HGAP))
      .setDefaultWeightX(1)
      .setDefaultWeightY(0)
      .setDefaultFill(GridBagConstraints.BOTH);

    panel.add(createMappingsTable(), gb.nextLine().next().fillCell().weighty(1));
    myLimitHistory = new VcsLimitHistoryConfigurable(myProject);
    panel.add(myLimitHistory.createComponent(), gb.nextLine().next().fillCellHorizontally());
    panel.add(createProjectMappingDescription(), gb.nextLine().next().fillCellHorizontally());
    panel.add(createErrorList(), gb.nextLine().next().fillCellHorizontally());
    panel.add(createShowRecursivelyDirtyOption(), gb.nextLine().next().fillCellHorizontally());
    panel.add(createStoreBaseRevisionOption(), gb.nextLine().next().fillCellHorizontally());
    panel.add(createShowChangedOption(), gb.nextLine().next().fillCellHorizontally());
    panel.add(createShowVcsRootErrorNotificationOption(), gb.nextLine().next().fillCellHorizontally());

    return panel;
  }

  private JComponent createMappingsTable() {
    JPanel panelForTable = ToolbarDecorator.createDecorator(myDirectoryMappingTable, null)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          addMapping();
          updateRootCheckers();
        }
      }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editMapping();
          updateRootCheckers();
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeMapping();
          updateRootCheckers();
        }
      }).setAddActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return !myIsDisabled;
        }
      }).setEditActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          final boolean hasSelection = myDirectoryMappingTable.getSelectedObject() != null;
          return (!myIsDisabled) && hasSelection;
        }
      }).setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          final boolean hasSelection = myDirectoryMappingTable.getSelectedObject() != null;
          return (!myIsDisabled) && hasSelection;
        }
      }).disableUpDownActions().createPanel();
    panelForTable.setPreferredSize(new Dimension(-1, 200));
    return panelForTable;
  }

  private JComponent createErrorList() {
    Box box = Box.createVerticalBox();
    for (Map.Entry<String, VcsRootChecker> entry : myCheckers.entrySet()) {
      VcsRootChecker checker = entry.getValue();
      for (final String root : checker.getUnregisteredRoots()) {
        final String vcs = entry.getKey();
        String title = "Unregistered " + vcs + " root: " + FileUtil.toSystemDependentName(root);
        final VcsRootErrorLabel vcsRootErrorLabel = new VcsRootErrorLabel(title);
        vcsRootErrorLabel.setAddRootLinkHandler(new Runnable() {
          @Override
          public void run() {
            addMapping(new VcsDirectoryMapping(root, vcs));
            vcsRootErrorLabel.setVisible(false);
          }
        });
        box.add(vcsRootErrorLabel);
      }
    }
    return box;
  }

  private JComponent createProjectMappingDescription() {
    final JBLabel label = new JBLabel(myProjectMessage);
    label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    label.setFontColor(UIUtil.FontColor.BRIGHTER);
    label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));
    return label;
  }

  private JComponent createStoreBaseRevisionOption() {
    final JBLabel noteLabel = new JBLabel("File texts bigger than " + ourMaximumFileForBaseRevisionSize / 1000 + "K are not stored");
    noteLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    noteLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    noteLabel.setBorder(BorderFactory.createEmptyBorder(2, 25, 5, 0));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myBaseRevisionTexts, BorderLayout.NORTH);
    panel.add(noteLabel, BorderLayout.SOUTH);
    return panel;
  }

  private JComponent createShowChangedOption() {
    myRecentlyChangedConfigurable = new VcsContentAnnotationConfigurable(myProject);
    JComponent component = myRecentlyChangedConfigurable.createComponent();
    assert component != null;
    return component;
  }

  private JComponent createShowVcsRootErrorNotificationOption() {
    myShowVcsRootErrorNotification = new JCheckBox("Notify about VCS root errors",
                                                   myVcsConfiguration.SHOW_VCS_ERROR_NOTIFICATIONS);
    myShowVcsRootErrorNotification.setVisible(!myCheckers.isEmpty());
    return myShowVcsRootErrorNotification;
  }

  private JComponent createShowRecursivelyDirtyOption() {
    myShowChangedRecursively = new JCheckBox("Show directories with changed descendants", myVcsConfiguration.SHOW_DIRTY_RECURSIVELY);
    return myShowChangedRecursively;
  }

  public void reset() {
    initializeModel();
  }

  public void apply() throws ConfigurationException {
    myVcsManager.setDirectoryMappings(myModel.getItems());
    myRecentlyChangedConfigurable.apply();
    myLimitHistory.apply();
    myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF = myBaseRevisionTexts.isSelected();
    myVcsConfiguration.SHOW_VCS_ERROR_NOTIFICATIONS = myShowVcsRootErrorNotification.isSelected();
    myVcsConfiguration.SHOW_DIRTY_RECURSIVELY = myShowChangedRecursively.isSelected();
    initializeModel();
  }

  public boolean isModified() {
    if (myRecentlyChangedConfigurable.isModified()) return true;
    if (myLimitHistory.isModified()) return true;
    if (myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF != myBaseRevisionTexts.isSelected()) return true;
    if (myVcsConfiguration.SHOW_VCS_ERROR_NOTIFICATIONS != myShowVcsRootErrorNotification.isSelected()) {
      return true;
    }
    if (myVcsConfiguration.SHOW_DIRTY_RECURSIVELY != myShowChangedRecursively.isSelected()) {
      return true;
    }
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
      for (ModuleVcsListener listener : myListeners) {
        listener.activeVcsSetChanged(vcses);
      }
    }
  }

  public Collection<AbstractVcs> getActiveVcses() {
    Set<AbstractVcs> vcses = new HashSet<AbstractVcs>();
    for (VcsDirectoryMapping mapping : myModel.getItems()) {
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

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return this;
  }

  public void disposeUIResources() {
  }

  private static class VcsRootErrorLabel extends JPanel {

    private final LinkLabel myAddLabel;

    VcsRootErrorLabel(String title) {
      super(new BorderLayout(DEFAULT_HGAP, DEFAULT_VGAP));

      CompoundBorder outsideBorder =
        BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(5, 0, 5, 0, UIUtil.getPanelBackground()),
                                           BorderFactory.createLineBorder(UIUtil.getPanelBackground().darker()));
      setBorder(BorderFactory.createCompoundBorder(outsideBorder, BorderFactory.createEmptyBorder(DEFAULT_VGAP, DEFAULT_HGAP,
                                                                                                  DEFAULT_VGAP, DEFAULT_HGAP)));
      setOpaque(true);
      setBackground(new Color(255, 186, 192));

      JBLabel label = new JBLabel(title);

      myAddLabel = new LinkLabel("Add root", null);

      myAddLabel.setOpaque(false);

      JPanel actionsPanel = new JPanel(new BorderLayout(DEFAULT_HGAP, DEFAULT_VGAP));
      actionsPanel.setOpaque(false);
      actionsPanel.add(myAddLabel, BorderLayout.CENTER);

      add(label, BorderLayout.CENTER);
      add(actionsPanel, BorderLayout.EAST);
    }

    void setAddRootLinkHandler(final Runnable handler) {
      myAddLabel.setListener(new LinkListener() {
        @Override
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          handler.run();
        }
      }, null);
    }
  }
}
