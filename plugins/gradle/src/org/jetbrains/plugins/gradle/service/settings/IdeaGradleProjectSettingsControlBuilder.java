// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.settings.LocationSettingType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ui.configuration.SdkComboBox;
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import one.util.streamex.StreamEx;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.target.GradleRuntimeTargetUI;
import org.jetbrains.plugins.gradle.execution.target.TargetPathFieldWithBrowseButton;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.TestRunner;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil.INSETS;
import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createUniqueSdkName;
import static com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel.createJdkComboBoxModel;
import static com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo;
import static org.jetbrains.plugins.gradle.util.GradleJvmComboBoxUtil.*;
import static org.jetbrains.plugins.gradle.util.GradleJvmResolutionUtil.getGradleJvmLookupProvider;
import static org.jetbrains.plugins.gradle.util.GradleJvmUtil.nonblockingResolveGradleJvmInfo;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("FieldCanBeLocal") // Used implicitly by reflection at disposeUIResources() and showUi()
public class IdeaGradleProjectSettingsControlBuilder implements GradleProjectSettingsControlBuilder {
  private static final Logger LOG = Logger.getInstance(IdeaGradleProjectSettingsControlBuilder.class);

  private static final long BALLOON_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);
  private static final String HIDDEN_KEY = "hidden";

  private final @NotNull GradleProjectSettings myInitialSettings;

  private final @NotNull Alarm myAlarm = new Alarm();

  /**
   * The target {@link Project} reference of the UI control.
   * It can be the current project of the settings UI configurable (see {@link GradleConfigurable}),
   * or the target project from the wizard context.
   */
  private final @NotNull Ref<Project> myProjectRef = Ref.create();
  private final @NotNull Disposable myProjectRefDisposable = () -> myProjectRef.set(null);

  private boolean myShowBalloonIfNecessary;
  private boolean dropGradleJdkComponents;
  private boolean dropUseWrapperButton;
  private boolean dropCustomizableWrapperButton;
  private boolean dropUseLocalDistributionButton;
  private boolean dropUseBundledDistributionButton;
  private boolean dropResolveModulePerSourceSetCheckBox;
  private boolean dropResolveExternalAnnotationsCheckBox;
  private boolean dropDelegateBuildCombobox;
  private boolean dropTestRunnerCombobox;

  private @Nullable JComboBox<DistributionTypeItem> myGradleDistributionComboBox;
  private @Nullable JBLabel myGradleDistributionHint;
  private @NotNull LocationSettingType myGradleHomeSettingType = LocationSettingType.UNKNOWN;
  private @Nullable TargetPathFieldWithBrowseButton myGradleHomePathField;
  @SuppressWarnings({"unused", "RedundantSuppression"}) // used by ExternalSystemUiUtil.showUi to show/hide the component via reflection
  private @Nullable JPanel myGradlePanel;
  private @Nullable JLabel myGradleJdkLabel;
  private @Nullable SdkComboBox myGradleJdkComboBox;
  private @Nullable JPanel myGradleJdkComboBoxWrapper;
  @SuppressWarnings({"unused", "RedundantSuppression"}) // used by ExternalSystemUiUtil.showUi to show/hide the component via reflection
  private @Nullable JPanel myImportPanel;
  private @Nullable JPanel myModulePerSourceSetPanel;
  private @Nullable JBCheckBox myResolveModulePerSourceSetCheckBox;
  private @Nullable JBCheckBox myResolveExternalAnnotationsCheckBox;
  private @Nullable JLabel myDelegateBuildLabel;

  private @Nullable ComboBox<BuildRunItem> myDelegateBuildCombobox;
  private @Nullable JLabel myTestRunnerLabel;
  private @Nullable ComboBox<TestRunnerItem> myTestRunnerCombobox;
  private @Nullable JPanel myDelegatePanel;

  public IdeaGradleProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings) {
    myInitialSettings = initialSettings;
  }

  public IdeaGradleProjectSettingsControlBuilder dropGradleJdkComponents() {
    dropGradleJdkComponents = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropUseWrapperButton() {
    dropUseWrapperButton = true;
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

  /**
   * @deprecated Auto-import cannot be disabled
   * @see com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
   */
  @Deprecated(forRemoval = true)
  public IdeaGradleProjectSettingsControlBuilder dropUseAutoImportBox() {
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropResolveModulePerSourceSetCheckBox() {
    dropResolveModulePerSourceSetCheckBox = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropResolveExternalAnnotationsCheckBox() {
    dropResolveExternalAnnotationsCheckBox = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropDelegateBuildCombobox() {
    dropDelegateBuildCombobox = true;
    return this;
  }

  public IdeaGradleProjectSettingsControlBuilder dropTestRunnerCombobox() {
    dropTestRunnerCombobox = true;
    return this;
  }

  @Override
  public void showUi(boolean show) {
    ExternalSystemUiUtil.showUi(this, show);

    if (show) {
      // some controls need to remain hidden depending on the selection
      // also error notifications should be shown
      updateDistributionComponents();
      updateDeprecatedControls();
    }
  }

  @Override
  @NotNull
  public GradleProjectSettings getInitialSettings() {
    return myInitialSettings;
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

    addImportComponents(content, indentLevel);
    addDelegationComponents(content, indentLevel);
    addGradleComponents(content, indentLevel);
  }

  private void addImportComponents(PaintAwarePanel content, int indentLevel) {
    myImportPanel = addComponentsGroup(null, content, indentLevel, panel -> {
      if (!dropResolveModulePerSourceSetCheckBox) {
        myModulePerSourceSetPanel = new JPanel(new GridBagLayout());
        panel.add(myModulePerSourceSetPanel, ExternalSystemUiUtil.getFillLineConstraints(0).insets(0, 0, 0, 0));

        myModulePerSourceSetPanel.add(
          myResolveModulePerSourceSetCheckBox = new JBCheckBox(GradleBundle.message("gradle.settings.text.module.per.source.set",
                                                                                    getIDEName())),
          ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

        JBLabel myResolveModulePerSourceSetHintLabel = new JBLabel(
          XmlStringUtil.wrapInHtml(GradleBundle.message("gradle.settings.text.module.per.source.set.hint")),
          UIUtil.ComponentStyle.SMALL);
        myResolveModulePerSourceSetHintLabel.setIcon(AllIcons.General.BalloonWarning12);
        myResolveModulePerSourceSetHintLabel.setVerticalTextPosition(SwingConstants.TOP);
        myResolveModulePerSourceSetHintLabel.setForeground(UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER));

        GridBag constraints = ExternalSystemUiUtil.getFillLineConstraints(indentLevel);
        constraints.insets.top = 0;
        constraints.insets.left += UIUtil.getCheckBoxTextHorizontalOffset(myResolveModulePerSourceSetCheckBox);
        myModulePerSourceSetPanel.add(myResolveModulePerSourceSetHintLabel, constraints);
      }

      if (!dropResolveExternalAnnotationsCheckBox) {
        panel.add(
          myResolveExternalAnnotationsCheckBox = new JBCheckBox(GradleBundle.message("gradle.settings.text.download.annotations")),
          ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
      }
    });
  }

  @Override
  public void disposeUIResources() {
    ExternalSystemUiUtil.disposeUi(this);
    Disposer.dispose(myAlarm);
  }

  /**
   * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
   */
  private void deduceGradleHomeIfPossible() {
    if (myGradleHomePathField == null) return;

    File gradleHome = GradleInstallationManager.getInstance().getAutodetectedGradleHome(myProjectRef.get());
    if (gradleHome == null) {
      new DelayedBalloonInfo(MessageType.WARNING, LocationSettingType.UNKNOWN, BALLOON_DELAY_MILLIS).run();
      return;
    }
    myGradleHomeSettingType = LocationSettingType.DEDUCED;
    new DelayedBalloonInfo(MessageType.INFO, LocationSettingType.DEDUCED, BALLOON_DELAY_MILLIS).run();
    myGradleHomePathField.setText(gradleHome.getPath());
    myGradleHomePathField.getTextField().setForeground(LocationSettingType.DEDUCED.getColor());
  }

  private void addGradleComponents(PaintAwarePanel content, int indentLevel) {
    myGradlePanel = addComponentsGroup(GradleConstants.GRADLE_NAME, content, indentLevel, panel -> { //NON-NLS GRADLE_NAME
      addGradleChooserComponents(panel, indentLevel + 1);
      addGradleJdkComponents(panel, indentLevel + 1);
    });
  }

  @Override
  public IdeaGradleProjectSettingsControlBuilder addGradleJdkComponents(JPanel content, int indentLevel) {
    if (!dropGradleJdkComponents) {
      Project project = ProjectManager.getInstance().getDefaultProject();
      myGradleJdkLabel = new JBLabel(GradleBundle.message("gradle.settings.text.jvm.path"));
      myGradleJdkComboBoxWrapper = new JPanel(new BorderLayout());
      recreateGradleJdkComboBox(project, new ProjectSdksModel());

      myGradleJdkLabel.setLabelFor(myGradleJdkComboBoxWrapper);

      content.add(myGradleJdkLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
      content.add(myGradleJdkComboBoxWrapper, ExternalSystemUiUtil.getFillLineConstraints(0));
    }
    return this;
  }

  @Override
  public IdeaGradleProjectSettingsControlBuilder addGradleChooserComponents(JPanel content, int indentLevel) {
    ArrayList<DistributionTypeItem> availableDistributions = new ArrayList<>();

    if (!dropUseWrapperButton) availableDistributions.add(new DistributionTypeItem(DistributionType.DEFAULT_WRAPPED));
    if (!dropCustomizableWrapperButton) availableDistributions.add(new DistributionTypeItem(DistributionType.WRAPPED));
    if (!dropUseLocalDistributionButton) availableDistributions.add(new DistributionTypeItem(DistributionType.LOCAL));
    if (!dropUseBundledDistributionButton) availableDistributions.add(new DistributionTypeItem(DistributionType.BUNDLED));

    myGradleDistributionComboBox = new ComboBox<>();
    myGradleDistributionComboBox.setRenderer(new MyItemCellRenderer<>());

    myGradleDistributionHint = new JBLabel();
    myGradleHomePathField = new TargetPathFieldWithBrowseButton();
    myGradleDistributionHint.setLabelFor(myGradleHomePathField);

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

    myGradleDistributionComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDistributionComponents();
      }
    });

    myGradleDistributionComboBox.setModel(new CollectionComboBoxModel<>(availableDistributions));
    if (!availableDistributions.isEmpty()) {
      content.add(new JBLabel(GradleBundle.message("gradle.project.settings.distribution")),
                  ExternalSystemUiUtil.getLabelConstraints(indentLevel));
      content.add(myGradleDistributionComboBox, ExternalSystemUiUtil.getLabelConstraints(0));

      JPanel additionalControlsPanel = new JPanel(new GridBagLayout());
      additionalControlsPanel.add(myGradleDistributionHint);
      additionalControlsPanel.add(myGradleHomePathField, ExternalSystemUiUtil.getFillLineConstraints(0));
      content.add(additionalControlsPanel, ExternalSystemUiUtil.getFillLineConstraints(0).insets(0, 0, 0, 0));

      // adjust the combobox height to match the height of the editor and Gradle GDK combobox.
      // - without setting the prefered size, it's resized when path component is shown/hidden
      // - without adjusting the height the combobox is a little smaller then the next combobox (Gradle JVM)
      boolean macTheme = UIUtil.isUnderDefaultMacTheme();
      myGradleDistributionComboBox.setPreferredSize(new Dimension(myGradleDistributionComboBox.getPreferredSize().width,
                                                                  myGradleHomePathField.getPreferredSize().height + (macTheme ? 3 : 0)));
    }

    return this;
  }

  private void updateDistributionComponents() {
    if (myGradleDistributionComboBox == null) return;
    if (myGradleHomePathField == null) return;

    boolean localEnabled = getSelectedGradleDistribution() == DistributionType.LOCAL;
    boolean wrapperSelected = getSelectedGradleDistribution() == DistributionType.DEFAULT_WRAPPED;

    myGradleHomePathField.setEnabled(localEnabled);
    myGradleHomePathField.setVisible(localEnabled);

    if (myGradleDistributionHint != null) {
      myGradleDistributionHint.setEnabled(wrapperSelected);
      myGradleDistributionHint.setVisible(wrapperSelected);
    }

    if (localEnabled) {
      if (myGradleHomePathField.getText().isEmpty()) {
        deduceGradleHomeIfPossible();
      }
      else {
        Project project = myProjectRef.get();
        if (GradleInstallationManager.getInstance().isGradleSdkHome(project, myGradleHomePathField.getText())) {
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

  @Nullable
  private DistributionType getSelectedGradleDistribution() {
    if (myGradleDistributionComboBox == null) return null;
    Object selection = myGradleDistributionComboBox.getSelectedItem();
    return selection == null ? null : ((DistributionTypeItem)selection).value;
  }

  @Override
  public boolean validate(GradleProjectSettings settings) throws ConfigurationException {
    if (myGradleJdkComboBox != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      SdkInfo sdkInfo = getSelectedGradleJvmInfo(myGradleJdkComboBox);
      if (sdkInfo instanceof SdkInfo.Undefined) {
        throw new ConfigurationException(GradleBundle.message("gradle.jvm.undefined"));
      }
      if (sdkInfo instanceof SdkInfo.Resolved) {
        String homePath = ((SdkInfo.Resolved)sdkInfo).getHomePath();
        if (!ExternalSystemJdkUtil.isValidJdk(homePath)) {
          throw new ConfigurationException(GradleBundle.message("gradle.jvm.incorrect", homePath));
        }
      }
    }

    if (myGradleHomePathField != null && getSelectedGradleDistribution() == DistributionType.LOCAL) {
      String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
      if (StringUtil.isEmpty(gradleHomePath)) {
        myGradleHomeSettingType = LocationSettingType.UNKNOWN;
        throw new ConfigurationException(GradleBundle.message("gradle.home.setting.type.explicit.empty", gradleHomePath));
      }
      else if (!GradleInstallationManager.getInstance().isGradleSdkHome(myProjectRef.get(), new File(gradleHomePath))) {
        myGradleHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT;
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
        throw new ConfigurationException(GradleBundle.message("gradle.home.setting.type.explicit.incorrect", gradleHomePath));
      }
    }
    return true;
  }

  private @NotNull SdkLookupProvider getSdkLookupProvider(@NotNull Project project) {
    return getGradleJvmLookupProvider(project, myInitialSettings);
  }

  private @NotNull SdkInfo getSelectedGradleJvmInfo(@NotNull SdkComboBox comboBox) {
    Project project = comboBox.getModel().getProject();
    SdkLookupProvider sdkLookupProvider = getSdkLookupProvider(project);
    String externalProjectPath = myInitialSettings.getExternalProjectPath();
    Sdk projectSdk = comboBox.getModel().getSdksModel().getProjectSdk();
    String gradleJvm = getSelectedGradleJvmReference(comboBox, sdkLookupProvider);
    return nonblockingResolveGradleJvmInfo(sdkLookupProvider, project, projectSdk, externalProjectPath, gradleJvm);
  }

  @Override
  public void apply(GradleProjectSettings settings) {
    settings.setCompositeBuild(myInitialSettings.getCompositeBuild());
    if (myGradleHomePathField != null) {
      String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
      File gradleHomeFile = new File(gradleHomePath);
      String finalGradleHomePath;
      if (GradleInstallationManager.getInstance().isGradleSdkHome(myProjectRef.get(), gradleHomeFile)) {
        finalGradleHomePath = gradleHomePath;
      }
      else {
        finalGradleHomePath = GradleInstallationManager.getInstance().suggestBetterGradleHomePath(myProjectRef.get(), gradleHomePath);
        if (finalGradleHomePath != null) {
          SwingUtilities.invokeLater(() -> {
            myGradleHomePathField.setText(finalGradleHomePath);
          });
        }
      }
      if (StringUtil.isEmpty(finalGradleHomePath)) {
        settings.setGradleHome(null);
      }
      else {
        settings.setGradleHome(finalGradleHomePath);
        GradleUtil.storeLastUsedGradleHome(finalGradleHomePath);
      }
    }

    if (myGradleJdkComboBox != null) {
      wrapExceptions(() -> myGradleJdkComboBox.getModel().getSdksModel().apply());
      SdkLookupProvider sdkLookupProvider = getSdkLookupProvider(myGradleJdkComboBox.getModel().getProject());
      String gradleJvm = getSelectedGradleJvmReference(myGradleJdkComboBox, sdkLookupProvider);
      settings.setGradleJvm(StringUtil.isEmpty(gradleJvm) ? null : gradleJvm);
    }

    if (myResolveModulePerSourceSetCheckBox != null) {
      settings.setResolveModulePerSourceSet(myResolveModulePerSourceSetCheckBox.isSelected());
    }

    if (myResolveExternalAnnotationsCheckBox != null) {
      settings.setResolveExternalAnnotations(myResolveExternalAnnotationsCheckBox.isSelected());
    }

    if (myGradleDistributionComboBox != null) {
      Object selected = myGradleDistributionComboBox.getSelectedItem();
      if (selected instanceof DistributionTypeItem) {
        settings.setDistributionType(((DistributionTypeItem)selected).value);
      }
    }

    if (myDelegateBuildCombobox != null) {
      Object delegateBuildSelectedItem = myDelegateBuildCombobox.getSelectedItem();
      if (delegateBuildSelectedItem instanceof BuildRunItem) {
        settings.setDelegatedBuild(ObjectUtils.notNull(((BuildRunItem)delegateBuildSelectedItem).value,
                                                       GradleProjectSettings.DEFAULT_DELEGATE));
      }
    }
    if (myTestRunnerCombobox != null) {
      Object testRunnerSelectedItem = myTestRunnerCombobox.getSelectedItem();
      if (testRunnerSelectedItem instanceof TestRunnerItem) {
        settings.setTestRunner(ObjectUtils.notNull(((TestRunnerItem)testRunnerSelectedItem).value,
                                                   GradleProjectSettings.DEFAULT_TEST_RUNNER));
      }
    }
  }

  @Override
  public boolean isModified() {
    if (myGradleDistributionComboBox != null && myGradleDistributionComboBox.getSelectedItem() instanceof DistributionTypeItem
        && ((DistributionTypeItem)myGradleDistributionComboBox.getSelectedItem()).value != myInitialSettings.getDistributionType()) {
      return true;
    }

    if (myResolveModulePerSourceSetCheckBox != null &&
        (myResolveModulePerSourceSetCheckBox.isSelected() != myInitialSettings.isResolveModulePerSourceSet())) {
      return true;
    }

    if (myResolveExternalAnnotationsCheckBox != null &&
        (myResolveExternalAnnotationsCheckBox.isSelected() != myInitialSettings.isResolveExternalAnnotations())) {
      return true;
    }

    if (myDelegateBuildCombobox != null && myDelegateBuildCombobox.getSelectedItem() instanceof MyItem
        && !Objects.equals(((MyItem<?>)myDelegateBuildCombobox.getSelectedItem()).value, myInitialSettings.getDelegatedBuild())) {
      return true;
    }

    if (myTestRunnerCombobox != null && myTestRunnerCombobox.getSelectedItem() instanceof MyItem
        && !Objects.equals(((MyItem<?>)myTestRunnerCombobox.getSelectedItem()).value, myInitialSettings.getTestRunner())) {
      return true;
    }

    if (myGradleJdkComboBox != null) {
      SdkLookupProvider sdkLookupProvider = getSdkLookupProvider(myGradleJdkComboBox.getModel().getProject());
      String gradleJvm = getSelectedGradleJvmReference(myGradleJdkComboBox, sdkLookupProvider);
      if (!StringUtil.equals(gradleJvm, myInitialSettings.getGradleJvm())) {
        return true;
      }
      if (myGradleJdkComboBox.getModel().getSdksModel().isModified()) {
        return true;
      }
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
    updateProjectRef(project, wizardContext);

    String gradleHome = settings.getGradleHome();
    if (myGradleHomePathField != null) {
      GradleRuntimeTargetUI.installActionListener(myGradleHomePathField, myProjectRef.get(),
                                                  GradleBundle.message("gradle.settings.text.home.path"));
      myGradleHomePathField.setText(gradleHome == null ? "" : gradleHome);
      myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
    }
    resetImportControls(settings);

    resetGradleJdkComboBox(project, settings, wizardContext);
    resetWrapperControls(settings.getExternalProjectPath(), settings, isDefaultModuleCreation);
    resetGradleDelegationControls(wizardContext);

    if (StringUtil.isEmpty(gradleHome)) {
      myGradleHomeSettingType = LocationSettingType.UNKNOWN;
      deduceGradleHomeIfPossible();
    }
    else {
      File gradleHomeFile = new File(gradleHome);
      if (GradleInstallationManager.getInstance().isGradleSdkHome(project, gradleHomeFile)) {
        myGradleHomeSettingType = LocationSettingType.EXPLICIT_CORRECT;
      }
      else {
        myGradleHomeSettingType = GradleInstallationManager.getInstance().suggestBetterGradleHomePath(project, gradleHome) != null
                                  ? LocationSettingType.EXPLICIT_CORRECT
                                  : LocationSettingType.EXPLICIT_INCORRECT;
      }
      myAlarm.cancelAllRequests();
      if (myGradleHomeSettingType == LocationSettingType.EXPLICIT_INCORRECT &&
          settings.getDistributionType() == DistributionType.LOCAL) {
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
      }
    }
    updateDeprecatedControls();
  }

  @Override
  public void update(String linkedProjectPath, GradleProjectSettings settings, boolean isDefaultModuleCreation) {
    resetWrapperControls(linkedProjectPath, settings, isDefaultModuleCreation);
    resetImportControls(settings);
    updateDeprecatedControls();
  }

  private void resetImportControls(GradleProjectSettings settings) {
    if (myResolveModulePerSourceSetCheckBox != null) {
      myResolveModulePerSourceSetCheckBox.setSelected(settings.isResolveModulePerSourceSet());
      boolean showSetting = !settings.isResolveModulePerSourceSet()
                            || Registry.is("gradle.settings.showDeprecatedSettings", false);
      myModulePerSourceSetPanel.putClientProperty(HIDDEN_KEY, showSetting);
    }
    if (myResolveExternalAnnotationsCheckBox != null) {
      myResolveExternalAnnotationsCheckBox.setSelected(settings.isResolveExternalAnnotations());
    }
  }

  private void updateDeprecatedControls() {
    if (myModulePerSourceSetPanel != null) {
      myModulePerSourceSetPanel.setVisible(myModulePerSourceSetPanel.getClientProperty(HIDDEN_KEY) == Boolean.TRUE);
    }
  }

  protected void resetGradleJdkComboBox(@Nullable final Project project,
                                        GradleProjectSettings settings,
                                        @Nullable WizardContext wizardContext) {
    ProjectSdksModel sdksModel = new ProjectSdksModel();
    resetGradleJdkComboBox(project, settings, wizardContext, sdksModel);
  }

  protected final void resetGradleJdkComboBox(
    @Nullable Project project,
    @NotNull GradleProjectSettings settings,
    @Nullable WizardContext wizardContext,
    @NotNull ProjectSdksModel sdksModel
  ) {
    if (myGradleJdkComboBox == null) return;
    project = project == null || project.isDisposed() ? ProjectManager.getInstance().getDefaultProject() : project;
    Sdk projectSdk = wizardContext != null ? wizardContext.getProjectJdk() : null;
    setupProjectSdksModel(sdksModel, project, projectSdk);
    recreateGradleJdkComboBox(project, sdksModel);

    SdkLookupProvider sdkLookupProvider = getSdkLookupProvider(project);
    String externalProjectPath = myInitialSettings.getExternalProjectPath();
    addUsefulGradleJvmReferences(myGradleJdkComboBox, externalProjectPath);
    setSelectedGradleJvmReference(myGradleJdkComboBox, sdkLookupProvider, externalProjectPath, settings.getGradleJvm());
  }

  private void recreateGradleJdkComboBox(@NotNull Project project, @NotNull ProjectSdksModel sdksModel) {
    if (myGradleJdkComboBoxWrapper != null) {
      if (myGradleJdkComboBox != null) {
        myGradleJdkComboBoxWrapper.remove(myGradleJdkComboBox);
      }
      myGradleJdkComboBox = new SdkComboBox(createJdkComboBoxModel(project, sdksModel));
      myGradleJdkComboBoxWrapper.add(myGradleJdkComboBox, BorderLayout.CENTER);
    }
  }

  private void resetWrapperControls(String linkedProjectPath, @NotNull GradleProjectSettings settings, boolean isDefaultModuleCreation) {
    if (myGradleDistributionComboBox == null) return;

    if (isDefaultModuleCreation) {
      DistributionTypeItem toRemove = new DistributionTypeItem(DistributionType.WRAPPED);
      ((CollectionComboBoxModel<DistributionTypeItem>)myGradleDistributionComboBox.getModel()).remove(toRemove);
    }

    if (StringUtil.isEmpty(linkedProjectPath) && !isDefaultModuleCreation) {
      myGradleDistributionComboBox.setSelectedItem(new DistributionTypeItem(DistributionType.LOCAL));
      return;
    }

    if (myGradleDistributionHint != null && !dropUseWrapperButton) {
      final boolean isGradleDefaultWrapperFilesExist = GradleUtil.isGradleDefaultWrapperFilesExist(linkedProjectPath);
      boolean showError = !isGradleDefaultWrapperFilesExist && !isDefaultModuleCreation;
      myGradleDistributionHint.setText(showError ? GradleBundle.message("gradle.settings.wrapper.not.found") : null);
      myGradleDistributionHint.setIcon(showError ? AllIcons.General.Error : null);
    }

    if (settings.getDistributionType() == null) {
      if (myGradleDistributionComboBox.getItemCount() > 0) {
        myGradleDistributionComboBox.setSelectedIndex(0);
      }
    }
    else {
      myGradleDistributionComboBox.setSelectedItem(new DistributionTypeItem(settings.getDistributionType()));
    }
  }

  private void addDelegationComponents(PaintAwarePanel content, int indentLevel) {
    myDelegatePanel = addComponentsGroup(GradleBundle.message("gradle.settings.text.build.run.title"), content, indentLevel, panel -> {
      if (dropDelegateBuildCombobox && dropTestRunnerCombobox) return;

      JBLabel label = new JBLabel(
        XmlStringUtil.wrapInHtml(GradleBundle.message("gradle.settings.text.build.run.hint", getIDEName())),
        UIUtil.ComponentStyle.SMALL);
      label.setForeground(UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER));

      GridBag constraints = ExternalSystemUiUtil.getFillLineConstraints(indentLevel + 1);
      constraints.insets.bottom = UIUtil.LARGE_VGAP;
      panel.add(label, constraints);

      if (!dropDelegateBuildCombobox) {
        BuildRunItem[] states = new BuildRunItem[]{new BuildRunItem(Boolean.TRUE), new BuildRunItem(Boolean.FALSE)};
        myDelegateBuildCombobox = new ComboBox<>(states);
        myDelegateBuildCombobox.setRenderer(new MyItemCellRenderer<>());
        myDelegateBuildCombobox.setSelectedItem(new BuildRunItem(myInitialSettings.getDelegatedBuild()));

        myDelegateBuildLabel = new JBLabel(GradleBundle.message("gradle.settings.text.build.run"));
        panel.add(myDelegateBuildLabel, getLabelConstraints(indentLevel + 1));
        panel.add(myDelegateBuildCombobox, getLabelConstraints(0));
        panel.add(Box.createGlue(), ExternalSystemUiUtil.getFillLineConstraints(indentLevel + 1));

        myDelegateBuildLabel.setLabelFor(myDelegateBuildCombobox);
      }
      if (!dropTestRunnerCombobox) {
        TestRunnerItem[] testRunners = StreamEx.of(TestRunner.values()).map(TestRunnerItem::new).toArray(TestRunnerItem[]::new);
        myTestRunnerCombobox = new ComboBox<>(testRunners);
        myTestRunnerCombobox.setRenderer(new MyItemCellRenderer<>());
        myTestRunnerCombobox.setSelectedItem(new TestRunnerItem(myInitialSettings.getTestRunner()));

        // make sure that the two adjacent comboboxes have same size
        myTestRunnerCombobox.setPrototypeDisplayValue(new TestRunnerItem(TestRunner.CHOOSE_PER_TEST));
        if (myDelegateBuildCombobox != null) {
          myDelegateBuildCombobox.setPreferredSize(myTestRunnerCombobox.getPreferredSize());
        }

        myTestRunnerLabel = new JBLabel(GradleBundle.message("gradle.settings.text.run.tests"));
        panel.add(myTestRunnerLabel, getLabelConstraints(indentLevel + 1));
        panel.add(myTestRunnerCombobox, getLabelConstraints(0));
        panel.add(Box.createGlue(), ExternalSystemUiUtil.getFillLineConstraints(indentLevel + 1));

        myTestRunnerLabel.setLabelFor(myTestRunnerCombobox);
      }
    });
  }

  private void resetGradleDelegationControls(@Nullable WizardContext wizardContext) {
    if (wizardContext != null) {
      dropTestRunnerCombobox();
      dropDelegateBuildCombobox();
      if (myDelegatePanel != null) {
        Container parent = myDelegatePanel.getParent();
        if (parent != null) {
          parent.remove(myDelegatePanel);
        }
        myDelegatePanel = null;
        myDelegateBuildCombobox = null;
        myTestRunnerCombobox = null;
      }
      return;
    }
    if (myDelegateBuildCombobox != null) {
      myDelegateBuildCombobox.setSelectedItem(new BuildRunItem(myInitialSettings.getDelegatedBuild()));
    }
    if (myTestRunnerCombobox != null) {
      myTestRunnerCombobox.setSelectedItem(new TestRunnerItem(myInitialSettings.getTestRunner()));
    }
  }

  void showBalloonIfNecessary() {
    if (!myShowBalloonIfNecessary || (myGradleHomePathField != null && !myGradleHomePathField.isEnabled())) {
      return;
    }
    myShowBalloonIfNecessary = false;
    MessageType messageType = switch (myGradleHomeSettingType) {
      case DEDUCED -> MessageType.INFO;
      case EXPLICIT_INCORRECT, UNKNOWN -> MessageType.ERROR;
      default -> null;
    };
    if (messageType != null) {
      new DelayedBalloonInfo(messageType, myGradleHomeSettingType, BALLOON_DELAY_MILLIS).run();
    }
  }

  private void updateProjectRef(@Nullable Project project, @Nullable WizardContext wizardContext) {
    if (wizardContext != null && wizardContext.getProject() != null) {
      project = wizardContext.getProject();
    }
    if (project != null && project != myProjectRef.get()) {
      Disposer.register(project, myProjectRefDisposable);
    }
    myProjectRef.set(project);
  }

  private static JPanel addComponentsGroup(@Nullable @NlsContexts.Separator String title,
                                           PaintAwarePanel content,
                                           int indentLevel,
                                           @NotNull Consumer<JPanel> configuration) {
    JPanel result = new JPanel(new GridBagLayout());
    if (title != null) {
      GridBag constraints = ExternalSystemUiUtil.getFillLineConstraints(indentLevel);
      constraints.insets.top = UIUtil.LARGE_VGAP;
      result.add(new TitledSeparator(title), constraints);
    }
    int count = result.getComponentCount();

    configuration.accept(result);

    if (result.getComponentCount() > count) {
      content.add(result, ExternalSystemUiUtil.getFillLineConstraints(0).insets(0, 0, 0, 0));
    }
    return result;
  }

  private static void setupProjectSdksModel(@NotNull ProjectSdksModel sdksModel, @NotNull Project project, @Nullable Sdk projectSdk) {
    sdksModel.reset(project);
    deduplicateSdkNames(sdksModel);
    if (projectSdk == null) {
      projectSdk = sdksModel.getProjectSdk();
      // Find real sdk
      // see ProjectSdksModel#getProjectSdk for details
      projectSdk = sdksModel.findSdk(projectSdk);
    }
    if (projectSdk != null) {
      // resolves executable JDK
      // e.g: for Android projects
      projectSdk = ExternalSystemJdkUtil.resolveDependentJdk(projectSdk);
      // Find editable sdk
      // see ProjectSdksModel#getProjectSdk for details
      projectSdk = sdksModel.findSdk(projectSdk.getName());
    }
    sdksModel.setProjectSdk(projectSdk);
  }

  @NotNull
  private static GridBag getLabelConstraints(int indentLevel) {
    Insets insets = JBUI.insets(0, INSETS + INSETS * indentLevel, 0, INSETS);
    return new GridBag().anchor(GridBagConstraints.WEST).weightx(0).insets(insets);
  }

  private static void wrapExceptions(ThrowableRunnable<Throwable> runnable) {
    try {
      runnable.run();
    }
    catch (Throwable ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Deduplicates sdks name in corrupted sdks model
   */
  private static void deduplicateSdkNames(@NotNull ProjectSdksModel projectSdksModel) {
    Set<String> processedNames = new HashSet<>();
    Collection<Sdk> editableSdks = projectSdksModel.getProjectSdks().values();
    for (Sdk sdk : editableSdks) {
      if (processedNames.contains(sdk.getName())) {
        SdkModificator sdkModificator = sdk.getSdkModificator();
        String name = createUniqueSdkName(sdk.getName(), editableSdks);
        sdkModificator.setName(name);
        sdkModificator.commitChanges();
      }
      processedNames.add(sdk.getName());
    }
  }

  @NlsSafe
  static String getIDEName() {
    return ApplicationNamesInfo.getInstance().getFullProductName();
  }

  private static class MyItemCellRenderer<T> extends ColoredListCellRenderer<MyItem<T>> {

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends MyItem<T>> list,
                                         MyItem<T> value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      if (value == null) return;
      CompositeAppearance.DequeEnd ending = new CompositeAppearance().getEnding();
      ending.addText(value.getText(), getTextAttributes(selected));
      if (value.getComment() != null) {
        SimpleTextAttributes commentAttributes = getCommentAttributes(selected);
        ending.addComment(value.getComment(), commentAttributes);
      }
      ending.getAppearance().customize(this);
    }

    @NotNull
    private static SimpleTextAttributes getTextAttributes(boolean selected) {
      return selected && !(SystemInfoRt.isWindows && UIManager.getLookAndFeel().getName().contains("Windows"))
             ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES
             : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    }

    @NotNull
    private static SimpleTextAttributes getCommentAttributes(boolean selected) {
      return SystemInfo.isMac && selected
             ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.WHITE)
             : SimpleTextAttributes.GRAY_ATTRIBUTES;
    }
  }

  private static abstract class MyItem<T> {
    @Nullable
    protected final T value;

    private MyItem(@Nullable T value) {
      this.value = value;
    }

    @NlsContexts.ListItem
    protected abstract String getText();

    @NlsContexts.ListItem
    protected abstract String getComment();

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof MyItem<?> item && Objects.equals(value, item.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  private class DelayedBalloonInfo implements Runnable {
    private final MessageType myMessageType;
    private final @Nls String myText;
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

  private final class BuildRunItem extends MyItem<Boolean> {

    private BuildRunItem(@Nullable Boolean value) {
      super(value);
    }

    @Override
    protected String getText() {
      return getText(value);
    }

    @Override
    protected String getComment() {
      return Comparing.equal(value, GradleProjectSettings.DEFAULT_DELEGATE) ? GradleBundle.message("gradle.settings.text.default") : null;
    }

    @NotNull
    @NlsContexts.ListItem
    private static String getText(@Nullable Boolean state) {
      if (state == Boolean.TRUE) {
        return GradleConstants.GRADLE_NAME; //NON-NLS GRADLE_NAME
      }
      if (state == Boolean.FALSE) {
        return getIDEName();
      }
      LOG.error("Unexpected: " + state);
      return GradleBundle.message("gradle.settings.text.unexpected", state);
    }
  }

  private final class TestRunnerItem extends MyItem<TestRunner> {

    private TestRunnerItem(@Nullable TestRunner value) {
      super(value);
    }

    @Override
    protected String getText() {
      return getText(value);
    }

    @Override
    protected String getComment() {
      return Comparing.equal(value, GradleProjectSettings.DEFAULT_TEST_RUNNER)
             ? GradleBundle.message("gradle.settings.text.default")
             : null;
    }

    @NotNull
    @NlsContexts.ListItem
    private static String getText(@Nullable TestRunner runner) {
      if (runner == TestRunner.GRADLE) {
        return GradleConstants.GRADLE_NAME;  //NON-NLS GRADLE_NAME
      }
      if (runner == TestRunner.PLATFORM) {
        return getIDEName();
      }
      if (runner == TestRunner.CHOOSE_PER_TEST) {
        return GradleBundle.message("gradle.settings.text.build.run.per.test");
      }
      LOG.error("Unexpected: " + runner);
      return GradleBundle.message("gradle.settings.text.unexpected", runner);
    }
  }

  private final class DistributionTypeItem extends MyItem<DistributionType> {

    private DistributionTypeItem(@Nullable DistributionType value) {
      super(value);
    }

    @Override
    protected String getText() {
      return getText(value);
    }

    @Override
    protected String getComment() {
      return null;
    }

    @NotNull
    @NlsContexts.ListItem
    private static String getText(@Nullable DistributionType value) {
      if (value != null) {
        return switch (value) {
          case BUNDLED -> GradleBundle.message("gradle.project.settings.distribution.bundled");
          case DEFAULT_WRAPPED -> GradleBundle.message("gradle.project.settings.distribution.wrapper");
          case WRAPPED -> GradleBundle.message("gradle.project.settings.distribution.wrapper.task");
          case LOCAL -> GradleBundle.message("gradle.project.settings.distribution.local");
        };
      }
      LOG.error("Unexpected: " + value);
      return GradleBundle.message("gradle.settings.text.unexpected", value);
    }
  }
}
