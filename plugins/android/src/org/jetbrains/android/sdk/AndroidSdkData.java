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

package org.jetbrains.android.sdk;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.utils.ILogger;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.logcat.AdbErrors;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.BufferingFileWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkData");

  private static volatile boolean myDdmLibInitialized = false;

  private static volatile boolean myAdbCrashed = false;

  private static final Object myDdmsLock = new Object();

  private final Map<IAndroidTarget, SoftReference<AndroidTargetData>> myTargetDatas =
    new HashMap<IAndroidTarget, SoftReference<AndroidTargetData>>();

  private final SdkManager mySdkManager;
  private IAndroidTarget[] myTargets = null;

  private final int myPlatformToolsRevision;
  private final int mySdkToolsRevision;

  public AndroidSdkData(@NotNull SdkManager sdkManager, @NotNull String sdkDirOsPath) {
    mySdkManager = sdkManager;
    myPlatformToolsRevision = parsePackageRevision(sdkDirOsPath, SdkConstants.FD_PLATFORM_TOOLS);
    mySdkToolsRevision = parsePackageRevision(sdkDirOsPath, SdkConstants.FD_TOOLS);
  }

  private static int parsePackageRevision(@NotNull String sdkDirOsPath, @NotNull String packageDirName) {
    final File propFile =
      new File(sdkDirOsPath + File.separatorChar + packageDirName + File.separatorChar + SdkConstants.FN_SOURCE_PROP);
    int revisionNumber = -1;
    if (propFile.exists() && propFile.isFile()) {
      final Map<String, String> map =
        ProjectProperties.parsePropertyFile(new BufferingFileWrapper(propFile), new MessageBuildingSdkLog());
      final String revision = map.get("Pkg.Revision");
      if (revision != null) {
        try {
          revisionNumber = Integer.parseInt(revision);
        }
        catch (NumberFormatException e) {
          LOG.info(e);
        }
      }
    }
    return revisionNumber > 0 ? revisionNumber : -1;
  }

  @NotNull
  public String getLocation() {
    String location = mySdkManager.getLocation();
    if (location.length() > 0) {
      char lastChar = location.charAt(location.length() - 1);
      if (lastChar == '/' || lastChar == File.separatorChar) {
        return location.substring(0, location.length() - 1);
      }
    }
    return location;
  }

  @NotNull
  public IAndroidTarget[] getTargets() {
    if (myTargets == null) {
      IAndroidTarget[] targets = mySdkManager.getTargets();
      if (targets != null) {
        myTargets = new IAndroidTarget[targets.length];
        for (int i = 0; i < targets.length; i++) {
          myTargets[i] = new MyTargetWrapper(targets[i]);
        }
      }
    }
    return myTargets;
  }

  // be careful! target name is NOT unique

  @Nullable
  public IAndroidTarget findTargetByName(@NotNull String name) {
    for (IAndroidTarget target : getTargets()) {
      if (target.getName().equals(name)) {
        return target;
      }
    }
    return null;
  }

  @Nullable
  public IAndroidTarget findTargetByApiLevel(@NotNull String apiLevel) {
    IAndroidTarget candidate = null;
    for (IAndroidTarget target : getTargets()) {
      if (AndroidSdkUtils.targetHasId(target, apiLevel)) {
        if (target.isPlatform()) {
          return target;
        }
        else if (candidate == null) {
          candidate = target;
        }
      }
    }
    return candidate;
  }

  @Nullable
  public IAndroidTarget findTargetByHashString(@NotNull String hashString) {
    final IAndroidTarget target = mySdkManager.getTargetFromHashString(hashString);
    return target != null ? new MyTargetWrapper(target) : null;
  }

  public int getPlatformToolsRevision() {
    return myPlatformToolsRevision;
  }

  public int getSdkToolsRevision() {
    return mySdkToolsRevision;
  }

  @Nullable
  public static AndroidSdkData parse(@NotNull String path, @NotNull ILogger log) {
    final SdkManager manager = AndroidCommonUtils.createSdkManager(path, log);
    return manager != null ? new AndroidSdkData(manager, path) : null;
  }

  @Nullable
  public static AndroidSdkData parse(@NotNull String path, @NotNull final Component component) {
    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    AndroidSdkData sdkData = parse(path, log);
    if (sdkData == null) {
      String message = log.getErrorMessage();
      if (message.length() > 0) {
        message = "Android SDK is parsed incorrectly. Parsing log:\n" + message;
        Messages.showInfoMessage(component, message, CommonBundle.getErrorTitle());
      }
    }
    return sdkData;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;
    AndroidSdkData sdkData = (AndroidSdkData)obj;
    return FileUtil.pathsEqual(getLocation(), sdkData.getLocation());
  }

  @Override
  public int hashCode() {
    return getLocation().hashCode();
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private boolean initializeDdmlib(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    while (true) {
      final MyInitializeDdmlibTask task = new MyInitializeDdmlibTask(project);

      AdbErrors.clear();

      Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
          doInitializeDdmlib();
          task.finish();
        }
      });

      t.start();

      boolean retryWas = false;

      while (!task.isFinished()) {
        ProgressManager.getInstance().run(task);

        boolean finished = task.isFinished();

        if (task.isCanceled()) {
          myAdbCrashed = !finished;
          forceInterrupt(t);
          return false;
        }

        myAdbCrashed = false;

        if (!finished) {
          final String adbErrorString = combine(AdbErrors.getErrors());
          final int result = Messages.showDialog(project, "ADB not responding. You can wait more, or kill \"" +
                                                          SdkConstants.FN_ADB +
                                                          "\" process manually and click 'Restart'" +
                                                          (adbErrorString.length() > 0 ? "\nErrors from ADB:\n" + adbErrorString : ""),
                                                 CommonBundle.getErrorTitle(), new String[]{"&Wait more", "&Restart", "&Cancel"}, 0,
                                                 Messages.getErrorIcon());
          if (result == 2) {
            // cancel
            myAdbCrashed = true;
            forceInterrupt(t);
            return false;
          }
          else if (result == 1) {
            // restart
            myAdbCrashed = true;
            retryWas = true;
          }
        }
      }

      // task finished, but if we had problems, ddmlib can be still initialized incorrectly, so we invoke initialize once again
      if (!retryWas) {
        break;
      }
    }

    return true;
  }

  @NotNull
  private static String combine(@NotNull String[] strs) {
    final StringBuilder builder = new StringBuilder();

    for (String str : strs) {
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(str);
    }
    return builder.toString();
  }

  @SuppressWarnings({"BusyWait"})
  private static void forceInterrupt(Thread thread) {
    /*
      ddmlib has incorrect handling of InterruptedException, so we need to invoke it several times,
      because there are three blocking invokation in succession
    */

    for (int i = 0; i < 6 && thread.isAlive(); i++) {
      thread.interrupt();
      try {
        Thread.sleep(200);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void doInitializeDdmlib() {
    doInitializeDdmlib(getAdbPath());
  }

  private static void doInitializeDdmlib(@NotNull String adbPath) {
    synchronized (myDdmsLock) {
      if (!myDdmLibInitialized) {
        myDdmLibInitialized = true;
        DdmPreferences.setLogLevel(Log.LogLevel.INFO.getStringValue());
        DdmPreferences.setTimeOut(AndroidUtils.TIMEOUT);
        AndroidDebugBridge.init(AndroidEnableAdbServiceAction.isAdbServiceEnabled());
        LOG.info("DDMLib initialized");
        final AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(adbPath, true);
        waitUntilConnect(bridge);
        if (!bridge.isConnected()) {
          LOG.info("Failed to connect debug bridge");
        }
      }
      else {
        final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
        final boolean forceRestart = myAdbCrashed || (bridge != null && !bridge.isConnected());
        if (forceRestart) {
          LOG.info("Restart debug bridge: " + (myAdbCrashed ? "crashed" : "disconnected"));
        }
        final AndroidDebugBridge newBridge = AndroidDebugBridge.createBridge(adbPath, forceRestart);
        waitUntilConnect(newBridge);
        if (!newBridge.isConnected()) {
          LOG.info("Failed to connect debug bridge after restart");
        }
      }
    }
  }

  private static void waitUntilConnect(@NotNull AndroidDebugBridge bridge) {
    while (!bridge.isConnected() && !Thread.currentThread().isInterrupted()) {
      try {
        //noinspection BusyWait
        Thread.sleep(1000);
      }
      catch (InterruptedException e) {
        LOG.debug(e);
        return;
      }
    }
  }

  private String getAdbPath() {
    String path = getLocation() + File.separator + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_ADB;
    if (!new File(path).exists()) {
      path = getLocation() + File.separator + AndroidCommonUtils.toolPath(SdkConstants.FN_ADB);
    }
    try {
      return new File(path).getCanonicalPath();
    }
    catch (IOException e) {
      LOG.info(e);
      return path;
    }
  }

  public static void terminateDdmlib() {
    synchronized (myDdmsLock) {
      AndroidDebugBridge.disconnectBridge();
      AndroidDebugBridge.terminate();
      LOG.info("DDMLib terminated");
      myDdmLibInitialized = false;
    }
  }

  @Nullable
  public AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    if (!initializeDdmlib(project)) {
      return null;
    }
    return AndroidDebugBridge.getBridge();
  }

  @NotNull
  public SdkManager getSdkManager() {
    return mySdkManager;
  }

  @Nullable
  public AndroidTargetData getTargetData(@NotNull IAndroidTarget target) {
    final SoftReference<AndroidTargetData> targetDataRef = myTargetDatas.get(target);
    AndroidTargetData targetData = targetDataRef != null ? targetDataRef.get() : null;
    if (targetData == null) {
      targetData = new AndroidTargetData(this, target);
      myTargetDatas.put(target, new SoftReference<AndroidTargetData>(targetData));
    }
    return targetData;
  }

  private static class MyInitializeDdmlibTask extends Task.Modal {
    private final Object myLock = new Object();
    private volatile boolean myFinished;
    private volatile boolean myCanceled;

    public MyInitializeDdmlibTask(Project project) {
      super(project, "Waiting for ADB", true);
    }

    public boolean isFinished() {
      synchronized (myLock) {
        return myFinished;
      }
    }

    public boolean isCanceled() {
      synchronized (myLock) {
        return myCanceled;
      }
    }

    public void finish() {
      synchronized (myLock) {
        myFinished = true;
        myLock.notifyAll();
      }
    }

    @Override
    public void onCancel() {
      synchronized (myLock) {
        myCanceled = true;
        myLock.notifyAll();
      }
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      synchronized (myLock) {
        final long startTime = System.currentTimeMillis();

        final long timeout = 10000;

        while (!myFinished && !myCanceled && !indicator.isCanceled()) {
          long wastedTime = System.currentTimeMillis() - startTime;
          if (wastedTime >= timeout) {
            break;
          }
          try {
            myLock.wait(Math.min(timeout - wastedTime, 500));
          }
          catch (InterruptedException e) {
            break;
          }
        }
      }
    }
  }

  private static class MyTargetWrapper implements IAndroidTarget {
    private final TIntObjectHashMap<String> myAlternativePaths;
    private final IAndroidTarget myWrapee;

    private MyTargetWrapper(@NotNull IAndroidTarget wrapee) {
      myWrapee = wrapee;
      myAlternativePaths = new TIntObjectHashMap<String>();
      String oldPlatformToolsFolderPath = getOldPlatformToolsFolderPath();
      if (!canFindTool(AAPT)) {
        myAlternativePaths.put(AAPT, oldPlatformToolsFolderPath + SdkConstants.FN_AAPT);
      }
      if (!canFindTool(AIDL)) {
        myAlternativePaths.put(AIDL, oldPlatformToolsFolderPath + SdkConstants.FN_AIDL);
      }
      if (!canFindTool(DX)) {
        myAlternativePaths.put(DX, oldPlatformToolsFolderPath + SdkConstants.FN_DX);
      }
      if (!canFindTool(DX_JAR)) {
        myAlternativePaths.put(DX_JAR, oldPlatformToolsFolderPath + SdkConstants.FD_LIB + File.separator + SdkConstants.FN_DX_JAR);
      }
    }

    @Nullable
    private String getOldPlatformToolsFolderPath() {
      String platformLocation;
      if (myWrapee.isPlatform()) {
        platformLocation = myWrapee.getLocation();
      }
      else {
        IAndroidTarget parent = myWrapee.getParent();
        platformLocation = parent != null ? parent.getLocation() : null;
      }
      if (platformLocation == null) {
        return null;
      }
      return platformLocation + SdkConstants.FD_TOOLS + File.separator;
    }

    private boolean canFindTool(int pathId) {
      String path = myWrapee.getPath(pathId);
      return path != null && new File(path).exists();
    }

    @Override
    public String getLocation() {
      return myWrapee.getLocation();
    }

    @Override
    public String getVendor() {
      return myWrapee.getVendor();
    }

    @Override
    public String getName() {
      return myWrapee.getName();
    }

    @Override
    public String getFullName() {
      return myWrapee.getFullName();
    }

    @Override
    public String getClasspathName() {
      return myWrapee.getClasspathName();
    }

    @Override
    public String getShortClasspathName() {
      return myWrapee.getShortClasspathName();
    }

    @Override
    public String getDescription() {
      return myWrapee.getDescription();
    }

    @Override
    public AndroidVersion getVersion() {
      return myWrapee.getVersion();
    }

    @Override
    public String getVersionName() {
      return myWrapee.getVersionName();
    }

    @Override
    public int getRevision() {
      return myWrapee.getRevision();
    }

    @Override
    public boolean isPlatform() {
      return myWrapee.isPlatform();
    }

    @Override
    public IAndroidTarget getParent() {
      return myWrapee.getParent();
    }

    @Override
    public String getPath(int pathId) {
      String path = myAlternativePaths.get(pathId);
      if (path != null) {
        return path;
      }
      return myWrapee.getPath(pathId);
    }

    @Override
    public boolean hasRenderingLibrary() {
      return myWrapee.hasRenderingLibrary();
    }

    @Override
    public String[] getSkins() {
      return myWrapee.getSkins();
    }

    @Override
    public String getDefaultSkin() {
      return myWrapee.getDefaultSkin();
    }

    @Override
    public IOptionalLibrary[] getOptionalLibraries() {
      return myWrapee.getOptionalLibraries();
    }

    @Override
    public String[] getPlatformLibraries() {
      return myWrapee.getPlatformLibraries();
    }

    @Override
    public String getProperty(String name) {
      return myWrapee.getProperty(name);
    }

    @Override
    public Integer getProperty(String name, Integer defaultValue) {
      return myWrapee.getProperty(name, defaultValue);
    }

    @Override
    public Boolean getProperty(String name, Boolean defaultValue) {
      return myWrapee.getProperty(name, defaultValue);
    }

    @Override
    public Map<String, String> getProperties() {
      return myWrapee.getProperties();
    }

    @Override
    public int getUsbVendorId() {
      return myWrapee.getUsbVendorId();
    }

    @Override
    public ISystemImage[] getSystemImages() {
      return myWrapee.getSystemImages();
    }

    @Override
    public ISystemImage getSystemImage(String abiType) {
      return myWrapee.getSystemImage(abiType);
    }

    @Override
    public boolean canRunOn(IAndroidTarget target) {
      return myWrapee.canRunOn(target);
    }

    @Override
    public String hashString() {
      return myWrapee.hashString();
    }

    @Override
    public int compareTo(IAndroidTarget o) {
      return myWrapee.compareTo(o);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyTargetWrapper)) {
        return false;
      }
      MyTargetWrapper other = (MyTargetWrapper)obj;
      return myWrapee.equals(other.myWrapee);
    }

    @Override
    public int hashCode() {
      return myWrapee.hashCode();
    }
  }
}
