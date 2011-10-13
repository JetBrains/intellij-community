package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class DeviceChooserDialog extends DialogWrapper {
  private final DeviceChooser myDeviceChooser;

  public DeviceChooserDialog(@NotNull AndroidFacet facet,
                             boolean multipleSelection,
                             @Nullable String[] selectedSerials,
                             @Nullable Condition<IDevice> filter) {
    super(facet.getModule().getProject(), true);
    setTitle(AndroidBundle.message("choose.device.dialog.title"));

    getOKAction().setEnabled(false);

    myDeviceChooser = new DeviceChooser(multipleSelection, getOKAction(), facet, filter);
    Disposer.register(myDisposable, myDeviceChooser);
    myDeviceChooser.addListener(new DeviceChooserListener() {
      @Override
      public void selectedDevicesChanged() {
        updateOkButton();
      }
    });

    init();
    myDeviceChooser.init(selectedSerials);
  }

  private void updateOkButton() {
    IDevice[] devices = getSelectedDevices();
    boolean enabled = devices.length > 0;
    for (IDevice device : devices) {
      if (!device.isOnline()) {
        enabled = false;
      }
    }
    getOKAction().setEnabled(enabled);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDeviceChooser.getDeviceTable();
  }

  @Override
  protected void doOKAction() {
    myDeviceChooser.finish();
    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    return myDeviceChooser.getPanel();
  }

  public IDevice[] getSelectedDevices() {
    return myDeviceChooser.getSelectedDevices();
  }
}
