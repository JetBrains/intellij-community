package org.jetbrains.android.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
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
        Module module = getModule();
        if (module == null) {
          Messages.showErrorDialog(AvdComboBox.this, ExecutionBundle.message("module.not.specified.error.text"));
          return;
        }

        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          Messages.showErrorDialog(AvdComboBox.this, AndroidBundle.message("no.facet.error", module.getName()));
          return;
        }

        final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
        if (platform == null) {
          Messages.showErrorDialog(AvdComboBox.this, AndroidBundle.message("android.compilation.error.specify.platform", module.getName()));
          return;
        }

        RunAndroidAvdManagerAction.runAvdManager(platform.getSdkData().getLocation());
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
      for (AvdInfo avd : facet.getAllCompatibleAvds()) {
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
}
