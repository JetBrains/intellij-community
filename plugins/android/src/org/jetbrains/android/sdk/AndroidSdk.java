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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.actions.AndroidEnableDdmsAction;
import org.jetbrains.android.ddms.AdbManager;
import org.jetbrains.android.ddms.AdbNotRespondingException;
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
  private static volatile boolean myDdmLibInitialized = false;

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
    for (IAndroidTarget target : getTargets()) {
      if (apiLevel.equals(target.getVersion().getApiString())) {
        return target;
      }
    }
    return null;
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

  public void initializeDdmlib() {
    synchronized (myDdmsLock) {
      String adbPath = getAdbPath();
      if (!myDdmLibInitialized) {
        myDdmLibInitialized = true;
        DdmPreferences.setTimeOut(AndroidUtils.TIMEOUT);
        AndroidDebugBridge.init(AndroidEnableDdmsAction.isDdmsEnabled());
        AndroidDebugBridge.createBridge(adbPath, true);
      }
      else {
        AndroidDebugBridge.createBridge(adbPath, false);
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
      myDdmLibInitialized = false;
    }
  }

  @Nullable
  public AndroidDebugBridge getDebugBridge(Project project) {
    AndroidDebugBridge bridge;
    try {
      bridge = AdbManager.compute(new Computable<AndroidDebugBridge>() {
        public AndroidDebugBridge compute() {
          initializeDdmlib();
          return AndroidDebugBridge.getBridge();
        }
      }, false);
    }
    catch (AdbNotRespondingException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    return bridge;
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
}
