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
import com.android.sdklib.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 13, 2009
 * Time: 5:17:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidTarget11 implements IAndroidTarget {
  private final String myLocation;
  private final TIntObjectHashMap<String> myPaths = new TIntObjectHashMap<String>();
  private final AndroidVersion myVersion = new AndroidVersion(2, null);

  private static final String TOOLS_FOLDER = "tools/";
  private static final String LIB_FOLDER = TOOLS_FOLDER + "lib/";
  private static final String IMAGES_FOLDER = LIB_FOLDER + "images/";
  private static final String RES_FOLDER = LIB_FOLDER + "res/default/";
  private static final String VALUES_FOLDER = RES_FOLDER + "values/";

  private static final String AAPT_NAME = SystemInfo.isWindows ? "aapt.exe" : "aapt";
  private static final String AIDL_NAME = SystemInfo.isWindows ? "aidl.exe" : "aidl";
  private static final String DX_NAME = SystemInfo.isWindows ? "dx.bat" : "dx";

  public AndroidTarget11(@NotNull String location) {
    myLocation = location;
    myPaths.put(ANDROID_JAR, "android.jar");
    //myPaths.put(SOURCES, FD_ANDROID_SOURCES);
    myPaths.put(IMAGES, IMAGES_FOLDER);
    myPaths.put(SAMPLES, "samples/");
    myPaths.put(SKINS, IMAGES_FOLDER + "skins/");
    myPaths.put(TEMPLATES, LIB_FOLDER);
    //myPaths.put(DATA, OS_PLATFORM_DATA_FOLDER);
    myPaths.put(ATTRIBUTES, VALUES_FOLDER + "attrs.xml");
    myPaths.put(MANIFEST_ATTRIBUTES, VALUES_FOLDER + "attrs_manifest.xml");
    myPaths.put(RESOURCES, RES_FOLDER);
    myPaths.put(FONTS, LIB_FOLDER + "fonts/");
    myPaths.put(LAYOUT_LIB, LIB_FOLDER + "layoutlib.jar");
    myPaths.put(WIDGETS, LIB_FOLDER + "widgets.txt");
    myPaths.put(ACTIONS_ACTIVITY, LIB_FOLDER + "activity_actions.txt");
    myPaths.put(ACTIONS_BROADCAST, LIB_FOLDER + "broadcast_actions.txt");
    myPaths.put(ACTIONS_SERVICE, LIB_FOLDER + "service_actions.txt");
    myPaths.put(CATEGORIES, LIB_FOLDER + "categories.txt");
    myPaths.put(AAPT, TOOLS_FOLDER + AAPT_NAME);
    myPaths.put(AIDL, TOOLS_FOLDER + AIDL_NAME);
    myPaths.put(DX, TOOLS_FOLDER + DX_NAME);
    myPaths.put(DX_JAR, LIB_FOLDER + "dx.jar");
    myPaths.put(ANDROID_AIDL, LIB_FOLDER + SdkConstants.FN_FRAMEWORK_AIDL);
  }

  public boolean isValid() {
    if (!new File(myLocation).isDirectory()) {
      return false;
    }
    for (Object o : myPaths.getValues()) {
      String path = myLocation + File.separator + o;
      if (path.endsWith("/")) {
        File f = new File(path.substring(0, path.length() - 1));
        if (!f.isDirectory()) return false;
      }
      if (!new File(path).exists()) return false;
    }
    return true;
  }

  @NotNull
  public String getLocation() {
    return myLocation;
  }

  public String getVendor() {
    return "Android Open Source Project";
  }

  public String getName() {
    return "Android 1.1";
  }

  public String getFullName() {
    return getName();
  }

  public String getClasspathName() {
    return getName();
  }

  public String getDescription() {
    return "Android 1.1 Sdk Target";
  }

  public AndroidVersion getVersion() {
    return myVersion;
  }

  public String getVersionName() {
    return "1.1";
  }

  public int getRevision() {
    return 1;
  }

  public boolean isPlatform() {
    return true;
  }

  @Nullable
  public IAndroidTarget getParent() {
    return null;
  }

  public String getPath(int pathId) {
    String path = myLocation + '/' + myPaths.get(pathId);
    path = path.replace("/", File.separator);
    return path;
  }

  public String[] getSkins() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  public String getDefaultSkin() {
    return null;
  }

  @Nullable
  public IOptionalLibrary[] getOptionalLibraries() {
    return null;
  }

  public String[] getPlatformLibraries() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  @Override
  public String getProperty(String name) {
    return null;
  }

  @Override
  public Integer getProperty(String name, Integer defaultValue) {
    return defaultValue;
  }

  @Override
  public Boolean getProperty(String name, Boolean defaultValue) {
    return defaultValue;
  }

  @Nullable
  @Override
  public Map<String, String> getProperties() {
    return null;
  }

  public int getUsbVendorId() {
    return NO_USB_ID;
  }

  public boolean canRunOn(IAndroidTarget target) {
    return target == this || target.getVersion().getApiLevel() >= getVersion().getApiLevel();
  }

  public String hashString() {
    throw new UnsupportedOperationException();
  }

  public int compareTo(IAndroidTarget target) {
    if (target instanceof LegacyAndroidSdk) {
      return 0;
    }
    if (!target.isPlatform()) {
      return -1;
    }
    return getVersion().getApiLevel() - target.getVersion().getApiLevel();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidTarget11 that = (AndroidTarget11)o;

    if (myLocation != null ? !myLocation.equals(that.myLocation) : that.myLocation != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myLocation != null ? myLocation.hashCode() : 0;
  }
}
