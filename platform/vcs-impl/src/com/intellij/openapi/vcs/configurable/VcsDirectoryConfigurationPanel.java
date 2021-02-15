// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.roots.VcsRootErrorsFinder;
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.progress.util.ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;
import static com.intellij.openapi.project.ProjectUtil.guessProjectDir;
import static com.intellij.openapi.vcs.VcsConfiguration.getInstance;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static java.util.Arrays.asList;

/**
 * @author yole
 */
public class VcsDirectoryConfigurationPanel extends JPanel implements Configurable {
  private static final int POSTPONE_MAPPINGS_LOADING_PANEL = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;

  private final Project myProject;
  private final Disposable myDisposable = Disposer.newDisposable();
  private final @Nls String myProjectMessage;
  private final ProjectLevelVcsManager myVcsManager;
  private final TableView<MapInfo> myDirectoryMappingTable;
  private final ComboBox<AbstractVcs> myVcsComboBox;
  private final List<ModuleVcsListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final MyDirectoryRenderer myDirectoryRenderer;
  private final ColumnInfo<MapInfo, MapInfo> DIRECTORY;
  private ListTableModel<MapInfo> myModel;
  private final List<AbstractVcs> myAllVcss;
  private VcsContentAnnotationConfigurable myRecentlyChangedConfigurable;
  private final boolean myIsDisabled;
  private final VcsConfiguration myVcsConfiguration;
  private final @NotNull Map<String, VcsRootChecker> myCheckers;
  private JCheckBox myShowChangedRecursively;
  private final VcsLimitHistoryConfigurable myLimitHistory;
  private final VcsUpdateInfoScopeFilterConfigurable myScopeFilterConfig;
  private JBLoadingPanel myLoadingPanel;

  private static final class MapInfo {
    static final MapInfo SEPARATOR = new MapInfo(new VcsDirectoryMapping("SEPARATOR", "SEP"), Type.SEPARATOR); //NON-NLS
    static final Comparator<MapInfo> COMPARATOR = (o1, o2) -> {
      if (o1.type.isRegistered() && o2.type.isRegistered() || o1.type == Type.UNREGISTERED && o2.type == Type.UNREGISTERED) {
        return Comparing.compare(o1.mapping.getDirectory(), o2.mapping.getDirectory());
      }
      return o1.type.compareTo(o2.type);
    };

    static MapInfo unregistered(@NotNull VcsDirectoryMapping mapping) {
      return new MapInfo(mapping, Type.UNREGISTERED);
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
    private VcsDirectoryMapping mapping;

    private MapInfo(@NotNull VcsDirectoryMapping mapping, @NotNull Type type) {
      this.mapping = mapping;
      this.type = type;
    }

    @Override
    public String toString() {
      if (type == Type.SEPARATOR) return "";
      return mapping.toString();
    }
  }

  private static class MyDirectoryRenderer extends ColoredTableCellRenderer {
    private final Project myProject;

    MyDirectoryRenderer(Project project) {
      myProject = project;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof MapInfo) {
        MapInfo info = (MapInfo)value;

        if (!selected && (info == MapInfo.SEPARATOR || info.type == MapInfo.Type.UNREGISTERED)) {
          setBackground(UIUtil.getDecoratedRowColor());
        }

        if (info == MapInfo.SEPARATOR) {
          append(VcsBundle.message("unregistered.roots.label"), getAttributes(info));
          return;
        }

        if (info.mapping.isDefaultMapping()) {
          append(VcsDirectoryMapping.PROJECT_CONSTANT.get(), getAttributes(info));
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
    new ColumnInfo<>(VcsBundle.message("column.name.configure.vcses.vcs")) {
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
        o.mapping = new VcsDirectoryMapping(o.mapping.getDirectory(), aValue, o.mapping.getRootSettings());
        checkNotifyListeners(activeVcses);
      }

      @Override
      public TableCellRenderer getRenderer(final MapInfo info) {
        return new ColoredTableCellRenderer() {
          @Override
          protected void customizeCellRenderer(@NotNull JTable table,
                                               Object value,
                                               boolean selected,
                                               boolean hasFocus,
                                               int row,
                                               int column) {
            if (info == MapInfo.SEPARATOR) {
              if (!selected) {
                setBackground(UIUtil.getDecoratedRowColor());
              }
              return;
            }

            if (info.type == MapInfo.Type.UNREGISTERED && !selected) {
              setBackground(UIUtil.getDecoratedRowColor());
            }

            final String vcsName = info.mapping.getVcs();
            String text;
            if (vcsName.isEmpty()) {
              text = VcsBundle.message("none.vcs.presentation");
            }
            else {
              AbstractVcs vcs = ContainerUtil.find(myAllVcss, it -> vcsName.equals(it.getName()));
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
            AbstractVcs selectedVcs = myVcsComboBox.getItem();
            return selectedVcs == null ? "" : selectedVcs.getName(); //NON-NLS handled by custom renderer
          }

          @Override
          public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            myVcsComboBox.setSelectedItem(value);
            return myVcsComboBox;
          }
        };
      }

      @Nullable
      @Override
      public String getMaxStringValue() {
        String maxString = null;
        for (AbstractVcs vcs : myAllVcss) {
          String name = vcs.getDisplayName();
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
    myProjectMessage = new HtmlBuilder()
      .append(VcsDirectoryMapping.PROJECT_CONSTANT.get())
      .append(" - ")
      .append(DefaultVcsRootPolicy.getInstance(myProject).getProjectConfigurationMessage().replace('\n', ' '))
      .wrapWithHtmlBody().toString();
    myIsDisabled = myProject.isDefault();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);

    myAllVcss = asList(myVcsManager.getAllSupportedVcss());

    myDirectoryMappingTable = new TableView<>();
    myDirectoryMappingTable.setShowGrid(false);
    myDirectoryMappingTable.setIntercellSpacing(JBUI.emptySize());

    myLimitHistory = new VcsLimitHistoryConfigurable(myProject);
    myScopeFilterConfig = new VcsUpdateInfoScopeFilterConfigurable(myProject, myVcsConfiguration);

    myCheckers = new HashMap<>();
    updateRootCheckers();

    setLayout(new BorderLayout());
    add(createMainComponent());

    myDirectoryRenderer = new MyDirectoryRenderer(myProject);
    DIRECTORY = new ColumnInfo<>(VcsBundle.message("column.info.configure.vcses.directory")) {
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

    myVcsComboBox = buildVcsesComboBox(myProject);
    myVcsComboBox.addItemListener(e -> {
      if (myDirectoryMappingTable.isEditing()) {
        myDirectoryMappingTable.stopEditing();
      }
    });

    myDirectoryMappingTable.setRowHeight(myVcsComboBox.getPreferredSize().height);
    if (myIsDisabled) {
      myDirectoryMappingTable.setEnabled(false);
    }
  }

  private void updateRootCheckers() {
    myCheckers.clear();
    for (VcsRootChecker checker : VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList()) {
      VcsKey key = checker.getSupportedVcs();
      AbstractVcs vcs = myVcsManager.findVcsByName(key.getName());
      if (vcs == null) {
        continue;
      }
      myCheckers.put(key.getName(), checker);
    }
  }

  private void initializeModel() {
    myRecentlyChangedConfigurable.reset();
    myLimitHistory.reset();
    myScopeFilterConfig.reset();
    myShowChangedRecursively.setSelected(myVcsConfiguration.SHOW_DIRTY_RECURSIVELY);

    List<MapInfo> mappings = new ArrayList<>();
    for (VcsDirectoryMapping mapping : ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(MapInfo.registered(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()),
                                      isMappingValid(mapping)));
    }
    myModel = new ListTableModel<>(new ColumnInfo[]{DIRECTORY, VCS_SETTING}, mappings, 0);

    BackgroundTaskUtil.executeAndTryWait(indicator -> {
      Collection<VcsRootError> errors = findUnregisteredRoots();
      return () -> {
        if (!errors.isEmpty()) {
          List<MapInfo> newMappings = new ArrayList<>(mappings);
          newMappings.add(MapInfo.SEPARATOR);
          for (VcsRootError error : errors) {
            newMappings.add(MapInfo.unregistered(error.getMapping()));
          }
          myModel.setItems(newMappings);
        }
        myDirectoryMappingTable.setModelAndUpdateColumns(myModel);
        myLoadingPanel.stopLoading();
      };
    }, () -> myLoadingPanel.startLoading(), POSTPONE_MAPPINGS_LOADING_PANEL, false);
  }

  @NotNull
  private Collection<VcsRootError> findUnregisteredRoots() {
    return ContainerUtil.filter(VcsRootErrorsFinder.getInstance(myProject).getOrFind(),
                                error -> error.getType() == VcsRootError.Type.UNREGISTERED_ROOT);
  }

  private boolean isMappingValid(@NotNull VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return true;
    VcsRootChecker checker = myCheckers.get(mapping.getVcs());
    return checker == null || checker.isRoot(mapping.getDirectory());
  }

  @NotNull
  public static ComboBox<AbstractVcs> buildVcsesComboBox(@NotNull Project project) {
    AbstractVcs[] allVcses = ProjectLevelVcsManager.getInstance(project).getAllSupportedVcss();
    ComboBox<AbstractVcs> comboBox = new ComboBox<>(ArrayUtil.append(allVcses, null));
    comboBox.setRenderer(SimpleListCellRenderer.create(VcsBundle.message("none.vcs.presentation"), AbstractVcs::getDisplayName));
    return comboBox;
  }

  private void addMapping() {
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, VcsBundle.message("directory.mapping.add.title"));
    if (dlg.showAndGet()) {
      addMapping(dlg.getMapping());
    }
  }

  private void addMapping(VcsDirectoryMapping mapping) {
    List<MapInfo> items = new ArrayList<>(myModel.getItems());
    items.add(MapInfo.registered(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()),
                                 isMappingValid(mapping)));
    items.sort(MapInfo.COMPARATOR);
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
    items.sort(MapInfo.COMPARATOR);
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
    items.sort(MapInfo.COMPARATOR);
    myModel.setItems(items);
    checkNotifyListeners(getActiveVcses());
  }

  private void removeMapping() {
    Collection<AbstractVcs> activeVcses = getActiveVcses();
    ArrayList<MapInfo> mappings = new ArrayList<>(myModel.getItems());
    int index = myDirectoryMappingTable.getSelectionModel().getMinSelectionIndex();
    Collection<MapInfo> selection = myDirectoryMappingTable.getSelection();
    mappings.removeAll(selection);

    Collection<MapInfo> removedValidRoots = ContainerUtil.mapNotNull(selection, info -> {
      return info.type == MapInfo.Type.NORMAL && myCheckers.get(info.mapping.getVcs()) != null ?
             MapInfo.unregistered(info.mapping) : null;
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

    JComponent mappingsTable = createMappingsTable();
    // don't start loading automatically
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myDisposable, POSTPONE_MAPPINGS_LOADING_PANEL * 2);
    myLoadingPanel.add(mappingsTable);
    panel.add(myLoadingPanel, gb.nextLine().next().fillCell().weighty(1.0));

    panel.add(createProjectMappingDescription(), gb.nextLine().next());
    panel.add(myLimitHistory.createComponent(), gb.nextLine().next());
    panel.add(createShowRecursivelyDirtyOption(), gb.nextLine().next());
    panel.add(createShowChangedOption(), gb.nextLine().next());
    if (!AbstractCommonUpdateAction.showsCustomNotification(asList(myVcsManager.getAllActiveVcss()))) {
      panel.add(myScopeFilterConfig.createComponent(), gb.nextLine().next());
    }
    return panel;
  }

  private JComponent createMappingsTable() {
    JPanel panelForTable = ToolbarDecorator.createDecorator(myDirectoryMappingTable, null)
      .setAddActionUpdater(e -> !myIsDisabled && rootsOfOneKindInSelection())
      .setAddAction(button -> {
        List<MapInfo> unregisteredRoots = getSelectedUnregisteredRoots();
        if (unregisteredRoots.isEmpty()) {
          addMapping();
        }
        else {
          addSelectedUnregisteredMappings(unregisteredRoots);
        }
        updateRootCheckers();
      })
      .setEditActionUpdater(e -> !myIsDisabled && onlyRegisteredRootsInSelection())
      .setEditAction(button -> {
        editMapping();
        updateRootCheckers();
      })
      .setRemoveActionUpdater(e -> !myIsDisabled && onlyRegisteredRootsInSelection())
      .setRemoveAction(button -> {
        removeMapping();
        updateRootCheckers();
      })
      .disableUpDownActions()
      .createPanel();
    panelForTable.setPreferredSize(new JBDimension(-1, 200));
    return panelForTable;
  }

  @NotNull
  private List<MapInfo> getSelectedUnregisteredRoots() {
    return ContainerUtil.filter(myDirectoryMappingTable.getSelection(), info -> info.type == MapInfo.Type.UNREGISTERED);
  }

  private boolean rootsOfOneKindInSelection() {
    Collection<MapInfo> selection = myDirectoryMappingTable.getSelection();
    if (selection.isEmpty()) {
      return true;
    }
    if (selection.size() == 1 && selection.iterator().next().type == MapInfo.Type.SEPARATOR) {
      return true;
    }
    List<MapInfo> selectedRegisteredRoots = getSelectedRegisteredRoots();
    return selectedRegisteredRoots.size() == selection.size() || selectedRegisteredRoots.size() == 0;
  }

  @NotNull
  private List<MapInfo> getSelectedRegisteredRoots() {
    Collection<MapInfo> selection = myDirectoryMappingTable.getSelection();
    return ContainerUtil.filter(selection, info -> info.type == MapInfo.Type.NORMAL || info.type == MapInfo.Type.INVALID);
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

  private JComponent createShowChangedOption() {
    myRecentlyChangedConfigurable = new VcsContentAnnotationConfigurable(myProject);
    JComponent component = myRecentlyChangedConfigurable.createComponent();
    assert component != null;
    return component;
  }

  private JComponent createShowRecursivelyDirtyOption() {
    myShowChangedRecursively = new JCheckBox(VcsBundle.message("checkbox.show.dirty.recursively"), myVcsConfiguration.SHOW_DIRTY_RECURSIVELY);
    return myShowChangedRecursively;
  }

  @Override
  public void reset() {
    initializeModel();
  }

  @Override
  public void apply() throws ConfigurationException {
    adjustIgnoredRootsSettings();
    myVcsManager.setDirectoryMappings(getModelMappings());
    myRecentlyChangedConfigurable.apply();
    myLimitHistory.apply();
    myScopeFilterConfig.apply();
    myVcsConfiguration.SHOW_DIRTY_RECURSIVELY = myShowChangedRecursively.isSelected();
    initializeModel();
  }

  private void adjustIgnoredRootsSettings() {
    List<VcsDirectoryMapping> newMappings = getModelMappings();
    List<VcsDirectoryMapping> previousMappings = myVcsManager.getDirectoryMappings();
    myVcsConfiguration.addIgnoredUnregisteredRoots(previousMappings.stream()
        .filter(mapping -> !newMappings.contains(mapping))
        .map(mapping -> mapping.isDefaultMapping() ? guessProjectDir(myProject).getPath() : mapping.getDirectory())
        .collect(Collectors.toList()));
    myVcsConfiguration.removeFromIgnoredUnregisteredRoots(map(newMappings, VcsDirectoryMapping::getDirectory));
  }

  @Override
  public boolean isModified() {
    if (myRecentlyChangedConfigurable.isModified()) return true;
    if (myLimitHistory.isModified()) return true;
    if (myScopeFilterConfig.isModified()) return true;
    if (myVcsConfiguration.SHOW_DIRTY_RECURSIVELY != myShowChangedRecursively.isSelected()) {
      return true;
    }
    return !getModelMappings().equals(myVcsManager.getDirectoryMappings());
  }

  @NotNull
  private List<VcsDirectoryMapping> getModelMappings() {
    return ContainerUtil.mapNotNull(myModel.getItems(),
                                    info -> info == MapInfo.SEPARATOR || info.type == MapInfo.Type.UNREGISTERED ? null : info.mapping);
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

  @Nls
  @Override
  public String getDisplayName() {
    return VcsBundle.message("configurable.VcsDirectoryConfigurationPanel.display.name");
  }

  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposable);
    myLimitHistory.disposeUIResources();
    myScopeFilterConfig.disposeUIResources();
  }
}
