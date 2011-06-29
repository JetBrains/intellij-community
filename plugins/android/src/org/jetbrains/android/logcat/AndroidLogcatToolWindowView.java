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
package org.jetbrains.android.logcat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogConsoleListener;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.actions.AndroidEnableDdmsAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLogcatToolWindowView implements Disposable {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.logcat.AndroidLogcatToolWindowView");

  private final Project myProject;
  private JComboBox myDeviceCombo;
  private JPanel myConsoleWrapper;
  private JPanel myPanel;
  private JButton myClearLogButton;
  private JPanel mySearchComponentWrapper;
  private volatile IDevice myDevice;
  private final Object myLock = new Object();
  private final LogConsoleBase myLogConsole;

  private volatile Reader myCurrentReader;
  private volatile Writer myCurrentWriter;

  private final AndroidDebugBridge.IDeviceChangeListener myDeviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
    public void deviceConnected(IDevice device) {
      updateInUIThread();
    }

    public void deviceDisconnected(IDevice device) {
      updateInUIThread();
    }

    public void deviceChanged(IDevice device, int changeMask) {
      if ((changeMask & IDevice.CHANGE_STATE) != 0) {
        if (device == myDevice) {
          myDevice = null;
          updateInUIThread();
        }
      }
    }
  };

  private void updateInUIThread() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        updateDevices();
        updateLogConsole();
      }
    });
  }

  private class MyLoggingReader extends AndroidLoggingReader {
    @NotNull
    protected Object getLock() {
      return myLock;
    }

    protected Reader getReader() {
      return myCurrentReader;
    }
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public AndroidLogcatToolWindowView(final Project project) {
    myProject = project;
    Disposer.register(myProject, this);

    myDeviceCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateLogConsole();
      }
    });
    myDeviceCombo.setRenderer(new ListCellRendererWrapper(myDeviceCombo.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText("<html><font color='red'>[none]</font></html>");
        }
      }
    });
    LogFilterModel logFilterModel = new AndroidLogFilterModel(AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL) {
      @Override
      protected void setCustomFilter(String filter) {
        AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER = filter;
      }

      @Override
      protected void saveLogLevel(Log.LogLevel logLevel) {
        AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL = logLevel.name();
      }

      @Override
      public String getCustomFilter() {
        return AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER;
      }
    };
    myLogConsole = new LogConsoleBase(project, new MyLoggingReader() {
    }, null, false, logFilterModel) {
      @Override
      public boolean isActive() {
        return AndroidLogcatToolWindowView.this.isActive();
      }
    };
    myLogConsole.addListener(new LogConsoleListener() {
      @Override
      public void loggingWillBeStopped() {
        if (myCurrentWriter != null) {
          try {
            myCurrentWriter.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    });
    mySearchComponentWrapper.add(myLogConsole.getSearchComponent());
    JComponent consoleComponent = myLogConsole.getComponent();
    DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(myLogConsole.getToolbarActions());
    group.add(new AndroidEnableDdmsAction(AndroidUtils.DDMS_ICON));
    group.add(new MyRestartAction());
    final JComponent tbComp =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false).getComponent();
    myConsoleWrapper.add(tbComp, BorderLayout.WEST);
    myConsoleWrapper.add(consoleComponent, BorderLayout.CENTER);
    Disposer.register(this, myLogConsole);
    myClearLogButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
        if (device != null) {
          AndroidLogcatUtil.clearLogcat(project, device);
          myLogConsole.clear();
        }
      }
    });

    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener);

    updateDevices();
    updateLogConsole();
  }

  protected abstract boolean isActive();

  public void activate() {
    updateDevices();
    updateLogConsole();
    if (myLogConsole != null) {
      myLogConsole.activate();
    }
  }

  private void updateLogConsole() {
    IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
    if (myDevice != device) {
      synchronized (myLock) {
        myDevice = device;
        if (myCurrentWriter != null) {
          try {
            myCurrentWriter.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        if (myCurrentReader != null) {
          try {
            myCurrentReader.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        if (device != null) {
          final Pair<Reader,Writer> pair = AndroidLogcatUtil.startLoggingThread(myProject, device, false, myLogConsole);
          if (pair != null) {
            myCurrentReader = pair.first;
            myCurrentWriter = pair.second;
          }
          else {
            myCurrentReader = null;
            myCurrentWriter = null;
          }
        }
      }
    }
  }

  @Nullable
  private static AndroidPlatform getAndroidPlatform(@NotNull Project project) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      if (platform != null) {
        return platform;
      }
    }
    return null;
  }

  private void updateDevices() {
    AndroidPlatform platform = getAndroidPlatform(myProject);
    if (platform != null) {
      final AndroidDebugBridge debugBridge = platform.getSdk().getDebugBridge(myProject);
      if (debugBridge != null) {
        IDevice[] devices = debugBridge.getDevices();
        Object temp = myDeviceCombo.getSelectedItem();
        myDeviceCombo.setModel(new DefaultComboBoxModel(devices));
        if (devices.length > 0 && temp == null) {
          temp = devices[0];
        }
        myDeviceCombo.setSelectedItem(temp);
      }
    }
    else {
      myDeviceCombo.setModel(new DefaultComboBoxModel());
    }
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener);
  }

  private class MyRestartAction extends AnAction {
    public MyRestartAction() {
      super(AndroidBundle.message("android.restart.logcat.action.text"), AndroidBundle.message("android.restart.logcat.action.description"),
            AndroidUtils.RESTART_LOGCAT_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myDevice = null;
      updateLogConsole();
    }
  }
}
