// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.FileContentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Gregory.Shrago
 */
@ApiStatus.Internal
public final class InjectionsSettingsUI extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll {
  private final Project myProject;
  private final CfgInfo[] myInfos;

  private final JPanel myRoot;
  private final InjectionsTable myInjectionsTable;
  private final Map<String, LanguageInjectionSupport> mySupports = new LinkedHashMap<>();
  private final Map<String, AnAction> myEditActions = new LinkedHashMap<>();
  private final List<AnAction> myAddActions = new ArrayList<>();
  private final JLabel myCountLabel;

  private final Configuration myConfiguration;

  public InjectionsSettingsUI(@NotNull Project project) {
    myProject = project;
    myConfiguration = Configuration.getProjectInstance(project);

    final CfgInfo currentInfo = new CfgInfo(myConfiguration, "Project");
    myInfos = myConfiguration instanceof Configuration.Prj ?
              new CfgInfo[]{new CfgInfo(((Configuration.Prj)myConfiguration).getParentConfiguration(), "IDE"), currentInfo}
                                                           : new CfgInfo[]{currentInfo};

    myRoot = new JPanel(new BorderLayout());

    myInjectionsTable = new InjectionsTable(getInjInfoList(myInfos));
    myInjectionsTable.getEmptyText().setText(IntelliLangBundle.message("table.empty.text.no.injections.configured2"));

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myInjectionsTable);
    createActions(decorator);

    //myRoot.add(new TitledSeparator("Languages injection places"), BorderLayout.NORTH);
    myRoot.add(decorator.createPanel(), BorderLayout.CENTER);
    myCountLabel = new JLabel();
    myCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    myCountLabel.setForeground(SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES.getFgColor());
    myRoot.add(myCountLabel, BorderLayout.SOUTH);
    updateCountLabel();
  }

  private void createActions(ToolbarDecorator decorator) {
    final Consumer<BaseInjection> consumer = this::addInjection;
    final Factory<BaseInjection> producer = (NullableFactory<BaseInjection>)() -> {
      final InjInfo info = getSelectedInjection();
      return info == null? null : info.injection;
    };
    for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
      ContainerUtil.addAll(myAddActions, support.createAddActions(myProject, consumer));
      final AnAction action = support.createEditAction(myProject, producer);
      myEditActions
        .put(support.getId(), action == null ? AbstractLanguageInjectionSupport.createDefaultEditAction(myProject, producer) : action);
      mySupports.put(support.getId(), support);
    }
    myAddActions.sort((o1, o2) -> Comparing.compare(o1.getTemplatePresentation().getText(), o2.getTemplatePresentation().getText()));
    decorator.disableUpDownActions();
    decorator.setAddActionUpdater(e -> !myAddActions.isEmpty());
    decorator.setAddAction(this::performAdd);
    decorator.setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN);
    decorator.setRemoveActionUpdater(e -> {
      boolean enabled = false;
      for (InjInfo info : getSelectedInjections()) {
        if (!info.bundled) {
          enabled = true;
          break;
        }
      }
      return enabled;
    });
    decorator.setRemoveAction(button -> performRemove());

    decorator.setEditActionUpdater(e -> {
      AnAction edit = getEditAction();
      if (edit != null) edit.update(e);
      return edit != null && e.getPresentation().isEnabled();
    });
    decorator.setEditAction(button -> performEditAction());
    decorator.addExtraAction(new MyAction(IntelliLangBundle.messagePointer("action.AnActionButton.text.duplicate"),
                                          IntelliLangBundle.messagePointer("action.AnActionButton.description.duplicate"),
                                          IconManager.getInstance().getPlatformIcon(PlatformIcons.Copy)) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(getEditAction() != null);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final InjInfo injection = getSelectedInjection();
        if (injection != null) {
          addInjection(injection.injection.copy());
          //performEditAction(e);
        }
      }
    });

    decorator.addExtraAction(new MyAction(IntelliLangBundle.messagePointer("action.AnActionButton.text.enable.selected.injections"),
                                          IntelliLangBundle.messagePointer("action.AnActionButton.description.enable.selected.injections"),
                                          com.intellij.util.PlatformIcons.SELECT_ALL_ICON) {

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        performSelectedInjectionsEnabled(true);
      }
    });
    decorator.addExtraAction(new MyAction(IntelliLangBundle.messagePointer("action.AnActionButton.text.disable.selected.injections"),
                                          IntelliLangBundle.messagePointer("action.AnActionButton.description.disable.selected.injections"),
                                          com.intellij.util.PlatformIcons.UNSELECT_ALL_ICON) {

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        performSelectedInjectionsEnabled(false);
      }
    });

    new DumbAwareAction(IdeBundle.messagePointer("action.Anonymous.text.toggle")) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        SpeedSearchSupply supply = SpeedSearchSupply.getSupply(myInjectionsTable);
        e.getPresentation().setEnabled(supply == null || !supply.isPopupActive());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        performToggleAction();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myInjectionsTable);

    if (myInfos.length > 1) {
      AnAction shareAction = new MyAction(IntelliLangBundle.messagePointer("action.AnActionButton.text.move.to.ide.scope"),
                                          IntelliLangBundle.messagePointer("action.AnActionButton.text.move.to.ide.scope"),
                                          com.intellij.util.PlatformIcons.IMPORT_ICON) {
        @Override
        public void update(@NotNull AnActionEvent e) {
          CfgInfo cfg = getTargetCfgInfo(getSelectedInjections());
          e.getPresentation().setText(cfg == getDefaultCfgInfo() ? IntelliLangBundle.message("label.text.move.to.ide.scope")
                                                                 : IntelliLangBundle.message("label.text.move.to.project.scope"));
          e.getPresentation().setEnabled(cfg != null);
        }

        @Override
        public void actionPerformed(@NotNull final AnActionEvent e) {
          final List<InjInfo> injections = getSelectedInjections();
          final CfgInfo cfg = getTargetCfgInfo(injections);
          if (cfg == null) return;
          for (InjInfo info : injections) {
            if (info.cfgInfo == cfg) continue;
            if (info.bundled) continue;
            info.cfgInfo.injectionInfos.remove(info);
            cfg.addInjection(info.injection);
          }
          final int[] selectedRows = myInjectionsTable.getSelectedRows();
          myInjectionsTable.getListTableModel().setItems(new ArrayList<>(getInjInfoList(myInfos)));
          TableUtil.selectRows(myInjectionsTable, selectedRows);
        }

        @Nullable
        private CfgInfo getTargetCfgInfo(final List<InjInfo> injections) {
          CfgInfo cfg = null;
          for (InjInfo info : injections) {
            if (info.bundled) {
              continue;
            }
            if (cfg == null) {
              cfg = info.cfgInfo;
            }
            else if (cfg != info.cfgInfo) return info.cfgInfo;
          }
          if (cfg == null) return null;
          for (CfgInfo info : myInfos) {
            if (info != cfg) return info;
          }
          throw new AssertionError();
        }
      };
      shareAction.setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.SHIFT_DOWN_MASK)));
      decorator.addExtraAction(shareAction);
    }
    decorator.addExtraAction(new MyAction(IntelliLangBundle.messagePointer("action.AnActionButton.text.import"),
                                          IntelliLangBundle.messagePointer("action.AnActionButton.description.import"),
                                          AllIcons.Actions.Install) {

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        doImportAction(e.getDataContext());
        updateCountLabel();
      }
    });
    decorator.addExtraAction(new MyAction(IntelliLangBundle.messagePointer("action.AnActionButton.text.export"),
                                          IntelliLangBundle.messagePointer("action.AnActionButton.description.export"),
                                          AllIcons.ToolbarDecorator.Export) {

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        List<BaseInjection> injections = getInjectionList(getSelectedInjections());
        VirtualFileWrapper wrapper = FileChooserFactory.getInstance()
          .createSaveFileDialog(
            new FileSaverDescriptor(IntelliLangBundle.message("dialog.title.export.selected.injections.to.file"), "", "xml"), myProject)
          .save((Path)null, null);
        if (wrapper == null) {
          return;
        }
        Configuration configuration = new Configuration();
        configuration.setInjections(injections);
        try {
          JDOMUtil.write(configuration.getState(), wrapper.getFile().toPath());
        }
        catch (IOException ex) {
          final String msg = ex.getLocalizedMessage();
          Messages.showErrorDialog(myProject, StringUtil.isNotEmpty(msg) ? msg : ex.toString(),
                                   IntelliLangBundle.message("dialog.title.export.failed"));
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!getSelectedInjections().isEmpty());
      }
    });
  }


  private void performEditAction() {
    final AnAction action = getEditAction();
    if (action != null) {
      final int row = myInjectionsTable.getSelectedRow();
      action.actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(myInjectionsTable),
                                               ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0));
      myInjectionsTable.getListTableModel().fireTableDataChanged();
      myInjectionsTable.getSelectionModel().setSelectionInterval(row, row);
      updateCountLabel();
    }
  }

  private void updateCountLabel() {
    int placesCount = 0;
    int enablePlaceCount = 0;
    final List<InjInfo> items = myInjectionsTable.getListTableModel().getItems();
    if (!items.isEmpty()) {
      for (InjInfo injection : items) {
        for (InjectionPlace place : injection.injection.getInjectionPlaces()) {
          placesCount++;
          if (place.isEnabled()) enablePlaceCount++;
        }
      }
      myCountLabel.setText(
        IntelliLangBundle.message("label.text.0.injection.1.2.of.3.place.4.enabled", items.size(), enablePlaceCount, placesCount));
    }
    else {
      myCountLabel.setText(IntelliLangBundle.message("label.text.no.injections.configured"));
    }
  }

  @Nullable
  private AnAction getEditAction() {
    final InjInfo info = getSelectedInjection();
    final String supportId = info == null? null : info.injection.getSupportId();
    return supportId == null? null : myEditActions.get(supportId);
  }

  private void addInjection(final BaseInjection injection) {
    final InjInfo info = getDefaultCfgInfo().addInjection(injection);
    myInjectionsTable.getListTableModel().setItems(new ArrayList<>(getInjInfoList(myInfos)));
    final int index = myInjectionsTable.convertRowIndexToView(myInjectionsTable.getListTableModel().getItems().indexOf(info));
    myInjectionsTable.getSelectionModel().setSelectionInterval(index, index);
    TableUtil.scrollSelectionToVisible(myInjectionsTable);
  }

  private CfgInfo getDefaultCfgInfo() {
    return myInfos[0];
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  protected Configurable[] buildConfigurables() {
      final ArrayList<Configurable> configurables = new ArrayList<>();
      for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
        ContainerUtil.addAll(configurables, support.createSettings(myProject, myConfiguration));
      }
      configurables.sort((o1, o2) -> Comparing.compare(o1.getDisplayName(), o2.getDisplayName()));
      return configurables.toArray(new Configurable[0]);
  }

  @NotNull
  @Override
  public String getId() {
    return "IntelliLang.Configuration";
  }

  private static void sortInjections(final List<? extends BaseInjection> injections) {
    injections.sort((o1, o2) -> {
      final int support = Comparing.compare(o1.getSupportId(), o2.getSupportId());
      if (support != 0) return support;
      final int lang = Comparing.compare(o1.getInjectedLanguageId(), o2.getInjectedLanguageId());
      if (lang != 0) return lang;
      return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
    });
  }

  @Override
  public JComponent createComponent() {
    return myRoot;
  }

  @Override
  public void reset() {
    for (CfgInfo info : myInfos) {
      info.reset();
    }
    myInjectionsTable.getListTableModel().setItems(new ArrayList<>(getInjInfoList(myInfos)));
    updateCountLabel();
  }

  @Override
  public void apply() {
    for (CfgInfo info : myInfos) {
      info.apply();
    }
    reset();
  }

  @Override
  public boolean isModified() {
    return ContainerUtil.exists(myInfos, CfgInfo::isModified);
  }

  private void performSelectedInjectionsEnabled(final boolean enabled) {
    for (InjInfo info : getSelectedInjections()) {
      info.injection.setPlaceEnabled(null, enabled);
    }
    myInjectionsTable.updateUI();
    updateCountLabel();
  }

  private void performToggleAction() {
    final List<InjInfo> selectedInjections = getSelectedInjections();
    boolean enabledExists = false;
    boolean disabledExists = false;
    for (InjInfo info : selectedInjections) {
      if (info.injection.isEnabled()) enabledExists = true;
      else disabledExists = true;
      if (enabledExists && disabledExists) break;
    }
    boolean allEnabled = !enabledExists && disabledExists;
    performSelectedInjectionsEnabled(allEnabled);
  }

  private void performRemove() {
    final int selectedRow = myInjectionsTable.getSelectedRow();
    if (selectedRow < 0) return;
    final List<InjInfo> selected = getSelectedInjections();
    for (InjInfo info : selected) {
      if (info.bundled) continue;
      info.cfgInfo.injectionInfos.remove(info);
    }
    myInjectionsTable.getListTableModel().setItems(new ArrayList<>(getInjInfoList(myInfos)));
    final int index = Math.min(myInjectionsTable.getListTableModel().getRowCount() - 1, selectedRow);
    myInjectionsTable.getSelectionModel().setSelectionInterval(index, index);
    TableUtil.scrollSelectionToVisible(myInjectionsTable);
    updateCountLabel();
  }

  private List<InjInfo> getSelectedInjections() {
    List<InjInfo> toRemove = new ArrayList<>();
    for (int row : myInjectionsTable.getSelectedRows()) {
      toRemove.add(myInjectionsTable.getItems().get(myInjectionsTable.convertRowIndexToModel(row)));
    }
    return toRemove;
  }

  @Nullable
  private InjInfo getSelectedInjection() {
    final int row = myInjectionsTable.getSelectedRow();
    return row < 0? null : myInjectionsTable.getItems().get(myInjectionsTable.convertRowIndexToModel(row));
  }

  private void performAdd(AnActionButton e) {
    DefaultActionGroup group = new DefaultActionGroup(myAddActions);

    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true, this::updateCountLabel, -1)
      .show(e.getPreferredPopupPoint());
  }

  @Override
  @Nls
  public String getDisplayName() {
    return IntelliLangBundle.message("configurable.InjectionsSettingsUI.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.injection.language.injection.settings";
  }

  private final class InjectionsTable extends TableView<InjInfo> {
    private InjectionsTable(final List<InjInfo> injections) {
      super(new ListTableModel<>(createInjectionColumnInfos(), injections, 1));
      setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
      getColumnModel().getColumn(2).setCellRenderer(createLanguageCellRenderer());
      getColumnModel().getColumn(1).setCellRenderer(createDisplayNameCellRenderer());
      getColumnModel().getColumn(0).setResizable(false);
      setShowGrid(false);
      setShowVerticalLines(false);
      setGridColor(getForeground());
      TableUtil.setupCheckboxColumn(this, 0);

      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent e) {
          final int row = rowAtPoint(e.getPoint());
          if (row < 0) return false;
          if (columnAtPoint(e.getPoint()) <= 0) return false;
          myInjectionsTable.getSelectionModel().setSelectionInterval(row, row);
          performEditAction();
          return true;
        }
      }.installOn(this);

      final String[] maxName = new String[]{""};
      ContainerUtil.process(injections, injection -> {
        String languageId = injection.injection.getInjectedLanguageId();
        Language language = InjectedLanguage.findLanguageById(languageId);
        String displayName = language == null ? languageId : language.getDisplayName();
        if (maxName[0].length() < displayName.length()) maxName[0] = displayName;
        return true;
      });
      ContainerUtil.process(InjectedLanguage.getAvailableLanguages(), language -> {
        String displayName = language.getDisplayName();
        if (maxName[0].length() < displayName.length()) maxName[0] = displayName;
        return true;
      });
      Icon icon = FileTypes.PLAIN_TEXT.getIcon();
      int preferred = (int)(new JLabel(maxName[0], icon, SwingConstants.LEFT).getPreferredSize().width * 1.1);
      getColumnModel().getColumn(2).setMinWidth(preferred);
      getColumnModel().getColumn(2).setPreferredWidth(preferred);
      getColumnModel().getColumn(2).setMaxWidth(preferred);
      TableViewSpeedSearch<InjInfo> search = new TableViewSpeedSearch<>(this, null) {
        @Override
        protected String getItemText(@NotNull InjInfo element) {
          final BaseInjection injection = element.injection;
          return injection.getSupportId() + " " + injection.getInjectedLanguageId() + " " + injection.getDisplayName();
        }
      };
      search.setupListeners();
    }

  }

  private ColumnInfo<?, ?>[] createInjectionColumnInfos() {
    final TableCellRenderer booleanCellRenderer = createBooleanCellRenderer();
    final TableCellRenderer displayNameCellRenderer = createDisplayNameCellRenderer();
    final TableCellRenderer languageCellRenderer = createLanguageCellRenderer();
    final Comparator<InjInfo> languageComparator =
      (o1, o2) -> Comparing.compare(o1.injection.getInjectedLanguageId(), o2.injection.getInjectedLanguageId());
    final Comparator<InjInfo> displayNameComparator = (o1, o2) -> {
      final int support = Comparing.compare(o1.injection.getSupportId(), o2.injection.getSupportId());
      if (support != 0) return support;
      return Comparing.compare(o1.injection.getDisplayName(), o2.injection.getDisplayName());
    };
    final ColumnInfo<?, ?>[] columnInfos = {new ColumnInfo<InjInfo, Boolean>(" ") {
      @Override
      public Class<?> getColumnClass() {
        return Boolean.class;
      }

      @Override
      public Boolean valueOf(final InjInfo o) {
        return o.injection.isEnabled();
      }

      @Override
      public boolean isCellEditable(final InjInfo injection) {
        return true;
      }

      @Override
      public void setValue(final InjInfo injection, final Boolean value) {
        injection.injection.setPlaceEnabled(null, value.booleanValue());
      }

      @Override
      public TableCellRenderer getRenderer(final InjInfo injection) {
        return booleanCellRenderer;
      }
    }, new ColumnInfo<InjInfo, InjInfo>(IntelliLangBundle.message("column.info.name")) {
      @Override
      public InjInfo valueOf(final InjInfo info) {
        return info;
      }

      @Override
      public Comparator<InjInfo> getComparator() {
        return displayNameComparator;
      }

      @Override
      public TableCellRenderer getRenderer(final InjInfo injection) {
        return displayNameCellRenderer;
      }
    }, new ColumnInfo<InjInfo, InjInfo>(IntelliLangBundle.message("column.info.language")) {
      @Override
      public InjInfo valueOf(final InjInfo info) {
        return info;
      }

      @Override
      public Comparator<InjInfo> getComparator() {
        return languageComparator;
      }

      @Override
      public TableCellRenderer getRenderer(final InjInfo info) {
        return languageCellRenderer;
      }
    }};
    if (myInfos.length > 1) {
      final TableCellRenderer typeRenderer = createTypeRenderer();
      return ArrayUtil.append(columnInfos, new ColumnInfo<InjInfo, String>(IntelliLangBundle.message("column.info.scope")) {
        @Override
        public String valueOf(final InjInfo info) {
          return info.bundled ? "Built-in" : info.cfgInfo.title;
        }

        @Override
        public TableCellRenderer getRenderer(final InjInfo injInfo) {
          return typeRenderer;
        }

        @Override
        public int getWidth(final JTable table) {
          return table.getFontMetrics(table.getFont()).stringWidth(StringUtil.repeatSymbol('m', 6));
        }

        @Override
        public Comparator<InjInfo> getComparator() {
          return (o1, o2) -> Comparing.compare(valueOf(o1), valueOf(o2));
        }
      });
    }
    return columnInfos;
  }

  private static BooleanTableCellRenderer createBooleanCellRenderer() {
    return new BooleanTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(final JTable table,
                                                     final Object value,
                                                     final boolean isSelected,
                                                     final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        return setLabelColors(super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column), table, isSelected);
      }
    };
  }

  private static TableCellRenderer createLanguageCellRenderer() {
    return new TableCellRenderer() {
      final JLabel myLabel = new JLabel();

      @Override
      public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        final InjInfo injection = (InjInfo)value;
        // fix for a marvellous Swing peculiarity: AccessibleJTable likes to pass null here
        if (injection == null) return myLabel;
        final String languageId = injection.injection.getInjectedLanguageId();
        final Language language = InjectedLanguage.findLanguageById(languageId);
        final FileType fileType = language == null ? null : language.getAssociatedFileType();
        myLabel.setIcon(fileType == null ? null : fileType.getIcon());
        myLabel.setText(language == null ? languageId : language.getDisplayName());
        setLabelColors(myLabel, table, isSelected);
        return myLabel;
      }
    };
  }

  private TableCellRenderer createDisplayNameCellRenderer() {
    return new TableCellRenderer() {
      final SimpleColoredComponent myLabel = new SimpleColoredComponent();
      final SimpleColoredText myText = new SimpleColoredText();

      @Override
      public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        myLabel.clear();
        final InjInfo info = (InjInfo)value;
        // fix for a marvellous Swing peculiarity: AccessibleJTable likes to pass null here
        if (info == null) return myLabel;
        final SimpleTextAttributes grayAttrs = isSelected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
        final String supportId = info.injection.getSupportId();
        myText.append(supportId + ": ", grayAttrs);
        mySupports.get(supportId).setupPresentation(info.injection, myText, isSelected);
        myText.appendToComponent(myLabel);
        myText.clear();
        setLabelColors(myLabel, table, isSelected);
        return myLabel;
      }
    };
  }

  private static TableCellRenderer createTypeRenderer() {
    return new TableCellRenderer() {
      final SimpleColoredComponent myLabel = new SimpleColoredComponent();

      @Override
      public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        myLabel.clear();
        final String info = (String)value;
        if (info == null) return myLabel;
        final SimpleTextAttributes grayAttrs = isSelected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
        myLabel.append(info, grayAttrs);
        setLabelColors(myLabel, table, isSelected);
        return myLabel;
      }
    };
  }

  private static Component setLabelColors(final Component label, final JTable table, final boolean isSelected) {
    if (label instanceof JComponent) {
      ((JComponent)label).setOpaque(true);
    }
    label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
    label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    return label;
  }

  private void doImportAction(final DataContext dataContext) {
    var descriptor = new FileChooserDescriptor(true, false, false, false, true, false)
      .withExtensionFilter(FileTypeManager.getInstance().getStdFileType("XML"))
      .withTitle(IntelliLangBundle.message("dialog.file.chooser.title.import.configuration"))
      .withDescription(IntelliLangBundle.message("dialog.file.chooser.description.please.select.the.configuration.file"));

    descriptor.putUserData(LangDataKeys.MODULE_CONTEXT, PlatformCoreDataKeys.MODULE.getData(dataContext));

    final SplitterProportionsData splitterData = new SplitterProportionsDataImpl();
    splitterData.externalizeFromDimensionService("IntelliLang.ImportSettingsKey.SplitterProportions");

    final VirtualFile file = FileChooser.chooseFile(descriptor, myProject, null);
    if (file == null) return;
    try {
      final Configuration cfg = Configuration.load(file.getInputStream());
      if (cfg == null) {
        Messages.showWarningDialog(myProject,
                                   IntelliLangBundle.message("dialog.message.the.selected.file"),
                                   IntelliLangBundle.message("dialog.title.nothing.to.import"));
        return;
      }
      final CfgInfo info = getDefaultCfgInfo();
      final Map<String,Set<InjInfo>> currentMap =
        ContainerUtil.classify(info.injectionInfos.iterator(), o -> o.injection.getSupportId());
      final List<BaseInjection> originalInjections = new ArrayList<>();
      final List<BaseInjection> newInjections = new ArrayList<>();
      //// remove duplicates
      //for (String supportId : InjectorUtils.getActiveInjectionSupportIds()) {
      //  final Set<BaseInjection> currentInjections = currentMap.get(supportId);
      //  if (currentInjections == null) continue;
      //  for (BaseInjection injection : currentInjections) {
      //    Configuration.importInjections(newInjections, Collections.singleton(injection), originalInjections, newInjections);
      //  }
      //}
      //myInjections.clear();
      //myInjections.addAll(newInjections);

      for (String supportId : InjectorUtils.getActiveInjectionSupportIds()) {
        ArrayList<InjInfo> list = new ArrayList<>(ObjectUtils.notNull(currentMap.get(supportId), Collections.emptyList()));
        final List<BaseInjection> currentInjections = getInjectionList(list);
        final List<BaseInjection> importingInjections = cfg.getInjections(supportId);
        Configuration.importInjections(currentInjections, importingInjections, originalInjections, newInjections);
      }
      info.replace(originalInjections, newInjections);
      myInjectionsTable.getListTableModel().setItems(new ArrayList<>(getInjInfoList(myInfos)));
      final int n = newInjections.size();
      if (n > 1) {
        Messages.showInfoMessage(myProject, IntelliLangBundle.message("dialog.message.0.entries.have.been.successfully.imported", n),
                                 IntelliLangBundle.message("dialog.title.import.successful"));
      }
      else if (n == 1) {
        Messages.showInfoMessage(myProject, IntelliLangBundle.message("dialog.message.one.entry.has.been.successfully.imported"),
                                 IntelliLangBundle.message("dialog.title.import.successful"));
      }
      else {
        Messages.showInfoMessage(myProject, IntelliLangBundle.message("dialog.message.no.new.entries.have.been.imported"),
                                 IntelliLangBundle.message("dialog.title.import"));
      }
    }
    catch (Exception ex) {
      Configuration.LOG.error(ex);

      final String msg = ex.getLocalizedMessage();
      Messages.showErrorDialog(myProject, StringUtil.isNotEmpty(msg) ? msg : ex.toString(),
                               IntelliLangBundle.message("dialog.title.import.failed"));
    }
  }

  private static class CfgInfo {
    final Configuration cfg;
    final List<BaseInjection> originalInjections;
    final List<InjInfo> injectionInfos = new ArrayList<>();
    final Set<BaseInjection> bundledInjections = new ObjectOpenCustomHashSet<>(new SameParamsAndPlacesStrategy());
    final String title;

    CfgInfo(Configuration cfg, final String title) {
      this.cfg = cfg;
      this.title = title;
      bundledInjections.addAll(cfg.getDefaultInjections());
      originalInjections = new ArrayList<>(ContainerUtil
                                             .concat(InjectorUtils.getActiveInjectionSupportIds(), s -> {
                                               List<BaseInjection> injections =
                                                 this.cfg instanceof Configuration.Prj ? ((Configuration.Prj)this.cfg)
                                                   .getOwnInjections(s) : this.cfg
                                                   .getInjections(s);
                                               return ContainerUtil.findAll(injections, injection -> {
                                                 String id = injection.getInjectedLanguageId();
                                                 return InjectedLanguage.findLanguageById(id) != null ||
                                                        ReferenceInjector.findById(id) != null;
                                               });
                                             }));
      sortInjections(originalInjections);
      reset();
    }

    public void apply() {
      final List<BaseInjection> injectionList = getInjectionList(injectionInfos);
      cfg.replaceInjections(injectionList, originalInjections, true);
      originalInjections.clear();
      originalInjections.addAll(injectionList);
      sortInjections(originalInjections);
      FileContentUtil.reparseOpenedFiles();
    }

    public void reset() {
      injectionInfos.clear();
      for (BaseInjection injection : originalInjections) {
        injectionInfos.add(new InjInfo(injection.copy(), this, bundledInjections.contains(injection)));
      }
    }

    public InjInfo addInjection(final BaseInjection injection) {
      final InjInfo info = new InjInfo(injection, this, false);
      injectionInfos.add(info);
      return info;
    }

    public boolean isModified() {
      final List<BaseInjection> copy = new ArrayList<>(getInjectionList(injectionInfos));
      sortInjections(copy);
      return !originalInjections.equals(copy);
    }

    public void replace(final List<? extends BaseInjection> originalInjections, final List<? extends BaseInjection> newInjections) {
      for (Iterator<InjInfo> it = injectionInfos.iterator(); it.hasNext(); ) {
        final InjInfo info = it.next();
        if (originalInjections.contains(info.injection)) it.remove();
      }
      for (BaseInjection newInjection : newInjections) {
        injectionInfos.add(new InjInfo(newInjection, this, false));
      }
    }

  }

  private static class SameParamsAndPlacesStrategy implements Hash.Strategy<BaseInjection> {
    @Override
    public int hashCode(@Nullable BaseInjection object) {
      return object == null ? 0 : object.hashCode();
    }

    @Override
    public boolean equals(@Nullable BaseInjection o1, @Nullable BaseInjection o2) {
      return o1 == o2 || (o1 != null && o2 != null && o1.sameLanguageParameters(o2) && Arrays.equals(o1.getInjectionPlaces(), o2.getInjectionPlaces()));
    }
  }

  private record InjInfo(BaseInjection injection, CfgInfo cfgInfo, boolean bundled) {

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  private static List<InjInfo> getInjInfoList(final CfgInfo[] infos) {
    return ContainerUtil.concat(infos, cfgInfo -> cfgInfo.injectionInfos);
  }

  private static @NotNull List<BaseInjection> getInjectionList(final List<InjInfo> list) {
    return new AbstractList<>() {
      @Override
      public BaseInjection get(final int index) {
        return list.get(index).injection;
      }

      @Override
      public int size() {
        return list.size();
      }
    };
  }

  private static abstract class MyAction extends DumbAwareAction {
    MyAction(@NotNull Supplier<@NlsActions.ActionText String> dynamicText,
             @NotNull Supplier<@NlsActions.ActionDescription String> dynamicDescription,
             @Nullable Icon icon) {
      super(dynamicText, dynamicDescription, icon);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
}
