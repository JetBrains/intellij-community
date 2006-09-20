package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.impl.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.NewInstanceFactory;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class BuildFilePropertiesPanel {
  @NonNls private static final String DIMENSION_SERVICE_KEY = "antBuildFilePropertiesDialogDimension";
  private final Form myForm = new Form();
  private AntBuildFileBase myBuildFile;

  private BuildFilePropertiesPanel() {
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
    builder.setPreferedFocusComponent(myForm.getPreferedFocusComponent());
    builder.setTitle(AntBundle.message("build.file.properties.dialog.title"));
    builder.setHelpId("ant.buildDialog");

    boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
    if (isOk) {
      apply();
    }
    beforeClose();
    return isOk;
  }

  private void beforeClose() {
    myForm.beforeClose(myBuildFile);
  }

  public static boolean editBuildFile(AntBuildFileBase buildFile) {
    BuildFilePropertiesPanel panel = new BuildFilePropertiesPanel();
    panel.reset(buildFile);
    return panel.showDialog();
  }

  static abstract class Tab {
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();

    public abstract JComponent getComponent();

    public abstract String getDisplayName();

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

  private static class Form {
    private JLabel myBuildFileName;
    private JTextField myXmx;
    private JCheckBox myRunInBackground;
    private JCheckBox myCloseOnNoError;
    private JPanel myTabsPlace;
    private JPanel myWholePanel;
    private JLabel myHeapSizeLabel;
    private final Tab[] myTabs = new Tab[]{
          new PropertiesTab(),
          new ExecutionTab(GlobalAntConfiguration.getInstance()),
          new AdditionalClasspathTab(),
          new FiltersTab()
        };
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();
    private final TabbedPaneWrapper myWrapper;

    public Form() {
      myHeapSizeLabel.setLabelFor(myXmx);
      myWrapper = new TabbedPaneWrapper();
      myTabsPlace.setLayout(new BorderLayout());
      myTabsPlace.add(myWrapper.getComponent(), BorderLayout.CENTER);

      myBinding.bindBoolean(myRunInBackground, AntBuildFileImpl.RUN_IN_BACKGROUND);
      myBinding.bindBoolean(myCloseOnNoError, AntBuildFileImpl.CLOSE_ON_NO_ERRORS);
      myBinding.bindInt(myXmx, AntBuildFileImpl.MAX_HEAP_SIZE);

      for (Tab tab : myTabs) {
        myWrapper.addTab(tab.getDisplayName(), tab.getComponent());
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
  }

  private static class PropertiesTab extends Tab {
    private JButton myAddButton;
    private JButton myRemoveButton;
    private JTable myPropertiesTable;
    private JPanel myWholePanel;

    private static final ColumnInfo<BuildFileProperty, String> NAME_COLUMN = new ColumnInfo<BuildFileProperty, String>(
      AntBundle.message("edit.ant.properties.name.column.name")) {
      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyName();
      }

      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return true;
      }

      public void setValue(BuildFileProperty buildFileProperty, String name) {
        buildFileProperty.setPropertyName(name);
      }
    };
    private static final ColumnInfo<BuildFileProperty, String> VALUE_COLUMN = new ColumnInfo<BuildFileProperty, String>(
      AntBundle.message("edit.ant.properties.value.column.name")) {
      public boolean isCellEditable(BuildFileProperty buildFileProperty) {
        return true;
      }

      public String valueOf(BuildFileProperty buildFileProperty) {
        return buildFileProperty.getPropertyValue();
      }

      public void setValue(BuildFileProperty buildFileProperty, String value) {
        buildFileProperty.setPropertyValue(value);
      }

      public TableCellEditor getEditor(BuildFileProperty item) {
        return new AntUIUtil.PropertyValueCellEditor();
      }
    };
    private static final ColumnInfo[] PROPERTY_COLUMNS = new ColumnInfo[]{NAME_COLUMN, VALUE_COLUMN};

    public PropertiesTab() {
      UIPropertyBinding.TableListBinding tableListBinding = getBinding().bindList(myPropertiesTable, PROPERTY_COLUMNS,
                                                                                  AntBuildFileImpl.ANT_PROPERTIES);
      tableListBinding.setColumnWidths(GlobalAntConfiguration.PROPERTIES_TABLE_LAYOUT);
      tableListBinding.addAddFacility(myAddButton, NewInstanceFactory.fromClass(BuildFileProperty.class));
      tableListBinding.addRemoveFacility(myRemoveButton);
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.tab.display.name");
    }

    public JComponent getPreferedFocusComponent() {
      return myPropertiesTable;
    }
  }

  private static class FiltersTab extends Tab {
    private JTable myFiltersTable;
    private JPanel myWholePanel;

    private static final int PREFERRED_CHECKBOX_COLUMN_WIDTH = new JCheckBox().getPreferredSize().width + 4;
    private static final ColumnInfo<TargetFilter, Boolean> CHECK_BOX_COLUMN = new ColumnInfo<TargetFilter, Boolean>("") {
      public Boolean valueOf(TargetFilter targetFilter) {
        return targetFilter.isVisible();
      }

      public void setValue(TargetFilter targetFilter, Boolean aBoolean) {
        targetFilter.setVisible(aBoolean.booleanValue());
      }

      public int getWidth(JTable table) {
        return PREFERRED_CHECKBOX_COLUMN_WIDTH;
      }

      public Class getColumnClass() {
        return Boolean.class;
      }

      public boolean isCellEditable(TargetFilter targetFilter) {
        return true;
      }
    };

    private static final Comparator<TargetFilter> NAME_COMPARATOR = new Comparator<TargetFilter>() {
      public int compare(TargetFilter o1, TargetFilter o2) {
        final String name1 = o1.getTargetName();
        if(name1 == null) return -1;
        final String name2 = o2.getTargetName();
        if(name2 == null) return 1;
        return name1.compareToIgnoreCase(name2);
      }
    };
    private static final ColumnInfo<TargetFilter, String> NAME_COLUMN = new ColumnInfo<TargetFilter, String>(
      AntBundle.message("edit.ant.properties.target.column.name")) {
      public String valueOf(TargetFilter targetFilter) {
        return targetFilter.getTargetName();
      }

      public Comparator<TargetFilter> getComparator() {
        return NAME_COMPARATOR;
      }
    };

    private static final Comparator<TargetFilter> DESCRIPTION_COMPARATOR = new Comparator<TargetFilter>() {
      public int compare(TargetFilter o1, TargetFilter o2) {
        String description1 = o1.getDescription();
        if (description1 == null) {
          description1 = "";
        }
        String description2 = o2.getDescription();
        if (description2 == null) {
          description2 = "";
        }
        return description1.compareToIgnoreCase(description2);
      }
    };
    private static final ColumnInfo<TargetFilter, String> DESCRIPTION = new ColumnInfo<TargetFilter, String>(
      AntBundle.message("edit.ant.properties.description.column.name")) {
      public String valueOf(TargetFilter targetFilter) {
        return targetFilter.getDescription();
      }

      public Comparator<TargetFilter> getComparator() {
        return DESCRIPTION_COMPARATOR;
      }
    };
    private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{CHECK_BOX_COLUMN, NAME_COLUMN, DESCRIPTION};

    public FiltersTab() {
      myFiltersTable.getTableHeader().setReorderingAllowed(false);

      UIPropertyBinding.TableListBinding tableListBinding = getBinding().bindList(myFiltersTable, COLUMNS, AntBuildFileImpl.TARGET_FILTERS);
      tableListBinding.setColumnWidths(GlobalAntConfiguration.FILTERS_TABLE_LAYOUT);
      tableListBinding.setSortable(true);
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.filters.tab.display.name");
    }

    public JComponent getPreferedFocusComponent() {
      return myFiltersTable;
    }

    public void reset(AbstractProperty.AbstractPropertyContainer options) {
      super.reset(options);
    }
  }

  static class ExecutionTab extends Tab {
    private JPanel myWholePanel;
    private JLabel myAntCmdLineLabel;
    private JLabel myJDKLabel;
    private RawCommandLineEditor myAntCommandLine;
    private ComboboxWithBrowseButton myAnts;
    private ComboboxWithBrowseButton myJDKs;
    private ChooseAndEditComboBoxController<ProjectJdk, String> myJDKsController;
    private JButton mySetDefaultAnt;
    private SimpleColoredComponent myDefaultAnt;
    private JRadioButton myUseCastomAnt;
    private JRadioButton myUseDefaultAnt;

    private AntReference myProjectDefaultAnt = null;
    private final GlobalAntConfiguration myAntGlobalConfiguration;

    public ExecutionTab(final GlobalAntConfiguration antConfiguration) {
      myAntGlobalConfiguration = antConfiguration;
      myAntCommandLine.attachLabel(myAntCmdLineLabel);
      myAntCommandLine.setDialodCaption(AntBundle.message("run.execution.tab.ant.command.line.dialog.title"));
      setLabelFor(myJDKLabel, myJDKs);

      myJDKsController = new ChooseAndEditComboBoxController<ProjectJdk, String>(myJDKs, new Convertor<ProjectJdk, String>() {
        public String convert(ProjectJdk jdk) {
          return jdk != null ? jdk.getName() : "";
        }
      }, String.CASE_INSENSITIVE_ORDER) {
        public Iterator<ProjectJdk> getAllListItems() {
          Application application = ApplicationManager.getApplication();
          if (application == null) {
            return Collections.singletonList((ProjectJdk)null).iterator();
          }
          ArrayList<ProjectJdk> allJdks = new ArrayList<ProjectJdk>(Arrays.asList(ProjectJdkTable.getInstance().getAllJdks()));
          allJdks.add(0, null);
          return allJdks.iterator();
        }

        public ProjectJdk openConfigureDialog(ProjectJdk jdk, JComponent parent) {
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
        public void actionPerformed(ActionEvent e) {
          AntSetPanel antSetPanel = new AntSetPanel(myAntGlobalConfiguration);
          antSetPanel.setSelection(myProjectDefaultAnt.find(myAntGlobalConfiguration));
          antSetPanel.reset();
          AntInstallation antInstallation = antSetPanel.showDialog(mySetDefaultAnt);
          if (antInstallation == null) {
            return;
          }
          myProjectDefaultAnt = antInstallation.getReference();
          updateDefaultAnt();
        }
      });
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.execution.tab.display.name");
    }

    public void reset(AbstractProperty.AbstractPropertyContainer options) {
      String projectJdkName = AntConfigurationImpl.DEFAULT_JDK_NAME.get(options);
      myJDKsController.setRenderer(new AntUIUtil.ProjectJdkRenderer(myAntGlobalConfiguration, true, projectJdkName));
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

    public void apply(AbstractProperty.AbstractPropertyContainer options) {
      AntConfigurationImpl.DEFAULT_ANT.set(options, myProjectDefaultAnt);
      super.apply(options);
    }

    public JComponent getPreferedFocusComponent() {
      return myAntCommandLine.getTextField();
    }
  }

  private static class AdditionalClasspathTab extends Tab {
    private JPanel myWholePanel;
    private AntClasspathEditorPanel myClasspath;

    public AdditionalClasspathTab() {
      getBinding().addBinding(myClasspath.setClasspathProperty(AntBuildFileImpl.ADDITIONAL_CLASSPATH));
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    @Nullable
    public String getDisplayName() {
      return AntBundle.message("edit.ant.properties.additional.classpath.tab.display.name");
    }

    public JComponent getPreferedFocusComponent() {
      return myClasspath.getPreferedFocusComponent();
    }
  }

  private static void setLabelFor(JLabel label, ComponentWithBrowseButton component) {
    label.setLabelFor(component.getChildComponent());
  }

}
