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
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.Alarm;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Allows to configure gradle settings.
 * <p/>
 * Basically, it has two modes:
 * <pre>
 * <ul>
 *   <li>no information about linked gradle project is available (e.g. gradle settings are opened from welcome screen);</li>
 *   <li>information about linked gradle project is available;</li>
 * </ul>
 * </pre>
 * The difference is in how we handle
 * <a href="http://www.gradle.org/docs/current/userguide/userguide_single.html#gradle_wrapper">gradle wrapper</a> settings - we
 * represent settings like 'use gradle wrapper whenever possible' at the former case and ask to explicitly define whether gradle
 * wrapper or particular local distribution should be used at the latest one.
 * 
 * @author peter
 */
public class GradleConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  @NonNls public static final String HELP_TOPIC = "reference.settingsdialog.project.gradle";

  private static final long BALLOON_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  @NotNull private final Helper myHelper;

  @Nullable private final Project myProject;

  @NotNull private GradleHomeSettingType myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;

  @NotNull private final JLabel myLinkedProjectLabel = new JBLabel(GradleBundle.message("gradle.import.label.select.project"));
  @NotNull private final JLabel myGradleHomeLabel    = new JBLabel(GradleBundle.message("gradle.import.text.home.path"));

  @NotNull private TextFieldWithBrowseButton myLinkedGradleProjectPathField;
  @NotNull private TextFieldWithBrowseButton myGradleHomePathField;
  @NotNull private JBCheckBox                myPreferWrapperWheneverPossibleCheckBox;
  @NotNull private JBRadioButton             myUseWrapperButton;
  @NotNull private JBRadioButton             myUseLocalDistributionButton;

  @Nullable private JComponent myComponent;

  private boolean myAlwaysShowLinkedProjectControls;
  private boolean myShowBalloonIfNecessary;
  private boolean myGradleHomeModifiedByUser;

  public GradleConfigurable(@Nullable Project project) {
    this(project, ServiceManager.getService(GradleInstallationManager.class));
  }

  public GradleConfigurable(@Nullable Project project, @NotNull GradleInstallationManager gradleInstallationManager) {
    myProject = project;
    myHelper = new DefaultHelper(gradleInstallationManager);
    buildContent();
  }

  public GradleConfigurable(@Nullable Project project, @NotNull Helper helper) {
    myHelper = helper;
    myProject = project;
    buildContent();
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.name");
  }

  @NotNull
  public TextFieldWithBrowseButton getGradleHomePathField() {
    return myGradleHomePathField;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public JComponent createComponent() {
    if (myComponent == null) {
      buildContent();
    }
    return myComponent;
  }

  @Nullable
  public String getLinkedProjectPath() {
    return myLinkedGradleProjectPathField.getText();
  }

  @Nullable
  public String getGradleHomePath() {
    return myGradleHomePathField.getText();
  }

  public boolean isPreferLocalInstallationToWrapper() {
    if (myPreferWrapperWheneverPossibleCheckBox.isVisible()) {
      return !myPreferWrapperWheneverPossibleCheckBox.isSelected();
    }
    else {
      return myUseLocalDistributionButton.isSelected();
    }
  }

  public void setLinkedGradleProjectPath(@NotNull String path) {
    myLinkedGradleProjectPathField.setText(path);
  }

  public void setAlwaysShowLinkedProjectControls(boolean alwaysShowLinkedProjectControls) {
    myAlwaysShowLinkedProjectControls = alwaysShowLinkedProjectControls;
  }

  @NotNull
  JBRadioButton getUseWrapperButton() {
    return myUseWrapperButton;
  }

  private void buildContent() {
    initContentPanel();
    initLinkedGradleProjectPathControl();
    initWrapperVsLocalControls();
    initGradleHome();
    assert myComponent != null;

    GridBag pathLabelConstraints = new GridBag().anchor(GridBagConstraints.WEST).weightx(0);
    GridBag pathConstraints = new GridBag().weightx(1).coverLine().fillCellHorizontally().anchor(GridBagConstraints.WEST)
      .insets(myPreferWrapperWheneverPossibleCheckBox.getInsets());
    myComponent.add(myLinkedProjectLabel, pathLabelConstraints);
    myComponent.add(myLinkedGradleProjectPathField, pathConstraints);

    // Define 'prefer to use gradle wrapper' as a general recommendation. Use a checkbox for that.
    GridBag constraints = new GridBag().coverLine().anchor(GridBagConstraints.WEST);
    myComponent.add(myPreferWrapperWheneverPossibleCheckBox, constraints);
    
    // Provide radio buttons if gradle wrapper can be used for particular project.
    myComponent.add(myUseWrapperButton, constraints);
    myComponent.add(myUseLocalDistributionButton, constraints);

    myComponent.add(myGradleHomeLabel, pathLabelConstraints);
    myComponent.add(myGradleHomePathField, pathConstraints);

    myComponent.add(Box.createVerticalGlue(), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
  }

  private void initContentPanel() {
    myComponent = new JPanel(new GridBagLayout()) {
      @Override
      public void paint(Graphics g) {
        super.paint(g);
        showBalloonIfNecessary();
      }
    };
    myComponent.addPropertyChangeListener(new PropertyChangeListener() {
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
  }

  void showBalloonIfNecessary() {
    if (!myShowBalloonIfNecessary || !myGradleHomePathField.isEnabled()) {
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
      myHelper.showBalloon(messageType, myGradleHomeSettingType, BALLOON_DELAY_MILLIS);
    }
  }

  private void initLinkedGradleProjectPathControl() {
    myLinkedGradleProjectPathField = new TextFieldWithBrowseButton();
    myLinkedGradleProjectPathField.addBrowseFolderListener(
      "",
      GradleBundle.message("gradle.import.label.select.project"),
      myProject, GradleUtil.getGradleProjectFileChooserDescriptor()
    );
    myLinkedGradleProjectPathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onLinkedProjectPathChange();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onLinkedProjectPathChange();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onLinkedProjectPathChange();
      }
    });
  }

  private void onLinkedProjectPathChange() {
    String linkedProjectPath = myLinkedGradleProjectPathField.getText();
    if (StringUtil.isEmpty(linkedProjectPath) || !myHelper.isGradleWrapperDefined(linkedProjectPath)) {
      myUseWrapperButton.setEnabled(false);
      myUseWrapperButton.setText(GradleBundle.message("gradle.config.text.use.wrapper.disabled"));
      myUseLocalDistributionButton.setSelected(true);
    }
    else {
      myUseWrapperButton.setEnabled(true);
      myUseWrapperButton.setText(GradleBundle.message("gradle.config.text.use.wrapper"));
      if (myProject == null || myHelper.getSettings(myProject).isPreferLocalInstallationToWrapper()) {
        myUseLocalDistributionButton.setSelected(true);
      }
      else {
        myUseWrapperButton.setSelected(true);
        myGradleHomePathField.setEnabled(false);
      }
    }
  }

  private void initWrapperVsLocalControls() {
    myPreferWrapperWheneverPossibleCheckBox = new JBCheckBox(GradleBundle.message("gradle.config.text.prefer.wrapper.when.possible"), true);

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean enabled = e.getSource() == myUseLocalDistributionButton;
        myGradleHomePathField.setEnabled(enabled);
        if (enabled) {
          showBalloonIfNecessary();
        }
        else {
          myAlarm.cancelAllRequests();
        }
      }
    };
    myUseWrapperButton = new JBRadioButton(GradleBundle.message("gradle.config.text.use.wrapper"), true);
    myUseWrapperButton.addActionListener(listener);
    myUseLocalDistributionButton = new JBRadioButton(GradleBundle.message("gradle.config.text.use.local.distribution"));
    myUseLocalDistributionButton.addActionListener(listener);
    ButtonGroup group = new ButtonGroup();
    group.add(myUseWrapperButton);
    group.add(myUseLocalDistributionButton);
  }

  private void initGradleHome() {
    myGradleHomePathField = new TextFieldWithBrowseButton();
    myGradleHomePathField.addBrowseFolderListener(
      "",
      GradleBundle.message("gradle.import.title.select.project"),
      null,
      GradleUtil.getGradleHomeFileChooserDescriptor()
    );
    myGradleHomePathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        useNormalColorForPath();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        useNormalColorForPath();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    });
  }

  @Override
  public boolean isModified() {
    if (myProject == null) {
      return false;
    }

    GradleSettings settings = myHelper.getSettings(myProject);

    if (!Comparing.equal(myLinkedGradleProjectPathField.getText(), settings.getLinkedProjectPath())) {
      return true;
    }
    
    boolean preferLocalToWrapper = settings.isPreferLocalInstallationToWrapper();
    if (myPreferWrapperWheneverPossibleCheckBox.isVisible()) {
      if (myPreferWrapperWheneverPossibleCheckBox.isSelected() == preferLocalToWrapper) {
        return true;
      }
    }
    else if (myUseWrapperButton.isSelected() == preferLocalToWrapper) {
      return true;
    }

    if (!Comparing.equal(myGradleHomePathField.getText(), settings.getGradleHome())) {
      if (myGradleHomeModifiedByUser) {
        useNormalColorForPath();
      }
      return true;
    }
    
    return false;
  }

  @Override
  public void apply() {
    if (myProject == null) {
      return;
    }
    
    String linkedProjectPath = myLinkedGradleProjectPathField.getText();
    String gradleHomePath = myGradleHomePathField.getText();
    boolean preferLocalToWrapper;
    if (myPreferWrapperWheneverPossibleCheckBox.isVisible()) {
      preferLocalToWrapper = !myPreferWrapperWheneverPossibleCheckBox.isSelected();
    }
    else {
      preferLocalToWrapper = myUseLocalDistributionButton.isSelected();
    }
    GradleSettings.applySettings(linkedProjectPath, gradleHomePath, preferLocalToWrapper, myProject);

    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    if (myProject != defaultProject) {
      GradleSettings.applyPreferLocalInstallationToWrapper(preferLocalToWrapper, defaultProject);
    }

    if (isValidGradleHome(gradleHomePath)) {
      myGradleHomeSettingType = GradleHomeSettingType.EXPLICIT_CORRECT;
      // There is a possible case that user defines gradle home for particular open project. We want to apply that value
      // to the default project as well if it's still non-defined.
      if (defaultProject != myProject && !isValidGradleHome(GradleSettings.getInstance(defaultProject).getGradleHome())) {
        GradleSettings.applyGradleHome(gradleHomePath, defaultProject);
      }
      return;
    }

    useNormalColorForPath();
    if (StringUtil.isEmpty(gradleHomePath)) {
      myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;
    }
    else {
      myGradleHomeSettingType = GradleHomeSettingType.EXPLICIT_INCORRECT;
      myHelper.showBalloon(MessageType.ERROR, myGradleHomeSettingType, 0);
    }
  }

  private boolean isValidGradleHome(@Nullable String path) {
    if (StringUtil.isEmpty(path)) {
      return false;
    }
    assert path != null;
    return myHelper.isGradleSdkHome(new File(path));
  }
  
  @Override
  public void reset() {
    if (myProject == null) {
      return;
    }
    
    // Process gradle wrapper/local distribution settings.
    // There are the following possible cases:
    //   1. Default project or non-default project with no linked gradle project - 'use gradle wrapper whenever possible' check box
    //      should be shown;
    //   2. Non-default project with linked gradle project:
    //      2.1. Gradle wrapper is configured for the target project - radio buttons on whether to use wrapper or particular local gradle
    //           distribution should be show;
    //      2.2. Gradle wrapper is not configured for the target project - radio buttons should be shown and 
    //           'use gradle wrapper' option should be disabled;
    useNormalColorForPath();
    GradleSettings settings = myHelper.getSettings(myProject);
    String linkedProjectPath = myLinkedGradleProjectPathField.getText();
    if (StringUtil.isEmpty(linkedProjectPath)) {
      linkedProjectPath = settings.getLinkedProjectPath();
    }
    myLinkedProjectLabel.setVisible(myAlwaysShowLinkedProjectControls || !myProject.isDefault());
    myLinkedGradleProjectPathField.setVisible(myAlwaysShowLinkedProjectControls || !myProject.isDefault());
    if (linkedProjectPath != null) {
      myLinkedGradleProjectPathField.setText(linkedProjectPath);
    }
    
    myPreferWrapperWheneverPossibleCheckBox.setVisible(!myAlwaysShowLinkedProjectControls && myProject.isDefault());
    myPreferWrapperWheneverPossibleCheckBox.setSelected(!settings.isPreferLocalInstallationToWrapper());
    myUseWrapperButton.setVisible(myAlwaysShowLinkedProjectControls || (!myProject.isDefault() && linkedProjectPath != null));
    myUseLocalDistributionButton.setVisible(myAlwaysShowLinkedProjectControls || (!myProject.isDefault() && linkedProjectPath != null));
    if (myAlwaysShowLinkedProjectControls && linkedProjectPath == null) {
      myUseWrapperButton.setEnabled(false);
      myUseLocalDistributionButton.setSelected(true);
    }
    else if (linkedProjectPath != null) {
      if (myHelper.isGradleWrapperDefined(linkedProjectPath)) {
        myUseWrapperButton.setEnabled(true);
        myUseWrapperButton.setText(GradleBundle.message("gradle.config.text.use.wrapper"));
        if (settings.isPreferLocalInstallationToWrapper()) {
          myUseLocalDistributionButton.setSelected(true);
          myGradleHomePathField.setEnabled(true);
        }
        else {
          myUseWrapperButton.setSelected(true);
          myGradleHomePathField.setEnabled(myProject.isDefault());
        }
      }
      else {
        myUseWrapperButton.setText(GradleBundle.message("gradle.config.text.use.wrapper.disabled"));
        myUseWrapperButton.setEnabled(false);
        myUseLocalDistributionButton.setSelected(true);
      }
    }
    
    String localDistributionPath = settings.getGradleHome();
    if (!StringUtil.isEmpty(localDistributionPath)) {
      myGradleHomeSettingType = myHelper.isGradleSdkHome(new File(localDistributionPath)) ?
                                GradleHomeSettingType.EXPLICIT_CORRECT :
                                GradleHomeSettingType.EXPLICIT_INCORRECT;
      myAlarm.cancelAllRequests();
      if (myGradleHomeSettingType == GradleHomeSettingType.EXPLICIT_INCORRECT && settings.isPreferLocalInstallationToWrapper()) {
        myHelper.showBalloon(MessageType.ERROR, myGradleHomeSettingType, 0);
      }
      myGradleHomePathField.setText(localDistributionPath);
      return;
    }
    myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;
    deduceGradleHomeIfPossible();
  }

  private void useNormalColorForPath() {
    myGradleHomePathField.getTextField().setForeground(UIManager.getColor("TextField.foreground"));
    myGradleHomeModifiedByUser = true;
  }

  /**
   * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
   */
  private void deduceGradleHomeIfPossible() {
    File gradleHome = myHelper.getGradleHome(myProject);
    if (gradleHome == null) {
      myHelper.showBalloon(MessageType.WARNING, GradleHomeSettingType.UNKNOWN, BALLOON_DELAY_MILLIS);
      return;
    }
    myGradleHomeSettingType = GradleHomeSettingType.DEDUCED;
    myHelper.showBalloon(MessageType.INFO, GradleHomeSettingType.DEDUCED, BALLOON_DELAY_MILLIS);
    myGradleHomePathField.setText(gradleHome.getPath());
    myGradleHomePathField.getTextField().setForeground(UIManager.getColor("TextField.inactiveForeground"));
    myGradleHomeModifiedByUser = false;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void disposeUIResources() {
    myComponent = null;
    myGradleHomePathField = null;
    myPreferWrapperWheneverPossibleCheckBox = null;
    myUseWrapperButton = null;
    myUseLocalDistributionButton = null;
  }

  @NotNull
  public String getHelpTopic() {
    return HELP_TOPIC;
  }

  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  @NotNull
  public GradleHomeSettingType getCurrentGradleHomeSettingType() {
    String path = myGradleHomePathField.getText();
    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      GradleLog.LOG.info(String.format("Checking 'gradle home' status. Manually entered value is '%s'", path));
    }
    if (path == null || StringUtil.isEmpty(path.trim())) {
      return GradleHomeSettingType.UNKNOWN;
    }
    if (isModified()) {
      return myHelper.isGradleSdkHome(new File(path)) ? GradleHomeSettingType.EXPLICIT_CORRECT
                                                                   : GradleHomeSettingType.EXPLICIT_INCORRECT;
    }
    return myGradleHomeSettingType;
  }

  private class DelayedBalloonInfo implements Runnable {
    private final MessageType myMessageType;
    private final String      myText;
    private final long        myTriggerTime;

    DelayedBalloonInfo(@NotNull MessageType messageType, @NotNull GradleHomeSettingType settingType, long delayMillis) {
      myMessageType = messageType;
      myText = settingType.getDescription();
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
      if (!myGradleHomePathField.isShowing()) {
        // Don't schedule the balloon if the configurable is hidden.
        return;
      }
      GradleUtil.showBalloon(myGradleHomePathField, myMessageType, myText);
    }
  }

  /**
   * Encapsulates functionality which default implementation is backed by static API usage (IJ infrastructure limitation).
   * <p/>
   * The main idea is to allow to provide a mock implementation for {@link GradleConfigurable} logic testing.
   */
  interface Helper {
    boolean isGradleSdkHome(@NotNull File file);

    @Nullable
    File getGradleHome(@Nullable Project project);

    @NotNull
    GradleSettings getSettings(@NotNull Project project);

    boolean isGradleWrapperDefined(@Nullable String linkedProjectPath);
    
    void showBalloon(@NotNull MessageType messageType, @NotNull GradleHomeSettingType settingType, long delayMillis);
  }
  
  private class DefaultHelper implements Helper {
    
    @NotNull private final GradleInstallationManager myInstallationManager;

    DefaultHelper(@NotNull GradleInstallationManager installationManager) {
      myInstallationManager = installationManager;
    }

    @Override
    public boolean isGradleSdkHome(@NotNull File file) {
      return myInstallationManager.isGradleSdkHome(file);
    }

    @Nullable
    @Override
    public File getGradleHome(@Nullable Project project) {
      return myInstallationManager.getGradleHome(project);
    }

    @NotNull
    @Override
    public GradleSettings getSettings(@NotNull Project project) {
      return GradleSettings.getInstance(project);
    }

    @Override
    public boolean isGradleWrapperDefined(@Nullable String linkedProjectPath) {
      return GradleUtil.isGradleWrapperDefined(linkedProjectPath);
    }

    @Override
    public void showBalloon(@NotNull MessageType messageType, @NotNull GradleHomeSettingType settingType, long delayMillis) {
      new DelayedBalloonInfo(messageType, settingType, delayMillis).run();
    }
  }
}
