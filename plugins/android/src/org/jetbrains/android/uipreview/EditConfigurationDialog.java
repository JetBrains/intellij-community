package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings("unchecked")
public class EditConfigurationDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.EditConfigurationDialog");

  private JPanel myDevicePanel;
  private JTextField myConfigNameField;
  private JPanel myContentPanel;
  private JTextPane myConfigurationTextPane;
  private JPanel myDeviceConfiguratorPanelWrapper;

  private final EditDeviceForm myEditDeviceForm = new EditDeviceForm();
  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;

  public EditConfigurationDialog(@NotNull Project project, @Nullable Object deviceOfConfig) {
    super(project);

    myConfigurationTextPane.setOpaque(false);

    LayoutDevice device = null;
    FolderConfiguration config = null;

    if (deviceOfConfig instanceof LayoutDevice) {
      device = (LayoutDevice)deviceOfConfig;
    }
    else if (deviceOfConfig instanceof LayoutDeviceConfiguration) {
      final LayoutDeviceConfiguration deviceConfig = (LayoutDeviceConfiguration)deviceOfConfig;
      device = deviceConfig.getDevice();
      config = deviceConfig.getConfiguration();
      myConfigNameField.setText(deviceConfig.getName());
    }

    myDevicePanel.add(myEditDeviceForm.getContentPanel());

    if (device != null) {
      myEditDeviceForm.reset(device);
    }

    myDeviceConfiguratorPanel = new DeviceConfiguratorPanel(config) {
      @Override
      public void applyEditors() {
        try {
          if (myEditDeviceForm.getName().length() == 0) {
            throw new InvalidOptionValueException("specify device name");
          }

          if (myConfigNameField.getText().length() == 0) {
            throw new InvalidOptionValueException("specify configuration name");
          }

          doApplyEditors();
        }
        catch (InvalidOptionValueException e) {
          LOG.debug(e);
          myConfigurationTextPane.setText("Error: " + e.getMessage());
          setOKActionEnabled(false);
          return;
        }
        myConfigurationTextPane.setText("Configuration: " + getConfiguration().toDisplayString());
        setOKActionEnabled(true);
      }

      @Override
      protected void createDefaultConfig(FolderConfiguration config) {
        super.createDefaultConfig(config);
        config.setLanguageQualifier(null);
        config.setVersionQualifier(null);
        config.setNightModeQualifier(null);
        config.setUiModeQualifier(null);
        config.setRegionQualifier(null);
      }
    };
    myDeviceConfiguratorPanelWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);

    myEditDeviceForm.getNameField().getDocument().addDocumentListener(myDeviceConfiguratorPanel.getUpdatingDocumentListener());
    myConfigNameField.getDocument().addDocumentListener(myDeviceConfiguratorPanel.getUpdatingDocumentListener());

    myDeviceConfiguratorPanel.updateAll();

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public EditDeviceForm getEditDeviceForm() {
    return myEditDeviceForm;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    myDeviceConfiguratorPanel.applyEditors();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myEditDeviceForm.getNameField().getText().length() == 0) {
      return myEditDeviceForm.getNameField();
    }
    return myConfigNameField;
  }

  @NotNull
  public String getConfigName() {
    return myConfigNameField.getText();
  }

  @NotNull
  public FolderConfiguration getConfiguration() {
    return myDeviceConfiguratorPanel.getConfiguration();
  }
}

