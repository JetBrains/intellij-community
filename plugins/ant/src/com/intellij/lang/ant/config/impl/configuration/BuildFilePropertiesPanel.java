// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.impl.*;
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
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public final class BuildFilePropertiesPanel {
  @NonNls private static final String DIMENSION_SERVICE_KEY = "antBuildFilePropertiesDialogDimension";

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
    private JLabel myBuildFileName;
    private JTextField myXmx;
    private JTextField myXss;
    private JCheckBox myRunInBackground;
    private JCheckBox myCloseOnNoError;
    private JPanel myTabsPlace;
    private JPanel myWholePanel;
    private JLabel myHeapSizeLabel;
    private JCheckBox myColoredOutputMessages;
    private JCheckBox myCollapseFinishedTargets;
    private final Tab[] myTabs;
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();

    private Form() {
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
    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myPropertiesTable;
    }
  }

  private static class FiltersTab extends Tab {
    private JTable myFiltersTable;
    private JPanel myWholePanel;

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
      myFiltersTable.getTableHeader().setReorderingAllowed(false);

      UIPropertyBinding.TableListBinding tableListBinding = getBinding().bindList(myFiltersTable, COLUMNS, AntBuildFileImpl.TARGET_FILTERS);
      tableListBinding.setColumnWidths(GlobalAntConfiguration.FILTERS_TABLE_LAYOUT);
      tableListBinding.setSortable(true);
    }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.filters.tab.display.name");
    }

    @Override
    public JComponent getPreferedFocusComponent() {
      return myFiltersTable;
    }
  }

  static class ExecutionTab extends Tab {
    private JPanel myWholePanel;
    private JLabel myAntCmdLineLabel;
    private JLabel myJDKLabel;
    private RawCommandLineEditor myAntCommandLine;
    private ComboboxWithBrowseButton myAnts;
    private ComboboxWithBrowseButton myJDKs;
    private final ChooseAndEditComboBoxController<Sdk, String> myJDKsController;
    private JButton mySetDefaultAnt;
    private SimpleColoredComponent myDefaultAnt;
    private JRadioButton myUseCastomAnt;
    private JRadioButton myUseDefaultAnt;

    private AntReference myProjectDefaultAnt = null;
    private final GlobalAntConfiguration myAntGlobalConfiguration;

    ExecutionTab(final GlobalAntConfiguration antConfiguration) {
      myAntGlobalConfiguration = antConfiguration;
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

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    @Nullable
    public String getDisplayName() {
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
    private JPanel myWholePanel;
    private AntClasspathEditorPanel myClasspath;

    AdditionalClasspathTab() {
      getBinding().addBinding(myClasspath.setClasspathProperty(AntBuildFileImpl.ADDITIONAL_CLASSPATH));
    }

    @Override
    public JComponent getComponent() {
      return myWholePanel;
    }

    @Override
    @Nullable
    public String getDisplayName() {
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
