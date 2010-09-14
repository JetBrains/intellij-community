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

package org.intellij.plugins.intelliLang;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import gnu.trove.THashMap;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class InjectionsSettingsUI implements Configurable {

  private final Project myProject;
  private final Configuration myConfiguration;
  private final List<BaseInjection> myInjections;
  private final List<BaseInjection> myOriginalInjections;


  private final JPanel myRoot;
  private final InjectionsTable myInjectionsTable;
  private final Map<String, LanguageInjectionSupport> mySupports = new THashMap<String, LanguageInjectionSupport>();
  private final Map<String, AnAction> myEditActions = new THashMap<String, AnAction>();
  private final List<AnAction> myAddActions = new ArrayList<AnAction>();
  private final ActionToolbar myToolbar;
  private final JLabel myCountLabel;

  public InjectionsSettingsUI(final Project project, final Configuration configuration) {
    myProject = project;
    myConfiguration = configuration;

    myOriginalInjections = ContainerUtil
      .concat(InjectorUtils.getActiveInjectionSupportIds(), new Function<String, Collection<? extends BaseInjection>>() {
        public Collection<? extends BaseInjection> fun(final String s) {
          return ContainerUtil.findAll(myConfiguration.getInjections(s), new Condition<BaseInjection>() {
            public boolean value(final BaseInjection injection) {
              return InjectedLanguage.findLanguageById(injection.getInjectedLanguageId()) != null;
            }
          });
        }
      });
    sortInjections(myOriginalInjections);
    myInjections = new ArrayList<BaseInjection>();
    for (BaseInjection injection : myOriginalInjections) {
      myInjections.add(injection.copy());
    }

    myRoot = new JPanel(new BorderLayout());

    myInjectionsTable = new InjectionsTable(myInjections);
    final JPanel tablePanel = new JPanel(new BorderLayout());

    tablePanel.add(ScrollPaneFactory.createScrollPane(myInjectionsTable), BorderLayout.CENTER);

    final DefaultActionGroup group = createActions();

    myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    myToolbar.setTargetComponent(myInjectionsTable);
    myRoot.add(myToolbar.getComponent(), BorderLayout.NORTH);
    myRoot.add(tablePanel, BorderLayout.CENTER);
    myCountLabel = new JLabel();
    myCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    myCountLabel.setForeground(SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.getFgColor());
    myRoot.add(myCountLabel, BorderLayout.SOUTH);
    updateCountLabel();
  }

  private DefaultActionGroup createActions() {
    final Consumer<BaseInjection> consumer = new Consumer<BaseInjection>() {
      public void consume(final BaseInjection injection) {
        addInjection(injection);
      }
    };
    final Factory<BaseInjection> producer = new NullableFactory<BaseInjection>() {
      public BaseInjection create() {
        return getSelectedInjection();
      }
    };
    for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
      ContainerUtil.addAll(myAddActions, support.createAddActions(myProject, consumer));
      final AnAction action = support.createEditAction(myProject, producer);
      myEditActions
        .put(support.getId(), action == null ? AbstractLanguageInjectionSupport.createDefaultEditAction(myProject, producer) : action);
      mySupports.put(support.getId(), support);
    }
    Collections.sort(myAddActions, new Comparator<AnAction>() {
      public int compare(final AnAction o1, final AnAction o2) {
        return Comparing.compare(o1.getTemplatePresentation().getText(), o2.getTemplatePresentation().getText());
      }
    });

    final DefaultActionGroup group = new DefaultActionGroup();
    final AnAction addAction = new AnAction("Add", "Add", Icons.ADD_ICON) {
      @Override
      public void update(final AnActionEvent e) {
        e.getPresentation().setEnabled(!myAddActions.isEmpty());
      }

      @Override
      public void actionPerformed(final AnActionEvent e) {
        performAdd(e);
      }
    };
    final AnAction removeAction = new AnAction("Remove", "Remove", Icons.DELETE_ICON) {
      @Override
      public void update(final AnActionEvent e) {
        e.getPresentation().setEnabled(!getSelectedInjections().isEmpty());
      }

      @Override
      public void actionPerformed(final AnActionEvent e) {
        performRemove();
      }
    };

    final AnAction editAction = new AnAction("Edit", "Edit", Icons.PROPERTIES_ICON) {
      @Override
      public void update(final AnActionEvent e) {
        final AnAction action = getEditAction();
        e.getPresentation().setEnabled(action != null);
        if (action != null) action.update(e);
      }

      @Override
      public void actionPerformed(final AnActionEvent e) {
        performEditAction(e);
      }
    };
    final AnAction copyAction = new AnAction("Duplicate", "Duplicate", Icons.DUPLICATE_ICON) {
      @Override
      public void update(final AnActionEvent e) {
        final AnAction action = getEditAction();
        e.getPresentation().setEnabled(action != null);
        if (action != null) action.update(e);
      }

      @Override
      public void actionPerformed(final AnActionEvent e) {
        final BaseInjection injection = getSelectedInjection();
        if (injection != null) {
          addInjection(injection.copy());
          //performEditAction(e);
        }
      }
    };
    group.add(addAction);
    group.add(removeAction);
    group.add(copyAction);
    group.add(editAction);

    addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myInjectionsTable);
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myInjectionsTable);
    editAction.registerCustomShortcutSet(CommonShortcuts.ENTER, myInjectionsTable);

    group.add(new AnAction("Import", "Import", IconLoader.getIcon("/actions/install.png")) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        doImportAction(e.getDataContext());
        updateCountLabel();
      }
    });
    group.addSeparator();
    group.add(new AnAction("Enable Selected Injections", "Enable Selected Injections", Icons.SELECT_ALL_ICON) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        performSelectedInjectionsEnabled(true);
      }
    });
    group.add(new AnAction("Disable Selected Injections", "Disable Selected Injections", Icons.UNSELECT_ALL_ICON) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        performSelectedInjectionsEnabled(false);
      }
    });

    new AnAction("Toggle") {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        performToggleAction();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myInjectionsTable);
    return group;
  }

  private void performEditAction(AnActionEvent e) {
    final AnAction action = getEditAction();
    if (action != null) {
      final int row = myInjectionsTable.getSelectedRow();
      action.actionPerformed(e);
      ((ListTableModel)myInjectionsTable.getModel()).fireTableDataChanged();
      myInjectionsTable.getSelectionModel().setSelectionInterval(row, row);
      updateCountLabel();
    }
  }

  private void updateCountLabel() {
    int placesCount = 0;
    int enablePlacesCount = 0;
    for (BaseInjection injection : myInjections) {
      for (InjectionPlace place : injection.getInjectionPlaces()) {
        placesCount++;
        if (place.isEnabled()) enablePlacesCount++;
      }
    }
    if (!myInjections.isEmpty()) {
      final StringBuilder sb = new StringBuilder();
      sb.append(myInjections.size()).append(" injection").append(myInjections.size() > 1 ? "s" : "").append(" (").append(enablePlacesCount)
        .append(" of ").append(placesCount).append(" place").append(placesCount > 1 ? "s" : "").append(" enabled) ");
      myCountLabel.setText(sb.toString());
    }
    else {
      myCountLabel.setText("no injections configured ");
    }
  }

  @Nullable
  private AnAction getEditAction() {
    final BaseInjection injection = getSelectedInjection();
    final String supportId = injection == null? null : injection.getSupportId();
    return supportId == null? null : myEditActions.get(supportId);
  }

  private void addInjection(final BaseInjection injection) {
    injection.initializePlaces(true);
    myInjections.add(injection);
    myInjectionsTable.getListTableModel().setItems(myInjections);
    final int index = myInjections.indexOf(injection);
    myInjectionsTable.getSelectionModel().setSelectionInterval(index, index);
    TableUtil.scrollSelectionToVisible(myInjectionsTable);
  }

  private static void sortInjections(final List<BaseInjection> injections) {
    Collections.sort(injections, new Comparator<BaseInjection>() {
      public int compare(final BaseInjection o1, final BaseInjection o2) {
        final int support = Comparing.compare(o1.getSupportId(), o2.getSupportId());
        if (support != 0) return support;
        final int lang = Comparing.compare(o1.getInjectedLanguageId(), o2.getInjectedLanguageId());
        if (lang != 0) return lang;
        return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
      }
    });
  }

  public JComponent createComponent() {
    return myRoot;
  }

  public void reset() {                                                 
    myInjections.clear();
    for (BaseInjection injection : myOriginalInjections) {
      myInjections.add(injection.copy());
    }
    myInjectionsTable.getListTableModel().setItems(myInjections);
    updateCountLabel();
  }

  public void disposeUIResources() {
  }

  public void apply() {
    myConfiguration.replaceInjections(myInjections, myOriginalInjections);
    myOriginalInjections.clear();
    myOriginalInjections.addAll(myInjections);
    sortInjections(myOriginalInjections);    
    reset();
  }

  public boolean isModified() {
    final List<BaseInjection> copy = new ArrayList<BaseInjection>(myInjections);
    sortInjections(copy);
    return !myOriginalInjections.equals(copy);
  }

  private void performSelectedInjectionsEnabled(final boolean enabled) {
    for (BaseInjection injection : getSelectedInjections()) {
      injection.setPlaceEnabled(null, enabled);
    }
    myInjectionsTable.updateUI();
    updateCountLabel();
  }

  private void performToggleAction() {
    final List<BaseInjection> selectedInjections = getSelectedInjections();
    boolean enabledExists = false;
    boolean disabledExists = false;
    for (BaseInjection injection : selectedInjections) {
      if (injection.isEnabled()) enabledExists = true;
      else disabledExists = true;
      if (enabledExists && disabledExists) break;
    }
    boolean allEnabled = !enabledExists && disabledExists;
    performSelectedInjectionsEnabled(allEnabled);
  }

  private void performRemove() {
    final int selectedRow = myInjectionsTable.getSelectedRow();
    if (selectedRow < 0) return;
    final List<BaseInjection> selected = getSelectedInjections();
    main: for (Iterator<BaseInjection> it = myInjections.iterator(); it.hasNext(); ) {
      final BaseInjection injection = it.next();
      for (BaseInjection selectedInjection : selected) {
        if (injection == selectedInjection) {
          it.remove();
          continue main;
        }
      }
    }
    ((ListTableModel)myInjectionsTable.getModel()).fireTableDataChanged();
    final int index = Math.min(myInjections.size() - 1, selectedRow);
    myInjectionsTable.getSelectionModel().setSelectionInterval(index, index);
    TableUtil.scrollSelectionToVisible(myInjectionsTable);
    updateCountLabel();
  }

  private List<BaseInjection> getSelectedInjections() {
    final ArrayList<BaseInjection> toRemove = new ArrayList<BaseInjection>();
    for (int row : myInjectionsTable.getSelectedRows()) {
      toRemove.add(myInjectionsTable.getItems().get(myInjectionsTable.convertRowIndexToModel(row)));
    }
    return toRemove;
  }

  @Nullable
  private BaseInjection getSelectedInjection() {
    final int row = myInjectionsTable.getSelectedRow();
    return row < 0? null : myInjectionsTable.getItems().get(myInjectionsTable.convertRowIndexToModel(row));
  }

  private void performAdd(final AnActionEvent e) {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (AnAction action : myAddActions) {
      group.add(action);
    }

    JBPopupFactory.getInstance().createActionGroupPopup(null, group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true, new Runnable() {
      public void run() {
        updateCountLabel();
      }
    }, -1).showUnderneathOf(myToolbar.getComponent());
  }

  @Nls
  public String getDisplayName() {
    return "Injections";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settings.injection.language.injection.settings";
  }

  private class InjectionsTable extends TableView<BaseInjection> {
    private InjectionsTable(final List<BaseInjection> injections) {
      super(new ListTableModel<BaseInjection>(createInjectionColumnInfos(), injections, 1));
      setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
      getColumnModel().getColumn(2).setCellRenderer(createLanguageCellRenderer());
      getColumnModel().getColumn(1).setCellRenderer(createDisplayNameCellRenderer());
      getColumnModel().getColumn(0).setResizable(false);
      setShowGrid(false);
      setShowVerticalLines(false);
      setGridColor(getForeground());
      getColumnModel().getColumn(0).setMaxWidth(new JCheckBox().getPreferredSize().width);
      final int[] preffered = new int[] {0} ;
      ContainerUtil.process(myInjections, new Processor<BaseInjection>() {
        public boolean process(final BaseInjection injection) {
          final String languageId = injection.getInjectedLanguageId();
          if (preffered[0] < languageId.length()) preffered[0] = languageId.length();
          return true;
        }
      });
      final int[] max = new int[] {0};
      addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() != 2) return;
          final int row = rowAtPoint(e.getPoint());
          if (row < 0) return;
          if (columnAtPoint(e.getPoint()) <= 0) return;
          myInjectionsTable.getSelectionModel().setSelectionInterval(row, row);
          performEditAction(new AnActionEvent(e, DataManager.getInstance().getDataContext(InjectionsTable.this),
                                              ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0));
        }
      });
      ContainerUtil.process(InjectedLanguage.getAvailableLanguageIDs(), new Processor<String>() {
        public boolean process(final String languageId) {
          if (max[0] < languageId.length()) max[0] = languageId.length();
          return true;
        }
      });
      getColumnModel().getColumn(2).setResizable(false);
      final Icon icon = StdFileTypes.PLAIN_TEXT.getIcon();
      final int preferred = new JLabel(StringUtil.repeatSymbol('m', preffered[0]), icon, SwingConstants.LEFT).getPreferredSize().width;
      getColumnModel().getColumn(2).setMinWidth(preferred);
      getColumnModel().getColumn(2).setPreferredWidth(preferred);
      getColumnModel().getColumn(2).setMaxWidth(new JLabel(StringUtil.repeatSymbol('m', max[0]), icon, SwingConstants.LEFT).getPreferredSize().width);
      new TableViewSpeedSearch(this) {

        @Override
        protected String getElementText(final Object element) {
          final BaseInjection injection = (BaseInjection)element;
          return injection.getSupportId() + " " + injection.getInjectedLanguageId() + " " + injection.getDisplayName();
        }
      };
    }

  }

  private ColumnInfo[] createInjectionColumnInfos() {
    final TableCellRenderer booleanCellRenderer = createBooleanCellRenderer();
    final TableCellRenderer displayNameCellRenderer = createDisplayNameCellRenderer();
    final TableCellRenderer languageCellRenderer = createLanguageCellRenderer();
    final Comparator<BaseInjection> languageComparator = new Comparator<BaseInjection>() {
      public int compare(final BaseInjection o1, final BaseInjection o2) {
        return Comparing.compare(o1.getInjectedLanguageId(), o2.getInjectedLanguageId());
      }
    };
    final Comparator<BaseInjection> displayNameComparator = new Comparator<BaseInjection>() {
      public int compare(final BaseInjection o1, final BaseInjection o2) {
        final int support = Comparing.compare(o1.getSupportId(), o2.getSupportId());
        if (support != 0) return support;
        return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
      }
    };
    return new ColumnInfo[]{new ColumnInfo<BaseInjection, Boolean>(" ") {
      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public Boolean valueOf(final BaseInjection o) {
        return o.isEnabled();
      }

      @Override
      public boolean isCellEditable(final BaseInjection injection) {
        return true;
      }

      @Override
      public void setValue(final BaseInjection injection, final Boolean value) {
        injection.setPlaceEnabled(null, value.booleanValue());
      }

      @Override
      public TableCellRenderer getRenderer(final BaseInjection injection) {
        return booleanCellRenderer;
      }
    }, new ColumnInfo<BaseInjection, BaseInjection>("Display Name") {
      @Override
      public BaseInjection valueOf(final BaseInjection injection) {
        return injection;
      }

      @Override
      public Comparator<BaseInjection> getComparator() {
        return displayNameComparator;
      }

      @Override
      public TableCellRenderer getRenderer(final BaseInjection injection) {
        return displayNameCellRenderer;
      }
    }, new ColumnInfo<BaseInjection, BaseInjection>("Language") {
      @Override
      public BaseInjection valueOf(final BaseInjection injection) {
        return injection;
      }

      @Override
      public Comparator<BaseInjection> getComparator() {
        return languageComparator;
      }

      @Override
      public TableCellRenderer getRenderer(final BaseInjection injection) {
        return languageCellRenderer;
      }
    }};
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
        final BaseInjection injection = (BaseInjection)value;
        // fix for a marvellous Swing peculiarity: AccessibleJTable likes to pass null here
        if (injection == null) return myLabel;
        final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
        final FileType fileType = language == null ? null : language.getAssociatedFileType();
        myLabel.setIcon(fileType == null ? null : fileType.getIcon());
        myLabel.setText(language == null ? injection.getInjectedLanguageId() : language.getDisplayName());
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
        final BaseInjection injection = (BaseInjection)value;
        // fix for a marvellous Swing peculiarity: AccessibleJTable likes to pass null here
        if (injection == null) return myLabel;
        final SimpleTextAttributes grayAttrs = isSelected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
        myText.append(injection.getSupportId() + ": ", grayAttrs);
        mySupports.get(injection.getSupportId()).setupPresentation(injection, myText, isSelected);
        myText.appendToComponent(myLabel);
        myText.clear();
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
               (file.isDirectory() || "xml".equals(file.getExtension()) || file.getFileType() == StdFileTypes.ARCHIVE);
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return file.getFileType() == StdFileTypes.XML;
      }
    };
    descriptor.setDescription("Please select the configuration file (usually named IntelliLang.xml) to import.");
    descriptor.setTitle("Import Configuration");

    descriptor.putUserData(LangDataKeys.MODULE_CONTEXT, LangDataKeys.MODULE.getData(dataContext));

    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, myProject);

    final SplitterProportionsData splitterData = new SplitterProportionsDataImpl();
    splitterData.externalizeFromDimensionService("IntelliLang.ImportSettingsKey.SplitterProportions");

    final VirtualFile[] files = chooser.choose(null, myProject);
    if (files.length != 1) return;
    try {
      final Configuration cfg = Configuration.load(files[0].getInputStream());
      if (cfg == null) {
        Messages.showWarningDialog(myProject, "The selected file does not contain any importable configuration.", "Nothing to Import");
        return;
      }
      final Map<String,Set<BaseInjection>> currentMap =
        ContainerUtil.classify(myInjections.iterator(), new Convertor<BaseInjection, String>() {
          public String convert(final BaseInjection o) {
            return o.getSupportId();
          }
        });
      final List<BaseInjection> originalInjections = new ArrayList<BaseInjection>();
      final List<BaseInjection> newInjections = new ArrayList<BaseInjection>();
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
        final Set<BaseInjection> currentInjections = currentMap.get(supportId);
        final List<BaseInjection> importingInjections = cfg.getInjections(supportId);
        if (currentInjections == null) {
          newInjections.addAll(importingInjections);
        }
        else {
          Configuration.importInjections(currentInjections, importingInjections, originalInjections, newInjections);
        }
      }
      myInjections.removeAll(originalInjections);
      myInjections.addAll(newInjections);
      for (BaseInjection injection : newInjections) {
        injection.initializePlaces(true);
      }
      myInjectionsTable.getListTableModel().setItems(myInjections);
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
    catch (Exception e1) {
      Configuration.LOG.error("Unable to load Settings", e1);

      final String msg = e1.getLocalizedMessage();
      Messages.showErrorDialog(myProject, msg != null && msg.length() > 0 ? msg : e1.toString(), "Could not load Settings");
    }
  }
}
