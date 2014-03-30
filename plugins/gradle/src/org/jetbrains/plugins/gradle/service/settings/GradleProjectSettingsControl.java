/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.settings.LocationSettingType;
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author Denis Zhdanov
 * @since 4/24/13 1:45 PM
 */
public class GradleProjectSettingsControl extends AbstractExternalProjectSettingsControl<GradleProjectSettings> {

  private static final long BALLOON_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);

  @NotNull private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  @NotNull private LocationSettingType myGradleHomeSettingType = LocationSettingType.UNKNOWN;

  @NotNull private final GradleInstallationManager myInstallationManager;

  @SuppressWarnings("FieldCanBeLocal") // Used implicitly by reflection at disposeUIResources() and showUi()
  private JLabel                    myGradleHomeLabel;
  private TextFieldWithBrowseButton myGradleHomePathField;
  private JBRadioButton             myUseWrapperButton;
  private JBRadioButton             myUseWrapperWithVerificationButton;
  private JBLabel                   myUseWrapperVerificationLabel;
  private JBRadioButton             myUseLocalDistributionButton;
  private JBRadioButton             myUseBundledDistributionButton;

  private boolean myShowBalloonIfNecessary;

  public GradleProjectSettingsControl(@NotNull GradleProjectSettings initialSettings) {
    super(initialSettings);
    myInstallationManager = ServiceManager.getService(GradleInstallationManager.class);
  }

  @Override
  protected void fillExtraControls(@NotNull PaintAwarePanel content, int indentLevel) {
    content.setPaintCallback(new Consumer<Graphics>() {
      @Override
      public void consume(Graphics graphics) {
        showBalloonIfNecessary();
      }
    });

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

    myGradleHomeLabel = new JBLabel(GradleBundle.message("gradle.settings.text.home.path"));
    initGradleHome();

    initControls();
    content.add(myUseWrapperButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    content.add(myUseWrapperWithVerificationButton, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    content.add(myUseWrapperVerificationLabel,  ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    //content.add(Box.createGlue(), ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    // Hide bundled distribution option for a while
    // content.add(myUseBundledDistributionButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    content.add(myUseLocalDistributionButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

    content.add(myGradleHomeLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    content.add(myGradleHomePathField, ExternalSystemUiUtil.getFillLineConstraints(0));
  }

  private void initControls() {
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean localDistributionEnabled = myUseLocalDistributionButton.isSelected();
        myGradleHomePathField.setEnabled(localDistributionEnabled);
        if (localDistributionEnabled) {
          if(myGradleHomePathField.getText().isEmpty()){
            deduceGradleHomeIfPossible();
          } else {
            if(myInstallationManager.isGradleSdkHome(myGradleHomePathField.getText())){
              myGradleHomeSettingType = LocationSettingType.EXPLICIT_CORRECT;
            } else {
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

    myUseWrapperButton = new JBRadioButton(GradleBundle.message("gradle.settings.text.use.default_wrapper.configured"));
    myUseWrapperButton.addActionListener(listener);
    myUseWrapperWithVerificationButton = new JBRadioButton(GradleBundle.message("gradle.settings.text.use.customizable_wrapper"));
    myUseWrapperWithVerificationButton.addActionListener(listener);
    myUseWrapperVerificationLabel = new JBLabel(GradleBundle.message("gradle.settings.text.wrapper.customization.compatibility"));
    myUseWrapperVerificationLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.MINI));
    myUseWrapperVerificationLabel.setIcon(UIUtil.getBalloonInformationIcon());

    myUseLocalDistributionButton = new JBRadioButton(GradleBundle.message("gradle.settings.text.use.local.distribution"));
    myUseLocalDistributionButton.addActionListener(listener);

    myUseBundledDistributionButton = new JBRadioButton(
      GradleBundle.message("gradle.settings.text.use.bundled.distribution", GradleVersion.current().getVersion()));
    myUseBundledDistributionButton.addActionListener(listener);
    myUseBundledDistributionButton.setEnabled(false);

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myUseWrapperButton);
    buttonGroup.add(myUseWrapperWithVerificationButton);
    buttonGroup.add(myUseBundledDistributionButton);
    buttonGroup.add(myUseLocalDistributionButton);
  }

  private void initGradleHome() {
    myGradleHomePathField = new TextFieldWithBrowseButton();

    FileChooserDescriptor fileChooserDescriptor = GradleUtil.getGradleHomeFileChooserDescriptor();

    myGradleHomePathField.addBrowseFolderListener(
      "",
      GradleBundle.message("gradle.settings.text.home.path"),
      null,
      fileChooserDescriptor,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
      false
    );
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
  }

  @Override
  public boolean validate(@NotNull GradleProjectSettings settings) throws ConfigurationException {
    String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
    if (myUseLocalDistributionButton.isSelected()) {
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
  protected void applyExtraSettings(@NotNull GradleProjectSettings settings) {
    String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
    if (StringUtil.isEmpty(gradleHomePath)) {
      settings.setGradleHome(null);
    }
    else {
      settings.setGradleHome(gradleHomePath);
      GradleUtil.storeLastUsedGradleHome(gradleHomePath);
    }

    if (myUseLocalDistributionButton.isSelected()) {
      settings.setDistributionType(DistributionType.LOCAL);
    } else if(myUseWrapperButton.isSelected()) {
      settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    } else if(myUseWrapperWithVerificationButton.isSelected() || myUseBundledDistributionButton.isSelected()) {
      settings.setDistributionType(DistributionType.WRAPPED);
    }
  }

  @Override
  protected void updateInitialExtraSettings() {
    String gradleHomePath = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
    getInitialSettings().setGradleHome(StringUtil.isEmpty(gradleHomePath) ? null : gradleHomePath);
    if (myUseLocalDistributionButton.isSelected()) {
      getInitialSettings().setDistributionType(DistributionType.LOCAL);
    } else if(myUseWrapperButton.isSelected()) {
      getInitialSettings().setDistributionType(DistributionType.DEFAULT_WRAPPED);
    } else if(myUseWrapperWithVerificationButton.isSelected() || myUseBundledDistributionButton.isSelected()) {
      getInitialSettings().setDistributionType(DistributionType.WRAPPED);
    }
  }

  @Override
  protected boolean isExtraSettingModified() {
    DistributionType distributionType = getInitialSettings().getDistributionType();
    if (myUseBundledDistributionButton.isSelected() && distributionType != DistributionType.BUNDLED) {
      return true;
    }

    if (myUseWrapperButton.isSelected() && distributionType != DistributionType.DEFAULT_WRAPPED) {
        return true;
    }

    if (myUseWrapperWithVerificationButton.isSelected() && distributionType != DistributionType.WRAPPED) {
        return true;
    }

    if (myUseLocalDistributionButton.isSelected() && distributionType != DistributionType.LOCAL) {
      return true;
    }

    String gradleHome = FileUtil.toCanonicalPath(myGradleHomePathField.getText());
    if (StringUtil.isEmpty(gradleHome)) {
      return !StringUtil.isEmpty(getInitialSettings().getGradleHome());
    }
    else {
      return !gradleHome.equals(getInitialSettings().getGradleHome());
    }
  }

  @Override
  protected void resetExtraSettings(boolean isDefaultModuleCreation) {
    String gradleHome = getInitialSettings().getGradleHome();
    myGradleHomePathField.setText(gradleHome == null ? "" : gradleHome);
    myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
    
    updateWrapperControls(getInitialSettings().getExternalProjectPath(), isDefaultModuleCreation);
    if (!myUseLocalDistributionButton.isSelected()) {
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
          getInitialSettings().getDistributionType() == DistributionType.LOCAL) {
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
      }
    }
  }

  public void updateWrapperControls(@Nullable String linkedProjectPath, boolean isDefaultModuleCreation) {
    if(StringUtil.isEmpty(linkedProjectPath) && !isDefaultModuleCreation) {
        myUseLocalDistributionButton.setSelected(true);
        myGradleHomePathField.setEnabled(true);
        return;
    }

    final boolean isGradleDefaultWrapperFilesExist = GradleUtil.isGradleDefaultWrapperFilesExist(linkedProjectPath);
    if (isGradleDefaultWrapperFilesExist || isDefaultModuleCreation) {
      myUseWrapperButton.setEnabled(true);
      myUseWrapperButton.setSelected(true);
      myGradleHomePathField.setEnabled(false);
      myUseWrapperButton.setText(GradleBundle.message("gradle.settings.text.use.default_wrapper.configured"));
    } else {
      myUseWrapperButton.setEnabled(false);
      myUseLocalDistributionButton.setSelected(true);
      myGradleHomePathField.setEnabled(true);
      myUseWrapperButton.setText(GradleBundle.message("gradle.settings.text.use.default_wrapper.not_configured"));
    }

    if(getInitialSettings().getDistributionType() == null) {
      return;
    }

    switch (getInitialSettings().getDistributionType()) {
      case LOCAL:
        myGradleHomePathField.setEnabled(true);
        myUseLocalDistributionButton.setSelected(true);
        break;
      case DEFAULT_WRAPPED:
        myGradleHomePathField.setEnabled(false);
        myUseWrapperButton.setSelected(true);
        myUseWrapperButton.setEnabled(true);
        break;
      case WRAPPED:
        myGradleHomePathField.setEnabled(false);
        myUseWrapperWithVerificationButton.setSelected(true);
        break;
      case BUNDLED:
        myGradleHomePathField.setEnabled(false);
        myUseBundledDistributionButton.setSelected(true);
        break;
    }
  }

  /**
   * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
   */
  private void deduceGradleHomeIfPossible() {
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
      new DelayedBalloonInfo(messageType, myGradleHomeSettingType, BALLOON_DELAY_MILLIS).run();
    }
  }

  private class DelayedBalloonInfo implements Runnable {
    private final MessageType myMessageType;
    private final String      myText;
    private final long        myTriggerTime;

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
