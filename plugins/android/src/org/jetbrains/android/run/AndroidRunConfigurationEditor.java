/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.run;

import com.android.prefs.AndroidLocation;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> {
  private JPanel myPanel;
  private JComboBox myModulesComboBox;
  private JCheckBox myChooseDeviceManually;
  private LabeledComponent<ComboboxWithBrowseButton> myChooseAvdComponent;
  private LabeledComponent<RawCommandLineEditor> myCommandLineComponent;
  private JPanel myConfigurationSpecificPanel;
  private JCheckBox myWipeUserDataCheckBox;
  private JComboBox myNetworkSpeedCombo;
  private JComboBox myNetworkLatencyCombo;
  private JCheckBox myDisableBootAnimationCombo;
  private JCheckBox myClearLogCheckBox;
  private JTabbedPane myTabbedPane;
  private ComboboxWithBrowseButton myAvdBox;
  private RawCommandLineEditor myCommandLineField;
  private String incorrectPreferredAvd;

  @NonNls private final static String[] NETWORK_SPEEDS = new String[]{"Full", "GSM", "HSCSD", "GPRS", "EDGE", "UMTS", "HSPDA"};
  @NonNls private final static String[] NETWORK_LATENCIES = new String[]{"None", "GPRS", "EDGE", "UMTS"};

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  public void setConfigurationSpecificEditor(ConfigurationSpecificEditor<T> configurationSpecificEditor) {
    myConfigurationSpecificEditor = configurationSpecificEditor;
    myConfigurationSpecificPanel.add(configurationSpecificEditor.getComponent());
  }

  public AndroidRunConfigurationEditor(final Project project) {
    myAvdBox = myChooseAvdComponent.getComponent();
    myCommandLineField = myCommandLineComponent.getComponent();
    myCommandLineField.setDialogCaption(myCommandLineComponent.getRawText());
    myCommandLineComponent.getLabel().setLabelFor(myCommandLineField.getTextField());
    myModuleSelector = new ConfigurationModuleSelector(project, myModulesComboBox);
    myChooseDeviceManually.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean enabled = !myChooseDeviceManually.isSelected();
        myChooseAvdComponent.setEnabled(enabled);
      }
    });
    myModulesComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateAvds();
      }
    });
    myAvdBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Module module = myModuleSelector.getModule();
        if (module == null) {
          Messages.showErrorDialog(myPanel, ExecutionBundle.message("module.not.specified.error.text"));
          return;
        }
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          Messages.showErrorDialog(myPanel, AndroidBundle.message("no.facet.error", module.getName()));
          return;
        }
        AvdManager manager = null;
        try {
          manager = facet.getAvdManager();
        }
        catch (AvdsNotSupportedException e1) {
          Messages.showInfoMessage(project, AndroidBundle.message("sdk.11.doesnt.support.avd.error"), CommonBundle.getErrorTitle());
        }
        catch (AndroidLocation.AndroidLocationException e1) {
          Messages.showErrorDialog(project, e1.getMessage(), CommonBundle.getErrorTitle());
        }
        if (manager != null) {
          AvdChooser chooser = new AvdChooser(project, facet, manager, false, true);
          String preferredAvdName = (String)myAvdBox.getComboBox().getSelectedItem();
          if (preferredAvdName == null) preferredAvdName = "";
          chooser.setSelectedAvd(preferredAvdName);
          chooser.show();
          updateAvds();
          AvdManager.AvdInfo avd = chooser.getSelectedAvd();
          String item = avd != null ? avd.getName() : "";
          if (chooser.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            myAvdBox.getComboBox().setSelectedItem(item);
          }
        }
      }
    });
    myNetworkSpeedCombo.setModel(new DefaultComboBoxModel(NETWORK_SPEEDS));
    myNetworkLatencyCombo.setModel(new DefaultComboBoxModel(NETWORK_LATENCIES));
  }

  private void updateAvds() {
    Module module = myModuleSelector.getModule();
    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    Object selected = myAvdBox.getComboBox().getSelectedItem();
    if (facet != null) {
      DefaultComboBoxModel model = (DefaultComboBoxModel)myAvdBox.getComboBox().getModel();
      model.removeAllElements();
      model.addElement("");
      for (AvdManager.AvdInfo avd : facet.getAllCompatibleAvds()) {
        model.addElement(avd.getName());
      }
    }
    myAvdBox.getComboBox().setSelectedItem(selected);
  }

  private static boolean containsItem(JComboBox combo, @NotNull Object item) {
    for (int i = 0, n = combo.getItemCount(); i < n; i++) {
      if (item.equals(combo.getItemAt(i))) {
        return true;
      }
    }
    return false;
  }

  protected void resetEditorFrom(T configuration) {
    myModuleSelector.reset(configuration);
    final String avd = configuration.PREFERRED_AVD;
    if (avd != null) {
      JComboBox combo = myAvdBox.getComboBox();
      if (containsItem(combo, avd)) {
        combo.setSelectedItem(avd);
      }
      else {
        combo.setRenderer(new ListCellRendererWrapper(combo.getRenderer()) {
          @Override
          public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            if (value == null) {
              setText("<html><font color='red'>" + avd + "</font></html>");
            }
          }
        });
        combo.setSelectedItem(null);
        incorrectPreferredAvd = avd;
      }
    }

    boolean selected = configuration.CHOOSE_DEVICE_MANUALLY;
    myChooseDeviceManually.setSelected(selected);
    myChooseAvdComponent.setEnabled(!selected);
    myCommandLineField.setText(configuration.COMMAND_LINE);
    myConfigurationSpecificEditor.resetFrom(configuration);
    myWipeUserDataCheckBox.setSelected(configuration.WIPE_USER_DATA);
    myDisableBootAnimationCombo.setSelected(configuration.DISABLE_BOOT_ANIMATION);
    myNetworkSpeedCombo.setSelectedItem(configuration.NETWORK_SPEED);
    myNetworkLatencyCombo.setSelectedItem(configuration.NETWORK_SPEED);
    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
  }

  protected void applyEditorTo(T configuration) throws ConfigurationException {
    myModuleSelector.applyTo(configuration);
    configuration.CHOOSE_DEVICE_MANUALLY = myChooseDeviceManually.isSelected();
    configuration.COMMAND_LINE = myCommandLineField.getText();
    configuration.PREFERRED_AVD = "";
    configuration.WIPE_USER_DATA = myWipeUserDataCheckBox.isSelected();
    configuration.DISABLE_BOOT_ANIMATION = myDisableBootAnimationCombo.isSelected();
    configuration.NETWORK_SPEED = ((String)myNetworkSpeedCombo.getSelectedItem()).toLowerCase();
    configuration.NETWORK_LATENCY = ((String)myNetworkLatencyCombo.getSelectedItem()).toLowerCase();
    configuration.CLEAR_LOGCAT = myClearLogCheckBox.isSelected();
    if (myAvdBox.isEnabled()) {
      JComboBox combo = myAvdBox.getComboBox();
      String preferredAvd = (String)combo.getSelectedItem();
      if (preferredAvd == null) {
        preferredAvd = incorrectPreferredAvd != null ? incorrectPreferredAvd : "";
      }
      configuration.PREFERRED_AVD = preferredAvd;
    }
    myConfigurationSpecificEditor.applyTo(configuration);
  }

  @NotNull
  protected JComponent createEditor() {
    return myPanel;
  }

  protected void disposeEditor() {
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }
}
