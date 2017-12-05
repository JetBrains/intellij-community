/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.settings.LocationSettingType;
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemSettingsControlCustomizer;
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemJdkComboBox;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK;

/**
 * @author Vladislav.Soroka
 * @since 2/24/2015
 */
public class IdeaGradleProjectSettingsControlBuilder implements GradleProjectSettingsControlBuilder {

  private static final long BALLOON_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);
  @NotNull
  private final GradleInstallationManager myInstallationManager;
  @NotNull
  private final GradleProjectSettings myInitialSettings;
  @NotNull
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  @NotNull
  private LocationSettingType myGradleHomeSettingType = LocationSettingType.UNKNOWN;
  private boolean myShowBalloonIfNecessary;
  private ActionListener myActionListener;


  private boolean dropUseAutoImportBox;
  private boolean dropCreateEmptyContentRootDirectoriesBox;

  @SuppressWarnings("FieldCanBeLocal") // Used implicitly by reflection at disposeUIResources() and showUi()
  @Nullable
  private JLabel myGradleHomeLabel;
  @Nullable
  private TextFieldWithBrowseButton myGradleHomePathField;
  private boolean dropGradleHomePathComponents;

  @SuppressWarnings("FieldCanBeLocal") // Used implicitly by reflection at disposeUIResources() and showUi()
  @Nullable
  private JLabel myGradleJdkLabel;
  @Nullable
  private ExternalSystemJdkComboBox myGradleJdkComboBox;
  @Nullable
  private FixedSizeButton myGradleJdkSetUpButton;
  private boolean dropGradleJdkComponents;

  @Nullable
  private JBRadioButton myUseWrapperButton;
  private boolean dropUseWrapperButton;

  @Nullable
  private JBRadioButton myUseWrapperWithVerificationButton;
  @SuppressWarnings("FieldCanBeLocal") // Used implicitly by reflection at disposeUIResources() and showUi()
  @Nullable
  private JBLabel myUseWrapperVerificationLabel;
  private boolean dropCustomizableWrapperButton;

  @Nullable
  private JBRadioButton myUseLocalDistributionButton;
  private boolean dropUseLocalDistributionButton;

  @Nullable
  private JBRadioButton myUseBundledDistributionButton;
  private boolean dropUseBundledDistributionButton;
  @Nullable
  private JBCheckBox myResolveModulePerSourceSetCheckBox;
  private boolean dropResolveModulePerSourceSetCheckBox;

  @Nullable
  private JBCheckBox myStoreExternallyCheckBox;

  public IdeaGradleProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings) {
    myInstallationManager = ServiceManager.getService(GradleInstallationManager.class);
    myInitialSettings = initialSettings;

    myActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myGradleHomePathField == null) return;

        boolean localDistributionEnabled = myUseLocalDistributionButton != null && myUseLocalDistributionButton.isSelected();
        myGradleHomePathField.setEnabled(localDistributionEnabled);
        if (localDistributionEnabled) {
          if (myGradleHomePathField.getText().isEmpty()) {
            deduceGradleHomeIfPossible();
          }
          else {
            if (myInstallationManager.isGradleSdkHome(myGradleHomePathField.getText())) {
              myGradleHomeSettingType = LocationSettingType.EXPLICIT_CORRECT;
            }
            else {
              myGradleHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT;
              myShowBalloonIfNecessary = true;
            }
          }
          showBalloonIfNecessary();
        }
        else {
          myAlarm.cancelAllRequests();
        }
      }
    };
  }

  public IdeaGradleProjectSettingsControlBuilder dropGradleJdkComponents() {
    dropGradleJdkComponents = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropUseWrapperButton() {
    dropUseWrapperButton = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropGradleHomePathComponents() {
    dropGradleHomePathComponents = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropCustomizableWrapperButton() {
    dropCustomizableWrapperButton = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropUseLocalDistributionButton() {
    dropUseLocalDistributionButton = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropUseBundledDistributionButton() {
    dropUseBundledDistributionButton = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropUseAutoImportBox() {
    dropUseAutoImportBox = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropCreateEmptyContentRootDirectoriesBox() {
    dropCreateEmptyContentRootDirectoriesBox = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropResolveModulePerSourceSetCheckBox() {
    dropResolveModulePerSourceSetCheckBox = true;
    return this;
  }

  @Override
  public void showUi(boolean show) {
    ExternalSystemUiUtil.showUi(this, show);
  }

  @NotNull
  public GradleProjectSettings getInitialSettings() {
    return myInitialSettings;
  }

  @Override
  public ExternalSystemSettingsControlCustomizer getExternalSystemSettingsControlCustomizer() {
    return new ExternalSystemSettingsControlCustomizer(dropUseAutoImportBox, dropCreateEmptyContentRootDirectoriesBox);
  }

  @Override
  public void createAndFillControls(PaintAwarePanel content, int indentLevel) {
    content.setPaintCallback(graphics -> showBalloonIfNecessary());

    content.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (!"ancestor".equals(evt.getPropertyName())) {
          return;
        }

        // Configure the balloon to show on initial configurable drawing.
        myShowBalloonIfNecessary = evt.getNewValue() != null && evt.getOldValue() == null;

        if (evt.getNewValue() == null && evt.getOldValue() != null) {
          // Cancel delayed balloons when the configurable is hidden.
          myAlarm.cancelAllRequests();
        }
      }
    });

    if (!dropResolveModulePerSourceSetCheckBox) {
      myResolveModulePerSourceSetCheckBox = new JBCheckBox(GradleBundle.message("gradle.settings.text.create.module.per.sourceset"));
      content.add(myResolveModulePerSourceSetCheckBox, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    }

    myStoreExternallyCheckBox = new JBCheckBox("Store generated project files externally");
    content.add(myStoreExternallyCheckBox, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

    addGradleChooserComponents(content, indentLevel);
    addGradleHomeComponents(content, indentLevel);
    addGradleJdkComponents(content, indentLevel);
  }

  @Override
  public void disposeUIResources() {
    ExternalSystemUiUtil.disposeUi(this);
  }

  /**
   * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
   */
  private void deduceGradleHomeIfPossible() {
    if (myGradleHomePathField == null) return;

    File gradleHome = myInstallationManager.getAutodetectedGradleHome();
    if (gradleHome == null) {
      new DelayedBalloonInfo(MessageType.WARNING, LocationSettingType.UNKNOWN, BALLOON_DELAY_MILLIS).run();
      return;
    }
    myGradleHomeSettingType = LocationSettingType.DEDUCED;
    new DelayedBalloonInfo(MessageType.INFO, LocationSettingType.DEDUCED, BALLOON_DELAY_MILLIS).run();
    myGradleHomePathField.setText(gradleHome.getPath());
    myGradleHomePathField.getTextField().setForeground(LocationSettingType.DEDUCED.getColor());
  }

  @Override
  public IdeaGradleProjectSettingsControlBuilder addGradleJdkComponents(PaintAwarePanel content, int indentLevel) {
    if(!dropGradleJdkComponents) {
      myGradleJdkLabel = new JBLabel(GradleBundle.message("gradle.settings.text.jvm.path"));
      myGradleJdkComboBox = new ExternalSystemJdkComboBox().withoutJre();

      content.add(myGradleJdkLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
      JPanel gradleJdkPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));
      gradleJdkPanel.setFocusable(false);
      gradleJdkPanel.add(myGradleJdkComboBox, BorderLayout.CENTER);
      myGradleJdkSetUpButton = new FixedSizeButton(myGradleJdkComboBox);
      myGradleJdkSetUpButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
      // FixedSizeButton isn't focusable but it should be selectable via keyboard.
      DumbAwareAction.create(event -> {
        for (ActionListener listener : myGradleJdkSetUpButton.getActionListeners()) {
          listener.actionPerformed(new ActionEvent(myGradleJdkComboBox, ActionEvent.ACTION_PERFORMED, "action"));
        }
      }).registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                                   myGradleJdkComboBox);

      if (ScreenReader.isActive()) {
        myGradleJdkSetUpButton.setFocusable(true);
        myGradleJdkSetUpButton.getAccessibleContext().setAccessibleName(ApplicationBundle.message("button.new"));
      }
      gradleJdkPanel.add(myGradleJdkSetUpButton, BorderLayout.EAST);
      content.add(gradleJdkPanel, ExternalSystemUiUtil.getFillLineConstraints(0));
    }
    return this;
  }

  @Override
  public IdeaGradleProjectSettingsControlBuilder addGradleChooserComponents(PaintAwarePanel content, int indentLevel) {
    ButtonGroup buttonGroup = new ButtonGroup();

    if(!dropUseWrapperButton) {
      myUseWrapperButton = new JBRadioButton(GradleBundle.message("gradle.settings.text.use.default_wrapper.configured"));
      myUseWrapperButton.addActionListener(myActionListener);
      buttonGroup.add(myUseWrapperButton);
      content.add(myUseWrapperButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    }

    if(!dropCustomizableWrapperButton) {
      myUseWrapperWithVerificationButton = new JBRadioButton(GradleBundle.message("gradle.settings.text.use.customizable_wrapper"));
      myUseWrapperWithVerificationButton.addActionListener(myActionListener);
      myUseWrapperVerificationLabel = new JBLabel(GradleBundle.message("gradle.settings.text.wrapper.customization.compatibility"));
      myUseWrapperVerificationLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.MINI));
      myUseWrapperVerificationLabel.setIcon(UIUtil.getBalloonInformationIcon());
      buttonGroup.add(myUseWrapperWithVerificationButton);
      content.add(myUseWrapperWithVerificationButton, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
      content.add(myUseWrapperVerificationLabel, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    }

    if(!dropUseLocalDistributionButton) {
      myUseLocalDistributionButton = new JBRadioButton(GradleBundle.message("gradle.settings.text.use.local.distribution"));
      myUseLocalDistributionButton.addActionListener(myActionListener);
      buttonGroup.add(myUseLocalDistributionButton);
      content.add(myUseLocalDistributionButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    }

    if(!dropUseBundledDistributionButton) {
      myUseBundledDistributionButton = new JBRadioButton(
        GradleBundle.message("gradle.settings.text.use.bundled.distribution", GradleVersion.current().getVersion()));
      myUseBundledDistributionButton.addActionListener(myActionListener);
      buttonGroup.add(myUseBundledDistributionButton);
      //content.add(Box.createGlue(), ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
      content.add(myUseBundledDistributionButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    }

    return this;
  }

  @Override
  public boolean validate(GradleProjectSettings settings) throws ConfigurationException {
    if (myGradleHomePathField == null) return true;

    String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
    if (myUseLocalDistributionButton != null && myUseLocalDistributionButton.isSelected()) {
      if (StringUtil.isEmpty(gradleHomePath)) {
        myGradleHomeSettingType = LocationSettingType.UNKNOWN;
        throw new ConfigurationException(GradleBundle.message("gradle.home.setting.type.explicit.empty", gradleHomePath));
      }
      else if (!myInstallationManager.isGradleSdkHome(new File(gradleHomePath))) {
        myGradleHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT;
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
        throw new ConfigurationException(GradleBundle.message("gradle.home.setting.type.explicit.incorrect", gradleHomePath));
      }
    }
    return true;
  }

  @Override
  public void apply(GradleProjectSettings settings) {
    settings.setCompositeBuild(myInitialSettings.getCompositeBuild());
    if (myGradleHomePathField != null) {
      String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
      if (StringUtil.isEmpty(gradleHomePath)) {
        settings.setGradleHome(null);
      }
      else {
        settings.setGradleHome(gradleHomePath);
        GradleUtil.storeLastUsedGradleHome(gradleHomePath);
      }
    }

    if (myGradleJdkComboBox != null) {
      final String gradleJvm = FileUtil.toCanonicalPath(myGradleJdkComboBox.getSelectedValue());
      settings.setGradleJvm(StringUtil.isEmpty(gradleJvm) ? null : gradleJvm);
    }

    if (myResolveModulePerSourceSetCheckBox != null) {
      settings.setResolveModulePerSourceSet(myResolveModulePerSourceSetCheckBox.isSelected());
    }

    if (myStoreExternallyCheckBox != null) {
      settings.setStoreProjectFilesExternally(myStoreExternallyCheckBox.isSelected());
    }

    if (myUseLocalDistributionButton != null && myUseLocalDistributionButton.isSelected()) {
      settings.setDistributionType(DistributionType.LOCAL);
    }
    else if (myUseWrapperButton != null && myUseWrapperButton.isSelected()) {
      settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    }
    else if ((myUseWrapperWithVerificationButton != null && myUseWrapperWithVerificationButton.isSelected()) ||
             (myUseBundledDistributionButton != null && myUseBundledDistributionButton.isSelected())) {
      settings.setDistributionType(DistributionType.WRAPPED);
    }
  }

  @Override
  public boolean isModified() {
    DistributionType distributionType = myInitialSettings.getDistributionType();
    if (myUseBundledDistributionButton != null &&
        myUseBundledDistributionButton.isSelected() &&
        distributionType != DistributionType.BUNDLED) {
      return true;
    }

    if (myUseWrapperButton != null && myUseWrapperButton.isSelected() && distributionType != DistributionType.DEFAULT_WRAPPED) {
      return true;
    }

    if (myUseWrapperWithVerificationButton != null &&
        myUseWrapperWithVerificationButton.isSelected() &&
        distributionType != DistributionType.WRAPPED) {
      return true;
    }

    if (myUseLocalDistributionButton != null && myUseLocalDistributionButton.isSelected() && distributionType != DistributionType.LOCAL) {
      return true;
    }

    if (myResolveModulePerSourceSetCheckBox != null &&
        (myResolveModulePerSourceSetCheckBox.isSelected() != myInitialSettings.isResolveModulePerSourceSet())) {
      return true;
    }

    if (myStoreExternallyCheckBox != null && myStoreExternallyCheckBox.isSelected() != myInitialSettings.isStoreProjectFilesExternally()) {
      return true;
    }

    if (myGradleJdkComboBox != null && !StringUtil.equals(myGradleJdkComboBox.getSelectedValue(), myInitialSettings.getGradleJvm())) {
      return true;
    }

    if (myGradleHomePathField == null) return false;
    String gradleHome = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
    if (StringUtil.isEmpty(gradleHome)) {
      return !StringUtil.isEmpty(myInitialSettings.getGradleHome());
    }
    else {
      return !gradleHome.equals(myInitialSettings.getGradleHome());
    }
  }

  @Override
  public void reset(@Nullable Project project, GradleProjectSettings settings, boolean isDefaultModuleCreation) {
    reset(project, settings, isDefaultModuleCreation, null);
  }

  @Override
  public void reset(@Nullable Project project,
                    GradleProjectSettings settings,
                    boolean isDefaultModuleCreation,
                    @Nullable WizardContext wizardContext) {
    String gradleHome = settings.getGradleHome();
    if (myGradleHomePathField != null) {
      myGradleHomePathField.setText(gradleHome == null ? "" : gradleHome);
      myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
    }
    if (myResolveModulePerSourceSetCheckBox != null) {
      myResolveModulePerSourceSetCheckBox.setSelected(settings.isResolveModulePerSourceSet());
    }
    if (myStoreExternallyCheckBox != null) {
      myStoreExternallyCheckBox.setSelected(settings.isStoreProjectFilesExternally());
    }

    resetGradleJdkComboBox(project, settings, wizardContext);
    resetWrapperControls(settings.getExternalProjectPath(), settings, isDefaultModuleCreation);

    if (myUseLocalDistributionButton != null && !myUseLocalDistributionButton.isSelected()) {
      myGradleHomePathField.setEnabled(false);
      return;
    }

    if (StringUtil.isEmpty(gradleHome)) {
      myGradleHomeSettingType = LocationSettingType.UNKNOWN;
      deduceGradleHomeIfPossible();
    }
    else {
      myGradleHomeSettingType = myInstallationManager.isGradleSdkHome(new File(gradleHome)) ?
                                LocationSettingType.EXPLICIT_CORRECT :
                                LocationSettingType.EXPLICIT_INCORRECT;
      myAlarm.cancelAllRequests();
      if (myGradleHomeSettingType == LocationSettingType.EXPLICIT_INCORRECT &&
          settings.getDistributionType() == DistributionType.LOCAL) {
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
      }
    }
  }

  @Override
  public void update(String linkedProjectPath, GradleProjectSettings settings, boolean isDefaultModuleCreation) {
    resetWrapperControls(linkedProjectPath, settings, isDefaultModuleCreation);
    if (myResolveModulePerSourceSetCheckBox != null) {
      myResolveModulePerSourceSetCheckBox.setSelected(settings.isResolveModulePerSourceSet());
    }
  }

  @Override
  public IdeaGradleProjectSettingsControlBuilder addGradleHomeComponents(PaintAwarePanel content, int indentLevel) {
    if(dropGradleHomePathComponents) return this;

    myGradleHomeLabel = new JBLabel(GradleBundle.message("gradle.settings.text.home.path"));
    myGradleHomePathField = new TextFieldWithBrowseButton();

    myGradleHomePathField.addBrowseFolderListener("", GradleBundle.message("gradle.settings.text.home.path"), null,
                                                  GradleUtil.getGradleHomeFileChooserDescriptor(),
                                                  TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    myGradleHomePathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    });

    content.add(myGradleHomeLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    content.add(myGradleHomePathField, ExternalSystemUiUtil.getFillLineConstraints(0));

    return this;
  }

  private void resetGradleJdkComboBox(@Nullable final Project project,
                                      GradleProjectSettings settings,
                                      @Nullable WizardContext wizardContext) {
    if (myGradleJdkComboBox == null) return;

    final String gradleJvm = settings.getGradleJvm();
    myGradleJdkComboBox.setProject(project);

    Sdk projectJdk = wizardContext != null ? wizardContext.getProjectJdk() : null;
    final String sdkItem = ObjectUtils.nullizeByCondition(gradleJvm, s ->
      (projectJdk == null && project == null && StringUtil.equals(USE_PROJECT_JDK, s)) || StringUtil.isEmpty(s));

    myGradleJdkComboBox.refreshData(sdkItem, projectJdk);
    if (myGradleJdkSetUpButton != null) {
      ProjectSdksModel sdksModel = ProjectStructureConfigurable.getInstance(
        project == null || project.isDisposed() ? ProjectManager.getInstance().getDefaultProject() : project).getProjectJdksModel();
      myGradleJdkComboBox.setSetupButton(myGradleJdkSetUpButton, sdksModel, null, id -> id instanceof JavaSdk);
    }
  }

  private void resetWrapperControls(String linkedProjectPath, @NotNull GradleProjectSettings settings, boolean isDefaultModuleCreation) {
    if (isDefaultModuleCreation) {
      JComponent[] toRemove = new JComponent[]{myUseWrapperWithVerificationButton, myUseWrapperVerificationLabel};
      for (JComponent component : toRemove) {
        if (component != null) {
          Container parent = component.getParent();
          if (parent != null) {
            parent.remove(component);
          }
        }
      }
      myUseWrapperWithVerificationButton = null;
      myUseWrapperVerificationLabel = null;
    }

    if (StringUtil.isEmpty(linkedProjectPath) && !isDefaultModuleCreation) {
      if (myUseLocalDistributionButton != null) {
        myUseLocalDistributionButton.setSelected(true);
      }
      if (myGradleHomePathField != null) {
        myGradleHomePathField.setEnabled(true);
      }
      return;
    }

    final boolean isGradleDefaultWrapperFilesExist = GradleUtil.isGradleDefaultWrapperFilesExist(linkedProjectPath);
    if (myUseWrapperButton != null && (isGradleDefaultWrapperFilesExist || isDefaultModuleCreation)) {
      myUseWrapperButton.setEnabled(true);
      myUseWrapperButton.setSelected(true);
      if (myGradleHomePathField != null) {
        myGradleHomePathField.setEnabled(false);
      }
      myUseWrapperButton.setText(GradleBundle.message("gradle.settings.text.use.default_wrapper.configured"));
    }
    else {
      if (myUseWrapperButton != null) {
        myUseWrapperButton.setEnabled(false);
        myUseWrapperButton.setText(GradleBundle.message("gradle.settings.text.use.default_wrapper.not_configured"));
      }
      if (myUseLocalDistributionButton != null) {
        myUseLocalDistributionButton.setSelected(true);
      }
      if (myGradleHomePathField != null) {
        myGradleHomePathField.setEnabled(true);
      }
    }

    if (settings.getDistributionType() == null) {
      return;
    }

    switch (settings.getDistributionType()) {
      case LOCAL:
        if (myGradleHomePathField != null) {
          myGradleHomePathField.setEnabled(true);
        }
        if (myUseLocalDistributionButton != null) {
          myUseLocalDistributionButton.setSelected(true);
        }
        break;
      case DEFAULT_WRAPPED:
        if (isGradleDefaultWrapperFilesExist) {
          if (myGradleHomePathField != null) {
            myGradleHomePathField.setEnabled(false);
          }
          if (myUseWrapperButton != null) {
            myUseWrapperButton.setSelected(true);
            myUseWrapperButton.setEnabled(true);
          }
        }
        break;
      case WRAPPED:
        if (myGradleHomePathField != null) {
          myGradleHomePathField.setEnabled(false);
        }
        if (myUseWrapperWithVerificationButton != null) {
          myUseWrapperWithVerificationButton.setSelected(true);
        }
        break;
      case BUNDLED:
        if (myGradleHomePathField != null) {
          myGradleHomePathField.setEnabled(false);
        }
        if (myUseBundledDistributionButton != null) {
          myUseBundledDistributionButton.setSelected(true);
        }
        break;
    }
  }

  void showBalloonIfNecessary() {
    if (!myShowBalloonIfNecessary || (myGradleHomePathField != null && !myGradleHomePathField.isEnabled())) {
      return;
    }
    myShowBalloonIfNecessary = false;
    MessageType messageType = null;
    switch (myGradleHomeSettingType) {
      case DEDUCED:
        messageType = MessageType.INFO;
        break;
      case EXPLICIT_INCORRECT:
      case UNKNOWN:
        messageType = MessageType.ERROR;
        break;
      default:
    }
    if (messageType != null) {
      new DelayedBalloonInfo(messageType, myGradleHomeSettingType, BALLOON_DELAY_MILLIS).run();
    }
  }

  private class DelayedBalloonInfo implements Runnable {
    private final MessageType myMessageType;
    private final String myText;
    private final long myTriggerTime;

    DelayedBalloonInfo(@NotNull MessageType messageType, @NotNull LocationSettingType settingType, long delayMillis) {
      myMessageType = messageType;
      myText = settingType.getDescription(GradleConstants.SYSTEM_ID);
      myTriggerTime = System.currentTimeMillis() + delayMillis;
    }

    @Override
    public void run() {
      long diff = myTriggerTime - System.currentTimeMillis();
      if (diff > 0) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this, diff);
        return;
      }
      if (myGradleHomePathField == null || !myGradleHomePathField.isShowing()) {
        // Don't schedule the balloon if the configurable is hidden.
        return;
      }
      ExternalSystemUiUtil.showBalloon(myGradleHomePathField, myMessageType, myText);
    }
  }
}
