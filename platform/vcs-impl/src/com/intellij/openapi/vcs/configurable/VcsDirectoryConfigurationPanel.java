// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.roots.VcsRootErrorsFinder;
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.vcsUtil.VcsUtil;
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
import static com.intellij.openapi.vcs.VcsConfiguration.getInstance;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static java.util.Arrays.asList;


public class VcsDirectoryConfigurationPanel extends JPanel implements Disposable {
  private static final int POSTPONE_MAPPINGS_LOADING_PANEL = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;

  private final @NotNull Project myProject;
  private final @Nls String myProjectMessage;
  private final ProjectLevelVcsManager myVcsManager;
  private final TableView<MapInfo> myDirectoryMappingTable;
  private final ComboBox<AbstractVcs> myVcsComboBox;

  private final MyDirectoryRenderer myDirectoryRenderer;
  private final ListTableModel<MapInfo> myModel;
  private final List<AbstractVcs> myAllVcss;
  private final boolean myIsDisabled;
  private final VcsConfiguration myVcsConfiguration;
  private final @NotNull Map<String, VcsRootChecker> myCheckers;
  private final VcsUpdateInfoScopeFilterConfigurable myScopeFilterConfig;
  private JBLoadingPanel myLoadingPanel;

  private ProgressIndicator myRootDetectionIndicator = null;

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
      if (value instanceof MapInfo info) {
        SimpleTextAttributes textAttributes = getAttributes(info);

        if (!selected && (info == MapInfo.SEPARATOR || info.type == MapInfo.Type.UNREGISTERED)) {
          setBackground(UIUtil.getDecoratedRowColor());
        }

        if (info == MapInfo.SEPARATOR) {
          append(VcsBundle.message("unregistered.roots.label"), textAttributes);
          return;
        }

        String presentablePath = getPresentablePath(myProject, info.mapping);
        SpeedSearchUtil.appendFragmentsForSpeedSearch(table, presentablePath, textAttributes, selected, this);
      }
    }

    private static @NotNull @NlsSafe String getPresentablePath(@NotNull Project project, @NotNull VcsDirectoryMapping mapping) {
      if (mapping.isDefaultMapping()) {
        return VcsDirectoryMapping.PROJECT_CONSTANT.get();
      }

      String directory = mapping.getDirectory();

      VirtualFile baseDir = project.getBaseDir();
      if (baseDir == null) {
        return new File(directory).getPath();
      }

      File directoryFile = new File(StringUtil.trimEnd(UriUtil.trimTrailingSlashes(directory), "\\") + "/");
      File ioBase = new File(baseDir.getPath());
      if (directoryFile.isAbsolute() && !FileUtil.isAncestor(ioBase, directoryFile, false)) {
        return new File(directory).getPath();
      }

      String relativePath = FileUtil.getRelativePath(ioBase, directoryFile);
      if (".".equals(relativePath) || relativePath == null) {
        return ioBase.getPath();
      }
      else {
        return relativePath + " (" + ioBase + ")";
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

  private static class MyVcsRenderer extends ColoredTableCellRenderer {
    private final MapInfo myInfo;
    private final @NotNull List<? extends AbstractVcs> myAllVcses;

    MyVcsRenderer(@NotNull MapInfo info, @NotNull List<? extends AbstractVcs> allVcses) {
      myInfo = info;
      myAllVcses = allVcses;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JTable table,
                                         Object value,
                                         boolean selected,
                                         boolean hasFocus,
                                         int row,
                                         int column) {
      if (myInfo == MapInfo.SEPARATOR) {
        if (!selected) {
          setBackground(UIUtil.getDecoratedRowColor());
        }
        return;
      }

      if (myInfo.type == MapInfo.Type.UNREGISTERED && !selected) {
        setBackground(UIUtil.getDecoratedRowColor());
      }

      final String vcsName = myInfo.mapping.getVcs();
      String text;
      if (vcsName.isEmpty()) {
        text = VcsBundle.message("none.vcs.presentation");
      }
      else {
        AbstractVcs vcs = ContainerUtil.find(myAllVcses, it -> vcsName.equals(it.getName()));
        if (vcs != null) {
          text = vcs.getDisplayName();
        }
        else {
          text = VcsBundle.message("unknown.vcs.presentation", vcsName);
        }
      }
      append(text, getAttributes(myInfo));
    }
  }

  public VcsDirectoryConfigurationPanel(@NotNull Project project) {
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
    TableSpeedSearch.installOn(myDirectoryMappingTable, info -> {
      return info instanceof MapInfo ? MyDirectoryRenderer.getPresentablePath(myProject, ((MapInfo)info).mapping) : "";
    });

    myScopeFilterConfig = new VcsUpdateInfoScopeFilterConfigurable(myProject, myVcsConfiguration);

    myCheckers = new HashMap<>();
    updateRootCheckers();

    setLayout(new BorderLayout());
    add(createMainComponent());

    myDirectoryRenderer = new MyDirectoryRenderer(myProject);

    myModel = new ListTableModel<>(new ColumnInfo[]{new MyDirectoryColumnInfo(), new MyVcsColumnInfo()});
    myDirectoryMappingTable.setModelAndUpdateColumns(myModel);

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

  @Override
  public void dispose() {
    if (myRootDetectionIndicator != null) myRootDetectionIndicator.cancel();
    myScopeFilterConfig.disposeUIResources();
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
    myScopeFilterConfig.reset();

    List<MapInfo> mappings = new ArrayList<>();
    for (VcsDirectoryMapping mapping : ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(MapInfo.registered(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings()),
                                      isMappingValid(mapping)));
    }
    myModel.setItems(mappings);

    scheduleUnregisteredRootsLoading();
  }

  private void scheduleUnregisteredRootsLoading() {
    if (myProject.isDefault() || !TrustedProjects.isTrusted(myProject)) return;
    if (myRootDetectionIndicator != null) myRootDetectionIndicator.cancel();
    if (!VcsUtil.shouldDetectVcsMappingsFor(myProject)) return;

    myRootDetectionIndicator = BackgroundTaskUtil.executeAndTryWait(indicator -> {
      List<VcsDirectoryMapping> unregisteredRoots = VcsRootErrorsFinder.getInstance(myProject).getOrFind().stream()
        .filter(error -> error.getType() == VcsRootError.Type.UNREGISTERED_ROOT)
        .map(error -> error.getMapping())
        .toList();
      return () -> {
        if (indicator.isCanceled()) return;
        myLoadingPanel.stopLoading();

        if (!unregisteredRoots.isEmpty()) {
          myModel.addRow(MapInfo.SEPARATOR);
          for (VcsDirectoryMapping mapping : unregisteredRoots) {
            myModel.addRow(MapInfo.unregistered(mapping));
          }
        }
      };
    }, () -> myLoadingPanel.startLoading(), POSTPONE_MAPPINGS_LOADING_PANEL, false);
  }

  private boolean isMappingValid(@NotNull VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return true;
    VcsRootChecker checker = myCheckers.get(mapping.getVcs());
    if (checker == null) return true;
    VirtualFile directory = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
    return directory != null && checker.validateRoot(directory);
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
  }


  private void addSelectedUnregisteredMappings(List<MapInfo> infos) {
    List<MapInfo> items = new ArrayList<>(myModel.getItems());
    for (MapInfo info : infos) {
      items.remove(info);
      items.add(MapInfo.registered(info.mapping, isMappingValid(info.mapping)));
    }
    sortAndAddSeparatorIfNeeded(items);
    myModel.setItems(items);
  }

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
  }

  private void removeMapping() {
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
  }

  protected JComponent createMainComponent() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBag gb = new GridBag()
      .setDefaultInsets(JBUI.insets(0, 0, DEFAULT_VGAP, DEFAULT_HGAP))
      .setDefaultWeightX(1)
      .setDefaultFill(GridBagConstraints.HORIZONTAL);

    if (!TrustedProjects.isTrusted(myProject)) {
      EditorNotificationPanel notificationPanel = new EditorNotificationPanel(LightColors.RED, EditorNotificationPanel.Status.Error);
      notificationPanel.setText(VcsBundle.message("configuration.project.not.trusted.label"));
      panel.add(notificationPanel, gb.nextLine().next());
    }

    JComponent mappingsTable = createMappingsTable();
    // don't start loading automatically
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this, POSTPONE_MAPPINGS_LOADING_PANEL * 2);
    myLoadingPanel.add(mappingsTable);
    panel.add(myLoadingPanel, gb.nextLine().next().fillCell().weighty(1.0));

    panel.add(createProjectMappingDescription(), gb.nextLine().next());
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

  public void reset() {
    initializeModel();
  }

  public void apply() throws ConfigurationException {
    adjustIgnoredRootsSettings();
    myVcsManager.setDirectoryMappings(getModelMappings());
    myScopeFilterConfig.apply();
    initializeModel();
  }

  private void adjustIgnoredRootsSettings() {
    List<VcsDirectoryMapping> newMappings = getModelMappings();
    List<VcsDirectoryMapping> previousMappings = myVcsManager.getDirectoryMappings();
    myVcsConfiguration.addIgnoredUnregisteredRoots(previousMappings.stream()
                                                     .filter(mapping -> !newMappings.contains(mapping) && !mapping.isDefaultMapping())
                                                     .map(mapping -> mapping.getDirectory())
                                                     .collect(Collectors.toList()));
    myVcsConfiguration.removeFromIgnoredUnregisteredRoots(map(newMappings, VcsDirectoryMapping::getDirectory));
  }

  public boolean isModified() {
    if (myScopeFilterConfig.isModified()) return true;
    return !getModelMappings().equals(myVcsManager.getDirectoryMappings());
  }

  @NotNull
  private List<VcsDirectoryMapping> getModelMappings() {
    return ContainerUtil.mapNotNull(myModel.getItems(),
                                    info -> info == MapInfo.SEPARATOR || info.type == MapInfo.Type.UNREGISTERED ? null : info.mapping);
  }

  private class MyDirectoryColumnInfo extends ColumnInfo<MapInfo, MapInfo> {
    MyDirectoryColumnInfo() {
      super(VcsBundle.message("column.info.configure.vcses.directory"));
    }

    @Override
    public MapInfo valueOf(final MapInfo mapping) {
      return mapping;
    }

    @Override
    public TableCellRenderer getRenderer(MapInfo vcsDirectoryMapping) {
      return myDirectoryRenderer;
    }
  }

  private class MyVcsColumnInfo extends ColumnInfo<MapInfo, String> {
    MyVcsColumnInfo() {
      super(VcsBundle.message("column.name.configure.vcses.vcs"));
    }

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
      o.mapping = new VcsDirectoryMapping(o.mapping.getDirectory(), aValue, o.mapping.getRootSettings());
    }

    @Override
    public TableCellRenderer getRenderer(final MapInfo info) {
      return new MyVcsRenderer(info, myAllVcss);
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
  }
}
