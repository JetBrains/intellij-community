/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.newProject;

import com.android.prefs.AndroidLocation;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.ComponentBasedErrorReporter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 26, 2009
 * Time: 7:43:13 PM
 */
public class AndroidModuleWizardStep extends ModuleWizardStep {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.newProject.AndroidModuleWizardStep");

  private final AndroidAppPropertiesEditor myAppPropertiesEditor;

  @Nullable
  private final AndroidTestPropertiesEditor myTestPropertiesEditor;

  private final AndroidModuleBuilder myModuleBuilder;
  private final WizardContext myContext;
  private final ProjectType myProjectType;

  private JPanel myPanel;
  private JPanel myPropertiesPanel;

  private JRadioButton myDoNotCreateConfigurationRadioButton;
  private JRadioButton myShowDeviceChooserRadioButton;
  private JRadioButton myUSBDeviceRadioButton;
  private JRadioButton myEmulatorRadioButton;
  private LabeledComponent<ComboboxWithBrowseButton> myAvdComboComponent;
  private JPanel myDeploymentTargetPanel;

  private final ComboboxWithBrowseButton myAvdCombo;
  private final Alarm myAvdsUpdatingAlarm = new Alarm();

  private String[] myOldAvds = ArrayUtil.EMPTY_STRING_ARRAY;

  @NonNls private static final String TARGET_SELECTION_MODE_FOR_NEW_MODULE_PROPERTY = "ANDROID_TARGET_SELECTION_MODE_FOR_NEW_MODULE";
  @NonNls private static final String TARGET_AVD_FOR_NEW_MODULE_PROPERTY = "ANDROID_TARGET_AVD_FOR_NEW_MODULE";
  private boolean myInitialized;

  public AndroidModuleWizardStep(@NotNull AndroidModuleBuilder moduleBuilder,
                                 final WizardContext context,
                                 ModulesProvider modulesProvider, ProjectType projectType) {
    super();
    myContext = context;
    myProjectType = projectType;
    myModuleBuilder = moduleBuilder;
    myAvdCombo = myAvdComboComponent.getComponent();

    myAppPropertiesEditor = new AndroidAppPropertiesEditor(moduleBuilder.getName(), modulesProvider);
    Project project = context.getProject();
    if (project == null && projectType == ProjectType.TEST) {
      myTestPropertiesEditor = null;
      return;
    }
    myTestPropertiesEditor = projectType == ProjectType.TEST ? new AndroidTestPropertiesEditor(project) : null;
    myPropertiesPanel.setLayout(new OverlayLayout(myPropertiesPanel));
    if (myTestPropertiesEditor != null) {
      myPropertiesPanel.add(myTestPropertiesEditor.getContentPanel());
      myTestPropertiesEditor.getContentPanel().setVisible(false);
    }
    myPropertiesPanel.add(myAppPropertiesEditor.getContentPanel());


    final ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAvdComboComponent.setEnabled(myEmulatorRadioButton.isSelected());
      }
    };

    myEmulatorRadioButton.addActionListener(l);
    myDoNotCreateConfigurationRadioButton.addActionListener(l);
    myShowDeviceChooserRadioButton.addActionListener(l);
    myUSBDeviceRadioButton.addActionListener(l);


    myAvdCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Sdk selectedSdk = context.getProjectJdk();
        if (selectedSdk == null || !(selectedSdk.getSdkType() instanceof AndroidSdkType)) {
          Messages.showErrorDialog(myPanel, AndroidBundle.message("specify.platform.error"));
          return;
        }

        final AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)selectedSdk.getSdkAdditionalData();
        if (sdkAdditionalData == null) {
          Messages.showErrorDialog(myPanel, AndroidBundle.message("android.wizard.invalid.sdk.error"));
          return;
        }

        final AndroidPlatform platform = sdkAdditionalData.getAndroidPlatform();
        if (platform == null) {
          Messages.showErrorDialog(myPanel, AndroidBundle.message("cannot.parse.sdk.error"));
          return;
        }

        RunAndroidAvdManagerAction.runAvdManager(platform.getSdkData().getLocation(), new ComponentBasedErrorReporter(myPanel),
                                                 ModalityState.stateForComponent(myPanel));
      }
    });

    myAvdCombo.setMinimumSize(new Dimension(100, myAvdCombo.getMinimumSize().height));

    final PropertiesComponent properties = PropertiesComponent.getInstance();
    final String targetSelectionModeStr = properties.getValue(TARGET_SELECTION_MODE_FOR_NEW_MODULE_PROPERTY);

    if (targetSelectionModeStr != null) {
      if (targetSelectionModeStr.length() > 0) {
        try {
          final TargetSelectionMode targetSelectionMode = TargetSelectionMode.valueOf(targetSelectionModeStr);
          switch (targetSelectionMode) {
            case SHOW_DIALOG:
              myShowDeviceChooserRadioButton.setSelected(true);
              break;
            case EMULATOR:
              myEmulatorRadioButton.setSelected(true);
              break;
            case USB_DEVICE:
              myUSBDeviceRadioButton.setSelected(true);
              break;
            default:
              assert false : "Unknown target selection mode " + targetSelectionMode;
          }
        }
        catch (IllegalArgumentException ignored) {
        }
      }
      else {
        myDoNotCreateConfigurationRadioButton.setSelected(true);
      }
    }
    updatePropertiesEditor();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myAvdsUpdatingAlarm);
  }

  private void updateDeploymentTargetPanel() {
    final boolean enabled = myProjectType == ProjectType.APPLICATION || myProjectType == ProjectType.TEST;
    UIUtil.setEnabled(myDeploymentTargetPanel, enabled, true);
    if (enabled) {
      myAvdComboComponent.setEnabled(myEmulatorRadioButton.isSelected());
    }
  }

  private void updatePropertiesEditor() {
    if (myProjectType == ProjectType.APPLICATION || myProjectType == ProjectType.LIBRARY) {
      myAppPropertiesEditor.getContentPanel().setVisible(true);
      if (myTestPropertiesEditor != null) {
        myTestPropertiesEditor.getContentPanel().setVisible(false);
      }
      myAppPropertiesEditor.update(myProjectType == ProjectType.APPLICATION);
    }
    else {
      myAppPropertiesEditor.getContentPanel().setVisible(false);
      assert myTestPropertiesEditor != null;
      myTestPropertiesEditor.getContentPanel().setVisible(true);
    }
  }

  @Override
  public void updateStep() {
    if (!myInitialized) {
      myInitialized = true;
      initialize();
    }
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private void initialize() {
    final String moduleName = myModuleBuilder.getName();

    if (moduleName != null) {
      myAppPropertiesEditor.getApplicationNameField().setText(moduleName);
      myAppPropertiesEditor.getPackageNameField().setText(AndroidAppPropertiesEditor.getDefaultPackageNameByModuleName(moduleName));
    }
    final PropertiesComponent properties = PropertiesComponent.getInstance();

    boolean shouldReset = myAvdCombo.getComboBox().getSelectedItem() == null;
    startUpdatingAvds();
    if (shouldReset) {
      final String targetAvd = properties.getValue(TARGET_AVD_FOR_NEW_MODULE_PROPERTY);
      if (targetAvd != null && targetAvd.length() > 0) {
        myAvdCombo.getComboBox().setSelectedItem(targetAvd);
      }
    }

    updateDeploymentTargetPanel();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (myProjectType == ProjectType.APPLICATION || myProjectType == ProjectType.LIBRARY) {
      myAppPropertiesEditor.validate(myProjectType == ProjectType.LIBRARY);
    }
    else {
      assert myTestPropertiesEditor != null;
      myTestPropertiesEditor.validate();
    }

    return true;
  }

  public void updateDataModel() {

    final PropertiesComponent properties = PropertiesComponent.getInstance();

    if (myProjectType == ProjectType.APPLICATION || myProjectType == ProjectType.LIBRARY) {
      myModuleBuilder.setActivityName(myAppPropertiesEditor.getActivityName());
      myModuleBuilder.setPackageName(myAppPropertiesEditor.getPackageName());
      myModuleBuilder.setApplicationName(myAppPropertiesEditor.getApplicationName());
    }
    else {
      assert myTestPropertiesEditor != null;
      myModuleBuilder.setTestedModule(myTestPropertiesEditor.getModule());
    }
    
    if (myProjectType == ProjectType.APPLICATION || myProjectType == ProjectType.TEST) {
      String preferredAvdName = null;
      TargetSelectionMode targetSelectionMode = null;
      
      if (myEmulatorRadioButton.isSelected()) {
        preferredAvdName = (String)myAvdCombo.getComboBox().getSelectedItem();
        targetSelectionMode = TargetSelectionMode.EMULATOR;
      }
      else if (myShowDeviceChooserRadioButton.isSelected()) {
        targetSelectionMode = TargetSelectionMode.SHOW_DIALOG;
      }
      else if (myUSBDeviceRadioButton.isSelected()) {
        targetSelectionMode = TargetSelectionMode.USB_DEVICE;
      }
      
      myModuleBuilder.setTargetSelectionMode(targetSelectionMode);
      myModuleBuilder.setPreferredAvd(preferredAvdName);
      
      properties.setValue(TARGET_SELECTION_MODE_FOR_NEW_MODULE_PROPERTY, targetSelectionMode != null ? targetSelectionMode.name() : "");
      properties.setValue(TARGET_AVD_FOR_NEW_MODULE_PROPERTY, preferredAvdName != null ? preferredAvdName : "");
    }
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.android";
  }

  private void startUpdatingAvds() {
    if (!myAvdCombo.getComboBox().isPopupVisible()) {
      doUpdateAvds();
    }
    addUpdatingRequest();
  }

  private void addUpdatingRequest() {
    if (myAvdsUpdatingAlarm.isDisposed()) {
      return;
    }
    myAvdsUpdatingAlarm.cancelAllRequests();
    myAvdsUpdatingAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        startUpdatingAvds();
      }
    }, 500);
  }

  private void doUpdateAvds() {
    final Sdk selectedSdk = myContext.getProjectJdk();

    String[] newAvds = ArrayUtil.EMPTY_STRING_ARRAY;

    if (selectedSdk != null && selectedSdk.getSdkType() instanceof AndroidSdkType) {
      final AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)selectedSdk.getSdkAdditionalData();
      if (sdkAdditionalData != null) {
        final AndroidPlatform androidPlatform = sdkAdditionalData.getAndroidPlatform();
        if (androidPlatform != null) {
          newAvds = getAvds(androidPlatform);
        }
      }
    }

    if (!Arrays.equals(myOldAvds, newAvds)) {
      myOldAvds = newAvds;
      final JComboBox combo = myAvdCombo.getComboBox();
      final Object selected = combo.getSelectedItem();
      combo.setModel(new DefaultComboBoxModel(newAvds));
      combo.setSelectedItem(selected);
    }
  }

  @NotNull
  private static String[] getAvds(@NotNull AndroidPlatform androidPlatform) {
    final AndroidSdkData sdkData = androidPlatform.getSdkData();
    final SdkManager sdkManager = sdkData.getSdkManager();
    try {
      final AvdManager avdManager = AvdManager.getInstance(sdkManager, new MessageBuildingSdkLog());
      final AvdInfo[] validAvds = avdManager.getValidAvds();

      final String[] avdNames = new String[validAvds.length];
      for (int i = 0; i < validAvds.length; i++) {
        avdNames[i] = validAvds[i].getName();
      }
      return avdNames;
    }
    catch (AndroidLocation.AndroidLocationException e) {
      LOG.info(e);
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }
}
