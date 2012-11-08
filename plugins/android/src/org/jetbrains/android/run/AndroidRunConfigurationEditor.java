/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> implements PanelWithAnchor {

  private JPanel myPanel;
  private JComboBox myModulesComboBox;
  private LabeledComponent<RawCommandLineEditor> myCommandLineComponent;
  private JPanel myConfigurationSpecificPanel;
  private JCheckBox myWipeUserDataCheckBox;
  private JComboBox myNetworkSpeedCombo;
  private JComboBox myNetworkLatencyCombo;
  private JCheckBox myDisableBootAnimationCombo;
  private JCheckBox myClearLogCheckBox;
  private JBLabel myModuleJBLabel;
  private JRadioButton myShowChooserRadioButton;
  private JRadioButton myEmulatorRadioButton;
  private JRadioButton myUsbDeviceRadioButton;
  private LabeledComponent<AvdComboBox> myAvdComboComponent;
  private JBLabel myMinSdkInfoMessageLabel;
  private AvdComboBox myAvdCombo;
  private RawCommandLineEditor myCommandLineField;
  private String incorrectPreferredAvd;
  private JComponent anchor;

  @NonNls private final static String[] NETWORK_SPEEDS = new String[]{"Full", "GSM", "HSCSD", "GPRS", "EDGE", "UMTS", "HSPDA"};
  @NonNls private final static String[] NETWORK_LATENCIES = new String[]{"None", "GPRS", "EDGE", "UMTS"};

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  public void setConfigurationSpecificEditor(ConfigurationSpecificEditor<T> configurationSpecificEditor) {
    myConfigurationSpecificEditor = configurationSpecificEditor;
    myConfigurationSpecificPanel.add(configurationSpecificEditor.getComponent());
    setAnchor(myConfigurationSpecificEditor.getAnchor());
  }

  public AndroidRunConfigurationEditor(final Project project) {
    myCommandLineField = myCommandLineComponent.getComponent();
    myCommandLineField.setDialogCaption(myCommandLineComponent.getRawText());
    myCommandLineComponent.getLabel().setLabelFor(myCommandLineField.getTextField());
    myModuleSelector = new ConfigurationModuleSelector(project, myModulesComboBox) {
      @Override
      public boolean isModuleAccepted(Module module) {
        if (module == null || !super.isModuleAccepted(module)) {
          return false;
        }
        final AndroidFacet facet = AndroidFacet.getInstance(module);
        return facet != null && !facet.getConfiguration().LIBRARY_PROJECT;
      }
    };

    myAvdComboComponent.setComponent(new AvdComboBox(true, false) {
      @Override
      public Module getModule() {
        return getModuleSelector().getModule();
      }
    });
    myAvdCombo = myAvdComboComponent.getComponent();

    myMinSdkInfoMessageLabel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    myMinSdkInfoMessageLabel.setIcon(AllIcons.General.Warning);
    myMinSdkInfoMessageLabel.setDisabledIcon(AllIcons.General.Warning);

    Disposer.register(this, myAvdCombo);

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean enabled = myEmulatorRadioButton.isSelected();
        myAvdComboComponent.setEnabled(enabled);
        myMinSdkInfoMessageLabel.setEnabled(enabled);
      }
    };
    myModulesComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myAvdCombo.startUpdatingAvds(ModalityState.current());
      }
    });
    myShowChooserRadioButton.addActionListener(listener);
    myEmulatorRadioButton.addActionListener(listener);
    myUsbDeviceRadioButton.addActionListener(listener);

    myNetworkSpeedCombo.setModel(new DefaultComboBoxModel(NETWORK_SPEEDS));
    myNetworkLatencyCombo.setModel(new DefaultComboBoxModel(NETWORK_LATENCIES));
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myModuleJBLabel.setAnchor(anchor);
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
      JComboBox combo = myAvdCombo.getComboBox();
      if (containsItem(combo, avd)) {
        combo.setSelectedItem(avd);
      }
      else {
        combo.setRenderer(new ListCellRendererWrapper() {
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

    final TargetSelectionMode targetSelectionMode = configuration.getTargetSelectionMode();

    myShowChooserRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.SHOW_DIALOG);
    myEmulatorRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.EMULATOR);
    myUsbDeviceRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.USB_DEVICE);

    myAvdComboComponent.setEnabled(targetSelectionMode == TargetSelectionMode.EMULATOR);
    myMinSdkInfoMessageLabel.setEnabled(targetSelectionMode == TargetSelectionMode.EMULATOR);
    
    myCommandLineField.setText(configuration.COMMAND_LINE);
    myConfigurationSpecificEditor.resetFrom(configuration);
    myWipeUserDataCheckBox.setSelected(configuration.WIPE_USER_DATA);
    myDisableBootAnimationCombo.setSelected(configuration.DISABLE_BOOT_ANIMATION);
    selectItemCaseInsensitively(myNetworkSpeedCombo, configuration.NETWORK_SPEED);
    selectItemCaseInsensitively(myNetworkLatencyCombo, configuration.NETWORK_LATENCY);
    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
  }

  private static void selectItemCaseInsensitively(@NotNull JComboBox comboBox, @Nullable String item) {
    if (item == null) {
      comboBox.setSelectedItem(null);
      return;
    }

    final ComboBoxModel model = comboBox.getModel();

    for (int i = 0, n = model.getSize(); i < n; i++) {
      final Object element = model.getElementAt(i);
      if (element instanceof String && item.equalsIgnoreCase((String)element)) {
        comboBox.setSelectedItem(element);
        return;
      }
    }
  }

  protected void applyEditorTo(T configuration) throws ConfigurationException {
    myModuleSelector.applyTo(configuration);

    if (myShowChooserRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.SHOW_DIALOG);
    }
    else if (myEmulatorRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.EMULATOR);
    }
    else if (myUsbDeviceRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.USB_DEVICE);
    }

    configuration.COMMAND_LINE = myCommandLineField.getText();
    configuration.PREFERRED_AVD = "";
    configuration.WIPE_USER_DATA = myWipeUserDataCheckBox.isSelected();
    configuration.DISABLE_BOOT_ANIMATION = myDisableBootAnimationCombo.isSelected();
    configuration.NETWORK_SPEED = ((String)myNetworkSpeedCombo.getSelectedItem()).toLowerCase();
    configuration.NETWORK_LATENCY = ((String)myNetworkLatencyCombo.getSelectedItem()).toLowerCase();
    configuration.CLEAR_LOGCAT = myClearLogCheckBox.isSelected();
    if (myAvdComboComponent.isEnabled()) {
      JComboBox combo = myAvdCombo.getComboBox();
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
