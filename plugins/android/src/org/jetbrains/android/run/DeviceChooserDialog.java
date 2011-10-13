package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene.Kudelevsky
 */
public class DeviceChooserDialog extends DialogWrapper {
  private final AndroidFacet myFacet;
  private final boolean myShowLaunchEmulator;
  private final DeviceChooser myDeviceChooser;

  public DeviceChooserDialog(@NotNull AndroidFacet facet,
                             boolean multipleSelection,
                             @Nullable String[] selectedSerials,
                             @Nullable Condition<IDevice> filter,
                             boolean showLaunchEmulator) {
    super(facet.getModule().getProject(), true);
    setTitle(AndroidBundle.message("choose.device.dialog.title"));
    myFacet = facet;
    myShowLaunchEmulator = showLaunchEmulator;

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

  @Override
  protected Action[] createActions() {
    return myShowLaunchEmulator
           ? new Action[]{new RefreshAction(), new LaunchEmulatorAction(), getOKAction(), getCancelAction()}
           : new Action[]{new RefreshAction(), getOKAction(), getCancelAction()};
  }

  public IDevice[] getSelectedDevices() {
    return myDeviceChooser.getSelectedDevices();
  }

  private class LaunchEmulatorAction extends AbstractAction {
    public LaunchEmulatorAction() {
      putValue(NAME, "Launch Emulator");
    }

    public void actionPerformed(ActionEvent e) {
      AvdManager.AvdInfo avd = null;
      AvdManager manager = myFacet.getAvdManagerSilently();
      if (manager != null) {
        AvdChooser chooser = new AvdChooser(myFacet.getModule().getProject(), myFacet, manager, true, false);
        chooser.show();
        avd = chooser.getSelectedAvd();
        if (chooser.getExitCode() != OK_EXIT_CODE) return;
        if (avd == null) return;
      }
      myFacet.launchEmulator(avd != null ? avd.getName() : null, "", null);
    }
  }

  private class RefreshAction extends AbstractAction {
    RefreshAction() {
      putValue(NAME, "Refresh");
    }

    public void actionPerformed(ActionEvent e) {
      myDeviceChooser.updateTable();
    }
  }
}
