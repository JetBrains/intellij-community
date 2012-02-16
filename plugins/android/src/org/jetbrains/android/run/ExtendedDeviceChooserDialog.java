package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HtmlListCellRenderer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public class ExtendedDeviceChooserDialog extends DialogWrapper {
  private final Project myProject;
  private final DeviceChooser myDeviceChooser;

  private JPanel myPanel;
  private JRadioButton myChooserRunningDeviceRadioButton;
  private JPanel myDeviceChooserWrapper;
  private JRadioButton myLaunchEmulatorRadioButton;
  private JPanel myComboBoxWrapper;
  private JLabel myAvdLabel;
  private final AvdComboBox myAvdCombo;

  @NonNls private static final String SELECTED_SERIALS_PROPERTY = "ANDROID_EXTENDED_DEVICE_CHOOSER_SERIALS";
  @NonNls private static final String SELECTED_AVD_PROPERTY = "ANDROID_EXTENDED_DEVICE_CHOOSER_AVD";

  public ExtendedDeviceChooserDialog(@NotNull final AndroidFacet facet,
                                     boolean multipleSelection) {
    super(facet.getModule().getProject(), true);
    setTitle(AndroidBundle.message("choose.device.dialog.title"));

    myProject = facet.getModule().getProject();
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    final String[] selectedSerials;
    final String serialsStr = properties.getValue(SELECTED_SERIALS_PROPERTY);
    if (serialsStr != null) {
      selectedSerials = serialsStr.split(" ");
    }
    else {
      selectedSerials = null;
    }

    getOKAction().setEnabled(false);

    myDeviceChooser = new DeviceChooser(multipleSelection, getOKAction(), facet, null);
    Disposer.register(myDisposable, myDeviceChooser);
    myDeviceChooser.addListener(new DeviceChooserListener() {
      @Override
      public void selectedDevicesChanged() {
        updateOkButton();
      }
    });
    
    myAvdCombo = new AvdComboBox(false, true) {
      @Override
      public Module getModule() {
        return facet.getModule();
      }
    };
    Disposer.register(myDisposable, myAvdCombo);


    myAvdCombo.getComboBox().setRenderer(new HtmlListCellRenderer(myAvdCombo.getComboBox().getRenderer()) {
      @Override
      protected void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText(myAvdCombo.getComboBox().isEnabled()
                  ? "<html><font color='red'>[none]</font></html>"
                  : "[none]");
        }
      }
    });
    myComboBoxWrapper.add(myAvdCombo);
    myAvdLabel.setLabelFor(myAvdCombo);
    myDeviceChooserWrapper.add(myDeviceChooser.getPanel());

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabled();
      }
    };
    myLaunchEmulatorRadioButton.addActionListener(listener);
    myChooserRunningDeviceRadioButton.addActionListener(listener);

    init();

    myDeviceChooser.init(selectedSerials);
    myLaunchEmulatorRadioButton.setSelected(myDeviceChooser.getDeviceTable().getRowCount() == 0);
    myChooserRunningDeviceRadioButton.setSelected(myDeviceChooser.getDeviceTable().getRowCount() > 0);

    myAvdCombo.startUpdatingAvds(ModalityState.stateForComponent(myPanel));
    final String savedAvd = properties.getValue(SELECTED_AVD_PROPERTY);
    String avdToSelect = null;
    if (savedAvd != null) {
      final ComboBoxModel model = myAvdCombo.getComboBox().getModel();
      for (int i = 0, n = model.getSize(); i < n; i++) {
        final String item = (String)model.getElementAt(i);
        if (savedAvd.equals(item)) {
          avdToSelect = item;
          break;
        }
      }
    }
    if (avdToSelect != null) {
      myAvdCombo.getComboBox().setSelectedItem(avdToSelect);
    }
    else if (myAvdCombo.getComboBox().getModel().getSize() > 0) {
      myAvdCombo.getComboBox().setSelectedIndex(0);
    }

    updateEnabled();
  }
  
  private void updateOkButton() {
    if (myLaunchEmulatorRadioButton.isSelected()) {
      getOKAction().setEnabled(getSelectedAvd() != null);
    }
    else {
      getOKAction().setEnabled(getSelectedDevices().length > 0);
    }
  }

  private void updateEnabled() {
    myAvdCombo.setEnabled(myLaunchEmulatorRadioButton.isSelected());
    myAvdLabel.setEnabled(myLaunchEmulatorRadioButton.isSelected());
    myDeviceChooser.setEnabled(myChooserRunningDeviceRadioButton.isSelected());
    updateOkButton();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDeviceChooser.getDeviceTable();
  }

  @Override
  protected void doOKAction() {
    myDeviceChooser.finish();

    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    properties.setValue(SELECTED_SERIALS_PROPERTY, AndroidRunningState.toString(myDeviceChooser.getSelectedDevices()));

    final String selectedAvd = (String)myAvdCombo.getComboBox().getSelectedItem();
    if (selectedAvd != null) {
      properties.setValue(SELECTED_AVD_PROPERTY, selectedAvd);
    }
    else {
      properties.unsetValue(SELECTED_AVD_PROPERTY);
    }

    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public IDevice[] getSelectedDevices() {
    return myDeviceChooser.getSelectedDevices();
  }

  @Nullable
  public String getSelectedAvd() {
    return (String)myAvdCombo.getComboBox().getSelectedItem();
  }

  public boolean isToLaunchEmulator() {
    return myLaunchEmulatorRadioButton.isSelected();
  }
}
