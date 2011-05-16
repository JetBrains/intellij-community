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
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.CommonBundle;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.sdk.AndroidSdkImpl;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author coyote
 */
public abstract class AndroidRunningState implements RunProfileState, AndroidDebugBridge.IClientChangeListener {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunningState");

  public static final int WAITING_TIME = 5;

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

  private final Object myDebugLock = new Object();

  @NotNull
  private volatile IDevice[] myTargetDevices;

  private volatile String myAvdName;
  private volatile boolean myDebugMode;

  private volatile DebugLauncher myDebugLauncher;

  private final ExecutionEnvironment myEnv;

  private boolean myStopped;
  private volatile ProcessHandler myProcessHandler;
  private final Object myLock = new Object();

  private volatile boolean myDeploy = true;

  private volatile boolean myApplicationDeployed = false;

  public void setDebugMode(boolean debugMode) {
    myDebugMode = debugMode;
  }

  public void setDebugLauncher(DebugLauncher debugLauncher) {
    myDebugLauncher = debugLauncher;
  }

  public boolean isDebugMode() {
    return myDebugMode;
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
      console = attachConsole();
    }
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        start();
      }
    });
    return new DefaultExecutionResult(console, myProcessHandler);
  }

  @NotNull
  protected abstract ConsoleView attachConsole() throws ExecutionException;

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

  public AndroidFacet getAndroidFacet() {
    return myFacet;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public Module getModule() {
    return myFacet.getModule();
  }

  public AndroidFacet getFacet() {
    return myFacet;
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
                             @NotNull IDevice[] targetDevices,
                             @Nullable String avdName,
                             @NotNull String commandLine,
                             @NotNull String packageName,
                             AndroidApplicationLauncher applicationLauncher,
                             Map<AndroidFacet, String> additionalFacet2PackageName) throws ExecutionException {
    myFacet = facet;
    myCommandLine = commandLine;
    myTargetDevices = targetDevices;
    myAvdName = avdName;
    myEnv = environment;
    myApplicationLauncher = applicationLauncher;
    myPackageName = packageName;
    myTargetPackageName = packageName;
    myAdditionalFacet2PackageName = additionalFacet2PackageName;
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
  private IDevice chooseDeviceAutomaticaly() {
    final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null) {
      return null;
    }
    IDevice[] devices = bridge.getDevices();
    boolean exactlyCompatible = false;
    IDevice targetDevice = null;
    for (IDevice device : devices) {
      Boolean compatible = isCompatibleDevice(device);
      if (compatible == Boolean.FALSE) {
        continue;
      }
      if (targetDevice == null ||
          (targetDevice.isEmulator() && !device.isEmulator()) ||
          (targetDevice.isEmulator() == device.isEmulator() && !exactlyCompatible)) {
        exactlyCompatible = compatible != null;
        targetDevice = device;
      }
    }
    return targetDevice;
  }

  private void chooseAvd() {
    IAndroidTarget buildTarget = myFacet.getConfiguration().getAndroidTarget();
    assert buildTarget != null;
    AvdManager.AvdInfo[] avds = myFacet.getValidCompatibleAvds();
    if (avds.length > 0) {
      myAvdName = avds[0].getName();
    }
    else {
      final Project project = myFacet.getModule().getProject();
      AvdManager manager = null;
      try {
        manager = myFacet.getAvdManager();
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
            AvdManager.AvdInfo createdAvd = dialog.getCreatedAvd();
            if (createdAvd != null) {
              myAvdName = createdAvd.getName();
            }
          }
        }
      }, true);
    }
  }

  private void start() {
    message("Waiting for device.", STDOUT);
    if (myTargetDevices.length == 0) {
      chooseOrLaunchDevice();
    }
    if (myDebugMode) {
      AndroidDebugBridge.addClientChangeListener(this);
    }
    final AndroidDebugBridge.IDeviceChangeListener[] deviceListener = {null};
    getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (myDebugMode) {
          AndroidDebugBridge.removeClientChangeListener(AndroidRunningState.this);
        }
        if (deviceListener[0] != null) {
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

  private void chooseOrLaunchDevice() {
    IDevice targetDevice = chooseDeviceAutomaticaly();
    if (targetDevice != null) {
      myTargetDevices = new IDevice[] {targetDevice};
    }
    else {
      if (isAndroidSdk15OrHigher()) {
        if (myAvdName == null) {
          chooseAvd();
        }
        if (myAvdName != null) {
          myFacet.launchEmulator(myAvdName, myCommandLine);
        }
        else if (getProcessHandler().isStartNotified()) {
          getProcessHandler().destroyProcess();
        }
      }
      else {
        myFacet.launchEmulator(myAvdName, myCommandLine);
      }
    }
  }

  private void message(@NotNull String message, @NotNull Key outputKey) {
    getProcessHandler().notifyTextAvailable(message + '\n', outputKey);
  }

  private boolean isAndroidSdk15OrHigher() {
    return myFacet.getConfiguration().getAndroidSdk() instanceof AndroidSdkImpl;
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

  private Boolean isCompatibleDevice(@NotNull IDevice device) {
    if (!isAndroidSdk15OrHigher()) {
      return true;
    }
    String avdName = device.isEmulator() ? device.getAvdName() : null;
    if (myAvdName != null) {
      return myAvdName.equals(avdName);
    }
    return myFacet.isCompatibleDevice(device);
  }

  private boolean isMyDevice(@NotNull IDevice device) {
    if (myTargetDevices.length > 0) {
      return ArrayUtil.find(myTargetDevices, device) >= 0;
    }
    Boolean compatible = isCompatibleDevice(device);
    return compatible != null ? compatible.booleanValue() : true;
  }

  @Nullable
  private AndroidDebugBridge.IDeviceChangeListener prepareAndStartAppWhenDeviceIsOnline() {
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
    final AndroidDebugBridge.IDeviceChangeListener deviceListener = new AndroidDebugBridge.IDeviceChangeListener() {
      boolean installed = false;

      public void deviceConnected(IDevice device) {
        if (device.getAvdName() == null || isMyDevice(device)) {
          message("Device connected: " + device.getSerialNumber(), STDOUT);
        }
      }

      public void deviceDisconnected(IDevice device) {
        if (isMyDevice(device)) {
          message("Device disconnected: " + device.getSerialNumber(), STDOUT);
        }
      }

      public void deviceChanged(final IDevice device, int changeMask) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
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
        });
      }
    };
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
    message("Target device: " + getDevicePresentableName(device), STDOUT);
    try {
      if (myDeploy) {
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
    if (myDebugMode && bridge != null && AndroidUtils.isDdmsCorrupted(bridge)) {
      message(AndroidBundle.message("ddms.corrupted.error"), STDERR);
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
    String localPath = facet.getApkPath();
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
    String exceptionMessage = null;
    String errorMessage = null;
    try {
      SyncService service = device.getSyncService();
      if (service == null) {
        message("Can't upload file: device is not available.", STDERR);
        return false;
      }
      service.pushFile(localPath, remotePath, new MyISyncProgressMonitor());

      SyncService.SyncResult result = service.pushFile(localPath, remotePath, new MyISyncProgressMonitor());
      int code = result.getCode();
      switch (code) {
        case SyncService.RESULT_OK:
          return true;
        case SyncService.RESULT_CANCELED:
          errorMessage = "Command canceled";
          break;
        case SyncService.RESULT_CONNECTION_ERROR:
          errorMessage = "Connection error";
          break;
        case SyncService.RESULT_CONNECTION_TIMEOUT:
          errorMessage = "Connection timeout";
          break;
        case SyncService.RESULT_FILE_READ_ERROR:
          errorMessage = "Cannot read the file";
          break;
        case SyncService.RESULT_FILE_WRITE_ERROR:
          errorMessage = "Cannot write the file";
          break;
        case SyncService.RESULT_LOCAL_IS_DIRECTORY:
          errorMessage = "Local is directory";
          break;
        case SyncService.RESULT_NO_DIR_TARGET:
          errorMessage = "Target directory not found";
          break;
        case SyncService.RESULT_NO_LOCAL_FILE:
          errorMessage = "Local file not found";
          break;
        case SyncService.RESULT_NO_REMOTE_OBJECT:
          errorMessage = "No remote object";
          break;
        case SyncService.RESULT_REMOTE_IS_FILE:
          errorMessage = "Remote is a file";
          break;
        case SyncService.RESULT_REMOTE_PATH_ENCODING:
          errorMessage = "Incorrect remote path encoding";
          break;
        case SyncService.RESULT_REMOTE_PATH_LENGTH:
          errorMessage = "Incorrect remote path length";
          break;
        case SyncService.RESULT_TARGET_IS_FILE:
          errorMessage = "Target is a file";
          break;
        default:
          errorMessage = "Can't upload file";
      }
    }
    catch (TimeoutException e) {
      exceptionMessage = e.getMessage();
      errorMessage = "Connection timeout";
    }
    catch (AdbCommandRejectedException e) {
      exceptionMessage = e.getMessage();
      errorMessage = "ADB refused the command";
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
    AndroidUtils.executeCommand(device, command, receiver, false);
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
    /*if (receiver.failureMessage != null && receiver.failureMessage.equals("INSTALL_FAILED_ALREADY_EXISTS")) {
      if (myStopped) return false;
      receiver = new MyReceiver();
      getProcessHandler().notifyTextAvailable("Application is already installed. Reinstalling.\n", STDOUT);
      executeDeviceCommandAndWriteToConsole(device, "pm install -r \"" + remotePath + '\"', receiver);
      if (myStopped) return false;
    }*/
    /*if (!isSuccess(receiver)) {
      getProcessHandler().notifyTextAvailable("Can't reinstall application. Installing from scratch.\n", STDOUT);
      executeDeviceCommandAndWriteToConsole(device, "pm uninstall \"" + remotePath + '\"', receiver);
      if (myStopped) return false;
      executeDeviceCommandAndWriteToConsole(device, "pm install \"" + remotePath + '\"', receiver);
      if (myStopped) return false;
    }*/
    boolean success = isSuccess(receiver);
    message(receiver.output.toString(), success ? STDOUT : STDERR);
    return success;
  }
}
