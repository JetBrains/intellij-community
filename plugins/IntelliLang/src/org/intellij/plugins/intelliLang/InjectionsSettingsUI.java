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

package org.intellij.plugins.intelliLang;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
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
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jdom.Document;
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
import java.util.*;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class InjectionsSettingsUI extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll {

  private final Project myProject;
  private final CfgInfo[] myInfos;

  private final JPanel myRoot;
  private final InjectionsTable myInjectionsTable;
  private final Map<String, LanguageInjectionSupport> mySupports = ContainerUtil.newLinkedHashMap();
  private final Map<String, AnAction> myEditActions = ContainerUtil.newLinkedHashMap();
  private final List<AnAction> myAddActions = ContainerUtil.newArrayList();
  private final JLabel myCountLabel;

  private Configuration myConfiguration;

  public InjectionsSettingsUI(final Project project, final Configuration configuration) {
    myProject = project;
    myConfiguration = configuration;

    final CfgInfo currentInfo = new CfgInfo(configuration, "Project");
    myInfos = configuration instanceof Configuration.Prj ?
              new CfgInfo[]{new CfgInfo(((Configuration.Prj)configuration).getParentConfiguration(), "IDE"), currentInfo}
                                                         : new CfgInfo[]{currentInfo};

    myRoot = new JPanel(new BorderLayout());

    myInjectionsTable = new InjectionsTable(getInjInfoList(myInfos));
    myInjectionsTable.getEmptyText().setText("No injections configured");

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myInjectionsTable);
    createActions(decorator);

    //myRoot.add(new TitledSeparator("Languages injection places"), BorderLayout.NORTH);
    myRoot.add(decorator.createPanel(), BorderLayout.CENTER);
    myCountLabel = new JLabel();
    myCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    myCountLabel.setForeground(SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.getFgColor());
    myRoot.add(myCountLabel, BorderLayout.SOUTH);
    updateCountLabel();
  }

  private void createActions(ToolbarDecorator decorator) {
    final Consumer<BaseInjection> consumer = injection -> addInjection(injection);
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
    Collections.sort(myAddActions,
                     (o1, o2) -> Comparing.compare(o1.getTemplatePresentation().getText(), o2.getTemplatePresentation().getText()));
    decorator.disableUpDownActions();
    decorator.setAddActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        return !myAddActions.isEmpty();
      }
    });
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        performAdd(button);
      }
    });
    decorator.setRemoveActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        boolean enabled = false;
        for (InjInfo info : getSelectedInjections()) {
          if (!info.bundled) {
            enabled = true;
            break;
          }
        }
        return enabled;
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        performRemove();
      }
    });

    decorator.setEditActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        AnAction edit = getEditAction();
        if (edit != null) edit.update(e);
        return edit != null && edit.getTemplatePresentation().isEnabled();
      }
    });
    decorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        performEditAction();
      }
    });
    decorator.addExtraAction(new DumbAwareActionButton("Duplicate", "Duplicate", PlatformIcons.COPY_ICON) {

      @Override
      public boolean isEnabled() {
        return getEditAction() != null;
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

    decorator.addExtraAction(new DumbAwareActionButton("Enable Selected Injections", "Enable Selected Injections", PlatformIcons.SELECT_ALL_ICON) {

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        performSelectedInjectionsEnabled(true);
      }
    });
    decorator.addExtraAction(new DumbAwareActionButton("Disable Selected Injections", "Disable Selected Injections", PlatformIcons.UNSELECT_ALL_ICON) {

        @Override
        public void actionPerformed(@NotNull final AnActionEvent e) {
          performSelectedInjectionsEnabled(false);
        }
      });

    new DumbAwareAction("Toggle") {
      @Override
      public void update(@NotNull AnActionEvent e) {
        SpeedSearchSupply supply = SpeedSearchSupply.getSupply(myInjectionsTable);
        e.getPresentation().setEnabled(supply == null || !supply.isPopupActive());
      }

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        performToggleAction();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myInjectionsTable);

    if (myInfos.length > 1) {
      AnActionButton shareAction = new DumbAwareActionButton("Move to IDE Scope", null, PlatformIcons.IMPORT_ICON) {
        {
          addCustomUpdater(new AnActionButtonUpdater() {
            @Override
            public boolean isEnabled(AnActionEvent e) {
              CfgInfo cfg = getTargetCfgInfo(getSelectedInjections());
              e.getPresentation().setText(cfg == getDefaultCfgInfo() ? "Move to IDE Scope" : "Move to Project Scope");
              return cfg != null;
            }
          });
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
          myInjectionsTable.getListTableModel().setItems(getInjInfoList(myInfos));
          TableUtil.selectRows(myInjectionsTable, selectedRows);
        }

        @Nullable
        private CfgInfo getTargetCfgInfo(final List<InjInfo> injections) {
          CfgInfo cfg = null;
          for (InjInfo info : injections) {
            if (info.bundled) {
              continue;
            }
            if (cfg == null) cfg = info.cfgInfo;
            else if (cfg != info.cfgInfo) return info.cfgInfo;
          }
          if (cfg == null) return null;
          for (CfgInfo info : myInfos) {
            if (info != cfg) return info;
          }
          throw new AssertionError();
        }
      };
      shareAction.setShortcut(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.SHIFT_DOWN_MASK)));
      decorator.addExtraAction(shareAction);
    }
    decorator.addExtraAction(new DumbAwareActionButton("Import", "Import", AllIcons.Actions.Install) {

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        doImportAction(e.getDataContext());
        updateCountLabel();
      }
    });
    decorator.addExtraAction(new DumbAwareActionButton("Export", "Export", AllIcons.Actions.Export) {

      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        final List<BaseInjection> injections = getInjectionList(getSelectedInjections());
        final VirtualFileWrapper wrapper = FileChooserFactory.getInstance().createSaveFileDialog(
          new FileSaverDescriptor("Export Selected Injections to File...", "", "xml"), myProject).save(null, null);
        if (wrapper == null) return;
        final Configuration configuration = new Configuration();
        configuration.setInjections(injections);
        final Document document = new Document(configuration.getState());
        try {
          JDOMUtil.writeDocument(document, wrapper.getFile(), "\n");
        }
        catch (IOException ex) {
          final String msg = ex.getLocalizedMessage();
          Messages.showErrorDialog(myProject, msg != null && msg.length() > 0 ? msg : ex.toString(), "Export Failed");
        }
      }

      @Override
      public boolean isEnabled() {
        return !getSelectedInjections().isEmpty();
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
    int enablePlacesCount = 0;
    final List<InjInfo> items = myInjectionsTable.getListTableModel().getItems();
    if (!items.isEmpty()) {
      for (InjInfo injection : items) {
        for (InjectionPlace place : injection.injection.getInjectionPlaces()) {
          placesCount++;
          if (place.isEnabled()) enablePlacesCount++;
        }
      }
      myCountLabel.setText(items.size() + " injection" + (items.size() > 1 ? "s" : "") + " (" + enablePlacesCount + " of " +
                           placesCount + " place" + (placesCount > 1 ? "s" : "") + " enabled) ");
    }
    else {
      myCountLabel.setText("no injections configured ");
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
    myInjectionsTable.getListTableModel().setItems(getInjInfoList(myInfos));
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
      Collections.sort(configurables, (o1, o2) -> Comparing.compare(o1.getDisplayName(), o2.getDisplayName()));
      return configurables.toArray(new Configurable[configurables.size()]);
  }

  @NotNull
  @Override
  public String getId() {
    return "IntelliLang.Configuration";
  }

  private static void sortInjections(final List<BaseInjection> injections) {
    Collections.sort(injections, (o1, o2) -> {
      final int support = Comparing.compare(o1.getSupportId(), o2.getSupportId());
      if (support != 0) return support;
      final int lang = Comparing.compare(o1.getInjectedLanguageId(), o2.getInjectedLanguageId());
      if (lang != 0) return lang;
      return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
    });
  }

  public JComponent createComponent() {
    return myRoot;
  }

  public void reset() {
    for (CfgInfo info : myInfos) {
      info.reset();
    }
    myInjectionsTable.getListTableModel().setItems(getInjInfoList(myInfos));
    updateCountLabel();
  }

  public void apply() {
    for (CfgInfo info : myInfos) {
      info.apply();
    }
    reset();
  }

  public boolean isModified() {
    for (CfgInfo info : myInfos) {
      if (info.isModified()) return true;
    }
    return false;
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
    myInjectionsTable.getListTableModel().setItems(getInjInfoList(myInfos));
    final int index = Math.min(myInjectionsTable.getListTableModel().getRowCount() - 1, selectedRow);
    myInjectionsTable.getSelectionModel().setSelectionInterval(index, index);
    TableUtil.scrollSelectionToVisible(myInjectionsTable);
    updateCountLabel();
  }

  private List<InjInfo> getSelectedInjections() {
    final ArrayList<InjInfo> toRemove = new ArrayList<>();
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

    JBPopupFactory.getInstance().createActionGroupPopup(null, group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true,
                                                        () -> updateCountLabel(), -1).show(e.getPreferredPopupPoint());
  }

  @Nls
  public String getDisplayName() {
    return "Language Injections";
  }

  public String getHelpTopic() {
    return "reference.settings.injection.language.injection.settings";
  }

  private class InjectionsTable extends TableView<InjInfo> {
    private InjectionsTable(final List<InjInfo> injections) {
      super(new ListTableModel<>(createInjectionColumnInfos(), injections, 1));
      setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
      getColumnModel().getColumn(2).setCellRenderer(createLanguageCellRenderer());
      getColumnModel().getColumn(1).setCellRenderer(createDisplayNameCellRenderer());
      getColumnModel().getColumn(0).setResizable(false);
      setShowGrid(false);
      setShowVerticalLines(false);
      setGridColor(getForeground());
      TableUtil.setupCheckboxColumn(getColumnModel().getColumn(0));

      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
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
      new TableViewSpeedSearch<InjInfo>(this) {
        @Override
        protected String getItemText(@NotNull InjInfo element) {
          final BaseInjection injection = element.injection;
          return injection.getSupportId() + " " + injection.getInjectedLanguageId() + " " + injection.getDisplayName();
        }
      };
    }

  }

  private ColumnInfo[] createInjectionColumnInfos() {
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
    final ColumnInfo[] columnInfos = {new ColumnInfo<InjInfo, Boolean>(" ") {
      @Override
      public Class getColumnClass() {
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
    }, new ColumnInfo<InjInfo, InjInfo>("Name") {
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
    }, new ColumnInfo<InjInfo, InjInfo>("Language") {
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
      return ArrayUtil.append(columnInfos, new ColumnInfo<InjInfo, String>("Scope") {
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
        return setLabelColors(super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column), table, isSelected, row);
      }
    };
  }

  private static TableCellRenderer createLanguageCellRenderer() {
    return new TableCellRenderer() {
      final JLabel myLabel = new JLabel();

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
        setLabelColors(myLabel, table, isSelected, row);
        return myLabel;
      }
    };
  }

  private TableCellRenderer createDisplayNameCellRenderer() {
    return new TableCellRenderer() {
      final SimpleColoredComponent myLabel = new SimpleColoredComponent();
      final SimpleColoredText myText = new SimpleColoredText();

      public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        myLabel.clear();
        final InjInfo info = (InjInfo)value;
        // fix for a marvellous Swing peculiarity: AccessibleJTable likes to pass null here
        if (info == null) return myLabel;
        final SimpleTextAttributes grayAttrs = isSelected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
        final String supportId = info.injection.getSupportId();
        myText.append(supportId + ": ", grayAttrs);
        mySupports.get(supportId).setupPresentation(info.injection, myText, isSelected);
        myText.appendToComponent(myLabel);
        myText.clear();
        setLabelColors(myLabel, table, isSelected, row);
        return myLabel;
      }
    };
  }

  private static TableCellRenderer createTypeRenderer() {
    return new TableCellRenderer() {
      final SimpleColoredComponent myLabel = new SimpleColoredComponent();

      public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        myLabel.clear();
        final String info = (String)value;
        if (info == null) return myLabel;
        final SimpleTextAttributes grayAttrs = isSelected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
        myLabel.append(info, grayAttrs);
        setLabelColors(myLabel, table, isSelected, row);
        return myLabel;
      }
    };
  }

  private static Component setLabelColors(final Component label, final JTable table, final boolean isSelected, final int row) {
    if (label instanceof JComponent) {
      ((JComponent)label).setOpaque(true);
    }
    label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
    label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    return label;
  }

  private void doImportAction(final DataContext dataContext) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, false, true, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) &&
               (file.isDirectory() || "xml".equals(file.getExtension()) || file.getFileType() == FileTypes.ARCHIVE);
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return file.getFileType() == StdFileTypes.XML;
      }
    };
    descriptor.setDescription("Please select the configuration file (usually named IntelliLang.xml) to import.");
    descriptor.setTitle("Import Configuration");

    descriptor.putUserData(LangDataKeys.MODULE_CONTEXT, LangDataKeys.MODULE.getData(dataContext));

    final SplitterProportionsData splitterData = new SplitterProportionsDataImpl();
    splitterData.externalizeFromDimensionService("IntelliLang.ImportSettingsKey.SplitterProportions");

    final VirtualFile file = FileChooser.chooseFile(descriptor, myProject, null);
    if (file == null) return;
    try {
      final Configuration cfg = Configuration.load(file.getInputStream());
      if (cfg == null) {
        Messages.showWarningDialog(myProject, "The selected file does not contain any importable configuration.", "Nothing to Import");
        return;
      }
      final CfgInfo info = getDefaultCfgInfo();
      final Map<String,Set<InjInfo>> currentMap =
        ContainerUtil.classify(info.injectionInfos.iterator(), new Convertor<InjInfo, String>() {
          public String convert(final InjInfo o) {
            return o.injection.getSupportId();
          }
        });
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
        ArrayList<InjInfo> list = new ArrayList<>(ObjectUtils.notNull(currentMap.get(supportId), Collections.<InjInfo>emptyList()));
        final List<BaseInjection> currentInjections = getInjectionList(list);
        final List<BaseInjection> importingInjections = cfg.getInjections(supportId);
        if (currentInjections == null) {
          newInjections.addAll(importingInjections);
        }
        else {
          Configuration.importInjections(currentInjections, importingInjections, originalInjections, newInjections);
        }
      }
      info.replace(originalInjections, newInjections);
      myInjectionsTable.getListTableModel().setItems(getInjInfoList(myInfos));
      final int n = newInjections.size();
      if (n > 1) {
        Messages.showInfoMessage(myProject, n + " entries have been successfully imported", "Import Successful");
      }
      else if (n == 1) {
        Messages.showInfoMessage(myProject, "One entry has been successfully imported", "Import Successful");
      }
      else {
        Messages.showInfoMessage(myProject, "No new entries have been imported", "Import");
      }
    }
    catch (Exception ex) {
      Configuration.LOG.error(ex);

      final String msg = ex.getLocalizedMessage();
      Messages.showErrorDialog(myProject, msg != null && msg.length() > 0 ? msg : ex.toString(), "Import Failed");
    }
  }

  private static class CfgInfo {
    final Configuration cfg;
    final List<BaseInjection> originalInjections;
    final List<InjInfo> injectionInfos = new ArrayList<>();
    final THashSet<BaseInjection> bundledInjections = new THashSet<>(new SameParamsAndPlacesStrategy());
    final String title;

    public CfgInfo(Configuration cfg, final String title) {
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

    public void replace(final List<BaseInjection> originalInjections, final List<BaseInjection> newInjections) {
      for (Iterator<InjInfo> it = injectionInfos.iterator(); it.hasNext(); ) {
        final InjInfo info = it.next();
        if (originalInjections.contains(info.injection)) it.remove();
      }
      for (BaseInjection newInjection : newInjections) {
        injectionInfos.add(new InjInfo(newInjection, this, false));
      }
    }

  }

  private static class SameParamsAndPlacesStrategy implements TObjectHashingStrategy<BaseInjection> {
    @Override
    public int computeHashCode(final BaseInjection object) {
      return object.hashCode();
    }

    @Override
    public boolean equals(final BaseInjection o1, final BaseInjection o2) {
      return o1.sameLanguageParameters(o2) && Arrays.equals(o1.getInjectionPlaces(), o2.getInjectionPlaces());
    }
  }

  private static class InjInfo {
    final BaseInjection injection;
    final CfgInfo cfgInfo;
    final boolean bundled;

    private InjInfo(BaseInjection injection, CfgInfo cfgInfo, boolean bundled) {
      this.injection = injection;
      this.cfgInfo = cfgInfo;
      this.bundled = bundled;
    }
  }

  private static List<InjInfo> getInjInfoList(final CfgInfo[] infos) {
    return ContainerUtil.concat(infos, new Function<CfgInfo, Collection<? extends InjInfo>>() {
      @Override
      public Collection<InjInfo> fun(final CfgInfo cfgInfo) {
        return cfgInfo.injectionInfos;
      }
    });
  }

  private static List<BaseInjection> getInjectionList(final List<InjInfo> list) {
    return new AbstractList<BaseInjection>() {
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
}
