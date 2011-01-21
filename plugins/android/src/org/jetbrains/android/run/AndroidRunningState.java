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
import com.intellij.openapi.util.Computable;
import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.ddms.AdbManager;
import org.jetbrains.android.ddms.AdbNotRespondingException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AvdsNotSupportedException;
import org.jetbrains.android.sdk.AndroidSdkImpl;
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
  private volatile String[] myTargetDeviceSerialNumbers;

  private volatile IDevice myTargetDevice = null;

  private volatile String myAvdName;
  private volatile boolean myDebugMode;

  private volatile DebugLauncher myDebugLauncher;

  private final ExecutionEnvironment myEnv;

  private boolean myStopped;
  private volatile ProcessHandler myProcessHandler;
  private final Object myLock = new Object();

  private boolean myDeploy = true;

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
        try {
          start();
        }
        catch (AdbNotRespondingException e) {
          LOG.info(e);
          myProcessHandler.notifyTextAvailable(e.getMessage() + '\n', STDERR);
          if (!myProcessHandler.isProcessTerminated() && myProcessHandler.isStartNotified()) {
            myProcessHandler.destroyProcess();
          }
        }
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
                             @NotNull String[] targetDeviceSerialNumbers,
                             @Nullable String avdName,
                             @NotNull String commandLine,
                             @NotNull String packageName,
                             AndroidApplicationLauncher applicationLauncher,
                             Map<AndroidFacet, String> additionalFacet2PackageName) throws ExecutionException {
    myFacet = facet;
    myCommandLine = commandLine;
    myTargetDeviceSerialNumbers = targetDeviceSerialNumbers;
    myAvdName = avdName;
    myEnv = environment;
    myApplicationLauncher = applicationLauncher;
    /*final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      throw new ExecutionException("Can't start application");
    }*/
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

  private void chooseDeviceAutomaticaly() throws AdbNotRespondingException {
    final AndroidDebugBridge bridge = myFacet.getDebugBridge();
    if (bridge == null) return;
    IDevice[] devices = AdbManager.compute(new Computable<IDevice[]>() {
      public IDevice[] compute() {
        return bridge.getDevices();
      }
    }, true);
    boolean exactlyCompatible = false;
    IDevice targetDevice = null;
    for (IDevice device : devices) {
      Boolean compatible = isMyCompatibleDevice(device);
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
    if (targetDevice != null) {
      // it may be, device doesn't have proper serial number
      myTargetDevice = targetDevice;

      myTargetDeviceSerialNumbers = new String[]{targetDevice.getSerialNumber()};
    }
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

  private void start() throws AdbNotRespondingException {
    getProcessHandler().notifyTextAvailable("Waiting for device.\n", STDOUT);
    if (myTargetDeviceSerialNumbers.length == 0) {
      chooseDeviceAutomaticaly();
      if (myTargetDeviceSerialNumbers.length == 0) {
        if (isAndroidSdk15OrHigher()) {
          if (myAvdName == null) {
            chooseAvd();
          }
          if (myAvdName != null) {
            myFacet.launchEmulator(myAvdName, myCommandLine, myProcessHandler);
          }
          else if (getProcessHandler().isStartNotified()) {
            getProcessHandler().destroyProcess();
          }
        }
        else {
          myFacet.launchEmulator(myAvdName, myCommandLine, myProcessHandler);
        }
      }
    }
    if (myDebugMode) {
      AdbManager.run(new Runnable() {
        public void run() {
          AndroidDebugBridge.addClientChangeListener(AndroidRunningState.this);
        }
      }, false);
    }
    final AndroidDebugBridge.IDeviceChangeListener[] deviceListener = {null};
    getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        try {
          AdbManager.run(new Runnable() {
            public void run() {
              if (myDebugMode) {
                AndroidDebugBridge.removeClientChangeListener(AndroidRunningState.this);
              }
              if (deviceListener[0] != null) {
                AndroidDebugBridge.removeDeviceChangeListener(deviceListener[0]);
              }
            }
          }, false);
        }
        catch (AdbNotRespondingException e) {
          LOG.info(e);
        }
        myStopped = true;
        synchronized (myLock) {
          myLock.notifyAll();
        }
      }
    });
    deviceListener[0] = prepareAndStartAppWhenDeviceIsOnline();
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
        if (myTargetDeviceSerialNumbers.length == 0) {
          myTargetDeviceSerialNumbers = new String[]{device.getSerialNumber()};
        }
        ClientData data = client.getClientData();
        String description = data.getClientDescription();
        if (description != null && description.equals(myTargetPackageName)) {
          launchDebug(client);
        }
      }
    }
  }

  private void launchDebug(Client client) {
    if (myDebugLauncher != null && myApplicationLauncher.isReadyForDebugging(client.getClientData(), getProcessHandler())) {
      String port = Integer.toString(client.getDebuggerListenPort());
      myDebugLauncher.launchDebug(client.getDevice(), port);
      myDebugLauncher = null;
    }
  }

  private Boolean isMyCompatibleDevice(@NotNull IDevice device) {
    if (myTargetDevice != null) {
      return device == myTargetDevice;
    }
    if (myTargetDeviceSerialNumbers.length > 0) {
      return ArrayUtil.find(myTargetDeviceSerialNumbers, device.getSerialNumber()) >= 0;
    }
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
    Boolean compatible = isMyCompatibleDevice(device);
    return compatible != null ? compatible.booleanValue() : true;
  }

  /*@Nullable
  private AvdManager.AvdInfo getAvdByName(String avdName) {
    avdName = StringUtil.capitalize(avdName);
    AvdManager.AvdInfo result = null;
    for (AvdManager.AvdInfo info : myFacet.getAllAvds()) {
      String name = StringUtil.capitalize(info.getName());
      if (avdName.equals(name)) {
        result = info;
      }
    }
    return result;
  }*/

  @Nullable
  private IDevice getDeviceBySerialNumber(@NotNull String serialNumber) throws AdbNotRespondingException {
    final AndroidDebugBridge bridge = myFacet.getDebugBridge();
    if (bridge == null) return null;
    IDevice[] devices = AdbManager.compute(new Computable<IDevice[]>() {
      public IDevice[] compute() {
        return bridge.getDevices();
      }
    }, true);
    for (IDevice device : devices) {
      if (device.getSerialNumber().equals(serialNumber)) {
        return device;
      }
    }
    return null;
  }

  @Nullable
  private AndroidDebugBridge.IDeviceChangeListener prepareAndStartAppWhenDeviceIsOnline() throws AdbNotRespondingException {
    if (myTargetDeviceSerialNumbers.length > 0) {
      if (myTargetDevice != null) {
        if (myTargetDevice.isOnline()) {
          if (!prepareAndStartApp(myTargetDevice) && !myStopped) {
            myStopped = true;
            getProcessHandler().destroyProcess();
          }
        }
      }
      else {
        for (String serialNumber : myTargetDeviceSerialNumbers) {
          IDevice targetDevice = getDeviceBySerialNumber(serialNumber);
          if (targetDevice != null && targetDevice.isOnline()) {
            if (!prepareAndStartApp(targetDevice) && !myStopped) {
              myStopped = true;
              getProcessHandler().destroyProcess();
              break;
            }
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
          getProcessHandler().notifyTextAvailable("Device connected: " + device.getSerialNumber() + '\n', STDOUT);
        }
      }

      public void deviceDisconnected(IDevice device) {
        if (isMyDevice(device)) {
          getProcessHandler().notifyTextAvailable("Device disconnected: " + device.getSerialNumber() + "\n", STDOUT);
        }
      }

      public void deviceChanged(final IDevice device, int changeMask) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            if (!installed && isMyDevice(device) && device.isOnline()) {
              if (myTargetDeviceSerialNumbers.length == 0) {
                myTargetDeviceSerialNumbers = new String[]{device.getSerialNumber()};
              }
              getProcessHandler().notifyTextAvailable("Device is online: " + device.getSerialNumber() + "\n", STDOUT);
              installed = true;
              if ((!prepareAndStartApp(device) || !myDebugMode) && !myStopped) {
                getProcessHandler().destroyProcess();
              }
            }
          }
        });
      }
    };
    AdbManager.run(new Runnable() {
      public void run() {
        AndroidDebugBridge.addDeviceChangeListener(deviceListener);
      }
    }, false);
    return deviceListener;
  }

  public synchronized void setProcessHandler(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  public synchronized ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  private boolean prepareAndStartApp(IDevice device) {
    StringBuilder deviceMessageBuilder = new StringBuilder("Target device: ");
    deviceMessageBuilder.append(device.getSerialNumber());
    if (device.getAvdName() != null) {
      deviceMessageBuilder.append(" (").append(device.getAvdName()).append(')');
    }
    deviceMessageBuilder.append('\n');
    getProcessHandler().notifyTextAvailable(deviceMessageBuilder.toString(), STDOUT);
    try {
      if (myDeploy) {
        if (!uploadAndInstall(device, myPackageName, myFacet)) return false;
        if (!uploadAndInstallDependentModules(device)) return false;
        myApplicationDeployed = true;
      }
      if (!myApplicationLauncher.launch(this, device)) return false;
      synchronized (myDebugLock) {
        Client client = device.getClient(myTargetPackageName);
        if (client != null) {
          launchDebug(client);
        }
      }
      return true;
    }
    catch (TimeoutException e) {
      LOG.info(e);
      getProcessHandler().notifyTextAvailable("Error: Connection to ADB failed with a timeout\n", STDERR);
      return false;
    }
    catch (AdbCommandRejectedException e) {
      LOG.info(e);
      getProcessHandler().notifyTextAvailable("Error: Adb refused a command\n", STDERR);
      return false;
    }
    catch (IOException e) {
      LOG.info(e);
      String message = e.getMessage();
      getProcessHandler().notifyTextAvailable("I/O Error" + (message != null ? ": " + message : "") + '\n', STDERR);
      return false;
    }
  }

  private boolean uploadAndInstallDependentModules(@NotNull IDevice device) throws IOException {
    for (AndroidFacet depFacet : myAdditionalFacet2PackageName.keySet()) {
      String packageName = myAdditionalFacet2PackageName.get(depFacet);
      if (!uploadAndInstall(device, packageName, depFacet)) {
        return false;
      }
    }
    return true;
  }

  private boolean uploadAndInstall(@NotNull IDevice device, @NotNull String packageName, AndroidFacet facet) throws IOException {
    String remotePath = "/data/local/tmp/" + packageName;
    String localPath = facet.getApkPath();
    if (localPath == null) {
      getProcessHandler().notifyTextAvailable("ERROR: APK path is not specified for module \"" + facet.getModule().getName() + '"', STDERR);
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
    getProcessHandler().notifyTextAvailable("Uploading file\n\tlocal path: " + localPath + "\n\tremote path: " + remotePath + '\n', STDOUT);
    SyncService service = device.getSyncService();
    if (service == null) {
      getProcessHandler().notifyTextAvailable("Can't upload file: device is not available.\n", STDERR);
      return false;
    }
    SyncService.SyncResult result = service.pushFile(localPath, remotePath, new MyISyncProgressMonitor());
    int code = result.getCode();
    String errorMessage;
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
    getProcessHandler()
      .notifyTextAvailable(errorMessage + (result.getMessage() != null ? "\n" + result.getMessage() + "\n" : "\n"), STDERR);
    return false;
  }

  @SuppressWarnings({"DuplicateThrows"})
  public void executeDeviceCommandAndWriteToConsole(IDevice device, String command, AndroidOutputReceiver receiver) throws IOException,
                                                                                                                           TimeoutException,
                                                                                                                           AdbCommandRejectedException,
                                                                                                                           ShellCommandUnresponsiveException {
    getProcessHandler().notifyTextAvailable("DEVICE SHELL COMMAND: " + command + "\n", STDOUT);
    AndroidUtils.executeCommand(device, command, receiver, false);
  }

  private static boolean isSuccess(MyReceiver receiver) {
    return receiver.errorType == NO_ERROR && receiver.failureMessage == null;
  }

  private boolean installApp(IDevice device, String remotePath, @NotNull String packageName) throws IOException {
    getProcessHandler().notifyTextAvailable("Installing " + packageName + ".\n", STDOUT);
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
      getProcessHandler().notifyTextAvailable("Device is not ready. Waiting for " + WAITING_TIME + " sec.\n", STDOUT);
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
    getProcessHandler().notifyTextAvailable(receiver.output.toString(), success ? STDOUT : STDERR);
    return success;
  }
}
