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
package org.jetbrains.android.logcat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.intellij.CommonBundle;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogConsoleListener;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IconUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLogcatToolWindowView implements Disposable {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.logcat.AndroidLogcatToolWindowView");
  static final String EMPTY_CONFIGURED_FILTER = "All messages";
  public static final Key<AndroidLogcatToolWindowView> ANDROID_LOGCAT_VIEW_KEY = Key.create("ANDROID_LOGCAT_VIEW_KEY");

  private final Project myProject;
  private JComboBox myDeviceCombo;
  private JPanel myConsoleWrapper;
  private final Splitter mySplitter;
  private JPanel mySearchComponentWrapper;
  private JPanel myFiltersToolbarPanel;

  private JBList myFiltersList;
  private JPanel myLeftPanel;
  private JPanel myRightPanel;
  private JPanel myTopPanel;
  private JBScrollPane myFiltersListScrollPane;

  private volatile IDevice myDevice;
  private final Object myLock = new Object();
  private final LogConsoleBase myLogConsole;

  private volatile Reader myCurrentReader;
  private volatile Writer myCurrentWriter;

  private final IDevice myPreselectedDevice;

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
        if (myProject.isDisposed()) {
          return;
        }

        updateDevices();
        updateLogConsole();
      }
    });
  }

  Project getProject() {
    return myProject;
  }

  @NotNull
  public LogConsoleBase getLogConsole() {
    return myLogConsole;
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
  public AndroidLogcatToolWindowView(final Project project, @Nullable IDevice preselectedDevice, boolean addBorderToScrollPane) {
    myProject = project;
    myPreselectedDevice = preselectedDevice;
    Disposer.register(myProject, this);

    mySplitter = new Splitter();
    mySplitter.setFirstComponent(myLeftPanel);
    mySplitter.setSecondComponent(myRightPanel);
    mySplitter.setProportion(0.2f);

    if (addBorderToScrollPane) {
      myFiltersListScrollPane.setViewportBorder(IdeBorderFactory.createBorder());
    }
    else {
      myFiltersList.setBorder(BorderFactory.createEmptyBorder());
    }

    if (preselectedDevice == null) {
      myDeviceCombo.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateLogConsole();
        }
      });
      myDeviceCombo.setRenderer(new ListCellRendererWrapper() {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value == null) {
            setText("<html><font color='red'>[none]</font></html>");
          }
          else if (value instanceof IDevice) {
            setText(((IDevice)value).getSerialNumber());
          }
        }
      });
    }
    else {
      myTopPanel.setVisible(false);
    }
    final AndroidLogFilterModel logFilterModel =
      new AndroidLogFilterModel() {
        private ConfiguredFilter myConfiguredFilter;
        
        @Override
        protected void setCustomFilter(String filter) {
          AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER = filter;
        }

        @Override
        protected void saveLogLevel(String logLevelName) {
          AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL = logLevelName;
        }

        @Override
        public String getSelectedLogLevelName() {
          return AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL;
        }

        @Override
        public String getCustomFilter() {
          return AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER;
        }

        @Override
        protected void setConfiguredFilter(ConfiguredFilter filter) {
          AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CONFIGURED_FILTER = filter != null ? filter.getName() : "";
          myConfiguredFilter = filter;
        }

        @Nullable
        @Override
        protected ConfiguredFilter getConfiguredFilter() {
          if (myConfiguredFilter == null) {
            final String name = AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CONFIGURED_FILTER;
            myConfiguredFilter = compileConfiguredFilter(name);
          }
          return myConfiguredFilter;
        }
      };
    myLogConsole = new MyLogConsole(project, logFilterModel);
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
    if (preselectedDevice == null) {
      mySearchComponentWrapper.add(createSearchComponent(project));
    }
    JComponent consoleComponent = myLogConsole.getComponent();

    final DefaultActionGroup group1 = new DefaultActionGroup();
    group1.addAll(myLogConsole.getToolbarActions());
    group1.add(new MyRestartAction());
    final JComponent tbComp1 =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group1, false).getComponent();
    myConsoleWrapper.add(tbComp1, BorderLayout.EAST);

    final DefaultActionGroup group2 = new DefaultActionGroup();
    group2.add(new MyAddFilterAction());
    group2.add(new MyRemoveFilterAction());
    group2.add(new MyEditFilterAction());
    final JComponent tbComp2 =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group2, true).getComponent();
    myFiltersToolbarPanel.add(tbComp2, BorderLayout.CENTER);

    myFiltersList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        
        final String filterName = (String)myFiltersList.getSelectedValue();
        if (filterName == null) {
          return;
        }
        final ConfiguredFilter filter = compileConfiguredFilter(filterName);

        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, LogConsoleBase.APPLYING_FILTER_TITLE) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            logFilterModel.updateConfiguredFilter(filter);
          }
        });
      }
    });

    myConsoleWrapper.add(consoleComponent, BorderLayout.CENTER);
    Disposer.register(this, myLogConsole);

    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener);

    updateDevices();
    updateLogConsole();
    updateConfiguredFilters(AndroidLogcatFiltersPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER);
    if (myFiltersList.getSelectedValue() == null && myFiltersList.getItemsCount() > 0) {
      myFiltersList.setSelectedIndex(0);
    }
  }

  @NotNull
  public JPanel createSearchComponent(final Project project) {
    final JPanel searchComponent = new JPanel(new BorderLayout());
    final JButton clearLogButton = new JButton(AndroidBundle.message("android.logcat.clear.log.button.title"));
    clearLogButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IDevice device = getSelectedDevice();
        if (device != null) {
          AndroidLogcatUtil.clearLogcat(project, device);
          myLogConsole.clear();
        }
      }
    });
    searchComponent.add(myLogConsole.getSearchComponent(), BorderLayout.CENTER);
    searchComponent.add(clearLogButton, BorderLayout.EAST);
    return searchComponent;
  }

  protected abstract boolean isActive();
  
  @Nullable
  private ConfiguredFilter compileConfiguredFilter(@NotNull String name) {
    if (EMPTY_CONFIGURED_FILTER.equals(name)) {
      return null;
    }

    final AndroidConfiguredLogFilters.MyFilterEntry entry = 
      AndroidConfiguredLogFilters.getInstance(myProject).findFilterEntryByName(name);
    if (entry == null) {
      return null;
    }

    Pattern logMessagePattern = null;
    final String logMessagePatternStr = entry.getLogMessagePattern();
    if (logMessagePatternStr != null && logMessagePatternStr.length() > 0) {
      try {
        logMessagePattern = Pattern.compile(logMessagePatternStr, AndroidConfiguredLogFilters.getPatternCompileFlags(logMessagePatternStr));
      }
      catch (PatternSyntaxException e) {
        LOG.info(e);
      }
    }

    Pattern logTagPattern = null;
    final String logTagPatternStr = entry.getLogTagPattern();
    if (logTagPatternStr != null && logTagPatternStr.length() > 0) {
      try {
        logTagPattern = Pattern.compile(logTagPatternStr, AndroidConfiguredLogFilters.getPatternCompileFlags(logTagPatternStr));
      }
      catch (PatternSyntaxException e) {
        LOG.info(e);
      }
    }
    
    final String pid = entry.getPid();

    Log.LogLevel logLevel = null;
    final String logLevelStr = entry.getLogLevel();
    if (logLevelStr != null && logLevelStr.length() > 0) {
      logLevel = Log.LogLevel.getByString(logLevelStr);
    }

    return new ConfiguredFilter(name, logMessagePattern, logTagPattern, pid, logLevel);
  }

  public void activate() {
    if (isActive()) {
      updateDevices();
      updateLogConsole();
      updateConfiguredFilters(AndroidLogcatFiltersPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER);
      if (myFiltersList.getSelectedValue() == null && myFiltersList.getItemsCount() > 0) {
        myFiltersList.setSelectedIndex(0);
      }
    }
    if (myLogConsole != null) {
      myLogConsole.activate();
    }
  }

  private void updateLogConsole() {
    IDevice device = getSelectedDevice();
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
          myLogConsole.getConsole().clear();
          final Pair<Reader, Writer> pair = AndroidLogcatUtil.startLoggingThread(myProject, device, false, myLogConsole);
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
  public IDevice getSelectedDevice() {
    return myPreselectedDevice != null ? myPreselectedDevice : (IDevice)myDeviceCombo.getSelectedItem();
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

  private void updateConfiguredFilters(String defaultSelection) {
    final String selectedFilterName = defaultSelection != null
                                      ? defaultSelection
                                      : (String)myFiltersList.getSelectedValue();

    final AndroidConfiguredLogFilters filters = AndroidConfiguredLogFilters.getInstance(myProject);
    final List<AndroidConfiguredLogFilters.MyFilterEntry> entries = filters.getFilterEntries();
    final List<String> filterNames = new ArrayList<String>(entries.size() + 1);
    filterNames.add(EMPTY_CONFIGURED_FILTER);

    boolean hasSelectedFilter = false;
    for (AndroidConfiguredLogFilters.MyFilterEntry entry : entries) {
      final String name = entry.getName();

      if (selectedFilterName != null && selectedFilterName.equals(name)) {
        hasSelectedFilter = true;
      }
      filterNames.add(name);
    }

    myFiltersList.setModel(new CollectionListModel<String>(filterNames));

    if (hasSelectedFilter) {
      myFiltersList.setSelectedValue(selectedFilterName, true);
    }
    else if (myFiltersList.getItemsCount() > 0) {
      myFiltersList.setSelectedIndex(0);
    }
  }

  private void updateDevices() {
    AndroidPlatform platform = getAndroidPlatform(myProject);
    if (platform != null) {
      final AndroidDebugBridge debugBridge = platform.getSdkData().getDebugBridge(myProject);
      if (debugBridge != null) {
        IDevice[] devices = debugBridge.getDevices();
        Object temp = myDeviceCombo.getSelectedItem();
        myDeviceCombo.setModel(new DefaultComboBoxModel(devices));
        if (devices.length > 0 && temp == null) {
          temp = devices[0];
        }
        if (temp != null) {
          myDeviceCombo.setSelectedItem(temp);
        }
      }
    }
    else {
      myDeviceCombo.setModel(new DefaultComboBoxModel());
    }
  }

  public JPanel getContentPanel() {
    return mySplitter;
  }

  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener);
  }

  @Nullable
  private AndroidConfiguredLogFilters.MyFilterEntry getSelectedFilterEntry() {
    final String filterName = (String)myFiltersList.getSelectedValue();

    if (filterName == null || EMPTY_CONFIGURED_FILTER.equals(filterName)) {
      return null;
    }

    final AndroidConfiguredLogFilters.MyFilterEntry filterEntry =
      AndroidConfiguredLogFilters.getInstance(myProject).findFilterEntryByName(filterName);
    if (filterEntry == null) {
      return null;
    }
    return filterEntry;
  }

  private class MyRestartAction extends AnAction {
    public MyRestartAction() {
      super(AndroidBundle.message("android.restart.logcat.action.text"), AndroidBundle.message("android.restart.logcat.action.description"),
            AllIcons.Actions.Restart);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myDevice = null;
      updateLogConsole();
    }
  }

  class MyLogConsole extends LogConsoleBase {

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    public MyLogConsole(Project project, AndroidLogFilterModel logFilterModel) {
      super(project, new MyLoggingReader(), null, false, logFilterModel);
    }

    @Override
    public boolean isActive() {
      return AndroidLogcatToolWindowView.this.isActive();
    }
  }

  private class MyAddFilterAction extends AnAction {
    private MyAddFilterAction() {
      super(CommonBundle.message("button.add"), AndroidBundle.message("android.logcat.add.logcat.filter.button"), IconUtil.getAddIcon());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final EditLogFilterDialog dialog = new EditLogFilterDialog(AndroidLogcatToolWindowView.this, null);
      dialog.setTitle(AndroidBundle.message("android.logcat.new.filter.dialog.title"));
      dialog.show();

      if (dialog.isOK()) {
        final AndroidConfiguredLogFilters.MyFilterEntry newEntry = dialog.getCustomLogFiltersEntry();
        final AndroidConfiguredLogFilters configuredLogFilters = AndroidConfiguredLogFilters.getInstance(myProject);
        final List<AndroidConfiguredLogFilters.MyFilterEntry> entries =
          new ArrayList<AndroidConfiguredLogFilters.MyFilterEntry>(configuredLogFilters.getFilterEntries());
        entries.add(newEntry);
        configuredLogFilters.setFilterEntries(entries);

        updateConfiguredFilters(null);
        myFiltersList.setSelectedValue(newEntry.getName(), true);
      }
    }
  }

  private class MyRemoveFilterAction extends AnAction {
    private MyRemoveFilterAction() {
      super(CommonBundle.message("button.delete"), AndroidBundle.message("android.logcat.remove.logcat.filter.button"),
            IconUtil.getRemoveIcon());
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(getSelectedFilterEntry() != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final AndroidConfiguredLogFilters.MyFilterEntry filterEntry = getSelectedFilterEntry();
      if (filterEntry == null) {
        return;
      }

      final int selectedIndex = myFiltersList.getSelectedIndex();
      final AndroidConfiguredLogFilters configuredLogFilters = AndroidConfiguredLogFilters.getInstance(myProject);
      final List<AndroidConfiguredLogFilters.MyFilterEntry> entries =
        new ArrayList<AndroidConfiguredLogFilters.MyFilterEntry>(configuredLogFilters.getFilterEntries());
      entries.remove(filterEntry);
      configuredLogFilters.setFilterEntries(entries);

      updateConfiguredFilters(null);
      final int index = selectedIndex < myFiltersList.getItemsCount()
                        ? selectedIndex
                        : myFiltersList.getItemsCount() - 1;
      myFiltersList.setSelectedIndex(index);
      myFiltersList.ensureIndexIsVisible(index);
    }
  }

  private class MyEditFilterAction extends AnAction {
    private MyEditFilterAction() {
      super(CommonBundle.message("button.edit"), AndroidBundle.message("android.logcat.edit.logcat.filter.button"), IconUtil.getEditIcon());
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(getSelectedFilterEntry() != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final AndroidConfiguredLogFilters.MyFilterEntry filterEntry = getSelectedFilterEntry();
      if (filterEntry == null) {
        return;
      }

      final EditLogFilterDialog dialog = new EditLogFilterDialog(AndroidLogcatToolWindowView.this, filterEntry);
      dialog.setTitle(AndroidBundle.message("android.logcat.edit.filter.dialog.title"));
      dialog.show();

      if (dialog.isOK()) {
        final String newName = filterEntry.getName();
        updateConfiguredFilters(null);
        myFiltersList.setSelectedValue(newName, true);
      }
    }
  }
}
