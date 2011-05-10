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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.actions.AndroidEnableDdmsAction;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;

import static org.jetbrains.android.util.AndroidUtils.ADB;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 2, 2009
 * Time: 2:35:49 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AndroidSdk {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdk");

  private static volatile boolean myDdmLibInitialized = false;

  private static volatile boolean myAdbCrashed = false;

  private static final Object myDdmsLock = new Object();

  @NotNull
  public abstract String getLocation();

  @NotNull
  public abstract IAndroidTarget[] getTargets();

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
  public IAndroidTarget findTargetByLocation(@NotNull String location) {
    for (IAndroidTarget target : getTargets()) {
      String targetPath = FileUtil.toSystemIndependentName(target.getLocation());
      if (FileUtil.pathsEqual(location + '/', targetPath) || FileUtil.pathsEqual(location, targetPath)) {
        return target;
      }
    }
    return null;
  }

  public abstract IAndroidTarget findTargetByHashString(@NotNull String hashString);

  @Nullable
  public static AndroidSdk parse(@NotNull String path, @NotNull ISdkLog log) {
    path = FileUtil.toSystemDependentName(path);
    SdkManager manager = SdkManager.createManager(path + File.separatorChar, log);
    if (manager != null) {
      return new AndroidSdkImpl(manager);
    }
    return null;
  }

  @Nullable
  public static AndroidSdk parse(@NotNull String path, @NotNull final Component component) {
    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    AndroidSdk sdk = parse(path, log);
    if (sdk == null) {
      String message = log.getErrorMessage();
      if (message.length() > 0) {
        message = "Android SDK is parsed incorrectly. Parsing log:\n" + message;
        Messages.showInfoMessage(component, message, CommonBundle.getErrorTitle());
      }
    }
    return sdk;
  }

  static boolean isAndroid15Sdk(@NotNull String location) {
    VirtualFile sdkDir = LocalFileSystem.getInstance().findFileByPath(location);
    return sdkDir != null && sdkDir.findChild(SdkConstants.FD_PLATFORMS) != null;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj.getClass() != getClass()) return false;
    AndroidSdk sdk = (AndroidSdk)obj;
    return FileUtil.pathsEqual(getLocation(), sdk.getLocation());
  }

  @Override
  public int hashCode() {
    return getLocation().hashCode();
  }

  private boolean initializeDdmlib(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    while (true) {
      final MyInitializeDdmlibTask task = new MyInitializeDdmlibTask(project);

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

        myAdbCrashed = !finished;

        if (task.isCanceled()) {
          forceInterrupt(t);
          return false;
        }

        if (!finished) {
          int result = Messages
            .showDialog(project, "ADB not responding. Please, kill \"" + SdkConstants.FN_ADB + "\" process manually and click 'Retry'",
                        CommonBundle.getErrorTitle(), new String[]{"&Retry", "&Cancel"}, 0, Messages.getErrorIcon());

          if (result == 1) {
            forceInterrupt(t);
            return false;
          }
          retryWas = true;
        }
      }

      // task finished, but if we had problems, ddmlib can be still initialized incorrectly, so we invoke initialize once again
      if (!retryWas) {
        break;
      }
    }

    return true;
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
    synchronized (myDdmsLock) {
      String adbPath = getAdbPath();
      if (!myDdmLibInitialized) {
        myDdmLibInitialized = true;
        DdmPreferences.setTimeOut(AndroidUtils.TIMEOUT);
        AndroidDebugBridge.init(AndroidEnableDdmsAction.isDdmsEnabled());
        LOG.info("DDMLib initialized");
        AndroidDebugBridge.createBridge(adbPath, true);
      }
      else {
        final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
        final boolean forceRestart = myAdbCrashed || (bridge != null && !bridge.isConnected());
        AndroidDebugBridge.createBridge(adbPath, forceRestart);
      }
    }
  }

  private String getAdbPath() {
    String path = getLocation() + File.separator + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + ADB;
    if (!new File(path).exists()) {
      return getLocation() + File.separator + AndroidUtils.toolPath(ADB);
    }
    return path;
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

  @Nullable
  public IAndroidTarget getNewerPlatformTarget() {
    IAndroidTarget[] targets = getTargets();
    IAndroidTarget result = null;
    for (IAndroidTarget target : targets) {
      if (target.isPlatform()) {
        if (result == null || target.compareTo(result) > 0) {
          result = target;
        }
      }
    }
    return result;
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

        final long timeout = 7000;

        while (!myFinished && !myCanceled) {
          long wastedTime = System.currentTimeMillis() - startTime;
          if (wastedTime >= timeout) {
            break;
          }
          try {
            myLock.wait(timeout - wastedTime);
          }
          catch (InterruptedException e) {
            break;
          }
        }
      }
    }
  }
}
