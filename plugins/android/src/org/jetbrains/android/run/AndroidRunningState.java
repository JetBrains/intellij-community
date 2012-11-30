/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.android.ddmlib.*;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.CommonBundle;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.logcat.AndroidLogcatToolWindowFactory;
import org.jetbrains.android.logcat.AndroidLogcatView;
import org.jetbrains.android.logcat.AndroidLogcatUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author coyote
 */
public class AndroidRunningState implements RunProfileState, AndroidDebugBridge.IClientChangeListener, AndroidExecutionState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunningState");

  @NonNls private static final String ANDROID_TARGET_DEVICES_PROPERTY = "AndroidTargetDevices";
  private static final IDevice[] EMPTY_DEVICE_ARRAY = new IDevice[0];

  public static final int WAITING_TIME = 20;

  private static final Pattern FAILURE = Pattern.compile("Failure\\s+\\[(.*)\\]");
  private static final Pattern TYPED_ERROR = Pattern.compile("Error\\s+[Tt]ype\\s+(\\d+).*");
  private static final String ERROR_PREFIX = "Error";

  static final int NO_ERROR = -2;
  private static final int UNTYPED_ERROR = -1;

  private final String myPackageName;
  private String myTargetPackageName;
  private final AndroidFacet myFacet;
  private final String myCommandLine;
  private final AndroidApplicationLauncher myApplicationLauncher;
  private final Map<AndroidFacet, String> myAdditionalFacet2PackageName;
  private final AndroidRunConfigurationBase myConfiguration;

  private final Object myDebugLock = new Object();

  @NotNull
  private volatile IDevice[] myTargetDevices = EMPTY_DEVICE_ARRAY;

  private volatile String myAvdName;
  private volatile boolean myDebugMode;

  private volatile DebugLauncher myDebugLauncher;

  private final ExecutionEnvironment myEnv;

  private boolean myStopped;
  private volatile ProcessHandler myProcessHandler;
  private final Object myLock = new Object();

  private volatile boolean myDeploy = true;

  private volatile boolean myApplicationDeployed = false;

  private ConsoleView myConsole;
  private Runnable myRestarter;
  private TargetChooser myTargetChooser;
  private final boolean mySupportMultipleDevices;
  private final boolean myClearLogcatBeforeStart;
  private final List<AndroidRunningStateListener> myListeners = new ArrayList<AndroidRunningStateListener>();

  public void setDebugMode(boolean debugMode) {
    myDebugMode = debugMode;
  }

  public void setDebugLauncher(DebugLauncher debugLauncher) {
    myDebugLauncher = debugLauncher;
  }

  public boolean isDebugMode() {
    return myDebugMode;
  }

  public void setRestarter(Runnable restarter) {
    myRestarter = restarter;
  }

  private static void runInDispatchedThread(Runnable r, boolean blocking) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      r.run();
    }
    else if (blocking) {
      application.invokeAndWait(r, ModalityState.defaultModalityState());
    }
    else {
      application.invokeLater(r);
    }
  }

  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    myProcessHandler = new DefaultDebugProcessHandler();
    AndroidProcessText.attach(myProcessHandler);
    ConsoleView console;
    if (isDebugMode()) {
      Project project = myFacet.getModule().getProject();
      final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
      console = builder.getConsole();
      if (console != null) {
        console.attachToProcess(myProcessHandler);
      }
    }
    else {
      console = myConfiguration.attachConsole(this, executor);
    }
    myConsole = console;

    if (myTargetChooser instanceof ManualTargetChooser) {

      final ExtendedDeviceChooserDialog chooser = new ExtendedDeviceChooserDialog(myFacet, mySupportMultipleDevices);
      chooser.show();
      if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
        return null;
      }
      
      if (chooser.isToLaunchEmulator()) {
        final String selectedAvd = chooser.getSelectedAvd();
        if (selectedAvd == null) {
          return null;
        }
        myTargetChooser = new EmulatorTargetChooser(selectedAvd);
        myAvdName = selectedAvd;
      }
      else {
        final IDevice[] selectedDevices = chooser.getSelectedDevices();
        if (selectedDevices.length == 0) {
          return null;
        }
        myTargetDevices = selectedDevices;
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        chooseTargetDeviceAndStart();
      }
    });
    return new DefaultExecutionResult(console, myProcessHandler);
  }

  @NotNull
  public AndroidRunConfigurationBase getConfiguration() {
    return myConfiguration;
  }

  @Nullable
  public RunnerSettings getRunnerSettings() {
    return myEnv.getRunnerSettings();
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myEnv.getConfigurationSettings();
  }

  public boolean isStopped() {
    return myStopped;
  }

  public Object getRunningLock() {
    return myLock;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public Module getModule() {
    return myFacet.getModule();
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Override
  public IDevice[] getDevices() {
    return myTargetDevices;
  }

  @Nullable
  @Override
  public ConsoleView getConsoleView() {
    return myConsole;
  }

  public class MyReceiver extends AndroidOutputReceiver {
    private int errorType = NO_ERROR;
    private String failureMessage = null;
    private final StringBuilder output = new StringBuilder();

    @Override
    protected void processNewLine(String line) {
      if (line.length() > 0) {
        Matcher failureMatcher = FAILURE.matcher(line);
        if (failureMatcher.matches()) {
          failureMessage = failureMatcher.group(1);
        }
        Matcher errorMatcher = TYPED_ERROR.matcher(line);
        if (errorMatcher.matches()) {
          errorType = Integer.parseInt(errorMatcher.group(1));
        }
        else if (line.startsWith(ERROR_PREFIX) && errorType == NO_ERROR) {
          errorType = UNTYPED_ERROR;
        }
      }
      output.append(line).append('\n');
    }

    public int getErrorType() {
      return errorType;
    }

    public boolean isCancelled() {
      return myStopped;
    }

    public StringBuilder getOutput() {
      return output;
    }
  }

  public AndroidRunningState(@NotNull ExecutionEnvironment environment,
                             @NotNull AndroidFacet facet,
                             @Nullable TargetChooser targetChooser,
                             @NotNull String commandLine,
                             @NotNull String packageName,
                             AndroidApplicationLauncher applicationLauncher,
                             Map<AndroidFacet, String> additionalFacet2PackageName,
                             boolean supportMultipleDevices,
                             boolean clearLogcatBeforeStart,
                             @NotNull AndroidRunConfigurationBase configuration) throws ExecutionException {
    myFacet = facet;
    myCommandLine = commandLine;
    myConfiguration = configuration;

    myTargetChooser = targetChooser;
    mySupportMultipleDevices = supportMultipleDevices;

    myAvdName = targetChooser instanceof EmulatorTargetChooser
                ? ((EmulatorTargetChooser)targetChooser).getAvd()
                : null;
      
    myEnv = environment;
    myApplicationLauncher = applicationLauncher;
    myPackageName = packageName;
    myTargetPackageName = packageName;
    myAdditionalFacet2PackageName = additionalFacet2PackageName;
    myClearLogcatBeforeStart = clearLogcatBeforeStart;
  }

  public void setDeploy(boolean deploy) {
    myDeploy = deploy;
  }

  public void setTargetPackageName(String targetPackageName) {
    synchronized (myDebugLock) {
      myTargetPackageName = targetPackageName;
    }
  }

  @Nullable
  private IDevice[] chooseDevicesAutomaticaly() {
    final List<IDevice> compatibleDevices = getAllCompatibleDevices();

    if (compatibleDevices.size() == 0) {
      return EMPTY_DEVICE_ARRAY;
    }
    else if (compatibleDevices.size() == 1) {
      return new IDevice[] {compatibleDevices.get(0)};
    }
    else {
      final IDevice[][] devicesWrapper = {null};
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          devicesWrapper[0] = chooseDevicesManually(new Condition<IDevice>() {
            @Override
            public boolean value(IDevice device) {
              return isCompatibleDevice(device) != Boolean.FALSE;
            }
          });
        }
      }, ModalityState.defaultModalityState());
      return devicesWrapper[0].length > 0 ? devicesWrapper[0] : null;
    }
  }

  @NotNull
  List<IDevice> getAllCompatibleDevices() {
    final List<IDevice> compatibleDevices = new ArrayList<IDevice>();
    final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();

    if (bridge != null) {
      IDevice[] devices = bridge.getDevices();

      for (IDevice device : devices) {
        if (isCompatibleDevice(device) != Boolean.FALSE) {
          compatibleDevices.add(device);
        }
      }
    }
    return compatibleDevices;
  }

  private void chooseAvd() {
    IAndroidTarget buildTarget = myFacet.getConfiguration().getAndroidTarget();
    assert buildTarget != null;
    AvdInfo[] avds = myFacet.getValidCompatibleAvds();
    if (avds.length > 0) {
      myAvdName = avds[0].getName();
    }
    else {
      final Project project = myFacet.getModule().getProject();
      AvdManager manager = null;
      try {
        manager = myFacet.getAvdManager(new AvdManagerLog() {
          @Override
          public void error(Throwable t, String errorFormat, Object... args) {
            super.error(t, errorFormat, args);

            if (errorFormat != null) {
              final String msg = String.format(errorFormat, args);
              message(msg, STDERR);
            }
          }
        });
      }
      catch (AvdsNotSupportedException e) {
        // can't be
        LOG.error(e);
      }
      catch (final AndroidLocation.AndroidLocationException e) {
        LOG.info(e);
        runInDispatchedThread(new Runnable() {
          public void run() {
            Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
          }
        }, false);
        return;
      }
      final AvdManager finalManager = manager;
      runInDispatchedThread(new Runnable() {
        public void run() {
          CreateAvdDialog dialog = new CreateAvdDialog(project, myFacet, finalManager, true, true);
          dialog.show();
          if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            AvdInfo createdAvd = dialog.getCreatedAvd();
            if (createdAvd != null) {
              myAvdName = createdAvd.getName();
            }
          }
        }
      }, true);
    }
  }

  private void chooseTargetDeviceAndStart() {
    message("Waiting for device.", STDOUT);
    if (myTargetDevices.length == 0) {
      if (!chooseOrLaunchDevice()) {
        getProcessHandler().destroyProcess();
        fireExecutionFailed();
        return;
      }
    }
    start();
  }

  void start() {
    if (myDebugMode) {
      AndroidDebugBridge.addClientChangeListener(this);
    }
    final MyDeviceChangeListener[] deviceListener = {null};
    getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (myDebugMode) {
          AndroidDebugBridge.removeClientChangeListener(AndroidRunningState.this);
        }
        if (deviceListener[0] != null) {
          Disposer.dispose(deviceListener[0]);
          AndroidDebugBridge.removeDeviceChangeListener(deviceListener[0]);
        }
        myStopped = true;
        synchronized (myLock) {
          myLock.notifyAll();
        }
      }
    });
    deviceListener[0] = prepareAndStartAppWhenDeviceIsOnline();
  }

  private boolean chooseOrLaunchDevice() {
    IDevice[] targetDevices = chooseDevicesAutomaticaly();
    if (targetDevices == null) {
      message("Canceled", STDERR);
      return false;
    }

    if (targetDevices.length > 0) {
      myTargetDevices = targetDevices;
    }
    else if (myTargetChooser instanceof EmulatorTargetChooser) {
      if (myAvdName == null) {
        chooseAvd();
      }
      if (myAvdName != null) {
        myFacet.launchEmulator(myAvdName, myCommandLine, getProcessHandler());
      }
      else if (getProcessHandler().isStartNotified()) {
        message("Canceled", STDERR);
        return false;
      }
    }
    else {
      message("USB device not found", STDERR);
      return false;
    }
    return true;
  }

  @NotNull
  private IDevice[] chooseDevicesManually(@Nullable Condition<IDevice> filter) {
    final Project project = myFacet.getModule().getProject();
    String value = PropertiesComponent.getInstance(project).getValue(ANDROID_TARGET_DEVICES_PROPERTY);
    String[] selectedSerials = value != null ? fromString(value) : null;
    DeviceChooserDialog chooser = new DeviceChooserDialog(myFacet, mySupportMultipleDevices, selectedSerials, filter);
    chooser.show();
    IDevice[] devices = chooser.getSelectedDevices();
    if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE || devices.length == 0) {
      return DeviceChooser.EMPTY_DEVICE_ARRAY;
    }
    PropertiesComponent.getInstance(project).setValue(ANDROID_TARGET_DEVICES_PROPERTY, toString(devices));
    return devices;
  }

  public static String toString(IDevice[] devices) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = devices.length; i < n; i++) {
      builder.append(devices[i].getSerialNumber());
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  private static String[] fromString(String s) {
    return s.split(" ");
  }

  private void message(@NotNull String message, @NotNull Key outputKey) {
    getProcessHandler().notifyTextAvailable(message + '\n', outputKey);
  }

  public void clientChanged(Client client, int changeMask) {
    synchronized (myDebugLock) {
      if (myDebugLauncher == null) {
        return;
      }
      if (myDeploy && !myApplicationDeployed) {
        return;
      }
      IDevice device = client.getDevice();
      if (isMyDevice(device) && device.isOnline()) {
        if (myTargetDevices.length == 0) {
          myTargetDevices = new IDevice[]{device};
        }
        ClientData data = client.getClientData();
        if (myDebugLauncher != null && isToLaunchDebug(data)) {
          launchDebug(client);
        }
      }
    }
  }

  private boolean isToLaunchDebug(@NotNull ClientData data) {
    if (data.getDebuggerConnectionStatus() == ClientData.DebuggerStatus.WAITING) {
      return true;
    }
    String description = data.getClientDescription();
    if (description == null) {
      return false;
    }
    return description.equals(myTargetPackageName) && myApplicationLauncher.isReadyForDebugging(data, getProcessHandler());
  }

  private void launchDebug(Client client) {
    String port = Integer.toString(client.getDebuggerListenPort());
    myDebugLauncher.launchDebug(client.getDevice(), port);
    myDebugLauncher = null;
  }

  @Nullable
  Boolean isCompatibleDevice(@NotNull IDevice device) {
    if (myTargetChooser instanceof EmulatorTargetChooser) {
      if (device.isEmulator()) {
        String avdName = device.isEmulator() ? device.getAvdName() : null;
        if (myAvdName != null) {
          return myAvdName.equals(avdName);
        }
        return myFacet.isCompatibleDevice(device);
      }
    }
    else if (myTargetChooser instanceof UsbDeviceTargetChooser) {
      return !device.isEmulator();
    }
    return false;
  }

  private boolean isMyDevice(@NotNull IDevice device) {
    if (myTargetDevices.length > 0) {
      return ArrayUtil.find(myTargetDevices, device) >= 0;
    }
    Boolean compatible = isCompatibleDevice(device);
    return compatible == null || compatible.booleanValue();
  }

  public void setTargetDevices(@NotNull IDevice[] targetDevices) {
    myTargetDevices = targetDevices;
  }

  public void setConsole(@NotNull ConsoleView console) {
    myConsole = console;
  }

  @Nullable
  private MyDeviceChangeListener prepareAndStartAppWhenDeviceIsOnline() {
    if (myTargetDevices.length > 0) {
      for (IDevice targetDevice : myTargetDevices) {
        if (targetDevice.isOnline()) {
          if (!prepareAndStartApp(targetDevice) && !myStopped) {
            // todo: check: it may be we don't need to assign it directly
            myStopped = true;
            getProcessHandler().destroyProcess();
            break;
          }
        }
      }
      if (!myDebugMode && !myStopped) {
        getProcessHandler().destroyProcess();
      }
      return null;
    }
    final MyDeviceChangeListener deviceListener = new MyDeviceChangeListener();
    AndroidDebugBridge.addDeviceChangeListener(deviceListener);
    return deviceListener;
  }

  public synchronized void setProcessHandler(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  public synchronized ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  private boolean prepareAndStartApp(IDevice device) {
    if (!doPrepareAndStart(device)) {
      fireExecutionFailed();
      return false;
    }
    return true;
  }

  private void fireExecutionFailed() {
    for (AndroidRunningStateListener listener : myListeners) {
      listener.executionFailed();
    }
  }

  private boolean doPrepareAndStart(IDevice device) {
    if (myClearLogcatBeforeStart) {
      clearLogcatAndConsole(getModule().getProject(), device);
    }

    message("Target device: " + getDevicePresentableName(device), STDOUT);
    try {
      if (myDeploy) {
        if (!checkPackageNames()) return false;
        if (!uploadAndInstall(device, myPackageName, myFacet)) return false;
        if (!uploadAndInstallDependentModules(device)) return false;
        myApplicationDeployed = true;
      }
      if (!myApplicationLauncher.launch(this, device)) return false;

      checkDdms();

      synchronized (myDebugLock) {
        Client client = device.getClient(myTargetPackageName);
        if (myDebugLauncher != null) {
          if (client != null &&
              myApplicationLauncher.isReadyForDebugging(client.getClientData(), getProcessHandler())) {
            launchDebug(client);
          }
          else {
            message("Waiting for process: " + myTargetPackageName, STDOUT);
          }
        }
      }
      return true;
    }
    catch (TimeoutException e) {
      LOG.info(e);
      message("Error: Connection to ADB failed with a timeout", STDERR);
      return false;
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      message("Error: Adb refused a command", STDERR);
      return false;
    }
    catch (IOException e) {
      LOG.info(e);
      String message = e.getMessage();
      message("I/O Error" + (message != null ? ": " + message : ""), STDERR);
      return false;
    }
  }

  private boolean checkPackageNames() {
    final Map<String, List<String>> packageName2ModuleNames = new HashMap<String, List<String>>();
    packageName2ModuleNames.put(myPackageName, new ArrayList<String>(Arrays.asList(myFacet.getModule().getName())));

    for (Map.Entry<AndroidFacet, String> entry : myAdditionalFacet2PackageName.entrySet()) {
      final String moduleName = entry.getKey().getModule().getName();
      final String packageName = entry.getValue();
      List<String> list = packageName2ModuleNames.get(packageName);

      if (list == null) {
        list = new ArrayList<String>();
        packageName2ModuleNames.put(packageName, list);
      }
      list.add(moduleName);
    }
    boolean result = true;

    for (Map.Entry<String, List<String>> entry : packageName2ModuleNames.entrySet()) {
      final String packageName = entry.getKey();
      final List<String> moduleNames = entry.getValue();

      if (moduleNames.size() > 1) {
        final StringBuilder messageBuilder = new StringBuilder("Applications have the same package name ");
        messageBuilder.append(packageName).append(":\n    ");

        for (Iterator<String> it = moduleNames.iterator(); it.hasNext(); ) {
          String moduleName = it.next();
          messageBuilder.append(moduleName);
          if (it.hasNext()) {
            messageBuilder.append(", ");
          }
        }
        message(messageBuilder.toString(), STDERR);
        result = false;
      }
    }
    return result;
  }

  protected static void clearLogcatAndConsole(@NotNull final Project project, @NotNull final IDevice device) {
    final boolean[] result = {true};

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AndroidLogcatToolWindowFactory.TOOL_WINDOW_ID);
        if (toolWindow == null) {
          result[0] = false;
          return;
        }

        for (Content content : toolWindow.getContentManager().getContents()) {
          final AndroidLogcatView view = content.getUserData(AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY);

          if (view != null && device == view.getSelectedDevice()) {
            view.getLogConsole().clear();
          }
        }
      }
    }, ModalityState.defaultModalityState());

    if (result[0]) {
      AndroidLogcatUtil.clearLogcat(project, device);
    }
  }

  @NotNull
  private static String getDevicePresentableName(IDevice device) {
    StringBuilder deviceMessageBuilder = new StringBuilder();
    deviceMessageBuilder.append(device.getSerialNumber());
    if (device.getAvdName() != null) {
      deviceMessageBuilder.append(" (").append(device.getAvdName()).append(')');
    }
    return deviceMessageBuilder.toString();
  }

  private boolean checkDdms() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (myDebugMode && bridge != null && AndroidSdkUtils.canDdmsBeCorrupted(bridge)) {
      message(AndroidBundle.message("ddms.corrupted.error"), STDERR);
      if (myConsole != null && myRestarter != null) {
        final Runnable r = myRestarter;
        myConsole.printHyperlink(AndroidBundle.message("restart.adb.fix.text"), new HyperlinkInfo() {
          @Override
          public void navigate(Project project) {
            AndroidSdkUtils.restartDdmlib(project);

            final ProcessHandler processHandler = getProcessHandler();
            if (!processHandler.isProcessTerminated()) {
              processHandler.destroyProcess();
            }
            r.run();
          }
        });
        myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      }
      return false;
    }
    return true;
  }

  private boolean uploadAndInstallDependentModules(@NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    for (AndroidFacet depFacet : myAdditionalFacet2PackageName.keySet()) {
      String packageName = myAdditionalFacet2PackageName.get(depFacet);
      if (!uploadAndInstall(device, packageName, depFacet)) {
        return false;
      }
    }
    return true;
  }

  private boolean uploadAndInstall(@NotNull IDevice device, @NotNull String packageName, AndroidFacet facet)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    String remotePath = "/data/local/tmp/" + packageName;
    String localPath = AndroidRootUtil.getApkPath(facet);
    if (localPath == null) {
      message("ERROR: APK path is not specified for module \"" + facet.getModule().getName() + '"', STDERR);
      return false;
    }
    if (!uploadApp(device, remotePath, localPath)) return false;
    if (!installApp(device, remotePath, packageName)) return false;
    return true;
  }

  private class MyISyncProgressMonitor implements SyncService.ISyncProgressMonitor {
    @Override
    public void start(int totalWork) {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isCanceled() {
      return myStopped;
    }

    @Override
    public void startSubTask(String name) {
    }

    @Override
    public void advance(int work) {
    }
  }

  private boolean uploadApp(IDevice device, String remotePath, String localPath) throws IOException {
    if (myStopped) return false;
    message("Uploading file\n\tlocal path: " + localPath + "\n\tremote path: " + remotePath, STDOUT);
    String exceptionMessage;
    String errorMessage;
    try {
      SyncService service = device.getSyncService();
      if (service == null) {
        message("Can't upload file: device is not available.", STDERR);
        return false;
      }
      service.pushFile(localPath, remotePath, new MyISyncProgressMonitor());
      return true;
    }
    catch (TimeoutException e) {
      LOG.info(e);
      exceptionMessage = e.getMessage();
      errorMessage = "Connection timeout";
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      exceptionMessage = e.getMessage();
      errorMessage = "ADB refused the command";
    }
    catch (SyncException e) {
      LOG.info(e);
      final SyncException.SyncError errorCode = e.getErrorCode();
      errorMessage = errorCode.getMessage();
      exceptionMessage = e.getMessage();
    }
    if (errorMessage.equals(exceptionMessage) || exceptionMessage == null) {
      message(errorMessage, STDERR);
    }
    else {
      message(errorMessage + '\n' + exceptionMessage, STDERR);
    }
    return false;
  }

  @SuppressWarnings({"DuplicateThrows"})
  public void executeDeviceCommandAndWriteToConsole(IDevice device, String command, AndroidOutputReceiver receiver) throws IOException,
                                                                                                                           TimeoutException,
                                                                                                                           AdbCommandRejectedException,
                                                                                                                           ShellCommandUnresponsiveException {
    message("DEVICE SHELL COMMAND: " + command, STDOUT);
    AndroidUtils.executeCommandOnDevice(device, command, receiver, false);
  }

  private static boolean isSuccess(MyReceiver receiver) {
    return receiver.errorType == NO_ERROR && receiver.failureMessage == null;
  }

  private boolean installApp(IDevice device, String remotePath, @NotNull String packageName)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    message("Installing " + packageName, STDOUT);
    MyReceiver receiver = new MyReceiver();
    while (true) {
      if (myStopped) return false;
      boolean deviceNotResponding = false;
      try {
        executeDeviceCommandAndWriteToConsole(device, "pm install -r \"" + remotePath + "\"", receiver);
      }
      catch (ShellCommandUnresponsiveException e) {
        LOG.info(e);
        deviceNotResponding = true;
      }
      if (!deviceNotResponding && receiver.errorType != 1 && receiver.errorType != UNTYPED_ERROR) {
        break;
      }
      message("Device is not ready. Waiting for " + WAITING_TIME + " sec.", STDOUT);
      synchronized (myLock) {
        try {
          myLock.wait(WAITING_TIME * 1000);
        }
        catch (InterruptedException e) {
          LOG.info(e);
        }
      }
      receiver = new MyReceiver();
    }
    boolean success = isSuccess(receiver);
    message(receiver.output.toString(), success ? STDOUT : STDERR);
    return success;
  }

  public void addListener(@NotNull AndroidRunningStateListener listener) {
    myListeners.add(listener);
  }

  private class MyDeviceChangeListener implements AndroidDebugBridge.IDeviceChangeListener, Disposable {
    private final MergingUpdateQueue myQueue =
      new MergingUpdateQueue("ANDROID_DEVICE_STATE_UPDATE_QUEUE", 1000, true, null, this, null, false);
    private volatile boolean installed;

    public MyDeviceChangeListener() {
      installed = false;
    }

    public void deviceConnected(final IDevice device) {
      // avd may be null if usb device is used, or if it didn't set by ddmlib yet
      if (device.getAvdName() == null || isMyDevice(device)) {
        message("Device connected: " + device.getSerialNumber(), STDOUT);

        // we need this, because deviceChanged is not triggered if avd is set to the emulator
        myQueue.queue(new MyDeviceStateUpdate(device));
      }
    }

    public void deviceDisconnected(IDevice device) {
      if (isMyDevice(device)) {
        message("Device disconnected: " + device.getSerialNumber(), STDOUT);
      }
    }

    public void deviceChanged(final IDevice device, int changeMask) {
      myQueue.queue(new Update(device.getSerialNumber()) {
        @Override
        public void run() {
          onDeviceChanged(device);
        }
      });
    }

    private synchronized void onDeviceChanged(IDevice device) {
      if (!installed && isMyDevice(device) && device.isOnline()) {
        if (myTargetDevices.length == 0) {
          myTargetDevices = new IDevice[]{device};
        }
        message("Device is online: " + device.getSerialNumber(), STDOUT);
        installed = true;
        if ((!prepareAndStartApp(device) || !myDebugMode) && !myStopped) {
          getProcessHandler().destroyProcess();
        }
      }
    }

    public void dispose() {
    }

    private class MyDeviceStateUpdate extends Update {
      private final IDevice myDevice;

      public MyDeviceStateUpdate(IDevice device) {
        super(device.getSerialNumber());
        myDevice = device;
      }

      @Override
      public void run() {
        onDeviceChanged(myDevice);
        myQueue.queue(new MyDeviceStateUpdate(myDevice));
      }
    }
  }
}
