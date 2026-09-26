// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.AntReference;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.lang.ant.config.impl.TargetFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellEditor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.ResourceBundle;

public final class BuildFilePropertiesPanel {
  private static final @NonNls String DIMENSION_SERVICE_KEY = "antBuildFilePropertiesDialogDimension";

  private final Form myForm;
  private AntBuildFileBase myBuildFile;

  private BuildFilePropertiesPanel() {
    myForm = new Form();
  }

  private void reset(final AntBuildFileBase buildFile) {
    myBuildFile = buildFile;
    buildFile.updateProperties();
    myForm.reset(myBuildFile);
  }

  private void apply() {
    myForm.apply(myBuildFile);
    myBuildFile.updateConfig();
  }

  private boolean showDialog() {
    DialogBuilder builder = new DialogBuilder(myBuildFile.getProject());
    builder.setCenterPanel(myForm.myWholePanel);
    builder.setDimensionServiceKey(DIMENSION_SERVICE_KEY);
    builder.setPreferredFocusComponent(myForm.getPreferedFocusComponent());
    builder.setTitle(AntBundle.message("build.file.properties.dialog.title"));
    builder.removeAllActions();
    builder.addOkAction();
    builder.addCancelAction();
    builder.setHelpId("reference.dialogs.buildfileproperties");

    boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
    if (isOk) {
      apply();
    }
    beforeClose();
    return isOk;
  }

  private void beforeClose() {
    myForm.beforeClose(myBuildFile);
    Disposer.dispose(myForm);
  }

  public static boolean editBuildFile(AntBuildFileBase buildFile) {
    BuildFilePropertiesPanel panel = new BuildFilePropertiesPanel();
    panel.reset(buildFile);
    return panel.showDialog();
  }

  abstract static class Tab {
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();

    public abstract JComponent getComponent();

    public abstract @NlsContexts.TabTitle String getDisplayName();

    public UIPropertyBinding.Composite getBinding() {
      return myBinding;
    }

    public void reset(AbstractProperty.AbstractPropertyContainer options) {
      myBinding.loadValues(options);
    }

    public void apply(AbstractProperty.AbstractPropertyContainer options) {
      myBinding.apply(options);
    }

    public void beforeClose(AbstractProperty.AbstractPropertyContainer options) {
      myBinding.beforeClose(options);
    }

    public abstract JComponent getPreferedFocusComponent();
  }

  private static final class Form implements Disposable {
    private final JLabel myBuildFileName;
    private final JTextField myXmx;
    private final JTextField myXss;
    private final JCheckBox myRunInBackground;
    private final JCheckBox myCloseOnNoError;
    private final JPanel myTabsPlace;
    private final JPanel myWholePanel;
    private final JLabel myHeapSizeLabel;
    private final JCheckBox myColoredOutputMessages;
    private final JCheckBox myCollapseFinishedTargets;
    private final Tab[] myTabs;
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();

    private Form() {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(5, 4, new Insets(0, 0, 0, 0), -1, -1));
        myBuildFileName = new JLabel();
        myBuildFileName.setText("<######## ## ####> ");
        myWholePanel.add(myBuildFileName, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
        myRunInBackground = new JCheckBox();
        this.$$$loadButtonText$$$(myRunInBackground,
                                  this.$$$getMessageFromBundle$$$("messages/AntBundle",
                                                                  "build.file.properties.make.in.background.cjeclbox"));
        myWholePanel.add(myRunInBackground, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myTabsPlace = new JPanel();
        myWholePanel.add(myTabsPlace, new GridConstraints(4, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
        myHeapSizeLabel = new JLabel();
        this.$$$loadLabelText$$$(myHeapSizeLabel,
                                 this.$$$getMessageFromBundle$$$("messages/AntBundle", "build.file.properties.maximum.heap.size.label"));
        myWholePanel.add(myHeapSizeLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null,
                                                              null, 0, false));
        myCloseOnNoError = new JCheckBox();
        this.$$$loadButtonText$$$(myCloseOnNoError,
                                  this.$$$getMessageFromBundle$$$("messages/AntBundle",
                                                                  "build.file.properties.close.message.view.checkbox"));
        myCloseOnNoError.setToolTipText("");
        myWholePanel.add(myCloseOnNoError, new GridConstraints(2, 2, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myXmx = new JTextField();
        myXmx.setColumns(4);
        myWholePanel.add(myXmx, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                    false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1,
                                 this.$$$getMessageFromBundle$$$("messages/AntBundle", "build.file.properties.maximum.stack.size.label"));
        myWholePanel.add(label1, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0,
                                                     false));
        myXss = new JTextField();
        myXss.setColumns(4);
        myWholePanel.add(myXss, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                    null,
                                                    0, false));
        myColoredOutputMessages = new JCheckBox();
        this.$$$loadButtonText$$$(myColoredOutputMessages,
                                  this.$$$getMessageFromBundle$$$("messages/AntBundle", "checkbox.colored.output.messages"));
        myWholePanel.add(myColoredOutputMessages, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                      null, null, null, 0, false));
        myCollapseFinishedTargets = new JCheckBox();
        this.$$$loadButtonText$$$(myCollapseFinishedTargets, this.$$$getMessageFromBundle$$$("messages/AntBundle",
                                                                                             "checkbox.collapse.finished.targets.in.message.view"));
        myWholePanel.add(myCollapseFinishedTargets, new GridConstraints(3, 2, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                        GridConstraints.SIZEPOLICY_FIXED,
                                                                        null, null, null, 0, false));
      }
      myTabs = new Tab[]{
        new PropertiesTab(),
        new ExecutionTab(GlobalAntConfiguration.getInstance()),
        new AdditionalClasspathTab(),
        new FiltersTab()
      };

      myHeapSizeLabel.setLabelFor(myXmx);
      TabbedPaneWrapper wrapper = new TabbedPaneWrapper(this);
      myTabsPlace.setLayout(new BorderLayout());
      myTabsPlace.add(wrapper.getComponent(), BorderLayout.CENTER);

      myBinding.bindBoolean(myRunInBackground, AntBuildFileImpl.RUN_IN_BACKGROUND);
      myBinding.bindBoolean(myCloseOnNoError, AntBuildFileImpl.CLOSE_ON_NO_ERRORS);
      myBinding.bindBoolean(myColoredOutputMessages, AntBuildFileImpl.TREE_VIEW_ANSI_COLOR);
      myBinding.bindBoolean(myCollapseFinishedTargets, AntBuildFileImpl.TREE_VIEW_COLLAPSE_TARGETS);
      myBinding.bindInt(myXmx, AntBuildFileImpl.MAX_HEAP_SIZE);
      myBinding.bindInt(myXss, AntBuildFileImpl.MAX_STACK_SIZE);

      for (Tab tab : myTabs) {
        wrapper.addTab(tab.getDisplayName(), tab.getComponent());
      }
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    /** @noinspection ALL */
    private String $$$getMessageFromBundle$$$(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if ($$$cachedGetBundleMethod$$$ == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /** @noinspection ALL */
    private void $$$loadLabelText$$$(JLabel component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setDisplayedMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myWholePanel; }

    public JComponent getComponent() {
      return myWholePanel;
    }

    public JComponent getPreferedFocusComponent() {
      return myTabs[0].getPreferedFocusComponent();
    }

    public void reset(final AntBuildFileBase buildFile) {
      myBinding.loadValues(buildFile.getAllOptions());
      myBuildFileName.setText(buildFile.getPresentableUrl());
      for (Tab tab : myTabs) {
        tab.reset(buildFile.getAllOptions());
      }
    }

    public void apply(AntBuildFileBase buildFile) {
      myBinding.apply(buildFile.getAllOptions());
      for (Tab tab : myTabs) {
        tab.apply(buildFile.getAllOptions());
      }
    }

    public void beforeClose(AntBuildFileBase buildFile) {
      myBinding.beforeClose(buildFile.getAllOptions());
      for (Tab tab : myTabs) {
        tab.beforeClose(buildFile.getAllOptions());
      }
    }

    @Override
    public void dispose() {
    }
  }

  private static class PropertiesTab extends Tab {
    private final JTable myPropertiesTable;
    private final JPanel myWholePanel;

    private static final ColumnInfo<BuildFileProperty, String> NAME_COLUMN = new ColumnInfo<>(
      AntBundle.message("edit.ant.properties.name.column.name")) {
      @Override
      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyName();
      }

      @Override
      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return true;
      }

      @Override
      public void setValue(BuildFileProperty buildFileProperty, String name) {
        buildFileProperty.setPropertyName(name);
      }
    };
    private static final ColumnInfo<BuildFileProperty, String> VALUE_COLUMN = new ColumnInfo<>(
      AntBundle.message("edit.ant.properties.value.column.name")) {
      @Override
      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return true;
      }

      @Override
      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyValue();
      }

      @Override
      public void setValue(BuildFileProperty buildFileProperty, String value) {
        buildFileProperty.setPropertyValue(value);
      }

      @Override
      public TableCellEditor getEditor(BuildFileProperty item) {
        return new AntUIUtil.PropertyValueCellEditor();
      }
    };
    private static final ColumnInfo[] PROPERTY_COLUMNS = new ColumnInfo[]{NAME_COLUMN, VALUE_COLUMN};

    PropertiesTab() {
      myPropertiesTable = new JBTable();
      UIPropertyBinding.TableListBinding<BuildFileProperty> tableListBinding = getBinding().bindList(myPropertiesTable, PROPERTY_COLUMNS,
                                                                                                     AntBuildFileImpl.ANT_PROPERTIES);
      tableListBinding.setColumnWidths(GlobalAntConfiguration.PROPERTIES_TABLE_LAYOUT);

      myWholePanel = ToolbarDecorator.createDecorator(myPropertiesTable)
        .setAddAction(new AnActionButtonRunnable() {


          @Override
          public void run(AnActionButton button) {
            if (myPropertiesTable.isEditing() && !myPropertiesTable.getCellEditor().stopCellEditing()) {
              return;
            }
            BuildFileProperty item = new BuildFileProperty();
            ListTableModel<BuildFileProperty> model = (ListTableModel<BuildFileProperty>)myPropertiesTable.getModel();
            ArrayList<BuildFileProperty> items = new ArrayList<>(model.getItems());
            items.add(item);
            model.setItems(items);
            int newIndex = model.indexOf(item);
            ListSelectionModel selectionModel = myPropertiesTable.getSelectionModel();
            selectionModel.clearSelection();
            selectionModel.setSelectionInterval(newIndex, newIndex);
            ColumnInfo[] columns = model.getColumnInfos();
            for (int i = 0; i < columns.length; i++) {
              ColumnInfo column = columns[i];
              if (column.isCellEditable(item)) {
                myPropertiesTable.requestFocusInWindow();
                myPropertiesTable.editCellAt(newIndex, i);
                break;
              }
            }
          }
        }).disableUpDownActions().createPanel();
      myWholePanel.setBorder(null);
    }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    public @Nullable String getDisplayName() {
      return AntBundle.message("edit.ant.properties.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myPropertiesTable;
    }
  }

  private static class FiltersTab extends Tab {
    private final JTable myFiltersTable;
    private final JPanel myWholePanel;

    private static final int PREFERRED_CHECKBOX_COLUMN_WIDTH = new JCheckBox().getPreferredSize().width + 4;
    private static final ColumnInfo<TargetFilter, Boolean> CHECK_BOX_COLUMN = new ColumnInfo<>("") {
      @Override
      public Boolean valueOf(TargetFilter targetFilter) {
        return targetFilter.isVisible();
      }

      @Override
      public void setValue(TargetFilter targetFilter, Boolean aBoolean) {
        targetFilter.setVisible(aBoolean.booleanValue());
      }

      @Override
      public int getWidth(JTable table) {
        return PREFERRED_CHECKBOX_COLUMN_WIDTH;
      }

      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public boolean isCellEditable(TargetFilter targetFilter) {
        return true;
      }
    };

    private static final Comparator<TargetFilter> NAME_COMPARATOR = (o1, o2) -> {
      final String name1 = o1.getTargetName();
      if (name1 == null) return -1;
      final String name2 = o2.getTargetName();
      if (name2 == null) return 1;
      return name1.compareToIgnoreCase(name2);
    };
    private static final ColumnInfo<TargetFilter, String> NAME_COLUMN = new ColumnInfo<>(
      AntBundle.message("ant.target")) {
      @Override
      public String valueOf(TargetFilter targetFilter) {
        return targetFilter.getTargetName();
      }

      @Override
      public Comparator<TargetFilter> getComparator() {
        return NAME_COMPARATOR;
      }
    };

    private static final Comparator<TargetFilter> DESCRIPTION_COMPARATOR = (o1, o2) -> {
      String description1 = o1.getDescription();
      if (description1 == null) {
        description1 = "";
      }
      String description2 = o2.getDescription();
      if (description2 == null) {
        description2 = "";
      }
      return description1.compareToIgnoreCase(description2);
    };
    private static final ColumnInfo<TargetFilter, String> DESCRIPTION = new ColumnInfo<>(
      AntBundle.message("edit.ant.properties.description.column.name")) {
      @Override
      public String valueOf(TargetFilter targetFilter) {
        return targetFilter.getDescription();
      }

      @Override
      public Comparator<TargetFilter> getComparator() {
        return DESCRIPTION_COMPARATOR;
      }
    };
    private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{CHECK_BOX_COLUMN, NAME_COLUMN, DESCRIPTION};

    FiltersTab() {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JBScrollPane jBScrollPane1 = new JBScrollPane();
        myWholePanel.add(jBScrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            null, null, null, 0, false));
        myFiltersTable = new JBTable();
        jBScrollPane1.setViewportView(myFiltersTable);
      }
      myFiltersTable.getTableHeader().setReorderingAllowed(false);

      UIPropertyBinding.TableListBinding tableListBinding = getBinding().bindList(myFiltersTable, COLUMNS, AntBuildFileImpl.TARGET_FILTERS);
      tableListBinding.setColumnWidths(GlobalAntConfiguration.FILTERS_TABLE_LAYOUT);
      tableListBinding.setSortable(true);
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myWholePanel; }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    public @Nullable String getDisplayName() {
      return AntBundle.message("edit.ant.properties.filters.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myFiltersTable;
    }
  }

  static class ExecutionTab extends Tab {
    private final JPanel myWholePanel;
    private final JLabel myAntCmdLineLabel;
    private final JLabel myJDKLabel;
    private final RawCommandLineEditor myAntCommandLine;
    private final ComboboxWithBrowseButton myAnts;
    private final ComboboxWithBrowseButton myJDKs;
    private final ChooseAndEditComboBoxController<Sdk, String> myJDKsController;
    private final JButton mySetDefaultAnt;
    private final SimpleColoredComponent myDefaultAnt;
    private final JRadioButton myUseCastomAnt;
    private final JRadioButton myUseDefaultAnt;

    private AntReference myProjectDefaultAnt = null;
    private final GlobalAntConfiguration myAntGlobalConfiguration;

    ExecutionTab(final GlobalAntConfiguration antConfiguration) {
      myAntGlobalConfiguration = antConfiguration;
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(1, 1, new Insets(6, 6, 6, 6), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(4, 2, new Insets(0, 3, 0, 3), -1, -1));
        myWholePanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null,
                                                     null, 0, false));
        myJDKLabel = new JLabel();
        this.$$$loadLabelText$$$(myJDKLabel,
                                 this.$$$getMessageFromBundle$$$("messages/AntBundle", "run.execution.tab.run.under.jdk.label"));
        panel1.add(myJDKLabel,
                   new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myJDKs = new ComboboxWithBrowseButton();
        panel1.add(myJDKs, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                               false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 2, new Insets(10, 0, 10, 0), -1, 0));
        panel1.add(panel2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null,
                                               0, true));
        myAntCommandLine = new RawCommandLineEditor();
        panel2.add(myAntCommandLine, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                         new Dimension(150, -1), null, 0, false));
        myAntCmdLineLabel = new JLabel();
        this.$$$loadLabelText$$$(myAntCmdLineLabel,
                                 this.$$$getMessageFromBundle$$$("messages/AntBundle", "run.execution.tab.ant.command.line.label"));
        panel2.add(myAntCmdLineLabel,
                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JBLabel jBLabel1 = new JBLabel();
        jBLabel1.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        jBLabel1.setFontColor(UIUtil.FontColor.BRIGHTER);
        this.$$$loadLabelText$$$(jBLabel1,
                                 this.$$$getMessageFromBundle$$$("messages/AntBundle", "run.execution.tab.ant.command.line.hint"));
        panel2.add(jBLabel1,
                   new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/AntBundle", "run.execution.tab.run.with.ant.border"));
        panel1.add(label1,
                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null,
                                               0, false));
        myUseDefaultAnt = new JRadioButton();
        this.$$$loadButtonText$$$(myUseDefaultAnt,
                                  this.$$$getMessageFromBundle$$$("messages/AntBundle", "run.execution.tab.use.project.default.ant.radio"));
        panel3.add(myUseDefaultAnt, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null,
                                               0, false));
        mySetDefaultAnt = new JButton();
        this.$$$loadButtonText$$$(mySetDefaultAnt,
                                  this.$$$getMessageFromBundle$$$("messages/AntBundle", "run.execution.tab.set.default.button"));
        panel4.add(mySetDefaultAnt,
                   new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDefaultAnt = new SimpleColoredComponent();
        panel4.add(myDefaultAnt, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                     null,
                                                     0, false));
        myUseCastomAnt = new JRadioButton();
        this.$$$loadButtonText$$$(myUseCastomAnt,
                                  this.$$$getMessageFromBundle$$$("messages/AntBundle", "run.execution.tab.use.custom.ant.radio"));
        panel3.add(myUseCastomAnt, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myAnts = new ComboboxWithBrowseButton();
        panel3.add(myAnts, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                               false));
      }
      myAntCommandLine.attachLabel(myAntCmdLineLabel);
      myAntCommandLine.setDialogCaption(AntBundle.message("run.execution.tab.ant.command.line.dialog.title"));
      setLabelFor(myJDKLabel, myJDKs);

      myJDKsController =
        new ChooseAndEditComboBoxController<>(myJDKs, jdk -> jdk != null ? jdk.getName() : "", String.CASE_INSENSITIVE_ORDER) {
          @Override
          public Iterator<Sdk> getAllListItems() {
            Application application = ApplicationManager.getApplication();
            if (application == null) {
              return Collections.singletonList((Sdk)null).iterator();
            }
            ArrayList<Sdk> allJdks = new ArrayList<>(Arrays.asList(ProjectJdkTable.getInstance().getAllJdks()));
            allJdks.add(0, null);
            return allJdks.iterator();
          }

          @Override
          public Sdk openConfigureDialog(Sdk jdk, JComponent parent) {
            ProjectJdksEditor editor = new ProjectJdksEditor(jdk, myJDKs.getComboBox());
            editor.show();
            return editor.getSelectedJdk();
          }
        };

      UIPropertyBinding.Composite binding = getBinding();
      binding.bindString(myAntCommandLine.getTextField(), AntBuildFileImpl.ANT_COMMAND_LINE_PARAMETERS);
      binding.bindString(myJDKs.getComboBox(), AntBuildFileImpl.CUSTOM_JDK_NAME);
      binding.addBinding(new RunWithAntBinding(myUseDefaultAnt, myUseCastomAnt, myAnts, myAntGlobalConfiguration));

      mySetDefaultAnt.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          AntSetPanel antSetPanel = new AntSetPanel(myAntGlobalConfiguration);
          antSetPanel.reset();
          antSetPanel.setSelection(myProjectDefaultAnt.find(myAntGlobalConfiguration));
          AntInstallation antInstallation = antSetPanel.showDialog(mySetDefaultAnt);
          if (antInstallation == null) {
            return;
          }
          myProjectDefaultAnt = antInstallation.getReference();
          updateDefaultAnt();
        }
      });
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    /** @noinspection ALL */
    private String $$$getMessageFromBundle$$$(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if ($$$cachedGetBundleMethod$$$ == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /** @noinspection ALL */
    private void $$$loadLabelText$$$(JLabel component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setDisplayedMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myWholePanel; }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    public @Nullable String getDisplayName() {
      return AntBundle.message("edit.ant.properties.execution.tab.display.name");
    }

    @Override
    public void reset(AbstractProperty.AbstractPropertyContainer options) {
      String projectJdkName = AntConfigurationImpl.DEFAULT_JDK_NAME.get(options);
      myJDKsController.setRenderer(new AntUIUtil.ProjectJdkRenderer(true, projectJdkName));
      super.reset(options);
      myJDKsController.resetList(null);
      myProjectDefaultAnt = AntConfigurationImpl.DEFAULT_ANT.get(options);
      updateDefaultAnt();
    }

    private void updateDefaultAnt() {
      myDefaultAnt.clear();
      AntUIUtil.customizeReference(myProjectDefaultAnt, myDefaultAnt, myAntGlobalConfiguration);
      myDefaultAnt.revalidate();
      myDefaultAnt.repaint();
    }

    @Override
    public void apply(AbstractProperty.AbstractPropertyContainer options) {
      AntConfigurationImpl.DEFAULT_ANT.set(options, myProjectDefaultAnt);
      super.apply(options);
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myAntCommandLine.getTextField();
    }
  }

  private static class AdditionalClasspathTab extends Tab {
    private final JPanel myWholePanel;
    private final AntClasspathEditorPanel myClasspath;

    AdditionalClasspathTab() {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(1, 1, new Insets(6, 6, 6, 6), -1, -1));
        myClasspath = new AntClasspathEditorPanel();
        myClasspath.setEnabled(true);
        myWholePanel.add(myClasspath, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          null,
                                                          null, null, 0, false));
      }
      getBinding().addBinding(myClasspath.setClasspathProperty(AntBuildFileImpl.ADDITIONAL_CLASSPATH));
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myWholePanel; }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    public @Nullable String getDisplayName() {
      return AntBundle.message("edit.ant.properties.additional.classpath.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myClasspath.getPreferedFocusComponent();
    }
  }

  private static void setLabelFor(JLabel label, ComponentWithBrowseButton component) {
    label.setLabelFor(component.getChildComponent());
  }
}
