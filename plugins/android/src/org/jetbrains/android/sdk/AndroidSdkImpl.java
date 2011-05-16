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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 2, 2009
 * Time: 2:37:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidSdkImpl extends AndroidSdk {
  private final SdkManager mySdkManager;
  private IAndroidTarget[] myTargets = null;

  public AndroidSdkImpl(@NotNull SdkManager sdkManager) {
    mySdkManager = sdkManager;
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

  @Override
  public IAndroidTarget findTargetByHashString(@NotNull String hashString) {
    final IAndroidTarget target = mySdkManager.getTargetFromHashString(hashString);
    return target != null ? new MyTargetWrapper(target) : null;
  }

  @NotNull
  public SdkManager getSdkManager() {
    return mySdkManager;
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
