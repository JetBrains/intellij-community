/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vcs.roots.VcsRootErrorsFinder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
public class VcsDirectoryConfigurationPanel extends JPanel implements Configurable {
  private final Project myProject;
  private final String myProjectMessage;
  private final ProjectLevelVcsManager myVcsManager;
  private final TableView<MapInfo> myDirectoryMappingTable;
  private final ComboboxWithBrowseButton myVcsComboBox = new ComboboxWithBrowseButton();
  private final List<ModuleVcsListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final MyDirectoryRenderer myDirectoryRenderer;
  private final ColumnInfo<MapInfo, MapInfo> DIRECTORY;
  private final JCheckBox myBaseRevisionTexts;
  private ListTableModel<MapInfo> myModel;
  private final Map<String, VcsDescriptor> myAllVcss;
  private VcsContentAnnotationConfigurable myRecentlyChangedConfigurable;
  private final boolean myIsDisabled;
  private final VcsConfiguration myVcsConfiguration;
  private final @NotNull Map<String, VcsRootChecker> myCheckers;
  private JCheckBox myShowChangedRecursively;
  private final VcsLimitHistoryConfigurable myLimitHistory;
  private final VcsUpdateInfoScopeFilterConfigurable myScopeFilterConfig;
  private VcsCommitMessageMarginConfigurable myCommitMessageMarginConfigurable;
  private JCheckBox myShowUnversionedFiles;
  private JCheckBox myCheckCommitMessageSpelling;

  private static class MapInfo {
    static final MapInfo SEPARATOR = new MapInfo(new VcsDirectoryMapping("SEPARATOR", "SEP"), Type.SEPARATOR);
    static final Comparator<MapInfo> COMPARATOR = new Comparator<MapInfo>() {
      @Override
      public int compare(@NotNull MapInfo o1, @NotNull MapInfo o2) {
        if (o1.type.isRegistered() && o2.type.isRegistered() || o1.type == Type.UNREGISTERED && o2.type == Type.UNREGISTERED) {
          return NewMappings.MAPPINGS_COMPARATOR.compare(o1.mapping, o2.mapping);
        }
        return o1.type.ordinal() - o2.type.ordinal();
      }
    };

    static MapInfo unregistered(@NotNull String path, @NotNull String vcs) {
      return new MapInfo(new VcsDirectoryMapping(path, vcs), Type.UNREGISTERED);
    }

    static MapInfo registered(@NotNull VcsDirectoryMapping mapping, boolean valid) {
      return new MapInfo(mapping, valid ? Type.NORMAL : Type.INVALID);
    }

    enum Type {
      NORMAL,
      INVALID,
      SEPARATOR,
      UNREGISTERED;

      boolean isRegistered() {
        return this == NORMAL || this == INVALID;
      }
    }

    private final Type type;
    private final VcsDirectoryMapping mapping;

    private MapInfo(@NotNull VcsDirectoryMapping mapping, @NotNull Type type) {
      this.mapping = mapping;
      this.type = type;
    }
  }

  private static class MyDirectoryRenderer extends ColoredTableCellRenderer {
    private final Project myProject;

    public MyDirectoryRenderer(Project project) {
      myProject = project;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof MapInfo) {
        MapInfo info = (MapInfo)value;

        if (!selected && (info == MapInfo.SEPARATOR || info.type == MapInfo.Type.UNREGISTERED)) {
          setBackground(getUnregisteredRootBackground());
        }

        if (info == MapInfo.SEPARATOR) {
          append("Unregistered roots:", getAttributes(info));
          return;
        }

        if (info.mapping.isDefaultMapping()) {
          append(VcsDirectoryMapping.PROJECT_CONSTANT, getAttributes(info));
          return;
        }

        String directory = info.mapping.getDirectory();
        VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir != null) {
          final File directoryFile = new File(StringUtil.trimEnd(UriUtil.trimTrailingSlashes(directory), "\\") + "/");
          File ioBase = new File(baseDir.getPath());
          if (directoryFile.isAbsolute() && !FileUtil.isAncestor(ioBase, directoryFile, false)) {
            append(new File(directory).getPath(), getAttributes(info));
            return;
          }
          String relativePath = FileUtil.getRelativePath(ioBase, directoryFile);
          if (".".equals(relativePath) || relativePath == null) {
            append(ioBase.getPath(), getAttributes(info));
          }
          else {
            append(relativePath, getAttributes(info));
            append(" (" + ioBase + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      }
    }
  }

  @NotNull
  private static Color getUnregisteredRootBackground() {
    return new JBColor(UIUtil.getLabelBackground(), new Color(0x45494A));
  }

  @NotNull
  private static SimpleTextAttributes getAttributes(@NotNull MapInfo info) {
    if (info == MapInfo.SEPARATOR) {
      return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_SMALLER, null);
    }
    else if (info.type == MapInfo.Type.INVALID) {
      return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED);
    }
    else if (info.type == MapInfo.Type.UNREGISTERED) {
      return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GRAY);
    }
    else {
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }

  private final ColumnInfo<MapInfo, String> VCS_SETTING =
    new ColumnInfo<MapInfo, String>(VcsBundle.message("column.name.configure.vcses.vcs")) {
      @Override
      public String valueOf(final MapInfo object) {
        return object.mapping.getVcs();
      }

      @Override
      public boolean isCellEditable(MapInfo info) {
        return info != MapInfo.SEPARATOR && info.type != MapInfo.Type.UNREGISTERED;
      }

      @Override
      public void setValue(final MapInfo o, final String aValue) {
        Collection<AbstractVcs> activeVcses = getActiveVcses();
        o.mapping.setVcs(aValue);
        checkNotifyListeners(activeVcses);
      }

      @Override
      public TableCellRenderer getRenderer(final MapInfo info) {
        return new ColoredTableCellRenderer() {
          @Override
          protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
            if (info == MapInfo.SEPARATOR) {
              if (!selected) {
                setBackground(getUnregisteredRootBackground());
              }
              return;
            }

            if (info.type == MapInfo.Type.UNREGISTERED && !selected) {
              setBackground(getUnregisteredRootBackground());
            }

            final String vcsName = info.mapping.getVcs();
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
            append(text, getAttributes(info));
          }
        };
      }

      @Override
      public TableCellEditor getEditor(final MapInfo o) {
        return new AbstractTableCellEditor() {
          @Override
          public Object getCellEditorValue() {
            final VcsDescriptor selectedVcs = (VcsDescriptor)myVcsComboBox.getComboBox().getSelectedItem();
            return ((selectedVcs == null) || selectedVcs.isNone()) ? "" : selectedVcs.getName();
          }

          @Override
          public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            String vcsName = (String)value;
            myVcsComboBox.getComboBox().setSelectedItem(myAllVcss.get(vcsName));
            return myVcsComboBox;
          }
        };
      }

      @Nullable
      @Override
      public String getMaxStringValue() {
        String maxString = null;
        for (String name : myAllVcss.keySet()) {
          if (maxString == null || maxString.length() < name.length()) {
            maxString = name;
          }
        }
        return maxString;
      }

      @Override
      public int getAdditionalWidth() {
        return DEFAULT_HGAP;
      }
    };

  public VcsDirectoryConfigurationPanel(final Project project) {
    myProject = project;
    myVcsConfiguration = getInstance(myProject);
    myProjectMessage = XmlStringUtil.wrapInHtml(StringUtil.escapeXml(VcsDirectoryMapping.PROJECT_CONSTANT) + " - " +
                                                DefaultVcsRootPolicy.getInstance(myProject).getProjectConfigurationMessage(myProject)
                                                  .replace('\n', ' '));
    myIsDisabled = myProject.isDefault();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    final VcsDescriptor[] vcsDescriptors = myVcsManager.getAllVcss();
    myAllVcss = new HashMap<>();
    for (VcsDescriptor vcsDescriptor : vcsDescriptors) {
      myAllVcss.put(vcsDescriptor.getName(), vcsDescriptor);
    }

    myDirectoryMappingTable = new TableView<>();
    myDirectoryMappingTable.setIntercellSpacing(JBUI.emptySize());

    myBaseRevisionTexts = new JCheckBox("Store on shelf base revision texts for files under DVCS");
    myLimitHistory = new VcsLimitHistoryConfigurable(myProject);
    myScopeFilterConfig = new VcsUpdateInfoScopeFilterConfigurable(myProject, myVcsConfiguration);

    myCheckers = new HashMap<>();
    updateRootCheckers();

    setLayout(new BorderLayout());
    add(createMainComponent());

    myDirectoryRenderer = new MyDirectoryRenderer(myProject);
    DIRECTORY = new ColumnInfo<MapInfo, MapInfo>(VcsBundle.message("column.info.configure.vcses.directory")) {
      @Override
      public MapInfo valueOf(final MapInfo mapping) {
        return mapping;
      }

      @Override
      public TableCellRenderer getRenderer(MapInfo vcsDirectoryMapping) {
        return myDirectoryRenderer;
      }
    };
    initializeModel();

    final JComboBox comboBox = myVcsComboBox.getComboBox();
    comboBox.setModel(buildVcsWrappersModel(myProject));
    comboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        if (myDirectoryMappingTable.isEditing()) {
          myDirectoryMappingTable.stopEditing();
        }
      }
    });
    myVcsComboBox.addActionListener(new ActionListener() {
      @Override
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
    VcsRootChecker[] checkers = Extensions.getExtensions(VcsRootChecker.EXTENSION_POINT_NAME);
    for (VcsRootChecker checker : checkers) {
      VcsKey key = checker.getSupportedVcs();
      AbstractVcs vcs = myVcsManager.findVcsByName(key.getName());
      if (vcs == null) {
        continue;
      }
      myCheckers.put(key.getName(), checker);
    }
  }

  private void initializeModel() {
    List<MapInfo> mappings = new ArrayList<>();
    for (VcsDirectoryMapping mapping : ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(MapInfo.registered(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()),
                                      isMappingValid(mapping)));
    }

    Collection<VcsRootError> errors = findUnregisteredRoots();
    if (!errors.isEmpty()) {
      mappings.add(MapInfo.SEPARATOR);
      for (VcsRootError error : errors) {
        mappings.add(MapInfo.unregistered(error.getMapping(), error.getVcsKey().getName()));
      }
    }

    myModel = new ListTableModel<>(new ColumnInfo[]{DIRECTORY, VCS_SETTING}, mappings, 0);
    myDirectoryMappingTable.setModelAndUpdateColumns(myModel);

    myRecentlyChangedConfigurable.reset();
    myLimitHistory.reset();
    myScopeFilterConfig.reset();
    myBaseRevisionTexts.setSelected(myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF);
    myShowChangedRecursively.setSelected(myVcsConfiguration.SHOW_DIRTY_RECURSIVELY);
    myCommitMessageMarginConfigurable.reset();
    myShowUnversionedFiles.setSelected(myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT);
    myCheckCommitMessageSpelling.setSelected(myVcsConfiguration.CHECK_COMMIT_MESSAGE_SPELLING);
  }

  @NotNull
  private Collection<VcsRootError> findUnregisteredRoots() {
    return ContainerUtil.filter(VcsRootErrorsFinder.getInstance(myProject).find(), new Condition<VcsRootError>() {
      @Override
      public boolean value(VcsRootError error) {
        return error.getType() == VcsRootError.Type.UNREGISTERED_ROOT;
      }
    });
  }

  private boolean isMappingValid(@NotNull VcsDirectoryMapping mapping) {
    String vcs = mapping.getVcs();
    VcsRootChecker checker = myCheckers.get(vcs);
    return checker == null ||
           (mapping.isDefaultMapping() ? checker.isRoot(myProject.getBasePath()) : checker.isRoot(mapping.getDirectory()));
  }

  public static DefaultComboBoxModel buildVcsWrappersModel(final Project project) {
    final VcsDescriptor[] vcsDescriptors = ProjectLevelVcsManager.getInstance(project).getAllVcss();
    final VcsDescriptor[] result = new VcsDescriptor[vcsDescriptors.length + 1];
    result[0] = VcsDescriptor.createFictive();
    System.arraycopy(vcsDescriptors, 0, result, 1, vcsDescriptors.length);
    return new DefaultComboBoxModel(result);
  }

  private void addMapping() {
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.add.title"));
    // due to wonderful UI designer bug
    dlg.initProjectMessage();
    if (dlg.showAndGet()) {
      addMapping(dlg.getMapping());
    }
  }

  private void addMapping(VcsDirectoryMapping mapping) {
    List<MapInfo> items = new ArrayList<>(myModel.getItems());
    items.add(MapInfo.registered(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()),
                                 isMappingValid(mapping)));
    Collections.sort(items, MapInfo.COMPARATOR);
    myModel.setItems(items);
    checkNotifyListeners(getActiveVcses());
  }


  private void addSelectedUnregisteredMappings(List<MapInfo> infos) {
    List<MapInfo> items = new ArrayList<>(myModel.getItems());
    for (MapInfo info : infos) {
      items.remove(info);
      items.add(MapInfo.registered(info.mapping, isMappingValid(info.mapping)));
    }
    sortAndAddSeparatorIfNeeded(items);
    myModel.setItems(items);
    checkNotifyListeners(getActiveVcses());
  }

  @Contract(pure = false)
  private static void sortAndAddSeparatorIfNeeded(@NotNull List<MapInfo> items) {
    boolean hasUnregistered = false;
    boolean hasSeparator = false;
    for (MapInfo item : items) {
      if (item.type == MapInfo.Type.UNREGISTERED) {
        hasUnregistered = true;
      }
      else if (item.type == MapInfo.Type.SEPARATOR) {
        hasSeparator = true;
      }
    }
    if (!hasUnregistered && hasSeparator) {
      items.remove(MapInfo.SEPARATOR);
    }
    else if (hasUnregistered && !hasSeparator) {
      items.add(MapInfo.SEPARATOR);
    }
    Collections.sort(items, MapInfo.COMPARATOR);
  }

  private void editMapping() {
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.remove.title"));
    int row = myDirectoryMappingTable.getSelectedRow();
    VcsDirectoryMapping mapping = myDirectoryMappingTable.getRow(row).mapping;
    dlg.setMapping(mapping);
    if (dlg.showAndGet()) {
      setMapping(row, dlg.getMapping());
    }
  }

  private void setMapping(int row, @NotNull VcsDirectoryMapping mapping) {
    List<MapInfo> items = new ArrayList<>(myModel.getItems());
    items.set(row, MapInfo.registered(mapping, isMappingValid(mapping)));
    Collections.sort(items, MapInfo.COMPARATOR);
    myModel.setItems(items);
    checkNotifyListeners(getActiveVcses());
  }

  private void removeMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    ArrayList<MapInfo> mappings = new ArrayList<>(myModel.getItems());
    int index = myDirectoryMappingTable.getSelectionModel().getMinSelectionIndex();
    Collection<MapInfo> selection = myDirectoryMappingTable.getSelection();
    mappings.removeAll(selection);

    Collection<MapInfo> removedValidRoots = ContainerUtil.mapNotNull(selection, new Function<MapInfo, MapInfo>() {
      @Override
      public MapInfo fun(MapInfo info) {
        return info.type == MapInfo.Type.NORMAL && myCheckers.get(info.mapping.getVcs()) != null ?
               MapInfo.unregistered(info.mapping.getDirectory(), info.mapping.getVcs()) :
               null;
      }
    });
    mappings.addAll(removedValidRoots);
    sortAndAddSeparatorIfNeeded(mappings);

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
      .setDefaultFill(GridBagConstraints.HORIZONTAL);

    panel.add(createMappingsTable(), gb.nextLine().next().fillCell().weighty(1.0));
    panel.add(createProjectMappingDescription(), gb.nextLine().next());
    panel.add(myLimitHistory.createComponent(), gb.nextLine().next());
    panel.add(createShowRecursivelyDirtyOption(), gb.nextLine().next());
    panel.add(createStoreBaseRevisionOption(), gb.nextLine().next());
    panel.add(createShowChangedOption(), gb.nextLine().next());
    panel.add(myScopeFilterConfig.createComponent(), gb.nextLine().next());
    panel.add(createUseCommitMessageRightMargin(), gb.nextLine().next().fillCellHorizontally());
    createShowUnversionedFilesOption();
    if (Registry.is("vcs.unversioned.files.in.commit")) {
      panel.add(myShowUnversionedFiles, gb.nextLine().next());
    }
    panel.add(createCheckCommitMessageSpelling(), gb.nextLine().next());
    return panel;
  }

  private JComponent createMappingsTable() {
    JPanel panelForTable = ToolbarDecorator.createDecorator(myDirectoryMappingTable, null)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          if (onlyRegisteredRootsInSelection()) {
            addMapping();
          }
          else {
            addSelectedUnregisteredMappings(getSelectedUnregisteredRoots());
          }
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
          return !myIsDisabled && rootsOfOneKindInSelection();
        }
      }).setEditActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return !myIsDisabled && onlyRegisteredRootsInSelection();
        }
      }).setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return !myIsDisabled && onlyRegisteredRootsInSelection();
        }
      }).disableUpDownActions().createPanel();
    panelForTable.setPreferredSize(new Dimension(-1, 200));
    return panelForTable;
  }

  @NotNull
  private List<MapInfo> getSelectedUnregisteredRoots() {
    return ContainerUtil.filter(myDirectoryMappingTable.getSelection(), new Condition<MapInfo>() {
      @Override
      public boolean value(MapInfo info) {
        return info.type == MapInfo.Type.UNREGISTERED;
      }
    });
  }

  private boolean rootsOfOneKindInSelection() {
    Collection<MapInfo> selection = myDirectoryMappingTable.getSelection();
    if (selection.isEmpty()) {
      return true;
    }
    if (selection.size() == 1 && selection.iterator().next().type == MapInfo.Type.SEPARATOR) {
      return false;
    }
    List<MapInfo> selectedRegisteredRoots = getSelectedRegisteredRoots();
    return selectedRegisteredRoots.size() == selection.size() || selectedRegisteredRoots.size() == 0;
  }

  @NotNull
  private List<MapInfo> getSelectedRegisteredRoots() {
    Collection<MapInfo> selection = myDirectoryMappingTable.getSelection();
    return ContainerUtil.filter(selection, new Condition<MapInfo>() {
      @Override
      public boolean value(MapInfo info) {
        return info.type == MapInfo.Type.NORMAL || info.type == MapInfo.Type.INVALID;
      }
    });
  }

  private boolean onlyRegisteredRootsInSelection() {
    return getSelectedRegisteredRoots().size() == myDirectoryMappingTable.getSelection().size();
  }

  private JComponent createProjectMappingDescription() {
    final JBLabel label = new JBLabel(myProjectMessage);
    label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    label.setFontColor(UIUtil.FontColor.BRIGHTER);
    label.setBorder(JBUI.Borders.empty(2, 5, 2, 0));
    return label;
  }

  private JComponent createStoreBaseRevisionOption() {
    final JBLabel noteLabel = new JBLabel("File texts bigger than " + ourMaximumFileForBaseRevisionSize / 1000 + "K are not stored");
    noteLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    noteLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    noteLabel.setBorder(JBUI.Borders.empty(2, 25, 5, 0));

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

  private JComponent createUseCommitMessageRightMargin() {
    myCommitMessageMarginConfigurable = new VcsCommitMessageMarginConfigurable(myProject, myVcsConfiguration);
    return myCommitMessageMarginConfigurable.createComponent();
  }

  private JComponent createShowRecursivelyDirtyOption() {
    myShowChangedRecursively = new JCheckBox("Show directories with changed descendants", myVcsConfiguration.SHOW_DIRTY_RECURSIVELY);
    return myShowChangedRecursively;
  }

  @NotNull
  private JComponent createShowUnversionedFilesOption() {
    myShowUnversionedFiles =
      new JCheckBox("Show unversioned files in Commit dialog", myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT);
    return myShowUnversionedFiles;
  }

  @NotNull
  private JComponent createCheckCommitMessageSpelling() {
    myCheckCommitMessageSpelling = new JBCheckBox("Check commit message spelling", myVcsConfiguration.CHECK_COMMIT_MESSAGE_SPELLING);
    return myCheckCommitMessageSpelling;
  }

  @Override
  public void reset() {
    initializeModel();
  }

  @Override
  public void apply() throws ConfigurationException {
    myVcsManager.setDirectoryMappings(getModelMappings());
    myRecentlyChangedConfigurable.apply();
    myLimitHistory.apply();
    myScopeFilterConfig.apply();
    myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF = myBaseRevisionTexts.isSelected();
    myVcsConfiguration.SHOW_DIRTY_RECURSIVELY = myShowChangedRecursively.isSelected();
    myCommitMessageMarginConfigurable.apply();
    myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT = myShowUnversionedFiles.isSelected();
    myVcsConfiguration.CHECK_COMMIT_MESSAGE_SPELLING = myCheckCommitMessageSpelling.isSelected();
    initializeModel();
  }

  @Override
  public boolean isModified() {
    if (myRecentlyChangedConfigurable.isModified()) return true;
    if (myLimitHistory.isModified()) return true;
    if (myScopeFilterConfig.isModified()) return true;
    if (myVcsConfiguration.INCLUDE_TEXT_INTO_SHELF != myBaseRevisionTexts.isSelected()) return true;
    if (myVcsConfiguration.SHOW_DIRTY_RECURSIVELY != myShowChangedRecursively.isSelected()) {
      return true;
    }
    if (myCommitMessageMarginConfigurable.isModified()) {
      return true;
    }
    if (myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT != myShowUnversionedFiles.isSelected()) {
      return true;
    }
    if (myVcsConfiguration.CHECK_COMMIT_MESSAGE_SPELLING != myCheckCommitMessageSpelling.isSelected()) {
      return true;
    }
    return !getModelMappings().equals(myVcsManager.getDirectoryMappings());
  }

  @NotNull
  private List<VcsDirectoryMapping> getModelMappings() {
    return ContainerUtil.mapNotNull(myModel.getItems(), new Function<MapInfo, VcsDirectoryMapping>() {
      @Override
      public VcsDirectoryMapping fun(MapInfo info) {
        return info == MapInfo.SEPARATOR || info.type == MapInfo.Type.UNREGISTERED ? null : info.mapping;
      }
    });
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
    Set<AbstractVcs> vcses = new HashSet<>();
    for (VcsDirectoryMapping mapping : getModelMappings()) {
      if (mapping.getVcs().length() > 0) {
        vcses.add(myVcsManager.findVcsByName(mapping.getVcs()));
      }
    }
    return vcses;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Mappings";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public void disposeUIResources() {
    myLimitHistory.disposeUIResources();
    myScopeFilterConfig.disposeUIResources();
  }
}
