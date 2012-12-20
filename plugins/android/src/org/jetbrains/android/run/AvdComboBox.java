package org.jetbrains.android.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.ComponentBasedErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AvdComboBox extends ComboboxWithBrowseButton {
  private final boolean myAddEmptyElement;
  private final boolean myShowNotLaunchedOnly;
  private final Alarm myAlarm = new Alarm(this);
  private String[] myOldAvds = ArrayUtil.EMPTY_STRING_ARRAY;

  public AvdComboBox(boolean addEmptyElement, boolean showNotLaunchedOnly) {
    myAddEmptyElement = addEmptyElement;
    myShowNotLaunchedOnly = showNotLaunchedOnly;

    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final AndroidPlatform platform = findAndroidPlatform();
        if (platform == null) {
          Messages.showErrorDialog(AvdComboBox.this, "Cannot find any configured Android SDK");
          return;
        }

        RunAndroidAvdManagerAction.runAvdManager(platform.getSdkData().getLocation(), new ComponentBasedErrorReporter(AvdComboBox.this),
                                                 ModalityState.stateForComponent(AvdComboBox.this));
      }
    });

    setMinimumSize(new Dimension(100, getMinimumSize().height));
  }


  public void startUpdatingAvds(@NotNull ModalityState modalityState) {
    if (!getComboBox().isPopupVisible()) {
      doUpdateAvds();
    }
    addUpdatingRequest(modalityState);
  }

  private void addUpdatingRequest(@NotNull final ModalityState modalityState) {
    if (myAlarm.isDisposed()) {
      return;
    }
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        startUpdatingAvds(modalityState);
      }
    }, 500, modalityState);
  }

  @Override
  public void dispose() {
    myAlarm.cancelAllRequests();
    super.dispose();
  }

  private void doUpdateAvds() {
    final Module module = getModule();
    final AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    final String[] newAvds;

    if (facet != null) {
      final Set<String> filteringSet = new HashSet<String>();
      if (myShowNotLaunchedOnly) {
        final AndroidDebugBridge debugBridge = facet.getDebugBridge();
        if (debugBridge != null) {
          for (IDevice device : debugBridge.getDevices()) {
            final String avdName = device.getAvdName();
            if (avdName != null && avdName.length() > 0) {
              filteringSet.add(avdName);
            }
          }
        }
      }

      final List<String> newAvdList = new ArrayList<String>();
      if (myAddEmptyElement) {
        newAvdList.add("");
      }
      for (AvdInfo avd : facet.getAllAvds()) {
        final String avdName = avd.getName();
        if (!filteringSet.contains(avdName)) {
          newAvdList.add(avdName);
        }
      }

      newAvds = newAvdList.toArray(new String[newAvdList.size()]);
    }
    else {
      newAvds = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    if (!Arrays.equals(myOldAvds, newAvds)) {
      myOldAvds = newAvds;
      final Object selected = getComboBox().getSelectedItem();
      getComboBox().setModel(new DefaultComboBoxModel(newAvds));
      getComboBox().setSelectedItem(selected);
    }
  }

  @Nullable
  public abstract Module getModule();

  @Nullable
  private AndroidPlatform findAndroidPlatform() {
    AndroidPlatform platform = findAndroidPlatformFromModule();
    if (platform != null) {
       return platform;
    }

    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (sdk.getSdkType() instanceof AndroidSdkType) {
        final SdkAdditionalData data = sdk.getSdkAdditionalData();
        if (data instanceof AndroidSdkAdditionalData) {
          platform = ((AndroidSdkAdditionalData)data).getAndroidPlatform();
          if (platform != null) {
            return platform;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private AndroidPlatform findAndroidPlatformFromModule() {
    Module module = getModule();
    if (module == null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    return facet.getConfiguration().getAndroidPlatform();
  }
}
