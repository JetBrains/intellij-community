/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.intellij.plugins.intelliLang;

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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.ui.tabs.BetterJTable;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Gregory.Shrago
 */
public class InjectionsSettingsUI implements Configurable {

  private final Project myProject;
  private final Configuration myConfiguration;
  private List<BaseInjection> myInjections;
  private List<BaseInjection> myOriginalInjections;


  private final JPanel myRoot;
  private final InjectionsTable myInjectionsTable;

  public InjectionsSettingsUI(final Project project, final Configuration configuration) {
    myProject = project;
    myConfiguration = configuration;

    myOriginalInjections = ContainerUtil
      .concat(InjectorUtils.getActiveInjectionSupportIds(), new Function<String, Collection<? extends BaseInjection>>() {
        public Collection<? extends BaseInjection> fun(final String s) {
          return myConfiguration.getInjections(s);
        }
      });
    Collections.sort(myOriginalInjections, new Comparator<BaseInjection>() {
      public int compare(final BaseInjection o1, final BaseInjection o2) {
        final int support = Comparing.compare(o1.getSupportId(), o2.getSupportId());
        if (support != 0) return support;
        final int lang = Comparing.compare(o1.getInjectedLanguageId(), o2.getInjectedLanguageId());
        if (lang != 0) return lang;
        final int name = Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
        return name;
      }
    });
    myInjections = new ArrayList<BaseInjection>();
    for (BaseInjection injection : myOriginalInjections) {
      myInjections.add(injection.copy());
    }

    myRoot = new JPanel(new BorderLayout());

    myInjectionsTable = new InjectionsTable(myInjections);
    final JPanel tablePanel = new JPanel(new BorderLayout());
    //tablePanel.setBorder(BorderFactory.createTitledBorder("Available Injections"));
    tablePanel.add(BetterJTable.createStripedJScrollPane(myInjectionsTable), BorderLayout.CENTER);
    tablePanel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction("Add", "Add", Icons.ADD_ICON) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        performAdd(e);
      }
    });
    group.add(new AnAction("Remove", "Remove", Icons.DELETE_ICON) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        performRemove();
      }
    });
    group.add(new AnAction("Import", "Import", IconLoader.getIcon("/actions/install.png")) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        doImportAction(e.getDataContext());
      }
    });
    group.addSeparator();
    group.add(new AnAction("Enabled Selected Injections", "Enabled Selected Injections", Icons.SELECT_ALL_ICON) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        performSelectedInjectionsEnabled(true);
      }
    });
    group.add(new AnAction("Disabled Selected Injections", "Disabled Selected Injections", Icons.UNSELECT_ALL_ICON) {
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

    myRoot.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(), BorderLayout.NORTH);
    myRoot.add(tablePanel, BorderLayout.CENTER);
  }

  public JComponent createComponent() {
    return myRoot;
  }

  public void reset() {                                                 
    myInjections.clear();
    for (BaseInjection injection : myOriginalInjections) {
      myInjections.add(injection.copy());
    }
  }

  public void disposeUIResources() {
  }

  public void apply() {
    myConfiguration.replaceInjections(myInjections, myOriginalInjections);
  }

  public boolean isModified() {
    return !myOriginalInjections.equals(myInjections);
  }

  private void performSelectedInjectionsEnabled(final boolean enabled) {
    for (BaseInjection injection : getSelectedInjections()) {
      injection.setPlaceEnabled(null, enabled);
    }
    myInjectionsTable.updateUI();    
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
    myInjections.removeAll(getSelectedInjections());
    myInjectionsTable.updateUI();
    myInjectionsTable.getSelectionModel().setLeadSelectionIndex(Math.min(myInjections.size()-1, selectedRow));
  }

  private List<BaseInjection> getSelectedInjections() {
    final ArrayList<BaseInjection> toRemove = new ArrayList<BaseInjection>();
    for (int row : myInjectionsTable.getSelectedRows()) {
      toRemove.add((BaseInjection)myInjectionsTable.getItems().get(row));
    }
    return toRemove;
  }

  private void performAdd(final AnActionEvent button) {
    // todo add popup 
    Messages.showInfoMessage(myProject, "Unfortunately this functionality is not yet implemented.\nUse in place \'Inject language\' intention action.", "Add Injection");
  }

  @Nls
  public String getDisplayName() {
    return "Injections";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  private class InjectionsTable extends TableView {
    private InjectionsTable(final List<BaseInjection> injections) {
      super(new ListTableModel<BaseInjection>(createInjectionColumnInfos(), injections, -1));
      setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
      getColumnModel().getColumn(2).setCellRenderer(createLanguageCellRenderer());
      getColumnModel().getColumn(1).setCellRenderer(createDisplayNameCellRenderer());
      getColumnModel().getColumn(0).setResizable(false);
      setShowGrid(false);
      setShowVerticalLines(false);
      setGridColor(getForeground());
      setOpaque(false);
      getColumnModel().getColumn(0).setMaxWidth(new JCheckBox().getPreferredSize().width);
      final int[] preffered = new int[] {0} ;
      ContainerUtil.process(myInjections, new Processor<BaseInjection>() {
        public boolean process(final BaseInjection injection) {
          final String languageId = injection.getInjectedLanguageId();
          if (preffered[0] < languageId.length()) preffered[0] = languageId.length();
          return true;
        }
      });
      final int[] max = new int[] {0} ;
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

  private static ColumnInfo[] createInjectionColumnInfos() {
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
      final JLabel label = new JLabel();

      public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        final BaseInjection injection = (BaseInjection)value;
        final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
        final FileType fileType = language == null ? null : language.getAssociatedFileType();
        label.setIcon(fileType == null ? null : fileType.getIcon());
        label.setText(language == null ? injection.getInjectedLanguageId() : language.getDisplayName());
        setLabelColors(label, table, isSelected, row);
        return label;
      }
    };
  }

  private static TableCellRenderer createDisplayNameCellRenderer() {
    return new TableCellRenderer() {
      final SimpleColoredComponent myLabel = new SimpleColoredComponent();
      final Pattern myPattern = Pattern.compile("(.+)(\\(\\S+(?:\\.\\S+)+\\))");

      public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus,
                                                     final int row,
                                                     final int column) {
        myLabel.clear();

        final BaseInjection injection = (BaseInjection)value;
        myLabel.append(injection.getSupportId(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        myLabel.append(": ", SimpleTextAttributes.GRAY_ATTRIBUTES);

        final String text = injection.getDisplayName();
        final Matcher matcher = myPattern.matcher(text);
        if (matcher.matches()) {
          myLabel.append(matcher.group(1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          myLabel.append(matcher.group(2), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          myLabel.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        setLabelColors(myLabel, table, isSelected, row);
        return myLabel;
      }
    };
  }

  private static Component setLabelColors(final Component label, final JTable table, final boolean isSelected, final int row) {
    if (label instanceof JComponent) {
      ((JComponent)label).setOpaque(isSelected);
    }
    label.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
    label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    //if (row % 2 != 0 && !isSelected) {
    //  label.setBackground(darken(table.getBackground()));
    //}
    //else {
    //  label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    //}
    return label;
  }


  //private static final double FACTOR = 0.92;
  //public static Color darken(Color color) {
  //  return new Color(Math.max((int)(color.getRed() * FACTOR), 0), Math.max((int)(color.getGreen() * FACTOR), 0),
  //                   Math.max((int)(color.getBlue() * FACTOR), 0));
  //}


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

    final SplitterProportionsData splitterData = PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();
    splitterData.externalizeFromDimensionService("IntelliLang.ImportSettingsKey.SplitterProportions");

    final VirtualFile[] files = chooser.choose(null, myProject);
    if (files.length != 1) return;
    try {
      final Configuration cfg = Configuration.load(files[0].getInputStream());
      if (cfg == null) {
        Messages.showWarningDialog(myProject, "The selected file does not contain any importable configuration.", "Nothing to Import");
        return;
      }
      final List<BaseInjection> newInjections =
        ContainerUtil.concat(cfg.getAllInjectorIds(), new Function<String, Collection<? extends BaseInjection>>() {
          public Collection<? extends BaseInjection> fun(final String s) {
            return cfg.getInjections(s);
          }
        });
      newInjections.removeAll(myInjections);
      myInjections.addAll(newInjections);
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
