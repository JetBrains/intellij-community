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
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
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
  private JBRadioButton             myUseLocalDistributionButton;

  private boolean myShowBalloonIfNecessary;
  private boolean myGradleHomeModifiedByUser;

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

    initWrapperControls();
    content.add(myUseWrapperButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    content.add(myUseLocalDistributionButton, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));

    content.add(myGradleHomeLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel));
    content.add(myGradleHomePathField, ExternalSystemUiUtil.getFillLineConstraints(0));
  }

  private void initWrapperControls() {
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
    myUseWrapperButton = new JBRadioButton(GradleBundle.message("gradle.settings.text.use.wrapper"), true);
    myUseWrapperButton.addActionListener(listener);
    myUseLocalDistributionButton = new JBRadioButton(GradleBundle.message("gradle.settings..text.use.local.distribution"));
    myUseLocalDistributionButton.addActionListener(listener);
    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myUseWrapperButton);
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
        myGradleHomeModifiedByUser = true;
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
        myGradleHomeModifiedByUser = true;
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    });
  }

  @Override
  @Nullable
  protected String applyExtraSettings(@NotNull GradleProjectSettings settings) {
    String gradleHomePath = myGradleHomePathField.getText();
    if (myGradleHomeModifiedByUser) {
      if (StringUtil.isEmpty(gradleHomePath)) {
        settings.setGradleHome(null);
      }
      else {
        settings.setGradleHome(gradleHomePath);
        GradleUtil.storeLastUsedGradleHome(gradleHomePath);
      }
    }
    else {
      settings.setGradleHome(getInitialSettings().getGradleHome());
    }

    if (myUseLocalDistributionButton.isSelected()) {
      if (StringUtil.isEmpty(gradleHomePath)) {
        myGradleHomeSettingType = LocationSettingType.UNKNOWN;
      }
      else if (!myInstallationManager.isGradleSdkHome(new File(gradleHomePath))) {
        myGradleHomeSettingType = LocationSettingType.EXPLICIT_INCORRECT;
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
        return GradleBundle.message("gradle.home.setting.type.explicit.incorrect", gradleHomePath);
      }
    }
    settings.setPreferLocalInstallationToWrapper(myUseLocalDistributionButton.isSelected());
    return null;
  }
  
  @Override
  protected boolean isExtraSettingModified() {
    if (myUseLocalDistributionButton.isSelected() != getInitialSettings().isPreferLocalInstallationToWrapper()) {
      return true;
    }

    String gradleHome = myGradleHomePathField.getText();
    if (StringUtil.isEmpty(gradleHome)) {
      return !StringUtil.isEmpty(getInitialSettings().getGradleHome());
    }
    else {
      return !gradleHome.equals(getInitialSettings().getGradleHome());
    }
  }

  @Override
  protected void resetExtraSettings() {
    myGradleHomeModifiedByUser = false;
    String gradleHome = getInitialSettings().getGradleHome();
    myGradleHomePathField.setText(gradleHome == null ? "" : gradleHome);
    myGradleHomePathField.getTextField().setForeground(LocationSettingType.EXPLICIT_CORRECT.getColor());
    
    updateWrapperControls(getInitialSettings().getExternalProjectPath());
    if (myUseWrapperButton.isSelected()) {
      myGradleHomePathField.setEnabled(false);
      return;
    }

    if (StringUtil.isEmpty(gradleHome)) {
      myGradleHomeSettingType = LocationSettingType.UNKNOWN;
      deduceGradleHomeIfPossible();
    }
    else {
      assert gradleHome != null;
      myGradleHomeSettingType = myInstallationManager.isGradleSdkHome(new File(gradleHome)) ?
                                LocationSettingType.EXPLICIT_CORRECT :
                                LocationSettingType.EXPLICIT_INCORRECT;
      myAlarm.cancelAllRequests();
      if (myGradleHomeSettingType == LocationSettingType.EXPLICIT_INCORRECT && getInitialSettings().isPreferLocalInstallationToWrapper()) {
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
      }
    }
  }

  public void updateWrapperControls(@Nullable String linkedProjectPath) {
    if (linkedProjectPath != null && GradleUtil.isGradleWrapperDefined(linkedProjectPath)) {
      myUseWrapperButton.setText(GradleBundle.message("gradle.settings.text.use.wrapper"));
      myUseWrapperButton.setEnabled(true);
      if (getInitialSettings().isPreferLocalInstallationToWrapper()) {
        myUseLocalDistributionButton.setSelected(true);
      }
      else {
        myUseWrapperButton.setSelected(true);
      }
    }
    else {
      myUseWrapperButton.setText(GradleBundle.message("gradle.settings.text.use.wrapper.disabled"));
      myUseWrapperButton.setEnabled(false);
      myUseLocalDistributionButton.setSelected(true);
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
    myGradleHomeModifiedByUser = false;
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
