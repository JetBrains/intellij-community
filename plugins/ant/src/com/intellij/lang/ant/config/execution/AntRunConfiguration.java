// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ListTableWithButtons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.lang.ant.config.impl.TargetChooserDialog;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AntRunConfiguration extends LocatableConfigurationBase implements RunProfileWithCompileBeforeLaunchOption{
  private AntSettings mySettings = new AntSettings();

  public AntRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  public RunConfiguration clone() {
    final AntRunConfiguration configuration = (AntRunConfiguration)super.clone();
    configuration.mySettings = mySettings.copy();
    return configuration;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new AntConfigurationSettingsEditor();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (!AntConfiguration.getInstance(getProject()).isInitialized()) {
      throw new RuntimeConfigurationException(AntBundle.message("dialog.message.ant.configuration.not.initialized"));
    }
    if (getTarget() == null)
      throw new RuntimeConfigurationException(AntBundle.message("dialog.message.target.not.specified"),
                                              AntBundle.message("dialog.title.ant.configuration.missing.parameters"));
  }

  @Override
  public String suggestedName() {
    AntBuildTarget target = getTarget();
    return target == null ? null : target.getDisplayName();
  }

  @NotNull
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    return new AntRunProfileState(env);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    mySettings.readExternal(element);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    mySettings.writeExternal(element);
  }

  @Nullable
  public AntBuildTarget getTarget() {
    return GlobalAntConfiguration.getInstance().findTarget(getProject(), mySettings.myFileUrl, mySettings.myTargetName);
  }

  @NotNull
  public List<BuildFileProperty> getProperties() {
    return Collections.unmodifiableList(mySettings.myProperties);
  }

  @SuppressWarnings("UnusedReturnValue")
  public boolean acceptSettings(AntBuildTarget target) {
    VirtualFile virtualFile = target.getModel().getBuildFile().getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    mySettings.myFileUrl = virtualFile.getUrl();
    mySettings.myTargetName = target.getName();
    return true;
  }

  public static class AntSettings implements JDOMExternalizable {
    private static final String SETTINGS = "antsettings";
    private static final String PROPERTY = "property";
    private static final String FILE = "antfile";
    private static final String TARGET = "target";
    private String myFileUrl = null;
    private String myTargetName = null;
    private final List<BuildFileProperty> myProperties = new ArrayList<>();

    public AntSettings() {
    }

    public AntSettings(String fileUrl, String targetName) {
      myFileUrl = fileUrl;
      myTargetName = targetName;
    }

    @Override
    public String toString() {
      return myTargetName + "@" + myFileUrl;
    }

    public AntSettings copy() {
      final AntSettings copy = new AntSettings(myFileUrl, myTargetName);
      copyProperties(myProperties, copy.myProperties);
      return copy;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      element = element.getChild(SETTINGS);
      if (element != null) {
        myFileUrl = element.getAttributeValue(FILE);
        myTargetName = element.getAttributeValue(TARGET);
        myProperties.clear();
        for (Element pe : element.getChildren(PROPERTY)) {
          BuildFileProperty prop = new BuildFileProperty();
          prop.readExternal(pe);
          myProperties.add(prop);
        }
      }
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      if (myFileUrl != null && myTargetName != null) {
        final Element settingsElem = new Element(SETTINGS);
        settingsElem.setAttribute(FILE, myFileUrl);
        settingsElem.setAttribute(TARGET, myTargetName);
        for (BuildFileProperty property : myProperties) {
          final Element pe = new Element(PROPERTY);
          property.writeExternal(pe);
          settingsElem.addContent(pe);
        }
        element.addContent(settingsElem);
      }
    }
  }

  private class AntConfigurationSettingsEditor extends SettingsEditor<RunConfiguration> {
    private String myFileUrl = null;
    private String myTargetName = null;

    private ExtendableTextField myTextField;
    private final PropertiesTable myPropTable = new PropertiesTable();

    private final Runnable myAction = () -> {
      AntBuildTarget buildTarget = getTarget();
      final TargetChooserDialog dlg = new TargetChooserDialog(getProject(), buildTarget);
      if (dlg.showAndGet()) {
        myFileUrl = null;
        myTargetName = null;
        buildTarget = dlg.getSelectedTarget();
        if (buildTarget != null) {
          final VirtualFile vFile = buildTarget.getModel().getBuildFile().getVirtualFile();
          if (vFile != null) {
            myFileUrl = vFile.getUrl();
            myTargetName = buildTarget.getName();
          }
        }
        updateUI();
      }
    };

    private void updateUI() {
      myTextField.setText("");
      if (myFileUrl != null && myTargetName != null) {
        myTextField.setText(myTargetName);
      }
      myPropTable.refreshValues();
      fireEditorStateChanged();
    }

    @Override
    protected void resetEditorFrom(@NotNull RunConfiguration s) {
      final AntRunConfiguration config = (AntRunConfiguration)s;
      myFileUrl = config.mySettings.myFileUrl;
      myTargetName = config.mySettings.myTargetName;
      myPropTable.setValues(config.mySettings.myProperties);
      updateUI();
    }

    @Override
    protected void applyEditorTo(@NotNull RunConfiguration s) {
      final AntRunConfiguration config = (AntRunConfiguration)s;
      config.mySettings.myFileUrl = myFileUrl;
      config.mySettings.myTargetName = myTargetName;
      copyProperties(ContainerUtil.filter(myPropTable.getElements(), property -> !myPropTable.isEmpty(property)), config.mySettings.myProperties);
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
      myTextField = new ExtendableTextField().addBrowseExtension(myAction, this);

      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(LabeledComponent.create(myTextField, AntBundle.message("label.ant.run.configuration.target.name"), BorderLayout.WEST), BorderLayout.NORTH);

      String propertiesTableName = AntBundle.message("label.table.name.ant.properties");
      final LabeledComponent<JComponent> tableComponent = LabeledComponent.create(myPropTable.getComponent(), propertiesTableName);
      tableComponent.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
      panel.add(tableComponent, BorderLayout.CENTER);
      return panel;
    }
  }

  private static class PropertiesTable extends ListTableWithButtons<BuildFileProperty> {
    @Override
    protected ListTableModel createListModel() {
      final ColumnInfo nameColumn = new TableColumn(AntBundle.message("column.name.ant.configuration.property.name")) {
        @Nullable
        @Override
        public String valueOf(BuildFileProperty property) {
          return property.getPropertyName();
        }

        @Override
        public void setValue(BuildFileProperty property, String value) {
          property.setPropertyName(value);
        }
      };
      final ColumnInfo valueColumn = new TableColumn(AntBundle.message("column.name.ant.configuration.property.value")) {
        @Nullable
        @Override
        public String valueOf(BuildFileProperty property) {
          return property.getPropertyValue();
        }

        @Override
        public void setValue(BuildFileProperty property, String value) {
          property.setPropertyValue(value);
        }
      };
      return new ListTableModel(nameColumn, valueColumn);
    }

    @Override
    protected BuildFileProperty createElement() {
      return new BuildFileProperty();
    }

    @Override
    protected boolean isEmpty(BuildFileProperty element) {
      return StringUtil.isEmpty(element.getPropertyName()) && StringUtil.isEmpty(element.getPropertyValue());
    }

    @Override
    protected BuildFileProperty cloneElement(BuildFileProperty p) {
      return p.clone();
    }

    @Override
    protected boolean canDeleteElement(BuildFileProperty selection) {
      return true;
    }

    @Override
    public List<BuildFileProperty> getElements() {
      return super.getElements();
    }

    private abstract static class TableColumn extends ElementsColumnInfoBase<BuildFileProperty> {
      TableColumn(final @NlsContexts.ColumnName String name) {
        super(name);
      }

      @Override
      public boolean isCellEditable(BuildFileProperty property) {
        return true;
      }

      @Nullable
      @Override
      protected String getDescription(BuildFileProperty element) {
        return null;
      }
    }
  }

  private static void copyProperties(final Iterable<BuildFileProperty> from, final List<? super BuildFileProperty> to) {
    to.clear();
    for (BuildFileProperty p : from) {
      to.add(p.clone());
    }
  }
}
