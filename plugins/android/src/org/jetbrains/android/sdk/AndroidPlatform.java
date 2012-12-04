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

import com.android.sdklib.IAndroidTarget;
import com.android.SdkConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 15, 2009
 * Time: 6:54:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidPlatform {
  private final AndroidSdkData mySdkData;
  private final IAndroidTarget myTarget;

  public AndroidPlatform(@NotNull AndroidSdkData sdkData, @NotNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  @Nullable
  public static AndroidPlatform getInstance(@NotNull Module module) {
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return null;
    }

    final AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (sdkAdditionalData == null) {
      return null;
    }
    return sdkAdditionalData.getAndroidPlatform();
  }

  @NotNull
  public AndroidSdkData getSdkData() {
    return mySdkData;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @Nullable
  public static AndroidPlatform parse(@NotNull Sdk sdk) {
    if (!(sdk.getSdkType().equals(AndroidSdkType.getInstance()))) {
      return null;
    }
    String sdkPath = sdk.getHomePath();
    if (sdkPath != null) {
      AndroidSdkData sdkData = AndroidSdkData.parse(sdkPath, new EmptySdkLog());
      if (sdkData != null) {
        AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
        IAndroidTarget target = data != null ? data.getBuildTarget(sdkData) : null;
        if (target != null) {
          return new AndroidPlatform(sdkData, target);
        }
      }
    }
    return null;
  }

  // deprecated, use only for converting
  @Nullable
  @Deprecated
  public static AndroidPlatform parse(@NotNull Library library,
                                      @Nullable Library.ModifiableModel model,
                                      @Nullable Map<String, AndroidSdkData> parsedSdks) {
    VirtualFile[] files = model != null ? model.getFiles(OrderRootType.CLASSES) : library.getFiles(OrderRootType.CLASSES);
    Set<String> jarPaths = new HashSet<String>();
    VirtualFile frameworkLibrary = null;
    for (VirtualFile file : files) {
      VirtualFile vFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (vFile != null) {
        if (vFile.getName().equals(SdkConstants.FN_FRAMEWORK_LIBRARY)) {
          frameworkLibrary = vFile;
        }
        jarPaths.add(vFile.getPath());
      }
    }
    if (frameworkLibrary != null) {
      VirtualFile sdkDir = frameworkLibrary.getParent();
      if (sdkDir != null) {
        VirtualFile platformsDir = sdkDir.getParent();
        if (platformsDir != null && platformsDir.getName().equals(SdkConstants.FD_PLATFORMS)) {
          sdkDir = platformsDir.getParent();
          if (sdkDir == null) return null;
        }
        String sdkPath = sdkDir.getPath();
        AndroidSdkData sdkData = parsedSdks != null ? parsedSdks.get(sdkPath) : null;
        if (sdkData == null) {
          sdkData = AndroidSdkData.parse(sdkPath, new EmptySdkLog());
          if (sdkData == null) return null;
          if (parsedSdks != null) {
            parsedSdks.put(sdkPath, sdkData);
          }
        }
        IAndroidTarget resultTarget = null;
        for (IAndroidTarget target : sdkData.getTargets()) {
          String targetsFrameworkLibPath = PathUtil.getCanonicalPath(target.getPath(IAndroidTarget.ANDROID_JAR));
          if (frameworkLibrary.getPath().equals(targetsFrameworkLibPath)) {
            if (target.isPlatform()) {
              if (resultTarget == null) resultTarget = target;
            }
            else {
              boolean ok = true;
              IAndroidTarget.IOptionalLibrary[] libraries = target.getOptionalLibraries();
              if (libraries == null) {
                // we cannot identify add-on target without optional libraries by classpath
                ok = false;
              }
              else {
                for (IAndroidTarget.IOptionalLibrary optionalLibrary : libraries) {
                  if (!jarPaths.contains(PathUtil.getCanonicalPath(optionalLibrary.getJarPath()))) {
                    ok = false;
                  }
                }
              }
              if (ok) resultTarget = target;
            }
          }
        }
        if (resultTarget != null) {
          return new AndroidPlatform(sdkData, resultTarget);
        }
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidPlatform platform = (AndroidPlatform)o;

    if (!mySdkData.equals(platform.mySdkData)) return false;
    if (!myTarget.equals(platform.myTarget)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySdkData.hashCode();
    result = 31 * result + myTarget.hashCode();
    return result;
  }

  public boolean needToAddAnnotationsJarToClasspath() {
    // todo: check if we really don't need to add this
    return getApiLevel() <= 15;
  }

  public int getApiLevel() {
    return myTarget.getVersion().getApiLevel();
  }
}
